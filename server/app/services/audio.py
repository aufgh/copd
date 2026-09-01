"""音频质检与归一化。

只依赖标准库 wave + numpy，刻意避开 soundfile / librosa / scipy，
让 Windows 上 pip install 一次成功。

对外提供 AudioRef：归一化到 16 kHz 单声道 float32 的音频，加质检指标。
真模型接入后直接消费 AudioRef.samples，不需要再碰文件。
"""

from __future__ import annotations

import math
import wave
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np

from app import config

_EPS = 1e-12


@dataclass(slots=True)
class AudioRef:
    """一段已归一化的音频 + 它的质检结论。"""

    path: Path | None
    samples: np.ndarray            # float32, [-1, 1], 单声道, 16 kHz
    sample_rate: int
    source_sample_rate: int
    source_channels: int
    duration_ms: int
    rms_dbfs: float
    peak_dbfs: float
    clip_ratio: float
    ok: bool = True
    label: str = "良好"            # 良好 / 可用 / 不合格
    issues: list[str] = field(default_factory=list)
    codes: list[str] = field(default_factory=list)

    def as_quality_dict(self) -> dict:
        return {
            "ok": self.ok,
            "label": self.label,
            "sample_rate": self.sample_rate,
            "channels": 1,
            "duration_ms": self.duration_ms,
            "rms_dbfs": round(self.rms_dbfs, 1),
            "peak_dbfs": round(self.peak_dbfs, 1),
            "clip_ratio": round(self.clip_ratio, 4),
            "issues": list(self.issues),
        }


class AudioDecodeError(Exception):
    def __init__(self, code: str, message: str, field: str | None = None) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        # 调用方解码完两段录音后会把 field 填成 breathing_file / cough_file
        self.field = field


def _to_dbfs(value: float) -> float:
    return 20.0 * math.log10(max(value, _EPS))


def _resample_linear(x: np.ndarray, src_sr: int, dst_sr: int) -> np.ndarray:
    """线性插值重采样。手机端本来就录 16 kHz，这里只是兜底路径。"""
    if src_sr == dst_sr or x.size == 0:
        return x
    n_dst = int(round(x.size * dst_sr / src_sr))
    if n_dst <= 1:
        return np.zeros(0, dtype=np.float32)
    src_idx = np.linspace(0.0, x.size - 1.0, n_dst, dtype=np.float64)
    return np.interp(src_idx, np.arange(x.size, dtype=np.float64), x).astype(np.float32)


def decode_wav(path: Path) -> AudioRef:
    """读 WAV -> 单声道 float32 -> 重采样到 16 kHz -> 算质检指标。"""
    try:
        with wave.open(str(path), "rb") as wf:
            channels = wf.getnchannels()
            sample_width = wf.getsampwidth()
            src_sr = wf.getframerate()
            n_frames = wf.getnframes()
            raw = wf.readframes(n_frames)
    except (wave.Error, EOFError) as exc:
        # 截断的文件 RIFF 头可能是合法的，wave 读到一半抛 EOFError
        raise AudioDecodeError("UNSUPPORTED_FORMAT", f"不是有效的 WAV 文件：{exc}") from exc
    except OSError as exc:
        raise AudioDecodeError("UNSUPPORTED_FORMAT", f"无法读取音频文件：{exc}") from exc

    if sample_width == 2:
        data = np.frombuffer(raw, dtype="<i2").astype(np.float32) / 32768.0
    elif sample_width == 1:
        data = (np.frombuffer(raw, dtype=np.uint8).astype(np.float32) - 128.0) / 128.0
    elif sample_width == 4:
        data = np.frombuffer(raw, dtype="<i4").astype(np.float32) / 2147483648.0
    else:
        raise AudioDecodeError(
            "UNSUPPORTED_FORMAT", f"不支持 {sample_width * 8} 位采样，需要 16 位 PCM WAV"
        )

    if channels > 1:
        usable = (data.size // channels) * channels
        data = data[:usable].reshape(-1, channels).mean(axis=1)

    # 削波比例要在重采样之前算，插值会把峰值削掉
    clip_ratio = float(np.mean(np.abs(data) >= 0.997)) if data.size else 0.0
    peak = float(np.max(np.abs(data))) if data.size else 0.0

    samples = _resample_linear(np.ascontiguousarray(data, dtype=np.float32),
                              src_sr, config.AUDIO_SAMPLE_RATE)
    rms = float(np.sqrt(np.mean(np.square(samples)))) if samples.size else 0.0
    duration_ms = int(round(samples.size / config.AUDIO_SAMPLE_RATE * 1000))

    return AudioRef(
        path=path,
        samples=samples,
        sample_rate=config.AUDIO_SAMPLE_RATE,
        source_sample_rate=src_sr,
        source_channels=channels,
        duration_ms=duration_ms,
        rms_dbfs=_to_dbfs(rms),
        peak_dbfs=_to_dbfs(peak),
        clip_ratio=clip_ratio,
    )


def check_quality(ref: AudioRef, kind: str) -> AudioRef:
    """按 config 里的阈值判定质量，就地写回 ok / label / issues / codes。

    kind: "breathing" 或 "cough"，两者最短时长要求不同。
    """
    min_ms = config.BREATHING_MIN_MS if kind == "breathing" else config.COUGH_MIN_MS
    issues: list[str] = []
    codes: list[str] = []
    fatal = False

    if ref.samples.size == 0:
        issues.append("没有采集到任何声音")
        codes.append("AUDIO_EMPTY")
        fatal = True
    else:
        if ref.duration_ms < min_ms:
            issues.append(f"时长 {ref.duration_ms / 1000:.1f} 秒，不足 {min_ms / 1000:.0f} 秒")
            codes.append("AUDIO_TOO_SHORT")
            fatal = True
        if ref.rms_dbfs < config.MIN_RMS_DBFS:
            issues.append("音量过低，可能距离过远或麦克风被遮挡")
            codes.append("AUDIO_TOO_QUIET")
            fatal = True
        if ref.clip_ratio > config.MAX_CLIP_RATIO:
            issues.append("音量过大出现削波失真")
            codes.append("AUDIO_CLIPPING")
            fatal = True

    ref.issues = issues
    ref.codes = codes
    ref.ok = not fatal

    if fatal:
        ref.label = "不合格"
    elif ref.rms_dbfs < config.WARN_RMS_DBFS:
        ref.label = "可用"
        if not issues:
            ref.issues = ["音量偏低，建议下次靠近一些"]
    else:
        ref.label = "良好"
    return ref


def overall_label(breathing: AudioRef, cough: AudioRef) -> str:
    labels = {breathing.label, cough.label}
    if "不合格" in labels:
        return "不合格"
    if "可用" in labels:
        return "可用"
    return "良好"


def load_and_check(path: Path, kind: str) -> AudioRef:
    return check_quality(decode_wav(path), kind)

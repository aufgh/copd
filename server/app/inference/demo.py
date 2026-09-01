"""虚拟推理引擎。

可重复性的做法：seed = sha256(user_id | 日期 | 场景)，
用它驱动一个独立的 Random 实例在场景区间内做确定性抖动。
效果是同一天同一用户重复检测结果完全一致，跨天有自然波动，
既不会"完全随机"导致演示前后矛盾，也不会假到每次数字一模一样。
"""

from __future__ import annotations

import hashlib
import random
from dataclasses import dataclass

from app.inference.base import InferenceEngine, InferenceRequest, InferenceResult

DEMO_VERSION = "demo-1.0.0"

SCENARIOS = ("stable", "attention", "alert")

SCENARIO_LABELS = {
    "stable": "状态平稳",
    "attention": "建议关注",
    "alert": "风险警示",
}


@dataclass(frozen=True, slots=True)
class ScenarioSpec:
    key: str
    risk_level: str
    rr: tuple[float, float]
    rhythm: tuple[float, float]
    abnormal: tuple[float, float]
    cough_count: tuple[int, int]
    cough_intensity: tuple[float, float]
    risk_score: tuple[int, int]
    confidence: tuple[float, float]
    delta_rr: tuple[float, float]
    delta_cough: tuple[float, float]


SPECS: dict[str, ScenarioSpec] = {
    "stable": ScenarioSpec(
        key="stable", risk_level="stable",
        rr=(16.0, 19.0), rhythm=(0.78, 0.93), abnormal=(0.04, 0.17),
        cough_count=(2, 3), cough_intensity=(0.18, 0.34),
        risk_score=(8, 22), confidence=(0.74, 0.88),
        delta_rr=(-1.2, 0.9), delta_cough=(-0.8, 0.6),
    ),
    "attention": ScenarioSpec(
        key="attention", risk_level="attention",
        rr=(20.0, 23.0), rhythm=(0.54, 0.71), abnormal=(0.27, 0.46),
        cough_count=(3, 5), cough_intensity=(0.44, 0.63),
        risk_score=(38, 56), confidence=(0.62, 0.78),
        delta_rr=(1.8, 3.6), delta_cough=(0.9, 2.1),
    ),
    "alert": ScenarioSpec(
        key="alert", risk_level="recheck",
        rr=(24.0, 28.0), rhythm=(0.30, 0.49), abnormal=(0.55, 0.76),
        cough_count=(5, 8), cough_intensity=(0.66, 0.88),
        risk_score=(68, 87), confidence=(0.58, 0.73),
        delta_rr=(4.0, 7.2), delta_cough=(2.4, 4.5),
    ),
}


def normalize_scenario(value: str | None) -> str:
    key = (value or "").strip().lower()
    return key if key in SPECS else "attention"


def stable_seed(*parts: str) -> int:
    """跨进程稳定的 seed。不能用内置 hash()，它每次启动都加盐。"""
    digest = hashlib.sha256("|".join(parts).encode("utf-8")).digest()
    return int.from_bytes(digest[:8], "big", signed=False)


class DemoInferenceEngine(InferenceEngine):
    name = "demo"
    version = DEMO_VERSION

    def __init__(self, default_scenario: str = "attention") -> None:
        self.default_scenario = normalize_scenario(default_scenario)

    @property
    def engine_kind(self) -> str:
        return "demo"

    def infer(self, request: InferenceRequest) -> InferenceResult:
        scenario = normalize_scenario(request.scenario or self.default_scenario)
        spec = SPECS[scenario]
        day = request.recorded_at.strftime("%Y-%m-%d")
        rng = random.Random(stable_seed(request.user_id, day, scenario))

        rr = round(rng.uniform(*spec.rr), 1)
        rhythm = round(rng.uniform(*spec.rhythm), 2)
        abnormal = round(rng.uniform(*spec.abnormal), 2)
        cough_count = rng.randint(*spec.cough_count)
        cough_intensity = round(rng.uniform(*spec.cough_intensity), 2)
        risk_score = rng.randint(*spec.risk_score)
        confidence = round(rng.uniform(*spec.confidence), 2)

        # 录音质量差会拉低可信度：这条逻辑真模型也需要，所以放在引擎里
        if not (request.breathing.ok and request.cough.ok):
            confidence = round(max(0.35, confidence - 0.18), 2)
        elif "可用" in {request.breathing.label, request.cough.label}:
            confidence = round(max(0.40, confidence - 0.07), 2)

        base = request.baseline
        if base.days_used > 0 and base.respiratory_rate is not None:
            delta_rr = round(rr - base.respiratory_rate, 1)
        else:
            delta_rr = round(rng.uniform(*spec.delta_rr), 1)
        if base.days_used > 0 and base.cough_count is not None:
            delta_cough = round(cough_count - base.cough_count, 1)
        else:
            delta_cough = round(rng.uniform(*spec.delta_cough), 1)
        if base.days_used > 0 and base.cough_intensity is not None:
            delta_intensity = round(cough_intensity - base.cough_intensity, 2)
        else:
            delta_intensity = round(rng.uniform(0.02, 0.14), 2)

        return InferenceResult(
            respiratory_rate=rr,
            rhythm_score=rhythm,
            abnormal_sound_prob=abnormal,
            cough_count=cough_count,
            cough_intensity=cough_intensity,
            risk_level=spec.risk_level,
            risk_score=risk_score,
            confidence=confidence,
            reasons=_reasons(spec, rr, delta_rr, delta_cough, request),
            recommendations=_recommendations(spec),
            delta_respiratory_rate=delta_rr,
            delta_cough_count=delta_cough,
            delta_cough_intensity=delta_intensity,
            model_version=DEMO_VERSION,
            scenario=scenario,
            inference_ms=None,
        )


def _reasons(
    spec: ScenarioSpec,
    rr: float,
    delta_rr: float,
    delta_cough: float,
    request: InferenceRequest,
) -> list[str]:
    days = request.baseline.days_used
    ref = f"较近 {days} 日均值" if days > 0 else "较个人基线"
    out: list[str] = []

    if spec.key == "stable":
        out.append(f"呼吸频率 {rr:.0f} 次每分钟，处于个人常态区间")
        out.append("呼吸节律规整，未见明显异常呼吸声特征")
        out.append(f"咳嗽声学指标{ref}变化不明显")
    elif spec.key == "attention":
        out.append(f"呼吸频率 {rr:.0f} 次每分钟，{ref}偏高约 {abs(delta_rr):.1f} 次")
        out.append(f"咳嗽声学指标{ref}上升约 {abs(delta_cough):.1f}")
        out.append("呼吸节律规整度下降，建议继续观察")
    else:
        out.append(f"呼吸频率 {rr:.0f} 次每分钟，{ref}明显偏高")
        out.append(f"咳嗽次数与强度连续上升，{ref}增加约 {abs(delta_cough):.1f}")
        out.append("呼吸节律规整度偏低，多项指标同时偏离个人基线")

    if request.spo2 is not None and request.spo2 < 93:
        out.append(f"本次填写的血氧 {request.spo2}% 低于日常参考范围")
    if request.symptom_score is not None and request.symptom_score >= 3:
        out.append(f"自评症状评分 {request.symptom_score}，主观感受较前次加重")
    if not (request.breathing.ok and request.cough.ok):
        out.append("本次录音质量不理想，结果可信度已相应下调")
    return out


def _recommendations(spec: ScenarioSpec) -> list[str]:
    if spec.key == "stable":
        return [
            "保持当前用药与日常活动节奏",
            "建议每天固定时间检测一次，便于形成稳定的个人基线",
        ]
    if spec.key == "attention":
        return [
            "请在安静环境下再次检测一次，确认是否为偶发波动",
            "关注气促、痰量和血氧的变化，并记录下来",
            "如两天内趋势继续上升，建议联系随访医生沟通",
        ]
    return [
        "建议尽快联系随访医生说明近期趋势变化",
        "如出现明显气促加重、痰量或痰色改变、血氧下降，请及时就医",
        "在安静环境下复测一次，并保留本次记录供医生参考",
    ]


def available_scenarios() -> list[dict]:
    return [{"key": key, "label": SCENARIO_LABELS[key]} for key in SCENARIOS]

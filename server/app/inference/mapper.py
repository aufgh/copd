"""InferenceResult -> API 响应字段的映射层。

这一层的存在是为了让推理输出和对外契约解耦：
真模型换掉后 InferenceResult 里的字段可以变，只改这个文件即可。
"""

from __future__ import annotations

from app.inference.base import InferenceResult
from app.schemas.assessment import RISK_LABELS
from app.services.audio import AudioRef, overall_label


def rhythm_label(score: float) -> str:
    if score >= 0.75:
        return "规整"
    if score >= 0.50:
        return "轻度不规整"
    return "明显不规整"


def cough_trend_label(delta: float | None) -> str:
    if delta is None:
        return "暂无可比数据"
    if delta <= -0.5:
        return "较基线减少"
    if delta < 0.5:
        return "与基线基本持平"
    if delta < 2.0:
        return "较基线上升"
    return "较基线明显上升"


def quality_payload(breathing: AudioRef, cough: AudioRef) -> dict:
    return {
        "breathing": breathing.as_quality_dict(),
        "cough": cough.as_quality_dict(),
        "overall_label": overall_label(breathing, cough),
    }


def metrics_payload(result: InferenceResult, baseline_days: int) -> dict:
    return {
        "respiratory_rate": result.respiratory_rate,
        "rhythm_score": result.rhythm_score,
        "rhythm_label": rhythm_label(result.rhythm_score),
        "abnormal_sound_prob": result.abnormal_sound_prob,
        "cough_count": result.cough_count,
        "cough_intensity": result.cough_intensity,
        "cough_trend_label": cough_trend_label(result.delta_cough_count),
        "baseline": {
            "days_used": baseline_days,
            "respiratory_rate": result.delta_respiratory_rate,
            "cough_count": result.delta_cough_count,
            "cough_intensity": result.delta_cough_intensity,
        },
    }


def risk_payload(result: InferenceResult) -> dict:
    return {
        "level": result.risk_level,
        "level_label": RISK_LABELS.get(result.risk_level, "建议关注"),
        "score": result.risk_score,
        "confidence": result.confidence,
        "reasons": list(result.reasons),
        "recommendations": list(result.recommendations),
    }

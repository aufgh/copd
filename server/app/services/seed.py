"""预置最近七天的演示数据。

轨迹是手工设计的（前四天平稳、后三天缓慢上行），不是随机生成，
这样历史趋势页的曲线有明确的"故事"，演示时讲得通；
当天做真实检测后，live 记录会覆盖同一天的 seed 记录（history 取当日最新一条）。
"""

from __future__ import annotations

import hashlib
import random
from datetime import datetime, timedelta

from app import config
from app.inference.demo import DEMO_VERSION, stable_seed
from app.inference.mapper import cough_trend_label, rhythm_label
from app.schemas.assessment import RISK_LABELS
from app.services import store

# 七天轨迹：索引 0 是最早的一天，索引 6 是今天
_TRAJECTORY = [
    # rr,   rhythm, abnormal, cough, intensity, score, level,       quality
    (16.8, 0.88, 0.09, 2, 0.22, 13, "stable", "良好"),
    (17.2, 0.85, 0.11, 2, 0.24, 15, "stable", "良好"),
    (17.0, 0.86, 0.10, 3, 0.26, 14, "stable", "可用"),
    (18.4, 0.79, 0.16, 3, 0.31, 22, "stable", "良好"),
    (20.1, 0.68, 0.29, 4, 0.47, 35, "attention", "良好"),
    (20.8, 0.63, 0.34, 4, 0.53, 41, "attention", "良好"),
    (21.4, 0.60, 0.38, 5, 0.58, 47, "attention", "良好"),
]

_REASONS = {
    "stable": [
        "呼吸频率处于个人常态区间",
        "呼吸节律规整，未见明显异常呼吸声特征",
    ],
    "attention": [
        "呼吸频率较近七日均值偏高",
        "咳嗽声学指标较近七日均值上升",
    ],
    "recheck": [
        "呼吸频率明显偏高",
        "多项指标同时偏离个人基线",
    ],
}

_RECOMMENDATIONS = {
    "stable": ["保持当前用药与日常活动节奏"],
    "attention": ["建议在安静环境下再次检测，并关注气促、痰量和血氧变化"],
    "recheck": ["建议尽快联系随访医生说明近期趋势变化"],
}


def _seed_id(user_id: str, date_str: str) -> str:
    digest = hashlib.sha256(f"seed|{user_id}|{date_str}".encode()).hexdigest()
    return f"seed-{digest[:20]}"


def seed_history(user_id: str, *, days: int = config.SEED_DAYS, force: bool = False) -> int:
    """写入 days 天演示数据，返回写入条数。已存在则跳过（force=True 覆盖）。"""
    if not force and store.seed_exists(user_id):
        return 0

    today = datetime.now().replace(hour=9, minute=30, second=0, microsecond=0)
    written = 0
    trajectory = _TRAJECTORY[-days:]

    for offset, point in enumerate(trajectory):
        day = today - timedelta(days=len(trajectory) - 1 - offset)
        date_str = day.strftime("%Y-%m-%d")
        rr, rhythm, abnormal, cough, intensity, score, level, quality_label = point

        rng = random.Random(stable_seed("seed", user_id, date_str))
        rr = round(rr + rng.uniform(-0.25, 0.25), 1)
        intensity = round(min(0.95, max(0.05, intensity + rng.uniform(-0.02, 0.02))), 2)
        confidence = round(rng.uniform(0.66, 0.84), 2)

        prev = trajectory[offset - 1] if offset > 0 else point
        delta_rr = round(rr - prev[0], 1)
        delta_cough = float(cough - prev[3])

        quality = {
            "breathing": _quality_item(quality_label, config.BREATHING_TARGET_MS, rng),
            "cough": _quality_item(quality_label, 7200, rng),
            "overall_label": quality_label,
        }
        metrics = {
            "respiratory_rate": rr,
            "rhythm_score": rhythm,
            "rhythm_label": rhythm_label(rhythm),
            "abnormal_sound_prob": abnormal,
            "cough_count": cough,
            "cough_intensity": intensity,
            "cough_trend_label": cough_trend_label(delta_cough),
            "baseline": {
                "days_used": offset,
                "respiratory_rate": delta_rr if offset else None,
                "cough_count": delta_cough if offset else None,
                "cough_intensity": None,
            },
        }
        risk = {
            "level": level,
            "level_label": RISK_LABELS[level],
            "score": score,
            "confidence": confidence,
            "reasons": list(_REASONS[level]),
            "recommendations": list(_RECOMMENDATIONS[level]),
        }

        store.insert_seed_record(
            {
                "id": _seed_id(user_id, date_str),
                "user_id": user_id,
                "recorded_at": day.isoformat(timespec="seconds"),
                "local_date": date_str,
                "created_at": day.isoformat(timespec="seconds"),
                "engine": "demo",
                "model_version": DEMO_VERSION,
                "scenario": "seed",
                "inference_ms": rng.randint(2100, 3600),
                "risk_level": level,
                "risk_score": score,
                "respiratory_rate": rr,
                "cough_count": cough,
                "cough_intensity": intensity,
                "quality_label": quality_label,
                "quality_json": _json(quality),
                "metrics_json": _json(metrics),
                "risk_json": _json(risk),
            }
        )
        written += 1
    return written


def _quality_item(label: str, target_ms: int, rng: random.Random) -> dict:
    rms = rng.uniform(-36.0, -28.0) if label == "良好" else rng.uniform(-43.0, -38.5)
    return {
        "ok": True,
        "label": label,
        "sample_rate": config.AUDIO_SAMPLE_RATE,
        "channels": 1,
        "duration_ms": target_ms + rng.randint(-400, 600),
        "rms_dbfs": round(rms, 1),
        "peak_dbfs": round(rms + rng.uniform(9.0, 15.0), 1),
        "clip_ratio": 0.0,
        "issues": [] if label == "良好" else ["音量偏低，建议下次靠近一些"],
    }


def _json(value: dict) -> str:
    import json

    return json.dumps(value, ensure_ascii=False)


def ensure_seeded(user_id: str) -> int:
    """惰性 seed：任何用户第一次出现时自动补齐七天演示数据。

    这样 App 里换一个匿名编号也不会看到空白的历史页，
    不依赖启动时必须知道用户编号。
    """
    if not config.SEED_DEMO_HISTORY or not user_id:
        return 0
    return seed_history(user_id)

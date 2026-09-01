"""健康检查 / 能力声明。

不只返回 ok：把契约版本、引擎类型、录音规格和质检阈值一起下发，
Android 的"连接测试"顺便完成版本兼容检查和阈值同步。
"""

from __future__ import annotations

from datetime import datetime

from fastapi import APIRouter

from app import config
from app.inference import get_engine
from app.inference.demo import available_scenarios
from app.schemas.assessment import HealthResponse

router = APIRouter(tags=["health"])


@router.get("/health", response_model=HealthResponse)
def health() -> dict:
    engine = get_engine()
    is_demo = engine.engine_kind == "demo"
    return {
        "status": "ok",
        "schema_version": config.SCHEMA_VERSION,
        "supported_schema_versions": config.SUPPORTED_SCHEMA_VERSIONS,
        "engine": engine.engine_kind,
        "model_version": engine.version,
        # 真引擎下这两项为空，客户端据此隐藏设置页的演示场景选择器
        "demo_scenario": config.DEMO_SCENARIO if is_demo else None,
        "available_scenarios": available_scenarios() if is_demo else [],
        "server_time": datetime.now(),
        "audio_requirements": config.audio_requirements(),
    }

"""推理引擎工厂。切换引擎只改环境变量 COPD_ENGINE。"""

from __future__ import annotations

from functools import lru_cache

from app import config
from app.inference.base import InferenceEngine


@lru_cache(maxsize=1)
def get_engine() -> InferenceEngine:
    if config.ENGINE == "real":
        from app.inference.real import RealInferenceEngine

        engine: InferenceEngine = RealInferenceEngine()
    else:
        from app.inference.demo import DemoInferenceEngine

        engine = DemoInferenceEngine(default_scenario=config.DEMO_SCENARIO)
    engine.warmup()
    return engine


__all__ = ["get_engine"]

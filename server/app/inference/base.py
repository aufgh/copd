"""推理层的统一契约。

设计要点（换真模型时不改上层的关键）：
1. 输入是 InferenceRequest 对象，不是文件路径。真模型需要采样率、
   个人基线、历史序列时直接往这个对象里加字段即可。
2. 输出 InferenceResult 是推理层自己的模型，不等于 API 响应体。
   两者之间由 inference/mapper.py 做映射，真模型输出变化不会击穿 API 契约。
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from datetime import datetime

from app.services.audio import AudioRef


@dataclass(slots=True)
class BaselineSummary:
    """个人基线：由服务端从历史记录算出后喂给引擎。"""

    days_used: int = 0
    respiratory_rate: float | None = None
    cough_count: float | None = None
    cough_intensity: float | None = None
    risk_score: float | None = None


@dataclass(slots=True)
class InferenceRequest:
    user_id: str
    recorded_at: datetime
    breathing: AudioRef
    cough: AudioRef
    baseline: BaselineSummary
    scenario: str | None = None          # 仅 demo 引擎使用
    symptom_score: int | None = None
    spo2: int | None = None


@dataclass(slots=True)
class InferenceResult:
    respiratory_rate: float
    rhythm_score: float
    abnormal_sound_prob: float
    cough_count: int
    cough_intensity: float

    risk_level: str                      # stable | attention | recheck
    risk_score: int
    confidence: float
    reasons: list[str] = field(default_factory=list)
    recommendations: list[str] = field(default_factory=list)

    # 相对基线的变化，由引擎给出（真模型可以用自己的算法）
    delta_respiratory_rate: float | None = None
    delta_cough_count: float | None = None
    delta_cough_intensity: float | None = None

    model_version: str = "unknown"
    scenario: str | None = None
    inference_ms: int | None = None


class InferenceEngine(ABC):
    """所有推理实现的父类。"""

    name: str = "base"
    version: str = "0.0"

    @property
    @abstractmethod
    def engine_kind(self) -> str:
        """demo 或 real。客户端据此决定是否显示演示相关的 UI。"""

    @abstractmethod
    def infer(self, request: InferenceRequest) -> InferenceResult:
        """同步执行推理。耗时由调用方在后台任务里承担。"""

    def warmup(self) -> None:
        """真模型可以在这里加载权重；demo 引擎不需要。"""
        return None

"""API 响应数据结构（schema v1.0）。

分组设计的目的：真模型接入后只会往 metrics / risk 里加字段，
顶层结构和客户端解析代码不用动。
"""

from __future__ import annotations

from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field

RiskLevel = Literal["stable", "attention", "recheck"]
Status = Literal["queued", "running", "succeeded", "failed"]
Stage = Literal["queued", "quality_check", "feature_extraction", "inference", "done", "failed"]

RISK_LABELS: dict[str, str] = {
    "stable": "状态平稳",
    "attention": "建议关注",
    "recheck": "建议复测",
}

STAGE_LABELS: dict[str, str] = {
    "queued": "已接收，排队中",
    "quality_check": "检查录音质量",
    "feature_extraction": "提取声学特征",
    "inference": "生成趋势结果",
    "done": "分析完成",
    "failed": "分析未完成",
}


class ErrorBody(BaseModel):
    code: str
    message: str
    hint: str = ""
    field: str | None = None
    needs_rerecord: bool = False


class QualityItem(BaseModel):
    ok: bool
    label: str                      # 良好 / 可用 / 不合格
    sample_rate: int
    channels: int
    duration_ms: int
    rms_dbfs: float
    peak_dbfs: float
    clip_ratio: float
    issues: list[str] = Field(default_factory=list)


class AudioQuality(BaseModel):
    breathing: QualityItem
    cough: QualityItem
    overall_label: str              # 良好 / 可用 / 不合格


class BaselineDelta(BaseModel):
    """相对个人基线的变化。days_used=0 表示还没有基线可比。"""

    days_used: int
    respiratory_rate: float | None = None
    cough_count: float | None = None
    cough_intensity: float | None = None


class Metrics(BaseModel):
    respiratory_rate: float
    rhythm_score: float
    rhythm_label: str
    abnormal_sound_prob: float
    cough_count: int
    cough_intensity: float
    cough_trend_label: str
    baseline: BaselineDelta


class Risk(BaseModel):
    level: RiskLevel
    level_label: str
    score: int                      # 0-100，越高越需要关注
    confidence: float               # 0-1
    reasons: list[str] = Field(default_factory=list)
    recommendations: list[str] = Field(default_factory=list)


class ModelInfo(BaseModel):
    engine: Literal["demo", "real"]
    version: str
    scenario: str | None = None     # 仅 demo 引擎有值
    inference_ms: int | None = None


class AssessmentResponse(BaseModel):
    schema_version: str
    assessment_id: str
    client_assessment_id: str | None = None
    user_id: str
    status: Status
    stage: Stage
    stage_label: str
    progress: int                   # 0-100，服务端侧进度
    created_at: datetime
    completed_at: datetime | None = None
    model: ModelInfo
    audio_quality: AudioQuality | None = None
    metrics: Metrics | None = None
    risk: Risk | None = None
    error: ErrorBody | None = None


class CreateAssessmentResponse(BaseModel):
    schema_version: str
    assessment_id: str
    client_assessment_id: str | None = None
    status: Status
    stage: Stage
    stage_label: str
    progress: int
    poll_after_ms: int = 600        # 建议客户端多久轮询一次
    deduplicated: bool = False      # true 表示命中幂等，没有新建任务


class HistoryItem(BaseModel):
    date: str                       # YYYY-MM-DD
    assessment_id: str
    risk_level: RiskLevel
    risk_level_label: str
    risk_score: int
    respiratory_rate: float
    cough_count: int
    quality_label: str
    source: Literal["seed", "live"]  # seed=预置演示数据，live=当天真实录音


class HistoryResponse(BaseModel):
    schema_version: str
    user_id: str
    days: int
    items: list[HistoryItem]


class HealthResponse(BaseModel):
    status: Literal["ok"]
    schema_version: str
    supported_schema_versions: list[str]
    engine: Literal["demo", "real"]
    model_version: str
    demo_scenario: str | None = None
    available_scenarios: list[dict] = Field(default_factory=list)
    server_time: datetime
    audio_requirements: dict

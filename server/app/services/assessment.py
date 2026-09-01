"""检测任务的状态机与结果组装。

阶段划分（progress 只表示服务端侧进度，客户端上传进度由客户端自己算）：
    queued 0 -> quality_check 15 -> feature_extraction 45 -> inference 75 -> done 100
Android 只渲染服务端返回的 stage / stage_label，不在客户端硬编码阶段列表；
真模型多加一个阶段时客户端零改动。
"""

from __future__ import annotations

import random
import time
import uuid
from datetime import datetime
from pathlib import Path

from app import config
from app.errors import ErrorCode, error_body
from app.inference import get_engine
from app.inference.base import BaselineSummary, InferenceRequest
from app.inference.mapper import metrics_payload, quality_payload, risk_payload
from app.schemas.assessment import STAGE_LABELS
from app.services import store
from app.services.audio import AudioDecodeError, check_quality, decode_wav


def new_assessment_id() -> str:
    return uuid.uuid4().hex


def save_upload(assessment_id: str, kind: str, data: bytes) -> Path:
    """文件名只用随机检测编号，不含任何用户可识别信息。"""
    config.ensure_dirs()
    path = config.UPLOAD_DIR / f"{assessment_id}_{kind}.wav"
    path.write_bytes(data)
    return path


def prune_uploads() -> None:
    paths = store.oldest_upload_paths(config.KEEP_UPLOAD_FILES)
    removed: list[str] = []
    for raw in paths:
        try:
            Path(raw).unlink(missing_ok=True)
            removed.append(raw)
        except OSError:
            continue
    store.clear_upload_paths(removed)


def create(
    *,
    user_id: str,
    client_assessment_id: str | None,
    recorded_at: datetime,
    scenario: str | None,
    breathing_path: Path,
    cough_path: Path,
    assessment_id: str | None = None,
) -> str:
    """登记一条 queued 记录。文件必须已经落盘（save_upload 的返回值）。"""
    engine = get_engine()
    assessment_id = assessment_id or new_assessment_id()
    store.insert_queued(
        assessment_id=assessment_id,
        client_assessment_id=client_assessment_id,
        user_id=user_id,
        recorded_at=recorded_at,
        engine=engine.engine_kind,
        model_version=engine.version,
        scenario=scenario,
        breathing_path=str(breathing_path),
        cough_path=str(cough_path),
    )
    return assessment_id


def get_engine_kind() -> str:
    return get_engine().engine_kind


def get_engine_version() -> str:
    return get_engine().version


def _stage_budget(assessment_id: str) -> tuple[float, float, float]:
    """把 2-4 秒总耗时切成三段。真引擎不走这条路径。"""
    rng = random.Random(int(assessment_id[:8], 16))
    total = rng.uniform(config.DEMO_MIN_SECONDS, config.DEMO_MAX_SECONDS)
    return total * 0.25, total * 0.40, total * 0.35


def run_pipeline(
    assessment_id: str,
    *,
    symptom_score: int | None = None,
    spo2: int | None = None,
) -> None:
    """后台任务入口。耗时是真的发生在服务端，不是客户端假装等待。"""
    row = store.get(assessment_id)
    if row is None:
        return

    engine = get_engine()
    is_demo = engine.engine_kind == "demo"
    t_quality, t_features, t_inference = _stage_budget(assessment_id)
    started = time.perf_counter()

    try:
        store.set_stage(assessment_id, status="running", stage="quality_check", progress=15)
        # 分开解码，这样解码失败时能准确告诉客户端是哪一段录音有问题
        try:
            breathing = check_quality(decode_wav(Path(row["breathing_path"])), "breathing")
        except AudioDecodeError as exc:
            exc.field = "breathing_file"
            raise
        try:
            cough = check_quality(decode_wav(Path(row["cough_path"])), "cough")
        except AudioDecodeError as exc:
            exc.field = "cough_file"
            raise
        if is_demo:
            time.sleep(t_quality)

        quality = quality_payload(breathing, cough)

        if not breathing.ok or not cough.ok:
            bad = breathing if not breathing.ok else cough
            field = "breathing_file" if not breathing.ok else "cough_file"
            code = ErrorCode(bad.codes[0]) if bad.codes else ErrorCode.AUDIO_EMPTY
            message = "；".join(bad.issues) or "录音质量不合格"
            store.mark_failed(assessment_id, error_body(code, message, field), quality=quality)
            return

        store.set_stage(
            assessment_id, status="running", stage="feature_extraction", progress=45
        )
        if is_demo:
            time.sleep(t_features)

        store.set_stage(assessment_id, status="running", stage="inference", progress=75)
        baseline_raw = store.baseline(
            row["user_id"], before_date=row["local_date"], days=config.SEED_DAYS
        )
        baseline = BaselineSummary(**baseline_raw)

        result = engine.infer(
            InferenceRequest(
                user_id=row["user_id"],
                recorded_at=datetime.fromisoformat(row["recorded_at"]),
                breathing=breathing,
                cough=cough,
                baseline=baseline,
                scenario=row["scenario"],
                symptom_score=symptom_score,
                spo2=spo2,
            )
        )
        if is_demo:
            time.sleep(t_inference)

        elapsed_ms = int((time.perf_counter() - started) * 1000)
        store.mark_succeeded(
            assessment_id,
            quality=quality,
            metrics=metrics_payload(result, baseline.days_used),
            risk=risk_payload(result),
            model_version=result.model_version or engine.version,
            scenario=result.scenario,
            inference_ms=result.inference_ms or elapsed_ms,
        )
        prune_uploads()

    except AudioDecodeError as exc:
        store.mark_failed(
            assessment_id,
            error_body(ErrorCode(exc.code), exc.message, getattr(exc, "field", None)),
        )
    except NotImplementedError as exc:
        store.mark_failed(
            assessment_id, error_body(ErrorCode.ENGINE_FAILED, str(exc))
        )
    except Exception as exc:  # noqa: BLE001 - 兜底，保证任务不会卡在 running
        store.mark_failed(
            assessment_id,
            error_body(ErrorCode.INTERNAL_ERROR, f"分析过程出现异常：{exc}"),
        )


def build_response(row: dict) -> dict:
    """行记录 -> AssessmentResponse 字典。"""
    return {
        "schema_version": config.SCHEMA_VERSION,
        "assessment_id": row["id"],
        "client_assessment_id": row["client_assessment_id"],
        "user_id": row["user_id"],
        "status": row["status"],
        "stage": row["stage"],
        "stage_label": STAGE_LABELS.get(row["stage"], "处理中"),
        "progress": row["progress"],
        "created_at": row["created_at"],
        "completed_at": row["completed_at"],
        "model": {
            "engine": row["engine"],
            "version": row["model_version"],
            "scenario": row["scenario"],
            "inference_ms": row["inference_ms"],
        },
        "audio_quality": row["quality"],
        "metrics": row["metrics"],
        "risk": row["risk"],
        "error": row["error"],
    }


def build_create_response(row: dict, *, deduplicated: bool) -> dict:
    return {
        "schema_version": config.SCHEMA_VERSION,
        "assessment_id": row["id"],
        "client_assessment_id": row["client_assessment_id"],
        "status": row["status"],
        "stage": row["stage"],
        "stage_label": STAGE_LABELS.get(row["stage"], "处理中"),
        "progress": row["progress"],
        "poll_after_ms": 600,
        "deduplicated": deduplicated,
    }


def build_history(user_id: str, days: int) -> dict:
    items = []
    for row in store.history(user_id, days):
        risk = row["risk"] or {}
        items.append(
            {
                "date": row["local_date"],
                "assessment_id": row["id"],
                "risk_level": row["risk_level"] or "stable",
                "risk_level_label": risk.get("level_label", ""),
                "risk_score": row["risk_score"] or 0,
                "respiratory_rate": row["respiratory_rate"] or 0.0,
                "cough_count": row["cough_count"] or 0,
                "quality_label": row["quality_label"] or "",
                "source": row["source"],
            }
        )
    return {
        "schema_version": config.SCHEMA_VERSION,
        "user_id": user_id,
        "days": days,
        "items": items,
    }


__all__ = [
    "build_create_response",
    "build_history",
    "build_response",
    "create",
    "get_engine_kind",
    "get_engine_version",
    "new_assessment_id",
    "prune_uploads",
    "run_pipeline",
    "save_upload",
]

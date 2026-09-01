"""检测的创建与查询。"""

from __future__ import annotations

from datetime import datetime

from fastapi import APIRouter, BackgroundTasks, File, Form, Header, Query, UploadFile

from app import config
from app.errors import ApiError, ErrorCode
from app.schemas.assessment import (
    AssessmentResponse,
    CreateAssessmentResponse,
    HistoryResponse,
)
from app.services import assessment as svc
from app.services import seed, store

router = APIRouter(tags=["assessments"])

_MAX_BYTES = config.MAX_UPLOAD_MB * 1024 * 1024


def _parse_recorded_at(value: str | None) -> datetime:
    """手机传来的是带时区偏移的 ISO 时间，先换算到服务器本地时区再去掉时区。

    直接 replace(tzinfo=None) 会把手机的偏移当成本地时间，手机和电脑不在同一时区时
    local_date 会算成前一天或后一天，当天的记录就永远盖不掉演示种子记录。
    """
    if not value:
        return datetime.now()
    try:
        text = value.strip().replace("Z", "+00:00")
        parsed = datetime.fromisoformat(text)
        if parsed.tzinfo is None:
            return parsed
        return parsed.astimezone().replace(tzinfo=None)
    except ValueError:
        return datetime.now()


async def _read_upload(upload: UploadFile | None, field: str) -> bytes:
    if upload is None:
        raise ApiError(400, ErrorCode.AUDIO_MISSING, f"缺少音频文件 {field}", field)
    data = await upload.read()
    if not data:
        raise ApiError(400, ErrorCode.AUDIO_EMPTY, f"{field} 是空文件", field)
    if len(data) > _MAX_BYTES:
        raise ApiError(
            413,
            ErrorCode.FILE_TOO_LARGE,
            f"{field} 超过 {config.MAX_UPLOAD_MB} MB 上限",
            field,
        )
    if data[:4] != b"RIFF" or data[8:12] != b"WAVE":
        raise ApiError(
            415, ErrorCode.UNSUPPORTED_FORMAT, f"{field} 不是 WAV 文件", field
        )
    return data


@router.post(
    "/assessments",
    response_model=CreateAssessmentResponse,
    status_code=202,
)
async def create_assessment(
    background: BackgroundTasks,
    user_id: str = Form(..., min_length=1, max_length=64),
    breathing_file: UploadFile = File(...),
    cough_file: UploadFile = File(...),
    recorded_at: str | None = Form(None),
    client_assessment_id: str | None = Form(None),
    symptom_score: int | None = Form(None),
    spo2: int | None = Form(None),
    demo_scenario: str | None = Form(None),
    x_demo_scenario: str | None = Header(None),
) -> dict:
    # 幂等：弱网重试不会产生第二条记录和第二份音频
    if client_assessment_id:
        existing = store.find_by_client_id(user_id, client_assessment_id)
        if existing is not None:
            return svc.build_create_response(existing, deduplicated=True)

    breathing_bytes = await _read_upload(breathing_file, "breathing_file")
    cough_bytes = await _read_upload(cough_file, "cough_file")

    scenario = x_demo_scenario or demo_scenario or config.DEMO_SCENARIO
    assessment_id = svc.new_assessment_id()
    breathing_path = svc.save_upload(assessment_id, "breathing", breathing_bytes)
    cough_path = svc.save_upload(assessment_id, "cough", cough_bytes)

    # 新用户第一次上传时补齐七天演示历史，保证基线和趋势页都有数据
    seed.ensure_seeded(user_id)

    svc.create(
        user_id=user_id,
        client_assessment_id=client_assessment_id,
        recorded_at=_parse_recorded_at(recorded_at),
        scenario=scenario,
        breathing_path=breathing_path,
        cough_path=cough_path,
        assessment_id=assessment_id,
    )

    background.add_task(
        svc.run_pipeline, assessment_id, symptom_score=symptom_score, spo2=spo2
    )

    row = store.get(assessment_id)
    assert row is not None
    return svc.build_create_response(row, deduplicated=False)


@router.get("/assessments", response_model=HistoryResponse)
def list_assessments(
    user_id: str = Query(..., min_length=1, max_length=64),
    days: int = Query(config.SEED_DAYS, ge=1, le=90),
) -> dict:
    seed.ensure_seeded(user_id)
    return svc.build_history(user_id, days)


@router.get("/assessments/{assessment_id}", response_model=AssessmentResponse)
def get_assessment(assessment_id: str) -> dict:
    row = store.get(assessment_id)
    if row is None:
        raise ApiError(404, ErrorCode.NOT_FOUND, f"检测记录 {assessment_id} 不存在")
    return svc.build_response(row)

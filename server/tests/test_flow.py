"""服务端全链路测试。

运行：
    cd server
    python -m pytest -q
（pytest 不是运行演示的必需依赖，只在开发时用：pip install pytest）
"""

from __future__ import annotations

import io
import math
import struct
import time
import wave

import pytest
from fastapi.testclient import TestClient

from app import config
from app.main import app
from app.services import store

USER = "test-user-001"


def make_wav(
    seconds: float,
    *,
    sample_rate: int = 16_000,
    amplitude: float = 0.25,
    channels: int = 1,
    freq: float = 220.0,
) -> bytes:
    n = int(seconds * sample_rate)
    frames = bytearray()
    for i in range(n):
        # 加一点包络，让波形不是纯正弦，RMS 更接近真实录音
        env = 0.6 + 0.4 * math.sin(2 * math.pi * 0.35 * i / sample_rate)
        value = int(amplitude * env * math.sin(2 * math.pi * freq * i / sample_rate) * 32767)
        value = max(-32768, min(32767, value))
        for _ in range(channels):
            frames += struct.pack("<h", value)

    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as wf:
        wf.setnchannels(channels)
        wf.setsampwidth(2)
        wf.setframerate(sample_rate)
        wf.writeframes(bytes(frames))
    return buffer.getvalue()


@pytest.fixture(autouse=True)
def fast_demo(monkeypatch):
    """把虚拟耗时压到很短，测试不用真等 2-4 秒。"""
    monkeypatch.setattr(config, "DEMO_MIN_SECONDS", 0.01)
    monkeypatch.setattr(config, "DEMO_MAX_SECONDS", 0.05)


@pytest.fixture
def client():
    store.init_db()
    store.reset_all()
    with TestClient(app) as c:
        yield c


def upload(client, *, user_id=USER, breathing=None, cough=None, **extra):
    files = {
        "breathing_file": ("b.wav", breathing or make_wav(20.0), "audio/wav"),
        "cough_file": ("c.wav", cough or make_wav(7.0), "audio/wav"),
    }
    data = {"user_id": user_id, **{k: str(v) for k, v in extra.items() if v is not None}}
    return client.post("/api/v1/assessments", data=data, files=files)


def wait_done(client, assessment_id, timeout=15.0):
    deadline = time.time() + timeout
    last = None
    while time.time() < deadline:
        last = client.get(f"/api/v1/assessments/{assessment_id}").json()
        if last["status"] in ("succeeded", "failed"):
            return last
        time.sleep(0.05)
    raise AssertionError(f"任务未在 {timeout} 秒内结束，最后状态：{last}")


# ---------------------------------------------------------------- health


def test_health_declares_capabilities(client):
    body = client.get("/api/v1/health").json()
    assert body["status"] == "ok"
    assert body["engine"] == "demo"
    assert body["schema_version"] in body["supported_schema_versions"]
    req = body["audio_requirements"]
    assert req["sample_rate"] == 16_000
    assert req["channels"] == 1
    assert {s["key"] for s in body["available_scenarios"]} == {
        "stable", "attention", "alert"
    }


# ---------------------------------------------------------------- 正常闭环


def test_full_flow_succeeds(client):
    created = upload(client, demo_scenario="attention")
    assert created.status_code == 202, created.text
    payload = created.json()
    assert payload["status"] == "queued"
    assert payload["deduplicated"] is False

    result = wait_done(client, payload["assessment_id"])
    assert result["status"] == "succeeded"
    assert result["stage"] == "done"
    assert result["progress"] == 100
    assert result["model"]["engine"] == "demo"
    assert result["model"]["scenario"] == "attention"

    metrics = result["metrics"]
    assert 20.0 <= metrics["respiratory_rate"] <= 23.0
    assert metrics["cough_count"] >= 1
    assert metrics["rhythm_label"]
    assert metrics["baseline"]["days_used"] > 0      # seed 历史提供了基线

    risk = result["risk"]
    assert risk["level"] == "attention"
    assert risk["level_label"] == "建议关注"
    assert risk["reasons"] and risk["recommendations"]
    assert 0.0 < risk["confidence"] <= 1.0

    quality = result["audio_quality"]
    assert quality["breathing"]["ok"] is True
    assert quality["cough"]["ok"] is True
    assert result["error"] is None


def test_no_diagnosis_wording(client):
    """合规：任何文案都不能出现确诊或急性加重的断言。"""
    forbidden = ["确诊", "已经发生急性加重", "诊断为"]
    result = wait_done(client, upload(client).json()["assessment_id"])
    text = str(result)
    for word in forbidden:
        assert word not in text, f"结果文案里出现了禁用词：{word}"


# ---------------------------------------------------------------- 三套场景


@pytest.mark.parametrize(
    "scenario,level,rr_range",
    [
        ("stable", "stable", (16.0, 19.0)),
        ("attention", "attention", (20.0, 23.0)),
        ("alert", "recheck", (24.0, 28.0)),
    ],
)
def test_scenarios(client, scenario, level, rr_range):
    result = wait_done(
        client,
        upload(client, user_id=f"u-{scenario}", demo_scenario=scenario)
        .json()["assessment_id"],
    )
    assert result["risk"]["level"] == level
    lo, hi = rr_range
    assert lo <= result["metrics"]["respiratory_rate"] <= hi


def test_scenario_header_overrides_form(client):
    files = {
        "breathing_file": ("b.wav", make_wav(20.0), "audio/wav"),
        "cough_file": ("c.wav", make_wav(7.0), "audio/wav"),
    }
    created = client.post(
        "/api/v1/assessments",
        data={"user_id": USER, "demo_scenario": "stable"},
        files=files,
        headers={"X-Demo-Scenario": "alert"},
    )
    result = wait_done(client, created.json()["assessment_id"])
    assert result["model"]["scenario"] == "alert"


def test_same_day_same_user_is_repeatable(client):
    """同一天同一用户同一场景，两次检测结果必须一致。"""
    first = wait_done(client, upload(client, demo_scenario="alert").json()["assessment_id"])
    second = wait_done(client, upload(client, demo_scenario="alert").json()["assessment_id"])
    assert first["metrics"]["respiratory_rate"] == second["metrics"]["respiratory_rate"]
    assert first["risk"]["score"] == second["risk"]["score"]
    assert first["metrics"]["cough_count"] == second["metrics"]["cough_count"]


# ---------------------------------------------------------------- 质检与错误


def test_too_short_breathing_fails_with_rerecord_flag(client):
    result = wait_done(
        client, upload(client, breathing=make_wav(3.0)).json()["assessment_id"]
    )
    assert result["status"] == "failed"
    assert result["error"]["code"] == "AUDIO_TOO_SHORT"
    assert result["error"]["needs_rerecord"] is True
    assert result["error"]["field"] == "breathing_file"
    # 质检结果仍然返回，UI 可以告诉用户具体哪一段不合格
    assert result["audio_quality"]["breathing"]["ok"] is False


def test_too_quiet_fails(client):
    result = wait_done(
        client,
        upload(client, breathing=make_wav(20.0, amplitude=0.0005))
        .json()["assessment_id"],
    )
    assert result["status"] == "failed"
    assert result["error"]["code"] == "AUDIO_TOO_QUIET"
    assert result["error"]["needs_rerecord"] is True


def test_clipping_fails(client):
    result = wait_done(
        client, upload(client, cough=make_wav(7.0, amplitude=3.0)).json()["assessment_id"]
    )
    assert result["status"] == "failed"
    assert result["error"]["code"] == "AUDIO_CLIPPING"


def test_non_wav_rejected(client):
    files = {
        "breathing_file": ("b.wav", b"not a wav at all", "audio/wav"),
        "cough_file": ("c.wav", make_wav(7.0), "audio/wav"),
    }
    resp = client.post("/api/v1/assessments", data={"user_id": USER}, files=files)
    assert resp.status_code == 415
    body = resp.json()
    assert body["code"] == "UNSUPPORTED_FORMAT"
    assert body["needs_rerecord"] is True


def test_empty_file_rejected(client):
    files = {
        "breathing_file": ("b.wav", b"", "audio/wav"),
        "cough_file": ("c.wav", make_wav(7.0), "audio/wav"),
    }
    resp = client.post("/api/v1/assessments", data={"user_id": USER}, files=files)
    assert resp.status_code == 400
    assert resp.json()["code"] == "AUDIO_EMPTY"


def test_missing_user_id_returns_flat_error(client):
    files = {
        "breathing_file": ("b.wav", make_wav(20.0), "audio/wav"),
        "cough_file": ("c.wav", make_wav(7.0), "audio/wav"),
    }
    resp = client.post("/api/v1/assessments", files=files)
    assert resp.status_code == 422
    assert resp.json()["code"] == "BAD_REQUEST"


def test_unknown_assessment_returns_404(client):
    resp = client.get("/api/v1/assessments/does-not-exist")
    assert resp.status_code == 404
    assert resp.json()["code"] == "NOT_FOUND"


# ---------------------------------------------------------------- 幂等


def test_idempotent_retry_does_not_create_second_record(client):
    cid = "client-uuid-abc"
    first = upload(client, client_assessment_id=cid).json()
    second = upload(client, client_assessment_id=cid).json()
    assert second["deduplicated"] is True
    assert second["assessment_id"] == first["assessment_id"]


# ---------------------------------------------------------------- 历史与基线


def test_history_has_seven_days(client):
    body = client.get(
        "/api/v1/assessments", params={"user_id": "fresh-user", "days": 7}
    ).json()
    assert len(body["items"]) == 7
    dates = [i["date"] for i in body["items"]]
    assert dates == sorted(dates)                       # 升序，便于直接画图
    assert all(i["source"] == "seed" for i in body["items"])
    assert body["items"][0]["risk_level"] == "stable"    # 轨迹前段平稳
    assert body["items"][-1]["risk_level"] == "attention"


def test_live_result_replaces_today_seed(client):
    user = "user-live-override"
    client.get("/api/v1/assessments", params={"user_id": user})
    wait_done(client, upload(client, user_id=user).json()["assessment_id"])

    body = client.get("/api/v1/assessments", params={"user_id": user, "days": 7}).json()
    assert len(body["items"]) == 7                       # 当天不会多出一条
    assert body["items"][-1]["source"] == "live"

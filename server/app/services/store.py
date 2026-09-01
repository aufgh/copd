"""SQLite 持久层。

为什么服务端要有存储：结果页要显示"较个人基线偏高"、历史页要显示七天趋势。
这两样都必须由服务端算——如果把七天数据预置在 Android 端，
接入真模型时客户端的数据来源就要改，等于推翻"不改客户端"的前提。

演示阶段库文件在 server/data/copd_demo.sqlite3，删掉即可重置。
"""

from __future__ import annotations

import json
import sqlite3
import threading
from contextlib import contextmanager
from datetime import datetime
from typing import Any, Iterator

from app import config

_lock = threading.Lock()

_SCHEMA = """
CREATE TABLE IF NOT EXISTS assessments (
    id                   TEXT PRIMARY KEY,
    client_assessment_id TEXT,
    user_id              TEXT NOT NULL,
    status               TEXT NOT NULL,
    stage                TEXT NOT NULL,
    progress             INTEGER NOT NULL DEFAULT 0,
    source               TEXT NOT NULL DEFAULT 'live',
    recorded_at          TEXT NOT NULL,
    local_date           TEXT NOT NULL,
    created_at           TEXT NOT NULL,
    completed_at         TEXT,
    engine               TEXT NOT NULL,
    model_version        TEXT NOT NULL,
    scenario             TEXT,
    inference_ms         INTEGER,
    risk_level           TEXT,
    risk_score           INTEGER,
    respiratory_rate     REAL,
    cough_count          INTEGER,
    cough_intensity      REAL,
    quality_label        TEXT,
    quality_json         TEXT,
    metrics_json         TEXT,
    risk_json            TEXT,
    error_json           TEXT,
    breathing_path       TEXT,
    cough_path           TEXT
);
CREATE INDEX IF NOT EXISTS idx_user_date ON assessments (user_id, local_date DESC);
CREATE UNIQUE INDEX IF NOT EXISTS idx_client_id
    ON assessments (user_id, client_assessment_id)
    WHERE client_assessment_id IS NOT NULL;
"""


@contextmanager
def connect() -> Iterator[sqlite3.Connection]:
    config.ensure_dirs()
    conn = sqlite3.connect(config.DB_PATH, timeout=10.0)
    conn.row_factory = sqlite3.Row
    try:
        yield conn
        conn.commit()
    finally:
        conn.close()


def init_db() -> None:
    with connect() as conn:
        conn.execute("PRAGMA journal_mode=WAL")
        conn.executescript(_SCHEMA)


def _dumps(value: Any) -> str | None:
    return None if value is None else json.dumps(value, ensure_ascii=False)


def _loads(value: str | None) -> Any:
    return None if value in (None, "") else json.loads(value)


def row_to_dict(row: sqlite3.Row) -> dict:
    data = dict(row)
    data["quality"] = _loads(data.pop("quality_json"))
    data["metrics"] = _loads(data.pop("metrics_json"))
    data["risk"] = _loads(data.pop("risk_json"))
    data["error"] = _loads(data.pop("error_json"))
    return data


def insert_queued(
    *,
    assessment_id: str,
    client_assessment_id: str | None,
    user_id: str,
    recorded_at: datetime,
    engine: str,
    model_version: str,
    scenario: str | None,
    breathing_path: str | None,
    cough_path: str | None,
) -> None:
    now = datetime.now().isoformat(timespec="seconds")
    with _lock, connect() as conn:
        conn.execute(
            """
            INSERT INTO assessments (
                id, client_assessment_id, user_id, status, stage, progress, source,
                recorded_at, local_date, created_at, engine, model_version, scenario,
                breathing_path, cough_path
            ) VALUES (?, ?, ?, 'queued', 'queued', 0, 'live', ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                assessment_id, client_assessment_id, user_id,
                recorded_at.isoformat(timespec="seconds"),
                recorded_at.strftime("%Y-%m-%d"), now,
                engine, model_version, scenario,
                breathing_path, cough_path,
            ),
        )


def find_by_client_id(user_id: str, client_assessment_id: str) -> dict | None:
    with connect() as conn:
        row = conn.execute(
            "SELECT * FROM assessments WHERE user_id = ? AND client_assessment_id = ?",
            (user_id, client_assessment_id),
        ).fetchone()
    return row_to_dict(row) if row else None


def get(assessment_id: str) -> dict | None:
    with connect() as conn:
        row = conn.execute(
            "SELECT * FROM assessments WHERE id = ?", (assessment_id,)
        ).fetchone()
    return row_to_dict(row) if row else None


def set_stage(assessment_id: str, *, status: str, stage: str, progress: int) -> None:
    with _lock, connect() as conn:
        conn.execute(
            "UPDATE assessments SET status = ?, stage = ?, progress = ? WHERE id = ?",
            (status, stage, progress, assessment_id),
        )


def mark_succeeded(
    assessment_id: str,
    *,
    quality: dict,
    metrics: dict,
    risk: dict,
    model_version: str,
    scenario: str | None,
    inference_ms: int,
) -> None:
    with _lock, connect() as conn:
        conn.execute(
            """
            UPDATE assessments SET
                status = 'succeeded', stage = 'done', progress = 100,
                completed_at = ?, model_version = ?, scenario = ?, inference_ms = ?,
                risk_level = ?, risk_score = ?, respiratory_rate = ?,
                cough_count = ?, cough_intensity = ?, quality_label = ?,
                quality_json = ?, metrics_json = ?, risk_json = ?, error_json = NULL
            WHERE id = ?
            """,
            (
                datetime.now().isoformat(timespec="seconds"),
                model_version, scenario, inference_ms,
                risk["level"], risk["score"], metrics["respiratory_rate"],
                metrics["cough_count"], metrics["cough_intensity"],
                quality["overall_label"],
                _dumps(quality), _dumps(metrics), _dumps(risk),
                assessment_id,
            ),
        )


def mark_failed(assessment_id: str, error: dict, *, quality: dict | None = None) -> None:
    with _lock, connect() as conn:
        conn.execute(
            """
            UPDATE assessments SET
                status = 'failed', stage = 'failed', progress = 100,
                completed_at = ?, error_json = ?,
                quality_json = COALESCE(?, quality_json),
                quality_label = COALESCE(?, quality_label)
            WHERE id = ?
            """,
            (
                datetime.now().isoformat(timespec="seconds"),
                _dumps(error),
                _dumps(quality),
                quality["overall_label"] if quality else None,
                assessment_id,
            ),
        )


def history(user_id: str, days: int) -> list[dict]:
    """每天保留最新一条成功记录，按日期升序返回。

    同一天里真实检测（source='live'）永远优先于演示种子记录，
    否则在早上做的检测会因为种子记录的时间戳更晚而被盖掉。
    """
    with connect() as conn:
        rows = conn.execute(
            """
            SELECT * FROM assessments
            WHERE user_id = ? AND status = 'succeeded'
            ORDER BY local_date DESC, (source = 'live') DESC, created_at DESC
            """,
            (user_id,),
        ).fetchall()

    latest_per_day: dict[str, dict] = {}
    for row in rows:
        data = row_to_dict(row)
        latest_per_day.setdefault(data["local_date"], data)
    ordered = sorted(latest_per_day.values(), key=lambda d: d["local_date"])
    return ordered[-days:]


def baseline(user_id: str, *, before_date: str, days: int = 7) -> dict:
    """算 before_date 之前 days 天的均值，作为个人基线。不含当天。

    按天去重：一天里有几条记录都只算一条，所以 days_used 就是真实天数。
    """
    with connect() as conn:
        rows = conn.execute(
            """
            SELECT local_date,
                   AVG(respiratory_rate) AS respiratory_rate,
                   AVG(cough_count)      AS cough_count,
                   AVG(cough_intensity)  AS cough_intensity,
                   AVG(risk_score)       AS risk_score
            FROM assessments
            WHERE user_id = ? AND status = 'succeeded' AND local_date < ?
              AND respiratory_rate IS NOT NULL
            GROUP BY local_date
            ORDER BY local_date DESC
            LIMIT ?
            """,
            (user_id, before_date, days),
        ).fetchall()

    values = [r for r in rows if r["respiratory_rate"] is not None]
    if not values:
        return {"days_used": 0}

    def mean(key: str) -> float | None:
        present = [float(r[key]) for r in values if r[key] is not None]
        if not present:
            return None
        return round(sum(present) / len(present), 2)

    return {
        "days_used": len(values),
        "respiratory_rate": mean("respiratory_rate"),
        "cough_count": mean("cough_count"),
        "cough_intensity": mean("cough_intensity"),
        "risk_score": mean("risk_score"),
    }


def count_for_user(user_id: str) -> int:
    with connect() as conn:
        row = conn.execute(
            "SELECT COUNT(*) AS n FROM assessments WHERE user_id = ?", (user_id,)
        ).fetchone()
    return int(row["n"])


def oldest_upload_paths(keep: int) -> list[str]:
    """返回超出保留条数的旧记录的音频路径，交给调用方删除文件。"""
    with connect() as conn:
        rows = conn.execute(
            """
            SELECT breathing_path, cough_path FROM assessments
            WHERE source = 'live'
            ORDER BY created_at DESC
            LIMIT -1 OFFSET ?
            """,
            (keep,),
        ).fetchall()
    paths: list[str] = []
    for row in rows:
        paths.extend(p for p in (row["breathing_path"], row["cough_path"]) if p)
    return paths


def clear_upload_paths(paths: list[str]) -> None:
    if not paths:
        return
    with _lock, connect() as conn:
        conn.executemany(
            "UPDATE assessments SET breathing_path = NULL WHERE breathing_path = ?",
            [(p,) for p in paths],
        )
        conn.executemany(
            "UPDATE assessments SET cough_path = NULL WHERE cough_path = ?",
            [(p,) for p in paths],
        )


def insert_seed_record(record: dict) -> None:
    """写入预置的历史演示数据（source='seed'）。"""
    with _lock, connect() as conn:
        conn.execute(
            """
            INSERT OR REPLACE INTO assessments (
                id, client_assessment_id, user_id, status, stage, progress, source,
                recorded_at, local_date, created_at, completed_at,
                engine, model_version, scenario, inference_ms,
                risk_level, risk_score, respiratory_rate, cough_count, cough_intensity,
                quality_label, quality_json, metrics_json, risk_json
            ) VALUES (
                :id, NULL, :user_id, 'succeeded', 'done', 100, 'seed',
                :recorded_at, :local_date, :created_at, :created_at,
                :engine, :model_version, :scenario, :inference_ms,
                :risk_level, :risk_score, :respiratory_rate, :cough_count, :cough_intensity,
                :quality_label, :quality_json, :metrics_json, :risk_json
            )
            """,
            record,
        )


def seed_exists(user_id: str) -> bool:
    with connect() as conn:
        row = conn.execute(
            "SELECT COUNT(*) AS n FROM assessments WHERE user_id = ? AND source = 'seed'",
            (user_id,),
        ).fetchone()
    return int(row["n"]) > 0


def reset_all() -> None:
    """仅用于测试。"""
    with _lock, connect() as conn:
        conn.execute("DELETE FROM assessments")

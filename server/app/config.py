"""全局配置。

所有可调项都可以用环境变量覆盖，演示时不用改代码。
最重要的两个：
    COPD_ENGINE          demo | real      当前启用哪个推理引擎
    COPD_DEMO_SCENARIO   stable | attention | alert   demo 引擎的默认场景
"""

from __future__ import annotations

import os
from pathlib import Path

# ---------------------------------------------------------------- 路径

SERVER_DIR = Path(__file__).resolve().parent.parent
DATA_DIR = Path(os.getenv("COPD_DATA_DIR", SERVER_DIR / "data"))
UPLOAD_DIR = DATA_DIR / "uploads"
DB_PATH = Path(os.getenv("COPD_DB_PATH", DATA_DIR / "copd_demo.sqlite3"))

# ---------------------------------------------------------------- 服务

HOST = os.getenv("COPD_HOST", "0.0.0.0")          # 必须是 0.0.0.0，手机才连得上
PORT = int(os.getenv("COPD_PORT", "8000"))

# 契约版本。客户端用它判断能不能解析响应；加字段不升版，删改字段才升。
SCHEMA_VERSION = "1.0"
SUPPORTED_SCHEMA_VERSIONS = ["1.0"]

# ---------------------------------------------------------------- 推理引擎

ENGINE = os.getenv("COPD_ENGINE", "demo").strip().lower()
DEMO_SCENARIO = os.getenv("COPD_DEMO_SCENARIO", "attention").strip().lower()

# 虚拟推理的耗时区间（秒）。真引擎接入后这两个值不再生效。
DEMO_MIN_SECONDS = float(os.getenv("COPD_DEMO_MIN_SECONDS", "2.0"))
DEMO_MAX_SECONDS = float(os.getenv("COPD_DEMO_MAX_SECONDS", "4.0"))

# ---------------------------------------------------------------- 音频要求
#
# 这一份是唯一权威来源：/api/v1/health 会把它下发给 Android，
# 客户端只做粗筛，不自己写死阈值，避免两端规则漂移。

AUDIO_SAMPLE_RATE = 16_000
AUDIO_CHANNELS = 1
AUDIO_BITS_PER_SAMPLE = 16

BREATHING_TARGET_MS = 20_000
BREATHING_MIN_MS = 15_000
COUGH_MIN_MS = 2_500
COUGH_TARGET_MS = 8_000

MIN_RMS_DBFS = -45.0        # 比这个还轻，基本是没录到人
WARN_RMS_DBFS = -38.0       # 能用但偏轻，提示但不拦
MAX_CLIP_RATIO = 0.02       # 削波样本占比超过 2% 判不合格
MAX_UPLOAD_MB = 25

# ---------------------------------------------------------------- 保留策略

# 演示环境不需要长期留存音频，超出条数后自动清理最旧的。
KEEP_UPLOAD_FILES = int(os.getenv("COPD_KEEP_UPLOAD_FILES", "40"))

# 启动时是否 seed 最近七天演示数据（历史趋势页要用）
SEED_DEMO_HISTORY = os.getenv("COPD_SEED_HISTORY", "1") != "0"
SEED_DAYS = 7


def audio_requirements() -> dict:
    """下发给客户端的录音规格与质检阈值。"""
    return {
        "sample_rate": AUDIO_SAMPLE_RATE,
        "channels": AUDIO_CHANNELS,
        "bits_per_sample": AUDIO_BITS_PER_SAMPLE,
        "breathing_target_ms": BREATHING_TARGET_MS,
        "breathing_min_ms": BREATHING_MIN_MS,
        "cough_min_ms": COUGH_MIN_MS,
        "cough_target_ms": COUGH_TARGET_MS,
        "min_rms_dbfs": MIN_RMS_DBFS,
        "warn_rms_dbfs": WARN_RMS_DBFS,
        "max_clip_ratio": MAX_CLIP_RATIO,
        "max_upload_mb": MAX_UPLOAD_MB,
    }


def ensure_dirs() -> None:
    UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)

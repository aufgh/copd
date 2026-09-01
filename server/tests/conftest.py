"""测试环境隔离：用临时数据库和临时上传目录，不污染演示数据。

必须在 import app.* 之前设置环境变量，config 在导入时就读取它们。
"""

from __future__ import annotations

import os
import sys
import tempfile
from pathlib import Path

# 让 `pytest` 和 `python -m pytest` 两种写法都能 import 到 app 包
_SERVER_DIR = Path(__file__).resolve().parent.parent
if str(_SERVER_DIR) not in sys.path:
    sys.path.insert(0, str(_SERVER_DIR))

_TMP = Path(tempfile.mkdtemp(prefix="copd-test-"))
os.environ["COPD_DATA_DIR"] = str(_TMP)
os.environ["COPD_DB_PATH"] = str(_TMP / "test.sqlite3")
os.environ["COPD_ENGINE"] = "demo"
os.environ["COPD_DEMO_SCENARIO"] = "attention"
os.environ["COPD_DEMO_MIN_SECONDS"] = "0.01"
os.environ["COPD_DEMO_MAX_SECONDS"] = "0.05"

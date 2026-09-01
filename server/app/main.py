"""FastAPI 应用入口。

启动方式（Windows PowerShell / CMD 都可以）：
    cd server
    python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
或者直接：
    python run_server.py
"""

from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from typing import AsyncIterator

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException

from app import config
from app.api import assessments, health
from app.errors import ErrorCode, error_body
from app.inference import get_engine
from app.services import store

logger = logging.getLogger("copd")


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    config.ensure_dirs()
    store.init_db()
    engine = get_engine()
    logger.info(
        "推理引擎 = %s (%s)，监听 %s:%s",
        engine.engine_kind, engine.version, config.HOST, config.PORT,
    )
    yield


app = FastAPI(
    title="COPD 趋势观察演示服务",
    version=config.SCHEMA_VERSION,
    description=(
        "呼吸音与咳嗽音上传、虚拟推理与趋势查询。"
        "当前为演示用途，结果不构成医学诊断。"
    ),
    lifespan=lifespan,
)

# 局域网演示环境，放开 CORS 只是为了方便用浏览器直接调接口
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health.router, prefix="/api/v1")
app.include_router(assessments.router, prefix="/api/v1")


@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException) -> JSONResponse:
    """让错误体保持顶层扁平结构 {code, message, hint, ...}，而不是被包进 detail。

    注册在 starlette 的 HTTPException 上，这样路径写错导致的 404 / 方法不对导致的 405
    也走同一套错误体，客户端不会看到 {"detail": "Not Found"} 这种解析不了的响应。
    """
    if isinstance(exc.detail, dict) and "code" in exc.detail:
        return JSONResponse(status_code=exc.status_code, content=exc.detail)
    code = ErrorCode.NOT_FOUND if exc.status_code == 404 else ErrorCode.BAD_REQUEST
    return JSONResponse(
        status_code=exc.status_code, content=error_body(code, str(exc.detail))
    )


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(
    request: Request, exc: RequestValidationError
) -> JSONResponse:
    first = exc.errors()[0] if exc.errors() else {}
    field = ".".join(str(p) for p in first.get("loc", []) if p != "body")
    return JSONResponse(
        status_code=422,
        content=error_body(
            ErrorCode.BAD_REQUEST,
            first.get("msg", "请求参数校验失败"),
            field or None,
        ),
    )


@app.exception_handler(Exception)
async def unhandled_exception_handler(request: Request, exc: Exception) -> JSONResponse:
    logger.exception("未捕获异常: %s", exc)
    return JSONResponse(
        status_code=500,
        content=error_body(ErrorCode.INTERNAL_ERROR, "服务端内部错误，可以直接重试"),
    )


@app.get("/", include_in_schema=False)
def root() -> dict:
    return {
        "service": "copd-demo-server",
        "health": "/api/v1/health",
        "docs": "/docs",
    }

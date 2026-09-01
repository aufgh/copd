"""统一错误码。

客户端按 code 决定下一步动作，不解析 message 文案：
    RETRYABLE_UPLOAD  -> 只重传，不用重录
    NEEDS_RERECORD    -> 必须重录对应的那段音频
"""

from __future__ import annotations

from enum import StrEnum

from fastapi import HTTPException


class ErrorCode(StrEnum):
    # 需要重录
    AUDIO_MISSING = "AUDIO_MISSING"
    AUDIO_EMPTY = "AUDIO_EMPTY"
    AUDIO_TOO_SHORT = "AUDIO_TOO_SHORT"
    AUDIO_TOO_QUIET = "AUDIO_TOO_QUIET"
    AUDIO_CLIPPING = "AUDIO_CLIPPING"
    UNSUPPORTED_FORMAT = "UNSUPPORTED_FORMAT"
    # 只需重传
    FILE_TOO_LARGE = "FILE_TOO_LARGE"
    ENGINE_FAILED = "ENGINE_FAILED"
    INTERNAL_ERROR = "INTERNAL_ERROR"
    # 其它
    NOT_FOUND = "NOT_FOUND"
    BAD_REQUEST = "BAD_REQUEST"


NEEDS_RERECORD = {
    ErrorCode.AUDIO_MISSING,
    ErrorCode.AUDIO_EMPTY,
    ErrorCode.AUDIO_TOO_SHORT,
    ErrorCode.AUDIO_TOO_QUIET,
    ErrorCode.AUDIO_CLIPPING,
    ErrorCode.UNSUPPORTED_FORMAT,
}

_HINTS: dict[ErrorCode, str] = {
    ErrorCode.AUDIO_MISSING: "请重新录制这一段音频。",
    ErrorCode.AUDIO_EMPTY: "没有采集到声音，请检查麦克风权限后重录。",
    ErrorCode.AUDIO_TOO_SHORT: "录音时长不足，请重录并录满提示时长。",
    ErrorCode.AUDIO_TOO_QUIET: "音量过低，请把手机移近口鼻约 20 至 30 厘米后重录。",
    ErrorCode.AUDIO_CLIPPING: "音量过大出现失真，请稍微拿远一点后重录。",
    ErrorCode.UNSUPPORTED_FORMAT: "音频格式不支持，需要单声道 16 位 WAV。",
    ErrorCode.FILE_TOO_LARGE: "文件过大，请重新上传。",
    ErrorCode.ENGINE_FAILED: "分析未完成，可以直接重试，不需要重新录音。",
    ErrorCode.INTERNAL_ERROR: "服务端异常，可以直接重试。",
    ErrorCode.NOT_FOUND: "请返回首页重新开始检测。",
    ErrorCode.BAD_REQUEST: "请求参数有误。",
}


def error_body(code: ErrorCode, message: str, field: str | None = None) -> dict:
    return {
        "code": str(code),
        "message": message,
        "hint": _HINTS.get(code, ""),
        "field": field,
        "needs_rerecord": code in NEEDS_RERECORD,
    }


class ApiError(HTTPException):
    """带结构化 body 的异常，FastAPI 直接序列化成统一错误体。"""

    def __init__(
        self,
        status_code: int,
        code: ErrorCode,
        message: str,
        field: str | None = None,
    ) -> None:
        super().__init__(status_code=status_code, detail=error_body(code, message, field))
        self.code = code

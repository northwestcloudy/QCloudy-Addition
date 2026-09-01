from __future__ import annotations

from datetime import UTC, datetime
from typing import Any

from fastapi import Request


SCHEMA_VERSION = 1


def utc_now_iso() -> str:
    return datetime.now(UTC).isoformat().replace("+00:00", "Z")


def ok(
    request: Request,
    data: Any,
    *,
    source: str = "qca",
    cache: dict[str, Any] | None = None,
) -> dict[str, Any]:
    meta: dict[str, Any] = {
        "requestId": getattr(request.state, "request_id", "unknown"),
        "generatedAt": utc_now_iso(),
        "source": source,
    }
    if cache is not None:
        meta["cache"] = cache
    return {
        "schemaVersion": SCHEMA_VERSION,
        "data": data,
        "meta": meta,
        "error": None,
    }


def error_body(
    request: Request,
    *,
    code: str,
    message: str,
    retryable: bool,
    retry_after_seconds: int | None = None,
) -> dict[str, Any]:
    error: dict[str, Any] = {
        "code": code,
        "message": message,
        "retryable": retryable,
    }
    if retry_after_seconds is not None:
        error["retryAfterSeconds"] = retry_after_seconds
    return {"schemaVersion": SCHEMA_VERSION, "error": error}

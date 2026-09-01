from __future__ import annotations

import logging
import uuid
from contextlib import asynccontextmanager
from dataclasses import dataclass
from typing import Any, AsyncIterator

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from . import __version__
from .api import router
from .cache import CacheStore
from .config import Settings
from .errors import ApiProblem
from .market import MarketManager
from .profile_service import PlayerProfileService
from .responses import error_body
from .storage import MarketStorage
from .upstream import HypixelUpstream

LOGGER = logging.getLogger(__name__)


@dataclass(slots=True)
class ApplicationServices:
    settings: Settings
    cache: CacheStore
    storage: MarketStorage
    upstream: Any
    profiles: PlayerProfileService
    market: MarketManager


def create_app(
    settings: Settings | None = None,
    *,
    upstream: Any | None = None,
    cache: CacheStore | None = None,
    storage: MarketStorage | None = None,
) -> FastAPI:
    settings = settings or Settings()
    cache = cache or CacheStore(
        redis_url=settings.redis_url,
        prefix=settings.redis_prefix,
        memory_max_entries=settings.cache_memory_max_entries,
        memory_max_bytes=settings.cache_memory_max_bytes,
    )
    storage = storage or MarketStorage(settings.sqlite_path)
    upstream = upstream or HypixelUpstream(settings)
    profile_service = PlayerProfileService(settings, cache, upstream)
    market = MarketManager(settings, cache, storage, upstream)
    service_bundle = ApplicationServices(
        settings=settings,
        cache=cache,
        storage=storage,
        upstream=upstream,
        profiles=profile_service,
        market=market,
    )

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        app.state.services = service_bundle
        await storage.initialize()
        if settings.scheduler_enabled:
            await market.start()
        try:
            yield
        finally:
            await market.stop()
            await cache.close()
            close = getattr(upstream, "close", None)
            if close is not None:
                await close()

    app = FastAPI(
        title="QCloudy API",
        summary="Player profile and transformed SkyBlock market data for QCloudy Addition.",
        version=__version__,
        openapi_url="/openapi.json",
        docs_url="/docs",
        redoc_url="/redoc",
        lifespan=lifespan,
    )
    app.state.services = service_bundle

    if settings.cors_origins:
        app.add_middleware(
            CORSMiddleware,
            allow_origins=settings.cors_origins,
            allow_credentials=False,
            allow_methods=["GET", "POST"],
            allow_headers=["Content-Type", "X-Request-ID"],
        )

    @app.middleware("http")
    async def request_context(request: Request, call_next: Any) -> Any:
        request_id = request.headers.get("X-Request-ID") or uuid.uuid4().hex
        request.state.request_id = request_id[:128]
        response = await call_next(request)
        response.headers["X-Request-ID"] = request.state.request_id
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["Cache-Control"] = "no-store"
        return response

    @app.exception_handler(ApiProblem)
    async def api_problem_handler(request: Request, exc: ApiProblem) -> JSONResponse:
        headers = None
        if exc.retry_after_seconds is not None:
            headers = {"Retry-After": str(exc.retry_after_seconds)}
        return JSONResponse(
            status_code=exc.status_code,
            headers=headers,
            content=error_body(
                request,
                code=exc.code,
                message=exc.message,
                retryable=exc.retryable,
                retry_after_seconds=exc.retry_after_seconds,
            ),
        )

    @app.exception_handler(RequestValidationError)
    async def validation_handler(request: Request, exc: RequestValidationError) -> JSONResponse:
        return JSONResponse(
            status_code=422,
            content=error_body(
                request,
                code="INVALID_REQUEST",
                message="The request path, query, or body is invalid.",
                retryable=False,
            ),
        )

    @app.exception_handler(Exception)
    async def unhandled_handler(request: Request, exc: Exception) -> JSONResponse:
        LOGGER.exception("Unhandled request failure", exc_info=exc)
        return JSONResponse(
            status_code=500,
            content=error_body(
                request,
                code="INTERNAL_ERROR",
                message="The service encountered an internal error.",
                retryable=True,
            ),
        )

    app.include_router(router)
    return app


app = create_app()

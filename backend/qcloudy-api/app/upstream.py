from __future__ import annotations

import asyncio
import logging
import math
import time
from collections import deque
from collections.abc import Callable
from typing import Any
from urllib.parse import quote

import httpx

from .config import Settings
from .errors import (
    ApiProblem,
    UpstreamAuthenticationError,
    UpstreamNotFound,
    UpstreamTemporaryError,
)

LOGGER = logging.getLogger(__name__)


class HypixelUpstream:
    """A fixed-method upstream client. It intentionally exposes no generic proxy method."""

    USER_AGENT = "QCloudy-API/1 (+https://qcloudy.net/)"

    def __init__(
        self,
        settings: Settings,
        client: httpx.AsyncClient | None = None,
        *,
        clock: Callable[[], float] | None = None,
    ):
        self._settings = settings
        self._owns_client = client is None
        self._clock = clock or time.monotonic
        self._authenticated_budget_lock = asyncio.Lock()
        self._authenticated_token_capacity = float(
            min(
                settings.hypixel_authenticated_budget_per_minute,
                settings.hypixel_authenticated_burst,
            )
        )
        self._authenticated_tokens = self._authenticated_token_capacity
        self._authenticated_last_refill = self._clock()
        self._authenticated_backoff_until = 0.0
        self._authenticated_request_times: deque[float] = deque()
        if client is None:
            timeout = httpx.Timeout(
                settings.upstream_timeout_seconds,
                connect=settings.upstream_connect_timeout_seconds,
            )
            limits = httpx.Limits(
                max_connections=settings.upstream_max_connections,
                max_keepalive_connections=settings.upstream_max_connections,
            )
            client = httpx.AsyncClient(
                timeout=timeout,
                limits=limits,
                follow_redirects=False,
                headers={"Accept": "application/json", "User-Agent": self.USER_AGENT},
            )
        self._client = client

    async def _claim_authenticated_budget(self) -> None:
        """Fail fast before an authenticated request can spend the shared API key."""

        async with self._authenticated_budget_lock:
            now = self._clock()
            if now < self._authenticated_backoff_until:
                retry_after = max(1, math.ceil(self._authenticated_backoff_until - now))
                raise UpstreamTemporaryError(
                    "Authenticated Hypixel requests are temporarily backed off.",
                    retry_after_seconds=retry_after,
                )

            elapsed = max(0.0, now - self._authenticated_last_refill)
            refill_rate = self._settings.hypixel_authenticated_budget_per_minute / 60.0
            minute_boundary = now - 60.0
            while (
                self._authenticated_request_times
                and self._authenticated_request_times[0] <= minute_boundary
            ):
                self._authenticated_request_times.popleft()
            self._authenticated_tokens = min(
                self._authenticated_token_capacity,
                self._authenticated_tokens + elapsed * refill_rate,
            )
            self._authenticated_last_refill = now

            if (
                len(self._authenticated_request_times)
                >= self._settings.hypixel_authenticated_budget_per_minute
            ):
                retry_after = max(
                    1,
                    math.ceil(self._authenticated_request_times[0] + 60.0 - now),
                )
                raise UpstreamTemporaryError(
                    "The authenticated Hypixel minute budget is temporarily exhausted.",
                    retry_after_seconds=retry_after,
                )

            if self._authenticated_tokens < 1.0:
                retry_after = max(
                    1,
                    math.ceil((1.0 - self._authenticated_tokens) / refill_rate),
                )
                raise UpstreamTemporaryError(
                    "The authenticated Hypixel request budget is temporarily exhausted.",
                    retry_after_seconds=retry_after,
                )
            self._authenticated_tokens -= 1.0
            self._authenticated_request_times.append(now)

    async def _back_off_authenticated_requests(self, retry_after: int) -> None:
        async with self._authenticated_budget_lock:
            self._authenticated_backoff_until = max(
                self._authenticated_backoff_until,
                self._clock() + retry_after,
            )

    async def _observe_authenticated_response(self, response: httpx.Response) -> None:
        raw_remaining = response.headers.get("RateLimit-Remaining")
        if raw_remaining is None:
            return
        try:
            remaining = int(float(raw_remaining))
        except (OverflowError, ValueError):
            return
        if remaining > 0:
            return
        retry_after = self._retry_after(response)
        if retry_after is None:
            retry_after = self._settings.hypixel_authenticated_429_backoff_seconds
        await self._back_off_authenticated_requests(retry_after)

    @staticmethod
    def _retry_after(response: httpx.Response) -> int | None:
        for header in ("Retry-After", "RateLimit-Reset"):
            raw = response.headers.get(header)
            if raw is not None:
                try:
                    return min(3600, max(1, int(float(raw))))
                except (OverflowError, ValueError):
                    continue
        return None

    async def _json_get(
        self,
        url: str,
        *,
        params: dict[str, Any] | None = None,
        authenticated: bool = False,
        not_found_is_none: bool = False,
    ) -> dict[str, Any] | None:
        headers: dict[str, str] = {}
        if authenticated:
            if not self._settings.hypixel_api_key:
                raise UpstreamAuthenticationError("QCA_HYPIXEL_API_KEY is not configured.")
            await self._claim_authenticated_budget()
            headers["API-Key"] = self._settings.hypixel_api_key

        try:
            response = await self._client.get(url, params=params, headers=headers)
        except (httpx.TimeoutException, httpx.NetworkError) as exc:
            raise UpstreamTemporaryError("The upstream service could not be reached.") from exc

        if authenticated:
            await self._observe_authenticated_response(response)

        if response.status_code in {204, 404} and not_found_is_none:
            return None
        if response.status_code in {400, 404, 422}:
            raise UpstreamNotFound()
        if response.status_code == 403:
            raise UpstreamAuthenticationError()
        if response.status_code == 429:
            retry_after = self._retry_after(response)
            if authenticated:
                if retry_after is None:
                    retry_after = self._settings.hypixel_authenticated_429_backoff_seconds
                await self._back_off_authenticated_requests(retry_after)
            raise UpstreamTemporaryError(
                "The upstream service is rate limiting requests.",
                retry_after_seconds=retry_after,
            )
        if response.status_code >= 500:
            raise UpstreamTemporaryError(
                f"The upstream service returned HTTP {response.status_code}."
            )
        if response.status_code != 200:
            raise ApiProblem(
                502,
                "UPSTREAM_UNEXPECTED_STATUS",
                f"The upstream service returned HTTP {response.status_code}.",
                retryable=False,
            )
        try:
            # Auction pages can be several megabytes. httpx buffers the bytes
            # asynchronously, but Response.json() performs CPU-bound decoding;
            # keep that work off Uvicorn's event loop.
            payload = await asyncio.to_thread(response.json)
        except ValueError as exc:
            raise UpstreamTemporaryError("The upstream service returned invalid JSON.") from exc
        if not isinstance(payload, dict):
            raise UpstreamTemporaryError("The upstream service returned an invalid payload.")
        if payload.get("success") is False:
            cause = payload.get("cause")
            message = "The upstream service rejected the request."
            if isinstance(cause, str) and cause:
                message = cause[:200]
            raise ApiProblem(502, "UPSTREAM_REJECTED_REQUEST", message, retryable=False)
        return payload

    def _hypixel_url(self, path: str) -> str:
        return f"{self._settings.hypixel_base_url.rstrip('/')}/{path.lstrip('/')}"

    async def resolve_name(self, player_name: str) -> dict[str, Any] | None:
        safe_name = quote(player_name, safe="")
        payload = await self._json_get(
            f"{self._settings.mojang_base_url.rstrip('/')}/users/profiles/minecraft/{safe_name}",
            not_found_is_none=True,
        )
        if payload is None:
            return None
        player_id = payload.get("id")
        name = payload.get("name")
        if not isinstance(player_id, str) or len(player_id.replace("-", "")) != 32:
            raise UpstreamTemporaryError("Mojang returned an invalid player identifier.")
        return {"uuid": player_id.replace("-", "").lower(), "name": name or player_name}

    async def fetch_player(self, player_uuid: str) -> dict[str, Any]:
        payload = await self._json_get(
            self._hypixel_url("player"),
            params={"uuid": player_uuid},
            authenticated=True,
        )
        assert payload is not None
        return payload

    async def fetch_profiles(self, player_uuid: str) -> dict[str, Any]:
        payload = await self._json_get(
            self._hypixel_url("skyblock/profiles"),
            params={"uuid": player_uuid},
            authenticated=True,
        )
        assert payload is not None
        return payload

    async def fetch_bazaar(self) -> dict[str, Any]:
        payload = await self._json_get(self._hypixel_url("skyblock/bazaar"))
        assert payload is not None
        return payload

    async def fetch_auction_page(self, page: int) -> dict[str, Any]:
        payload = await self._json_get(
            self._hypixel_url("skyblock/auctions"),
            params={"page": page},
        )
        assert payload is not None
        return payload

    async def fetch_ended_auctions(self) -> dict[str, Any]:
        payload = await self._json_get(self._hypixel_url("skyblock/auctions_ended"))
        assert payload is not None
        return payload

    async def fetch_item_resources(self) -> dict[str, Any]:
        payload = await self._json_get(self._hypixel_url("resources/skyblock/items"))
        assert payload is not None
        return payload

    async def close(self) -> None:
        if self._owns_client:
            await self._client.aclose()

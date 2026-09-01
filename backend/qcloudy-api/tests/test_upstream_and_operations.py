from __future__ import annotations

import asyncio
import threading
from pathlib import Path

import httpx
import pytest

from app.config import Settings
from app.errors import UpstreamTemporaryError
from app.upstream import HypixelUpstream
from tests.conftest import PLAYER_UUID


@pytest.mark.asyncio
async def test_upstream_json_decoding_runs_off_the_event_loop(monkeypatch) -> None:
    event_loop_thread = threading.get_ident()
    decode_threads: list[int] = []
    original_json = httpx.Response.json

    def observed_json(response: httpx.Response, **kwargs):
        decode_threads.append(threading.get_ident())
        return original_json(response, **kwargs)

    monkeypatch.setattr(httpx.Response, "json", observed_json)
    client = httpx.AsyncClient(
        transport=httpx.MockTransport(
            lambda _request: httpx.Response(
                200, json={"success": True, "products": {}}
            )
        )
    )
    upstream = HypixelUpstream(Settings(scheduler_enabled=False), client=client)
    await upstream.fetch_bazaar()
    assert decode_threads
    assert all(thread_id != event_loop_thread for thread_id in decode_threads)
    await client.aclose()


def test_default_name_uuid_cache_is_exactly_72_hours() -> None:
    settings = Settings(scheduler_enabled=False)
    expected = 72 * 60 * 60
    assert settings.name_fresh_seconds == expected
    assert settings.name_stale_seconds == expected


@pytest.mark.asyncio
async def test_api_key_is_never_sent_to_public_market_endpoints() -> None:
    requests: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        if request.url.path.endswith("/skyblock/bazaar"):
            return httpx.Response(200, json={"success": True, "products": {}})
        return httpx.Response(200, json={"success": True, "profiles": []})

    client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    upstream = HypixelUpstream(
        Settings(hypixel_api_key="super-secret", scheduler_enabled=False), client=client
    )
    await upstream.fetch_bazaar()
    await upstream.fetch_profiles(PLAYER_UUID)
    assert "API-Key" not in requests[0].headers
    assert requests[1].headers["API-Key"] == "super-secret"
    await client.aclose()


@pytest.mark.asyncio
async def test_authenticated_budget_fails_fast_and_refills() -> None:
    requests: list[httpx.Request] = []
    now = [100.0]

    def handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        return httpx.Response(200, json={"success": True, "profiles": []})

    client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    upstream = HypixelUpstream(
        Settings(
            hypixel_api_key="super-secret",
            scheduler_enabled=False,
            hypixel_authenticated_budget_per_minute=60,
            hypixel_authenticated_burst=2,
        ),
        client=client,
        clock=lambda: now[0],
    )

    await upstream.fetch_profiles(PLAYER_UUID)
    await upstream.fetch_profiles(PLAYER_UUID)
    with pytest.raises(UpstreamTemporaryError) as exhausted:
        await upstream.fetch_profiles(PLAYER_UUID)
    assert exhausted.value.retry_after_seconds == 1
    assert len(requests) == 2

    now[0] += 1.0
    await upstream.fetch_profiles(PLAYER_UUID)
    assert len(requests) == 3
    await client.aclose()


@pytest.mark.asyncio
async def test_authenticated_budget_is_shared_across_concurrent_endpoints() -> None:
    requests: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        return httpx.Response(200, json={"success": True, "profiles": [], "player": {}})

    client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    upstream = HypixelUpstream(
        Settings(
            hypixel_api_key="super-secret",
            scheduler_enabled=False,
            hypixel_authenticated_budget_per_minute=60,
            hypixel_authenticated_burst=2,
        ),
        client=client,
        clock=lambda: 100.0,
    )

    results = await asyncio.gather(
        upstream.fetch_player(PLAYER_UUID),
        upstream.fetch_profiles(PLAYER_UUID),
        upstream.fetch_museum("b" * 32),
        upstream.fetch_garden("b" * 32),
        return_exceptions=True,
    )
    assert sum(not isinstance(result, Exception) for result in results) == 2
    assert sum(isinstance(result, UpstreamTemporaryError) for result in results) == 2
    assert len(requests) == 2
    await client.aclose()


@pytest.mark.asyncio
async def test_authenticated_budget_also_enforces_a_rolling_minute_limit() -> None:
    requests: list[httpx.Request] = []
    now = [100.0]

    def handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        return httpx.Response(200, json={"success": True, "profiles": []})

    client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    upstream = HypixelUpstream(
        Settings(
            hypixel_api_key="super-secret",
            scheduler_enabled=False,
            hypixel_authenticated_budget_per_minute=3,
            hypixel_authenticated_burst=3,
        ),
        client=client,
        clock=lambda: now[0],
    )

    for _ in range(3):
        await upstream.fetch_profiles(PLAYER_UUID)
    now[0] += 20.0
    with pytest.raises(UpstreamTemporaryError) as exhausted:
        await upstream.fetch_profiles(PLAYER_UUID)
    assert exhausted.value.retry_after_seconds == 40
    assert len(requests) == 3

    now[0] += 40.0
    await upstream.fetch_profiles(PLAYER_UUID)
    assert len(requests) == 4
    await client.aclose()


@pytest.mark.asyncio
async def test_authenticated_429_opens_backoff_without_blocking_public_market() -> None:
    requests: list[httpx.Request] = []
    now = [200.0]

    def handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        if "API-Key" in request.headers:
            return httpx.Response(429, headers={"Retry-After": "30"})
        return httpx.Response(200, json={"success": True, "products": {}})

    client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    upstream = HypixelUpstream(
        Settings(hypixel_api_key="super-secret", scheduler_enabled=False),
        client=client,
        clock=lambda: now[0],
    )

    with pytest.raises(UpstreamTemporaryError) as limited:
        await upstream.fetch_profiles(PLAYER_UUID)
    assert limited.value.retry_after_seconds == 30

    with pytest.raises(UpstreamTemporaryError) as backed_off:
        await upstream.fetch_profiles(PLAYER_UUID)
    assert backed_off.value.retry_after_seconds == 30
    assert len(requests) == 1

    await upstream.fetch_bazaar()
    assert len(requests) == 2
    assert "API-Key" not in requests[-1].headers

    now[0] += 30.0
    with pytest.raises(UpstreamTemporaryError):
        await upstream.fetch_profiles(PLAYER_UUID)
    assert len(requests) == 3
    await client.aclose()


@pytest.mark.asyncio
async def test_authenticated_429_without_header_uses_configured_backoff() -> None:
    requests: list[httpx.Request] = []
    now = [300.0]

    def handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        return httpx.Response(429)

    client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    upstream = HypixelUpstream(
        Settings(
            hypixel_api_key="super-secret",
            scheduler_enabled=False,
            hypixel_authenticated_429_backoff_seconds=17,
        ),
        client=client,
        clock=lambda: now[0],
    )

    with pytest.raises(UpstreamTemporaryError) as limited:
        await upstream.fetch_profiles(PLAYER_UUID)
    assert limited.value.retry_after_seconds == 17

    with pytest.raises(UpstreamTemporaryError) as backed_off:
        await upstream.fetch_profiles(PLAYER_UUID)
    assert backed_off.value.retry_after_seconds == 17
    assert len(requests) == 1
    await client.aclose()


@pytest.mark.asyncio
async def test_zero_upstream_remaining_opens_backoff_before_the_next_request() -> None:
    requests: list[httpx.Request] = []
    now = [400.0]

    def handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        return httpx.Response(
            200,
            headers={"RateLimit-Remaining": "0", "RateLimit-Reset": "12"},
            json={"success": True, "profiles": []},
        )

    client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    upstream = HypixelUpstream(
        Settings(hypixel_api_key="super-secret", scheduler_enabled=False),
        client=client,
        clock=lambda: now[0],
    )

    await upstream.fetch_profiles(PLAYER_UUID)
    with pytest.raises(UpstreamTemporaryError) as backed_off:
        await upstream.fetch_profiles(PLAYER_UUID)
    assert backed_off.value.retry_after_seconds == 12
    assert len(requests) == 1
    await client.aclose()


@pytest.mark.asyncio
async def test_zero_upstream_remaining_without_reset_uses_configured_backoff() -> None:
    requests: list[httpx.Request] = []
    now = [450.0]

    def handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        return httpx.Response(
            200,
            headers={"RateLimit-Remaining": "0"},
            json={"success": True, "profiles": []},
        )

    client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    upstream = HypixelUpstream(
        Settings(
            hypixel_api_key="super-secret",
            scheduler_enabled=False,
            hypixel_authenticated_429_backoff_seconds=19,
        ),
        client=client,
        clock=lambda: now[0],
    )

    await upstream.fetch_profiles(PLAYER_UUID)
    with pytest.raises(UpstreamTemporaryError) as backed_off:
        await upstream.fetch_profiles(PLAYER_UUID)
    assert backed_off.value.retry_after_seconds == 19
    assert len(requests) == 1
    await client.aclose()


@pytest.mark.asyncio
async def test_public_429_does_not_open_authenticated_backoff() -> None:
    requests: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        if request.url.path.endswith("/skyblock/bazaar"):
            return httpx.Response(429, headers={"Retry-After": "45"})
        return httpx.Response(200, json={"success": True, "profiles": []})

    client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    upstream = HypixelUpstream(
        Settings(hypixel_api_key="super-secret", scheduler_enabled=False), client=client
    )

    with pytest.raises(UpstreamTemporaryError) as limited:
        await upstream.fetch_bazaar()
    assert limited.value.retry_after_seconds == 45

    await upstream.fetch_profiles(PLAYER_UUID)
    assert len(requests) == 2
    assert requests[-1].headers["API-Key"] == "super-secret"
    await client.aclose()


@pytest.mark.asyncio
async def test_authenticated_fail_fast_retry_after_reaches_the_http_client(
    app_client, upstream, monkeypatch: pytest.MonkeyPatch
) -> None:
    async def throttled(player_uuid: str) -> dict[str, object]:
        raise UpstreamTemporaryError(
            "The authenticated Hypixel request budget is temporarily exhausted.",
            retry_after_seconds=9,
        )

    monkeypatch.setattr(upstream, "fetch_player", throttled)
    _, client = app_client
    response = await client.get(f"/v1/pv/{PLAYER_UUID}")

    assert response.status_code == 503
    assert response.headers["Retry-After"] == "9"
    assert response.json()["error"] == {
        "code": "UPSTREAM_TEMPORARY_FAILURE",
        "message": "The authenticated Hypixel request budget is temporarily exhausted.",
        "retryable": True,
        "retryAfterSeconds": 9,
    }


@pytest.mark.asyncio
async def test_health_readiness_openapi_and_error_envelope(app_client) -> None:
    _, client = app_client
    assert (await client.get("/health")).status_code == 200
    ready = await client.get("/ready")
    assert ready.status_code == 200
    assert ready.json()["data"]["dependencies"]["sqlite"] == "ok"

    spec = (await client.get("/openapi.json")).json()
    assert "/v1/pv/{target}" in spec["paths"]
    assert "/v1/market/prices" in spec["paths"]
    assert "/v1/market/bazaar/shards" in spec["paths"]

    invalid = await client.get("/v1/pv/!!")
    assert invalid.status_code == 422
    assert set(invalid.json()) == {"schemaVersion", "error"}


@pytest.mark.asyncio
async def test_production_readiness_requires_the_authenticated_hypixel_key(
    app_client, settings
) -> None:
    _, client = app_client
    settings.environment = "production"
    settings.hypixel_api_key = "   "

    response = await client.get("/ready")
    assert response.status_code == 503
    assert response.json()["error"] == {
        "code": "NOT_READY",
        "message": "The authenticated Hypixel integration is not configured.",
        "retryable": False,
    }


def test_nginx_template_discards_request_scoped_logs_with_pv_identities() -> None:
    config = (
        Path(__file__).resolve().parents[1]
        / "deploy"
        / "nginx-api.qcloudy.net.conf"
    ).read_text(encoding="utf-8")
    assert config.count("access_log off;") == 2
    assert config.count("error_log /dev/null crit;") == 2

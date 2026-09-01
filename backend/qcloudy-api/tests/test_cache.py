from __future__ import annotations

import asyncio
import logging
import threading

import pytest
from redis.exceptions import RedisError

from app.cache import CacheStore, MemoryBackend
from app.errors import UpstreamTemporaryError


@pytest.mark.asyncio
async def test_cache_fresh_stale_and_negative_states() -> None:
    now = [1_000.0]
    cache = CacheStore(prefix="cache-test:", clock=lambda: now[0])
    calls = 0

    async def load() -> dict[str, int]:
        nonlocal calls
        calls += 1
        return {"value": calls}

    first = await cache.get_or_load("key", fresh_ttl=10, stale_ttl=30, loader=load)
    second = await cache.get_or_load("key", fresh_ttl=10, stale_ttl=30, loader=load)
    assert first.value == second.value == {"value": 1}
    assert calls == 1

    now[0] += 11

    async def unavailable() -> None:
        raise UpstreamTemporaryError("offline")

    stale = await cache.get_or_load(
        "key", fresh_ttl=10, stale_ttl=30, loader=unavailable
    )
    assert stale.value == {"value": 1}
    assert stale.metadata.state == "stale"

    negative_calls = 0

    async def missing() -> None:
        nonlocal negative_calls
        negative_calls += 1
        return None

    negative = await cache.get_or_load(
        "missing",
        fresh_ttl=100,
        stale_ttl=200,
        negative_ttl=15,
        loader=missing,
    )
    again = await cache.get_or_load(
        "missing",
        fresh_ttl=100,
        stale_ttl=200,
        negative_ttl=15,
        loader=missing,
    )
    assert negative.value is again.value is None
    assert negative_calls == 1

    replacement = await cache.get_or_load(
        "replace-positive",
        fresh_ttl=10,
        stale_ttl=30,
        negative_ttl=15,
        loader=load,
    )
    assert replacement.value is not None
    now[0] += 11
    replaced = await cache.get_or_load(
        "replace-positive",
        fresh_ttl=10,
        stale_ttl=30,
        negative_ttl=15,
        loader=missing,
    )
    assert replaced.value is None
    assert replaced.metadata.state == "negative"


@pytest.mark.asyncio
async def test_cache_coalesces_concurrent_loads() -> None:
    cache = CacheStore(prefix="singleflight-test:")
    calls = 0

    async def load() -> str:
        nonlocal calls
        calls += 1
        await asyncio.sleep(0.01)
        return "loaded"

    results = await asyncio.gather(
        *(cache.get_or_load("same", fresh_ttl=60, stale_ttl=120, loader=load) for _ in range(8))
    )
    assert [result.value for result in results] == ["loaded"] * 8
    assert calls == 1
    assert cache._singleflight == {}


@pytest.mark.asyncio
async def test_memory_cache_prunes_expiry_and_enforces_lru_capacity() -> None:
    now = [1_000.0]
    memory = MemoryBackend(
        clock=lambda: now[0],
        max_entries=2,
        max_bytes=100,
    )
    await memory.set("a", "aaaa", 10)
    await memory.set("b", "bbbb", 10)
    assert await memory.get("a") == "aaaa"

    await memory.set("c", "cccc", 10)
    assert await memory.get("b") is None
    assert await memory.get("a") == "aaaa"
    assert await memory.get("c") == "cccc"

    now[0] += 11
    await memory.set("d", "dddd", 10)
    assert set(memory._values) == {"d"}
    assert memory._total_bytes == 4


@pytest.mark.asyncio
async def test_memory_cache_enforces_utf8_byte_budget_and_skips_oversized_values() -> None:
    memory = MemoryBackend(max_entries=10, max_bytes=6)
    await memory.set("first", "1234", 60)
    await memory.set("second", "5678", 60)
    assert await memory.get("first") is None
    assert await memory.get("second") == "5678"
    assert memory._total_bytes == 4

    await memory.set("oversized", "猫猫猫", 60)
    assert await memory.get("oversized") is None
    assert memory._total_bytes == 4


@pytest.mark.asyncio
async def test_oversized_replacement_preserves_the_previous_cache_value() -> None:
    memory = MemoryBackend(max_entries=10, max_bytes=6)
    await memory.set("snapshot", "old", 60)
    await memory.set("snapshot", "too-large", 60)
    assert await memory.get("snapshot") == "old"
    assert memory._total_bytes == 3


@pytest.mark.asyncio
async def test_json_encoding_and_decoding_run_off_the_event_loop(monkeypatch) -> None:
    cache = CacheStore(prefix="json-thread-test:")
    event_loop_thread = threading.get_ident()
    worker_threads: dict[str, list[int]] = {"dumps": [], "loads": []}

    from app import cache as cache_module

    original_dumps = cache_module.json.dumps
    original_loads = cache_module.json.loads

    def observed_dumps(*args, **kwargs):
        worker_threads["dumps"].append(threading.get_ident())
        return original_dumps(*args, **kwargs)

    def observed_loads(*args, **kwargs):
        worker_threads["loads"].append(threading.get_ident())
        return original_loads(*args, **kwargs)

    monkeypatch.setattr(cache_module.json, "dumps", observed_dumps)
    monkeypatch.setattr(cache_module.json, "loads", observed_loads)

    value = {"rows": [{"itemId": "TEST", "price": index} for index in range(100)]}
    await cache.set_json("snapshot", value, ttl_seconds=60)
    assert await cache.get_json("snapshot") == value
    assert worker_threads["dumps"]
    assert worker_threads["loads"]
    assert all(thread_id != event_loop_thread for thread_id in worker_threads["dumps"])
    assert all(thread_id != event_loop_thread for thread_id in worker_threads["loads"])


@pytest.mark.asyncio
async def test_singleflight_entries_are_reclaimed_after_unique_loads_and_errors() -> None:
    cache = CacheStore(prefix="singleflight-reclaim:")

    async def load() -> str:
        return "loaded"

    for index in range(100):
        result = await cache.get_or_load(
            f"unique:{index}", fresh_ttl=60, stale_ttl=120, loader=load
        )
        assert result.value == "loaded"
    assert cache._singleflight == {}

    async def unavailable() -> None:
        raise UpstreamTemporaryError("offline")

    with pytest.raises(UpstreamTemporaryError):
        await cache.get_or_load(
            "error", fresh_ttl=60, stale_ttl=120, loader=unavailable
        )
    assert cache._singleflight == {}


@pytest.mark.asyncio
async def test_redis_delete_failure_log_does_not_reveal_the_cache_key(caplog) -> None:
    sensitive_key = "hypixel:player:" + "a" * 32

    class BrokenRedis:
        async def delete(self, key: str) -> None:
            raise RedisError("delete unavailable")

    cache = CacheStore(prefix="privacy-test:")
    cache._redis = BrokenRedis()
    with caplog.at_level(logging.WARNING):
        await cache.delete(sensitive_key)
    assert "Redis delete failed" in caplog.text
    assert sensitive_key not in caplog.text

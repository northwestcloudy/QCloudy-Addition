from __future__ import annotations

import asyncio
import heapq
import json
import logging
import time
import uuid
from collections import OrderedDict
from contextlib import asynccontextmanager
from dataclasses import asdict, dataclass
from typing import Any, AsyncIterator, Awaitable, Callable

from redis.asyncio import Redis
from redis.exceptions import RedisError

from .errors import UpstreamTemporaryError

LOGGER = logging.getLogger(__name__)


@dataclass(slots=True)
class CacheRecord:
    value: Any
    fetched_at: float
    fresh_until: float
    stale_until: float
    negative: bool = False


@dataclass(slots=True)
class CacheMetadata:
    state: str
    fetched_at: float
    fresh_until: float
    stale_until: float

    def to_dict(self) -> dict[str, Any]:
        return {
            "state": self.state,
            "fetchedAt": int(self.fetched_at * 1000),
            "freshUntil": int(self.fresh_until * 1000),
            "staleUntil": int(self.stale_until * 1000),
            "stale": self.state == "stale",
            "ageSeconds": max(0, int(time.time() - self.fetched_at)),
        }

    def to_source(self, source_version: str | int | None = None) -> dict[str, Any]:
        status = "stale" if self.state == "stale" else "fresh"
        return {
            "status": status,
            "fetchedAt": int(self.fetched_at * 1000),
            "expiresAt": int(self.fresh_until * 1000),
            "staleUntil": int(self.stale_until * 1000),
            "nextRefreshAt": int(self.fresh_until * 1000),
            "sourceVersion": source_version,
        }


@dataclass(slots=True)
class CacheResult:
    value: Any
    metadata: CacheMetadata


@dataclass(slots=True)
class _SingleflightEntry:
    lock: asyncio.Lock
    users: int = 0


class MemoryBackend:
    def __init__(
        self,
        clock: Callable[[], float] = time.time,
        *,
        max_entries: int = 4096,
        max_bytes: int = 128 * 1024 * 1024,
    ):
        self._clock = clock
        self._max_entries = max(1, max_entries)
        self._max_bytes = max(1, max_bytes)
        self._values: OrderedDict[str, tuple[str, float, int, int]] = OrderedDict()
        self._expirations: list[tuple[float, int, str]] = []
        self._generation = 0
        self._total_bytes = 0
        self._mutex = asyncio.Lock()

    def _remove(self, key: str) -> None:
        item = self._values.pop(key, None)
        if item is not None:
            self._total_bytes -= item[3]

    def _prune_expired(self, now: float) -> None:
        while self._expirations and self._expirations[0][0] <= now:
            _, generation, key = heapq.heappop(self._expirations)
            item = self._values.get(key)
            if item is not None and item[2] == generation:
                self._remove(key)

    def _compact_expirations(self) -> None:
        if len(self._expirations) <= max(self._max_entries * 2, len(self._values) * 4):
            return
        self._expirations = [
            (expires_at, generation, key)
            for key, (_, expires_at, generation, _) in self._values.items()
        ]
        heapq.heapify(self._expirations)

    async def get(self, key: str) -> str | None:
        async with self._mutex:
            self._prune_expired(self._clock())
            item = self._values.get(key)
            if item is None:
                return None
            value = item[0]
            self._values.move_to_end(key)
            return value

    async def set(
        self,
        key: str,
        value: str,
        ttl_seconds: int,
        *,
        size_bytes: int | None = None,
    ) -> None:
        size = (
            size_bytes
            if size_bytes is not None
            else await asyncio.to_thread(lambda: len(value.encode("utf-8")))
        )
        async with self._mutex:
            now = self._clock()
            self._prune_expired(now)
            if size > self._max_bytes:
                # A rejected replacement must not destroy the last usable
                # value. Market publication relies on this fail-closed
                # behavior when a new snapshot exceeds the local budget.
                return
            self._remove(key)
            self._generation += 1
            generation = self._generation
            expires_at = now + max(1, ttl_seconds)
            self._values[key] = (value, expires_at, generation, size)
            self._total_bytes += size
            heapq.heappush(self._expirations, (expires_at, generation, key))
            while (
                len(self._values) > self._max_entries
                or self._total_bytes > self._max_bytes
            ):
                oldest_key = next(iter(self._values))
                self._remove(oldest_key)
            self._compact_expirations()

    async def delete(self, key: str) -> None:
        async with self._mutex:
            self._remove(key)

    async def ping(self) -> bool:
        return True


class CacheStore:
    """Redis-first cache with a process-local fallback and request coalescing."""

    def __init__(
        self,
        *,
        redis_url: str | None = None,
        prefix: str = "qca:v1:",
        clock: Callable[[], float] = time.time,
        memory_max_entries: int = 4096,
        memory_max_bytes: int = 128 * 1024 * 1024,
    ):
        self._clock = clock
        self._prefix = prefix
        self._memory = MemoryBackend(
            clock,
            max_entries=memory_max_entries,
            max_bytes=memory_max_bytes,
        )
        self._redis = Redis.from_url(redis_url, decode_responses=True) if redis_url else None
        self._singleflight: dict[str, _SingleflightEntry] = {}
        self._singleflight_guard = asyncio.Lock()
        self._local_distributed_locks: dict[str, asyncio.Lock] = {}

    def _key(self, key: str) -> str:
        return f"{self._prefix}{key}"

    async def _get_text(self, key: str) -> str | None:
        full_key = self._key(key)
        if self._redis is not None:
            try:
                value = await self._redis.get(full_key)
                if value is not None:
                    await self._memory.set(full_key, value, 60)
                    return value
            except RedisError as exc:
                LOGGER.warning("Redis read failed; using memory fallback: %s", exc)
        return await self._memory.get(full_key)

    async def _set_text(
        self,
        key: str,
        value: str,
        ttl_seconds: int,
        *,
        encoded_value: bytes | None = None,
    ) -> None:
        full_key = self._key(key)
        await self._memory.set(
            full_key,
            value,
            ttl_seconds,
            size_bytes=len(encoded_value) if encoded_value is not None else None,
        )
        if self._redis is not None:
            try:
                await self._redis.set(
                    full_key,
                    encoded_value if encoded_value is not None else value,
                    ex=max(1, ttl_seconds),
                )
            except RedisError as exc:
                LOGGER.warning("Redis write failed; value retained in memory: %s", exc)

    async def get_json(self, key: str) -> Any | None:
        value = await self._get_text(key)
        if value is None:
            return None
        # Market snapshots can be many megabytes. Decoding them on the sole
        # Uvicorn event loop would stall unrelated profile and health requests.
        return await asyncio.to_thread(json.loads, value)

    async def set_json(self, key: str, value: Any, ttl_seconds: int) -> None:
        def encode() -> tuple[str, bytes]:
            text = json.dumps(
                value,
                separators=(",", ":"),
                ensure_ascii=False,
            )
            return text, text.encode("utf-8")

        encoded, encoded_bytes = await asyncio.to_thread(encode)
        await self._set_text(
            key,
            encoded,
            ttl_seconds,
            encoded_value=encoded_bytes,
        )

    async def delete(self, key: str) -> None:
        full_key = self._key(key)
        await self._memory.delete(full_key)
        if self._redis is not None:
            try:
                await self._redis.delete(full_key)
            except RedisError:
                LOGGER.warning("Redis delete failed")

    async def exists(self, key: str) -> bool:
        """Check cache presence without decoding a potentially large value."""
        full_key = self._key(key)
        if self._redis is not None:
            try:
                if await self._redis.exists(full_key):
                    return True
            except RedisError as exc:
                LOGGER.warning(
                    "Redis existence check failed; using memory fallback: %s", exc
                )
        return await self._memory.get(full_key) is not None

    async def _record(self, key: str) -> CacheRecord | None:
        payload = await self.get_json(key)
        if payload is None:
            return None
        try:
            return CacheRecord(**payload)
        except (TypeError, ValueError):
            await self.delete(key)
            return None

    async def _write_record(self, key: str, record: CacheRecord) -> None:
        ttl = max(1, int(record.stale_until - self._clock()))
        await self.set_json(key, asdict(record), ttl)

    @asynccontextmanager
    async def _singleflight_for(self, key: str) -> AsyncIterator[None]:
        async with self._singleflight_guard:
            entry = self._singleflight.get(key)
            if entry is None:
                entry = _SingleflightEntry(asyncio.Lock())
                self._singleflight[key] = entry
            entry.users += 1
        acquired = False
        try:
            await entry.lock.acquire()
            acquired = True
            yield
        finally:
            if acquired:
                entry.lock.release()
            async with self._singleflight_guard:
                entry.users -= 1
                if entry.users == 0 and self._singleflight.get(key) is entry:
                    self._singleflight.pop(key, None)

    @staticmethod
    def _metadata(record: CacheRecord, state: str) -> CacheMetadata:
        return CacheMetadata(
            state=state,
            fetched_at=record.fetched_at,
            fresh_until=record.fresh_until,
            stale_until=record.stale_until,
        )

    async def get_or_load(
        self,
        key: str,
        *,
        fresh_ttl: int,
        stale_ttl: int,
        loader: Callable[[], Awaitable[Any]],
        negative_ttl: int | None = None,
    ) -> CacheResult:
        now = self._clock()
        record = await self._record(key)
        if record is not None and record.fresh_until > now:
            state = "negative" if record.negative else "fresh"
            return CacheResult(record.value, self._metadata(record, state))

        async with self._singleflight_for(key):
            now = self._clock()
            record = await self._record(key)
            if record is not None and record.fresh_until > now:
                state = "negative" if record.negative else "fresh"
                return CacheResult(record.value, self._metadata(record, state))
            try:
                value = await loader()
            except UpstreamTemporaryError:
                if record is not None and not record.negative and record.stale_until > now:
                    return CacheResult(record.value, self._metadata(record, "stale"))
                raise

            fetched_at = self._clock()
            negative = value is None and negative_ttl is not None
            effective_fresh = negative_ttl if negative else fresh_ttl
            effective_stale = negative_ttl if negative else stale_ttl
            new_record = CacheRecord(
                value=value,
                fetched_at=fetched_at,
                fresh_until=fetched_at + effective_fresh,
                stale_until=fetched_at + effective_stale,
                negative=negative,
            )
            await self._write_record(key, new_record)
            return CacheResult(value, self._metadata(new_record, "negative" if negative else "miss"))

    @asynccontextmanager
    async def distributed_lock(self, name: str, ttl_seconds: int) -> AsyncIterator[bool]:
        full_key = self._key(f"lock:{name}")
        token = uuid.uuid4().hex
        if self._redis is not None:
            try:
                acquired = bool(await self._redis.set(full_key, token, nx=True, ex=ttl_seconds))
                if not acquired:
                    yield False
                    return
                try:
                    yield True
                finally:
                    script = (
                        "if redis.call('get', KEYS[1]) == ARGV[1] then "
                        "return redis.call('del', KEYS[1]) else return 0 end"
                    )
                    await self._redis.eval(script, 1, full_key, token)
                return
            except RedisError as exc:
                LOGGER.warning("Redis lock unavailable; using process-local lock: %s", exc)

        lock = self._local_distributed_locks.setdefault(name, asyncio.Lock())
        if lock.locked():
            yield False
            return
        await lock.acquire()
        try:
            yield True
        finally:
            lock.release()

    async def health(self) -> dict[str, Any]:
        redis_status = "disabled"
        if self._redis is not None:
            try:
                redis_status = "ok" if await self._redis.ping() else "unavailable"
            except RedisError:
                redis_status = "unavailable"
        return {"memory": "ok", "redis": redis_status}

    async def close(self) -> None:
        if self._redis is not None:
            await self._redis.aclose()

from __future__ import annotations

import asyncio
import logging
import math
import random
import time
from collections.abc import Awaitable, Callable
from typing import Any

from .cache import CacheStore
from .config import Settings
from .errors import ApiProblem, SnapshotConsistencyError
from .pricing import (
    aggregate_active_auctions,
    ended_sale,
    normalize_bazaar,
)
from .storage import MarketStorage
from .upstream import HypixelUpstream

LOGGER = logging.getLogger(__name__)


class MarketManager:
    BAZAAR_KEY = "market:bazaar"
    BAZAAR_META_KEY = "market:bazaar:metadata"
    AUCTIONS_KEY = "market:auctions"
    AUCTIONS_META_KEY = "market:auctions:metadata"
    ITEMS_KEY = "resources:items"

    def __init__(
        self,
        settings: Settings,
        cache: CacheStore,
        storage: MarketStorage,
        upstream: HypixelUpstream,
    ):
        self.settings = settings
        self.cache = cache
        self.storage = storage
        self.upstream = upstream
        self._tasks: list[asyncio.Task[None]] = []
        self._stop = asyncio.Event()
        self._status: dict[str, dict[str, Any]] = {
            name: {"lastSuccessAt": None, "lastError": None}
            for name in ("bazaar", "auctions", "ended", "items")
        }

    @staticmethod
    def _snapshot_header(snapshot: dict[str, Any]) -> dict[str, Any]:
        return {
            "sourceLastUpdated": int(snapshot.get("sourceLastUpdated") or 0),
            "fetchedAt": int(snapshot.get("fetchedAt") or 0),
        }

    async def _published_header(
        self, snapshot_key: str, metadata_key: str
    ) -> dict[str, Any] | None:
        metadata = await self.cache.get_json(metadata_key)
        if isinstance(metadata, dict):
            return metadata
        # Backward-compatible migration path for snapshots published before the
        # small metadata key existed. The next collector pass publishes both.
        snapshot = await self.cache.get_json(snapshot_key)
        return self._snapshot_header(snapshot) if isinstance(snapshot, dict) else None

    async def _available_header(
        self, snapshot_key: str, metadata_key: str
    ) -> dict[str, Any] | None:
        header = await self._published_header(snapshot_key, metadata_key)
        if header is None:
            return None
        # Lightweight metadata remains useful as the monotonic source
        # watermark, but it must not claim reader availability after the memory
        # LRU has evicted the matching large payload.
        return header if await self.cache.exists(snapshot_key) else None

    async def _publish_snapshot(
        self,
        snapshot_key: str,
        metadata_key: str,
        snapshot: dict[str, Any],
        *,
        ttl_seconds: int,
    ) -> None:
        # Publish the value first. A reader racing this two-key update may see
        # the previous metadata briefly, which is conservative and cannot label
        # an older payload as a newer source version.
        await self.cache.set_json(snapshot_key, snapshot, ttl_seconds=ttl_seconds)
        published = await self.cache.get_json(snapshot_key)
        expected_header = self._snapshot_header(snapshot)
        if (
            not isinstance(published, dict)
            or self._snapshot_header(published) != expected_header
        ):
            # CacheStore can intentionally reject an oversized in-process
            # value. Never advance the lightweight metadata unless the matching
            # full snapshot can actually be read back (from Redis or memory).
            raise SnapshotConsistencyError(
                "The market snapshot could not be retained by the cache."
            )
        await self.cache.set_json(
            metadata_key,
            expected_header,
            ttl_seconds=ttl_seconds,
        )
        if not await self.cache.exists(snapshot_key):
            # On a very small memory-only cache, inserting the metadata itself
            # can evict a near-budget snapshot. Remove the now-orphaned marker
            # and expose the collector failure instead of false availability.
            await self.cache.delete(metadata_key)
            raise SnapshotConsistencyError(
                "The market snapshot was evicted while publishing metadata."
            )

    async def start(self) -> None:
        if self._tasks:
            return
        self._stop.clear()
        schedules = (
            ("bazaar", self.settings.bazaar_interval_seconds, self.collect_bazaar),
            ("auctions", self.settings.auction_interval_seconds, self.collect_auctions),
            ("ended", self.settings.ended_interval_seconds, self.collect_ended),
            ("items", 6 * 60 * 60, self.collect_items),
        )
        self._tasks = [
            asyncio.create_task(self._periodic(name, interval, collector), name=f"qca-{name}")
            for name, interval, collector in schedules
        ]

    async def stop(self) -> None:
        self._stop.set()
        for task in self._tasks:
            task.cancel()
        if self._tasks:
            await asyncio.gather(*self._tasks, return_exceptions=True)
        self._tasks.clear()

    async def _periodic(
        self, name: str, interval: int, collector: Callable[[], Awaitable[Any]]
    ) -> None:
        while not self._stop.is_set():
            started = time.monotonic()
            try:
                async with self.cache.distributed_lock(f"collector:{name}", max(30, interval)) as acquired:
                    if acquired:
                        result = await collector()
                        # The ended endpoint commonly repeats one 60-second
                        # generation. Only a generation advance represents new
                        # coverage and may move its success watermark.
                        if not (
                            isinstance(result, dict)
                            and result.get("advanced") is False
                        ):
                            self._status[name] = {
                                "lastSuccessAt": int(time.time() * 1000),
                                "lastError": None,
                            }
            except asyncio.CancelledError:
                raise
            except Exception as exc:  # Collector errors must not stop later cycles.
                LOGGER.warning("%s collector failed: %s", name, exc)
                self._status[name]["lastError"] = type(exc).__name__
            remaining = max(1.0, interval - (time.monotonic() - started))
            remaining += random.uniform(0.0, min(5.0, interval * 0.05))
            try:
                await asyncio.wait_for(self._stop.wait(), timeout=remaining)
            except TimeoutError:
                pass

    async def collect_bazaar(self) -> dict[str, Any]:
        payload = await self.upstream.fetch_bazaar()
        snapshot = normalize_bazaar(payload, int(time.time() * 1000))
        previous_header = await self._published_header(
            self.BAZAAR_KEY, self.BAZAAR_META_KEY
        )
        if (
            previous_header is not None
            and int(previous_header.get("sourceLastUpdated") or 0)
            >= snapshot["sourceLastUpdated"]
        ):
            previous = await self.cache.get_json(self.BAZAAR_KEY)
            if isinstance(previous, dict):
                return previous
            if (
                int(previous_header.get("sourceLastUpdated") or 0)
                > snapshot["sourceLastUpdated"]
            ):
                raise SnapshotConsistencyError(
                    "Refusing to replace a newer Bazaar watermark with older data."
                )
        await self._publish_snapshot(
            self.BAZAAR_KEY,
            self.BAZAAR_META_KEY,
            snapshot,
            ttl_seconds=self.settings.bazaar_stale_seconds,
        )
        return snapshot

    @staticmethod
    def _validate_page(
        payload: dict[str, Any],
        *,
        expected_page: int,
        last_updated: int,
        total_pages: int,
        total_auctions: int,
    ) -> list[dict[str, Any]]:
        if int(payload.get("page", -1)) != expected_page:
            raise SnapshotConsistencyError("An auction page returned the wrong page number.")
        if int(payload.get("lastUpdated") or -1) != last_updated:
            raise SnapshotConsistencyError()
        if int(payload.get("totalPages") or -1) != total_pages:
            raise SnapshotConsistencyError("Auction totalPages changed during collection.")
        if int(payload.get("totalAuctions") or -1) != total_auctions:
            raise SnapshotConsistencyError("Auction totalAuctions changed during collection.")
        auctions = payload.get("auctions")
        if not isinstance(auctions, list):
            raise SnapshotConsistencyError("An auction page had no auctions list.")
        return [auction for auction in auctions if isinstance(auction, dict)]

    @staticmethod
    def _build_auction_snapshot(
        pages: dict[int, list[dict[str, Any]]],
        *,
        total_pages: int,
        total_auctions: int,
        last_updated: int,
        fetched_at_ms: int,
    ) -> dict[str, Any]:
        """Deduplicate, bound and aggregate one consistent AH generation.

        This work is intentionally synchronous so the caller can move the
        entire CPU- and memory-heavy pass off the event loop in one thread.
        """

        by_uuid: dict[str, dict[str, Any]] = {}
        encoded_characters = 0
        for page in range(total_pages):
            for auction in pages[page]:
                auction_uuid = auction.get("uuid")
                if not isinstance(auction_uuid, str) or not auction_uuid:
                    continue
                by_uuid[auction_uuid] = auction
        if len(by_uuid) != total_auctions:
            raise SnapshotConsistencyError(
                "Auction pages did not contain the advertised number of unique auctions."
            )
        if len(by_uuid) > 500_000:
            raise SnapshotConsistencyError("Auction snapshot exceeded the safety item limit.")
        for auction in by_uuid.values():
            encoded = auction.get("item_bytes")
            if isinstance(encoded, dict):
                encoded = encoded.get("data")
            if isinstance(encoded, str):
                encoded_characters += len(encoded)
            if encoded_characters > 512 * 1024 * 1024:
                raise SnapshotConsistencyError("Auction item data exceeded the safety limit.")
        return aggregate_active_auctions(
            list(by_uuid.values()),
            source_last_updated=last_updated,
            fetched_at_ms=fetched_at_ms,
            total_auctions=total_auctions,
        )

    async def collect_auctions(self) -> dict[str, Any]:
        first = await self.upstream.fetch_auction_page(0)
        try:
            last_updated = int(first["lastUpdated"])
            total_pages = int(first["totalPages"])
            total_auctions = int(first["totalAuctions"])
        except (KeyError, TypeError, ValueError) as exc:
            raise SnapshotConsistencyError("Auction page zero had invalid metadata.") from exc
        if total_pages < 1 or total_pages > 1000:
            raise SnapshotConsistencyError("Auction totalPages is outside the safety limit.")

        previous_header = await self._published_header(
            self.AUCTIONS_KEY, self.AUCTIONS_META_KEY
        )
        if (
            previous_header is not None
            and int(previous_header.get("sourceLastUpdated") or 0) > last_updated
        ):
            previous = await self.cache.get_json(self.AUCTIONS_KEY)
            if isinstance(previous, dict):
                return previous
            raise SnapshotConsistencyError(
                "Refusing to replace a newer auction watermark with older data."
            )

        pages: dict[int, list[dict[str, Any]]] = {
            0: self._validate_page(
                first,
                expected_page=0,
                last_updated=last_updated,
                total_pages=total_pages,
                total_auctions=total_auctions,
            )
        }
        semaphore = asyncio.Semaphore(max(1, self.settings.auction_page_concurrency))

        async def fetch(page: int) -> tuple[int, list[dict[str, Any]]]:
            async with semaphore:
                payload = await self.upstream.fetch_auction_page(page)
            return page, self._validate_page(
                payload,
                expected_page=page,
                last_updated=last_updated,
                total_pages=total_pages,
                total_auctions=total_auctions,
            )

        if total_pages > 1:
            for page, auctions in await asyncio.gather(
                *(fetch(page) for page in range(1, total_pages))
            ):
                pages[page] = auctions
        if set(pages) != set(range(total_pages)):
            raise SnapshotConsistencyError("One or more auction pages were missing.")

        final_page_zero = await self.upstream.fetch_auction_page(0)
        self._validate_page(
            final_page_zero,
            expected_page=0,
            last_updated=last_updated,
            total_pages=total_pages,
            total_auctions=total_auctions,
        )

        if (
            previous_header is not None
            and int(previous_header.get("sourceLastUpdated") or 0) == last_updated
        ):
            previous = await self.cache.get_json(self.AUCTIONS_KEY)
            if isinstance(previous, dict):
                return previous

        snapshot = await asyncio.to_thread(
            self._build_auction_snapshot,
            pages,
            total_pages=total_pages,
            total_auctions=total_auctions,
            last_updated=last_updated,
            fetched_at_ms=int(time.time() * 1000),
        )
        await self._publish_snapshot(
            self.AUCTIONS_KEY,
            self.AUCTIONS_META_KEY,
            snapshot,
            ttl_seconds=self.settings.auction_stale_seconds,
        )
        return snapshot

    async def collect_ended(self) -> dict[str, Any]:
        payload = await self.upstream.fetch_ended_auctions()
        auctions = payload.get("auctions")
        if not isinstance(auctions, list):
            raise ValueError("Ended-auctions payload has no auctions list")
        try:
            source_last_updated = int(payload["lastUpdated"])
        except (KeyError, TypeError, ValueError) as exc:
            raise ValueError("Ended-auctions payload has invalid lastUpdated") from exc
        if source_last_updated <= 0:
            raise ValueError("Ended-auctions payload has invalid lastUpdated")

        now_ms = int(time.time() * 1000)
        previous_source_raw = await self.storage.get_state("ended_source_last_updated")
        previous_raw = await self.storage.get_state("ended_last_success_wall_ms")
        previous_source = int(previous_source_raw) if previous_source_raw is not None else None
        if previous_source is not None and source_last_updated <= previous_source:
            # Do not move the wall-clock success marker for a duplicate or
            # regressed generation. coverage_current will naturally become
            # false if the upstream remains frozen beyond its 60-second window.
            return {
                "received": len(auctions),
                "validSales": 0,
                "inserted": 0,
                "advanced": False,
                "sourceLastUpdated": source_last_updated,
            }

        if previous_raw is not None:
            previous_ms = int(previous_raw)
            threshold_ms = self.settings.ended_gap_threshold_seconds * 1000
            if now_ms - previous_ms > threshold_ms:
                await self.storage.record_coverage_gap(
                    previous_ms,
                    now_ms,
                    "collector unavailable longer than upstream 60-second window",
                )

        if await self.storage.get_state("ended_history_started_wall_ms") is None:
            await self.storage.set_state("ended_history_started_wall_ms", str(now_ms))

        sales = await asyncio.to_thread(
            lambda: [
                sale
                for auction in auctions
                if isinstance(auction, dict)
                if (sale := ended_sale(auction))
            ]
        )
        inserted = await self.storage.insert_sales(sales)
        await self.storage.set_state("ended_last_success_wall_ms", str(now_ms))
        await self.storage.set_state(
            "ended_source_last_updated", str(source_last_updated)
        )

        last_prune_raw = await self.storage.get_state("ended_last_prune_wall_ms")
        if last_prune_raw is None or now_ms - int(last_prune_raw) >= 24 * 60 * 60 * 1000:
            await self.storage.prune_sales(self.settings.ended_sales_retention_days)
            await self.storage.set_state("ended_last_prune_wall_ms", str(now_ms))
        return {
            "received": len(auctions),
            "validSales": len(sales),
            "inserted": inserted,
            "advanced": True,
            "sourceLastUpdated": source_last_updated,
        }

    async def collect_items(self) -> dict[str, Any]:
        payload = await self.upstream.fetch_item_resources()
        if not isinstance(payload.get("items"), list):
            raise ValueError("Item resources payload has no items list")
        await self.cache.set_json(self.ITEMS_KEY, payload, ttl_seconds=14 * 24 * 60 * 60)
        return payload

    @staticmethod
    def _snapshot_meta(snapshot: dict[str, Any], stale_after_seconds: int) -> dict[str, Any]:
        age_seconds = max(0, int((time.time() * 1000 - int(snapshot["fetchedAt"])) / 1000))
        return {
            "sourceLastUpdated": snapshot.get("sourceLastUpdated"),
            "fetchedAt": snapshot.get("fetchedAt"),
            "ageSeconds": age_seconds,
            "stale": age_seconds > stale_after_seconds,
        }

    async def bazaar_snapshot(self) -> tuple[dict[str, Any], dict[str, Any]]:
        snapshot = await self.cache.get_json(self.BAZAAR_KEY)
        if not isinstance(snapshot, dict):
            raise ApiProblem(503, "BAZAAR_NOT_READY", "Bazaar data is not ready.", retryable=True)
        return snapshot, self._snapshot_meta(snapshot, self.settings.bazaar_interval_seconds)

    async def auction_snapshot(self) -> tuple[dict[str, Any], dict[str, Any]]:
        snapshot = await self.cache.get_json(self.AUCTIONS_KEY)
        if not isinstance(snapshot, dict):
            raise ApiProblem(503, "AUCTIONS_NOT_READY", "Auction data is not ready.", retryable=True)
        return snapshot, self._snapshot_meta(snapshot, self.settings.auction_interval_seconds)

    async def prices(
        self, requests: list[tuple[str, str | None]]
    ) -> list[dict[str, Any]]:
        normalized = [(item_id.upper(), variant_key) for item_id, variant_key in requests]
        bazaar: dict[str, Any] | None = None
        bazaar_meta: dict[str, Any] | None = None
        try:
            bazaar, bazaar_meta = await self.bazaar_snapshot()
        except ApiProblem:
            pass

        auctions: dict[str, Any] | None = None
        auction_meta: dict[str, Any] | None = None
        try:
            auctions, auction_meta = await self.auction_snapshot()
        except ApiProblem:
            pass

        stats = await self.storage.sale_statistics_batch(
            normalized,
            window_seconds=(24 * 60 * 60, 7 * 24 * 60 * 60),
            max_coverage_lag_seconds=self.settings.ended_gap_threshold_seconds,
        )
        results: list[dict[str, Any]] = []
        semantics = {
            "instantBuyPrice": "coins paid to acquire immediately from Bazaar",
            "instantSellPrice": "coins received when liquidating immediately to Bazaar",
            "lowestBin": "lowest current BIN unit listing; not a completed sale",
            "robustListingPrice": "median of up to five lowest current BIN unit listings",
            "salesMedian": "median unit price of observed completed auctions in the requested window",
        }
        for item_id, variant_key in normalized:
            result: dict[str, Any] = {
                "itemId": item_id,
                "variantKey": variant_key,
                "semantics": semantics,
            }
            if bazaar is not None and bazaar_meta is not None:
                product = bazaar.get("products", {}).get(item_id)
                if isinstance(product, dict):
                    result["bazaar"] = {**product, **bazaar_meta}
            if auctions is not None and auction_meta is not None:
                variants = auctions.get("prices", {}).get(item_id, {})
                listing = variants.get(variant_key) if variant_key is not None else None
                # Variant-less callers intentionally request the cheapest base
                # item. An explicit unknown variant must never borrow another
                # variant's price.
                if variant_key is None and isinstance(variants, dict) and variants:
                    candidates = [
                        value
                        for value in variants.values()
                        if isinstance(value, dict)
                        and isinstance(value.get("lowestBin"), (int, float))
                    ]
                    if candidates:
                        listing = min(candidates, key=lambda value: value["lowestBin"])
                if isinstance(listing, dict):
                    result["auction"] = {**listing, **auction_meta}
            windows = stats[(item_id, variant_key)]
            result["sales24h"] = windows[24 * 60 * 60]
            result["sales7d"] = windows[7 * 24 * 60 * 60]
            if (
                not any(key in result for key in ("bazaar", "auction"))
                and result["sales7d"]["sampleCount"] == 0
            ):
                result["confidence"] = "UNKNOWN"
            elif (
                result["sales7d"]["sampleCount"] >= 5
                and result["sales7d"]["coverageComplete"]
            ):
                result["confidence"] = "HIGH"
            else:
                result["confidence"] = "LOW"
            results.append(result)
        return results

    async def price(self, item_id: str, variant_key: str | None = None) -> dict[str, Any]:
        return (await self.prices([(item_id, variant_key)]))[0]

    async def status(self) -> dict[str, Any]:
        return {
            "collectors": self._status,
            "bazaarAvailable": (
                await self._available_header(self.BAZAAR_KEY, self.BAZAAR_META_KEY)
                is not None
            ),
            "auctionsAvailable": (
                await self._available_header(self.AUCTIONS_KEY, self.AUCTIONS_META_KEY)
                is not None
            ),
            "endedLastSuccessAt": await self.storage.get_state("ended_last_success_wall_ms"),
        }

    async def source_metadata(self) -> dict[str, Any]:
        bazaar = await self._available_header(self.BAZAAR_KEY, self.BAZAAR_META_KEY)
        auctions = await self._available_header(
            self.AUCTIONS_KEY, self.AUCTIONS_META_KEY
        )
        ended_coverage = await self.storage.coverage_status(
            7 * 24 * 60 * 60,
            max_coverage_lag_seconds=self.settings.ended_gap_threshold_seconds,
        )
        snapshots = [value for value in (bazaar, auctions) if value is not None]
        if not snapshots:
            source = empty_source("error")
            source.update(
                {
                    "coverageComplete": False,
                    "confidence": "low",
                    "endedCoverageComplete": bool(
                        ended_coverage.get("coverageComplete")
                    ),
                    "endedCoverageThrough": ended_coverage.get("coverageThrough"),
                }
            )
            return source
        fetched_at = min(int(snapshot.get("fetchedAt") or 0) for snapshot in snapshots)
        expires_at = min(
            int(bazaar.get("fetchedAt") or 0) + self.settings.bazaar_interval_seconds * 1000
            if bazaar is not None
            else 0,
            int(auctions.get("fetchedAt") or 0) + self.settings.auction_interval_seconds * 1000
            if auctions is not None
            else 0,
        )
        stale_until = min(
            int(bazaar.get("fetchedAt") or 0) + self.settings.bazaar_stale_seconds * 1000
            if bazaar is not None
            else 0,
            int(auctions.get("fetchedAt") or 0) + self.settings.auction_stale_seconds * 1000
            if auctions is not None
            else 0,
        )
        now_ms = int(time.time() * 1000)
        snapshot_coverage_complete = bazaar is not None and auctions is not None
        if not snapshot_coverage_complete or now_ms > stale_until:
            status = "error"
        elif now_ms > expires_at:
            status = "stale"
        else:
            status = "fresh"
        ended_version = str(await self.storage.get_state("ended_source_last_updated") or "0")
        coverage_complete = (
            snapshot_coverage_complete
            and status != "error"
            and ended_coverage.get("coverageComplete") is True
        )
        return {
            "status": status,
            "fetchedAt": fetched_at,
            "expiresAt": expires_at or None,
            "staleUntil": stale_until,
            "nextRefreshAt": expires_at or None,
            "sourceVersion": (
                f"bazaar:{str(bazaar.get('sourceLastUpdated') or 'missing') if bazaar else 'missing'}"
                f"|auctions:{str(auctions.get('sourceLastUpdated') or 'missing') if auctions else 'missing'}"
                f"|ended:{ended_version}"
            ),
            "coverageComplete": coverage_complete,
            "confidence": (
                "high" if status == "fresh" and coverage_complete else "low"
            ),
            "endedCoverageComplete": bool(
                ended_coverage.get("coverageComplete")
            ),
            "endedCoverageThrough": ended_coverage.get("coverageThrough"),
        }

    def bazaar_source_metadata(self, snapshot: dict[str, Any]) -> dict[str, Any]:
        fetched_at = int(snapshot.get("fetchedAt") or 0)
        expires_at = fetched_at + self.settings.bazaar_interval_seconds * 1000
        stale_until = fetched_at + self.settings.bazaar_stale_seconds * 1000
        now_ms = int(time.time() * 1000)
        return {
            "status": "stale" if now_ms > expires_at else "fresh",
            "fetchedAt": fetched_at,
            "expiresAt": expires_at,
            "staleUntil": stale_until,
            "nextRefreshAt": expires_at,
            "sourceVersion": str(snapshot.get("sourceLastUpdated") or ""),
            "ageSeconds": max(0, int((now_ms - fetched_at) / 1000)),
            "stale": now_ms > expires_at,
        }


def empty_source(status: str = "not_requested") -> dict[str, Any]:
    return {
        "status": status,
        "fetchedAt": None,
        "expiresAt": None,
        "staleUntil": None,
        "nextRefreshAt": None,
        "sourceVersion": None,
    }

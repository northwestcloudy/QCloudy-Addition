from __future__ import annotations

import asyncio
import hashlib
import json
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
    variant_key_for_pet,
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

    @staticmethod
    def _portfolio_holdings(
        sections: dict[str, Any], limit: int, *, projection_complete: bool = True
    ) -> tuple[list[dict[str, Any]], bool]:
        holdings: dict[tuple[str, str | None], dict[str, Any]] = {}
        truncated = not projection_complete

        inventory_section = sections.get("inventory")
        if not isinstance(inventory_section, dict):
            truncated = True
        elif inventory_section.get("status") not in (None, "available"):
            truncated = True
        inventory = (
            inventory_section.get("payload")
            if isinstance(inventory_section, dict)
            else None
        )
        if not isinstance(inventory, dict):
            truncated = True
        if isinstance(inventory, dict):
            for container_name, container in inventory.items():
                if not isinstance(container, dict):
                    continue
                if (
                    "decodeStatus" in container
                    and container.get("decodeStatus") != "decoded"
                ):
                    truncated = True
                raw_items = container.get("items")
                if not isinstance(raw_items, list):
                    continue
                for item in raw_items:
                    if not isinstance(item, dict):
                        continue
                    item_id = item.get("itemId")
                    if not isinstance(item_id, str) or not item_id:
                        continue
                    variant_key = item.get("variantKey")
                    if not isinstance(variant_key, str):
                        variant_key = None
                    count = item.get("count", 1)
                    if isinstance(count, bool) or not isinstance(count, (int, float)):
                        count = 1
                    count = max(1, min(int(count), 2_147_483_647))
                    key = (item_id.upper(), variant_key)
                    if key not in holdings and len(holdings) >= limit:
                        truncated = True
                        continue
                    current = holdings.setdefault(
                        key,
                        {
                            "itemId": key[0],
                            "variantKey": variant_key,
                            "count": 0,
                            "kind": "item",
                            "containers": [],
                            **(
                                {
                                    "priceEligible": False,
                                    "unpriceableReason": "pet_level_unresolved",
                                }
                                if key[0] == "PET"
                                else {}
                            ),
                        },
                    )
                    current["count"] += count
                    if container_name not in current["containers"]:
                        current["containers"].append(container_name)

        pets_section = sections.get("pets")
        pets_payload = pets_section.get("payload") if isinstance(pets_section, dict) else None
        pets = pets_payload.get("pets") if isinstance(pets_payload, dict) else None
        if isinstance(pets, list):
            for pet in pets:
                if not isinstance(pet, dict) or not isinstance(pet.get("type"), str):
                    continue
                variant_key = variant_key_for_pet(pet)
                key = ("PET", variant_key)
                if key not in holdings and len(holdings) >= limit:
                    truncated = True
                    continue
                current = holdings.setdefault(
                    key,
                    {
                        "itemId": "PET",
                        "variantKey": variant_key,
                        "count": 0,
                        "kind": "pet",
                        "petType": pet["type"],
                        "containers": ["pets"],
                        # Pet experience/level is not yet represented in the
                        # stable AH variant fingerprint. Pricing this aggregate
                        # would mix level 1 and level 100 pets, so v1 fails
                        # closed instead of showing a misleading value.
                        "priceEligible": False,
                        "unpriceableReason": "pet_level_unresolved",
                    },
                )
                current["count"] += 1
        return list(holdings.values()), truncated

    @staticmethod
    def _choose_unit_price(price: dict[str, Any]) -> tuple[float | None, str | None]:
        bazaar = price.get("bazaar")
        if isinstance(bazaar, dict) and isinstance(
            bazaar.get("instantSellPrice"), (int, float)
        ):
            return float(bazaar["instantSellPrice"]), "bazaar_instant_sell"
        sales24h = price.get("sales24h")
        if (
            isinstance(sales24h, dict)
            and isinstance(sales24h.get("median"), (int, float))
            and sales24h.get("coverageComplete") is True
            and int(sales24h.get("sampleCount") or 0) >= 5
        ):
            return float(sales24h["median"]), "recent_sale_24h"
        sales7d = price.get("sales7d")
        if (
            isinstance(sales7d, dict)
            and isinstance(sales7d.get("median"), (int, float))
            and sales7d.get("coverageComplete") is True
            and int(sales7d.get("sampleCount") or 0) >= 5
        ):
            return float(sales7d["median"]), "recent_sale_7d"
        auction = price.get("auction")
        if (
            isinstance(auction, dict)
            and isinstance(auction.get("robustListingPrice"), (int, float))
            and auction.get("parseQuality") == "exact"
            and int(auction.get("listingCount") or 0) >= 3
        ):
            return float(auction["robustListingPrice"]), "ah_robust_listing"
        return None, None

    @staticmethod
    def _valuation_confidence(price: dict[str, Any], method: str | None) -> str:
        if method == "bazaar_instant_sell":
            bazaar = price.get("bazaar")
            return (
                "high"
                if isinstance(bazaar, dict) and bazaar.get("stale") is False
                else "low"
            )
        if method in {"recent_sale_24h", "recent_sale_7d"}:
            key = "sales24h" if method == "recent_sale_24h" else "sales7d"
            window = price.get(key)
            if (
                isinstance(window, dict)
                and window.get("coverageComplete") is True
                and int(window.get("sampleCount") or 0) >= 5
            ):
                return "high"
            return "low"
        if method == "ah_robust_listing":
            auction = price.get("auction")
            if (
                isinstance(auction, dict)
                and auction.get("stale") is False
                and auction.get("parseQuality") == "exact"
                and int(auction.get("listingCount") or 0) >= 3
            ):
                return "high"
            return "low"
        return "unknown"

    @staticmethod
    def _coin_balances(sections: dict[str, Any]) -> dict[str, float | None]:
        overview_section = sections.get("overview")
        overview = (
            overview_section.get("payload")
            if isinstance(overview_section, dict)
            else None
        )
        overview = overview if isinstance(overview, dict) else {}
        currencies = overview.get("currencies")
        currencies = currencies if isinstance(currencies, dict) else {}
        profile = overview.get("profile")
        profile = profile if isinstance(profile, dict) else {}
        banking = profile.get("banking")
        banking = banking if isinstance(banking, dict) else {}

        def amount(*values: Any) -> float | None:
            for value in values:
                if isinstance(value, bool) or not isinstance(value, (int, float)):
                    continue
                number = float(value)
                if math.isfinite(number) and number >= 0:
                    return number
            return None

        purse = amount(currencies.get("coin_purse"), currencies.get("coins"))
        bank = amount(banking.get("balance"))
        known = [value for value in (purse, bank) if value is not None]
        return {
            "purse": purse,
            "bank": bank,
            "total": sum(known) if known else None,
            "complete": purse is not None and bank is not None,
        }

    async def portfolio(
        self,
        sections: dict[str, Any],
        *,
        holdings_projection_complete: bool = True,
    ) -> tuple[dict[str, Any], dict[str, Any]]:
        """Value decoded holdings from already-published snapshots only."""
        holdings, truncated = self._portfolio_holdings(
            sections,
            self.settings.pv_market_item_limit,
            projection_complete=holdings_projection_complete,
        )
        coin_balances = self._coin_balances(sections)
        source = await self.profile_source()

        def public_holding(holding: dict[str, Any]) -> dict[str, Any]:
            return {
                key: value
                for key, value in holding.items()
                if key != "priceEligible"
            }

        if source["status"] == "error":
            return (
                {
                    "pricedItems": 0,
                    "unknownItems": len(holdings),
                    "pricedUnits": 0,
                    "unknownUnits": sum(item["count"] for item in holdings),
                    "instantSellNetWorth": None,
                    "estimatedNetWorth": None,
                    "knownEstimatedValue": None,
                    "itemEstimatedValue": None,
                    "coinBalances": coin_balances,
                    "estimateComplete": False,
                    "truncated": truncated,
                    "perItem": [
                        {
                            **public_holding(item),
                            "unitPrice": None,
                            "totalPrice": None,
                            "instantSellUnitPrice": None,
                            "instantSellTotal": None,
                            "method": None,
                            "confidence": "unknown",
                        }
                        for item in holdings
                    ],
                },
                source,
            )

        fingerprint = hashlib.sha256(
            json.dumps(
                {
                    "holdings": holdings,
                    "coinBalances": coin_balances,
                    "truncated": truncated,
                    "sourceVersion": source.get("sourceVersion"),
                    "sourceStatus": source.get("status"),
                    "sourceCoverageComplete": source.get("coverageComplete"),
                    "sourceConfidence": source.get("confidence"),
                    "coverageThrough": source.get("endedCoverageThrough"),
                },
                sort_keys=True,
                separators=(",", ":"),
                ensure_ascii=True,
            ).encode()
        ).hexdigest()
        cache_key = f"derived:portfolio:{fingerprint}"
        cached = await self.cache.get_json(cache_key)
        if isinstance(cached, dict):
            return cached, source

        eligible_holdings = [
            holding for holding in holdings if holding.get("priceEligible") is not False
        ]
        eligible_prices = (
            await self.prices(
                [
                    (holding["itemId"], holding.get("variantKey"))
                    for holding in eligible_holdings
                ]
            )
            if eligible_holdings
            else []
        )
        price_iterator = iter(eligible_prices)
        price_rows: list[dict[str, Any] | None] = [
            next(price_iterator) if holding.get("priceEligible") is not False else None
            for holding in holdings
        ]

        def value_holding(
            holding: dict[str, Any], price: dict[str, Any] | None
        ) -> dict[str, Any]:
            exposed = public_holding(holding)
            if price is None:
                return {
                    **exposed,
                    "unitPrice": None,
                    "totalPrice": None,
                    "instantSellUnitPrice": None,
                    "instantSellTotal": None,
                    "method": None,
                    "confidence": "unknown",
                }
            unit_price, method = self._choose_unit_price(price)
            count = holding["count"]
            bazaar = price.get("bazaar")
            instant_sell_unit = (
                float(bazaar["instantSellPrice"])
                if isinstance(bazaar, dict)
                and isinstance(bazaar.get("instantSellPrice"), (int, float))
                else None
            )
            confidence = self._valuation_confidence(price, method)
            if (
                method in {"recent_sale_24h", "recent_sale_7d", "ah_robust_listing"}
                and holding.get("variantKey") is None
            ):
                # Variant-less market queries deliberately span every variant,
                # so they cannot safely value one concrete held item.
                unit_price = None
                method = None
                confidence = "unknown"
            return {
                **exposed,
                "unitPrice": unit_price,
                "totalPrice": unit_price * count if unit_price is not None else None,
                "instantSellUnitPrice": instant_sell_unit,
                "instantSellTotal": (
                    instant_sell_unit * count if instant_sell_unit is not None else None
                ),
                "method": method,
                "confidence": confidence,
            }

        per_item = [
            value_holding(holding, price)
            for holding, price in zip(holdings, price_rows, strict=True)
        ]
        priced = [item for item in per_item if item["totalPrice"] is not None]
        liquid = [item for item in per_item if item["instantSellTotal"] is not None]
        item_estimate = sum(item["totalPrice"] for item in priced) if priced else None
        coin_total = coin_balances["total"]
        known_components = [
            value for value in (item_estimate, coin_total) if value is not None
        ]
        known_profile_value = sum(known_components) if known_components else None
        payload = {
            "pricedItems": len(priced),
            "unknownItems": len(per_item) - len(priced),
            "pricedUnits": sum(item["count"] for item in priced),
            "unknownUnits": sum(
                item["count"] for item in per_item if item["totalPrice"] is None
            ),
            "instantSellNetWorth": (
                sum(item["instantSellTotal"] for item in liquid) if liquid else None
            ),
            "estimatedNetWorth": known_profile_value,
            "knownEstimatedValue": known_profile_value,
            "itemEstimatedValue": item_estimate,
            "coinBalances": coin_balances,
            "estimateComplete": (
                bool(per_item)
                and not truncated
                and len(priced) == len(per_item)
                and bool(source.get("coverageComplete"))
                and coin_balances["complete"] is True
                and all(item["confidence"] == "high" for item in priced)
            ),
            "truncated": truncated,
            "perItem": per_item,
        }
        await self.cache.set_json(
            cache_key,
            payload,
            ttl_seconds=max(1, min(30, self.settings.ended_interval_seconds)),
        )
        return payload, source

    async def profile_source(self) -> dict[str, Any]:
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

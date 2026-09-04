from __future__ import annotations

import base64
import gzip
import sqlite3
import struct
import threading
import time

import pytest

from app.errors import SnapshotConsistencyError
from app.cache import CacheStore
from app.market import MarketManager
from app.pricing import aggregate_active_auctions, ended_sale
from app.storage import EndedSale


def _named(tag_type: int, name: str, payload: bytes) -> bytes:
    encoded = name.encode()
    return bytes([tag_type]) + struct.pack(">H", len(encoded)) + encoded + payload


def _string(value: str) -> bytes:
    encoded = value.encode()
    return struct.pack(">H", len(encoded)) + encoded


def _auction_blob(item_id: str, count: int) -> str:
    extra = _named(8, "id", _string(item_id)) + b"\x00"
    tag = _named(10, "ExtraAttributes", extra) + b"\x00"
    item = _named(1, "Count", struct.pack(">b", count)) + _named(10, "tag", tag) + b"\x00"
    root = bytes([10, 0, 0]) + _named(9, "i", bytes([10]) + struct.pack(">i", 1) + item) + b"\x00"
    return base64.b64encode(gzip.compress(root)).decode()


@pytest.mark.asyncio
async def test_bazaar_semantics_and_shard_client_contract(app_client) -> None:
    app, client = app_client
    await app.state.services.market.collect_bazaar()
    response = await client.get("/v1/market/bazaar/shards?side=instant_buy")
    assert response.status_code == 200
    body = response.json()
    assert body["schemaVersion"] == 1
    assert body["side"] == "instant_buy"
    assert body["prices"] == {"SHARD_TEST": 125.5}
    assert set(body["metadata"]) >= {
        "status", "fetchedAt", "expiresAt", "staleUntil", "nextRefreshAt", "sourceVersion"
    }
    assert body["metadata"]["expiresAt"] - body["metadata"]["fetchedAt"] == 60_000
    assert body["metadata"]["staleUntil"] - body["metadata"]["fetchedAt"] == 600_000
    assert isinstance(body["metadata"]["sourceVersion"], str)

    sell = (await client.get("/v1/market/bazaar/shards?side=instant_sell")).json()
    assert sell["prices"] == {"SHARD_TEST": 100.25}


@pytest.mark.asyncio
async def test_auction_pages_are_published_only_as_one_consistent_snapshot(app_client) -> None:
    app, _ = app_client
    market = app.state.services.market
    snapshot = await market.collect_auctions()
    assert snapshot["totalAuctions"] == 2
    variants = snapshot["prices"]["TEST_BLADE"]
    only = next(iter(variants.values()))
    assert only["lowestBin"] == 1000
    assert only["robustListingPrice"] == 1100.0


@pytest.mark.asyncio
async def test_auction_version_drift_does_not_replace_previous_snapshot(
    app_client, upstream
) -> None:
    app, _ = app_client
    market: MarketManager = app.state.services.market
    original = await market.collect_auctions()
    upstream.auction_pages[1]["lastUpdated"] = 2001
    with pytest.raises(SnapshotConsistencyError):
        await market.collect_auctions()
    retained = await app.state.services.cache.get_json(market.AUCTIONS_KEY)
    assert retained == original


@pytest.mark.asyncio
async def test_auction_snapshot_never_rolls_back_to_an_older_consistent_generation(
    app_client, upstream
) -> None:
    app, _ = app_client
    market = app.state.services.market
    original = await market.collect_auctions()
    for page in upstream.auction_pages.values():
        page["lastUpdated"] = 1_999

    returned = await market.collect_auctions()
    retained = await app.state.services.cache.get_json(market.AUCTIONS_KEY)
    metadata = await app.state.services.cache.get_json(market.AUCTIONS_META_KEY)
    assert returned == retained == original
    assert metadata["sourceLastUpdated"] == 2_000


@pytest.mark.asyncio
async def test_orphaned_newer_watermark_still_blocks_auction_rollback(
    app_client, upstream
) -> None:
    app, _ = app_client
    market = app.state.services.market
    await market.collect_auctions()
    await market.cache.delete(market.AUCTIONS_KEY)
    for page in upstream.auction_pages.values():
        page["lastUpdated"] = 1_999

    with pytest.raises(SnapshotConsistencyError, match="newer auction watermark"):
        await market.collect_auctions()
    metadata = await market.cache.get_json(market.AUCTIONS_META_KEY)
    assert metadata["sourceLastUpdated"] == 2_000
    assert await market.cache.get_json(market.AUCTIONS_KEY) is None


@pytest.mark.asyncio
async def test_auction_snapshot_build_runs_off_the_event_loop(
    app_client, monkeypatch
) -> None:
    app, _ = app_client
    market = app.state.services.market
    event_loop_thread = threading.get_ident()
    worker_threads: list[int] = []
    original = market._build_auction_snapshot

    def observed(*args, **kwargs):
        worker_threads.append(threading.get_ident())
        return original(*args, **kwargs)

    monkeypatch.setattr(market, "_build_auction_snapshot", observed)
    await market.collect_auctions()
    assert worker_threads
    assert all(thread_id != event_loop_thread for thread_id in worker_threads)


@pytest.mark.asyncio
async def test_ended_sales_are_deduplicated_and_coverage_is_conservative(app_client) -> None:
    app, _ = app_client
    market = app.state.services.market
    first = await market.collect_ended()
    second = await market.collect_ended()
    assert first["inserted"] == 1
    assert second["inserted"] == 0

    stats = await app.state.services.storage.sale_statistics(
        "TEST_BLADE", None, window_seconds=24 * 60 * 60
    )
    assert stats["sampleCount"] == 1
    assert stats["coverageComplete"] is False

    old = int((time.time() - 2 * 24 * 60 * 60) * 1000)
    await app.state.services.storage.set_state("ended_history_started_wall_ms", str(old))
    complete = await app.state.services.storage.sale_statistics(
        "TEST_BLADE", None, window_seconds=24 * 60 * 60
    )
    assert complete["coverageComplete"] is True

    await app.state.services.storage.record_coverage_gap(old, int(time.time() * 1000), "test")
    incomplete = await app.state.services.storage.sale_statistics(
        "TEST_BLADE", None, window_seconds=24 * 60 * 60
    )
    assert incomplete["coverageComplete"] is False


@pytest.mark.asyncio
async def test_repeated_ended_generation_does_not_advance_coverage_watermark(
    app_client, upstream, monkeypatch
) -> None:
    app, _ = app_client
    market = app.state.services.market
    now = [1_000.0]
    monkeypatch.setattr(time, "time", lambda: now[0])

    first = await market.collect_ended()
    first_success = await app.state.services.storage.get_state(
        "ended_last_success_wall_ms"
    )
    await app.state.services.storage.set_state(
        "ended_history_started_wall_ms",
        str(int((now[0] - 2 * 24 * 60 * 60) * 1000)),
    )
    assert first["advanced"] is True

    now[0] += 70
    repeated = await market.collect_ended()
    assert repeated["advanced"] is False
    assert (
        await app.state.services.storage.get_state("ended_last_success_wall_ms")
        == first_success
    )
    frozen = await app.state.services.storage.coverage_status(24 * 60 * 60)
    assert frozen["coverageComplete"] is False

    upstream.ended_payload["lastUpdated"] += 1
    now[0] += 1
    recovered = await market.collect_ended()
    assert recovered["advanced"] is True
    after_recovery = await app.state.services.storage.coverage_status(24 * 60 * 60)
    assert after_recovery["coverageComplete"] is False


@pytest.mark.asyncio
async def test_sale_statistics_batch_uses_a_fixed_number_of_selects(
    app_client, monkeypatch
) -> None:
    app, _ = app_client
    storage = app.state.services.storage
    now_ms = int(time.time() * 1000)
    await storage.insert_sales(
        [
            EndedSale(
                auction_id="batch-a",
                ended_at_ms=now_ms - 30_000,
                item_id="BATCH_ITEM",
                variant_key="variant-a",
                price=100.0,
                is_bin=True,
                parse_quality="test",
            ),
            EndedSale(
                auction_id="batch-b",
                ended_at_ms=now_ms - 90_000,
                item_id="BATCH_ITEM",
                variant_key="variant-b",
                price=200.0,
                is_bin=True,
                parse_quality="test",
            ),
        ]
    )
    await storage.set_state(
        "ended_history_started_wall_ms", str(now_ms - 8 * 24 * 60 * 60 * 1000)
    )
    await storage.set_state("ended_last_success_wall_ms", str(now_ms))

    statements: list[str] = []

    def traced_connect() -> sqlite3.Connection:
        connection = sqlite3.connect(storage.path)
        connection.row_factory = sqlite3.Row
        connection.set_trace_callback(statements.append)
        connection.execute("PRAGMA journal_mode=WAL")
        connection.execute("PRAGMA foreign_keys=ON")
        connection.execute("PRAGMA busy_timeout=5000")
        return connection

    monkeypatch.setattr(storage, "_connect", traced_connect)
    requests = [
        ("BATCH_ITEM", None),
        ("BATCH_ITEM", "variant-a"),
        *((f"EMPTY_{index}", None) for index in range(254)),
    ]
    windows = (60, 60 * 60, 24 * 60 * 60, 7 * 24 * 60 * 60)
    result = await storage.sale_statistics_batch(
        requests,
        window_seconds=windows,
        max_coverage_lag_seconds=120,
    )

    reads = [
        statement
        for statement in statements
        if statement.lstrip().upper().startswith(("SELECT", "WITH"))
    ]
    assert len(requests) == 256
    assert len(reads) == 3
    assert sum("FROM service_state" in statement for statement in reads) == 1
    assert sum("FROM coverage_gaps" in statement for statement in reads) == 1
    assert sum("FROM ended_sales" in statement for statement in reads) == 1
    assert result[("BATCH_ITEM", None)][60]["sampleCount"] == 1
    assert result[("BATCH_ITEM", None)][60]["median"] == 100.0
    assert result[("BATCH_ITEM", None)][60 * 60]["sampleCount"] == 2
    assert result[("BATCH_ITEM", None)][60 * 60]["median"] == 150.0
    assert result[("BATCH_ITEM", "variant-a")][60]["sampleCount"] == 1
    assert result[("BATCH_ITEM", "variant-a")][60]["median"] == 100.0

    statements.clear()
    coverage = await storage.coverage_status(
        7 * 24 * 60 * 60,
        max_coverage_lag_seconds=120,
    )
    coverage_reads = [
        statement
        for statement in statements
        if statement.lstrip().upper().startswith(("SELECT", "WITH"))
    ]
    assert len(coverage_reads) == 2
    assert coverage == {
        key: result[("BATCH_ITEM", None)][7 * 24 * 60 * 60][key]
        for key in ("coverageComplete", "coverageThrough", "windowSeconds")
    }


@pytest.mark.asyncio
async def test_batch_price_labels_listing_and_sale_semantics(app_client) -> None:
    app, client = app_client
    await app.state.services.market.collect_bazaar()
    await app.state.services.market.collect_auctions()
    response = await client.post("/v1/market/prices", json={"items": [{"itemId": "shard_test"}]})
    assert response.status_code == 200
    item = response.json()["items"][0]
    assert item["bazaar"]["instantBuyPrice"] == 125.5
    assert "not a completed sale" in item["semantics"]["lowestBin"]


@pytest.mark.asyncio
async def test_explicit_unknown_variant_never_borrows_another_variant(app_client) -> None:
    app, _ = app_client
    market = app.state.services.market
    await market.collect_auctions()
    price = await market.price("TEST_BLADE", "variant-that-does-not-exist")
    assert "auction" not in price
    assert price["confidence"] == "UNKNOWN"


def test_auction_stack_prices_are_normalized_per_unit() -> None:
    encoded = _auction_blob("STACKED_ITEM", 64)
    active = aggregate_active_auctions(
        [{"uuid": "a", "bin": True, "starting_bid": 6_400, "item_bytes": encoded}],
        source_last_updated=1,
        fetched_at_ms=2,
        total_auctions=1,
    )
    listing = next(iter(active["prices"]["STACKED_ITEM"].values()))
    assert listing["lowestBin"] == 100.0
    assert listing["robustListingPrice"] == 100.0

    sale = ended_sale(
        {
            "auction_id": "ended-stack",
            "buyer": "b" * 32,
            "price": 6_400,
            "timestamp": 1_900_000_000_000,
            "bin": True,
            "item_bytes": encoded,
        }
    )
    assert sale is not None
    assert sale.price == 100.0


@pytest.mark.asyncio
async def test_market_source_requires_bazaar_and_auction_snapshots(app_client) -> None:
    app, _ = app_client
    market = app.state.services.market
    await market.collect_bazaar()
    source = await market.source_metadata()
    assert source["status"] == "error"
    assert source["coverageComplete"] is False
    assert "auctions:missing" in source["sourceVersion"]


async def _mark_seven_day_ended_coverage_complete(app) -> None:
    market = app.state.services.market
    await market.collect_ended()
    now_ms = int(time.time() * 1000)
    await app.state.services.storage.set_state(
        "ended_history_started_wall_ms",
        str(now_ms - 8 * 24 * 60 * 60 * 1000),
    )
    await app.state.services.storage.set_state(
        "ended_last_success_wall_ms", str(now_ms)
    )


@pytest.mark.asyncio
async def test_market_source_requires_continuous_ended_coverage(app_client) -> None:
    app, _ = app_client
    market = app.state.services.market
    await market.collect_bazaar()
    await market.collect_auctions()

    incomplete = await market.source_metadata()
    assert incomplete["status"] == "fresh"
    assert incomplete["endedCoverageComplete"] is False
    assert incomplete["coverageComplete"] is False
    assert incomplete["confidence"] == "low"

    await _mark_seven_day_ended_coverage_complete(app)
    complete = await market.source_metadata()
    assert complete["status"] == "fresh"
    assert complete["endedCoverageComplete"] is True
    assert complete["coverageComplete"] is True
    assert complete["confidence"] == "high"


@pytest.mark.asyncio
async def test_market_source_reads_small_metadata_instead_of_full_snapshots(
    app_client, monkeypatch
) -> None:
    app, _ = app_client
    market = app.state.services.market
    await market.collect_bazaar()
    await market.collect_auctions()

    requested_keys: list[str] = []
    original_get_json = market.cache.get_json

    async def observed(key: str):
        requested_keys.append(key)
        return await original_get_json(key)

    monkeypatch.setattr(market.cache, "get_json", observed)
    await market.source_metadata()
    assert market.BAZAAR_META_KEY in requested_keys
    assert market.AUCTIONS_META_KEY in requested_keys
    assert market.BAZAAR_KEY not in requested_keys
    assert market.AUCTIONS_KEY not in requested_keys


@pytest.mark.asyncio
async def test_orphaned_metadata_does_not_claim_snapshot_availability(
    app_client
) -> None:
    app, _ = app_client
    market = app.state.services.market
    await market.collect_bazaar()
    await market.collect_auctions()
    await market.cache.delete(market.AUCTIONS_KEY)

    source = await market.source_metadata()
    assert source["status"] == "error"
    assert source["coverageComplete"] is False
    assert "auctions:missing" in source["sourceVersion"]
    status = await market.status()
    assert status["auctionsAvailable"] is False


@pytest.mark.asyncio
async def test_snapshot_metadata_never_advances_when_full_value_is_not_retained(
    app_client
) -> None:
    app, _ = app_client
    market = app.state.services.market
    market.cache = CacheStore(prefix="tiny-market:", memory_max_bytes=256)
    old = {"sourceLastUpdated": 1, "fetchedAt": 1, "payload": "old"}
    await market._publish_snapshot("snapshot", "metadata", old, ttl_seconds=60)

    rejected = {
        "sourceLastUpdated": 2,
        "fetchedAt": 2,
        "payload": "x" * 1_000,
    }
    with pytest.raises(SnapshotConsistencyError):
        await market._publish_snapshot(
            "snapshot", "metadata", rejected, ttl_seconds=60
        )

    assert await market.cache.get_json("snapshot") == old
    assert await market.cache.get_json("metadata") == {
        "sourceLastUpdated": 1,
        "fetchedAt": 1,
    }


@pytest.mark.asyncio
async def test_snapshot_publication_fails_closed_if_metadata_evicts_the_payload(
    app_client
) -> None:
    app, _ = app_client
    market = app.state.services.market
    market.cache = CacheStore(prefix="metadata-eviction:", memory_max_bytes=256)
    snapshot = {
        "sourceLastUpdated": 1,
        "fetchedAt": 1,
        "payload": "x" * 190,
    }
    with pytest.raises(SnapshotConsistencyError, match="publishing metadata"):
        await market._publish_snapshot(
            "snapshot", "metadata", snapshot, ttl_seconds=60
        )
    assert await market.cache.get_json("metadata") is None
    assert await market.cache.get_json("snapshot") is None

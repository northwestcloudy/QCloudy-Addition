from __future__ import annotations

import base64
import gzip
import struct

import pytest

from app.nbt import summarize_inventory_nbt
from tests.conftest import PLAYER_UUID, PROFILE_ID


EXPECTED_SECTIONS = {
    "overview",
    "gear",
    "accessories",
    "pets",
    "inventory",
    "skills",
    "slayer",
    "minions",
    "bestiary",
    "collections",
    "mining",
    "crimson_isle",
    "rift",
    "misc",
    "museum",
    "garden",
    "market",
}


@pytest.mark.asyncio
async def test_pv_contract_and_cache(app_client, upstream) -> None:
    _, client = app_client
    profile = upstream.profiles_payload["profiles"][0]
    profile["dungeon"] = {"legacy": True}
    profile["members"][PLAYER_UUID]["dungeons"] = {
        "dungeon_types": {"catacombs": {"experience": 12345}}
    }
    upstream.player_payload["player"]["dungeons"] = {"unexpected": True}
    first = await client.get("/v1/pv/NorthwestCloudy")
    second = await client.get("/v1/pv/NorthwestCloudy")
    assert first.status_code == second.status_code == 200
    body = first.json()
    assert set(body) == {
        "schemaVersion",
        "partial",
        "identity",
        "profiles",
        "selectedProfileId",
        "sections",
        "sources",
    }
    assert body["schemaVersion"] == 1
    assert body["identity"] == {
        "uuid": PLAYER_UUID,
        "name": "NorthwestCloudy",
        "query": "NorthwestCloudy",
        "skinTextureUrl": None,
    }
    assert body["selectedProfileId"] == PROFILE_ID
    assert set(body["sections"]) == EXPECTED_SECTIONS
    assert set(body["sources"]) == {
        "identity", "player", "profiles", "museum", "garden", "market"
    }
    for source in body["sources"].values():
        assert {
            "status", "fetchedAt", "expiresAt", "staleUntil", "nextRefreshAt", "sourceVersion"
        } <= set(source)
        assert source["status"] == source["status"].lower()
    misc = body["sections"]["misc"]["payload"]
    assert "rawMember" not in misc
    assert "rawProfile" not in misc
    assert "rawPlayer" not in misc
    assert not _contains_dungeon_key(body["sections"])
    # PV may read already-published market snapshots and SQLite sales only; a
    # player request must never launch the global market collectors.
    assert upstream.calls.get("bazaar", 0) == 0
    assert upstream.calls.get("auction:0", 0) == 0
    assert upstream.calls.get("ended", 0) == 0


def _contains_dungeon_key(value) -> bool:
    if isinstance(value, dict):
        return any(
            str(key).casefold() in {"dungeon", "dungeons"}
            or _contains_dungeon_key(child)
            for key, child in value.items()
        )
    if isinstance(value, list):
        return any(_contains_dungeon_key(child) for child in value)
    return False


@pytest.mark.asyncio
async def test_pv_rejects_non_hex_profile_id(app_client) -> None:
    _, client = app_client
    response = await client.get(f"/v1/pv/NorthwestCloudy?profileId={'z' * 32}")
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "INVALID_UUID"


@pytest.mark.asyncio
async def test_lazy_museum_and_garden_are_cached(app_client, upstream) -> None:
    _, client = app_client
    museum_url = f"/v1/pv/{PLAYER_UUID}/{PROFILE_ID}/museum"
    garden_url = f"/v1/pv/{PLAYER_UUID}/{PROFILE_ID}/garden"
    for _ in range(2):
        assert (await client.get(museum_url)).status_code == 200
        assert (await client.get(garden_url)).status_code == 200
    assert upstream.calls["museum"] == 1
    assert upstream.calls["garden"] == 1


@pytest.mark.asyncio
async def test_museum_is_member_scoped_bounded_and_contains_no_raw_blob(
    app_client, upstream
) -> None:
    _, client = app_client
    other_uuid = "c" * 32
    encoded = _inventory_blob("MUSEUM_ITEM")
    upstream.museum_payload = {
        "success": True,
        "members": {
            PLAYER_UUID: {
                "items": {
                    "MUSEUM_ITEM": [
                        {"item_data": {"type": 0, "data": encoded}}
                    ]
                }
            },
            other_uuid: {"secret": "must-not-leak"},
        },
    }
    response = await client.get(f"/v1/pv/{PLAYER_UUID}/{PROFILE_ID}/museum")
    assert response.status_code == 200
    raw = response.text
    assert encoded not in raw
    assert other_uuid not in raw
    assert "must-not-leak" not in raw
    assert len(response.content) < 4 * 1024 * 1024
    section = response.json()["sections"]["museum"]
    assert section["status"] == "available"
    summary = section["payload"]["member"]["items"]["MUSEUM_ITEM"][0]["item_data"]
    assert summary["decodeStatus"] == "decoded"
    assert summary["items"][0]["itemId"] == "MUSEUM_ITEM"


@pytest.mark.asyncio
async def test_supplement_rejects_profile_not_owned_by_player(app_client, upstream) -> None:
    _, client = app_client
    response = await client.get(f"/v1/pv/{'c' * 32}/{PROFILE_ID}/museum")
    assert response.status_code == 404
    assert response.json()["error"]["code"] == "PROFILE_NOT_FOUND"
    assert upstream.calls.get("museum", 0) == 0


@pytest.mark.asyncio
async def test_private_museum_and_missing_garden_use_negative_states(
    app_client, upstream
) -> None:
    _, client = app_client
    upstream.museum_payload = {"success": True, "members": {}}
    upstream.garden_payload = {"success": True, "garden": None}
    museum = (
        await client.get(f"/v1/pv/{PLAYER_UUID}/{PROFILE_ID}/museum")
    ).json()
    garden = (
        await client.get(f"/v1/pv/{PLAYER_UUID}/{PROFILE_ID}/garden")
    ).json()
    assert museum["sections"]["museum"]["status"] == "private"
    assert museum["sources"]["museum"]["status"] == "private"
    assert garden["sections"]["garden"]["status"] == "not_found"
    assert garden["sources"]["garden"]["status"] == "not_found"


def _named(tag_type: int, name: str, payload: bytes) -> bytes:
    encoded = name.encode()
    return bytes([tag_type]) + struct.pack(">H", len(encoded)) + encoded + payload


def _string(value: str) -> bytes:
    encoded = value.encode()
    return struct.pack(">H", len(encoded)) + encoded


def _inventory_blob(item_id: str = "TEST_BLADE") -> str:
    extra = _named(8, "id", _string(item_id)) + _named(3, "upgrade_level", struct.pack(">i", 5)) + b"\x00"
    display = (
        _named(8, "Name", _string("\u00a76Test Blade"))
        + _named(9, "Lore", bytes([8]) + struct.pack(">i", 1) + _string("\u00a76LEGENDARY SWORD"))
        + b"\x00"
    )
    tag = _named(10, "display", display) + _named(10, "ExtraAttributes", extra) + b"\x00"
    item = (
        _named(1, "Slot", struct.pack(">b", 4))
        + _named(1, "Count", struct.pack(">b", 1))
        + _named(10, "tag", tag)
        + b"\x00"
    )
    root = bytes([10, 0, 0]) + _named(9, "i", bytes([10]) + struct.pack(">i", 1) + item) + b"\x00"
    return base64.b64encode(gzip.compress(root)).decode()


def test_inventory_nbt_is_reduced_to_bounded_item_summary() -> None:
    items = summarize_inventory_nbt(_inventory_blob())
    assert items == [
        {
            "slot": 4,
            "count": 1,
            "itemId": "TEST_BLADE",
            "displayName": "Test Blade",
            "rarity": "LEGENDARY",
            "extraAttributes": {"id": "TEST_BLADE", "upgrade_level": 5},
        }
    ]


@pytest.mark.asyncio
async def test_main_pv_values_inventory_from_published_market_without_collecting(
    app_client, upstream
) -> None:
    app, client = app_client
    member = upstream.profiles_payload["profiles"][0]["members"][PLAYER_UUID]
    member["inventory"] = {"inv_contents": {"data": _inventory_blob("SHARD_TEST")}}
    await app.state.services.market.collect_bazaar()
    await app.state.services.market.collect_auctions()
    bazaar_calls = upstream.calls["bazaar"]
    auction_calls = sum(
        count for name, count in upstream.calls.items() if name.startswith("auction:")
    )

    response = await client.get("/v1/pv/NorthwestCloudy")
    assert response.status_code == 200
    body = response.json()
    market = body["sections"]["market"]
    assert market["status"] == "available"
    assert market["payload"]["pricedItems"] == 1
    assert market["payload"]["unknownItems"] == 0
    assert market["payload"]["instantSellNetWorth"] == 100.25
    assert market["payload"]["estimatedNetWorth"] == 100.25
    assert market["payload"]["perItem"][0]["method"] == "bazaar_instant_sell"
    assert upstream.calls["bazaar"] == bazaar_calls
    assert sum(
        count for name, count in upstream.calls.items() if name.startswith("auction:")
    ) == auction_calls


@pytest.mark.asyncio
async def test_main_pv_succeeds_when_market_is_not_ready(app_client) -> None:
    _, client = app_client
    response = await client.get("/v1/pv/NorthwestCloudy")
    assert response.status_code == 200
    body = response.json()
    assert body["partial"] is True
    assert body["sources"]["market"]["status"] == "error"
    assert body["sections"]["market"]["status"] == "not_loaded"


@pytest.mark.asyncio
async def test_main_pv_is_partial_when_only_one_market_source_exists(app_client) -> None:
    app, client = app_client
    await app.state.services.market.collect_bazaar()
    response = await client.get("/v1/pv/NorthwestCloudy")
    assert response.status_code == 200
    body = response.json()
    assert body["partial"] is True
    assert body["sources"]["market"]["status"] == "error"
    assert body["sources"]["market"]["coverageComplete"] is False
    assert body["sections"]["market"]["status"] == "not_loaded"

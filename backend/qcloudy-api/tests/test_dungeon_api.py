from __future__ import annotations

import base64
import gzip
import struct

import pytest

from app.dungeon_service import catacombs_level
from app.nbt import summarize_inventory_nbt
from tests.conftest import PLAYER_UUID


def _named(tag_type: int, name: str, payload: bytes) -> bytes:
    encoded = name.encode()
    return bytes([tag_type]) + struct.pack(">H", len(encoded)) + encoded + payload


def _string(value: str) -> bytes:
    encoded = value.encode()
    return struct.pack(">H", len(encoded)) + encoded


def _inventory_blob(item_id: str, slot: int, name: str) -> str:
    extra = _named(8, "id", _string(item_id)) + b"\x00"
    display = (
        _named(8, "Name", _string(name))
        + _named(9, "Lore", bytes([8]) + struct.pack(">i", 1)
                 + _string("\u00a77Health: \u00a7a+100\nignored"))
        + b"\x00"
    )
    tag = _named(10, "display", display) + _named(10, "ExtraAttributes", extra) + b"\x00"
    item = (
        _named(1, "Slot", struct.pack(">b", slot))
        + _named(1, "Count", struct.pack(">b", 1))
        + _named(10, "tag", tag)
        + b"\x00"
    )
    root = bytes([10, 0, 0]) + _named(9, "i", bytes([10]) + struct.pack(">i", 1) + item) + b"\x00"
    return base64.b64encode(gzip.compress(root)).decode()


@pytest.mark.asyncio
async def test_dungeon_quick_view_contract_is_selected_profile_scoped_and_cached(
    app_client, upstream
) -> None:
    _, client = app_client
    upstream.player_payload["player"]["achievements"] = {
        "skyblock_treasure_hunter": 2432
    }
    member = upstream.profiles_payload["profiles"][0]["members"][PLAYER_UUID]
    member.update(
        {
            "dungeons": {
                "dungeon_types": {
                    "catacombs": {
                        "experience": 51_359_640,
                        "tier_completions": {"7": 100},
                    },
                    "master_catacombs": {
                        "tier_completions": {"7": 100},
                        "fastest_time_s_plus": {"7": 298_321},
                        "fastest_time": {"7": 340_000},
                    },
                },
                "player_classes": {
                    name: {"experience": xp}
                    for name, xp in {
                        "healer": 3_084_640,
                        "mage": 66_359_640,
                        "berserk": 39_359_640,
                        "archer": 85_359_640,
                        "tank": 4_149_640,
                    }.items()
                },
            },
            "accessory_bag_storage": {"highest_magical_power": 1330},
            "inventory": {
                "inv_armor": {"data": _inventory_blob(
                    "GOLDEN_NECRON_HEAD", 3, "\u00a76Ancient Golden Necron Head"
                )},
                "inv_contents": {"data": _inventory_blob(
                    "HYPERION", 0, "\u00a7dHyperion"
                )},
            },
            "pets_data": {
                "pets": [
                    {"type": "GOLDEN_DRAGON", "tier": "LEGENDARY", "exp": 1000,
                     "heldItem": "PET_ITEM_MINOS_RELIC", "active": True}
                ]
            },
        }
    )

    first = await client.get("/v1/dungeons/quick-view/NorthwestCloudy?floor=M7")
    second = await client.get("/v1/dungeons/quick-view/NorthwestCloudy?floor=M7")
    assert first.status_code == second.status_code == 200
    body = first.json()
    assert body["schemaVersion"] == 1
    assert body["identity"]["name"] == "NorthwestCloudy"
    assert body["catacombs"]["level"] == pytest.approx(40.0)
    assert set(body["classes"]) == {"healer", "mage", "berserk", "archer", "tank"}
    assert body["floor"] == {"id": "M7", "runs": 100, "fastestMs": 298_321}
    assert body["secrets"]["averagePerRun"] == pytest.approx(12.16)
    assert body["armor"][0]["itemId"] == "GOLDEN_NECRON_HEAD"
    assert body["armor"][0]["name"].startswith("\u00a76")
    assert body["weapons"]["witherBlade"]["present"] is True
    assert body["weapons"]["terminator"]["present"] is False
    assert body["pets"]["goldenDragon"]["present"] is True
    assert body["pets"]["enderDragon"]["present"] is False
    assert upstream.calls["player"] == 1
    assert upstream.calls["profiles"] == 1


def test_catacombs_level_and_tooltip_projection_are_bounded() -> None:
    assert catacombs_level(569_809_640) == 50
    assert catacombs_level(669_809_640) == 50
    item = summarize_inventory_nbt(_inventory_blob("HYPERION", 0, "\u00a7dHyperion"))[0]
    assert item["displayName"] == "Hyperion"
    assert item["formattedName"] == "\u00a7dHyperion"
    assert item["lore"] == ["\u00a77Health: \u00a7a+100 ignored"]


@pytest.mark.asyncio
async def test_dungeon_quick_view_rejects_invalid_floor_and_removed_pv_routes(
    app_client,
) -> None:
    _, client = app_client
    private_inventory = await client.get(
        "/v1/dungeons/quick-view/NorthwestCloudy?floor=F7"
    )
    assert private_inventory.status_code == 200
    assert private_inventory.json()["weapons"]["complete"] is False
    assert (await client.get("/v1/dungeons/quick-view/NorthwestCloudy?floor=F9")).status_code == 422
    assert (await client.get("/v1/pv/NorthwestCloudy")).status_code == 404

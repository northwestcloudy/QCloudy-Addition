from __future__ import annotations

import base64
import copy
import gzip
import struct

import pytest

from app.cache import CacheMetadata
from app.dungeon_service import DungeonQuickViewService, catacombs_level
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
                "ender_chest_contents": {},
                "backpack_contents": {},
                "personal_vault_contents": {},
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
    assert body["identity"]["queryName"] == "NorthwestCloudy"
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
    evidence = body["requirementsEvidence"]
    assert evidence["version"] == 1
    assert evidence["identity"] == {
        "queryName": "NorthwestCloudy",
        "uuid": PLAYER_UUID,
        "name": "NorthwestCloudy",
    }
    assert evidence["profile"] == {
        "id": "b" * 32,
        "selection": "SELECTED",
        "selectionCertain": True,
    }
    assert evidence["request"] == {
        "floor": "M7",
        "responseFloor": "M7",
        "floorMatches": True,
    }
    assert evidence["floorCompletions"] == {"state": "KNOWN", "value": 100}
    assert evidence["fastestCompletion"] == {
        "state": "KNOWN",
        "valueMs": 298_321,
        "kind": "ANY_COMPLETION",
    }
    assert evidence["averageSecrets"] == {
        "state": "UNAVAILABLE",
        "value": None,
        "numerator": 2432,
        "denominator": 200,
        "scope": "ACCOUNT_SECRETS_SELECTED_PROFILE_RUNS",
        "complete": False,
        "reason": "SCOPE_MISMATCH",
    }
    assert evidence["magicalPower"] == {
        "state": "KNOWN",
        "value": 1330,
        "kind": "HIGHEST",
    }
    assert evidence["weapons"]["complete"] is True
    assert evidence["weapons"]["witherBlade"] == {"state": "PRESENT"}
    assert evidence["weapons"]["terminator"] == {"state": "ABSENT"}
    assert evidence["pets"]["complete"] is True
    assert evidence["pets"]["goldenDragon"] == {"state": "PRESENT"}
    assert evidence["pets"]["enderDragon"] == {"state": "ABSENT"}
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
    body = private_inventory.json()
    assert body["weapons"]["complete"] is False
    assert body["requirementsEvidence"]["weapons"]["terminator"] == {
        "state": "UNAVAILABLE",
        "reason": "INVENTORY_INCOMPLETE",
    }
    assert (await client.get("/v1/dungeons/quick-view/NorthwestCloudy?floor=F9")).status_code == 422
    assert (await client.get("/v1/pv/NorthwestCloudy")).status_code == 404


@pytest.mark.asyncio
async def test_partial_or_failed_inventory_never_proves_weapon_absence(
    app_client, upstream
) -> None:
    _, client = app_client
    member = upstream.profiles_payload["profiles"][0]["members"][PLAYER_UUID]
    member["inventory"] = {
        "inv_contents": {"data": _inventory_blob("HYPERION", 0, "Hyperion")},
        "ender_chest_contents": {"data": "not-valid-nbt"},
        "backpack_contents": {},
        # personal_vault_contents is deliberately missing.
    }
    member["pets_data"] = {}

    response = await client.get("/v1/dungeons/quick-view/NorthwestCloudy?floor=F7")
    assert response.status_code == 200
    evidence = response.json()["requirementsEvidence"]["weapons"]
    assert evidence["complete"] is False
    assert evidence["containersFailed"] == ["ender_chest_contents"]
    assert evidence["containersMissing"] == ["personal_vault_contents"]
    assert evidence["witherBlade"] == {"state": "PRESENT"}
    assert evidence["terminator"]["state"] == "UNAVAILABLE"
    pets = response.json()["requirementsEvidence"]["pets"]
    assert pets["complete"] is False
    assert pets["goldenDragon"] == {
        "state": "UNAVAILABLE",
        "reason": "PETS_UNAVAILABLE",
    }
    assert pets["enderDragon"] == {
        "state": "UNAVAILABLE",
        "reason": "PETS_UNAVAILABLE",
    }


@pytest.mark.asyncio
async def test_fallback_profile_is_displayable_but_not_requirement_evidence(
    app_client, upstream
) -> None:
    _, client = app_client
    profile = upstream.profiles_payload["profiles"][0]
    profile["selected"] = False
    member = profile["members"][PLAYER_UUID]
    member["dungeons"] = {
        "dungeon_types": {
            "catacombs": {"tier_completions": {"7": 50}},
        }
    }
    member["accessory_bag_storage"] = {"highest_magical_power": 1000}

    response = await client.get("/v1/dungeons/quick-view/NorthwestCloudy?floor=F7")
    assert response.status_code == 200
    body = response.json()
    assert body["floor"]["runs"] == 50
    evidence = body["requirementsEvidence"]
    assert evidence["profile"]["selection"] == "LATEST_SAVE"
    assert evidence["profile"]["selectionCertain"] is False
    assert evidence["floorCompletions"] == {
        "state": "UNAVAILABLE",
        "value": None,
        "reason": "PROFILE_SELECTION_UNCERTAIN",
    }
    assert evidence["magicalPower"] == {
        "state": "UNAVAILABLE",
        "value": None,
        "reason": "PROFILE_SELECTION_UNCERTAIN",
        "kind": "HIGHEST",
    }
    assert evidence["pets"]["enderDragon"] == {
        "state": "UNAVAILABLE",
        "reason": "PROFILE_SELECTION_UNCERTAIN",
    }


@pytest.mark.asyncio
async def test_multiple_selected_profiles_are_displayable_but_selection_is_uncertain(
    app_client, upstream
) -> None:
    _, client = app_client
    first = upstream.profiles_payload["profiles"][0]
    first["members"][PLAYER_UUID]["last_save"] = 100
    second = copy.deepcopy(first)
    second["profile_id"] = "c" * 32
    second["members"][PLAYER_UUID]["last_save"] = 200
    second["members"][PLAYER_UUID]["dungeons"] = {
        "dungeon_types": {
            "catacombs": {"tier_completions": {"7": 77}},
        }
    }
    upstream.profiles_payload["profiles"].append(second)

    response = await client.get("/v1/dungeons/quick-view/NorthwestCloudy?floor=F7")
    assert response.status_code == 200
    body = response.json()
    assert body["floor"]["runs"] == 77
    evidence = body["requirementsEvidence"]
    assert evidence["profile"] == {
        "id": "c" * 32,
        "selection": "SELECTED",
        "selectionCertain": False,
    }
    assert evidence["floorCompletions"] == {
        "state": "UNAVAILABLE",
        "value": None,
        "reason": "PROFILE_SELECTION_UNCERTAIN",
    }


def test_stale_sources_make_numeric_and_ownership_evidence_unavailable() -> None:
    fresh = CacheMetadata("fresh", 100.0, 200.0, 300.0)
    stale = CacheMetadata("stale", 90.0, 95.0, 300.0)
    evidence = DungeonQuickViewService._requirements_evidence(
        query_name="NorthwestCloudy",
        player_uuid=PLAYER_UUID,
        name="NorthwestCloudy",
        profile_id="b" * 32,
        profile_selection="SELECTED",
        selection_certain=True,
        requested_floor="M7",
        returned_floor="M7",
        identity_metadata=fresh,
        player_metadata=fresh,
        profile_metadata=stale,
        inputs={
            "floorRuns": 100,
            "fastestMs": 300_000,
            "secretsNumerator": 2000,
            "secretsDenominator": 200,
            "magicalPower": 1500,
            "inventoryCoverage": {
                "complete": True,
                "containersExpected": [],
                "containersPresent": [],
                "containersDecoded": [],
                "containersFailed": [],
                "containersMissing": [],
                "truncated": False,
            },
            "witherBladePresent": True,
            "terminatorPresent": False,
            "petsComplete": True,
            "goldenDragonPresent": True,
            "enderDragonPresent": False,
        },
    )

    assert evidence["fresh"] is False
    assert evidence["sources"]["profile"]["status"] == "stale"
    assert evidence["floorCompletions"]["state"] == "UNAVAILABLE"
    assert evidence["floorCompletions"]["reason"] == "SOURCE_STALE"
    assert evidence["magicalPower"]["state"] == "UNAVAILABLE"
    assert evidence["weapons"]["witherBlade"] == {
        "state": "UNAVAILABLE",
        "reason": "SOURCE_STALE",
    }
    assert evidence["weapons"]["terminator"]["state"] == "UNAVAILABLE"
    assert evidence["pets"]["goldenDragon"]["state"] == "UNAVAILABLE"
    assert evidence["pets"]["enderDragon"]["state"] == "UNAVAILABLE"

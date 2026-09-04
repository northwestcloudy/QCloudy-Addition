from __future__ import annotations

import asyncio
import math
import re
import time
from typing import Any

from .cache import CacheMetadata, CacheResult, CacheStore
from .config import Settings
from .errors import ApiProblem
from .nbt import NbtDecodeError, summarize_inventory_nbt
from .upstream import HypixelUpstream


USERNAME = re.compile(r"^[A-Za-z0-9_]{3,16}$")
UUID_TEXT = re.compile(
    r"^(?:[0-9a-fA-F]{32}|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-"
    r"[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$"
)
FLOOR = re.compile(r"^(?:E|[FM][1-7])$")

# Cumulative Catacombs XP at levels 0 through 50. XP can continue to grow,
# but the displayed Catacombs and class level remains capped at 50.
CATA_XP = (
    0, 50, 125, 235, 395, 625, 955, 1425, 2095, 3045, 4385, 6275,
    8940, 12700, 17960, 25340, 35640, 50040, 70040, 97640, 135640,
    188140, 259640, 356640, 488640, 668640, 911640, 1239640, 1684640,
    2284640, 3084640, 4149640, 5559640, 7459640, 9959640, 13259640,
    17559640, 23159640, 30359640, 39359640, 51359640, 66359640,
    85359640, 109559640, 139559640, 177559640, 225559640, 295559640,
    360559640, 453559640, 569809640,
)
WITHER_BLADES = {"HYPERION", "ASTRAEA", "SCYLLA", "VALKYRIE"}


def normalize_uuid(value: str) -> str:
    compact = value.replace("-", "").lower()
    if len(compact) != 32 or any(c not in "0123456789abcdef" for c in compact):
        raise ApiProblem(422, "INVALID_UUID", "The UUID is invalid.", retryable=False)
    return compact


def _number(value: Any) -> float | None:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return None
    result = float(value)
    return result if math.isfinite(result) and result >= 0 else None


def catacombs_level(experience: Any) -> float | None:
    xp = _number(experience)
    if xp is None:
        return None
    if xp >= CATA_XP[-1]:
        return 50.0
    for level in range(len(CATA_XP) - 1):
        if CATA_XP[level] <= xp < CATA_XP[level + 1]:
            span = CATA_XP[level + 1] - CATA_XP[level]
            return level + (xp - CATA_XP[level]) / span
    return 0.0


def _encoded(value: Any) -> str | None:
    if isinstance(value, str):
        return value
    if isinstance(value, dict) and isinstance(value.get("data"), str):
        return value["data"]
    return None


def _items(inventory: dict[str, Any]) -> tuple[dict[str, list[dict[str, Any]]], bool]:
    decoded: dict[str, list[dict[str, Any]]] = {}
    complete = True
    for raw_key, value in list(inventory.items())[:64]:
        encoded = _encoded(value)
        if encoded is None:
            continue
        try:
            decoded[str(raw_key)] = summarize_inventory_nbt(encoded, max_items=512)
        except NbtDecodeError:
            decoded[str(raw_key)] = []
            complete = False
    # An entirely absent inventory object means the API field is private or
    # unavailable. It must not be interpreted as proof that the player lacks
    # a weapon.
    return decoded, complete and bool(decoded)


def _item_view(item: dict[str, Any] | None) -> dict[str, Any] | None:
    if not isinstance(item, dict):
        return None
    item_id = item.get("itemId")
    name = item.get("formattedName") or item.get("displayName") or item_id
    if not isinstance(item_id, str) or not item_id:
        return None
    lore = item.get("lore") if isinstance(item.get("lore"), list) else []
    return {
        "itemId": item_id[:128],
        "name": str(name or item_id)[:256],
        "lore": [str(line)[:512] for line in lore[:80]],
        "rarity": str(item.get("rarity") or "")[:32],
    }


def _armor(decoded: dict[str, list[dict[str, Any]]]) -> list[dict[str, Any] | None]:
    raw = decoded.get("inv_armor") or decoded.get("armor") or []
    by_slot = {int(item.get("slot", -1)): item for item in raw if isinstance(item, dict)}
    # Hypixel armor NBT slots are boots, leggings, chestplate, helmet.
    return [_item_view(by_slot.get(slot)) for slot in (3, 2, 1, 0)]


def _best_item(all_items: list[dict[str, Any]], accepted: set[str]) -> dict[str, Any] | None:
    matches = [item for item in all_items if item.get("itemId") in accepted]
    if not matches:
        return None
    matches.sort(key=lambda item: (len(item.get("lore") or []), item.get("displayName") or ""), reverse=True)
    return _item_view(matches[0])


def _pet_view(raw: dict[str, Any] | None, label: str) -> dict[str, Any] | None:
    if not isinstance(raw, dict):
        return None
    tier = str(raw.get("tier") or "").upper()
    exp = _number(raw.get("exp"))
    held = raw.get("heldItem") or raw.get("held_item")
    lore = [f"§7Rarity: §6{tier or 'Missing'}"]
    lore.append(f"§7XP: §b{int(exp):,}" if exp is not None else "§7XP: §cMissing")
    lore.append(f"§7Held Item: §e{held}" if held else "§7Held Item: §cMissing")
    if raw.get("active") is True:
        lore.append("§aActive Pet")
    return {
        "itemId": "PET",
        "name": f"§6{label}",
        "lore": lore,
        "rarity": tier,
    }


class DungeonQuickViewService:
    def __init__(self, settings: Settings, cache: CacheStore, upstream: HypixelUpstream):
        self.settings = settings
        self.cache = cache
        self.upstream = upstream

    def _direct_metadata(self) -> CacheMetadata:
        now = time.time()
        return CacheMetadata("direct", now, now + self.settings.name_fresh_seconds,
                             now + self.settings.name_stale_seconds)

    async def resolve(self, target: str) -> CacheResult:
        if UUID_TEXT.fullmatch(target):
            return CacheResult({"query": target, "uuid": normalize_uuid(target), "name": None},
                               self._direct_metadata())
        if not USERNAME.fullmatch(target):
            raise ApiProblem(422, "INVALID_PLAYER_NAME",
                             "Player names must contain 3-16 letters, numbers, or underscores.",
                             retryable=False)
        result = await self.cache.get_or_load(
            f"dungeon:identity:{target.lower()}",
            fresh_ttl=self.settings.name_fresh_seconds,
            stale_ttl=self.settings.name_stale_seconds,
            negative_ttl=self.settings.name_negative_seconds,
            loader=lambda: self.upstream.resolve_name(target),
        )
        if result.value is None:
            raise ApiProblem(404, "PLAYER_NOT_FOUND", "That Minecraft player does not exist.")
        identity = dict(result.value)
        identity["query"] = target
        return CacheResult(identity, result.metadata)

    async def quick_view(self, target: str, floor: str | None) -> dict[str, Any]:
        normalized_floor = floor.upper() if isinstance(floor, str) else None
        if normalized_floor is not None and not FLOOR.fullmatch(normalized_floor):
            raise ApiProblem(422, "INVALID_FLOOR", "The Dungeon floor is invalid.", retryable=False)
        identity = await self.resolve(target)
        player_uuid = identity.value["uuid"]
        player_result, profiles_result = await asyncio.gather(
            self.cache.get_or_load(
                f"dungeon:player:{player_uuid}",
                fresh_ttl=self.settings.dungeon_player_fresh_seconds,
                stale_ttl=self.settings.dungeon_player_stale_seconds,
                loader=lambda: self.upstream.fetch_player(player_uuid),
            ),
            self.cache.get_or_load(
                f"dungeon:profiles:{player_uuid}",
                fresh_ttl=self.settings.dungeon_profiles_fresh_seconds,
                stale_ttl=self.settings.dungeon_profiles_stale_seconds,
                loader=lambda: self.upstream.fetch_profiles(player_uuid),
            ),
        )
        raw_player = player_result.value.get("player") if isinstance(player_result.value, dict) else None
        if not isinstance(raw_player, dict):
            raise ApiProblem(404, "HYPIXEL_PLAYER_NOT_FOUND", "Hypixel has no record for that player.")
        raw_profiles = profiles_result.value.get("profiles") if isinstance(profiles_result.value, dict) else None
        profiles = [p for p in raw_profiles or [] if isinstance(p, dict)]
        if not profiles:
            raise ApiProblem(404, "SKYBLOCK_PROFILES_NOT_FOUND", "That player has no visible SkyBlock profiles.")
        selected = self._select_profile(profiles, player_uuid)
        members = selected.get("members")
        member = members.get(player_uuid) if isinstance(members, dict) else None
        if not isinstance(member, dict):
            raise ApiProblem(404, "SKYBLOCK_MEMBER_NOT_FOUND", "The selected profile has no data for that player.")

        data = await asyncio.to_thread(self._project, raw_player, member, normalized_floor)
        name = identity.value.get("name") or raw_player.get("displayname") or target
        if not isinstance(name, str) or not USERNAME.fullmatch(name):
            raise ApiProblem(502, "INVALID_UPSTREAM_PLAYER_NAME",
                             "The player identity returned by the upstream service is invalid.",
                             retryable=False)
        stale = any(result.metadata.state == "stale" for result in (identity, player_result, profiles_result))
        return {
            "identity": {"uuid": player_uuid, "name": str(name)[:16]},
            **data,
            "metadata": {
                "status": "stale" if stale else "fresh",
                "fetchedAt": min(
                    int(player_result.metadata.fetched_at * 1000),
                    int(profiles_result.metadata.fetched_at * 1000),
                ),
            },
        }

    @staticmethod
    def _select_profile(profiles: list[dict[str, Any]], player_uuid: str) -> dict[str, Any]:
        for profile in profiles:
            if profile.get("selected") is True:
                return profile
        def last_save(profile: dict[str, Any]) -> int:
            members = profile.get("members")
            member = members.get(player_uuid) if isinstance(members, dict) else None
            return int(member.get("last_save") or 0) if isinstance(member, dict) else 0
        return max(profiles, key=last_save)

    @staticmethod
    def _project(raw_player: dict[str, Any], member: dict[str, Any], floor: str | None) -> dict[str, Any]:
        dungeons = member.get("dungeons") if isinstance(member.get("dungeons"), dict) else {}
        types = dungeons.get("dungeon_types") if isinstance(dungeons.get("dungeon_types"), dict) else {}
        catacombs = types.get("catacombs") if isinstance(types.get("catacombs"), dict) else {}
        cata_xp = _number(catacombs.get("experience"))

        raw_classes = dungeons.get("player_classes") if isinstance(dungeons.get("player_classes"), dict) else {}
        classes: dict[str, Any] = {}
        for key in ("healer", "mage", "berserk", "archer", "tank"):
            raw_class = raw_classes.get(key) if isinstance(raw_classes.get(key), dict) else {}
            xp = _number(raw_class.get("experience"))
            classes[key] = {"level": catacombs_level(xp), "xp": xp}

        runs = None
        fastest = None
        if floor is not None:
            dungeon_key = "master_catacombs" if floor.startswith("M") else "catacombs"
            floor_key = "0" if floor == "E" else floor[1:]
            floor_data = types.get(dungeon_key) if isinstance(types.get(dungeon_key), dict) else {}
            completions = floor_data.get("tier_completions") if isinstance(floor_data.get("tier_completions"), dict) else {}
            raw_runs = _number(completions.get(floor_key))
            runs = int(raw_runs) if raw_runs is not None else None
            candidates: list[float] = []
            for field in ("fastest_time_s_plus", "fastest_time_s", "fastest_time"):
                values = floor_data.get(field) if isinstance(floor_data.get(field), dict) else {}
                value = _number(values.get(floor_key))
                if value is not None and value > 0:
                    candidates.append(value)
            fastest = int(min(candidates)) if candidates else None

        total_runs = 0
        has_runs = False
        for dungeon_key in ("catacombs", "master_catacombs"):
            floor_data = types.get(dungeon_key) if isinstance(types.get(dungeon_key), dict) else {}
            completions = floor_data.get("tier_completions") if isinstance(floor_data.get("tier_completions"), dict) else {}
            for key, value in completions.items():
                if key == "total":
                    continue
                number = _number(value)
                if number is not None:
                    total_runs += int(number)
                    has_runs = True
        achievements = raw_player.get("achievements") if isinstance(raw_player.get("achievements"), dict) else {}
        secrets = _number(achievements.get("skyblock_treasure_hunter"))
        average = secrets / total_runs if secrets is not None and has_runs and total_runs > 0 else None

        accessory = member.get("accessory_bag_storage") if isinstance(member.get("accessory_bag_storage"), dict) else {}
        magical_power = _number(accessory.get("highest_magical_power"))

        inventory = member.get("inventory") if isinstance(member.get("inventory"), dict) else {}
        decoded, inventory_complete = _items(inventory)
        all_items = [item for items in decoded.values() for item in items if isinstance(item, dict)]
        wither_blade = _best_item(all_items, WITHER_BLADES)
        terminator = _best_item(all_items, {"TERMINATOR"})

        pet_data = member.get("pets_data") if isinstance(member.get("pets_data"), dict) else {}
        raw_pets = pet_data.get("pets") if isinstance(pet_data.get("pets"), list) else []
        def best_pet(pet_type: str) -> dict[str, Any] | None:
            candidates = [p for p in raw_pets if isinstance(p, dict) and p.get("type") == pet_type]
            return max(candidates, key=lambda p: _number(p.get("exp")) or 0) if candidates else None

        return {
            "catacombs": {"level": catacombs_level(cata_xp), "xp": cata_xp},
            "classes": classes,
            "floor": {"id": floor, "runs": runs, "fastestMs": fastest},
            "secrets": {"total": int(secrets) if secrets is not None else None, "averagePerRun": average},
            "magicalPower": int(magical_power) if magical_power is not None else None,
            "armor": _armor(decoded),
            "weapons": {
                "witherBlade": {"present": wither_blade is not None, "item": wither_blade},
                "terminator": {"present": terminator is not None, "item": terminator},
                "complete": inventory_complete,
            },
            "pets": {
                "goldenDragon": {
                    "present": (greg := best_pet("GOLDEN_DRAGON")) is not None,
                    "item": _pet_view(greg, "Golden Dragon"),
                },
                "enderDragon": {
                    "present": (edrag := best_pet("ENDER_DRAGON")) is not None,
                    "item": _pet_view(edrag, "Ender Dragon"),
                },
                "complete": isinstance(pet_data.get("pets"), list),
            },
        }

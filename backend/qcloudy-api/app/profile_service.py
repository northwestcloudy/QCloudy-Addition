from __future__ import annotations

import asyncio
import re
import time
from typing import Any

from .cache import CacheMetadata, CacheResult, CacheStore
from .config import Settings
from .errors import ApiProblem
from .inventory import (
    project_bounded,
    project_bounded_with_status,
    summarize_inventory_fields,
)
from .upstream import HypixelUpstream

USERNAME = re.compile(r"^[A-Za-z0-9_]{3,16}$")
UUID_TEXT = re.compile(
    r"^(?:[0-9a-fA-F]{32}|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-"
    r"[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$"
)


def normalize_uuid(value: str) -> str:
    compact = value.replace("-", "").lower()
    if len(compact) != 32 or any(character not in "0123456789abcdef" for character in compact):
        raise ApiProblem(422, "INVALID_UUID", "The UUID is invalid.", retryable=False)
    return compact


class PlayerProfileService:
    def __init__(self, settings: Settings, cache: CacheStore, upstream: HypixelUpstream):
        self.settings = settings
        self.cache = cache
        self.upstream = upstream

    def _direct_metadata(self) -> CacheMetadata:
        now = time.time()
        return CacheMetadata(
            "direct",
            now,
            now + self.settings.name_fresh_seconds,
            now + self.settings.name_stale_seconds,
        )

    async def resolve(self, target: str) -> CacheResult:
        if UUID_TEXT.fullmatch(target):
            return CacheResult(
                {"query": target, "uuid": normalize_uuid(target), "name": None},
                self._direct_metadata(),
            )
        if not USERNAME.fullmatch(target):
            raise ApiProblem(
                422,
                "INVALID_PLAYER_NAME",
                "Player names must contain 3-16 letters, numbers, or underscores.",
                retryable=False,
            )

        async def load() -> dict[str, Any] | None:
            return await self.upstream.resolve_name(target)

        result = await self.cache.get_or_load(
            f"identity:name:{target.lower()}",
            fresh_ttl=self.settings.name_fresh_seconds,
            stale_ttl=self.settings.name_stale_seconds,
            negative_ttl=self.settings.name_negative_seconds,
            loader=load,
        )
        if result.value is None:
            raise ApiProblem(404, "PLAYER_NOT_FOUND", "That Minecraft player does not exist.")
        identity = dict(result.value)
        identity["query"] = target
        return CacheResult(identity, result.metadata)

    async def player(self, player_uuid: str) -> CacheResult:
        return await self.cache.get_or_load(
            f"hypixel:player:{player_uuid}",
            fresh_ttl=self.settings.player_fresh_seconds,
            stale_ttl=self.settings.player_stale_seconds,
            loader=lambda: self.upstream.fetch_player(player_uuid),
        )

    async def profiles(self, player_uuid: str) -> CacheResult:
        return await self.cache.get_or_load(
            f"hypixel:profiles:{player_uuid}",
            fresh_ttl=self.settings.profiles_fresh_seconds,
            stale_ttl=self.settings.profiles_stale_seconds,
            loader=lambda: self.upstream.fetch_profiles(player_uuid),
        )

    async def _validate_profile_membership(
        self, player_uuid: str, profile_id: str
    ) -> None:
        player_uuid = normalize_uuid(player_uuid)
        profile_id = normalize_uuid(profile_id)
        result = await self.profiles(player_uuid)
        payload = result.value if isinstance(result.value, dict) else {}
        profiles = payload.get("profiles")
        for profile in profiles if isinstance(profiles, list) else []:
            if not isinstance(profile, dict):
                continue
            candidate = str(profile.get("profile_id") or "").replace("-", "").lower()
            members = profile.get("members")
            if (
                candidate == profile_id
                and isinstance(members, dict)
                and player_uuid in members
            ):
                return
        raise ApiProblem(
            404,
            "PROFILE_NOT_FOUND",
            "That SkyBlock profile does not belong to the requested player.",
            retryable=False,
        )

    async def museum(self, player_uuid: str, profile_id: str) -> CacheResult:
        player_uuid = normalize_uuid(player_uuid)
        profile_id = normalize_uuid(profile_id)
        await self._validate_profile_membership(player_uuid, profile_id)

        async def load() -> dict[str, Any] | None:
            raw = await self.upstream.fetch_museum(profile_id)
            members = raw.get("members") if isinstance(raw, dict) else None
            member = members.get(player_uuid) if isinstance(members, dict) else None
            if not isinstance(member, dict):
                return None
            projected = await asyncio.to_thread(project_bounded, member)
            return {"member": projected}

        return await self.cache.get_or_load(
            f"hypixel:museum:{profile_id}:{player_uuid}",
            fresh_ttl=self.settings.museum_fresh_seconds,
            stale_ttl=self.settings.museum_stale_seconds,
            negative_ttl=self.settings.museum_fresh_seconds,
            loader=load,
        )

    async def garden(self, player_uuid: str, profile_id: str) -> CacheResult:
        player_uuid = normalize_uuid(player_uuid)
        profile_id = normalize_uuid(profile_id)
        await self._validate_profile_membership(player_uuid, profile_id)

        async def load() -> dict[str, Any] | None:
            raw = await self.upstream.fetch_garden(profile_id)
            garden = raw.get("garden") if isinstance(raw, dict) else None
            if not isinstance(garden, dict):
                return None
            projected = await asyncio.to_thread(project_bounded, garden)
            return {"garden": projected}

        return await self.cache.get_or_load(
            f"hypixel:garden:{profile_id}",
            fresh_ttl=self.settings.garden_fresh_seconds,
            stale_ttl=self.settings.garden_stale_seconds,
            negative_ttl=self.settings.garden_fresh_seconds,
            loader=load,
        )

    @staticmethod
    def _profile_summary(profile: dict[str, Any], player_uuid: str) -> dict[str, Any]:
        profile_id = profile.get("profile_id")
        member = profile.get("members", {}).get(player_uuid, {}) if isinstance(profile.get("members"), dict) else {}
        return {
            "profileId": str(profile_id).replace("-", "") if profile_id else None,
            "cuteName": profile.get("cute_name"),
            "selected": profile.get("selected") is True,
            "gameMode": profile.get("game_mode"),
            "memberCount": len(profile.get("members", {}))
            if isinstance(profile.get("members"), dict)
            else 0,
        }

    @staticmethod
    def _pick(source: dict[str, Any], *keys: str) -> dict[str, Any]:
        return {key: source[key] for key in keys if key in source}

    @classmethod
    def _sections(
        cls,
        raw_player: dict[str, Any],
        selected: dict[str, Any],
        member: dict[str, Any] | None,
    ) -> tuple[dict[str, Any], bool]:
        member = member or {}
        inventory = member.get("inventory") if isinstance(member.get("inventory"), dict) else {}
        inventory_summary = summarize_inventory_fields(inventory)
        classified_member_fields = {
            "last_save", "inventory", "pets_data", "pets", "player_data",
            "currencies", "leveling", "experience_skill", "slayer",
            "slayer_bosses", "minions", "crafted_generators", "bestiary",
            "collection", "unlocked_coll_tiers", "mining_core",
            "nether_island_player_data", "rift", "accessory_bag_storage",
            "jacobs_contest", "farming", "garden_player_data",
            # Dungeon profile data is intentionally out of scope for this
            # release. Keep both current and compatibility aliases out of the
            # generic Misc projection so it cannot leak through indirectly.
            "dungeons", "dungeon",
        }
        classified_profile_fields = {
            "profile_id", "cute_name", "selected", "game_mode", "banking", "members",
            "dungeons", "dungeon",
        }
        classified_player_fields = {
            "uuid", "displayname", "rank", "newPackageRank", "dungeons", "dungeon"
        }
        payloads: dict[str, dict[str, Any]] = {
            "overview": {
                "player": cls._pick(raw_player, "uuid", "displayname", "rank", "newPackageRank"),
                "profile": cls._pick(
                    selected, "profile_id", "cute_name", "selected", "game_mode", "banking"
                ),
                "playerData": member.get("player_data", {}),
                "currencies": member.get("currencies", {}),
                "leveling": member.get("leveling", {}),
            },
            "gear": {
                **cls._pick(
                    inventory_summary,
                    "inv_armor",
                    "armor",
                    "equipment_contents",
                    "equipment",
                    "wardrobe_contents",
                    "wardrobe",
                )
            },
            "accessories": {
                "accessoryBagStorage": member.get("accessory_bag_storage", {}),
                **cls._pick(inventory_summary, "bag_contents", "talisman_bag"),
            },
            "pets": member.get("pets_data", {})
            if isinstance(member.get("pets_data"), dict)
            else {"pets": member.get("pets", [])},
            "inventory": inventory_summary,
            "skills": {
                "playerData": member.get("player_data", {}),
                "experience": member.get("experience_skill", {}),
            },
            "slayer": member.get("slayer", member.get("slayer_bosses", {})),
            "minions": member.get("minions", {"craftedGenerators": member.get("crafted_generators", [])}),
            "bestiary": member.get("bestiary", {}),
            "collections": {
                "collection": member.get("collection", {}),
                "unlockedTiers": member.get("unlocked_coll_tiers", []),
            },
            "mining": member.get("mining_core", {}),
            "crimson_isle": member.get("nether_island_player_data", {}),
            "rift": member.get("rift", {}),
            "misc": {
                "farming": cls._pick(
                    member, "jacobs_contest", "farming", "garden_player_data"
                ),
                "member": {
                    key: member[key]
                    for key in sorted(set(member) - classified_member_fields)
                },
                "profile": {
                    key: selected[key]
                    for key in sorted(set(selected) - classified_profile_fields)
                },
                "player": {
                    key: raw_player[key]
                    for key in sorted(set(raw_player) - classified_player_fields)
                },
            },
        }

        # Apply one shared response budget across the main profile. This keeps
        # all client-facing data transformed and prevents schema additions from
        # growing a response past the mod's fixed 4 MiB safety limit.
        bounded, projection_complete = project_bounded_with_status(payloads)
        payloads = bounded if isinstance(bounded, dict) else {}

        private_if_empty = {"gear", "accessories", "inventory"}
        sections: dict[str, Any] = {}
        for name, payload in payloads.items():
            if not isinstance(payload, dict):
                payload = {"value": payload}
            has_content = any(value not in ({}, [], None, "") for value in payload.values())
            if has_content:
                status = "available"
            elif name in private_if_empty:
                status = "private"
            else:
                status = "not_found"
            sections[name] = {"status": status, "message": None, "payload": payload}
        for name in ("museum", "garden", "market"):
            sections[name] = {"status": "not_loaded", "message": None, "payload": {}}
        return sections, projection_complete

    @staticmethod
    def _select_profile(
        profiles: list[dict[str, Any]], player_uuid: str, requested_profile_id: str | None
    ) -> dict[str, Any]:
        requested = requested_profile_id.replace("-", "").lower() if requested_profile_id else None
        if requested:
            for profile in profiles:
                candidate = str(profile.get("profile_id") or "").replace("-", "").lower()
                if candidate == requested:
                    return profile
            raise ApiProblem(404, "PROFILE_NOT_FOUND", "That SkyBlock profile was not found.")
        for profile in profiles:
            if profile.get("selected") is True:
                return profile

        def last_save(profile: dict[str, Any]) -> int:
            members = profile.get("members")
            if not isinstance(members, dict):
                return 0
            member = members.get(player_uuid)
            return int(member.get("last_save") or 0) if isinstance(member, dict) else 0

        return max(profiles, key=last_save)

    async def pv(self, target: str, requested_profile_id: str | None = None) -> dict[str, Any]:
        identity_result = await self.resolve(target)
        player_uuid = identity_result.value["uuid"]
        player_result = await self.player(player_uuid)
        profiles_result = await self.profiles(player_uuid)

        player_payload = player_result.value
        raw_player = player_payload.get("player") if isinstance(player_payload, dict) else None
        if not isinstance(raw_player, dict):
            raise ApiProblem(404, "HYPIXEL_PLAYER_NOT_FOUND", "Hypixel has no record for that player.")

        profiles_payload = profiles_result.value
        raw_profiles = profiles_payload.get("profiles") if isinstance(profiles_payload, dict) else None
        profiles = [profile for profile in raw_profiles or [] if isinstance(profile, dict)]
        if not profiles:
            raise ApiProblem(404, "SKYBLOCK_PROFILES_NOT_FOUND", "That player has no visible SkyBlock profiles.")
        selected = self._select_profile(profiles, player_uuid, requested_profile_id)
        profile_id = str(selected.get("profile_id") or "").replace("-", "").lower()
        members = selected.get("members")
        member = members.get(player_uuid) if isinstance(members, dict) else None

        identity = dict(identity_result.value)
        if not identity.get("name"):
            identity["name"] = raw_player.get("displayname")
        identity["skinTextureUrl"] = None

        sections, profile_projection_complete = await asyncio.to_thread(
            self._sections,
            raw_player,
            selected,
            member if isinstance(member, dict) else None,
        )

        return {
            "partial": any(
                source.metadata.state == "stale"
                for source in (identity_result, player_result, profiles_result)
            ),
            "identity": identity,
            "profiles": [self._profile_summary(profile, player_uuid) for profile in profiles],
            "selectedProfileId": profile_id,
            "sections": sections,
            # Internal request-scoped signal consumed by the API layer before
            # serialization. A truncated source projection must never be called
            # a complete portfolio valuation.
            "_profileProjectionComplete": profile_projection_complete,
            "sources": {
                "identity": identity_result.metadata.to_source(),
                "player": player_result.metadata.to_source(),
                "profiles": profiles_result.metadata.to_source(),
            },
        }

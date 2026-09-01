from __future__ import annotations

import hashlib
import json
import math
import re
import statistics
from collections import defaultdict
from dataclasses import dataclass
from typing import Any

from .nbt import NbtDecodeError, decode_nbt_base64, find_compound
from .storage import EndedSale

FORMATTING_CODE = re.compile(r"\u00a7[0-9A-FK-ORa-fk-or]")
NON_ID = re.compile(r"[^A-Z0-9_:]+")


@dataclass(frozen=True, slots=True)
class ItemIdentity:
    item_id: str
    variant_key: str
    parse_quality: str
    stack_count: int


def _fallback_id(name: Any) -> str:
    text = FORMATTING_CODE.sub("", str(name or "UNKNOWN")).upper().strip()
    return NON_ID.sub("_", text).strip("_") or "UNKNOWN"


def _stable_subset(extra: dict[str, Any]) -> dict[str, Any]:
    keys = (
        "modifier",
        "rarity_upgrades",
        "dungeon_item_level",
        "upgrade_level",
        "hot_potato_count",
        "art_of_war_count",
        "wood_singularity_count",
        "ethermerge",
        "ability_scroll",
        "skin",
        "unlocked_slots",
        "enchantments",
        "attributes",
        "gems",
        "runes",
    )
    # itemId already identifies the base item. Auction category/tier are omitted
    # so the same inventory NBT can reproduce the key without AH-only metadata.
    subset: dict[str, Any] = {}
    for key in keys:
        if key in extra:
            subset[key] = extra[key]

    pet_info = extra.get("petInfo")
    if isinstance(pet_info, str):
        try:
            parsed = json.loads(pet_info)
            if isinstance(parsed, dict):
                subset["pet"] = {
                    key: parsed.get(key)
                    for key in ("type", "tier", "heldItem", "skin", "candyUsed")
                    if parsed.get(key) is not None
                }
        except ValueError:
            subset["petInfoHash"] = hashlib.sha256(pet_info.encode()).hexdigest()[:16]
    return subset


def _variant_key(item_id: str, variant: dict[str, Any]) -> str:
    canonical = json.dumps(
        {"itemId": item_id, "variant": variant},
        sort_keys=True,
        separators=(",", ":"),
        ensure_ascii=True,
        default=str,
    )
    return hashlib.sha256(canonical.encode()).hexdigest()[:32]


def variant_key_from_extra(item_id: str, extra: dict[str, Any]) -> str:
    return _variant_key(_fallback_id(item_id), _stable_subset(extra))


def variant_key_for_pet(pet: dict[str, Any]) -> str | None:
    pet_type = pet.get("type")
    if not isinstance(pet_type, str) or not pet_type:
        return None
    stable_pet = {
        key: pet.get(key)
        for key in ("type", "tier", "heldItem", "skin", "candyUsed")
        if pet.get(key) is not None
    }
    return _variant_key("PET", {"pet": stable_pet})


def item_identity(auction: dict[str, Any]) -> ItemIdentity:
    encoded: Any = auction.get("item_bytes")
    if isinstance(encoded, dict):
        encoded = encoded.get("data")
    try:
        if not isinstance(encoded, str) or not encoded:
            raise NbtDecodeError("Missing item bytes")
        root = decode_nbt_base64(encoded)
        extra = find_compound(root, "ExtraAttributes")
        if extra is None:
            raise NbtDecodeError("ExtraAttributes is missing")
        item_id = _fallback_id(extra.get("id") or auction.get("item_name"))
        variant = _stable_subset(extra)
        quality = "exact"
        stack_count = _stack_count(root)
    except NbtDecodeError:
        item_id = _fallback_id(auction.get("item_name"))
        variant = {
            "fallbackName": FORMATTING_CODE.sub("", str(auction.get("item_name") or "")),
            "tier": auction.get("tier"),
            "category": auction.get("category"),
        }
        quality = "fallback"
        raw_count = auction.get("item_count", 1)
        try:
            stack_count = max(1, min(int(raw_count), 2_147_483_647))
        except (TypeError, ValueError):
            stack_count = 1

    return ItemIdentity(
        item_id=item_id,
        variant_key=_variant_key(item_id, variant),
        parse_quality=quality,
        stack_count=stack_count,
    )


def _stack_count(root: dict[str, Any]) -> int:
    for field in ("i", "items"):
        items = root.get(field)
        if not isinstance(items, list):
            continue
        for item in items:
            if not isinstance(item, dict) or not item:
                continue
            raw = item.get("Count", item.get("count", 1))
            try:
                return max(1, min(int(raw), 2_147_483_647))
            except (TypeError, ValueError):
                return 1
    return 1


def _positive_number(value: Any) -> float | None:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return None
    number = float(value)
    return number if math.isfinite(number) and number > 0 else None


def normalize_bazaar(payload: dict[str, Any], fetched_at_ms: int) -> dict[str, Any]:
    products = payload.get("products")
    if not isinstance(products, dict):
        raise ValueError("Bazaar payload has no products map")
    normalized: dict[str, Any] = {}
    for raw_id, product in products.items():
        if not isinstance(product, dict):
            continue
        quick = product.get("quick_status")
        if not isinstance(quick, dict):
            continue
        instant_buy = _positive_number(quick.get("buyPrice"))
        instant_sell = _positive_number(quick.get("sellPrice"))
        if instant_buy is None and instant_sell is None:
            continue
        item_id = _fallback_id(product.get("product_id") or raw_id)
        normalized[item_id] = {
            # Renamed intentionally: Hypixel's field names are easy to reverse.
            "instantBuyPrice": instant_buy,
            "instantSellPrice": instant_sell,
            "buyVolume": quick.get("buyVolume"),
            "sellVolume": quick.get("sellVolume"),
            "buyMovingWeek": quick.get("buyMovingWeek"),
            "sellMovingWeek": quick.get("sellMovingWeek"),
        }
    return {
        "sourceLastUpdated": int(payload.get("lastUpdated") or 0),
        "fetchedAt": fetched_at_ms,
        "products": normalized,
    }


def aggregate_active_auctions(
    auctions: list[dict[str, Any]],
    *,
    source_last_updated: int,
    fetched_at_ms: int,
    total_auctions: int,
) -> dict[str, Any]:
    groups: dict[tuple[str, str], list[float]] = defaultdict(list)
    quality: dict[tuple[str, str], str] = {}
    for auction in auctions:
        if auction.get("bin") is not True:
            continue
        price = auction.get("starting_bid")
        if isinstance(price, bool) or not isinstance(price, (int, float)) or price <= 0:
            continue
        identity = item_identity(auction)
        key = (identity.item_id, identity.variant_key)
        groups[key].append(float(price) / identity.stack_count)
        quality[key] = identity.parse_quality

    prices: dict[str, dict[str, Any]] = defaultdict(dict)
    for (item_id, variant_key), values in groups.items():
        ordered = sorted(values)
        robust_window = ordered[: min(5, len(ordered))]
        prices[item_id][variant_key] = {
            "lowestBin": ordered[0],
            "robustListingPrice": float(statistics.median(robust_window)),
            "listingCount": len(ordered),
            "parseQuality": quality[(item_id, variant_key)],
        }
    return {
        "sourceLastUpdated": source_last_updated,
        "fetchedAt": fetched_at_ms,
        "totalAuctions": total_auctions,
        "prices": dict(prices),
    }


def ended_sale(auction: dict[str, Any]) -> EndedSale | None:
    auction_id = auction.get("auction_id")
    buyer = auction.get("buyer")
    price = auction.get("price")
    timestamp = auction.get("timestamp")
    if not isinstance(auction_id, str) or not auction_id:
        return None
    if not isinstance(buyer, str) or not buyer:
        return None
    if isinstance(price, bool) or not isinstance(price, (int, float)) or price <= 0:
        return None
    if not isinstance(timestamp, (int, float)) or timestamp <= 0:
        return None
    identity = item_identity(auction)
    return EndedSale(
        auction_id=auction_id,
        ended_at_ms=int(timestamp),
        item_id=identity.item_id,
        variant_key=identity.variant_key,
        price=float(price) / identity.stack_count,
        is_bin=auction.get("bin") is True,
        parse_quality=identity.parse_quality,
    )

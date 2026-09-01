from __future__ import annotations

import base64
from dataclasses import dataclass
from typing import Any

from .nbt import NbtDecodeError, summarize_inventory_nbt
from .pricing import variant_key_from_extra


_BLOB_KEYS = {"data", "item_bytes", "item_data", "itembytes", "itemdata"}


@dataclass(slots=True)
class _ProjectionBudget:
    remaining_nodes: int = 6_000
    remaining_bytes: int = 2_500_000
    complete: bool = True

    def take(self) -> bool:
        if self.remaining_nodes <= 0 or self.remaining_bytes <= 0:
            self.complete = False
            return False
        self.remaining_nodes -= 1
        self.remaining_bytes -= 16
        return True

    def clip(self, value: str, maximum_characters: int) -> str:
        if len(value) > maximum_characters:
            self.complete = False
        candidate = value[:maximum_characters]
        encoded = candidate.encode("utf-8", errors="replace")
        if len(encoded) <= self.remaining_bytes:
            self.remaining_bytes -= len(encoded)
            return candidate
        allowed = max(0, self.remaining_bytes)
        clipped = encoded[:allowed].decode("utf-8", errors="ignore")
        self.remaining_bytes = 0
        self.complete = False
        return clipped


def _encoded_data(value: Any) -> str | None:
    if isinstance(value, str):
        return value
    if isinstance(value, dict) and isinstance(value.get("data"), str):
        return value["data"]
    return None


def _encoded_size(encoded: str) -> int:
    try:
        return len(base64.b64decode(encoded, validate=False))
    except (ValueError, TypeError):
        return len(encoded)


def _summarize_encoded_blob(encoded: str) -> dict[str, Any]:
    try:
        return {
            "decodeStatus": "decoded",
            "encodedByteLength": _encoded_size(encoded),
            "items": summarize_inventory_nbt(encoded),
        }
    except NbtDecodeError:
        return {
            "decodeStatus": "unavailable",
            "encodedByteLength": _encoded_size(encoded),
            "items": [],
        }


def project_bounded_with_status(value: Any) -> tuple[Any, bool]:
    """Project arbitrary Hypixel supplement JSON into a bounded safe shape.

    Museum payloads may contain co-op members and encoded item NBT.  Callers
    select the intended member before invoking this function; this projector
    then removes raw blobs, caps recursion/collection sizes, and truncates long
    strings so a successful upstream response cannot exceed the mod's response
    budget merely because Hypixel added a large field.
    """

    budget = _ProjectionBudget()

    def visit(current: Any, *, key_hint: str = "", depth: int = 0) -> Any:
        if not budget.take():
            return "<node-limit>"
        if depth >= 12:
            budget.complete = False
            return "<depth-limit>"
        if current is None or isinstance(current, (bool, int, float)):
            return current
        if isinstance(current, str):
            if key_hint.lower() in _BLOB_KEYS:
                return visit(_summarize_encoded_blob(current), depth=depth + 1)
            # Do not echo an unknown long encoded field under a newly-added key.
            if len(current) > 1_024:
                budget.complete = False
                return {"status": "omitted", "length": len(current)}
            return budget.clip(current, 256)
        if isinstance(current, list):
            projected = [visit(child, depth=depth + 1) for child in current[:128]]
            if len(current) > 128:
                budget.complete = False
                projected.append({"status": "truncated", "omitted": len(current) - 128})
            return projected
        if isinstance(current, dict):
            encoded = _encoded_data(current)
            if encoded is not None and (
                key_hint.lower() in _BLOB_KEYS
                or set(map(str.lower, map(str, current.keys()))) <= {"type", "data"}
            ):
                summary = _summarize_encoded_blob(encoded)
                if "type" in current:
                    summary["type"] = visit(current["type"], depth=depth + 1)
                return visit(summary, depth=depth + 1)
            result: dict[str, Any] = {}
            entries = list(current.items())
            for raw_key, child in entries[:128]:
                key = budget.clip(str(raw_key), 128)
                if not key:
                    break
                result[key] = visit(child, key_hint=key, depth=depth + 1)
            if len(entries) > 128:
                budget.complete = False
                result["_truncated"] = {"omitted": len(entries) - 128}
            return result
        return budget.clip(str(current), 256)

    return visit(value), budget.complete


def project_bounded(value: Any) -> Any:
    projected, _ = project_bounded_with_status(value)
    return projected


def summarize_inventory_fields(inventory: dict[str, Any]) -> dict[str, Any]:
    """Replace every encoded inventory field with bounded item summaries.

    Hypixel occasionally adds new inventory keys. Detecting blobs by shape keeps
    those fields usable without forwarding giant base64 values to the mod.
    """
    result: dict[str, Any] = {}
    for key, value in inventory.items():
        encoded = _encoded_data(value)
        if encoded is None:
            # Preserve only compact, already-structured metadata. Never echo an
            # unknown long string that may itself be an encoded item payload.
            if isinstance(value, str) and len(value) > 1024:
                result[key] = {"decodeStatus": "omitted", "encodedLength": len(value)}
            else:
                result[key] = value
            continue
        try:
            items = summarize_inventory_nbt(encoded)
            for item in items:
                item_id = item.get("itemId")
                extra = item.get("extraAttributes")
                if isinstance(item_id, str) and isinstance(extra, dict):
                    item["variantKey"] = variant_key_from_extra(item_id, extra)
            result[key] = {
                "decodeStatus": "decoded",
                "encodedByteLength": _encoded_size(encoded),
                "items": items,
            }
        except NbtDecodeError:
            result[key] = {
                "decodeStatus": "unavailable",
                "encodedByteLength": _encoded_size(encoded),
                "items": [],
            }
    return result

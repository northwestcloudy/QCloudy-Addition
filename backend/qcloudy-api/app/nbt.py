from __future__ import annotations

import base64
import gzip
import io
import json
import re
import struct
from dataclasses import dataclass
from typing import Any


class NbtDecodeError(ValueError):
    pass


MAX_COMPRESSED_BYTES = 8_000_000
MAX_DECOMPRESSED_BYTES = 16_000_000


@dataclass(slots=True)
class _Reader:
    stream: io.BytesIO

    def read(self, length: int) -> bytes:
        if length < 0 or length > 16_000_000:
            raise NbtDecodeError("NBT length is outside the safety limit")
        value = self.stream.read(length)
        if len(value) != length:
            raise NbtDecodeError("NBT payload ended unexpectedly")
        return value

    def unpack(self, fmt: str) -> Any:
        size = struct.calcsize(fmt)
        return struct.unpack(fmt, self.read(size))[0]

    def string(self) -> str:
        length = self.unpack(">H")
        return self.read(length).decode("utf-8", errors="replace")


def _payload(reader: _Reader, tag_type: int, depth: int = 0) -> Any:
    if depth > 64:
        raise NbtDecodeError("NBT nesting is too deep")
    if tag_type == 0:
        return None
    if tag_type == 1:
        return reader.unpack(">b")
    if tag_type == 2:
        return reader.unpack(">h")
    if tag_type == 3:
        return reader.unpack(">i")
    if tag_type == 4:
        return reader.unpack(">q")
    if tag_type == 5:
        return reader.unpack(">f")
    if tag_type == 6:
        return reader.unpack(">d")
    if tag_type == 7:
        length = reader.unpack(">i")
        return reader.read(length)
    if tag_type == 8:
        return reader.string()
    if tag_type == 9:
        child_type = reader.unpack(">B")
        length = reader.unpack(">i")
        if length < 0 or length > 1_000_000:
            raise NbtDecodeError("NBT list length is outside the safety limit")
        return [_payload(reader, child_type, depth + 1) for _ in range(length)]
    if tag_type == 10:
        result: dict[str, Any] = {}
        while True:
            child_type = reader.unpack(">B")
            if child_type == 0:
                return result
            name = reader.string()
            result[name] = _payload(reader, child_type, depth + 1)
    if tag_type == 11:
        length = reader.unpack(">i")
        if length < 0 or length > 1_000_000:
            raise NbtDecodeError("NBT int array length is outside the safety limit")
        return [reader.unpack(">i") for _ in range(length)]
    if tag_type == 12:
        length = reader.unpack(">i")
        if length < 0 or length > 1_000_000:
            raise NbtDecodeError("NBT long array length is outside the safety limit")
        return [reader.unpack(">q") for _ in range(length)]
    raise NbtDecodeError(f"Unsupported NBT tag type {tag_type}")


def decode_nbt_base64(encoded: str) -> dict[str, Any]:
    if len(encoded) > MAX_COMPRESSED_BYTES * 2:
        raise NbtDecodeError("Encoded NBT is outside the safety limit")
    try:
        raw = base64.b64decode(encoded, validate=True)
    except (ValueError, TypeError) as exc:
        raise NbtDecodeError("Invalid base64 item data") from exc
    if len(raw) > MAX_COMPRESSED_BYTES:
        raise NbtDecodeError("Compressed NBT is outside the safety limit")
    try:
        with gzip.GzipFile(fileobj=io.BytesIO(raw)) as compressed:
            decoded = compressed.read(MAX_DECOMPRESSED_BYTES + 1)
        if len(decoded) > MAX_DECOMPRESSED_BYTES:
            raise NbtDecodeError("Decompressed NBT is outside the safety limit")
        raw = decoded
    except (gzip.BadGzipFile, EOFError):
        if len(raw) > MAX_DECOMPRESSED_BYTES:
            raise NbtDecodeError("NBT is outside the safety limit")
    reader = _Reader(io.BytesIO(raw))
    root_type = reader.unpack(">B")
    if root_type != 10:
        raise NbtDecodeError("NBT root is not a compound")
    reader.string()  # Root name is not semantically relevant.
    root = _payload(reader, root_type)
    if not isinstance(root, dict):
        raise NbtDecodeError("NBT root did not decode to a compound")
    return root


def find_compound(value: Any, key: str) -> dict[str, Any] | None:
    if isinstance(value, dict):
        candidate = value.get(key)
        if isinstance(candidate, dict):
            return candidate
        for child in value.values():
            found = find_compound(child, key)
            if found is not None:
                return found
    elif isinstance(value, list):
        for child in value:
            found = find_compound(child, key)
            if found is not None:
                return found
    return None


_MINECRAFT_FORMATTING = re.compile(r"\u00a7[0-9A-FK-ORa-fk-or]")
_UNSAFE_LEGACY_CODES = re.compile(r"\u00a7(?![0-9A-FK-ORa-fk-or])")


def _bounded_json(value: Any, *, depth: int = 0) -> Any:
    """Make selected NBT data JSON-safe without returning unbounded blobs."""
    if depth >= 6:
        return "<depth-limit>"
    if value is None or isinstance(value, (bool, int, float)):
        return value
    if isinstance(value, str):
        return value[:512]
    if isinstance(value, bytes):
        return {"byteLength": len(value)}
    if isinstance(value, list):
        return [_bounded_json(child, depth=depth + 1) for child in value[:64]]
    if isinstance(value, dict):
        return {
            str(key)[:128]: _bounded_json(child, depth=depth + 1)
            for key, child in list(value.items())[:64]
        }
    return str(value)[:512]


def _display_text(value: Any) -> str | None:
    if not isinstance(value, str) or not value:
        return None
    # Modern item names may be JSON text components. Retain the original text if
    # it cannot be reduced safely; clients can still render it as plain text.
    try:
        component = json.loads(value)
        if isinstance(component, dict) and isinstance(component.get("text"), str):
            value = component["text"]
    except ValueError:
        pass
    return _MINECRAFT_FORMATTING.sub("", value)[:256]


def _legacy_text(value: Any, maximum: int) -> str | None:
    """Retain only vanilla legacy formatting codes for a native item tooltip."""
    if not isinstance(value, str) or not value:
        return None
    try:
        component = json.loads(value)
        if isinstance(component, dict) and isinstance(component.get("text"), str):
            value = component["text"]
    except ValueError:
        pass
    value = value.replace("\r", " ").replace("\n", " ").replace("\t", " ")
    value = _UNSAFE_LEGACY_CODES.sub("", value)
    return value[:maximum]


def _rarity_from_item(item: dict[str, Any]) -> str | None:
    tag = item.get("tag")
    if not isinstance(tag, dict):
        return None
    display = tag.get("display")
    if not isinstance(display, dict):
        return None
    lore = display.get("Lore")
    if not isinstance(lore, list):
        return None
    for line in reversed(lore):
        plain = _display_text(line)
        if not plain:
            continue
        upper = plain.upper()
        for rarity in (
            "VERY SPECIAL",
            "SPECIAL",
            "DIVINE",
            "MYTHIC",
            "LEGENDARY",
            "EPIC",
            "RARE",
            "UNCOMMON",
            "COMMON",
        ):
            if rarity in upper:
                return rarity
    return None


def summarize_inventory_nbt(encoded: str, *, max_items: int = 256) -> list[dict[str, Any]]:
    """Decode a Hypixel inventory blob into bounded, client-ready item summaries."""
    root = decode_nbt_base64(encoded)
    raw_items = root.get("i")
    if not isinstance(raw_items, list):
        raw_items = root.get("items")
    if not isinstance(raw_items, list):
        raise NbtDecodeError("Inventory NBT has no item list")

    summaries: list[dict[str, Any]] = []
    for fallback_slot, raw_item in enumerate(raw_items[:max_items]):
        if not isinstance(raw_item, dict) or not raw_item:
            continue
        tag = raw_item.get("tag") if isinstance(raw_item.get("tag"), dict) else {}
        extra = tag.get("ExtraAttributes") if isinstance(tag, dict) else None
        if not isinstance(extra, dict):
            extra = {}
        display = tag.get("display") if isinstance(tag, dict) else None
        if not isinstance(display, dict):
            display = {}
        item_id = extra.get("id")
        slot = raw_item.get("Slot", fallback_slot)
        count = raw_item.get("Count", raw_item.get("count", 1))
        try:
            slot = int(slot)
        except (TypeError, ValueError):
            slot = fallback_slot
        try:
            count = int(count)
        except (TypeError, ValueError):
            count = 1
        summaries.append(
            {
                "slot": max(0, min(slot, 1024)),
                "count": max(1, min(count, 2_147_483_647)),
                "itemId": str(item_id)[:128] if item_id is not None else None,
                "displayName": _display_text(display.get("Name")),
                "formattedName": _legacy_text(display.get("Name"), 256),
                "lore": [
                    line
                    for line in (
                        _legacy_text(raw_line, 512)
                        for raw_line in (
                            display.get("Lore", [])
                            if isinstance(display.get("Lore"), list)
                            else []
                        )[:80]
                    )
                    if line is not None
                ],
                "rarity": _rarity_from_item(raw_item),
                "extraAttributes": _bounded_json(extra),
            }
        )
    return summaries

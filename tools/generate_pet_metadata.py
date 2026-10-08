#!/usr/bin/env python3
"""Generate compact, offline pet/head/skin/accessory render metadata."""

from __future__ import annotations

import json
import base64
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
# This is a build-time snapshot only. Generated JSON is committed and bundled,
# so the released mod never needs NEU, Firmament, a network connection, or a
# local repository at runtime.
REPO = ROOT / "config/notenoughupdates/repo"
OUTPUT = ROOT / "src/main/resources/assets/qcloudy_addition/data"
FORMATTING = re.compile(r"§.")
MODEL = re.compile(r'ItemModel:"([^"]+)"')
TEXTURE = re.compile(r'Value:"([A-Za-z0-9+/=]+)"')
OFFICIAL_API_PET_ITEMS = {
    # Hypixel 0.27.2 items resource. These Drone Mods are not yet present in
    # every local NEU snapshot, so keep the committed offline renderer stable.
    "CONTRABAND": ("Drone Mod: Contraband", "hypixel_skyblock:item/uncategorized/contraband"),
    "GRUNGLE": ("Drone Mod: Grungle", "hypixel_skyblock:item/uncategorized/grungle"),
    "MINING_OFF_CAMERA": (
        "Drone Mod: Mining Off Camera",
        "hypixel_skyblock:item/uncategorized/mining_off_camera",
    ),
}


def clean(value: str) -> str:
    return FORMATTING.sub("", value).strip()


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    items = REPO / "items"

    skin_names: dict[str, str] = {}
    skin_profiles: dict[str, str] = {}
    texture_to_skin: dict[str, str] = {}
    for path in sorted(items.glob("PET_SKIN_*.json")):
        data = json.loads(path.read_text())
        key = data.get("internalname", path.stem).removeprefix("PET_SKIN_").lower()
        skin_names[key] = clean(data.get("displayname", key.replace("_", " ").title()))
        texture = TEXTURE.search(data.get("nbttag", ""))
        if texture:
            skin_profiles[key] = texture.group(1)
            texture_to_skin[texture_hash(texture.group(1))] = key

    animated = json.loads((REPO / "constants/animatedskulls.json").read_text())
    variant_owner: dict[str, str] = {}
    for family, variants in animated.get("pet_skin_variant", {}).items():
        owner = family.removeprefix("PET_SKIN_").lower()
        for variant in variants:
            variant_owner[variant] = owner
    for variant, entry in animated.get("skins", {}).items():
        owner = variant_owner.get(variant)
        if owner is None and variant.startswith("PET_SKIN_"):
            candidate = variant.removeprefix("PET_SKIN_").lower()
            if candidate in skin_names:
                owner = candidate
            else:
                # Some dynamic skins (currently including Baby Spinosaurus)
                # publish their colour/animation variants without adding the
                # family to pet_skin_variant. Resolve those entries to the
                # longest real skin id prefix, never to another pet's skin.
                owners = [skin for skin in skin_names
                          if candidate.startswith(skin + "_")]
                if owners:
                    owner = max(owners, key=len)
        if owner is None:
            continue
        for encoded in entry.get("textures", []):
            texture_to_skin[texture_hash(encoded)] = owner

    pets = json.loads((REPO / "constants/pets.json").read_text())
    # A pet is rendered as its normal pet stack with only PROFILE replaced by
    # the equipped skin. This is how the in-game Pets menu and established
    # clients preserve both custom resource-pack predicates and the real skull.
    pet_heads: dict[str, dict[str, str]] = {}
    pet_candidates: dict[str, tuple[int, Path]] = {}
    for path in items.glob("*.json"):
        match = re.fullmatch(r"(.+);(\d+)", path.stem)
        if not match:
            continue
        data = json.loads(path.read_text())
        nbt = data.get("nbttag", "")
        if 'petInfo:' not in nbt:
            continue
        pet_type = match.group(1)
        rarity_index = int(match.group(2))
        previous = pet_candidates.get(pet_type)
        if previous is None or rarity_index > previous[0]:
            pet_candidates[pet_type] = (rarity_index, path)
    for pet_type, (_, path) in sorted(pet_candidates.items()):
        data = json.loads(path.read_text())
        nbt = data.get("nbttag", "")
        texture = TEXTURE.search(nbt)
        if not texture:
            continue
        pet_heads[pet_type.lower()] = {
            "id": data.get("internalname", path.stem),
            "texture": texture.group(1),
        }
    # The legacy display-name table is not exhaustive. Newer pet items such as
    # Shelmets, Relics, Bandanas and hunting-island upgrades are regular repo
    # items whose lore ends in "PET ITEM", so include both sources.
    accessory_ids = set(pets["pet_item_display_name_to_id"].values())
    for path in items.glob("*.json"):
        data = json.loads(path.read_text())
        lore = [clean(line) for line in data.get("lore", [])]
        if any(line.endswith("PET ITEM") for line in lore):
            accessory_ids.add(data.get("internalname", path.stem))
    accessories: dict[str, dict[str, object]] = {}
    for item_id in sorted(accessory_ids):
        path = items / f"{item_id}.json"
        if not path.exists():
            continue
        data = json.loads(path.read_text())
        nbt = data.get("nbttag", "")
        model = MODEL.search(nbt)
        texture = TEXTURE.search(nbt)
        # NEU's itemid may still use pre-flattening identifiers (skull,
        # red_flower, fish, etc.). ItemModel is the current render item and is
        # therefore the safest base stack on Minecraft 26.1.2.
        base_item = model.group(1) if model else data.get("itemid", "minecraft:paper")
        if base_item == "minecraft:skull": base_item = "minecraft:player_head"
        accessories[item_id] = {
            "name": clean(data.get("displayname", item_id)),
            "base_item": base_item,
            "item_model": model.group(1) if model else base_item,
            "texture": texture.group(1) if texture else "",
        }
    for item_id, (name, model) in OFFICIAL_API_PET_ITEMS.items():
        accessories.setdefault(item_id, {
            "name": name,
            "base_item": "minecraft:paper",
            "item_model": model,
            "texture": "",
        })

    (OUTPUT / "pet_skin_names.json").write_text(
        json.dumps(skin_names, indent=2, sort_keys=True) + "\n"
    )
    (OUTPUT / "pet_accessories.json").write_text(
        json.dumps(accessories, indent=2, sort_keys=True) + "\n"
    )
    (OUTPUT / "pet_skin_texture_index.json").write_text(
        json.dumps(texture_to_skin, indent=2, sort_keys=True) + "\n"
    )
    (OUTPUT / "pet_head_profiles.json").write_text(
        json.dumps(pet_heads, indent=2, sort_keys=True) + "\n"
    )
    (OUTPUT / "pet_skin_profiles.json").write_text(
        json.dumps(skin_profiles, indent=2, sort_keys=True) + "\n"
    )
    print(f"wrote {len(pet_heads)} pet heads, {len(skin_names)} skin names, "
          f"{len(texture_to_skin)} texture mappings, {len(skin_profiles)} skin profiles, "
          f"and {len(accessories)} accessories")


def texture_hash(encoded: str) -> str:
    try:
        encoded = encoded.rsplit(":", 1)[-1]
        encoded += "=" * (-len(encoded) % 4)
        payload = json.loads(base64.b64decode(encoded).decode("utf-8"))
        url = payload["textures"]["SKIN"]["url"]
        return url.rstrip("/").rsplit("/", 1)[-1]
    except (ValueError, KeyError, UnicodeDecodeError, json.JSONDecodeError):
        return ""


if __name__ == "__main__":
    main()

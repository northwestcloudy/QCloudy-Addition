#!/usr/bin/env python3
"""Build offline 3D pet-head textures from local SkyBlock item metadata.

Only generated 32x32 icons are bundled. Runtime pet display never contacts the Wiki,
item repo, Hypixel APIs, or textures.minecraft.net.
"""

from __future__ import annotations

import base64
import concurrent.futures
import io
import json
import re
import urllib.error
import urllib.request
from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/resources/assets/qcloudy_addition/textures/gui/pets"
SKIN_OUTPUT = ROOT / "src/main/resources/assets/qcloudy_addition/textures/gui/pet_skins"
REPO_CANDIDATES = (
    ROOT / "run/.firmament/repo-extracted/items",
    ROOT / "run/config/skyblocker/item-repo/items",
    ROOT / "run/config/notenoughupdates/repo/items",
)
VALUE_PATTERN = re.compile(r'Value:\"([A-Za-z0-9+/=]+)\"')
FORMATTING_PATTERN = re.compile(r"§.")


def item_repo() -> Path:
    for candidate in REPO_CANDIDATES:
        if candidate.is_dir():
            return candidate
    raise RuntimeError("No local NEU item-repo snapshot found")


def pet_urls() -> dict[str, tuple[int, str, str]]:
    result: dict[str, tuple[int, str, str]] = {}
    for path in item_repo().glob("*;*.json"):
        try:
            key, tier_text = path.stem.rsplit(";", 1)
            tier = int(tier_text)
            data = json.loads(path.read_text())
        except (ValueError, OSError, json.JSONDecodeError):
            continue
        if "[Lvl {LVL}]" not in data.get("displayname", ""):
            continue
        match = VALUE_PATTERN.search(data.get("nbttag", ""))
        if match is None or (key in result and result[key][0] >= tier):
            continue
        try:
            payload = json.loads(base64.b64decode(match.group(1)).decode("utf-8"))
            url = payload["textures"]["SKIN"]["url"].replace("http://", "https://")
        except (ValueError, KeyError, UnicodeDecodeError, json.JSONDecodeError):
            continue
        display = FORMATTING_PATTERN.sub("", data.get("displayname", ""))
        display = display.replace("[Lvl {LVL}]", "").strip()
        result[key] = (tier, url, display)
    return result


def skin_urls() -> dict[str, tuple[str, str]]:
    result: dict[str, tuple[str, str]] = {}
    for path in item_repo().glob("PET_SKIN_*.json"):
        try:
            data = json.loads(path.read_text())
            match = VALUE_PATTERN.search(data.get("nbttag", ""))
            if match is None:
                continue
            payload = json.loads(base64.b64decode(match.group(1)).decode("utf-8"))
            url = payload["textures"]["SKIN"]["url"].replace("http://", "https://")
            key = data.get("internalname", path.stem).removeprefix("PET_SKIN_").lower()
            result[key] = (url, url.rstrip("/").rsplit("/", 1)[-1])
        except (ValueError, OSError, KeyError, UnicodeDecodeError, json.JSONDecodeError):
            continue
    return result


def _layer(skin: Image.Image, box: tuple[int, int, int, int], overlay_box: tuple[int, int, int, int]) -> Image.Image:
    base = skin.crop(box)
    overlay = skin.crop(overlay_box)
    base.alpha_composite(overlay)
    return base


def _shade(color: tuple[int, int, int, int], factor: float) -> tuple[int, int, int, int]:
    red, green, blue, alpha = color
    return (min(255, round(red * factor)), min(255, round(green * factor)),
            min(255, round(blue * factor)), alpha)


def _point(quad: tuple[tuple[float, float], ...], u: float, v: float) -> tuple[float, float]:
    top_x = quad[0][0] + (quad[1][0] - quad[0][0]) * u
    top_y = quad[0][1] + (quad[1][1] - quad[0][1]) * u
    bottom_x = quad[3][0] + (quad[2][0] - quad[3][0]) * u
    bottom_y = quad[3][1] + (quad[2][1] - quad[3][1]) * u
    return top_x + (bottom_x - top_x) * v, top_y + (bottom_y - top_y) * v


def _draw_face(target: Image.Image, source: Image.Image,
               quad: tuple[tuple[float, float], ...], shade: float):
    draw = ImageDraw.Draw(target)
    for py in range(8):
        for px in range(8):
            color = source.getpixel((px, py))
            if color[3] == 0:
                continue
            u0, u1 = px / 8.0, (px + 1) / 8.0
            v0, v1 = py / 8.0, (py + 1) / 8.0
            polygon = [_point(quad, u0, v0), _point(quad, u1, v0),
                       _point(quad, u1, v1), _point(quad, u0, v1)]
            draw.polygon(polygon, fill=_shade(color, shade))


def face_icon(raw: bytes) -> Image.Image:
    skin = Image.open(io.BytesIO(raw)).convert("RGBA")
    # Render a compact item-style cube instead of a flat face crop. Each original
    # skin pixel becomes a polygon, preserving the crisp Minecraft pixel language.
    front = _layer(skin, (8, 8, 16, 16), (40, 8, 48, 16))
    side = _layer(skin, (0, 8, 8, 16), (32, 8, 40, 16))
    top = _layer(skin, (8, 0, 16, 8), (40, 0, 48, 8))
    icon = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    _draw_face(icon, top, ((3, 8), (12, 3), (29, 8), (20, 13)), 1.12)
    _draw_face(icon, side, ((20, 13), (29, 8), (29, 24), (20, 29)), 0.72)
    _draw_face(icon, front, ((3, 8), (20, 13), (20, 29), (3, 24)), 1.0)
    return icon


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    SKIN_OUTPUT.mkdir(parents=True, exist_ok=True)
    request_headers = {"User-Agent": "AutumeCloudyAddition asset generator"}
    entries = pet_urls()
    def build(entry):
        key, (_, skin_url, _display_name) = entry
        request = urllib.request.Request(skin_url, headers=request_headers)
        with urllib.request.urlopen(request, timeout=20) as response:
            icon = face_icon(response.read())
        return key, icon

    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
        built = executor.map(build, sorted(entries.items()))
        for index, (key, icon) in enumerate(built, 1):
            icon.save(OUTPUT / f"{key.lower()}.png", optimize=True)
            print(f"[{index:02}/{len(entries)}] {key} (3D head)")
    (OUTPUT / "index.json").write_text(json.dumps(sorted(key.lower() for key in entries), indent=2) + "\n")

    skins = skin_urls()
    def build_skin(entry):
        key, (url, texture_hash) = entry
        destination = SKIN_OUTPUT / f"{key}.png"
        if destination.is_file():
            return key, texture_hash, None, True
        try:
            request = urllib.request.Request(url, headers=request_headers)
            with urllib.request.urlopen(request, timeout=20) as response:
                return key, texture_hash, face_icon(response.read()), True
        except (OSError, urllib.error.URLError, ValueError):
            return key, texture_hash, None, False

    texture_index = {}
    available_skins = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
        built_skins = executor.map(build_skin, sorted(skins.items()))
        for index, (key, texture_hash, icon, available) in enumerate(built_skins, 1):
            if not available:
                print(f"[skin {index:03}/{len(skins)}] {key} (unavailable)")
                continue
            if icon is not None:
                icon.save(SKIN_OUTPUT / f"{key}.png", optimize=True)
            available_skins.append(key)
            texture_index[texture_hash] = key
            print(f"[skin {index:03}/{len(skins)}] {key}")
    (SKIN_OUTPUT / "index.json").write_text(json.dumps(sorted(available_skins), indent=2) + "\n")
    (SKIN_OUTPUT / "texture_index.json").write_text(json.dumps(texture_index, indent=2, sort_keys=True) + "\n")


if __name__ == "__main__":
    main()

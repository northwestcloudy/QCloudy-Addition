#!/usr/bin/env python3
"""Generate offline Shard item-model resources from a reviewed SkyShards checkout.

The script performs no network access.  Its input is the MIT-licensed
``public/shardIcons`` directory from the reviewed SkyShards commit recorded in
``shard_fusions.json``.  The reviewed SkyShards set is supplemented with the
0.27.2 Folf, Flora, and Packrat images; legacy L49 Rainbug is ignored.
"""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
from pathlib import Path


EXPECTED_COUNT = 324
EXPECTED_SOURCE_EXTRA = {"L49"}
EXPECTED_SUPPLEMENT_IDS = {"R28", "L52", "U73"}
NAMESPACE = "qcloudy_addition"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--catalog", type=Path, required=True)
    parser.add_argument("--source-dir", type=Path, required=True)
    parser.add_argument("--supplement-dir", type=Path, required=True)
    parser.add_argument("--assets-dir", type=Path, required=True)
    parser.add_argument(
        "--max-size",
        type=int,
        default=0,
        help="Optional longest PNG edge; requires the macOS sips command.",
    )
    return parser.parse_args()


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    args = parse_args()
    catalog = json.loads(args.catalog.read_text(encoding="utf-8"))
    shards = catalog.get("shards")
    if not isinstance(shards, list) or len(shards) != EXPECTED_COUNT:
        raise ValueError(f"expected {EXPECTED_COUNT} catalog Shards")

    catalog_ids = {str(shard.get("id", "")) for shard in shards}
    if len(catalog_ids) != EXPECTED_COUNT or "L49" in catalog_ids or "" in catalog_ids:
        raise ValueError(
            f"catalog IDs must be {EXPECTED_COUNT} unique current IDs without L49 Rainbug"
        )

    source_by_id = {path.stem: path for path in args.source_dir.glob("*.png")}
    missing = catalog_ids - source_by_id.keys()
    extra = source_by_id.keys() - catalog_ids
    if set(missing) != EXPECTED_SUPPLEMENT_IDS or set(extra) != EXPECTED_SOURCE_EXTRA:
        raise ValueError(
            f"reviewed icon set mismatch; missing={sorted(missing)}, extra={sorted(extra)}"
        )
    supplement_by_id = {path.stem: path for path in args.supplement_dir.glob("*.png")}
    if set(supplement_by_id) != EXPECTED_SUPPLEMENT_IDS:
        raise ValueError(
            "reviewed supplement mismatch; "
            f"expected={sorted(EXPECTED_SUPPLEMENT_IDS)}, actual={sorted(supplement_by_id)}"
        )
    source_by_id.update(supplement_by_id)

    textures = args.assets_dir / NAMESPACE / "textures" / "item" / "shards"
    models = args.assets_dir / NAMESPACE / "models" / "item" / "shards"
    items = args.assets_dir / NAMESPACE / "items" / "shards"
    textures.mkdir(parents=True, exist_ok=True)

    for shard_id in sorted(catalog_ids):
        resource_id = shard_id.lower()
        texture = textures / f"{resource_id}.png"
        shutil.copyfile(source_by_id[shard_id], texture)
        if args.max_size:
            if args.max_size < 16:
                raise ValueError("--max-size must be at least 16")
            subprocess.run(
                ["sips", "-Z", str(args.max_size), str(texture)],
                check=True,
                stdout=subprocess.DEVNULL,
            )

        model_id = f"{NAMESPACE}:item/shards/{resource_id}"
        write_json(
            models / f"{resource_id}.json",
            {
                "parent": "minecraft:item/generated",
                "textures": {"layer0": model_id},
            },
        )
        write_json(
            items / f"{resource_id}.json",
            {
                "model": {
                    "type": "minecraft:model",
                    "model": model_id,
                }
            },
        )

    print(f"wrote {len(catalog_ids)} Shard icon resource sets to {args.assets_dir}")


if __name__ == "__main__":
    main()

# Third-party notices and reference provenance

QCloudy_Addition's Java implementation and visual design are original. The following open-source projects were inspected to understand Minecraft 26.1.2/26.2 APIs and resilient client-side patterns. They are not bundled as dependencies unless the table explicitly says otherwise.

| Project | Inspected version / commit | License | Use in QCA |
|---|---|---|---|
| Firmament | `44.3.0+mc26.1` / `d7fb24768ab2e211cc9237ce0197ff8cfeae5a37` | GPL-3.0-or-later (file-specific exceptions possible) | Architecture and compatibility reference only; no source or resource copied |
| Skyblocker | `v6.8.2+26.1.2` / `0f53a9cb7cafc4fd94f20de5e84e84168dde90a9` | LGPL-3.0-or-later | Reference for current HUD extraction, player-map transform, and vanilla outline integration; QCA code is independently written |
| SkyHanni | `7.41.0` / `3d047fc5a4683f51d73215ee31e8392f8a2f4c5c` | LGPL-2.1 | Reference for current Tab/chat formats |
| BabyzombieAddons | `v3.4.1` / `821fc044e336c551c8e2d27b627c8782793a68a1` | LGPL-3.0 | Reference for current client event/accessor patterns and bounded loaded-entity scanning; QCA code is independently written |
| Feesh | `1.13.0-alpha` / `9a5f9e074492a0f6c0eb4f6251ac361b7afb3992` | Apache-2.0 | Reference for its public Kotlin setting accessors, native save path, live Overlay registry, alignment anchors, and coordinate persistence; no Feesh source or resource is copied |
| Hypixel Mod API | core `1.0.2`; Fabric `1.0.2+build.1+mc26.1` | MIT | Official Fabric implementation bundled for Hello/Location session detection; QCA does not implement the protocol itself |
| SkyShards / SkyShards Parser | icons: `9688031dbc4e726168ffceb0f44884ff26e6e728`; planner-rate review: `382493203744df7f563d47bd6b3c30460725d047` | MIT, Copyright (c) 2026 Campion | Shard identifiers/properties, Special Fusion and acquisition-rate cross-checks; 320 PNGs and a normalized 320-entry baseline rate table are transformed and bundled, but no SkyShards application code is included |
| NotEnoughUpdates item repository | local 2026-08-04 snapshot | MIT, Copyright (c) 2020 Moulberry | Pet identifiers, rarity XP offsets, special level-200 pet metadata, and fallback skin texture references |
| Mod Menu API | 18.0.0 | MIT | Optional compile-only configuration entrypoint; Mod Menu is not bundled |

## User-supplied fishing audio

`assets/qcloudy_addition/sounds/fishing/ciallo.ogg` is a transcoded copy of `Ciallo.mp3`, supplied by the project owner specifically for the Fishing Bite Sound feature. QCA bundles it inside its client resource pack and never downloads it at runtime. The project publisher is responsible for confirming redistribution rights before making a public release that contains this audio.

## Century Cake metadata and icons

The 20-entry Century Cake catalog is generated before release from compact factual effect mappings verified against the [Hypixel SkyBlock Wiki Century Cake page](https://hypixelskyblock.minecraft.wiki/w/Century_Cake) and the official [SkyBlock Year 500 Century Celebration announcement](https://hypixel.net/threads/skyblock-year-500-century-celebration.6114883/). Cake names and player-head texture properties are read from the local MIT-licensed NotEnoughUpdates item-repository snapshot already credited above. The committed JSON contains only identifiers, names, bonuses, rarity, and texture properties needed for local display. QCA performs no runtime Wiki, forum, repository, Hypixel API, or texture-metadata request; Minecraft may resolve a normal player-head texture through its standard client texture path when the effects screen renders that head.

## Hypixel SkyBlock pet resources

Compact offline metadata for 88 base-pet profiles, 352 applied-pet-skin profiles, 5,422 pet-owned current/animated texture mappings, and 87 pet accessories is generated from the local NotEnoughUpdates item-repository snapshot. QCA constructs a normal Minecraft player head from the verified profile at runtime. It does not ship or select pre-rendered pet PNG fallbacks, does not add synthetic SkyBlock `petInfo`, and does not reuse an unrelated nearby item stack. These resources are used only to interpret and present client-received pet data. No Wiki, item-repository, Hypixel API, or texture request occurs while the mod is running.

The [Hypixel SkyBlock Wiki pet leveling table](https://hypixelskyblock.minecraft.wiki/w/Pets) was used to verify the rarity-adjusted level 1–100 XP curve. Local item-repository constants were used to verify Golden Dragon, Jade Dragon, and Rose Dragon level-200 extensions. Minecraft and Hypixel visual assets remain the property of their respective owners and are used subject to the applicable site terms, Minecraft EULA, and usage guidelines.

## Attribute Shard Fusion reference data

The bundled `assets/qcloudy_addition/data/shard_fusions.json` is generated before release. Fusion quantities, ordered ID-output behavior, special outputs, Chameleon stepping/rarity rollover, the three-output limit, and the Pure Reptile double-output rule were verified against the community [Attribute Fusion](https://hypixelskyblock.minecraft.wiki/w/Attribute_Fusion) documentation. Normalized effect and acquisition summaries are derived from the current community [Attributes tables](https://hypixelskyblock.minecraft.wiki/w/Attributes). Shard names, IDs, rarities, attributes, categories, families, skills, and special-rule pairs were cross-checked against [SkyShards](https://github.com/Campionnn/SkyShards), its parser data at commit `9688031dbc4e726168ffceb0f44884ff26e6e728`, and the [NotEnoughUpdates item repository](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO). QCA's Java implementation, indexes, fusion evaluation, caches, and UI are independently written.

The final catalog is an exact allow-list intersection with the Shard products returned by the [official Hypixel Bazaar endpoint](https://api.hypixel.net/v2/skyblock/bazaar) in the offline source snapshot. This produces 320 current Bazaar-listed Shards. It corrects the earlier 317-entry snapshot by including Anteater, Zombuddy, Troodon, and Ghost Crab, assigning Goldolot to `R92`, and excluding Rainbug because no Rainbug Shard product exists in that official Bazaar universe. The Wiki [Attributes list](https://hypixelskyblock.minecraft.wiki/w/Attributes) is explicitly marked incomplete/outdated, so it is used for human-readable rule verification rather than as the cardinality authority.

`tools/generate_shard_fusion_data.py` records the reviewed offline data transformation and performs strict identity/count validation before writing the committed JSON. The Shard-icon generator reads SkyShards `public/shardIcons/<Shard ID>.png` at reviewed commit `9688031dbc4e726168ffceb0f44884ff26e6e728`, filters the 321-source set through QCA's exact 320-Shard allow-list (excluding Rainbug), downscales each image to a maximum 64-pixel side, and emits local Minecraft item textures/models. The running QCA code contains no HTTP client and never contacts the Wiki, Bazaar API, SkyShards, NEU, or an icon service. When available, the guide gives an already-received native ItemStack priority over the bundled local model; QCA does not request that stack or initiate an additional texture fetch.

`assets/qcloudy_addition/data/shard_rates.json` is a normalized offline planning baseline reviewed against SkyShards' MIT-licensed rate data at commit `382493203744df7f563d47bd6b3c30460725d047`. QCA filters out Rainbug, requires exact one-to-one identity with its official 320-Shard catalog, and allows the player to replace any baseline with a local rate. The planner and route algorithm are independently written Java code. The running mod does not contact `skyshards.com` or its repository.

The transformed SkyShards icon resources are redistributed under the following MIT License:

> MIT License
>
> Copyright (c) 2026 Campion
>
> Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
>
> The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
>
> THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

The linked Hypixel SkyBlock Wiki pages are published under [CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/). The normalized effect/acquisition strings in `shard_fusions.json` are attributed to that Wiki and distributed under the same terms; `assets/qcloudy_addition/data/SHARD_DATA_NOTICE.txt` carries this attribution inside the JAR. QCA does not bundle Wiki images. The program code remains under the repository's LGPL-3.0-or-later license.

## SkyHanni-REPO map graph

The bundled Dwarven Mines and Glacite Tunnels PNGs are generated schematic derivatives of `constants/island_graphs/DWARVEN_MINES.json` and `GLACITE_TUNNELS.json` from the `SkyHanni-REPO` snapshot embedded in SkyHanni 7.41.0. The source repository is licensed under the MIT License:

> MIT License
>
> Copyright (c) 2022 hannibal2
>
> Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
>
> The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
>
> THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

`tools/generate_maps.py` documents and reproduces the transformation when the inspected SkyHanni JAR is present locally. QCA does not bundle any external provider JAR or provider class.

## Minecraft bitmap fonts

The generated Dwarven Mines PNGs use glyph bitmaps read from the locally installed Minecraft 26.1.2 `ascii.png` and `unifont.zip` assets so map labels match Minecraft's default visual language. The original font files are not separately bundled by QCA; only the small rendered labels inside the finished map textures are included. Minecraft assets remain property of Mojang Studios and are used subject to the Minecraft EULA and Usage Guidelines.

## Torrhus Canyon and Critter Safari reference data

The official Hypixel SkyBlock Wiki pages for [Torrhus Canyon](https://hypixelskyblock.minecraft.wiki/w/Torrhus_Canyon), [Critter Safari](https://hypixelskyblock.minecraft.wiki/w/Critter_Safari), [Safari Critters](https://hypixelskyblock.minecraft.wiki/w/Critters/Critter_Safari), [Safari Belt](https://hypixelskyblock.minecraft.wiki/w/Safari_Belt), [Tree Gifts](https://hypixelskyblock.minecraft.wiki/w/Tree_Gifts), [Wumpa](https://hypixelskyblock.minecraft.wiki/w/Wumpa), [Doomspiral](https://hypixelskyblock.minecraft.wiki/w/Doomspiral), [Torrhus Fairy Souls](https://hypixelskyblock.minecraft.wiki/w/Fairy_Souls/List/Torrhus_Canyon), [Critter Safari Fairy Souls](https://hypixelskyblock.minecraft.wiki/w/Fairy_Souls/List/Critter_Safari), and [Starlyn Contest](https://hypixelskyblock.minecraft.wiki/w/Starlyn_Contest) were used to verify names, Critter lists and Shard rarities, behavior, ticket tiers, contest thresholds, quest-item names, Fairy Soul coordinates, milestones, and received-message formats. QCA stores only compact factual identifiers, coordinates, and thresholds needed for local parsing/rendering. It does not bundle Wiki images or page text and makes no Wiki or Hypixel API request at runtime.

Safari Belt bonus values are read from the item lore already received by the client instead of hard-coding a potentially stale total. Torrhus and Safari trackers likewise consume only received scoreboard, Tab, chat, title, entity-name, inventory, and already-loaded block-state data; they do not request server state or perform gameplay actions.

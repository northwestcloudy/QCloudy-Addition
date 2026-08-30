# QCloudy_Addition

**A bilingual, client-only Hypixel SkyBlock utility mod for Fabric.**

QCloudy_Addition combines readable maps, content-aware HUDs, fishing and hunting alerts, pet information, Century Cake timers, deployable expiry warnings, and a complete offline Attribute Shard guide and planner in one function-first interface.

> **Latest stable version: Release 0.3.9 for Minecraft 26.1.2 and 26.2.** Download the JAR that exactly matches your Minecraft version. Java 25 and Fabric API are required.

## Experimental unified editors

> **Caution: Unified Settings Editor and Unified HUD Editor are concept tests. They are disabled by default and are not yet stable.** Provider updates can change internal configuration or HUD structures without notice. Enable them cautiously, keep configuration backups, and verify every change in the provider mod's own settings/HUD editor. The provider's native editor remains authoritative.

When explicitly enabled and confirmed, QCA can discover recognised capabilities from installed SkyHanni, Skyblocker, Firmament, BabyZombieAddons, and Feesh builds. Settings and HUD scanning are separate, every scan or refresh requires a second confirmation, unknown branches fail closed, empty categories stay hidden, and a read-only Compatibility Gaps page lists recognised features QCA could not safely manage. These mods are optional and are never required for QCA to start.

## Highlights

### Attribute Shard Lab

- Offline catalog for all 320 Bazaar-listed Attribute Shards, with per-ID icons, rarity and semantic game colours.
- Details include effect, family, skill, mob type, natural acquisition, capture/kill requirements, reviewed drop information, and Fusion-only status.
- Ordered Recipes and Uses pages, reverse relationships, clickable navigation, Special Fusion yields, Chameleon behavior, and alternative routes.
- Multi-step Fusion Tree, Materials Only totals, editable Shards/hour rates, Ironman planning, draggable Fusion Lines, and a per-profile Hunting Box warehouse.
- Bazaar-aware routes use only an optional compatible provider's existing client cache. QCA never contacts Bazaar, the Wiki, or a pricing server at runtime; price modes become unavailable when no suitable provider exists.

### HUDs, pets, and timers

- Equipped Pet HUD with received level, rarity-coloured name, verified pet/skin head, XP progress, remaining XP, skin, and held pet item.
- Mining tasks and powders, Torrhus and Galatea resources, Safari progress, Crimson Isle faction quests, and other panels render only when they have useful content.
- Century Cake menu via `/cake` or `/centurycakeeffect`, real-world 48-hour timers, grouped expiry notifications, and a directly clicked renewal action.
- Power Orb and Flare despawn alerts. Replacing a Flare resets its complete lifecycle; range, entity unloading, and failed uses are not treated as despawns.
- Fishing Ciallo cue for the local player's confirmed water or lava bite, disabled by default and deduplicated per hook.

### Maps and visual helpers

- Dwarven Mines overview with a continuous approximate X/Z projection. Y and scoreboard sub-location names are deliberately ignored, so bridges above The Mist do not switch layers or hide the marker.
- Three coordinate-aligned Glacite maps selected by elevation.
- Optional Fairy Soul waypoints, Ender Dragon outlines, Beeheemoth helper, Lasso REEL cue, Warden readiness, Tree Gift alerts, and other passive client-visible helpers.

## Installation

| Minecraft | Required Fabric API | Playable file |
|---|---|---|
| 26.1.2 | 0.155.2+26.1.2 or newer compatible build | `QCloudy_Addition-0.3.9+26.1.2-Release.jar` |
| 26.2 | matching 26.2 Fabric API build | `QCloudy_Addition-0.3.9+26.2-Release.jar` |

Also required: Fabric Loader 0.19.3 or newer and Java 25. Mod Menu is optional. Put exactly one playable JAR in the instance's `mods` folder; do not install a `-sources.jar` as the mod.

Open QCA with `O`, Mod Menu, `/qca`, or `/qc`. `/qca`, `/qc`, `/qshard`, `/cake`, and `/centurycakeeffect` are local client commands.

## Release update notice — next Beta/Release

Release 0.3.9 predates this checker. The checker is present but network-disabled in the Alpha 37 development build and will first become active in the next published Beta or Release. Once active, the Release update notice is always enabled and is not a settings card. Alpha builds never contact the update endpoint. Beta and Release builds make at most one asynchronous HTTPS request per client process to QCloudy's stable Release manifest after the first world join. Only a newer stable Release with an exact JAR for the running Minecraft version can trigger one toast and one local chat message; Alpha and Beta builds are never offered as update targets. The message links to QCloudy's download and changelog pages. QCA does not download, install, replace, or launch a mod file.

The request sends no Minecraft username, UUID, server address, profile, mod list, gameplay data, telemetry identifier, token, or cookie. Normal HTTPS still exposes the connecting IP address and `QCloudy_Addition/<version>` HTTP User-Agent to the website server. Network or validation failure is silent and is not retried during that client process.

## Client-only boundary

QCA reads information already delivered to the client: received Tab/scoreboard/chat/title text, open menus, local inventory, loaded entities, and already-loaded blocks. It does not automate movement, clicks, combat, fishing, captures, Fusions, or reconnect loops; it has no telemetry, automatic downloader/updater, hidden chunk request, or runtime Hypixel API dependency. Its only QCA-owned runtime web request is the bounded stable Release-manifest check disclosed above.

The always-available local `/th` and `/helia` shortcuts send `warp torrhus` and `chapter torrhus` only when the player enters those shortcuts; the Century Cake renewal action sends `visit northwestcloudy` only when its chat action is clicked. Separately enabled party/chat tools can send their documented Party, private-message, Stream, coordinate, Dungeon, and Kuudra command payloads only after their own master/child switches, sender scope, exact parser, player resolution, and cooldown gates permit them. These tools never simulate a click, move the player, or use an item.

## Compatibility and disclaimer

QCloudy_Addition runs without SkyHanni, Skyblocker, Firmament, BabyZombieAddons, Feesh, JEI, or Mod Menu. Optional adapters use capability discovery instead of an exact-version whitelist, but this cannot make undocumented third-party internals stable; unsupported structures are omitted.

All Minecraft modifications are used at the player's own risk. QCloudy_Addition is an independent community project and is not affiliated with or endorsed by Hypixel Studios, Mojang Studios, or Microsoft.

- Website: [qcloudy.net](https://qcloudy.net/)
- Downloads: [qcloudy.net/download](https://qcloudy.net/download/)
- Source: [GitHub](https://github.com/northwestcloudy/QCloudy-Addition)
- Issues: [GitHub Issues](https://github.com/northwestcloudy/QCloudy-Addition/issues)
- Wiki: [GitHub Wiki](https://github.com/northwestcloudy/QCloudy-Addition/wiki)

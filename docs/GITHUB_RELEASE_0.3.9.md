# QCloudy_Addition 0.3.9 Release

Stable client-only Fabric release for Minecraft 26.1.2 and 26.2. This release consolidates all public work completed since the previous stable Release 2.5.3.

> **Experimental warning:** Unified Settings Editor and Unified HUD Editor are concept tests. They are disabled by default and are not yet stable. Provider updates may invalidate recognised mappings. Back up provider configuration, use these editors cautiously, and verify changes in the provider mod's native editor.

## Highlights since Release 2.5.3

- Added the complete offline 320-Shard guide and planner: Shard details, natural and Fusion acquisition, Recipes, Uses, alternate routes, multi-step Fusion Trees, Materials Only, Ironman, optional provider-cached price routes, editable acquisition rates, Fusion Lines, and the locally observed Hunting Box warehouse.
- Added Shard-specific bundled icons, game/Wiki-semantic colours, clickable navigation, compact recipe layouts, responsive narrow-screen layouts, and corrected search focus handling.
- Added a default-off Fishing Bite Sound with the bundled Ciallo cue, independent volume, water/lava support, and one playback per hook.
- Added Power Orb and Flare despawn alerts. Power Orbs use exact personal despawn chat; Warning, Alert, and SOS Flares use a confirmed three-minute lifecycle, including a full timer reset after replacement.
- Added tracking for all 20 Century Cakes, real-world 48-hour timers, `/cake`, `/centurycakeeffect`, grouped expiry alerts, and a directly clicked renewal action.
- Added optional, separately gated unified settings and HUD discovery for recognised capabilities from SkyHanni, Skyblocker, Firmament, BabyZombieAddons, and Feesh, plus confirmation, progress, Refresh, and Compatibility Gaps views.
- Replaced the Dwarven Mines marker logic with a continuous approximate X/Z-only projection that ignores Y and scoreboard sub-location snapping.
- Fixed Tree Gift personal creature alerts, Park Jungle/mining-HUD confusion, Golden/Jade Dragon level display, fishing duplicate playback, Century Cake first-use/refresh parsing, SOS replacement timing, and multiple UI overlap/focus issues.

## Removed or replaced

- Removed slot locking, Storage Overlay, and menu middle-click conversion.
- Removed `/aca` and `/ca`; `/qca` and `/qc` remain.
- Removed the old Dwarven regional/Y-layer snapping and incomplete Flare chat/range/entity-unload guesses.
- Replaced exact provider-version whitelisting with per-capability validation; unknown branches fail closed.

## Compatibility

- Minecraft 26.1.2: Fabric API `0.155.2+26.1.2` or a newer compatible build.
- Minecraft 26.2: Fabric API `0.154.2+26.2` or a newer compatible build.
- Fabric Loader 0.19.3 or newer and Java 25.
- QCA remains standalone and client-only. Provider mods and Mod Menu are optional.

## Files

- `QCloudy_Addition-0.3.9+26.1.2-Release.jar`
- `QCloudy_Addition-0.3.9+26.1.2-Release-sources.jar`
- `QCloudy_Addition-0.3.9+26.2-Release.jar`
- `QCloudy_Addition-0.3.9+26.2-Release-sources.jar`

Install exactly one playable JAR matching your Minecraft version. Do not install a `-sources.jar` as the mod.

Full cumulative details: [CHANGELOG.md](https://github.com/gprztb6nw4-dotcom/QCloudy-Addition/blob/main/CHANGELOG.md)

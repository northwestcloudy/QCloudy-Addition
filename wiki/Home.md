# QCloudy_Addition Wiki

[简体中文](Home-zh-CN)

![QCloudy_Addition icon](https://raw.githubusercontent.com/gprztb6nw4-dotcom/QCloudy-Addition/main/src/main/resources/assets/qcloudy_addition/icon.png)

QCloudy_Addition (QCA) is an English-first, bilingual, client-only Fabric mod for Hypixel SkyBlock. It organizes maps, HUDs, passive visual helpers, pet information, Attribute Shard tools, and selected quality-of-life controls in one function-first interface.

> **Current stable version:** Release 0.3.9<br>
> **Minecraft:** 26.1.2 and 26.2<br>
> **Required:** Java 25, Fabric Loader 0.19.3 or newer, and the matching Fabric API<br>
> **Optional:** Mod Menu and reviewed builds of supported SkyBlock mods<br>
> **Notice:** QCA is an independent community project. It is not affiliated with or endorsed by Hypixel, Mojang, Microsoft, SkyHanni, Skyblocker, Firmament, BabyZombieAddons, or Feesh.

> **Experimental integration warning:** Unified Settings Editor and Unified HUD Editor are concept tests. They are disabled by default and are not yet stable. Provider updates can invalidate recognised fields or HUD contracts. Back up provider configuration and verify important changes in the provider's own editor.

## Contents

- [Installation](#installation)
- [What changed since Release 2.5.3](#what-changed-since-release-253)
- [Opening the mod](#opening-the-mod)
- [Settings, language, and HUD editing](#settings-language-and-hud-editing)
- [Feature guide](#feature-guide)
- [Attribute Shard Guide and Planner](#attribute-shard-guide-and-planner)
- [Unified controls for compatible SkyBlock mods](#unified-controls-for-compatible-skyblock-mods)
- [Client-only and safety boundary](#client-only-and-safety-boundary)
- [Commands and outbound actions](#commands-and-outbound-actions)
- [Saved data](#saved-data)
- [Compatibility and troubleshooting](#compatibility-and-troubleshooting)
- [Reporting a bug](#reporting-a-bug)
- [Validation, license, and credits](#validation-license-and-credits)

## Installation

Release 0.3.9 has two Minecraft targets. Install the JAR that exactly matches the game version.

| Minecraft | Required Fabric API | Playable file |
|---|---|---|
| 26.1.2 | 0.155.2+26.1.2 or newer compatible build | `QCloudy_Addition-0.3.9+26.1.2-Release.jar` |
| 26.2 | 0.154.2+26.2 or newer compatible build | `QCloudy_Addition-0.3.9+26.2-Release.jar` |

This target requires Fabric Loader 0.19.3 or newer and Java 25.

1. Install the matching Minecraft, Fabric Loader, Fabric API, and Java versions.
2. Download the playable QCA JAR from the [official QCloudy download page](https://qcloudy.net/download/). The file itself is served from the official GitHub Release asset.
3. Put the playable JAR in the instance's `mods` folder.
4. Remove older QCA JARs from that folder so that only one QCA build is loaded.
5. Start Minecraft and open QCA with `O` or one of its local settings commands.

Do not install the file ending in `-sources.jar` as the playable mod. It contains source code for developers and IDEs.

QCA does not require SkyHanni, Skyblocker, Firmament, BabyZombieAddons, Feesh, or Mod Menu to load. Mod Menu only adds another way to open QCA settings.

## What changed since Release 2.5.3

Release 0.3.9 rolls the post-2.5.3 Alpha and Beta work into the next stable line.

- Added the complete offline 320-Shard Guide and Planner: recipes, reverse uses, acquisition/effect details, multi-step Fusion trees, alternative routes, Materials Only, Ironman/rate planning, Fusion Lines, and a player-observed Hunting Box warehouse.
- Added Century Cake effect tracking and expiry reminders, Power Orb/Flare despawn alerts, the Ciallo fishing-bite cue, Hunting/Safari helpers, improved pet/Dragon level handling, maps, HUDs, and presentation controls.
- Added optional, default-off unified settings and HUD integration for recognised live capabilities from SkyHanni, Skyblocker, Firmament, BabyZombieAddons, and Feesh. This remains an unstable concept test and must be used cautiously.
- Fixed repeated bite sounds, lava-fishing detection, personal Tree Gift creature alerts, Power Orb/Flare duplicate or stale timers, Century Cake Hunting Fortune recognition, empty category/HUD rendering, Fishing grouping, Golden/Jade Dragon level display, Shard UI alignment/focus/icons/colors, and Dwarven X/Z marker continuity.
- Removed `/aca` and `/ca`, Slot Locking, Storage Overlay, menu middle-click conversion, and unsafe assumptions about unknown provider structures.

See the full [English changelog](https://github.com/gprztb6nw4-dotcom/QCloudy-Addition/blob/main/CHANGELOG.md) for the detailed history.

## Opening the mod

You can open the settings in any of these ways:

- Press `O` by default. Rebind it under **Controls → Key Binds → QCloudy_Addition**.
- Type `/qca` or `/qc`.
- Use the QCA entry in Mod Menu when Mod Menu is installed.

Both slash aliases are local client commands. Each alias is registered only when another client command has not already claimed that name. They open a local screen and are not sent to Hypixel.

Use `/qshard [English query]` to open the offline Attribute Shard Guide with an optional prefilled search.

## Settings, language, and HUD editing

### Function-first categories

The left sidebar preserves this order for categories that currently contain at least one available feature. Empty categories are omitted entirely:

1. General
2. Maps
3. Items & Menus
4. Combat
5. Dungeons
6. Slayer
7. Mining
8. Farming
9. Foraging
10. Fishing
11. Hunting
12. Rift
13. Events

Safari is a collapsible subgroup under Hunting, Garden belongs under Farming, and Crimson Isle/Kuudra belong under Combat. The Fishing subgroup is named **Bite Alerts**, avoiding a redundant Fishing → Fishing hierarchy. A function has one owner and appears in one place rather than being duplicated across categories. Collapsible groups start closed.

### Feature cards

- Left-click a card to enable or disable the feature.
- An enabled card has a blue strip on its left edge.
- Right-click a card to open all settings belonging to that function.
- Secondary pages do not repeat the primary enable switch.
- Broad numeric values use draggable sliders. Feature sound volume is 0–100% and defaults to 64% unless explicitly documented otherwise.
- Editable colors use the shared RGB/HSV picker, presets, and a Transparent option for backgrounds.
- Keybind rows accept keyboard keys, mouse buttons, and Ctrl/Shift/Alt/Cmd-Super combinations. Press `Esc`, Backspace, or Delete while listening to clear a binding.

### Language

English is the default. Simplified Chinese can be selected inside QCA settings.

QCA translates its own interface text. Hypixel-provided item names, task names, location names, pets, skins, accessories, and player-renamed HOTM presets remain exactly as received from the client. This avoids incorrect translations and keeps searches compatible with in-game names.

### Edit HUD

Select **Edit HUD** at the bottom-left of the settings screen.

- Only enabled HUDs that are currently loaded and contain visible content are editable.
- Drag a panel to move it.
- Drag its border or corner to resize it like a desktop window.
- Every HUD stores its own 50–200% scale.
- The small gear on a HUD opens that HUD's own settings.
- Position and scale are saved when the mouse is released and persist after restart.
- Background color/opacity, border visibility/width/color, title color, bold text, and text shadow are configurable per HUD.
- A HUD with no visible rows renders nothing: no empty title, border, or background remains on screen.

## Feature guide

### General

- **Interface animations** control QCA's local menu transitions.
- **Alert master mute** suppresses QCA warning sounds without forcing every visual warning off.
- **Manual Reconnect** adds one normal Reconnect button to failed-connection and disconnect screens. It reconnects only after a physical click and has no timer or retry loop.
- **Chat Peek** temporarily expands already-received chat while a configurable key or chord is held. Its mouse wheel can scroll chat or remain assigned to the hotbar.

### Maps

- **Dwarven Mines Map** uses the supplied single-layer 12-region overview and a live red arrow. One continuous transform maps the local player's real-time **X/Z only** to the background; Y and scoreboard sub-location names are deliberately ignored. The marker is an approximate visual position, not a survey-grade coordinate. Bridges above The Mist therefore do not select a different floor or region transform.
- **Glacite Tunnels Map** uses low, middle, and high artwork selected from the local Y coordinate. All layers share one coordinate system, and point-of-interest labels use collision avoidance.
- **Fairy Soul Waypoints** is one Maps feature for the bundled Torrhus and Safari coordinates. The feature defaults off; enabling it activates both coordinate sets together. Pink beams disappear after a matching received success/already-found confirmation.

Map point names use canonical English names.

### Items & Menus

- **Equipped Pet HUD** shows received pet level, rarity-colored name, real pet/skin head, XP progress, remaining XP to maximum, optional skin name, and held pet item. It supports level-200 Dragon curves and Ancient Golden Dragon overflow levels. A max-level pet hides only the redundant remaining-XP line, not its accessory.
- **Attribute Shard Guide and Planner** provide the offline 320-Shard browser, direct recipes, uses, multi-step planning, warehouse, and Fusion Lines described in the dedicated section below.
- **Item timestamps** display locally observed item creation times.
- **Cursor memory** restores configured cursor positions for supported menus.
- **AOTE/AOTV sounds** let Instant Transmission and Etherwarp use the original sound or a local preset at a configurable 0–100% volume, default 64%. QCA does not change teleport distance, cooldown, movement, packets, or item use.

Slot Locking, Storage Overlay, and menu middle-click conversion were removed from QCA completely rather than merely hidden from settings.

### Combat

- **Ender Dragon Highlight** places received Hypixel Ender Dragons into Minecraft's vanilla outline pipeline while the player is in The End or Dragon's Nest. The color is configurable.
- **Power Orb & SOS Despawn Alert** uses exact player-owned despawn chat for Radiant, Mana Flux, Overflux, and Plasmaflux Power Orbs. Warning, Alert, and SOS Flare instead use an exact item-use candidate confirmed by the successful placement sound, followed by a local three-minute lifecycle. Failed placement, entity unload, player distance, and buff range never trigger an alert. Power Orb, Flare, center text, sound, and 0–100% volume are separately configurable; sound defaults to 64%.
- **Crimson Isle Faction Tasks** show incomplete rows from the received `Faction Quests:` Tab block using their original names and progress. Completed tasks are omitted.
- Recognised provider-backed Crimson Isle and Kuudra functions appear as Combat subgroups when the installed provider still exposes compatible live configuration capabilities.

### Dungeons

This category contains function-matched settings discovered from compatible live capabilities in installed providers. QCA does not invent a replacement Dungeon implementation when a provider exposes no recognised usable capability, and the category itself is hidden when it has no available feature.

### Slayer

This category contains exact function-matched Slayer settings supplied by compatible installed providers. Unsupported or incompatible adapters remain hidden.

### Mining

- **Mining Tasks & Powders** reads received Tab data in Dwarven Mines, Crystal Hollows, Glacite Tunnels, and Glacite Mineshafts.
- Crystal Hollows `Jungle` is matched as an exact received location; The Park's `Jungle Island` therefore cannot activate this HUD.
- Commission names are shown in full and use a separate measured progress bar.
- Progress can use one-decimal percentage mode or current/target mode.
- Mithril, Gemstone, and Glacite Powder are tracked separately.
- The optional `HOTM: <slot name>` row remembers a selected Heart of the Mountain preset observed in the relevant menu.

### Farming

Garden and Farming functions exposed through recognised live provider capabilities appear here. QCA merges only truly equivalent functions; a price tooltip, profit tracker, and task tracker are not treated as the same feature simply because all relate to Farming.

### Foraging

- **Torrhus Chapter & Resources** combines the current Helia Chapter, complete task/progress, Forest Whispers, Desert Whispers, Forest Essence, Safari Essence, Sweep, and Forest Fortune in one wrapped HUD.
- **Galatea tracking** has separate settings for Hina Chapter and Agatha's Contest while following the same content and empty-panel rules.
- **Tree Critter Timer** shows the exact visible server countdown from a loaded Tree Protection Order nameplate. It does not start a guessed timer.
- **Miria and Agatha Contest information** can show the next bracket, remaining score, and estimated Safari Ticket without duplicating the scoreboard timer.
- **Benefactor state** is assembled from bounded received Tab, scoreboard, chat, and physically opened menu content.
- **Tree Gift alerts** require the local player's ownership-proven reward block. Configured rare loot and exact creature lines can create a center title and local sound; an unrelated nearby player's public line cannot arm an alert by itself.

### Fishing

- Fishing functions are grouped under **Bite Alerts**, not another subgroup named Fishing.
- **Fishing Bite Sound** is disabled by default.
- It associates an exact nearby received `!!!` marker with the local player's water hook or bounded Hypixel lava-hook presentation.
- It plays the bundled Ciallo sound once per hook at a configurable 0–100% volume, default 64%.
- Reeling an active hook does not replay or re-arm the cue.
- QCA never casts or reels automatically.

### Hunting

- **Beeheemoth helper** offers a configurable outline, temporary yellow spawn beacon, and independent Beeheemoth sound control. The beacon clears when the player approaches, receives their own capture confirmation, or the entity disappears.
- **Lasso REEL cue** plays once when the local player's received Lasso state changes to the exact `REEL` label.
- **Critter Behavior Assistant** presents bounded center-screen prompts for documented Critter mechanics.

#### Safari subgroup

- **Safari Run Dashboard** tracks local run time and Ticket Tier. The optional caught-Shard statistics are a separate switch and default off.
- **Safari Run Critterdex** groups the current run by Cavern, Forest, Haunted, and Icy and shows captured/missing progress without truncating names.
- **Critter highlights** use the official Shard rarity color on the real visible entity and do not render through walls. Armor Stand capture props are excluded.
- **Cold Safety** provides configurable first/second warnings (80/90 by default) and an optional nearest-loaded-campfire beacon that closes once Cold begins falling.
- **Doomspiral readiness** warns at four or more Soothing Incense. **Warden readiness** warns when the locally visible capture cooldown reaches its ready state.
- **Sparkling, Floor Drop, and Quest Item assistants** use received chat, visible names/entities, nearby already-loaded blocks, and the local inventory only.
- **Wumpa HUD** accepts personal and teammate Loot Share captures for the eight Icy prerequisites, then replaces the checklist with `Wumpa: Spawned`. Its red movement/collision projection is optional and defaults off.
- **Snoozle wall overlay** colors only nearby exposed matching wall faces. Its color is configurable.
- **Safari Belt tooltip** shows all four observed milestone levels and received attribute bonuses and saves confirmed progress per local account/profile.

### Rift

This category is reserved for recognised compatible provider capabilities. QCA hides the group when no usable implementation is discovered.

### Events

This category is reserved for recognised compatible provider event capabilities. One function still appears only once even when several providers offer it.

## Attribute Shard Guide and Planner

The Shard system is read-only and completely informational. QCA never clicks a Fusion menu, selects an output, moves an item, sends `/hb`, or performs a Fusion.

### Guide

The bundled catalog contains exactly 320 current Bazaar-listed Attribute Shards and Shard-specific offline icons.

- Search by canonical English name, Shard ID, effect, rarity, category, family, Skill, mob type, or acquisition text.
- **Details** shows the effect, semantic classifications, every bundled natural acquisition method, and whether the Shard is Fusion-only.
- **Recipes** shows all ordered input pairs that can make the selected Shard, including Shards such as Queen Bee that also have a natural source.
- **Uses** shows every recipe in which the selected Shard can be an input.
- Recipe cards preserve left/right input order, required quantities, up to three selectable outputs, ID/Chameleon/Special yield, and the Pure Reptile double-output note.
- Clickable Shard names darken and underline on hover. Rarity and semantic information use corresponding SkyBlock/Minecraft colors.
- A matching native ItemStack already received by the client takes presentation priority; otherwise the bundled per-ID icon is used.

The running mod never contacts the Wiki, Hypixel API, Bazaar API, SkyShards, or an icon service. The versioned data is generated and reviewed before release and bundled in the JAR.

### Planner pages

- **Plan:** enter a target Shard and quantity, then build a bounded multi-step Fusion tree.
- **Recipes:** filter direct ordered recipes by independent input and output fields.
- **Shards:** inspect effect, family, Skill, mob type, acquisition, baseline rate, and an editable local Shards-per-hour rate.
- **Fusion Lines:** view ID, Special, and Chameleon relationships; click and drag nodes, with positions saved locally.
- **Warehouse:** use Shard counts observed from Hunting Box pages that the player physically opened.
- **Settings:** configure route mode, price side, handling assumptions, Kuudra/Kraken inputs, warehouse use, and other planner options.

Planner options include Fastest or Cheapest objectives, Normal or Ironman rules, alternative recipes, and **Materials Only** summaries. Ironman never uses Bazaar. Kraken calculations can include Kuudra tier, completion time, coins/hour opportunity cost, key cost, and downtime.

QCA contains no Bazaar downloader. Price-based routes are available only when a compatible installed Skyblocker exposes its already-cached prices through the reviewed public method. Without that provider, Cheapest is clearly unavailable while the offline Guide, Ironman planning, rates, Fusion Lines, and warehouse remain usable.

The warehouse reads only visible Shard IDs and exact `Owned: N Shards` lore on the Hunting Box page currently open. It does not open `/hb`, change pages, inspect hidden inventory, or automate a plan.

## Unified controls for compatible SkyBlock mods

Release 0.3.9 includes optional capability-detected adapters for installed builds of:

- SkyHanni
- Skyblocker
- Firmament
- BabyZombieAddons
- Feesh

These mods are optional and are not QCA build or runtime dependencies.

> **Use cautiously:** both unified editors are experimental concept tests, not stable compatibility promises. They are disabled by default. Always keep a backup and use the provider's native editor to verify any important setting or HUD change.

Feesh support pairs its live public delegated-property getters/setters and saves through Feesh's own path. Its enabled, condition-valid, non-empty Overlays can enter Edit HUD with correct LEFT/CENTER/RIGHT anchor conversion, scale/alignment editing, and Feesh-native persistence. Ambiguous settings or changed HUD contracts are omitted and shown only in Compatibility Gaps.

Two independent master switches are located under **General**. **Unified Settings Editor** controls provider-setting discovery and editing; **Unified HUD Editor** controls provider HUD discovery and position editing. Both are disabled by default. Every provider scan requires a second confirmation. First enable without a valid session snapshot and every Refresh open a scope-specific dialog; cancelling the first dialog leaves the master off, cancelling Refresh preserves the latest validated snapshot, and an enabled master restored after restart does not scan silently. Confirmed jobs open the visual progress page. The settings page shows only settings totals and the HUD page only HUD totals. Uninstalled providers are not listed. Turning both off cancels pending work and unloads the session snapshot without disabling QCA's own settings or HUDs.

When several supported mods implement the same exact function, QCA displays one feature card. The secondary page begins with a provider selector. Selecting SkyHanni, for example, makes the card control SkyHanni's matching implementation and disables only exact equivalents in the other providers when the card is enabled. The same page then exposes the safely editable settings belonging to that selected implementation.

QCA writes to the provider's live local configuration object and asks that mod to save through its own path. It does not edit an unloaded mod's raw configuration file. Recognised Boolean, enum, bounded numeric, HUD position, and HUD scale values can appear in QCA; unsupported compound color/keybind objects remain in the provider's native editor.

When the HUD master switch is enabled, provider-owned HUDs can also appear in **Edit HUD** with the provider name shown. Changes are written on mouse release.

Provider version strings are not used as a whitelist. Only an explicitly confirmed initial scan or Refresh probes the installed provider's recognised configuration and save capabilities; opening an editor by itself does not scan. Compatible existing functions therefore remain editable after ordinary provider updates; new or changed structures that QCA cannot safely understand are omitted. An absent provider or a missing required root/save contract fails closed without affecting QCA's own features.

Under **General → Supported Mods**, **Compatibility Gaps** is a read-only information card rather than a function. It has no toggle or enabled strip, and either mouse button opens it. The report reads the latest completed snapshot, groups installed providers, and lists only confidently recognised functions that QCA cannot manage through Settings, HUD Editor, classification, or a combination of these. Fully supported functions are hidden. The report does not change provider values, and completely unknown future structures are not assigned guessed names.

## Client-only and safety boundary

QCA is declared as a client-only Fabric mod. Its normal features consume information the client has already received or can already see: local position, loaded entities/blocks, scoreboard, Tab, chat, visible menus, item lore, and local input.

QCA contains no Hypixel Mod API subscription, Hypixel public API client, HTTP client, WebSocket, telemetry, remote updater, macro, automatic movement, automatic click, automatic capture, automatic item use, hidden-inventory read, or chunk request.

Using another local mod's already-cached value does not create a hard dependency or an extra QCA network request. The optional Skyblocker price bridge fails closed when the reviewed method is unavailable.

Passive rendering is not the same as official approval. Entity outlines, beacons, wall overlays, and movement projections can make received information easier to see and may carry greater policy risk. Every Minecraft modification is used at the player's own risk. Review the current [Hypixel Allowed Modifications guide](https://support.hypixel.net/hc/en-us/articles/6472550754962-Hypixel-Allowed-Modifications) and [Hypixel SkyBlock Rules](https://support.hypixel.net/hc/en-us/articles/4508088842898-Hypixel-SkyBlock-Rules).

See the full [client data-flow and compliance inventory](https://github.com/gprztb6nw4-dotcom/QCloudy-Addition/blob/main/docs/COMPLIANCE.md).

## Commands and outbound actions

| User action | Result | Server payload |
|---|---|---|
| `/qca`, `/qc` | Opens local QCA settings | None |
| `/qshard [English query]` | Opens the local offline Shard Guide | None |
| `/th` | User-triggered Torrhus shortcut | `warp torrhus` |
| `/helia` | User-triggered Helia shortcut | `chapter torrhus` |
| Click **Reconnect** | Starts one normal connection to the remembered in-memory server target | Normal Minecraft connection |

`/th` is equivalent to manually entering `/warp torrhus`; `/helia` is equivalent to manually entering `/chapter torrhus`. Neither is triggered automatically. QCA has no `sendChat` call, automatic command, automatic reconnect loop, or generated chat message.

## Saved data

QCA stores ordinary local JSON only:

- `config/qcloudy_addition.json` — language, feature settings, HUD appearance/position/scale, remembered pet details, received profile-scoped Hunting values, Fairy Soul state, and Shard Planner settings/rates/graph positions.
- `config/qcloudy_addition_shard_warehouse.json` — per-local-profile Shard counts from Hunting Box pages the player actually opened, plus observation time.

The old `autumecloudyaddition.json` is read once for migration. Writes use a temporary file and atomic replacement when supported.

QCA does not store passwords, access tokens, Hypixel API keys, chat history, remote account data, or reconnect addresses on disk.

## Compatibility and troubleshooting

### The game does not start

- Confirm that the playable JAR exactly matches Minecraft 26.1.2 or 26.2.
- Confirm Java 25, Fabric Loader 0.19.3+, and the matching Fabric API.
- Remove duplicate/older QCA JARs.
- Do not put the `-sources.jar` in the mods folder as the playable file.

### A HUD or edit box is missing

This is often intentional. A HUD is drawn and exposed in Edit HUD only when its feature is enabled, its location/state is loaded, and it has real visible rows. A title or placeholder cannot keep an otherwise empty panel alive.

### The Dwarven marker is not an exact block position

The Dwarven map is an approximate single-layer X/Z overview. It intentionally ignores Y and floor/sub-location names. The arrow should move continuously in real time, but the background is not a precise block-by-block survey.

### Cheapest Shard planning is unavailable

QCA does not fetch Bazaar prices. Use the offline/Ironman/rate modes, or install a compatible Skyblocker build that still exposes its existing client price cache through the recognised public method. A provider update can make the bridge unavailable; other Shard tools continue to work.

### A third-party provider does not appear

QCA no longer rejects a provider only because its version number changed. Reopen QCA settings so it can probe the provider's live configuration and native save/update capabilities again. Existing recognised fields remain editable when their contracts still match; unknown new fields are omitted. If the provider changed or removed its root configuration/save contract, only that adapter stays hidden until QCA learns the new structure.

### A native icon looks different from the bundled Shard icon

When QCA has already received a matching real ItemStack, it lets Minecraft render that stack so resource-pack/server presentation can take priority. Unseen catalog entries use the bundled offline per-ID icon.

## Reporting a bug

Open a [GitHub Issue](https://github.com/gprztb6nw4-dotcom/QCloudy-Addition/issues) and include:

- QCA version and exact filename
- Minecraft, Fabric Loader, Fabric API, and Java versions
- Complete installed mod list with versions
- Language and GUI Scale
- Exact island/location and steps to reproduce
- Expected result and actual result
- Screenshot or short video for UI/rendering problems
- `latest.log`, and the crash report when a crash occurred
- Whether the issue still happens with only Fabric API and QCA installed

Do not include access tokens, session identifiers, private chat, or other secrets.

## Validation, license, and credits

Release 0.3.9 is built for Minecraft 26.1.2 and 26.2 with Java 25. The maintained validation report records the exact automated-test, archive, language, and compatibility-contract checks for both builds.

Automated tests and archive checks do not replace an authenticated Hypixel regression, every GUI Scale, every resource pack, or every future modpack combination. See the current [validation report](https://github.com/gprztb6nw4-dotcom/QCloudy-Addition/blob/main/docs/VALIDATION.md) for the exact tested boundary.

Project documentation:

- [Changelog](https://github.com/gprztb6nw4-dotcom/QCloudy-Addition/blob/main/CHANGELOG.md)
- [Feature specification](https://github.com/gprztb6nw4-dotcom/QCloudy-Addition/blob/main/docs/FEATURES.md)
- [Implementation and data flow](https://github.com/gprztb6nw4-dotcom/QCloudy-Addition/blob/main/docs/IMPLEMENTATION.md)
- [Compliance inventory](https://github.com/gprztb6nw4-dotcom/QCloudy-Addition/blob/main/docs/COMPLIANCE.md)
- [Third-party notices](https://github.com/gprztb6nw4-dotcom/QCloudy-Addition/blob/main/THIRD_PARTY_NOTICES.md)

QCloudy_Addition source code is licensed under the **GNU Lesser General Public License v3.0 or later (`LGPL-3.0-or-later`)**. Reviewed offline facts/assets and their licenses are documented in `THIRD_PARTY_NOTICES.md`, including the Hypixel SkyBlock Wiki and MIT-licensed SkyShards icon data. The running mod does not contact those sources.

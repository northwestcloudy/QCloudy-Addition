# QCloudy_Addition

QCloudy_Addition is a client-only Fabric mod focused on readable SkyBlock maps, compact objective HUDs, client-side visual helpers, pet information, inventory quality-of-life tools, and opt-in party/chat utilities. The mod is bilingual, English-first, and keeps Hypixel-provided names in their original form. The current public testing build is Beta 0.3.10 for Minecraft 26.1.2 and 26.2; the latest stable release remains 0.3.9.

## Quick links

- [Feature list](docs/FEATURES.md)
- [Implementation notes](docs/IMPLEMENTATION.md)
- [Modrinth description](docs/MODRINTH_DESCRIPTION.md)
- [Current 0.3.10 Beta changelog](CHANGELOG.md)
- [Version and artifact naming](docs/VERSIONING.md)
- [Validation](docs/VALIDATION.md)
- [Compliance](docs/COMPLIANCE.md)

Default language: English. Press `O` (rebindable under Controls → Key Binds → QCloudy_Addition) or use `/qca` or `/qc` to open the client-side settings, then switch to Simplified Chinese at any time. These settings aliases are registered only when their client-command names are free, open a local screen, and send nothing. The separately configured party/chat aliases are documented below.

The language option translates QCA interface labels only. Hypixel location names, task names, pets, skins, accessories, items, and player-renamed HOTM slots remain in their original client-received form.

## Release-only update notice

QCA's update notice is always enabled and deliberately has no settings card. Alpha builds never schedule or perform an update request. Beta and Release builds make at most one asynchronous HTTPS `GET` per client process, after the first world join, to `https://www.qcloudy.net/assets/data/release-manifest.json`. The response is accepted only when it is a valid stable `Release` manifest with a higher monotonic release sequence and exactly one playable Release JAR for the running Minecraft version. Beta, Alpha, malformed, wrong-version, Sources-only, duplicate, or untrusted-URL results fail closed and never become an update target.

When a newer Release is confirmed, QCA shows one vanilla toast and one local clickable chat message linking to `https://qcloudy.net/download/` and `https://qcloudy.net/changelog/`. It never downloads, replaces, or launches a JAR. The request includes no Minecraft username, UUID, server address, profile, mod list, gameplay state, telemetry identifier, or authentication token. As with any ordinary HTTPS request, the web server can observe the connecting IP address and the `QCloudy_Addition/<version>` HTTP User-Agent.

## Unified SkyBlock mod controls — experimental concept test

> **Caution:** The Unified Settings Editor and Unified HUD Editor are concept tests. They are disabled by default, are not yet stable, and can stop recognising individual fields after a provider mod updates. Enable them cautiously, keep configuration backups, and verify every write in the provider mod's own settings or HUD editor. The provider-native editor remains authoritative.

QCA can act as one function-first settings and HUD editor for its own features and installed builds of **SkyHanni**, **Skyblocker**, **Firmament**, **BabyZombieAddons**, and **Feesh**. The separate **Unified Settings Editor** and **Unified HUD Editor** master switches are both disabled by default and can be enabled independently. Every provider scan requires a second confirmation: the first enable without a valid session snapshot and every **Refresh** action open a scope-specific dialog before any work starts. Cancelling the first dialog leaves that master off; cancelling Refresh preserves the last validated snapshot. Restoring an enabled master after restart never starts a silent scan. Confirmed scans open a live progress page. The settings page reports only manageable settings, while the HUD page reports only manageable HUDs. Opening the normal settings menu does not rescan, uninstalled providers are not listed, and disabling both switches cancels pending work and releases the session snapshot. QCA probes live provider capabilities rather than enforcing an exact-version whitelist, so recognised compatible branches can remain available after a provider update while unknown or changed branches are omitted.

Native paths and verified classification rules run first. Only provider functions that are still uncategorised are passed to a small deterministic local metadata classifier. It uses fixed weights and a confidence threshold, runs without a model download or network connection, and never decides that two functions are equivalent or writes a provider value.

When several supported mods implement the same exact function, QCA shows one card. Right-clicking that card puts the provider selector first and then shows the safely editable native settings of the selected provider. Enabling the card enables the selected implementation and disables only its exact equivalents; nearby price, profit, tooltip, or tracker features with different purposes are not merged. Values are written to the provider's live configuration and saved through that mod's own save path. QCA never edits an unloaded mod's configuration file.

The existing **Edit HUD** screen also includes enabled HUDs owned by the selected compatible provider. External panels are labelled with the provider name; dragging or resizing writes the native position/scale only when the mouse is released. This experimental editor exposes validated Boolean, enum, bounded numeric, position, and scale values. Provider-specific compound color/keybind objects remain in their native editors until a safe adapter is implemented.

The top-level order is **General, Maps, Items & Menus, Combat, Dungeons, Slayer, Mining, Farming, Foraging, Fishing, Hunting, Rift, Events**, but a category is hidden when no QCA or discovered provider feature belongs to it. Safari is a Hunting subgroup, Garden is a Farming subgroup, Crimson Isle/Kuudra are Combat subgroups, and Fishing's bite cue is grouped under **Bite Alerts** rather than a duplicate Fishing heading. Every feature has one owner and appears once.

## Feature categories

### General

- **Manual Reconnect** — adds one vanilla-sized `Reconnect` button to connection-failed and disconnected screens. The target is captured when the normal connection attempt begins, so the button also works after an initial failure. It reconnects only after the player clicks it; there is no timer, loop, retry counter, command, or automatic join.

### Maps

- **Dwarven Mines Map** — the supplied single-layer overview with 12 individually shaped region blocks and a live red player arrow. One continuous approximate transform maps the local player's real-time X/Z across the complete background; Y and scoreboard sub-location names are deliberately excluded, so bridges above The Mist cannot make the marker jump between regions. Point-of-interest labels remain English.
- **Glacite Tunnels Layer Map** — low, middle, and high tunnel images share one coordinate system. The displayed layer changes at Y 126 and Y 143; the live arrow remains spatially consistent between layers. Generated English point-of-interest cards use collision avoidance so nearby locations never overlap.

### Mining

- **Mining Tasks & Powders** — displays the `Commissions:` and `Powders:` widgets already received in the player list. Every task uses its full, untruncated name and a separate progress bar. A bar ends at approximately the widest complete task name instead of stretching across the fixed panel; normal and bold styles are measured exactly, with enough room reserved for the full progress value. Progress defaults to one-decimal percentage mode and can be changed to current/target mode in the feature settings. Exact server-provided counts take priority; otherwise known targets are derived from the documented commission definition, while unknown future tasks safely remain percentages. An optional, default-on `HOTM: <slot name>` line caches the selected Heart of the Mountain loadout name observed in the slot/loadout menu. It supports Dwarven Mines, Crystal Hollows, Glacite Tunnels, and Glacite Mineshafts and separately shows Mithril, Gemstone, and Glacite Powder.

### Crimson Isle

- **Faction Task Tracker** — while on Crimson Isle, reads only the received `Faction Quests:` Tab widget and shows every original quest name, required amount, and the server's `✖`/`✔` status without shortening or translating it. It is a separate, default-on feature and shares the mutually exclusive task HUD position/style with the mining tracker.

### Foraging

- **Torrhus Chapter & Resources** — one wrapped, non-truncating HUD for the current Helia Chapter/task/progress plus Forest Whispers, Desert Whispers, Forest Essence, Safari Essence, Sweep, and Forest Fortune. Tab and scoreboard are parsed as separate bounded sources so a later `SB Level` fraction cannot become the Chapter task. The real `Helia's Chapters` overview and chapter-detail inventory layouts are supported, as are short split chat blocks. Confirmed absolute values are cached separately for each Minecraft account and received SkyBlock profile, survive reconnects, and change only when the client observes a newer value; stale non-Chapter tasks from older configs are repaired on load. Chat gain messages remain bounded additive updates. Safari Essence is intentionally not repeated inside Critter Safari. Optional completed-count, total-progress, and next-unlock rows default off.
- **Tree Critter Timer** — default-on and independently switchable. It reads the nearest visible `Critter in: 26m 47s` Tree Protection Order nameplate and adds that exact countdown to the combined Hunting HUD. It does not start a guessed local timer, so Fun-Sized (60m), Family-Sized (30m), Jumbo (15m), Behemoth (instant), Honeycomb Artifact acceleration, Honey Serendipity instant procs, and future server-side modifiers remain accurate.
- **Miria Contest** — parses received scoreboard/Tab tier lines such as `COMMON with 151` and `Uncommon requires +99`, then shows the next bracket, exact remaining score, and estimated Safari Ticket only in the combined Hunting HUD. It does not inject into the right sidebar or duplicate its contest timer.
- **Benefactor & Tree Gifts** — Benefactor state is merged from bounded Tab/scoreboard blocks, the already-open Forest/Desert Temple menu, and the player's exact received donation message. Multi-day donations, countdowns, temple-specific effects, expiration, and account/profile persistence are supported; a newly received donation is protected from a briefly stale open menu. Rare Tree Gift rewards are read from the player's exact personal reward-summary hover and from exact bonus rows inside that same bounded, ownership-proven gift block. This also consumes raw client-received messages canceled by compatible chat compactors; a nearby player's public drop line by itself never arms an alert.

### Fishing

- **Fishing Bite Sound** — an opt-in local cue for Hypixel's short bite window in water or lava. It prefers the local player's directly owned Fishing Hook and uses a short post-cast association window for Hypixel lava hooks whose owner link is absent, then requires the exact visible `!!!` marker beside that hook. It plays the bundled Ciallo OGG once per hook and has an independent 0–100% volume slider at the 64% default. It never casts or reels automatically. Its collapsible subgroup is named **Bite Alerts**, avoiding a redundant `Fishing → Fishing` hierarchy.

### Hunting

- **Beeheemoth & Lasso cues** — detects only the reference-mod signature of a scale-9 Bee. Its vanilla outline is default-on with the shared RGB/HSV color picker, while a yellow beacon marks the first visible spawn position until the player comes within 10 blocks, receives their own Beeheemoth capture confirmation, or the entity disappears. Bee sounds spatially associated with that scale-9 entity—including its short spawn/capture window—have their own default-on 64% volume control; ordinary Bee sounds elsewhere are untouched. A separate default-on, 64%-volume cue plays once when the local player's visible Lasso state changes to the exact `REEL` label.
- **Critter Behavior Assistant** — center-screen prompts for documented special Critter mechanics, with bounded suppression after the received capture confirmation.
- **Fairy Soul Waypoints** — one cross-island Hunting feature with independent Torrhus and Safari coordinate switches; it appears only in this category.

### Safari

- **Safari Run Dashboard & Critterdex** — session Shards, timer, Ticket Tier, four-biome progress, and complete current-biome captured/missing lists across the official 37 Critters.
- **Cold, Doomspiral, Critter, Snoozle, and Wumpa helpers** — two configurable Cold warnings (80/90 by default), an immediate red nearest-loaded-campfire beacon above the first threshold that closes once Cold begins falling, a 4-Soothing-Incense warning, a dedicated Doomspiral Warden capture-ready alert, official Shard-rarity real-entity Critter outlines, and an optional red Wumpa motion/collision projection. The Wumpa HUD accepts personal and teammate Loot Share captures for its eight Icy party prerequisites, then replaces the checklist with `Wumpa: Spawned`; projection follows the real Ravager body. A separate default-green RGB option overlays only nearby exposed `Cobbled Deepslate + Tuff` Snoozle wall faces. Armor Stand capture props are excluded from highlighting to prevent support-body outlines. Wumpa route prediction defaults off; the remaining helpers default on.
- **Sparkling, Floor Drop, and Quest Item assistants** — center alerts and read-only HUD state from received chat, visible names/entities, nearby already-loaded String blocks, and local inventory. Sparkling outline color is editable.
- **Safari Belt details** — embeds all four locally observed Cavern/Forest/Haunted/Icy milestone levels and received attribute bonuses in the actual belt tooltip. Split title/lore menu layouts are supported; confirmed levels are saved per account/profile and only increase when a higher observed level is received.

Foraging and Hunting are separate top-level settings categories, while Safari is a collapsible subgroup owned only by Hunting; Fairy Soul waypoints live only under Maps. Every feature card has exactly one owner. All related warnings use center titles. Every alert feature owns its own default-on 64% sound and continuous 0–100% volume slider; General also has a master mute. The combined HUD has its own persisted appearance, scale, and position in **Edit HUD**.

### Combat

- **Ender Dragon Highlight** — puts Hypixel Ender Dragons in the vanilla outline pipeline while the scoreboard location is The End or Dragon's Nest. The outline color is selectable from red, yellow, cyan, green, purple, and white.
- **Power Orb & SOS Despawn Alert** — the four Power Orbs use exact player-owned despawn chat lines. Warning/Alert/SOS Flares use exact item IDs plus the successful placement sound to start a three-minute local lifecycle. Replacing a Flare resets the complete three-minute timer and invalidates the old expiry. Failed uses, entity unload, distance, and buff range do not trigger an alert. Power Orb, Flare, center text, sound, and volume are independently configurable; sound defaults to 64%.
- **Century Cake Effect Expiry Alert** — one default-on master switch tracks all 20 received cake refreshes for 48 real-world hours, merges simultaneous expiries, and shows a center title plus an underlined chat action. `/cake` and `/centurycakeeffect` open the local timer screen; clicking the chat action runs `/visit northwestcloudy`.

### Pets

- **Equipped Pet HUD** — uses summon/despawn/Autopet chat notices for immediate state changes, then treats the received `Pet:` Tab widget as the source of truth. It constructs a plain player head from QCA's bundled verified profile and never adds synthetic `petInfo`, so another mod cannot replace the HUD icon with an unrelated item model. Dynamic skin-family frames—including all published Baby Spinosaurus variants—map back to their real skin. The HUD never shortens a pet, skin, XP, or accessory line with an ellipsis; bold text is measured before sizing. Current-level and max-level XP lines are independently switchable and default to on, while the max-level line is automatically hidden for a maxed pet without hiding its held item. A held item confirmed through the Pets menu, Tab, or received chat is retained locally across reconnects. Optional skin-name and cosmetic-overflow-level display are enabled by default. Ancient Golden Dragon overflow levels are derived only from received total/overflow XP. All 87 current pet-item resources are indexed; the held item can be shown as icon + name (default), icon only, or name only. Standard pets use their rarity-adjusted level-100 curve; Golden, Jade, and Rose Dragons use their level-200 curve.

### Items & Menus

- **Attribute Shard Fusion Guide** — a JEI-inspired, completely offline browser for all 320 current Bazaar-listed Attribute Shards. Search by original English name, Shard ID, attribute/effect, rarity, category, family, skill, mob type, or acquisition text. **Details** shows the Wiki-listed effect and all documented natural/Fusion acquisition methods; **Recipes** shows every ordered input pair that can produce the selected Shard, including Queen Bee and other Shards that also have natural sources; **Uses** shows what the selected Shard can make. Recipe cards preserve input order and show quantities, selectable outputs, normal/special yield, and Pure Reptile. Epic uses Minecraft dark purple (`§5`), while rarity, stats, categories, mob types, and acquisition methods retain semantic game colours. Clickable Shard text darkens and underlines on hover. The catalog and Shard-specific icons are generated offline from reviewed sources and committed into the mod; a matching native `ItemStack` already received by the client still takes priority for resource-pack presentation. Local `/qshard [English query]` opens the screen without sending chat or a server command. QCA makes no runtime Wiki, API, or icon request and never performs a Fusion.
- **Shard Planner** — keeps the original Guide intact and adds target quantity, complete multi-step Fusion trees, alternative routes, Materials Only summaries, separate input/output recipe filtering, editable Shards-per-hour rates, per-Shard details, a draggable Fusion Lines view, and a locally saved Hunting Box warehouse. Ironman uses hunting rates only. Normal Fastest can compare hunting time and buying time, while Cheapest requires an optional compatible Skyblocker client-price cache; QCA itself never downloads Bazaar prices. SkyHanni/Firmament are not used as price providers because they currently expose no stable public cross-mod price API. Without a provider, price-based routes are explicitly unavailable and every offline/rate-based feature remains usable.

### Chat

- **Chat Peek** — hold a user-defined key or modifier combination to temporarily render the focused-height chat history without opening Chat. While peeking, the mouse wheel defaults to scrolling chat; the secondary setting can leave it controlling the hotbar instead. The peek key is intentionally unbound by default to avoid conflicts.
- **Party Auto Accept** — optionally accepts qualifying party invitations from the configured friend category or a 16-player whitelist. The master switch is off by default; the whitelist overrides the friend-category choice.
- **Private-message Party Request** — when enabled, an exact received private-message keyword `!p`, `!party`, or `!invite` sends `party invite <sender>`. It is off by default and ignores unrelated private messages.
- **Quick Private `!p`** — when enabled, local `//invited <player>`, `//invited by <player>`, and `//i <player>` send `msg <player> !p`. It is off by default.
- **Fast Party Commands** — an opt-in Party Chat interpreter. It only handles recognized `!` aliases sent in Party Chat, never public or guild chat. The master switch is off by default; its nine independent child switches default on. Each command can be limited to the local player, other party members, or everyone. `!warp`/`!w` sends `party warp` with a shared five-second cooldown, and `!allinvite`/`!all`/`!allinv` sends `party settings allinvite` with a shared two-second cooldown. The remaining aliases send the documented Party, Stream, Dungeon, Kuudra, coordinate, transfer, kick, and promote commands listed in [the implementation notes](docs/IMPLEMENTATION.md).
- **Party Commands** — local double-slash equivalents such as `//m7` and `//pt <player>`. This master and all of its independent child switches are on by default. Known commands are handled locally; unknown `//` commands are left untouched. Player arguments accept exact names or a unique party-member prefix.

### HUD appearance

- Left-click a feature card to toggle it; the blue strip on its left is the only enabled-state indicator. Right-click still opens that feature's complete secondary settings page, without a redundant on-card hint.
- Per-HUD background opacity/color, border visibility/width/color, title color, bold text, and text shadow
- A shared RGB/HSV color picker with a wheel, brightness and R/G/B sliders, color presets, and a Transparent choice for every background color
- Per-HUD 50–200% scale; drag a loaded HUD's border or corner like a desktop window to resize it
- The bottom-left **Edit HUD** button opens an editor containing only HUDs currently loaded by the player's location/state; drag to reposition and use each panel's small gear for its settings
- Positions and individual scales are saved on mouse release and persist across restarts
- UI opening animations are enabled by default and can be disabled
- Optional Mod Menu integration opens QCA's settings directly when Mod Menu is installed

The configuration screen uses a compact BLC-inspired information hierarchy—not copied assets or layout code—with one **Features** tab, ordered available categories, collapsible subgroups, and searchable function cards. A category with no available QCA or provider feature is removed from the sidebar instead of opening an empty page. Fishing uses the **Bite Alerts** subgroup, avoiding the redundant “Fishing → Fishing” hierarchy. HUD position editing remains available from the bottom-left **Edit HUD** button. Feature cards do not repeat a top-right switch or bottom-right right-click hint, and secondary pages do not repeat the primary enable switch. There is deliberately no catch-all `ALL` category.

Inventory and menu tools include the Attribute Shard Fusion Guide, item timestamps, cursor position memory, configurable AOTE/AOTV sounds, and Chat Peek. Every QCA hotkey is edited inline on its existing secondary-settings page instead of opening a separate capture screen. Keyboard keys, mouse buttons 1–5/side buttons, and Ctrl/Shift/Alt/Cmd-Super combinations are supported; while a row is listening, `Esc` clears it to unbound.

**AOTE/AOTV sound settings** never silence teleport tools by default. Instant Transmission and Etherwarp each default to their original sound and can independently use Chorus Teleport, Enderman Teleport, Amethyst Chime, Experience Orb, End Portal Fill, or Shulker Teleport. Custom volume and pitch are continuous 10–200% and 50–200% sliders. Other broad numeric settings—including HUD opacity/scale and cursor-memory duration—use Windows-style draggable sliders and save on release; short discrete choices remain buttons.

## Installation

1. Install Minecraft 26.1.2 with Fabric API 0.155.2+26.1.2, or Minecraft 26.2 with Fabric API 0.154.2+26.2. Beta 0.3.10 requires Fabric Loader 0.19.3 or newer and Java 25.
2. Put the `QCloudy_Addition-*.jar` whose filename ends in your exact Minecraft version in the instance's `mods` folder. Mod Menu is optional.
3. Start the game and press `O` or type one of the local settings commands to configure the mod.

## Building from source

Install JDK 25 and run `bash tools/build_all_versions.sh`. The script builds the channel selected in `gradle.properties`: Alpha builds test and produce the Minecraft 26.1.2 playable and Sources JARs, while Beta and Release builds test and produce both the Minecraft 26.1.2 and 26.2 pairs in `release/`. The repository includes its own pinned Gradle 9.6.1 Wrapper and Fabric Loom 1.17.17 configuration; the inspected reference mods are not build or runtime dependencies. Pet profile metadata is generated offline from a local NEU repository snapshot and committed into QCA resources. Gameplay, Shard, Wiki, icon, and price data remain local/offline; the only QCA-owned runtime web request is the bounded Release-manifest check described above. QCA runs without Firmament.

## Safety boundary

The release contains no `sendChat`, Hypixel Mod API subscription, WebSocket, telemetry, runtime Shard-data request, macro, automatic movement, or chunk-request code. Its only QCA-owned HTTP path is the always-on, once-per-process Release-manifest check; Alpha builds return before scheduling it, and it cannot download or install an update. Normal HUD features consume only client-received state. `/qshard`, `/cake`, and `/centurycakeeffect` are local screen commands and send nothing. The always-available local `/th` and `/helia` shortcuts send exactly `warp torrhus` and `chapter torrhus`, equivalent to entering `/warp torrhus` and `/chapter torrhus`; they run only when the player types the corresponding shortcut. The underlined Century Cake renewal action sends exactly `/visit northwestcloudy`, but only after the player physically clicks it. The documented opt-in party/chat utilities can additionally send their listed server-command payloads after their own configured triggers; they never simulate a click, move the player, or use an item.

Hypixel states that all modifications are used at the player's own risk and that an unlisted feature is not guaranteed to be allowed. Review [docs/COMPLIANCE.md](docs/COMPLIANCE.md) and the current [Hypixel Allowed Modifications guide](https://support.hypixel.net/hc/en-us/articles/6472550754962-Hypixel-Allowed-Modifications) before use.

Chinese documentation: [README_zh_CN.md](README_zh_CN.md)

Detailed feature specification: [docs/FEATURES.md](docs/FEATURES.md)

Implementation and data flow: [docs/IMPLEMENTATION.md](docs/IMPLEMENTATION.md)

Modrinth-ready description: [docs/MODRINTH_DESCRIPTION.md](docs/MODRINTH_DESCRIPTION.md)

Current 0.3.10 Beta changes: [CHANGELOG.md](CHANGELOG.md)

Publication checklist: [docs/PUBLISHING_CHECKLIST.md](docs/PUBLISHING_CHECKLIST.md)

Changelog: [CHANGELOG.md](CHANGELOG.md)

Release validation: [docs/VALIDATION.md](docs/VALIDATION.md)

2026-08-04 crash analysis: [docs/CRASH_ANALYSIS_2026-08-04.md](docs/CRASH_ANALYSIS_2026-08-04.md)

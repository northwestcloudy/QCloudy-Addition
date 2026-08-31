# QCloudy_Addition feature specification

## Unified settings and HUD control — Beta 0.3.10 (experimental concept test)

> **Caution:** Unified Settings Editor and Unified HUD Editor are concept tests, remain disabled by default, and are not yet stable. Provider updates may invalidate recognised paths. Back up provider configuration, use these editors cautiously, and verify writes in the provider-native editor.

QCA presents one function-first catalog across its own features and safely recognised settings from installed SkyHanni, Skyblocker, Firmament, BabyZombieAddons, and Feesh builds. **General → Supported Mods** now starts collapsed, like the other subgroups. Expanding it reveals two independent opt-in master switches: **Manage Other Mod Settings** gates provider feature-setting discovery/editing, while **Manage Other Mod HUDs** gates provider HUD discovery/editing. Both default to off and are independent; QCA-owned controls remain available. Every provider scan requires a second confirmation. Enabling either gate without a valid session snapshot opens the scope-specific dialog first; every Refresh action does the same. Cancelling the first dialog leaves the gate off, cancelling Refresh preserves the current validated snapshot, and an enabled gate restored after restart never starts a silent scan. Confirmed scans open the live progress page. The settings view shows only its settings total and the HUD view only its HUD total; uninstalled providers are never displayed. A refresh retains the previous valid snapshot until the replacement has completed and validated. Refresh is unavailable while a scan is running. Turning both gates off cancels pending work and unloads the snapshot. Provider version strings are not a whitelist: recognised compatible capabilities are included while changed branches are omitted. The ordered category set is **General, Maps, Items & Menus, Combat, Dungeons, Slayer, Mining, Farming, Foraging, Fishing, Hunting, Rift, Events**, but the sidebar renders only categories that currently own at least one available QCA or discovered-provider feature. Safari belongs to Hunting, Garden to Farming, and Crimson Isle/Kuudra to Combat. Fishing's cue uses the **Bite Alerts** subgroup instead of repeating Fishing. All collapsible subgroups start closed, and one exact function has one card. Beta 0.3.10 is published for Minecraft 26.1.2 and 26.2; Release 0.3.9 remains the latest stable build.

Scanning is staged across client ticks and visualises provider detection, reading, classification, validation, the current item, and recent activity. Provider-native paths and verified rules have priority. Only still-uncategorised metadata reaches the fixed local weighted classifier; low-confidence results stay unclassified. The classifier cannot merge features, choose a provider, disable a function, move a HUD, or write any value.

For a shared function, right-click opens a secondary page whose first control selects the provider. Enabling the card then enables that provider and disables only exact equivalents in the other compatible providers. All selected-provider Boolean, enum, bounded numeric, position, and scale controls that pass the adapter's safety checks are edited in place and saved through the provider's native save path. When its independent master is enabled, QCA's Edit HUD screen also shows enabled, selected-provider HUDs and commits native position/scale changes on mouse release. QCA remains standalone.

**Feesh:** QCA recognises its live Kotlin setting accessors and Overlay registry without requiring Feesh. Only safely writable settings are grouped; ambiguous or compound controls remain in Feesh and appear as gaps. Only enabled, condition-valid, non-empty Feesh overlays enter Edit HUD, where anchor-aware position, scale, and alignment changes save through Feesh's own persistence path.

**Compatibility Gaps** is a separate read-only card under **General → Supported Mods**. It is not a feature and has no toggle or enabled strip. Left- or right-clicking it reads the latest completed scan snapshot and groups only confidently identified unmanaged controls by provider, with `[Settings]`, `[HUD Editor]`, and classification badges where applicable. Fully supported functions are omitted. Recognised complex or changed structures can be reported without being exposed to the editor; completely unknown future structures are not given invented names.

All first-party configuration screens use bounded responsive layouts. Shard details reserve a fixed control dock below independently scrollable metadata; Planner controls split before collision; narrow settings use one column; Fusion Lines grow into a scrollable canvas. Long labels are fitted within their real bounds, and clipped rows do not keep invisible click targets.

## 1. Maps

### 1.1 Dwarven Mines Map

**Purpose:** reduce navigation friction in the large, vertically complex Dwarven Mines without exposing live hidden terrain.

**What it does:** shows the supplied single-layer image with 12 individually shaped blocks: Village, Upper Mines, Rampart Quarry, Forge, Lava Springs, Cliffside, Far Reserve, Goblin Burrows, Royal Mines, The Mist, Ice Wall, and Royal Palace. Point-of-interest labels remain English regardless of QCA interface language.

**Player marker:** every rendered frame, one continuous approximate transform maps the local player's X/Z across the complete background and yaw rotates the arrow. Neither Y nor the scoreboard sub-location is accepted by the projection, so a bridge above The Mist shares the same overview point as the space below it instead of forcing a cross-region jump. The background is a bundled schematic PNG, not a precision survey map.

### 1.2 Glacite Tunnels Layer Map

**Purpose:** make the overlapping tunnel network readable without pretending that a single flat image represents all elevations.

**What it does:** switches among three pre-generated images: low (`Y ≤ 126`), middle (`126 < Y ≤ 143`), and high (`Y > 143`). Every image uses X `-131..130` and Z `181..485`, so the arrow has no coordinate discontinuity during a layer change. Non-current routes remain faintly visible for context. English label cards are automatically separated during generation so nearby locations do not overlap.

**Data used:** local X/Y/Z and yaw, plus bundled images.

## 2. Mining

### 2.1 Task and three-powder tracker

**Purpose:** keep current mining objectives and the three currencies visible without holding Tab open.

**What it does:** once per second, reads the sorted client Tab rows. Lines inside `Commissions:` become full-name task cards with separate progress bars; task names are never shortened with an ellipsis. Bar width follows the widest fully rendered task name, subject to a compact minimum and the panel boundary, and also reserves enough width for the full progress value. Measurement uses the exact active normal/bold style, preventing bold text from crossing the bar or HUD boundary. The default bar text is a one-decimal percentage, with an optional current/target mode in the feature settings. Client-received `x/y` counts are exact and take priority. When only a percentage is received, QCA converts it using the documented target for that island's known commission; unknown future tasks remain percentages rather than receiving a guessed target. Lines inside `Powders:` are parsed into Mithril, Gemstone, and Glacite values. Last received powder values remain visible for the session when a specific island stops displaying that widget; unknown values are shown as `—` rather than guessed.

**Supported places:** Dwarven Mines, Crystal Hollows, Glacite Tunnels, and Glacite Mineshafts. Location is derived from the location-marked scoreboard line and an explicit list of official sub-location names. The Crystal Hollows `Jungle` name is matched as a complete location rather than a substring, so The Park's `Jungle Island` cannot activate this HUD.

**Selected HOTM slot:** enabled by default and independently switchable. QCA reads `SELECTED` from the exact `Heart of the Mountain Slot` menu or `Current:` from the received loadout item lore, caches the original player-visible name, and displays `HOTM: <name>`. It does not issue a command or click a menu to obtain it.

## 3. Fishing

### 3.1 Fishing Bite Sound

**Purpose:** make Hypixel's short bite window audible without automating fishing.

**What it does:** while enabled, QCA prefers the local player's directly owned, already-loaded Fishing Hook. After a physical local rod use, it can also associate a newly loaded ownerless hook during a bounded 40-tick window so Hypixel lava fishing receives the same cue. Hooks present before the cast and hooks owned by another player are rejected. An invisible, visible-named ArmorStand whose exact received name is `!!!` inside the selected hook's four-block box triggers the supplied Ciallo cue once for that cast. Persistent markers cannot replay the sound every tick, and using the rod to reel does not re-arm the cue while that marker is disappearing; only a confirmed new cast or removed hook arms the next cast. The feature defaults off, lives in the top-level Fishing category under the **Bite Alerts** subgroup, and has its own continuous 0–100% volume slider at the project-wide 64% default.

**Safety:** the detector reads loaded client entities and observes the player's physical rod use without cancelling or replacing it. The broader hook lookup runs only during the short association window or while that fallback hook remains loaded; idle gameplay does not scan for hooks every tick. It plays one bundled local OGG and does not reel, cast, click, rotate, move, select an item, send a packet, chat message, or command.

## 4. Foraging, Hunting, and Safari

The settings UI gives every feature one owner. **Foraging** owns Torrhus Chapter/resources, Tree Critter Timer, Miria Contest, Benefactor, and Tree Gift. **Hunting** owns Beeheemoth, Lasso REEL, Critter Behavior, plus one collapsible **Safari** subgroup containing Cold, Doomspiral, Critter highlighting, Dashboard, Critterdex, Sparkling, Floor Drop/Quest Item, Wumpa, Snoozle wall overlay, and Safari Belt. Cross-island Fairy Soul waypoints live only under Maps. No card appears in multiple categories.

### 4.1 Helia Chapter and Torrhus resources

**Purpose:** keep the active Torrhus objective and the resources that affect it together in one readable panel.

**What it does:** while the location-marked scoreboard line identifies Torrhus Canyon or one of its documented sub-locations, QCA parses the scoreboard and Tab text already received by the client as separate bounded sources. This prevents an unrelated fraction later in Tab, such as `SB Level`, from being attached to an earlier Helia heading. It recognizes the real `Helia's Chapters` overview (`Tasks completed`) and chapter-detail item (`Progress`) layouts from an already-open menu without clicking it, and can join one short split Chapter chat block. Confirmed Chapter/resource state is cached under the Minecraft account UUID plus the explicit received SkyBlock Profile name (or an account-local fallback until that line is received), loaded on profile entry, and written only when a parsed value actually changes. Repeated Tab/scoreboard/menu snapshots are absolute rather than repeatedly added; only exact chat gain formats are additive. A newly received Chapter clears stale task/progress fields instead of inheriting them from the previous Chapter, and older cached false tasks are repaired on load. The default view shows the current Chapter, complete current-task name, exact received progress, Forest Whispers, Desert Whispers, Forest Essence, Safari Essence, Sweep, and Forest Fortune. Safari Essence is shown only here. Long task names wrap and are never replaced with an ellipsis.

**Tree Critter Timer (default on):** QCA follows SkyHanni's passive tree-progress acquisition pattern and scans only entity display names already rendered by the client. When the nearest loaded Tree Protection Order exposes a strictly parsed `Critter in: <duration>` line, the exact received duration appears in this same combined HUD; it disappears when the nameplate disappears, the player leaves Torrhus, or the feature is disabled. QCA deliberately does not infer use of a Pot or run its own clock. The current local item repository contains Fun-Sized Pot of Honeycomb (60m), Family-Sized Pot of Honeycomb (30m), Jumbo Pot of Honeycomb (15m), and Behemoth Pot of Honeycomb (instant). Reading the server's final nameplate also incorporates Honeycomb Artifact's 15% acceleration, Honey Serendipity instant attraction, other players' orders, and future Pot variants without hard-coded timing drift.

**Beeheemoth Helper (default on):** uses the supplied BabyzombieAddons implementation's narrow signature: a locally loaded Bee whose received entity scale is within 0.01 of 9.0. QCA adds that entity to the vanilla outline path; its default yellow color is fully editable with the shared RGB/HSV picker and presets. When a new UUID is first visible, QCA records that first observed block as a fixed yellow-beacon position. The beacon is permanently dismissed for that entity when the player enters a 10-block radius, when the client receives the player's exact `CAPTURE! You caught ... Beeheemoth ...` confirmation, or when the entity disappears/has been captured. Bee-family sounds within 12 blocks of the loaded entity (plus a three-second last-position grace window for the capture sound) use a separate default-on 64% volume slider; unrelated Bee sounds are returned unchanged. The outline naturally ends with the entity; no target, attack, click, or interaction is generated.

**Lasso REEL Sound (default on, 64%):** follows SkyHanni's relation instead of scanning arbitrary `REEL` text. QCA first requires a Leashable entity whose received leash holder is the local player, then accepts only an exact `REEL` ArmorStand within two blocks of the expected label position. One local pling plays on the transition into that state; a persistent label cannot replay it every tick, and leaving the state arms the next legitimate cue. Volume is a continuous 0–100% slider and the General alert master mute still applies.

### 4.2 Miria Contest HUD

Reads the received `Miria's Contest` scoreboard/Tab block, including live tier lines such as `COMMON with 151` and `Uncommon requires +99`. QCA shows the next bracket, exact score remaining, and expected Safari Ticket only in the combined Hunting HUD. Sidebar injection has been removed, and the contest timer is deliberately not parsed or repeated because the received scoreboard already displays it.

### 4.3 Critter Behavior Assistant

Uses only locally rendered, custom-named Critters and physical player movement. Blue Jay and Goldolot show the documented 8-second and 5-second stand-still countdowns when the player holds a received Lasso or Fishing Net. Dustybit and Hideonsun show the documented four-jump and three-projectile instructions, plus exact progress when the client receives it in a name or message. Every prompt is a center-screen title with optional sound. After the exact received `CAPTURE! You caught ...` confirmation, guidance for that captured Critter name is suppressed for three seconds so a removed entity or replacement nameplate UUID cannot replay the prompt; a different Critter type remains eligible immediately, and a real same-type target still present after the short window resumes normally. Each Critter can be disabled separately; no aim, movement, tool use, or capture input is generated.

### 4.4 Benefactor and rare Tree Gift alerts

The Benefactor panel merges the player's bounded Tab/scoreboard block, an already-open Forest/Desert Temple menu, and the exact received `BENEFACTOR: You donated ... will receive ... +Nd!` message. It retains active/inactive state, multi-day remaining duration, temple-specific effect text, and donation text under the account/Profile key. Repeated absolute countdowns do not extend the timer, same-temple donation messages add their received duration, switching temples starts the new temple duration, and a fresh authoritative donation cannot be erased by a briefly stale open menu. Each row defaults on. State changes and the final 30-second warning use the shared center alert.

Tree Gift parsing no longer assumes Torrhus and listens to both normally displayed game chat and raw client-received game chat canceled by a compatible compactor. The exact personal `+N rewards gained! (hover)` summary remains sufficient only for its own attached `SHOW_TEXT`. Separate `BONUS GIFT` percentage rows and exact `A <name> fell from the Tree!` rows become eligible only inside a 15-second, 64-block-border-delimited gift block that has also received `TREE GIFT`, the player's `You helped cut ...` contribution, and their personal reward summary. Alerts are deduplicated per block. A nearby player's public line alone, lasso capture text, or an incomplete block cannot arm an alert. It alerts for Firefox, Groundhog, Drybark, Puck, Grizzly Bear, Signal Enhancer, Chameleon Shard, Hummingbird Shard, Dreadwing, and Enchanted Book (Karma I). All ten default on and are individually switchable.

### 4.5 Safari Run Dashboard and Critterdex

Entering Critter Safari starts a local session timer. The combined Hunting HUD tracks received/captured Shard amounts, elapsed time, and Ticket Tier. It does not duplicate the existing Safari Essence display. It also shows Cavern, Forest, Haunted, and Icy session progress across the official 37-Critter list; complete captured and missing names are shown for the currently received biome and wrap without truncation. Each remaining row defaults on. Loot Share adds to the Shard total but does not falsely mark the Critter as personally captured.

### 4.6 Sparkling, Floor Drop, Quest Item, and Wumpa assistants

- **Sparkling Critter Alert:** recognizes the official spawn chat and visible `SPARKLING` custom name, then shows a center title, biome, its own configurable sound, and an optional vanilla outline. All options default on; the outline has a full RGB picker.
- **Floor Drop & Quest Items:** once per second, checks a bounded 10-block horizontal/3-block vertical area for the String/tripwire state used by Safari Floor Drops, shows a one-time center warning, and reports the nearest distance. It also lists the official Safari Quest Items actually present in the local inventory. It never requests chunks or interacts with a block.
- **Wumpa Encounter:** the combined HUD lists the eight non-Wumpa Icy Critters—Billygoat, Mantis Shrimp, Nozzlenose, Polaris, Shuddersquid, Strongarm, Tepid, and Troodon—with a green check or red cross and an exact `n/8` count. The player's anchored capture confirmation and the received `LOOT SHARE ... catching a <Critter>` teammate confirmation both update this party encounter checklist; Loot Share still does not mark the separate personal Critterdex. Once Wumpa has spawned, the eight-row list is replaced by `Wumpa: Spawned` and the live phase. Reaching 8/8 produces one center `WUMPA SPAWNED` alert through the existing Wumpa 64%-volume channel. The official massive-footsteps/awoken text shares the same per-run deduplication state, so it can update available/awoken phase without replaying the spawn alert. Movement and projection now follow the actual locally loaded Ravager body nearest the Wumpa label instead of the Armor Stand name carrier. Available/sleeping/awoken/chasing/stunned/caught/failed phases use short movement/stillness confirmation windows to avoid one-tick flicker. The experimental, default-off red line projects current horizontal movement up to the first local collision; it does not claim knowledge of a future server decision or steer the player.
- **Snoozle Wall Overlay (default on):** once per second, scans only nearby already-loaded blocks for small connected wall components containing both Wiki-documented materials, `Cobbled Deepslate` and `Tuff`. Only faces exposed to air receive a translucent overlay; oversized natural formations and single-material patches are rejected. The default is green and the secondary settings page provides the standard RGB/HSV picker. No block is clicked, changed, or requested from the server.

### 4.7 Cold, Doomspiral, Fairy Soul, and Critter highlights

- **Cold Safety Alert (default on):** parses only a received `Cold` value. It warns once above 80 and again above 90 by default; both thresholds are sliders and must remain ordered. On the first value strictly above the first threshold, QCA immediately searches only Block Entities in already-loaded client chunks, selects the nearest normal or soul campfire, and submits a red beacon beam. While still needed it refreshes that nearest result every 40 client ticks; the beam closes as soon as the next received Cold value decreases. It never requests a chunk or moves the player.
- **Doomspiral Ready (default on):** after the bounded local-inventory Quest Item scan finds at least 4 `Soothing Incense`, shows one center warning. Four is the Wiki-documented requirement; the warning resets if the count falls below four.
- **Warden Capture-Ready Alert (default on):** observes only locally loaded Warden entities inside the bounded Doomspiral arena. It follows the supplied BabyzombieAddons implementation's 140-client-tick cooldown with received player-latency compensation, rejects emerging/digging poses, and announces the not-ready-to-ready transition once per entity with a dedicated default-on 64%-volume sound. The 140-tick implementation threshold comes from the supplied reference mod; the Wiki documents Doomspiral's post-escape enraged/cannot-capture phase as approximately five seconds.
- **Fairy Soul Waypoints (default off):** submits pink beacon beams at the 12 official Torrhus Canyon and 4 official Critter Safari coordinates. Torrhus and Safari groups can be disabled independently. When the client receives either the successful `SOUL! You found a Fairy Soul!` response or the already-found response, QCA marks the nearest listed Soul within 10 blocks as collected for the current profile, saves it locally, and immediately stops rendering that beam. It never clicks or queries a Soul.
- **Safari Critter Highlight (default on):** outlines a locally visible real Critter entity with the standard color of its official Shard rarity. The bundled table covers all 37 Safari Critters. Armor Stand backed capture props are deliberately excluded from both normal and Sparkling outline paths because their support body cannot be separated reliably from the equipped capture model in this renderer version. This safe fallback prevents the tall Armor Stand outline shown during capture; real non-Armor-Stand Critters retain their rarity or configured Sparkling color. No entity data is changed.

### 4.8 Safari Belt tooltip

When the item ID is exactly `SAFARI_BELT`, QCA embeds all four locally observed Safari Milestone levels. The shared parser understands combined rows and split title/lore layouts such as `Cavern Milestone` plus `Current Level: IV`, ignores locked requirements and capture-progress fractions, and max-merges repeated entries. Confirmed levels are persisted separately per Minecraft account/SkyBlock Profile and only change after a higher level is observed in an already-open Safari Milestones menu or received item lore. The tooltip repeats only attribute values actually present in the received item lore, avoiding a guessed Sweep total while the official table and displayed item could differ. Milestone and bonus sections default on and are independently switchable.

### 4.9 Center warning style

All Hunting warning/prompt features use the vanilla center title/subtitle layer. Critter Behavior, Benefactor, Tree Gift, Sparkling, Floor Drop, Wumpa, Cold, Doomspiral, and Warden readiness each have their own default-on sound and continuous 0–100% volume slider, defaulting to 64%. General contains the master mute. Alert deduplication prevents a persistent entity, name tag, or Tab line from replaying every tick.

### 4.10 Manual reconnect

The default-on General feature adds a vanilla-width `Reconnect` button to `DisconnectedScreen`. QCA records the address, display name, server type, and resource-pack preference at the start of the normal `ConnectScreen` attempt, so initial connection failure and later interruption both retain a target for the current client run. A click creates a fresh vanilla `ServerData` and starts one normal connection. There is no countdown, background retry, loop, auto-join, chat, or command.

## 5. Crimson Isle

### 5.1 Faction task tracker

**Purpose:** keep accepted faction quests visible without holding Tab open.

**What it does:** only while the parsed location is Crimson Isle or one of its documented sub-locations, QCA reads the received `Faction Quests:` widget. Each anchored line preserves its original `✖` or `✔` state, unmodified quest name, and optional `xN` requirement. Unknown lines remain visible as received; QCA does not infer hidden progress or translate game names. The feature is independently switchable and shares the mining task panel's appearance/position because the two island HUDs cannot load simultaneously.

## 6. Combat

### 6.1 Ender Dragon Highlight

**Purpose:** make large, fast Ender Dragons easier to track visually during End fights.

**What it does:** at the end of vanilla entity render-state extraction, sets the Ender Dragon outline color only when the locally parsed SkyBlock location is The End or Dragon's Nest. The complete RGB picker and presets are available in this feature's secondary settings. No entity metadata, hitbox, movement, targeting, or combat input is changed.

### 6.2 Power Orb & SOS Despawn Alert

**Purpose:** make it immediately obvious when a temporary combat support deployable has ended.

**What it does:** exactly matches the received player-owned despawn line for Radiant, Mana Flux, Overflux, and Plasmaflux Power Orbs. Warning, Alert, and SOS Flares use a confirmed local lifecycle: an exact Flare item use must be followed by the successful placement sound before the three-minute timer starts. Replacing a Flare restarts the complete three-minute lifecycle from the newest confirmed placement and invalidates the previous expiry. Both air-use and use-on-block paths are observed; an exact placement sound can recover a missed use callback only while the local player still holds a recognised Flare. Failed uses, entity unload, world changes, distance, and effect range never create a false expiry. It displays `<Deployable Name> Despawned!!!` as a large red center title. Power Orb alerts, Flare alerts, center text, sound, and 0–100% volume are separately configurable; the feature and sound default on at 64%.

## 7. Pets

### 7.1 Equipped Pet HUD

**Purpose:** show the equipped pet and its server-presented progress without opening the Pets menu.

**What it does:** reacts immediately to anchored summon/despawn/Autopet messages and parses the received `Pet:` Tab widget once per second. QCA constructs a plain player head from a verified profile and deliberately omits synthetic `petInfo`, preventing external item-model predicates from replacing the HUD icon. The active Pets menu and nearby rendered pet may provide a matching Profile, but their entire ItemStack is never reused. Bundled offline metadata contains 88 base profiles, 352 skin profiles, 5,422 pet-only current/animated texture mappings, and 87 accessory definitions. Dynamic variants are assigned by the longest exact skin-family prefix; Baby Spinosaurus alone has 60 recognized current/animation textures. No runtime texture download or Firmament installation is required. All pet text is fully measured, including bold style, and is never shortened with an ellipsis. The current-level and progress-to-max lines default on; the latter hides at max level without affecting the held-item row. A held item confirmed through the Pets menu, Tab, or received chat is retained per pet in QCA's local config and remains available after reconnecting. Skin name and Ancient Golden Dragon overflow level are supported. Pet items are shown as icon + name by default, with icon-only and name-only modes. Large values use one decimal with `k`, `m`, `b`, or `t`.

## 8. Items and menus

### 8.1 Century Cake Effect Expiry Alert

**Purpose:** make all long-duration Century Cake bonuses and their real expiry times visible without creating twenty separate settings.

**What it does:** one default-on master switch controls every supported cake effect. The tracker accepts the two exact client-received forms: first activation `Yum! You gain <bonus> for 48 hours!` and refresh `Big Yum! You refresh <bonus> for 48 hours!`. Private-use stat glyphs such as the Hunting Fortune icon are normalized before matching. A valid line sets an absolute 48-hour real-world expiry, so offline time continues to count. `/cake` and `/centurycakeeffect` open a local `/effects`-inspired screen containing all 20 cake heads, bonuses, rarity, status, and remaining time. A single expiry produces a center title and `[QC] Century Cake <Effect> Expired! Click Here For Cake Eating`; expiries found in the same check are merged into one count. The link text is underlined and sends exactly `/visit northwestcloudy` only after a player click. The independent local sound is enabled by default at 64%.

### 8.2 Attribute Shard Fusion Guide

**Purpose:** answer both “what makes this Shard?” and “what can I make with this Shard?” without leaving Minecraft or guessing an order-sensitive recipe.

**What it does:** opens a JEI-inspired browser backed by a bundled offline catalog of exactly 320 current Bazaar-listed Shards. Search accepts the original English name, internal Shard ID, attribute/effect, rarity, category, family, related skill, mob type, or acquisition text. The **Details** tab presents the complete normalized Wiki effect, semantic classifications, and each documented natural or Fusion acquisition method. The **Recipes** tab enumerates every ordered input pair that can produce the selected Shard; this includes Shards such as Queen Bee that have both natural sources and Fusion recipes. The **Uses** tab enumerates every ordered pair containing the selected Shard and the outputs it can produce. Left-clicking a result opens Details, right-clicking opens Uses, and Back/Forward history preserves the browsing chain. No line is shortened with an ellipsis.

Recipe cards preserve first/second-input order, show the amount consumed from each side, show up to three selectable outputs in the algorithm's real order, distinguish ID/Chameleon output (`1`) from special-rule output (`2`), and note the Pure Reptile 2–20% chance to double output. The input amounts follow the documented rule: Chameleon Shard uses `1`, Reptile/Amphibian/Elemental Shards use `2`, and all others use `5`.

The data generator follows the [Wiki-documented Attribute Fusion rules](https://hypixelskyblock.minecraft.wiki/w/Attribute_Fusion) and current [Attributes tables](https://hypixelskyblock.minecraft.wiki/w/Attributes), then uses the [official Bazaar endpoint](https://api.hypixel.net/v2/skyblock/bazaar) as the exact 320-item allow-list. The current Wiki tables contain 321 rows; legacy Rainbug is excluded because it is absent from the official Bazaar Shard universe. Every catalog Shard has effect and acquisition data; where the current table does not document a method (currently Wild Hog), the UI says so instead of inventing one. The generated JSON is committed with the mod. Runtime never contacts the Wiki, Bazaar API, NEU, or another mod.

All 320 catalog IDs have their corresponding bundled Shard icon generated offline from the reviewed MIT-licensed SkyShards icon set; the generic amethyst placeholder is not used. Shards that intentionally share the same upstream in-game appearance retain that shared icon. After the client receives a matching native Shard `ItemStack` in an already-open menu or inventory, that stack takes priority and is retained in a session-wide cache so its resource-pack/server presentation remains authoritative across guide pages. QCA does not download an icon at runtime; an already-received player head remains subject to Minecraft's normal renderer.

Epic names use Minecraft's dark-purple `§5` instead of light-purple `§d`. Rarity, stat, category, mob-type, skill, and acquisition lines use the corresponding SkyBlock/Minecraft semantic colours. Clickable Shard text darkens and underlines only while its visible text is hovered. The search field releases focus when the player clicks outside it, presses `Esc`, or presses `Tab`, and it accepts input again after a direct click. Recipe inputs and candidate outputs are compact content-width groups centered inside the card; their click regions match the visible icon/text bounds rather than distant card halves. The local `/qshard [English query]` command and the feature's **Open Guide** setting open this screen only. The optional key chord is unbound by default. None of these entry points sends chat, a server command, a packet, a menu click, or an API request.

### 8.2 Shard Planner and local warehouse

**Purpose:** turn the Guide's direct recipes into an actionable, multi-step preparation plan while keeping every decision and action under player control.

**Planner:** enter a target Shard and quantity, then choose Ironman/Normal and Fastest/Cheapest. The planner evaluates ordered direct recipes to a bounded depth, prevents cyclic routes, exposes other candidate recipes for the selected tree node, and produces both a complete tree and hunt/buy/already-owned base-material totals. **Materials Only** hides the tree and shows only those totals. The tree is informational: it never clicks the Fusion House, chooses an output, or moves an item.

**Cost models:** Fastest uses the bundled baseline Shards-per-hour table or the player's local per-Shard override. Hunter Fortune, local handling time, and Pure Reptile expectation are considered. Kraken can instead use Kuudra tier, estimated clear time, coins/hour, key opportunity cost, and downtime. Normal Fastest may compare hunting time against price/coins-hour time; Normal Cheapest uses prices directly. Ironman never uses Bazaar. Guaranteed integer material requirements remain visible even where Pure Reptile can improve the expected route cost.

**Optional prices:** QCA has no Bazaar downloader and no required price mod. If a compatible Skyblocker is present, QCA reflectively calls only its public `ItemUtils.getItemPrice(String, boolean)` method and copies the already-cached result. This creates no compile/runtime dependency. SkyHanni and Firmament currently do not provide a stable public cross-mod price API, so QCA deliberately does not read their private fields. With no compatible price provider, the UI marks Cheapest as unavailable and all offline, Ironman, rate, recipe, detail, line, and warehouse functions remain available.

**Warehouse:** when—and only when—the player physically opens a received menu titled `(page/total) Hunting Box` or `Hunting Box`, QCA reads visible Shard item IDs and exact `Owned: N Shards` lore, then saves that observed page for the current local profile. It never sends `/hb`, changes page, clicks a slot, or infers an unseen page. A transition/empty frame cannot erase a prior snapshot. The planner can subtract saved quantities from the chosen route; the warehouse screen shows when that profile was last observed and allows a local clear.

**Other pages:** Recipes accepts independent input and output filters for direct ordered relationships. Shards shows effect, family, Skill, mob type, every bundled acquisition entry, the baseline rate, and a local editable rate. Fusion Lines draws the selected Shard's nearby ID/Special/Chameleon relationships; nodes can be selected and physically dragged, and positions persist in local config. Planner target, quantity, mode, objective, Materials Only, custom rates, graph positions, warehouse use, price-side preference, and Kuudra parameters all persist locally.

## 9. Chat

### 9.1 Chat Peek

**What it does:** while the configured key/chord is physically held and no screen is open, QCA asks the vanilla chat renderer to use its foreground/focused height. Releasing the key restores normal chat immediately. The key defaults to unbound. While peeking, the wheel target defaults to chat history; the player may switch it to the normal hotbar. No message is sent, copied, opened, or modified.

## 10. Configuration and HUD styling

Press the rebindable `O` key or run `/qca` or `/qc` to open the single **Features** configuration tab. Each alias is added only if its client-command name is unoccupied. These local commands open a screen and send no chat or command content to the server. Mod Menu can open the same screen when installed. **General** is the first category and contains UI animations plus the alert master mute. Left-click toggles a card, its left blue strip indicates the enabled state, and right-click opens meaningful secondary settings. Cards and secondary pages do not repeat an enable switch. The language switch controls QCA UI text independently of the Minecraft client language.

Every HUD has independent background alpha/color, border visibility/width/color, title color, bold, shadow, and 50–200% scale. Editable colors share an RGB/HSV picker with presets, and every background picker includes an explicit Transparent choice. The bottom-left **Edit HUD** button opens the layout screen, which shows only HUDs actually loaded in the current location/state. Dragging a border or corner uniformly resizes that HUD; each has a small settings gear. Mouse release immediately saves position and scale, and every style persists in `config/qcloudy_addition.json`. Opening animations default to on and are optional.

Every hotkey is captured inline on its existing feature-settings row; QCA no longer opens a dedicated chord screen. The listening row accepts a keyboard key or mouse button (including buttons 1–5 and additional GLFW side buttons) plus any combination of Ctrl, Shift, Alt, and Cmd/Super. `Esc`, Backspace, or Delete clears the binding and leaves the player on the same settings page.

The permanently available local `/th` and `/helia` commands have no settings. Explicit player invocations send exactly `warp torrhus` and `chapter torrhus`, equivalent to manually entering `/warp torrhus` and `/chapter torrhus`; they are never triggered automatically.

Broad numeric ranges use draggable sliders: HUD opacity and scale, cursor-memory duration, Fishing Bite volume, and teleport-sound volume. Values update continuously while dragging and persist on mouse release. Small discrete sets such as border width, progress-display modes, and sound presets remain direct cycle controls.

### 10.1 AOTE/AOTV sound customization

**Purpose:** let players change intrusive teleport sounds without forcing the tools to be silent.

**What it does:** the master feature and both teleport types default to original audio. Instant Transmission and Etherwarp can independently switch from the received original sound to one of six local Minecraft presets: Chorus Teleport, Enderman Teleport, Amethyst Chime, Experience Orb, End Portal Fill, or Shulker Teleport. Each custom sound has its own 0–100% volume slider at the 64% default; pitch is not altered. QCA replaces only a recognized nearby sound while the local player is holding `ASPECT_OF_THE_END` or `ASPECT_OF_THE_VOID`; it does not change packets, item use, cooldown, distance, or movement. Version-3 muting settings migrate to original audio.

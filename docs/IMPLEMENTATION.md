# QCloudy_Addition implementation and data-flow reference

This document explains what each public feature is for, which client-visible information it consumes, how QCA processes that information, what the player should see, and whether the feature can produce an outbound action. It tracks the current Alpha 35 development build for Minecraft 26.1.2; the latest stable Release remains `0.3.9` for Minecraft 26.1.2 and 26.2.

## 1. Runtime architecture

QCA is a Fabric client entrypoint. `QCloudyAdditionClient` performs six kinds of registration:

1. Loads normalized JSON settings and profile-aware inventory data.
2. Samples the vanilla Tab list and scoreboard once per second.
3. Receives normal and canceled-display game chat through Fabric message events.
4. Registers HUD and world-render callbacks.
5. Applies narrowly targeted Mixins for container input/rendering, chat peek, outlines, sound replacement, hotkeys, cursor memory, and connection screens.
6. Initializes the Release-only notification gate; Alpha ends at the local gate, while Beta/Release may schedule one bounded manifest request after the first world join.

The main tracking flow is:

```text
received Tab / scoreboard / chat / menu / title / entity / inventory / loaded blocks
                              ↓
                bounded parser or local-state filter
                              ↓
              session state or account/profile cache
                              ↓
        HUD, tooltip, outline, beacon, overlay, line, or sound
```

### 1.1 Optional unified-provider adapters

> **Experimental concept test:** Unified Settings Editor and Unified HUD Editor are both disabled by default and are not yet stable. Provider updates can invalidate previously recognised structures. Back up configuration files, verify important changes in the provider's native editor, and use these integrations cautiously.

`UnifiedModIntegration` recognises the provider IDs for SkyHanni, Skyblocker, Firmament, BabyZombieAddons, and Feesh, but does not reject an installed build because its version string is newer or differs from a whitelist. It has no compile-time dependency on them. Two independent `ModConfig.Integrations` gates default to false. Unless **Unified Settings Editor** is enabled, the normal settings catalog is built from QCA features only and no provider configuration is probed. Unless **Unified HUD Editor** is enabled, `externalHuds()` returns an empty list before provider discovery. Enabling only the HUD gate is valid and performs the minimum provider discovery needed for HUD ownership without exposing provider-setting cards. QCA-owned settings and HUDs bypass both gates.

The normal card path does not toggle a disabled integration master immediately when no valid session snapshot exists. It first opens `IntegrationScanConfirmScreen` with a settings- or HUD-specific explanation. Confirming toggles that one master, creates the `ScanJob`, and opens its progress page; cancelling returns to the catalog with the master still off and no job created. Each master page has a Refresh action, but it is unavailable while a job is running and otherwise opens the same second confirmation before requesting a replacement scan. Cancelling Refresh leaves the previous immutable `ScanSnapshot` live. An enabled master restored after restart does not create a scan on the first tick: `IntegrationScanService.tick()` only advances an already-confirmed job from detection through provider reading, classification, and validation. If both gates are disabled, QCA cancels pending work and releases the session snapshot. Opening an ordinary settings screen does not rescan.

The shared snapshot contains provider capabilities once, but `scanStatus(SETTINGS)` and `scanStatus(HUD)` calculate and display separate totals. Only installed providers that produced readable settings or HUD capabilities appear in the completed-provider summary. Provider absence is silent. One adapter failure is isolated as a partial scan and cannot discard valid results from another adapter.

Classification is deterministic and local. Provider-native paths and the verified rule table run first. Only a function that still has no category is passed to `LocalFeatureClassifier`, a small fixed weighted token classifier over its path, title, and description. A minimum score and winning-margin requirement reject uncertain results. The classifier may assign a category only; it cannot establish cross-mod equivalence, select or disable a provider, mutate a setting, move a HUD, or train at runtime.

The registry defensively walks the live provider configuration and exposes only readable/writable Boolean or enum toggles, annotated or otherwise bounded numeric values, and recognised HUD position structures. Exact `enabled` objects can expose their validated sibling settings; future prefixed layouts such as `enabledCommissions` can attach only settings sharing the `commissions` stem, including `commissionsX`, `commissionsY`, and `commissionsScale`. One inaccessible or unknown field is omitted without discarding unrelated recognised branches. Writes call the provider's own update/save/dirty mechanism and never edit another mod's JSON file directly. Exact cross-mod aliases merge one logical feature; enabling a selected provider first disables only bindings attached to that same alias. The provider choice is persisted in QCA config, while every provider's own values remain stored by that provider.

Feesh uses Kotlin delegated settings rather than public fields. Its adapter pairs public getters and setters, groups only deterministic settings under Boolean feature roots, invokes the live setter, and then calls Feesh's own `Settings.save()`. Orphan enum/numeric/compound settings that cannot be attached safely are reported as gaps instead of receiving a guessed feature card. Feesh HUDs are read from its live `FeeshGui` registry. QCA preserves Feesh's LEFT/CENTER/RIGHT anchor semantics when converting to Edit HUD's top-left coordinates and persists released changes through Feesh's `PersistentDataManager`; disabled, condition-failed, or empty-line overlays are not exposed as movable panels.

`HudLayoutScreen` combines QCA's currently loaded panels with enabled HUDs belonging to the selected external provider. Drag/resize uses a transient preview and writes native coordinates/scale only on mouse release. Capability discovery intentionally excludes opaque compound color/keybind/config-editor objects and new structures whose setter, range, or validation contract QCA cannot prove safe. Such options remain available in their original mod instead of being guessed by QCA.

`IntegrationCompatibilityScreen` is deliberately separate from `Feature` and `UnifiedFeature` toggles. It reads the latest completed snapshot and reports named features whose primary control, secondary setting, classification, or recognised HUD coordinate contract is unavailable, with independent Settings/HUD flags; supported features are filtered out. Empty or unreadable recognised provider roots become a provider-level configuration gap instead of a false all-supported result. The report never invokes a setter or save path. Provider grouping is computed once per opened report, and wrapped row geometry is cached until the content width changes instead of being rebuilt every render frame.

Location detection first confirms a Hypixel host and a received SkyBlock scoreboard. It then classifies the current island from the location-marked scoreboard line and a bounded list of known original location names. Island-specific parsers and renders do not run globally.

### 1.2 Always-on Release notification

This is lifecycle infrastructure, not a configurable gameplay feature, so it has no `ModConfig` value and no feature card. `ReleaseBuildInfo` loads the build resource containing the QCA channel, version, Minecraft target, and positive Release-baseline sequence; Alpha 35 embeds baseline `1`. The client entrypoint registers the world-join listener for every channel, but Alpha returns from the local gate before scheduling a task or constructing an HTTP request. For Beta and Release, the atomic process guard in `ReleaseUpdateChecker` permits only one scheduled check for the lifetime of the client process. The first join schedules the check after five seconds; the network work runs away from the render/client thread.

`ReleaseUpdateChecker` attempts at most one HTTPS `GET` to `https://www.qcloudy.net/assets/data/release-manifest.json`, with a five-second connect timeout, ten-second request timeout, redirect policy `NEVER`, an HTTP-200 requirement, a `QCloudy_Addition/<version>` User-Agent, and a 128-KiB maximum response. `ReleaseManifest` parses schema version 1 and rejects the whole response unless `channel` is exactly `Release`, `releaseSequence` is a positive integer greater than the embedded baseline, the version is three numeric parts with an exact `v<version>` tag, and exactly one asset has the exact current-Minecraft playable filename `QCloudy_Addition-<version>+<minecraft>-Release.jar`. That asset must also contain a lowercase `sha256:<64 hex>` digest and an HTTPS URL whose host, repository, exact `v<version>` Release tag, and filename all match the official `northwestcloudy/QCloudy-Addition` GitHub asset path. Duplicate matches are invalid. The checker does not choose the first vaguely matching release or compare Alpha/Beta suffixes as if they were stable versions.

On failure, timeout, malformed data, an unsupported schema, a Beta/Alpha channel, missing or duplicate compatible asset, non-increasing sequence, Sources-only entry, redirect, or untrusted URL, the checker logs and stops without player-facing errors or retries during that process; the next launch may check again. On success it hands the immutable result back to the Minecraft client thread. If no player is available, the pending result remains in memory until the next world join. Presentation occurs at most once per process as a vanilla `SystemToast` through version-specific `MinecraftClientCompat.toastManager` and a local clickable chat message. The two user actions open `https://qcloudy.net/download/` and `https://qcloudy.net/changelog/`; neither action is a JAR URL, and QCA never downloads, installs, replaces, launches, or restarts anything.

No username, UUID, server address, SkyBlock profile, mod list, gameplay value, telemetry identifier, cookie, token, or authentication header is added to the request. Ordinary HTTPS transport still exposes the connecting IP address and `QCloudy_Addition/<version>` User-Agent to the destination. The manifest result is not written into account/profile persistence. `ReleaseBuildInfoTest`, `ReleaseManifestTest`, `ReleaseUpdateCheckerTest`, and `ReleaseUpdateStateTest` cover the build gate, embedded metadata, direct endpoint and transport bounds, strict stable-channel/sequence checks, exact asset selection, untrusted URLs, malformed input, duplicate matches, process-lifetime request gating, pending-result retention, and one-time notification consumption.

## 2. Settings, localization, and HUD framework

### Purpose

Give every feature a clear single category and let players customize visual output without editing a file.

### Information and APIs used

- Local mouse/keyboard input through Minecraft input events and targeted input Mixins.
- `HudElementRegistry` for screen HUD submission.
- `GuiGraphicsExtractor` for Minecraft 26.1.2 and 26.2 text, item, panel, and texture rendering, with version-specific access isolated in `MinecraftClientCompat`.
- Local `qcloudy_addition.json` for settings and HUD layout.
- Bundled `en_us.json` and `zh_cn.json` for QCA-owned labels.

The screen layer derives inputs, buttons, lists, detail viewports, and click targets from shared layout records. Shard results and metadata have separate scroll state, rate controls are bottom-docked, narrow Planner/Settings layouts reflow before collision, and Fusion Lines use a canvas taller than the viewport when needed. Setting rows add hit targets only while intersecting the visible scissor rectangle.

### Implementation

- `ConfigScreen` owns one searchable Features page and preserves the required category order, but computes the visible sidebar from the currently available first-party and provider features. A category such as Dungeons is absent when it owns no visible feature. Fishing's sole first-party cue is placed under the `Bite Alerts` subgroup.
- Left-click changes a feature's primary state; right-click opens only settings specific to that feature.
- `HudLayoutScreen` lists only QCA HUDs currently loaded by location/state plus enabled HUDs from the selected compatible provider. Dragging changes position; dragging a border/corner changes that HUD's native scale when available.
- `PanelStyle` separately stores background color/alpha, border width/color, title color, bold state, shadow state, and scale for Map, Mining, Hunting, and Pet panels.
- `ColorPickerScreen` supplies RGB/HSV controls, brightness, presets, and transparent backgrounds.
- Key chords store a keyboard key or mouse button plus Ctrl/Shift/Alt/Super modifiers. `Esc` while listening clears the binding.

### Expected presentation

A compact dark BLC-inspired—not asset- or code-copied—settings window, blue enabled strip, searchable cards, smooth optional opening animation, and a separate direct-manipulation HUD editor. Positions and scales remain unchanged after restarting.

### Defaults and outbound behavior

English, animations on, Minecraft bitmap font, shadow on, one-pixel cyan border, partially transparent dark background. QCA UI translations never rename server-provided items, tasks, locations, pets, or HOTM presets. No outbound server action.

## 3. Manual reconnect

### Purpose

Let a player retry a failed or interrupted connection without returning through multiple menus.

### Information and APIs used

- Normal `ConnectScreen.startConnecting` arguments: server name, address, server type, and resource-pack preference.
- `DisconnectedScreen` and its vanilla `LinearLayout`.

### Implementation

`ConnectScreenMixin` records the last explicit connection target in memory for the current client process. `DisconnectedScreenMixin` appends one vanilla-width button before the original layout is arranged. Clicking it creates a fresh `ServerData` object and invokes the normal Minecraft connection screen once.

### Expected presentation

A `Reconnect`/`重新连接` button aligned with the existing disconnect-screen controls.

### Defaults and outbound behavior

On by default. One physical click starts one ordinary server connection. No saved address, countdown, retry loop, background connection, command, chat, or authentication bypass.

### 3.1 Fishing Bite Sound

- **Purpose:** replace a missed visual bite window with a short local audio cue, without automating fishing.
- **Inputs:** the directly owned `Player.fishing` hook when available; physical local fishing-rod use; already-loaded Fishing Hooks within a bounded association radius; entities inside the selected hook's bounding box expanded by four blocks; and exact received ArmorStand name/visibility state.
- **Implementation:** directly owned water or lava hooks always win. `FishingHookResolver` records the hook IDs already present when the local player physically uses a fishing rod, then accepts only a newly loaded local-owned or ownerless candidate during the next 40 ticks; an explicit other-player owner is rejected. The same resolver classifies another physical rod use while the direct or fallback hook is still active as reeling, not as a new cast. `FishingBiteSession` is re-armed only by a confirmed new cast, so the lingering `!!!` frame after reeling cannot play twice. `FishingBiteAlert` still requires an invisible ArmorStand with a visible custom name exactly equal to `!!!`, and keys playback to the chosen hook entity ID. The supplied MP3 is converted before packaging to `assets/qcloudy_addition/sounds/fishing/ciallo.ogg` and registered by `sounds.json`.
- **Expected effect:** one Ciallo cue when the local hook becomes ready; no per-tick replay from a persistent marker.
- **Default/outbound:** feature off, volume64% on a 0–100% slider; local sound only. The rod-use callback always passes through and never casts, reels, clicks, moves, sends a packet, chat, or command. The wider hook lookup is inactive while idle.

## 4. Maps

### 4.1 Dwarven Mines

- **Purpose:** replace an unreadable route web with a compact regional overview.
- **Inputs:** local player X/Z/yaw and received scoreboard sub-location. Y is not read for this map.
- **Implementation:** `DwarvenMapProjection` exposes only `project(x, z)` and applies one clamped continuous transform across the supplied single-layer texture. It has no Y or location-name parameter, so vertically overlapping bridges and caverns cannot select different image regions. The result is deliberately approximate and stable rather than a precision cave survey.
- **Expected effect:** the supplied English-labelled regional map and a live red directional arrow that stays synchronized with its named region and local X/Z position.
- **Default/outbound:** on; render only.

### 4.2 Glacite Tunnels

- **Purpose:** keep a multi-height tunnel network readable.
- **Inputs:** local X/Y/Z/yaw.
- **Implementation:** `HudRenderer` selects low, middle, or high bundled map imagery at Y 126 and Y 143. All images use the same X/Z projection; generated label cards are collision-separated.
- **Expected effect:** the map changes layer as elevation changes while the red arrow does not jump horizontally. All point names remain English.
- **Default/outbound:** on; render only.

## 5. Mining and Crimson Isle tasks

### 5.1 Mining commissions, powders, and HOTM preset

- **Purpose:** show objectives without holding Tab open.
- **Inputs:** the received `Commissions:` and `Powders:` Tab widgets; exact `Heart of the Mountain Slot`/loadout menu contents.
- **Implementation:** `TabListTracker` extracts a maximum bounded widget block. Exact `current/target` wins. Percentage-only known commissions can use documented island targets; unknown tasks remain percentages. `HotmSlotTracker` caches only a menu row explicitly marked `SELECTED` or received `Current:` lore.
- **Expected effect:** every complete commission name above a separate progress bar; percentage by default or numeric mode; Mithril, Gemstone, and Glacite Powder rows; optional `HOTM: <original name>`.
- **Default/outbound:** tracker and HOTM row on; percentage mode; no command or menu click.

### 5.2 Crimson Isle Faction Quests

- **Purpose:** preserve the complete faction task list outside Tab.
- **Inputs:** bounded received `Faction Quests:` Tab widget.
- **Implementation:** each `✖`/`✔`, name, and optional amount is parsed by `TabListTracker` and rendered through the Mining HUD slot only on Crimson Isle.
- **Expected effect:** complete original English task names, amounts, and ready state without ellipses.
- **Default/outbound:** on; render only.

## 6. Torrhus and Foraging

### 6.1 Helia Chapter and resources

- **Purpose:** combine long-lived progression in one readable HUD.
- **Inputs:** separately bounded Tab and scoreboard blocks, a four-second/twelve-line Chapter chat block, and already-open Helia Chapter menus.
- **Implementation:** `HuntingTextParser` creates partial snapshots; `HuntingTracker` merges only nonblank fields and clears stale task state when an explicitly different Chapter is observed. Forest/Desert Whispers, Forest/Safari Essence, Sweep, and Forest Fortune accept absolute received snapshots; only exact gain chat is additive. Values are saved per Minecraft account and received SkyBlock profile.
- **Expected effect:** Chapter, complete task name, task progress, and six resources in the combined HUD. Completed count, total progress, and next unlock are optional and off by default. Safari Essence appears here, not in the Safari Dashboard.
- **Default/outbound:** base rows and resources on; no command or menu click.

### 6.2 Tree Critter Timer

- **Purpose:** display the server's actual Honeycomb attraction time without drift.
- **Inputs:** the nearest loaded entity/nameplate matching exact `Critter in: <duration>` text.
- **Implementation:** every ten client ticks, `HuntingTracker` chooses the nearest matching visible label. It does not infer which Pot was used or run an independent countdown.
- **Expected effect:** the exact received countdown inside the combined HUD, including any server-side speed or instant modifier.
- **Default/outbound:** on; read/render only.

### 6.3 Miria Contest

- **Purpose:** show the next useful target rather than duplicating the scoreboard timer.
- **Inputs:** received contest tier/score/requirement lines from Tab and scoreboard.
- **Implementation:** `HuntingTextParser.ContestSnapshot` computes the next bracket, remaining score, and ticket estimate only when an active contest snapshot is complete enough.
- **Expected effect:** next bracket, required remaining score, and expected Safari Ticket rows inside the combined HUD. No sidebar injection and no duplicate timer.
- **Default/outbound:** on; render only.

### 6.4 Benefactor

- **Purpose:** keep temple benefit status and expiry visible.
- **Inputs:** bounded Tab/scoreboard text, already-open Forest/Desert Temple menu, and exact received donation chat.
- **Implementation:** received donation data is briefly authoritative so an old open menu cannot overwrite it. Local arithmetic converts a received duration into an expiry timestamp. State is persisted per account/profile and expires locally.
- **Expected effect:** active/inactive status, remaining time, temple/effect, and donation rows.
- **Default/outbound:** all rows and its independent 64% alert on; no command, click, or donation action.

### 6.5 Rare Tree Gift

- **Purpose:** alert only for configured rare rewards that belong to the local player's Tree Gift.
- **Inputs:** raw received game-chat `Component`, including `SHOW_TEXT`, plus messages canceled from display by a compatible chat compactor.
- **Implementation:** `TreeGiftAlertSession` accepts normal or multi-line received components and expires an open block after 15 seconds. The player-only `+N rewards gained!` summary is the ownership proof and can reveal its attached `SHOW_TEXT` loot. Exact bonus/creature rows are buffered until that proof arrives, independent of line order. A proven gift retains a five-second post-border window only for the exact `-A wild <creature> appeared!` sentence; a public creature line without the personal summary is inert. Each loot remains deduplicated per gift.
- **Expected effect:** `RARE TREE GIFT` center title, loot subtitle, and independent sound for an enabled loot.
- **Default/outbound:** feature, all ten configured rare loots, and sound on; volume64%; no chat or command.

## 7. Hunting

### 7.1 Beeheemoth

- **Purpose:** make spawn location and audio easier to notice without interacting automatically.
- **Inputs:** already-loaded Bee entities, entity scale, positions, exact local capture confirmation, and spatial Bee sound instances.
- **Implementation:** the helper accepts only a Bee with scale approximately 9.0. `EntityRendererMixin` supplies the configured vanilla outline. The first observed position becomes a yellow beacon until the player enters ten blocks, the exact personal capture confirmation arrives, or the entity disappears. `BeeheemothSoundCustomizer` changes only non-relative Bee event/resolved sounds within 12 blocks of the loaded entity or a three-second last-known origin.
- **Expected effect:** configurable outline, temporary yellow beacon, and normal spawn/capture Bee sound at the selected volume.
- **Default/outbound:** helper, outline, beacon, and sound on; sound64%; no capture action.

### 7.2 Lasso REEL cue

- **Purpose:** notify the player when a locally held Lasso is ready to reel.
- **Inputs:** local player's visible leash relation and nearby exact `REEL` Armor Stand label.
- **Implementation:** `HuntingTracker` traverses the loaded entity view once, then checks nearby exact labels only when it found a locally leashed entity. It plays only on a false-to-true state transition.
- **Expected effect:** one short cue at readiness, not a sound every tick.
- **Default/outbound:** on at64%; no simulated input or reel action.

### 7.3 Critter Behavior Assistant

- **Purpose:** surface the documented special interaction state of Blue Jay, Goldolot, Dustybit, and Hideonsun.
- **Inputs:** loaded entity names, local movement, held capture-tool name, progress labels, and exact capture confirmation.
- **Implementation:** bounded nearest-entity selection and per-behavior state calculate stand-still or interaction readiness. After a received capture, only that Critter name is suppressed for three seconds to prevent a stale entity from replaying the alert.
- **Expected effect:** center titles such as stand-still, follow-jumps, return-projectile, or ready prompts.
- **Default/outbound:** all behavior helpers and independent sound on at64%; advisory only.

### 7.4 Fairy Souls

- **Purpose:** show known Torrhus/Safari Soul positions on request.
- **Inputs:** fixed documented coordinates, local position, island, and received success/already-found confirmation.
- **Implementation:** `HuntingWorldRenderer` submits pink vanilla beacon beams. A success message hides only the nearest listed coordinate within ten blocks and persists the island/coordinate key per profile.
- **Expected effect:** uncollected pink beams on the selected island; a confirmed collected Soul disappears immediately.
- **Default/outbound:** master off; Torrhus and Safari subsets preselected for when enabled; render only.

## 8. Critter Safari

### 8.1 Dashboard and Critterdex

- **Purpose:** summarize the current Safari run and biome collection.
- **Inputs:** received capture/chat lines, scoreboard/Tab tier and biome text, and local session time.
- **Implementation:** a session accumulator counts only parsed capture results and Shards. The official 37-Critter table provides biome membership and Shard rarity; Loot Share can update Wumpa prerequisites without entering the personal Critterdex.
- **Expected effect:** run time, Shards, Ticket Tier, biome progress, and complete captured/missing names in the combined HUD. Safari Essence is deliberately absent here.
- **Default/outbound:** all dashboard/Critterdex rows on; read/render only.

### 8.2 Cold and campfire safety

- **Purpose:** warn before dangerous Cold and point to a nearby recovery source.
- **Inputs:** received Cold value and already-loaded campfire Block Entities.
- **Implementation:** ordered one-shot thresholds are strictly above80 and90 by default. On first threshold entry, QCA scans only already-loaded chunks within a bounded radius and chooses the nearest campfire. The beacon remains while Cold is high and not falling, and closes as soon as a lower received value establishes a falling state.
- **Expected effect:** two center warnings with independent sound and a red beacon above the nearest eligible campfire.
- **Default/outbound:** on; thresholds configurable; sound64%; no movement or block interaction.

### 8.3 Doomspiral and Warden readiness

- **Purpose:** show when the inventory meets the encounter requirement and when the visible Warden can be captured.
- **Inputs:** local inventory count of exact Soothing Incense, loaded Warden age/pose in the bounded arena, and local-player latency.
- **Implementation:** the incense alert is one-shot at four or more. `WardenCooldownSupport` applies the known 140-client-tick window with latency compensation and rejects emerging/digging poses.
- **Expected effect:** center readiness titles and independent sounds; each Warden alerts once per ready transition.
- **Default/outbound:** both on at64%; no item use or capture action.

### 8.4 Critter and Sparkling visibility

- **Purpose:** distinguish capturable entities and rare Sparkling events.
- **Inputs:** loaded real entities, visible entity names, received Sparkling chat, and official Shard rarity mapping.
- **Implementation:** `EntityRendererMixin` adds real non-Armor-Stand Critters to vanilla outline rendering. Capture props and their supporting Armor Stands are explicitly excluded. Sparkling can use its own configured outline color and center alert.
- **Expected effect:** rarity-colored real Critters, no full Armor Stand support body, and a configurable Sparkling prompt/outline.
- **Default/outbound:** on; Sparkling sound64%; rendering only.

### 8.5 Floor Drop and quest items

- **Purpose:** make already-visible nearby drops and required inventory items easier to track.
- **Inputs:** nearby already-loaded String block states, loaded names/entities, and local inventory.
- **Implementation:** bounded periodic scans update the nearest distance and exact quest-item counts. Persistent objects are deduplicated before alerting.
- **Expected effect:** center prompt and/or combined-HUD rows with distance and counts.
- **Default/outbound:** on at64%; no pickup, pathing, or interaction.

### 8.6 Wumpa

- **Purpose:** track party prerequisites and optionally preview the visible charge path.
- **Inputs:** exact personal capture and teammate `LOOT SHARE ... catching a <Critter>` chat, Wumpa spawn/phase text, loaded Wumpa name carrier, loaded Ravager body, movement, and local collision clipping.
- **Implementation:** eight Icy prerequisite names are stored separately from personal Critterdex state. At 8/8 or an exact spawn signal, the checklist becomes `Wumpa: Spawned`. Optional route logic follows the nearest matching Ravager body, confirms movement/stillness in short windows, and clips a red forward line against local collision data.
- **Expected effect:** check/cross prerequisite list before spawn, then one spawned/phase row; optional red charge line.
- **Default/outbound:** HUD and alerts on, route prediction off, sound64%; no movement or capture.

### 8.7 Snoozle breakable wall

- **Purpose:** mark plausible breakable wall surfaces without highlighting the entire cave.
- **Inputs:** only nearby already-loaded block states.
- **Implementation:** once per second, a bounded flood-fill accepts a small connected component only when it contains both Cobbled Deepslate and Tuff. Single-material and oversized terrain components are rejected. Only faces adjacent to air are submitted as translucent quads. `ClientLevel.hasChunk(chunkX, chunkZ)` prevents any chunk request.
- **Expected effect:** thin translucent color on exposed wall faces; green by default and RGB configurable.
- **Default/outbound:** on; local render only.

### 8.8 Safari Belt

- **Purpose:** keep all four milestone levels and actual received bonuses in the belt tooltip.
- **Inputs:** currently opened Safari Milestone menu and Safari Belt tooltip/lore.
- **Implementation:** `SafariMilestoneParser` accepts combined rows and split title/lore layouts, rejects locked/progress-fraction false levels, and updates Cavern, Forest, Haunted, and Icy independently only when a higher confirmed level is observed. Levels are cached per account/profile. `SafariBeltTooltip` reuses the item tooltip pipeline and reads bonus text instead of hard-coding potentially changing totals.
- **Expected effect:** four milestone rows plus received attribute bonuses embedded in the normal item tooltip.
- **Default/outbound:** on; no menu opening or click.

## 9. Combat

### Ender Dragon outline

- **Purpose:** make Ender Dragons easier to locate in The End.
- **Inputs:** loaded Ender Dragon entity and received scoreboard location classified as The End/Dragon's Nest.
- **Implementation:** `EntityRendererMixin` uses the vanilla glowing/outline pipeline and returns the configured RGB color.
- **Expected effect:** clean configurable dragon outline, not an altered model or hitbox.
- **Default/outbound:** on; local rendering only.

### Power Orb & SOS despawn alert

- **Purpose:** warn when the local player's temporary Power Orb or Flare has ended.
- **Inputs:** exact client-received Power Orb despawn chat lines; the exact `WARNING_FLARE`, `ALERT_FLARE`, or `SOS_FLARE` item ID used by the local player through either air-use or use-on-block; the exact Flare held in either hand; and the received successful Flare placement sound (`entity.firework_rocket.launch`, pitch 1, volume 3).
- **Implementation:** Power Orb formatting is stripped before an exact four-name full-line match. A Flare use creates only a two-second pending candidate; the matching placement sound confirms it and starts a three-minute monotonic timer. Every confirmed replacement overwrites the active Flare and expiry atomically, so the previous expiry cannot fire. If a use callback is missed, the placement sound can recover only from a recognised Flare currently held by the local player. World/server changes and disconnects clear state silently. Entity unload, distance, and buff range are not inputs. Duplicate presentation is suppressed.
- **Expected effect:** `<Deployable Name> Despawned!!!` appears once in large English center text when the Power Orb message is received or a confirmed Flare lifecycle reaches its end.
- **Default/outbound:** feature, Power Orb alerts, Flare alerts, center text, and sound on; volume 64%. No chat, command, packet, interaction, or network request.

### Century Cake effect expiry alert

- **Purpose:** retain the real expiry time of every Century Cake bonus through reconnects and offline periods, then present one unambiguous renewal reminder.
- **Inputs:** exact received first-activation `Yum! You gain <bonus> for 48 hours!` or refresh `Big Yum! You refresh <bonus> for 48 hours!` chat lines and the local wall clock. Formatting and private-use stat glyphs are normalized before catalog matching. The offline catalog contains exactly 20 verified cake/effect pairs and their item-head profiles.
- **Implementation:** the normalized bonus resolves to one catalog entry and writes an absolute `now + 48 hours` expiry under the active account/Profile. The once-per-second checker marks newly expired entries before presentation, merges entries found in the same pass, and never creates separate per-effect settings.
- **Expected effect:** one default-on master alert, a center title, an independently configured local sound at the shared 64% default, and a local chat component. `/cake` and `/centurycakeeffect` open a read-only effects-style screen with cake icons, bonus, rarity, and remaining time.
- **Default/outbound:** enabled. Nothing runs automatically. The underlined `Click Here For Cake Eating` component carries exactly `/visit northwestcloudy`; Minecraft executes it only after the player clicks the component.

## 10. Equipped Pet HUD

### Purpose

Show the equipped pet's identity and useful progression without opening the Pets menu.

### Information used

- Received summon, despawn, and Autopet chat.
- Received `Pet:` Tab widget as periodic source of truth.
- Already-open Pets menu and nearby rendered pet profile when they match.
- Bundled offline profile/skin/accessory metadata generated from the inspected NEU repository snapshot.

### Implementation

`PetTracker` maintains active pet identity, rarity, level, and experience. `PetSkinTracker` confirms a matching profile/skin/held item without reusing a complete unrelated ItemStack. `PetHeadResources` creates a normal player head and never attaches synthetic `petInfo`; exact and longest skin-family matches handle animated/dynamic frames. `PetLeveling` applies rarity-offset level-100 curves and the Golden/Jade/Rose Dragon level-200 curves. Confirmed skin, held item, and total XP are retained locally per pet.

### Expected presentation

A sharp 3D player-head icon, rarity-colored `[Lvl N] Pet Name`, current-level XP and percentage, optional XP to max, optional skin name, optional overflow level, and pet item as icon+name, icon-only, or name-only. Values use one decimal with `k/m/b/t`. No line is shortened with an ellipsis. At max level, only the redundant to-max line disappears; the held item remains.

### Defaults and outbound behavior

All information rows on; icon+name accessory mode; read/render only; no runtime texture or API download and no Firmament dependency.

## 11. Chat Peek

- **Purpose:** inspect chat history temporarily without opening Chat.
- **Inputs:** a configured held key/mouse chord and mouse wheel.
- **Implementation:** `ChatComponentMixin` renders focused-height chat when `ChatPeekManager.active()` is true. `MouseHandlerMixin` routes wheel input to chat or leaves it for the hotbar according to the selected mode.
- **Expected effect:** chat expands only while the chord is held; wheel controls chat by default.
- **Default/outbound:** feature on, chord unbound, scroll target Chat; no message is sent.

### 11.1 Party and chat command utilities

- **Inputs and bounded parsing:** `PartyText` removes Minecraft formatting before parsing. `PartyChatLine` accepts only received English Party Chat lines, extracts the sender, and accepts only a recognized `!` alias with normalized whitespace. `PrivatePartyRequestCommands` accepts only exact received English private-message bodies `!p`, `!party`, or `!invite`. Public chat, guild chat, unrecognized Party Chat text, and nonmatching private messages do not select an action.
- **Roster and completion:** `PartyRosterTracker` observes the client-visible party roster and resolves a player argument by exact case-insensitive name first, then a unique case-insensitive prefix. Ambiguous prefixes produce no command; a syntactically valid full player name remains usable when it is not in the observed roster. `!pt`/`!ptme` transfer leadership to the Party Chat sender; `//pt`/`//ptme` transfer it to the local player. The same resolver supplies command suggestions for aliases and player arguments.
- **Fast Party Commands:** this parent switch defaults off. Its independent children for Warp, All Invite, Transfer, Kick, Coordinates, Promote, Stream, Dungeon, and Kuudra default on. A child can independently accept the local player, other party members, or everyone as a Party Chat trigger. `!warp`/`!w` and `!allinvite`/`!all`/`!allinv` use shared action cooldowns of five seconds and two seconds respectively. All other recognized aliases have no added cooldown. The command is sent only after the parent, child, sender scope, parse, player resolution, and any cooldown permit it.
- **Party Commands:** the separate local `//` parent and all nine independent children default on. It shares the same parser and command mapping but has no sender scope because it is local input. Known malformed `//` commands are consumed with local feedback; unknown `//` commands are not intercepted and remain available to other client/server command handlers.
- **Private utilities:** Party Auto Accept remains a separately configured local friend/whitelist check. Private-message Party Request is off by default and sends `party invite <sender>` only for an exact allowed received keyword. Quick Private `!p` is off by default; local `//invited <player>`, `//invited by <player>`, and `//i <player>` send `msg <player> !p`.
- **Exact mapping:** Warp → `party warp`; All Invite → `party settings allinvite`; Transfer → `party transfer <player>`; Kick → `party kick <player>`; Coordinates → `pc x: <x>, y: <y>, z: <z>` using the local block position; Promote → `party promote <player>`; bare Stream → `stream`; Stream followed by any pure decimal `<n>` → `stream open <n>`; Stream followed by `c`, `close`, or `off` → `stream close`. `fe`/`f0` → `joininstance CATACOMBS_ENTRANCE`; `me`/`m0` → `joininstance MASTER_CATACOMBS_ENTRANCE`; `f1`–`f7` → `joininstance CATACOMBS_FLOOR_ONE` through `CATACOMBS_FLOOR_SEVEN`; `m1`–`m7` → `joininstance MASTER_CATACOMBS_FLOOR_ONE` through `MASTER_CATACOMBS_FLOOR_SEVEN`; `t1`–`t5` → `joininstance KUUDRA_NORMAL`, `KUUDRA_HOT`, `KUUDRA_BURNING`, `KUUDRA_FIERY`, and `KUUDRA_INFERNAL`.
- **Session boundaries:** message-command deduplication and private-request deduplication are short-lived local guards. Roster, deduplication, and cooldown state are reset when the client disconnects; neither stores chat history.

## 12. Inventory and menu tools

### 12.1 Item timestamps

- **Purpose:** show item creation time and supported completion countdowns.
- **Inputs:** item components/lore already present in the local ItemStack.
- **Implementation:** `ItemTimestampTooltip` formats received timestamps as local24-hour, local12-hour, ISO, or RFC text and appends tooltip rows.
- **Expected effect:** timestamp/countdown beneath the normal tooltip.
- **Default/outbound:** on; local tooltip only.

### 12.2 Cursor memory

- **Purpose:** return the pointer near its last useful position when reopening a compatible screen.
- **Inputs:** local screen identity, cursor coordinates, elapsed local time.
- **Implementation:** `CursorPositionSaver` records and restores within the configured tolerance; it does not synthesize a click.
- **Expected effect:** cursor returns to the saved position when the matching screen reopens soon enough.
- **Default/outbound:** on,500ms tolerance; local pointer positioning only.

### 12.3 AOTE/AOTV sound customization

- **Purpose:** replace, rather than indiscriminately mute, Instant Transmission and Etherwarp sounds.
- **Inputs:** held SkyBlock item ID, local sound event/resolved sound path, source coordinates, and selected sound settings.
- **Implementation:** `SoundEngineMixin` delegates only nearby matching sounds while an Aspect of the End/Void is held. `TeleportSoundCustomizer` preserves vanilla mode or plays one selected vanilla sound with independent volume/pitch, guarded against recursion.
- **Expected effect:** vanilla sound by default, or Chorus, Enderman, Amethyst, XP Orb, End Portal Fill, or Shulker sound at the chosen volume/pitch.
- **Default/outbound:** customization available but both modes remain vanilla; local sound only.

### 12.4 Attribute Shard Fusion Guide

- **Purpose:** provide a complete local answer for both reverse recipe lookup and forward uses lookup without guessing an order-sensitive Attribute Fusion pair.
- **Bundled inputs:** `assets/qcloudy_addition/data/shard_fusions.json`, generated offline from the current [Hypixel SkyBlock Wiki Attributes](https://hypixelskyblock.minecraft.wiki/w/Attributes) effect/acquisition tables and [Attribute Fusion rules](https://hypixelskyblock.minecraft.wiki/w/Attribute_Fusion), with identities cross-checked against [SkyShards](https://github.com/Campionnn/SkyShards), the [NotEnoughUpdates item repository](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO), and the [official Bazaar product list](https://api.hypixel.net/v2/skyblock/bazaar). The 320 local Shard PNGs are generated from SkyShards `public/shardIcons` at reviewed MIT commit `9688031dbc4e726168ffceb0f44884ff26e6e728`; the 321-source set is filtered through the catalog allow-list, excluding Rainbug.
- **Data calibration:** the runtime catalog is required to contain exactly 320 official Bazaar-listed Shards. Compared with the stale 317-item snapshot, Anteater, Zombuddy, Troodon, and Ghost Crab are present, Goldolot uses `R92`, and Rainbug is excluded because it is not in the official Bazaar Shard allow-list. The Wiki Attributes list is treated as supporting documentation rather than the cardinality authority because that page marks itself incomplete/outdated.
- **Implementation:** `ShardFusionCatalog` loads and validates the committed JSON once, including normalized rich-text effect spans, acquisition methods, mob types, and semantic colours. Its search index covers name/ID/attribute/effect/rarity/category/family/skill/mob type/acquisition; ordered-pair indexes serve both Recipes and Uses, so a Shard with natural sources (for example Queen Bee) still exposes every Fusion recipe. Special rules are checked symmetrically while remaining ID outputs retain first/second-input order. Chameleon follows numeric ID stepping and rarity rollover. `ShardItemResolver` keeps a session-wide native-ItemStack cache: a matching stack already received in an open menu/inventory overrides the bundled item model, while every unseen catalog entry resolves to its own offline Shard texture instead of amethyst. QCA performs no HTTP or texture request; an already-received player head continues through Minecraft's normal item renderer.
- **Recipe arithmetic:** Chameleon consumes `1`; Reptile, Amphibian, and Elemental consume `2`; all other Shards consume `5`. An ID/Chameleon result yields `1`, a special-rule result yields `2`, and Pure Reptile displays its received level's 2–20% double-output chance. Up to three selectable outputs are shown in their actual order and never equal either input.
- **Presentation:** `ShardFusionScreen` supplies Details/Recipes/Uses tabs, searchable result rows, Back/Forward history, page controls, item icons, input amounts, outputs, yields, and an explicit order note. Details presents exact effect and acquisition lines, explicitly labels Fusion-only Shards, and shows the verified Fusion-recipe count whenever nonzero. Epic is Minecraft `§5`; other rarity/stat/category/mob-type/acquisition text uses reviewed semantic colours. Hovering clickable Shard text darkens and underlines only the visible text. Clicking outside search, `Esc`, or `Tab` releases text focus; clicking search restores it. Input pairs and output candidates are measured as compact centered clusters, and hitboxes are derived from the same visible bounds. Text wraps or scales instead of using ellipses.
- **Entry points/default/outbound:** the feature is enabled by default. Its secondary setting contains **Open Guide** and an optional unbound keyboard/mouse chord. `/qshard [English query]` is a local client command that opens the same screen with the query prefilled. It sends no server command, chat, packet, menu input, Wiki request, Bazaar request, or other network traffic.

### 12.5 Shard Planner, price bridge, and Hunting Box warehouse

- **Purpose:** generate a bounded, cycle-safe multi-step route for a target Shard/quantity while preserving the original Guide as the exact direct-recipe reference.
- **Route engine:** `ShardFusionPlanner` performs depth-bounded dynamic relaxation over all catalog Shards and ordered output recipes. A route can terminate at a direct hunt rate, an optional Bazaar purchase, an already-observed warehouse quantity, or a further Fusion. The selected route is expanded into an immutable tree with candidate alternatives, craft count, estimated cost/time, and hunt/buy/inventory material maps. Expansion has cycle, depth, arithmetic, and node-count guards. Materials Only changes presentation, not the calculation.
- **Rates and Kraken:** `shard_rates.json` is a versioned offline baseline transformed from the reviewed SkyShards rate data and required to match all 320 catalog IDs. A locally saved player rate overrides the baseline. Hunter Fortune scales positive hunt rates. For Kraken, the planner can derive a rate from selected Kuudra tier, completion seconds, coins/hour opportunity cost, key cost, the documented tier multiplier, and 25 seconds of downtime. The local Crocodile level controls the 2–20% Pure Reptile expected multiplier; integer material totals remain conservative.
- **Prices:** `ShardPriceService` contains no HTTP client. At runtime it first checks whether Skyblocker is loaded, then reflectively resolves the public static `de.hysky.skyblocker.utils.ItemUtils.getItemPrice(String, boolean)` method. Only the returned values from Skyblocker's existing client cache are copied into an immutable local snapshot. Missing methods, linkage failure, missing entries, or malformed values safely become unavailable. There is no compile/runtime Skyblocker dependency. SkyHanni and Firmament are deliberately not inspected through private fields because neither currently publishes a stable cross-mod Bazaar API. Consequently Cheapest is disabled without a compatible provider, while non-price functions remain independent.
- **Warehouse:** once per second, `ShardWarehouseManager` checks only the currently displayed received container. The exact English title must match `Hunting Box` or `(current/total) Hunting Box`; each recognized Shard must expose a catalog ID/name and exact `Owned: N Shards` lore. Only that visible page is updated. A frame with zero recognized entries is ignored. Pages merge by Shard ID for the current local profile and are stored in `config/qcloudy_addition_shard_warehouse.json` using temporary-file replacement. QCA never sends `/hb`, requests another page, clicks a slot, or reads hidden inventory.
- **Screen and persistence:** `ShardPlanningScreen` provides Plan, Recipes, Shards, Fusion Lines, Warehouse, and Settings pages. Direct recipe filters have separate input/output values; Fusion graph nodes use local drag positions; modes, target, quantity, custom rates, graph positions, price-side preference, Kuudra parameters, and Materials Only persist through normal QCA config. The planner only renders information and cannot execute any plan step.

## 13. Persistence

- `config/qcloudy_addition.json`: language, feature settings, per-HUD appearance/position/scale, remembered pet details, Hunting resources/Chapter/Benefactor/Safari Belt state, collected Fairy Soul keys, and Shard Planner settings/rates/graph positions. The old `autumecloudyaddition.json` is read once for migration.
- `config/qcloudy_addition_shard_warehouse.json`: per-local-profile Shard counts from Hunting Box pages that the player actually opened, plus the last observation time. It contains no hidden inventory or server-fetched data.
- Configuration writes use a temporary file followed by atomic replacement when supported.

QCA stores no password, access token, Hypixel API key, chat history, remote account data, or reconnect address on disk.
Release-check state and a confirmed remote result are process-memory only; no update response, notification history, username, UUID, or server address is persisted.

## 14. Complete outbound-action inventory

| Trigger | Exact action | Automatic? |
|---|---|---|
| `/qca`, `/qc` | Opens the local QCA settings screen | No server payload |
| `/qshard [English query]` | Opens the local offline Shard Fusion Guide and pre-fills its search | No server payload |
| `/cake`, `/centurycakeeffect` | Opens the local Century Cake effects/timer screen | No server payload |
| Player types `/th` | `sendCommand("warp torrhus")` | No |
| Player types `/helia` | `sendCommand("chapter torrhus")` | No |
| Player clicks the underlined Century Cake renewal text | `sendCommand("visit northwestcloudy")` through Minecraft's `RUN_COMMAND` chat click event | No |
| Player clicks Reconnect | One normal Minecraft server connection to the remembered in-memory target | No |
| First world join in a Beta/Release build | After five seconds, at most one HTTPS `GET` in the client process to the fixed stable Release manifest; Alpha performs none | Yes; metadata check only, with no download or installation |
| Enabled Party Auto Accept receives a qualifying invite | `sendCommand("party accept <sender>")` | Yes, only after its local sender check |
| Enabled Private-message Party Request receives exact `!p`, `!party`, or `!invite` | `sendCommand("party invite <sender>")` | Yes, only after its exact message match |
| Enabled Fast Party Commands receives an allowed recognized Party Chat alias | `sendCommand` with the documented Party/Stream/`joininstance` payload | Yes, only after parent, child, sender-scope, parser, completion, and cooldown checks |
| Enabled Quick Private `!p` receives local `//invited …` or `//i …` input | `sendCommand("msg <player> !p")` | No; typed locally |
| Enabled Party Commands receives a recognized local `//` alias | `sendCommand` with the documented Party/Stream/`joininstance` payload | No; typed locally |

`sendChat` calls: none. The only generated message payload is `msg <player> !p`, through `sendCommand`, and only from the separately enabled Quick Private `!p` feature. Automatic movement, combat, capture, item use, block interaction, or reconnect: none.

## 15. Expected validation boundary

Automated tests validate parsers, defaults, routing, persistence normalization, boundary calculations, and archive structure. Local launches validate initialization with Fabric alone and with reviewed reference-mod combinations. They do not prove every future Hypixel wording, live entity layout, user resource pack, GUI scale, latency condition, provider update, or policy interpretation. Release `0.3.9` therefore keeps the unified settings and HUD editors explicitly experimental; authenticated Hypixel and real-modpack regression remains a separate validation boundary.

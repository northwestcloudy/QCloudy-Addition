# Changelog

All notable public changes to QCloudy_Addition are documented here.

## [0.3.10-alpha3] - 2026-09-04

Unpublished Alpha development build for Minecraft 26.1.2 only. Public Beta 0.3.10 remains the current testing release and stable Release 0.3.9 remains the update-check baseline.

### Changed

- Removed the generic QCA Player Profile Viewer in full: `//pv`, `/qpv`, its screen/models/caches/item-price tooltip client, and the corresponding `/v1/pv/*` and `/v1/market/tooltip-prices` backend routes and source code.
- Added an independent Dungeon Player Quick View. It triggers only from the exact Dungeon Finder newcomer message, analyzes that one joining player, and prints Catacombs, Secrets, five classes, queued-floor runs/fastest time, armor, selected weapons/pets, and Magical Power in a colored hover-first chat card.
- Class names and the manual kick action use native underlining. Item details use native item hover presentation, missing data is explicit, and the measured top/bottom separators share the same endpoints.
- The click action is the only kick path; no automatic kick, Party Finder listing scan, or class-conflict decision was added. A separate Dungeons toggle can disable the card without changing any existing party command.
- Moved the Shard Bazaar client types out of the removed profile package so the Shard Planner remains independent and functional.

### Scope

- Client-side request coalescing and a 60-second successful-result cache reduce repeat delay. The QCloudy API performs one bounded quick-view response and uses short shared source caches with stale fallback only for technical failures.
- `0.3.10-alpha3` is an Alpha source/build target only; it is not a GitHub Release, Modrinth version, or stable update target.

## [0.3.10-alpha2] - 2026-09-02

Unpublished Alpha development build for Minecraft 26.1.2 only. Public Beta 0.3.10 remains the current testing release and stable Release 0.3.9 remains the update-check baseline.

### Added

- Replaced the provisional key/value Profile Viewer with a visual QCA interface: a persistent identity/profile bar, icon-led section navigation, overview metric cards, skill progress, semantic statistic groups, and real slot-based item grids. Raw UUIDs, profile IDs, epoch values, `extraAttributes`, and generic JSON trees are no longer the normal presentation.
- Added PV-only item price tooltips backed by one bounded batch endpoint. Bazaar items show optional NPC Sell Price, BZ Sell Price, and BZ Buy Price; Auction House items show optional NPC Sell Price, clean Low. BIN Price, continuous 3 Day Avg. Price, and actual-variant Item NW Value.
- Added quantity-aware tooltip pricing. Stacks show the total first and the per-item value in `(… each)`; single items omit the parenthetical. Labels and values are rendered as measured columns rather than space-padded proportional text.
- Added a persistent clean-LBIN history table. The three-day price is the time-weighted clean Lowest BIN over a continuously observed 72-hour window, never a completed-auction median; it remains unavailable until the first complete window has been collected.

### Corrected and hardened

- Clean Low BIN now matches only the unmodified base-item variant and cannot borrow a cheaper upgraded, enchanted, reforged, skinned, gemmed, or otherwise modified listing. Item NW Value separately prices the held item's explicit variant and never substitutes clean LBIN.
- Bazaar wording follows the player's action: BZ Sell is coins received by selling immediately and BZ Buy is coins paid to buy immediately. Missing or unsafe values stay absent instead of displaying zero.
- Profile sections now receive independent bounded projection budgets, so large early sections cannot consume the complete response and silently replace Skills, Slayer, Collections, or later sections with `<node-limit>`. Truncated sections carry a local warning and make the snapshot partial.
- Reduced Overview and Misc to display-relevant SkyBlock fields, removed unrelated raw Hypixel-player dumps, and restored modern Minion, Glacite, and Trophy Fish fields to their intended sections.
- Tooltip requests are deduplicated, cached for a bounded period, cancellable when the screen closes or selection changes, strictly parsed, and confined to the QCA Profile Viewer rather than injected into every Minecraft tooltip.

### Scope

- Dungeon profile inspection remains intentionally deferred.
- `0.3.10-alpha2` is an Alpha artifact for Minecraft 26.1.2 only. It is not a GitHub/Modrinth publication target and is ignored by the stable-Release update checker.

## [0.3.10-alpha1] - 2026-09-01

Unpublished Alpha development build for Minecraft 26.1.2 only. Public Beta 0.3.10 remains the current testing release and stable Release 0.3.9 remains the update-check baseline.

### Added

- Added the read-only QCA Player Profile Viewer, opened with `//pv [player or UUID]` or `/qpv [player or UUID]`; omitting the target uses the local player. QCA deliberately does not register ordinary `/pv`.
- Added profile switching and bounded non-Dungeon sections for overview, gear, accessories, pets, inventories/storage, skills, Slayer, mining, minions, bestiary, collections, Crimson Isle, Rift, miscellaneous/farming, Museum, Garden, and Market/Net Worth.
- Added the deployable `api.qcloudy.net` backend for cached, transformed Hypixel profile and market data. The API key remains server-side; Museum is scoped to the requested member and raw item Base64/NBT is never sent to the mod.
- Added independent Bazaar, atomic active-AH, and deduplicated completed-sale collectors. Unknown prices remain unknown instead of becoming zero, and incomplete estimates are labelled as lower bounds.

### Changed

- Shard Planner prices now use QCloudy's bounded Bazaar snapshot instead of another mod's private client cache. Opening the planner performs one bounded asynchronous snapshot request; ordinary inventory actions still do not trigger networking.
- Added cached layout, visible-row-only drawing, cancellable HTTP requests, draggable side/content scrollbars, and lazy Museum/Garden requests to the profile screen.
- Added server-side authenticated-request budgets and 429 backoff, source-completeness checks, per-unit AH stack pricing, derived net-worth caching, and privacy-preserving deployment defaults with access logs disabled.
- Market publication now rejects regressed AH snapshots and duplicate ended generations, records collection gaps, batches sale statistics into fixed set-based queries, and fails closed for sparse, ambiguous, incomplete, or low-confidence prices. A complete estimate requires continuous seven-day ended-auction coverage plus complete Bazaar, AH, purse, bank, holdings, decoding, and high-confidence pricing inputs.
- Bounded caches now use the agreed freshness windows: player-name UUID mappings 72 hours, complete SkyBlock profiles 1 hour, Museum 6 hours, Garden 12 hours, Bazaar 60 seconds, active AH 120 seconds, and ended-auction polling 30 seconds.

### Scope

- Dungeon party profile inspection is intentionally not included in this build.
- `0.3.10-alpha1` is not a GitHub/Modrinth publication target and is ignored by the stable-Release update checker.

## [0.3.10-beta] - 2026-08-31

Public Beta for Minecraft 26.1.2 and 26.2. This entry contains every player-facing change completed since stable Release 0.3.9.

### Added

- Added exact death-save detection for Spirit Mask, Bonzo's Mask, and Phoenix, with an optional centre-screen alert and three independently positioned cooldown HUDs. The master alert and all three HUDs default to off. Spirit Mask shows up to 30 seconds, Bonzo's Mask up to 360 seconds, and Phoenix Rekindle an exact 60-second cooldown.
- Added opt-in Party Auto Accept for normal friends, special friends, and a separate 16-player whitelist. Friend data is learned from the player's explicitly opened `/friend list` pages.
- Added opt-in private-message party requests for exact `!p`, `!party`, and `!invite` messages, plus opt-in local `//invited`, `//invited by`, and `//i` helpers.
- Added opt-in Fast Party Commands for recognised `!` aliases received specifically in Party Chat. Public and Guild Chat cannot trigger them. The parent switch defaults off, all nine child groups default on, and each group can allow only self, only other party members, or everyone.
- Added default-on local double-slash Party Commands covering Warp, All Invite, transfer, kick, coordinates, promote, Stream, Dungeons, and Kuudra. Unknown `//` input is not intercepted.
- Added command and player-name completion. Full valid names and unique observed party-member prefixes are accepted; ambiguous or invalid prefixes fail without sending a command. Party Chat `!pt`/`!ptme` transfers to the sender, while local `//pt`/`//ptme` transfers to the local player.
- Added five-second Warp and two-second All Invite cooldowns. Stream open accepts any decimal player limit, while `c`, `close`, and `off` close the stream.
- Added an always-on, stable-Release-only update notice. Beta and Release builds check at most once per client process after the first world join. Only a newer, Minecraft-compatible, strictly validated Release can produce one toast and one local clickable message; Alpha builds make no request, and QCA never downloads or installs a JAR.
- Added standard homepage, source, and issue metadata for launchers/HMCL, plus Website, Downloads, and Source Code links for Mod Menu.

### Fixed and improved

- Fixed the upgraded Bonzo's Mask message `Your  Bonzo's Mask saved your life!` not starting the Bonzo cooldown. Standard and upgraded masks now share one cooldown and duplicate guard.
- Fixed multi-page `/friend list` synchronisation so recognised friends can actually trigger Party Auto Accept. Valid friends on the current page become available immediately; stale entries are removed only after every page is read in order, and public, Guild, or Party Chat lookalikes are rejected.
- Fixed the General settings catalog sometimes being unable to scroll to the bottom.
- Supported Mods now starts collapsed like every other subgroup.
- Added browser-style draggable scrollbars to the feature catalog, feature settings, compatibility report, and party whitelist, including track paging, wider hit areas, and continued dragging outside the track.
- Features without a real HUD no longer expose misleading HUD appearance controls, and cards without editable settings no longer open empty settings pages.
- Removed the obsolete “yield duplicate inventory features to Firmament” setting. Item timestamps and cursor memory now follow their own QCA switches even when Firmament is installed.
- Removed obsolete empty groups, unused translations, dead configuration fields, and the retired inventory feature gate.
- Chat remains a subgroup under General rather than a separate left-side category.

### Compatibility and build

- Beta 0.3.10 remains a standalone, client-only Fabric mod. It builds separately for Minecraft 26.1.2 and 26.2, requires Java 25 and Fabric Loader 0.19.3+, and requires the matching Fabric API.
- The stable update baseline remains Release 0.3.9 sequence 1. Publishing this Beta does not replace or increment the stable Release manifest.
- Unified Settings Editor and Unified HUD Editor remain experimental, default-off concept tests. Back up provider configuration and verify writes in provider-native editors.

### Beta artifacts

- `QCloudy_Addition-0.3.10+26.1.2-Beta.jar`
- `QCloudy_Addition-0.3.10+26.1.2-Beta-sources.jar`
- `QCloudy_Addition-0.3.10+26.2-Beta.jar`
- `QCloudy_Addition-0.3.10+26.2-Beta-sources.jar`

## [0.3.9] - 2026-08-25

Stable Release for Minecraft 26.1.2 and 26.2. This entry consolidates every public change completed after the previous stable Release 2.5.3; the individual Alpha and Beta entries remain below as the detailed development history.

### Added since Release 2.5.3

- Added a completely offline Attribute Shard guide for the exact 320 Bazaar-listed Shards, with Shard-specific icons, semantic rarity/category/family/Skill colours, Details, Recipes, Uses, ordered inputs, quantities, alternate outputs, acquisition methods, natural-plus-Fusion sources, and clear clickable navigation.
- Added the local Shard Planner: target quantities, complete multi-step Fusion Trees, alternate routes, Materials Only, Fastest/Cheapest and Ironman modes, Kraken/Kuudra parameters, editable Shards-per-hour rates, direct input/output recipe relations, draggable Fusion Lines, and a Hunting Box warehouse populated only after the player opens `/hb`.
- Added optional price-aware routes through safely recognised cached data exposed by compatible installed providers. QCA never performs its own Bazaar HTTP request; price modes stay unavailable when no safe provider is present.
- Added the configurable Fishing Bite Sound with the bundled Ciallo cue, water and lava detection, one playback per hook, a dedicated Fishing category, default-off enable state, and an independent volume control.
- Added function-first unified settings and HUD management for recognised capabilities from installed SkyHanni, Skyblocker, Firmament, BabyZombieAddons, and Feesh builds. Settings and HUDs have separate default-off master switches, confirmation dialogs, progress views, Refresh actions, capability-based version-drift handling, and a read-only Compatibility Gaps report.
- **Experimental warning:** the Unified Settings Editor and Unified HUD Editor are concept tests, remain disabled by default, and are not yet stable. Provider updates may invalidate recognised mappings; use them cautiously, keep backups, and verify writes in the provider-native editor.
- Added Power Orb and Flare despawn alerts. Four player-owned Power Orbs use exact received despawn messages; Warning, Alert, and SOS Flares use a confirmed three-minute local lifecycle with replacement timing, center text, optional sound, and a 64% default volume.
- Added unified Century Cake tracking for all 20 cakes, real-world 48-hour timers, `/cake` and `/centurycakeeffect`, cake-head tooltips, one master alert switch, merged expiry notices, and an underlined renewal action that sends `/visit northwestcloudy` only after a direct click.
- Added separate Minecraft 26.1.2 and 26.2 builds, bilingual Wiki material, release validation, and a version-aware official website/download page.

### Changed and improved

- Replaced and recalibrated the Dwarven Mines map. Its marker now uses one continuous approximate X/Z projection, updates live, ignores Y and scoreboard sub-location snapping, and remains visible on bridges and above The Mist.
- Reworked the Shard guide and planner layouts for wide, narrow, and resized screens. Recipe inputs/outputs now form compact content-width groups, click targets follow visible content, and search focus can be released without closing the screen.
- Corrected Epic to Minecraft dark purple (`§5`) and aligned Shard rarity, effect, family, mob-type, Skill, acquisition, and navigation colours with their corresponding game/Wiki semantics.
- Made empty HUD panels and empty settings categories disappear instead of leaving blank frames. Fishing now uses the specific **Bite Alerts** subgroup instead of a redundant Fishing → Fishing hierarchy.
- Made optional provider discovery on-demand, immutable-snapshot based, and capability driven instead of blocking every unlisted provider version. Changed or unknown branches fail closed while safely recognised branches remain usable.
- Corrected unmaxed Golden/Jade Dragon level and XP presentation in the Equipped Pet HUD.

### Fixed

- Fixed replacing an active SOS/Flare without resetting the old three-minute timer. A recognised replacement now restarts the complete lifecycle even when a confirmation callback is missed; unrelated held items cannot reset it.
- Fixed water/lava fishing inconsistencies and the duplicate Ciallo playback caused by reeling the same hook.
- Fixed personal Tree Gift creature alerts failing to trigger while still preventing another player's public event from arming the local alert.
- Fixed The Park's `Jungle Island` being mistaken for Crystal Hollows `Jungle`, which displayed the mining HUD on the wrong island.
- Fixed first-use and refresh parsing for Century Cakes, including Starborn Century Cake's private-use stat glyph and canonical `Hunting Fortune` label.
- Fixed Dwarven marker jumps, disappearance and desynchronisation, Golden Dragon falsely showing level 200, and multiple settings/Shard UI overlap, clipping, focus and narrow-window issues.

### Removed or replaced

- Removed slot locking, Storage Overlay, and menu middle-click conversion from implementation, configuration, tests, and current documentation.
- Removed the local `/aca` and `/ca` settings aliases. `/qca` and `/qc` remain available.
- Removed the old Dwarven per-region clamp, Y-layer selection, and scoreboard sub-location snapping; the continuous X/Z-only projection replaces them.
- Removed the incomplete Flare chat assumption and all distance/range/entity-unload expiry guesses; confirmed local lifecycle expiry replaces them.
- Removed exact provider-version whitelisting as the primary compatibility gate; per-capability validation now determines what can be exposed.

### Compatibility and safety

- Release 0.3.9 remains a standalone, client-only Fabric mod. Optional provider mods are not startup dependencies, and unsupported capabilities are omitted.
- Requires Java 25 and Fabric Loader 0.19.3 or newer. Minecraft 26.1.2 uses Fabric API 0.155.2+26.1.2; Minecraft 26.2 uses Fabric API 0.154.2+26.2.
- QCA does not automate movement, clicks, fishing, Fusion, cake use, deployables, reconnection, or gameplay. Local screen commands send nothing. The documented `/th`, `/helia`, and clickable Century Cake renewal actions require direct player input.

### Release artifacts

- `QCloudy_Addition-0.3.9+26.1.2-Release.jar`
- `QCloudy_Addition-0.3.9+26.1.2-Release-sources.jar`
- `QCloudy_Addition-0.3.9+26.2-Release.jar`
- `QCloudy_Addition-0.3.9+26.2-Release-sources.jar`

## [0.2.9-alpha.30] - 2026-08-21

### Fixed

- Replacing an active Warning, Alert, or SOS Flare now restarts its complete three-minute lifecycle as soon as the recognised replacement use is observed. The previous placement's expiry can no longer produce an early alert, even if the replacement does not repeat the first-placement confirmation signal.
- Flare placement tracking now also observes use-on-block interactions and can recover when the use callback is missed by pairing the exact successful-placement sound with the Flare held by the local player.
- Added regression coverage for same-SOS replacement without a second confirmation signal, a missed second use callback, and unrelated held items that must not reset the timer.

### Build

- Adopted the new artifact convention: `QCloudy_Addition-0.2.9+26.1.2-Alpha-30.jar`.
- Alpha uses a separate iteration suffix; Beta and Release use only their channel suffix.
- Alpha 30 is built only for Minecraft 26.1.2.

## [2.9.29] - 2026-08-19

### Fixed

- Made the two independent compatible-mod controls immediately visible under **General → Supported Mods**: **Manage Other Mod Settings** controls recognised feature settings, while **Manage Other Mod HUDs** controls recognised HUD positions.
- The Supported Mods group now opens by default, preventing the separate settings-management switch from being mistaken for the sidebar's QCA-only **Edit HUD** button.
- Added regression coverage requiring both master switches to remain registered. Both controls still default to off, keep separate scan/Refresh flows, and do not turn each other on.

### Build

- Beta 2.9.29 builds for Minecraft 26.1.2 and 26.2.

## [2.9.28] - 2026-08-18

Beta consolidation release for Minecraft 26.1.2 and 26.2. It includes the Alpha 2.8.18–2.8.28 work completed since Beta 2.8.17.

### Added

- Added optional function-first integration for installed SkyHanni, Skyblocker, Firmament, BabyZombieAddons, and Feesh builds. Unified settings and HUD discovery have separate master switches, progress screens, explicit confirmation, Refresh actions, and a read-only compatibility-gaps report.
- Added **Power Orb & SOS Despawn Alert**. Player-owned Power Orbs use exact received despawn chat lines, while Warning, Alert, and SOS Flares use a confirmed local placement lifecycle. Alerts never depend on distance, buff range, or entity unloading.
- Added the unified **Century Cake Effect Expiry Alert** for all 20 Century Cakes, `/cake` and `/centurycakeeffect`, real-world 48-hour timers, cake-head effects UI, merged expiry notices, and the click-only `/visit northwestcloudy` renewal link.

### Improved

- Provider scanning is on demand, local, capability based, and resilient to provider version drift. Installed providers expose only safely recognised branches; missing or changed branches fail closed.
- Settings categories now appear only when they contain a QCA or discovered-provider function. Fishing uses the specific **Bite Alerts** subgroup instead of repeating the top-level category name.
- Century Cake parsing now supports the exact first-activation and refresh message forms, including Hypixel private-use stat glyphs.

### Fixed

- Fixed The Park's `Jungle Island` being mistaken for Crystal Hollows `Jungle`, which could display the Mining Tasks & Powders HUD on the wrong island.
- Fixed newly activated Century Cakes remaining grey, and corrected Starborn Century Cake to Hypixel's canonical `Hunting Fortune` spelling.
- Replaced the old incomplete Flare chat assumption. Failed placement, cooldown use, server changes, distance, effect range, and entity unloading no longer create false despawn alerts.

### Compatibility and safety

- Beta 2.9.28 builds for Minecraft 26.1.2 and 26.2. QCA remains a standalone client-only Fabric mod; optional provider integrations are not required and unsupported branches are omitted.
- `/visit northwestcloudy` is sent only when the player clicks the underlined Century Cake renewal text. No cake, deployable, provider, or scan action sends a command automatically.

## [2.8.28] - 2026-08-18

### Fixed

- Fixed Starborn Century Cake refreshes still remaining grey after the exact client line `Big Yum! You refresh +1<stat icon> Hunting Fortune for 48 hours!`.
- Corrected the bundled Starborn effect and bonus from the non-canonical `Hunter Fortune` label to Hypixel's actual `Hunting Fortune` name. The private-use stat icon is still normalized before matching.
- Added regression coverage for the exact formatted and unformatted refresh lines. The previous `Hunter Fortune` spelling now fails closed instead of silently becoming a second alias.

### Build

- Alpha 2.8.28 is built only for Minecraft 26.1.2.

## [2.8.27] - 2026-08-17

### Fixed

- Fixed newly activated Century Cake effects, including **Starborn Century Cake / +1 Hunter Fortune**, remaining grey and showing `Not active` after the player ate the cake.
- The tracker now accepts Hypixel's exact first-activation line, `Yum! You gain ... for 48 hours!`, including the private-use stat icon embedded in Hunter Fortune, while retaining the exact `Big Yum! You refresh ... for 48 hours!` refresh path.
- Removed the incorrect legacy assumption that a first activation could be written as `Big Yum! You gain ...`. Only the two real message forms can create or refresh a 48-hour timer.

### Build

- Alpha 2.8.27 is built only for Minecraft 26.1.2.

## [2.8.26] - 2026-08-17

### Fixed

- Renamed the Combat setting to the explicit **Power Orb & SOS Despawn Alert** and replaced the incorrect Flare-chat assumption completely.
- Power Orbs now use only the four exact player-owned `Your <Power Orb> despawned.` chat lines. Warning, Alert, and SOS Flares are tracked separately: an exact Flare item use creates a short candidate and the matching successful placement sound confirms a three-minute local lifecycle.
- A failed/cooldown-blocked Flare use cannot start a false timer. A confirmed new Flare silently replaces the old record; world/server changes clear it silently; entity unload, distance, and buff range never trigger or suppress the alert.
- Added separate Power Orb, Flare, center-text, sound, and 0–100% volume controls. The feature and sound default on, with volume at 64%; one lifecycle end produces at most one alert.

### Build

- Alpha 2.8.26 is built only for Minecraft 26.1.2.

## [2.8.25] - 2026-08-17

### Added

- Added **Century Cake Effect Expiry Alert** under **Items & Menus → Century Cakes**. One master switch, enabled by default, controls expiry warnings for all 20 Century Cake effects; there are no per-effect switches.
- Century Cake refresh lines received by the client now start a 48-hour real-world timer that continues while the player is offline. `/cake` and `/centurycakeeffect` open a read-only effects-style screen with the actual cake heads, bonuses, rarity, and remaining time.
- A single expiry shows a large center title and the local chat line `[QC] Century Cake <Effect> Expired! Click Here For Cake Eating`. Effects that expire together are merged into one message such as `[QC] 8 Century Cake Effect Expired! Click Here For Cake Eating`.
- The underlined `Click Here For Cake Eating` text runs exactly `/visit northwestcloudy` only after the player clicks it. The feature has its own local sound, enabled by default at 64%.

### Safety and build

- Cake tracking reads only received chat and stores local absolute expiry timestamps. It does not query Hypixel, automate cake use, or send a command automatically.
- Alpha 2.8.25 is built only for Minecraft 26.1.2.

## [2.8.24] - 2026-08-16

### Added

- Added the first **Deployable Expiry Alert** under **Combat → Deployables**. Its original Flare-chat detection was incomplete and has been fully replaced by the confirmed lifecycle implementation in 2.8.26.
- Added an independent alert sound toggle and 0–100% volume slider for this feature. The sound is enabled by default at 64%.

### Safety and build

- The feature sends no chat, command, packet, interaction, or network request.
- Alpha 2.8.24 is built only for Minecraft 26.1.2.

## [2.8.23] - 2026-08-16

### Fixed

- Fixed The Park's exact `Jungle Island` location being mistaken for the Crystal Hollows `Jungle` subarea, which could show the Mining Tasks & Powders HUD outside a mining island.
- Empty top-level settings categories are now omitted completely. Categories such as Dungeons appear only when QCA or an installed compatible provider contributes at least one readable feature.
- Renamed the redundant Fishing → Fishing subgroup to the specific **Bite Alerts** group.

### Build

- Alpha 2.8.23 is published only for Minecraft 26.1.2, following the Alpha-channel target policy.

## [2.8.22] - 2026-08-15

### Added

- Added a second confirmation screen before every optional provider capability scan. The dialog identifies whether the scan is for the Unified Settings Editor or Unified HUD Editor and explains that the work is local, read-only, and may briefly use additional CPU.
- The confirmation is required both when first enabling an editor without a valid session snapshot and whenever the player presses that editor's Refresh action.

### Changed

- Cancelling the initial confirmation leaves the selected master switch disabled and starts no scan. Cancelling Refresh keeps the existing validated snapshot unchanged.
- Restoring an enabled master switch after restarting Minecraft no longer silently scans providers. A scan starts only after the player explicitly confirms it; Refresh is disabled while another scan is already running.
- Alpha 2.8.22 is built separately for Minecraft 26.1.2 and 26.2.

### Safety

- The confirmation changes only local UI and scan scheduling. Provider discovery remains deterministic and read-only, with no server request, HTTP request, packet, command, chat, telemetry, automatic input, or direct provider-config file edit.

## [2.8.21] - 2026-08-15

### Added

- Added **Feesh** as the fifth optional provider in QCA's independent Unified Settings Editor, Unified HUD Editor, visual scan progress, and Compatibility Gaps report.
- Added capability-based Feesh setting discovery for live Kotlin delegated properties. QCA exposes only safely paired public getters/setters, classifies only still-uncategorised functions, and saves successful changes through Feesh's own `Settings.save()` path.
- Added Feesh HUD discovery from its live overlay registry. Only enabled, currently drawable overlays with non-empty lines enter Edit HUD; position, scale, and alignment are converted through Feesh's native anchor model and persisted through its own coordinate store.

### Compatibility and safety

- Feesh support has no exact-version whitelist and no compile/runtime dependency. Compatible capabilities survive ordinary provider updates; changed or opaque settings/HUDs are omitted from the editors and reported as per-provider compatibility gaps without hiding valid siblings.
- QCA does not invoke Feesh API, chat, command, sharing, or gameplay features. Changing a Feesh-owned option uses Feesh's normal setter and save contract, so any later behavior remains owned by the setting the player explicitly selected in Feesh.
- The unified-editor master switches remain independent and disabled by default. No provider scan runs until one is enabled or the player presses Refresh.
- Alpha 2.8.21 is built separately for Minecraft 26.1.2 and 26.2.

## [2.8.20] - 2026-08-15

### Added

- Added an on-demand capability scan for the independent **Unified Settings Editor** and **Unified HUD Editor** master switches. Enabling either switch opens a live scan page with the current provider, phase, item, recent activity, and a progress bar.
- Added a **Refresh** action to each unified editor page. Refresh preserves the last valid snapshot until the new read-only scan completes and validates, preventing a partial provider state from replacing working results.
- Added a small deterministic local metadata classifier for provider functions that remain uncategorised after native paths and verified rules. It uses fixed local weights and a confidence threshold; low-confidence entries remain unclassified instead of being guessed.

### Changed

- Settings and HUD discovery now share one immutable session snapshot while keeping their switches, totals, and editor results independent. The settings page reports only manageable settings; the HUD page reports only manageable HUDs.
- Scans run only when a master switch is enabled or the player presses Refresh. Opening the settings menu no longer performs an unconditional provider rescan, and disabling both switches cancels pending work and releases the session snapshot.
- Scan results list only installed providers that produced readable capabilities. Uninstalled providers are not shown. Recognition remains best-effort and per capability, so one changed provider branch cannot hide every compatible branch.
- Provider work is staged across client ticks and progress text states exactly which installed provider and phase is being processed.

### Safety

- Discovery and classification are local, read-only, and deterministic. This update adds no cloud AI, network request, server query, packet, command, chat, telemetry, automatic input, or direct configuration-file editing.

## [2.8.19] - 2026-08-15

### Improved

- Cached the provider-grouped Compatibility Gaps layout and wrapped report rows. Opening the report still performs a fresh read-only capability audit, while normal rendering no longer rebuilds the same grouping and text layout every frame.
- Reduced Lasso `REEL` detection to one loaded-entity traversal per active check and deferred ArmorStand name parsing until a local player's lasso target actually exists.
- Removed the unused duplicate all-recipes reference list from the Shard Fusion index. Exact-pair, output, and input indexes continue to share the same immutable recipe objects and results.
- Captured Fabric Loader metadata during Gradle configuration so resource processing no longer uses the deprecated execution-time `Task.project` API. Clean builds now complete without the Gradle 10 deprecation warning previously emitted by the project script.
- Explicitly exclude Finder `.DS_Store` metadata from both playable and Sources archives.

### Compatibility and safety

- No feature defaults, recipe results, provider save behavior, HUD semantics, command payloads, or network behavior changed. QCA remains standalone and client-only.
- Alpha 2.8.19 is built and validated separately for Minecraft 26.1.2 and 26.2.

## [2.8.18] - 2026-08-15

### Added

- Added a read-only **Compatibility Gaps** card under **General → Supported Mods**. It is visually distinct from feature cards, has no toggle or enabled strip, and opens with either mouse button.
- Added a provider-grouped report for installed SkyHanni, Skyblocker, Firmament, and BabyZombieAddons builds. Each confidently recognised but unmanaged function is labelled with `[Settings]`, `[HUD Editor]`, or both; fully supported functions are omitted.

### Improved

- The report performs a fresh, read-only capability audit when opened, independent of the two default-off unified-editor master switches. A provider with an unreadable or empty recognised configuration root is no longer incorrectly described as fully supported.
- Recognised complex settings that QCA cannot safely write, including changed color, keybind, or position structures, remain hidden from the normal editor but are now visible in the compatibility report. Unknown future structures are not assigned invented feature names.

### Channel and safety

- Returned development builds to the **Alpha** channel as requested. This version is `Alpha 2.8.18` for Minecraft 26.1.2 and 26.2.
- The report reads only installed client-mod runtime structures. It never changes another mod's values, edits configuration files, contacts a server, or sends packets, commands, chat, HTTP requests, or telemetry.

## [2.8.17] - 2026-08-15

### Improved

- Added separate **Unified Settings Editor** and **Unified HUD Editor** master switches under General. Both are disabled by default, can be enabled independently, and do not affect QCA-owned settings or HUDs.
- Replaced the exact-version whitelist for SkyHanni, Skyblocker, Firmament, and BabyZombieAddons with capability discovery. An installed provider can continue exposing recognised settings and HUD positions after a version update when its live configuration and save contracts remain compatible.
- Added per-field defensive discovery for provider configuration trees. Recognised writable toggles, enums, bounded numeric settings, and known HUD position structures are shown; new or changed structures that QCA cannot safely edit are omitted without hiding the rest of the provider or preventing QCA from opening.
- Added prefixed toggle/HUD-coordinate recognition so layouts such as `enabledCommissions` with `commissionsX`, `commissionsY`, and `commissionsScale` remain editable without a version-specific field list.
- When the relevant master is enabled, re-probes provider capabilities whenever the unified settings screen is opened, preventing an early partial scan from remaining cached after another mod finishes initialising.

### Removed

- Removed the local `/aca` and `/ca` settings aliases. `/qca` and `/qc` remain available when their client-command names are free.

### Compatibility boundary

- This is best-effort structural compatibility, not a claim that unknown future provider code is automatically understood. A recognised live field is exposed only when QCA can read it, safely write its supported value type, and use the provider's own save path. Unsupported new functions remain in their original mod and are simply absent from QCA until an adapter is added.
- No provider configuration files are edited directly, and this change adds no server packet, command, chat, HTTP request, gameplay automation, or provider runtime dependency.

## [2.7.17] - 2026-08-14

Beta consolidation release for Minecraft 26.1.2 and 26.2, covering the completed work since Beta 2.6.6.

### Added

- Added the opt-in Fishing Bite Sound with its own 0–100% volume control at the shared 64% default. Directly owned water hooks and Hypixel's bounded ownerless lava-hook presentation are supported, and the cue plays only once for each confirmed bite.
- Added the local 320-Shard Planner alongside the existing Fusion Guide: multi-step Fusion trees, alternative direct routes, Materials Only totals, editable acquisition rates, Fastest/Cheapest routing, Ironman mode, Kraken/Kuudra parameters, draggable Fusion Lines, and a profile-scoped Hunting Box warehouse recorded only from pages the player opens.
- Added optional Bazaar-price routing through a compatible Skyblocker public cached-price API. QCA performs no price HTTP request and all price routes visibly stay unavailable when no reviewed provider is installed.
- Added the first function-first unified settings/HUD layer for the reviewed SkyHanni 7.41.0, Skyblocker 6.8.2, Firmament 44.3.0, and BabyZombieAddons 3.4.1 builds. Exact equivalent features can select one provider, safe native values persist through that provider, and unsupported versions fail closed.
- Added a maintained build matrix and one-command build script for separate Minecraft 26.1.2 and 26.2 playable and Sources artifacts.

### Improved

- Promoted Fishing to its own top-level category and made category spacing responsive on short screens.
- Reworked the Shard Planner, Shard details, Settings, Fusion Lines, RGB picker, feature pages, and HUD editor for narrow windows, long bilingual text, independent scrolling, clipping, and hitboxes that follow visible controls.
- Replaced the Dwarven Mines background and changed its live arrow to one continuous approximate X/Z-only projection across the complete single-layer overview. Y and scoreboard sub-locations do not influence the marker.

### Fixed

- Fixed missing Fishing Bite Sound support for the bounded Hypixel lava-hook presentation and prevented the cue from replaying when the player reels in.
- Fixed owned Tree Gift creature alerts such as `A wild Groundhog appeared!` being discarded, while preserving the rejection of an unrelated nearby player's public line.
- Fixed an unmaxed Ancient Golden Dragon being shown as `[Lvl 200]`; overflow levels now require received exact experience at or beyond the real maximum.
- Fixed overlapping Shard detail/rate controls, Planner controls, Fusion Line nodes, settings fields, RGB controls, and HUD-editor rows at supported compact layouts; clipped rows no longer keep invisible hitboxes.
- Fixed Dwarven marker jumps or disappearance on bridges above The Mist and other vertically overlapping paths, added `C&C Minecarts Co.` recognition, and kept out-of-range arrows safely inside the overview.

### Replaced

- Removed the old Dwarven scoreboard sub-location snapping and per-region marker clamping. No user-facing feature from Beta 2.6.6 was removed; the old positioning method was replaced by the continuous real-time X/Z projection.

### Compatibility and safety

- QCA remains a standalone, client-only Fabric mod. It adds no automatic click, Fusion, fishing action, movement, combat, capture, packet, chat, command, HTTP request, telemetry, or hidden server-data request.
- The QCA-owned feature set is built for both Minecraft 26.1.2 and 26.2. Exact-version third-party settings/HUD adapters remain reviewed for 26.1.2 only and deliberately fail closed on 26.2 until matching provider builds are audited.
- `/th` and `/helia` remain the only documented user-triggered server-command shortcuts; neither runs without direct player input.

## [2.6.17] - 2026-08-13

Alpha Dwarven Mines overview synchronization correction for Minecraft 26.1.2 and 26.2.

### Fixed

- Removed scoreboard sub-location selection and per-region clamping from the Dwarven Mines marker. The arrow now uses one continuous approximate X/Z transform across the complete single-layer background, so bridges above The Mist and other vertically overlapping paths no longer jump into another named area.
- Added the official `C&C Minecarts Co.` Dwarven sub-location to island recognition so entering that area cannot unload the map.
- Dwarven map coordinates now display X/Z only. Y is absent from the projection API and cannot influence the marker directly or indirectly.
- Kept the live marker safely inside the background at out-of-range coordinates and added regression coverage for continuous one-axis movement, The Mist bridge overlap, representative regions, and clamping.

## [2.6.16] - 2026-08-13

Alpha dual-version platform update for Minecraft 26.1.2 and 26.2.

### Added

- Added a version matrix that builds the same QCA feature set for both Minecraft 26.1.2 and 26.2 with the correct Fabric API and optional Mod Menu version for each target.
- Added `tools/build_all_versions.sh`, which tests and prepares both playable JARs and both Sources JARs in one run.

### Compatibility

- Ported screen access, screen switching, overlay checks, HUD visibility, title alerts, chat scrolling, player-list reading, and block-center distance checks to Minecraft 26.2 while preserving the 26.1.2 behavior through small target-specific adapters.
- Kept the mod client-only; this port adds no packet, command, click, automation, HTTP, or server-data behavior.
- Existing exact-version SkyHanni, Skyblocker, Firmament, and BabyZombieAddons adapters remain reviewed for 26.1.2 only and fail closed on 26.2 until matching provider builds are reviewed.

## [2.6.15] - 2026-08-12

Alpha responsive-UI correction for Minecraft 26.1.2.

### Fixed

- Rebuilt the Shards detail layout so the title, metadata, acquisition text, rate input, Save button, and Reset button share one consistent detail column instead of overlapping.
- Added independent scrolling for the Shard result list and detail text. Long effects and acquisition descriptions wrap inside a clipped viewport while rate controls remain anchored.
- Made Planner controls reflow before collision, made narrow Settings fields stack into one column, and made Fusion Lines use a scrollable canvas instead of stacking overflowing nodes. If the Settings page is too short to contain its controls safely, it now asks for a taller GUI instead of drawing fields outside the panel.
- Corrected responsive sizing and text fitting in the main settings screen, feature secondary pages, RGB picker, and HUD editor toolbar. Clipped setting rows no longer retain invisible hitboxes.

### Validation

- Added deterministic layout tests for wide and narrow Shards pages, Planner controls, Settings columns, and Fusion Lines canvas growth.

## [2.6.14] - 2026-08-12

Alpha pet-level display correction for Minecraft 26.1.2.

### Fixed

- Fixed an unmaxed Golden Dragon using the Ancient Golden Dragon Skin being displayed as `[Lvl 200]`. Cosmetic overflow levels now activate only after the received exact total experience has reached the pet's real level-200 maximum.
- When exact experience is unavailable, an ordinary non-max pet now keeps the level received from Hypixel instead of being promoted to its maximum level by the overflow fallback.

### Preserved

- Maxed Ancient Golden Dragons still display level 200, and verified experience beyond level 200 still displays the supported cosmetic overflow level when that option is enabled.

## [2.6.13] - 2026-08-12

First Alpha unified SkyBlock-mod controls for Minecraft 26.1.2.

### Added

- Added one function-first settings registry spanning QCloudy_Addition and the inspected SkyHanni 7.41.0, Skyblocker 6.8.2, Firmament 44.3.0, and BabyZombieAddons 3.4.1 builds. Integrations are optional; QCA still starts and works alone.
- Added the ordered top-level categories General, Maps, Items & Menus, Combat, Dungeons, Slayer, Mining, Farming, Foraging, Fishing, Hunting, Rift, and Events. Safari is grouped under Hunting, Garden under Farming, and Crimson Isle/Kuudra under Combat. A feature is shown only once.
- Added provider selection as the first row of each shared feature's secondary page. Selecting one provider and enabling the shared card enables that implementation and disables only exact equivalents from the other detected providers; related but different features remain independent.
- Added live native Boolean, enum, bounded numeric, position, and scale controls for compatible provider versions. Values are read from and written to each provider's own runtime config and saved through its native save path; QCA does not edit another mod's JSON while it is unloaded.
- Added external HUD panels to QCA's existing Edit HUD screen. Only enabled HUDs owned by the currently selected provider are shown; drag/resize previews remain local until mouse release, then update the provider's own position/scale.

### Safety and compatibility

- Integrations are reflection-based and version-locked to the exact reviewed builds. A missing, incompatible, or structurally changed provider is hidden instead of guessed or force-written.
- Complex provider-specific editors whose safe value contract is not yet audited—such as custom color objects and compound keybind objects—remain in the provider's native screen for this first Alpha. The unified menu exposes only settings it can validate and persist safely.
- No integration downloads data, sends a packet or command, clicks a menu, or creates a hard dependency on another SkyBlock mod.

## [2.6.12] - 2026-08-11

Beta fishing cue and settings-navigation fix for Minecraft 26.1.2.

### Fixed

- Fixed the bundled Ciallo bite cue playing a second time when the player reeled in. Physical rod use is now classified as either a new cast or a reel action; only a confirmed new cast re-arms the once-per-hook sound gate.
- Preserved the exact `!!!` bite-marker requirement, direct water-hook priority, bounded ownerless lava-hook association, per-hook deduplication, default-off state, and independent 64%-default volume setting.

### Changed

- Promoted Fishing from the General subgroup to its own top-level settings category, ordered between Foraging and Hunting.
- Made the eight-category sidebar compress its row spacing on short GUI layouts so Fishing and the existing bottom controls do not overlap.

### Safety

- The change only classifies the player's physical rod-use callback and plays a local sound. It does not cast, reel, cancel input, click, move, send chat, send a command, or send an additional packet.

## [2.6.11] - 2026-08-11

Beta Shard planning update for Minecraft 26.1.2.

### Added

- Preserved the existing offline 320-Shard Fusion Guide and added a separate in-game **Shard Planner** with target quantity, a complete multi-step Fusion tree, alternative direct recipes, and a Materials Only summary.
- Added **Fastest** routing from editable Shards-per-hour rates and **Cheapest** routing from an optional client-side Bazaar price cache. Normal mode can compare hunting time with buying time; Ironman mode never uses Bazaar prices.
- Added a read-only Hunting Box warehouse. QCA records Shard IDs and `Owned: N Shards` only while the player physically has a received `/hb` Hunting Box page open, stores each profile locally, and offsets planner material requirements with the saved quantities.
- Added separate Planner pages for direct input/output recipe filtering, full Shard effects/family/Skill/acquisition details and custom rates, draggable Fusion Lines, warehouse inspection, and local settings.
- Added Kraken planning controls for Kuudra tier, completion time, coins/hour opportunity cost, Hunter Fortune, Crocodile level, and per-Fusion handling time.

### Compatibility

- Bazaar pricing is optional and dependency-free. QCA can read Skyblocker's already-cached prices through its public `ItemUtils.getItemPrice` API when a compatible Skyblocker version is installed.
- SkyHanni and Firmament are not treated as price providers because they currently expose no stable public cross-mod Bazaar-price API. If no compatible provider is present, price-based Cheapest planning is shown as unavailable; the offline guide, Ironman routes, rate-based routes, warehouse, recipes, details, and Fusion Lines continue to work.

### Safety and persistence

- The planner, rates, graph positions, mode, target, quantities, and Kuudra parameters are stored in QCA's local config; the warehouse uses a separate per-profile local JSON file.
- QCA performs no price HTTP request, Wiki request, `/hb` command, container click, Fusion, output selection, packet send, chat send, movement, or automation. It consumes only bundled data, optional data already cached by another client mod, and menus the player has actually opened.

## [2.6.10] - 2026-08-11

Beta Tree Gift ownership and creature-alert fix for Minecraft 26.1.2.

### Fixed

- Fixed configured Tree Gift creatures such as `-A wild Groundhog appeared!` being parsed but silently rejected by the ownership state machine.
- Personal ownership now uses the player-only `+N rewards gained!` summary instead of also requiring one legacy `You helped cut...` sentence. Public creature lines from nearby players still cannot arm an alert by themselves.
- Preserved proven ownership for five seconds after the closing Tree Gift border, covering Hypixel's post-block creature-spawn line without opening an unbounded public-chat window.
- Added support for a complete Tree Gift arriving as one multi-line chat component, including compacted borderless components whose personal summary and creature line share that same received value.
- Pending creature/reward rows now flush correctly regardless of whether the personal summary arrives before or after them; duplicate loot remains limited to one alert per gift session.

### Safety

- The fix only reads already-received chat text and `SHOW_TEXT` hover data. It sends no packet, chat message, command, click, movement, or server request.

## [2.6.9] - 2026-08-11

Beta fishing compatibility fix for Minecraft 26.1.2.

### Fixed

- Fixed the Fishing Bite Sound not playing during some Hypixel lava-fishing casts. Water fishing continues to use the directly owned vanilla hook; after a physical local rod use, QCA can now briefly associate a newly loaded ownerless Fishing Hook used by the lava-fishing presentation.
- The fallback rejects hooks that were already present before the cast and hooks explicitly owned by another player, then keeps the same associated hook until it disappears or the player reels it in.

### Performance and safety

- The broader hook lookup runs only during the bounded 40-tick association window or while the associated fallback hook remains loaded; idle gameplay does not scan for hooks every tick.
- Detection remains passive and local. It does not cast, reel, click, move, cancel the rod use, send a packet, chat message, or command.

## [2.6.8] - 2026-08-11

Beta client-audio update for Minecraft 26.1.2.

### Added

- Added an opt-in Fishing Bite Sound under General > Fishing. It detects the exact visible `!!!` ArmorStand next to the local player's own Fishing Hook and plays the bundled Ciallo cue once per cast.
- Added a continuous 0–100% per-feature volume slider, defaulting to 64%.

### Safety

- The feature defaults off, scans only a four-block box around the local player's already-loaded hook, and never reels, clicks, moves, sends a packet, chat message, or command.
- The supplied MP3 is converted to a bundled OGG resource. Playback is fully local and requires no separate resource pack or runtime network request.

## [2.6.7] - 2026-08-10

Beta map update for Minecraft 26.1.2.

### Changed

- Replaced the bundled Dwarven Mines artwork with the newly supplied single-layer 12-region map.
- Recalibrated every named Dwarven region to the replacement image so the live player arrow follows the correct region and local X/Z position.
- Removed Y from Dwarven map selection and projection. The map now uses only received sub-location text, local X/Z, and yaw; a generic `Dwarven Mines` label falls back to the nearest X/Z region center.
- Bumped the Beta patch version to `2.6.7`; playable and source artifacts now use `QCloudy_Addition-Beta-2.6.7+26.1.2`.

### Safety

- The map remains client-only and render-only. It reads no hidden terrain, sends no packet, chat, command, click, movement, or other server interaction.

## [2.6.6] - 2026-08-10

Beta promotion for Minecraft 26.1.2.

### Changed

- Promoted the reviewed `2.5.6` feature set from Alpha to Beta without adding new gameplay automation or server interaction.
- Changed the version line to `2.6.6`, following the project's rule that Beta updates increment the second version component.
- Standardized the playable artifact as `QCloudy_Addition-Beta-2.6.6+26.1.2.jar` and the source artifact as `QCloudy_Addition-Beta-2.6.6+26.1.2-sources.jar`.
- Updated GitHub, Modrinth, implementation, validation, and publication documentation for the Beta channel.
- Rewrote the Modrinth project description around the actual seven-category settings structure, current Beta scope, dependencies, HUD customization, and explicit client/server-command boundaries.

### Included from the 2.5.x Alpha line

- The standalone offline Attribute Shard Fusion Guide for the official 320-Shard set, with Recipes, Uses, Details, ordered inputs, quantities, selectable outputs, acquisition information, semantic colours, and Shard-specific icons.
- Search-focus, compact recipe-layout, Epic-colour, clickable-link, Wiki-formatting, reverse-recipe, and natural-plus-Fusion source fixes from Alpha 2.5.4 through 2.5.6.
- Complete removal of slot locking, Storage Overlay, and menu middle-click conversion.

### Safety

- The Beta remains client-only and passive. The Shard guide performs no runtime Wiki/API request, packet send, inventory click, Fusion, chat send, server command, or automation.
- `/th` and `/helia` remain explicit user-triggered shortcuts documented in the compliance notes; no command is sent without direct player input.

## [2.5.6] - 2026-08-10

Alpha update for Minecraft 26.1.2.

### Added

- Added a dedicated **Details** view for every one of the 320 Shards. It shows the exact Wiki-listed effect, rarity/category/skill/family/mob-type classification, and every documented acquisition method. Capture entries retain the mob, tool, and biome; kill/drop/fusion/tree-gift/shop/chest entries retain the available source detail rather than inventing missing probabilities.
- Added an explicit verified Fusion-recipe count to Shards that can be produced through Fusion, including Shards such as Queen Bee that also have natural acquisition methods. Fusion-only Shards are labelled separately.

### Fixed

- Corrected Epic Shard names from light-purple/pink (`§d`) to Minecraft's Epic dark-purple (`§5`). Stat, category, mob-type, acquisition, and rarity text now use their corresponding SkyBlock/Minecraft semantic colours.
- Clickable Shard text now darkens and gains an underline only while the pointer is over the visible text, making recipe navigation clear without changing the click target.
- Preserved spaces between differently coloured effect fragments and removed residual Wiki formatting markers from the offline catalog.

### Changed

- Bumped the alpha version to `2.5.6` and the artifact name to `QCloudy_Addition-alpha-2.5.6-26.1.2.jar`.
- Updated the offline 320-Shard detail catalog against the current Wiki rarity-table revisions and the official Bazaar allow-list. Runtime behaviour remains fully local and read-only.

## [2.5.5] - 2026-08-10

Alpha update for Minecraft 26.1.2.

### Fixed

- Replaced the generic amethyst fallback with 320 bundled, Shard-specific icons. A native Shard `ItemStack` already received by the client still takes priority and is retained in the session cache, so server/resource-pack presentation remains authoritative when available.
- Search focus now exits when the player clicks outside the search field, presses `Esc`, or presses `Tab`; clicking the field focuses it again. This restores recipe navigation and normal screen shortcuts without forcing the player to close the guide.
- Centered each recipe's two-input expression and output set as compact content-width groups. Their click targets now follow the visible items instead of spanning distant halves of the card.

### Changed

- Bumped the alpha version to `2.5.5` and the release artifact name to `QCloudy_Addition-alpha-2.5.5-26.1.2.jar`.
- Generated the 320 offline icons from the MIT-licensed SkyShards `public/shardIcons` set at reviewed commit `9688031dbc4e726168ffceb0f44884ff26e6e728`, filtered through QCA's exact 320-Shard catalog and excluding the extra Rainbug asset.

### Safety

- The Shard catalog, fallback icons, item models, and UI remain bundled and read-only. QCA performs no runtime Wiki/API/icon request and sends no chat, server command, packet, menu click, fusion, or automation.

## [2.5.4] - 2026-08-09

Alpha update for Minecraft 26.1.2.

This begins the `2.5.x` Alpha development line after `1.5.3`, which remains the latest published release baseline. The release channel is still Alpha; only the post-1.5.3 version line was renumbered.

### Added

- Added a JEI-inspired, completely offline Attribute Shard Fusion Guide under Items & Menus.
- Added search across original Shard name, ID, attribute, rarity, category, family, and skill; Recipes/Uses tabs; order-preserving input pairs; history; pagination; native item icons observed by the client; input/output quantities; special yields; and the Pure Reptile double-output chance.
- Added the local `/qshard [English query]` screen command, an **Open Guide** settings action, and an optional unbound keyboard/mouse chord. None sends a server payload.

### Changed

- Bumped the alpha version to `2.5.4` and the release artifact name to `QCloudy_Addition-alpha-2.5.4-26.1.2.jar`.
- Rebuilt the Shard catalog as an exact 320-product official Bazaar allow-list. Anteater, Zombuddy, Troodon, and Ghost Crab are present; Goldolot is `R92`; Rainbug is excluded because it is absent from the official Bazaar Shard universe.
- Preserved Attribute Fusion input order, up to three selectable outputs, Chameleon numeric stepping/rarity rollover, and the documented consumption/yield rules.
- Kept separate output slots when ID Fusion and Special Fusion produce the same Shard, because the selectable yields remain different (`x1` versus `x2`).

### Removed

- Removed slot locking, Storage Overlay, and menu middle-click conversion from the implementation, configuration, tests, and current documentation.

### Safety

- The guide uses committed offline JSON and only client-observed ItemStacks for optional resource-pack-aware icons. It performs no runtime Wiki/API/network request, menu click, fusion, command, chat, or automation.

## [1.5.1] - 2026-08-06

First publication-ready build for Minecraft 26.1.2.

### Added

- Dwarven Mines and three-layer Glacite Tunnels maps.
- Mining commissions, Mithril/Gemstone/Glacite Powder, HOTM slot, and Crimson Isle task tracking.
- Torrhus Chapter/resource, Tree Critter, Miria Contest, Benefactor, and personal Tree Gift tracking.
- Critter Safari Dashboard/Critterdex, Cold/campfire, Doomspiral, Warden, Sparkling, Floor Drop, quest-item, Wumpa, Snoozle-wall, Safari Belt, and Critter highlight helpers.
- Beeheemoth outline, spawn beacon, and spatial sound-volume control.
- Configurable Lasso REEL audio and center-screen alert system.
- Equipped Pet HUD with verified player heads, skins, XP, overflow levels, and held items.
- Ender Dragon outline, Chat Peek, item timestamps, cursor memory, and configurable teleport sounds.
- Bilingual BLC-inspired settings and per-HUD editor.
- Manual reconnect button, `/th`, and `/helia` client shortcuts.

### Fixed

- Removed every legacy pre-rendered pet-icon fallback that could show a wrong or blurred icon.
- Prevented max-level pets from showing a redundant max-XP line while retaining their held-item row.
- Prevented bold text and long task/pet lines from overflowing or being shortened with ellipses.
- Prevented Safari capture Armor Stands from receiving Critter outlines.
- Fixed Wumpa party Loot Share progress, spawned-state HUD replacement, and Ravager-body route selection.
- Fixed four Safari Belt milestone layouts and account/profile persistence.
- Fixed Helia Chapter, Benefactor, Whispers, Essence, Forest Fortune, and Sweep acquisition/persistence.
- Fixed nearby-player and repeated Tree Gift alerts with a bounded personal-ownership state machine.
- Replaced the final deprecated loaded-chunk call without expanding scan scope.

### Changed

- Renamed the project and controls category to QCloudy_Addition / QCloudy Addition.
- Reorganized settings into General, Maps, Mining, Foraging, Hunting, Safari, Crimson Isle, Combat, Pets, Chat, and Inventory with no duplicate feature cards.
- Kept AOTE/AOTV teleport sounds vanilla by default and exposed sound, volume, and pitch choices.
- Standardized alert volume defaults at 64%.

### Removed

- Catch-all `ALL` settings category.
- Golden Dragon/Dragon's Lair finder.
- Duplicate feature switches, redundant right-click hints, and separate key-capture screen.
- Runtime Firmament dependency and legacy pet PNG selection.

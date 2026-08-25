# QCloudy_Addition 0.3.9 Release dual-target validation

Date: 2026-08-25<br>
Minecraft: 26.1.2 and 26.2<br>
Java: 25

Validated artifacts and SHA-256:

- `release/QCloudy_Addition-0.3.9+26.1.2-Release.jar` — 3,509,330 bytes — `bd6c986fbed65d2fe0368e275efd72996b48ad420550ead82c67c19b79b608cb`
- `release/QCloudy_Addition-0.3.9+26.1.2-Release-sources.jar` — 3,058,260 bytes — `e72e00f402fb7a8e60e2830cf556172f508c25f7e9a99c2461706de4a080beb5`
- `release/QCloudy_Addition-0.3.9+26.2-Release.jar` — 3,509,334 bytes — `bb3273bb3eb4135ff60c7c647e75f2ade86ca5c967e99b39311e179cc0368e97`
- `release/QCloudy_Addition-0.3.9+26.2-Release-sources.jar` — 3,058,249 bytes — `e6d8ee0d00498102826816e8f688bdc730bbfb1133dc4e66ed120de82f6d5891`
- `release/QCloudy_Addition_Website-0.3.9-20260825.zip` — 1,525,939 bytes — `0b6a640a770a7f02a141d13dc1c37ee288e0f37ee472adb1e6c6c19957f194a3`

Verified in this workspace:

- `tools/build_all_versions.sh` completed `clean test build prepareRelease` successfully for both Minecraft targets.
- Each target ran 197 tests with 0 failures and 0 errors.
- Expanded metadata declares `0.3.9+26.1.2` or `0.3.9+26.2`, the exact Minecraft target, its matching Fabric API dependency, Java 25, and `environment: client`.
- All inspected class files use Java major version 69 (Java 25).
- English and Simplified Chinese each contain 534 language keys, and both key sets are identical.
- Both playable JARs contain exactly 320 per-ID Shard item definitions, 320 models, and 320 textures.
- All four artifacts pass JDK 25 `jar --validate` and `unzip -t`. Every file is byte-identical to its corresponding copy under the target-specific `build/*/libs/` directory.
- Release 0.3.9 preserves the client-only behavior boundary and adds no autonomous movement, menu clicking, combat, fishing, fusion, or server-command loop.
- Unified settings management and unified HUD management remain separate, default-off experimental concept tests. Passing local contract tests does not establish compatibility with future provider updates.
- The website deployment archive passes `unzip -t`, contains no outer wrapper directory, excludes the preserved historical website ZIP, and has valid local links across all six HTML pages. Its bundled JavaScript passes Node syntax checks.

Outstanding live regression boundary:

- Automated tests and archive checks do not replace an authenticated Hypixel session or a full current provider-mod pack. Natural Power Orb/Flare expiry, SOS replacement timing, Century Cake activation/expiry, Tree Gift ownership signals, and provider-backed setting/HUD writes still require live regression before compatibility can be treated as confirmed.
- The website package, GitHub Release, Modrinth entry, and GitHub Wiki were prepared locally but were not deployed or published by this validation run.

---

# QCloudy_Addition 0.2.9 Alpha 30 single-target validation

Date: 2026-08-21<br>
Minecraft: 26.1.2<br>
Java: 25

Validated artifacts:

- `release/QCloudy_Addition-0.2.9+26.1.2-Alpha-30.jar`
- `release/QCloudy_Addition-0.2.9+26.1.2-Alpha-30-sources.jar`

SHA-256:

- 26.1.2 playable: `731ba81dfd0d0c55263aaf682be4adf2ce4325bbb3c401c3163e8097761ac319`
- 26.1.2 sources: `ffc4576997997017c720d5d728d381cb3792608745d0ee272ee88fd93ce3b402`
Version 0.2.9 Alpha 30 fixes replacement Flare lifecycles. Using a recognised Warning, Alert, or SOS Flare while one is already active immediately replaces the active Flare and expiry, so the prior three-minute deadline cannot survive even when a replacement omits the initial placement-confirmation signal. Use-on-block placement is observed, and an exact placement sound can recover a missed use callback only while the local player holds a recognised Flare.

Verified in this workspace:

- Java 25 `clean test build prepareRelease` completed successfully for Minecraft 26.1.2.
- The test suite ran 197 tests in 37 suites with 0 failures, 0 errors, and 0 skips.
- Regression coverage confirms that replacing the same SOS at two minutes immediately restarts a complete three-minute timer without requiring a second confirmation signal, a missed second use callback is recovered from the exact held SOS plus placement sound, and an unrelated held item cannot reset the active lifecycle.
- **General -> Supported Mods** opens with both independent controls visible: **Manage Other Mod Settings** and **Manage Other Mod HUDs**. Regression coverage requires both entries to remain registered, and both persisted defaults remain off.
- Expanded metadata declares `0.2.9-alpha.30+26.1.2`, the matching Minecraft target, required Fabric API version, and a client-only environment.
- All class files use Java major version 69 (Java 25).
- English and Simplified Chinese each contain 534 language keys, and both key sets are identical.
- The playable JAR contains the complete 20-cake catalog. Starborn resolves to `Hunting Fortune` and `+1 Hunting Fortune`.
- Both artifacts pass JDK 25 `jar --validate` and `unzip -t`. Every release artifact is byte-identical to its corresponding copy under `build/libs/`.
- The playable artifacts retain the client-only behavior boundary: no autonomous movement, menu clicking, fusion, combat, fishing, or server-command loop was added.

Outstanding live regression boundary:

- Automated tests and archive checks do not replace an authenticated Hypixel session. Replacing SOS before its previous three-minute deadline must still be verified live: the old deadline should remain silent and only the replacement deadline should alert. Natural Power Orb and Warning/Alert/SOS Flare expiry, Century Cake expiry/renewal interaction, and provider-backed settings/HUD discovery also remain live checks.

---

# QCloudy_Addition Alpha 2.8.28 single-target validation

Date: 2026-08-18<br>
Minecraft: 26.1.2<br>
Java: 25

Validated artifacts:

- `release/QCloudy_Addition-Alpha-2.8.28+26.1.2.jar`
- `release/QCloudy_Addition-Alpha-2.8.28+26.1.2-sources.jar`

Playable SHA-256: `c83c60bc9c07126481f893b512d0747ee0f70998e0b17d8eca45bae5ed6d7d95`<br>
Sources SHA-256: `90d1b43d0d35e7a7790eff6b32263913354a7858bd409964072f60d50c87b792`

Alpha 2.8.28 fixes Starborn Century Cake remaining inactive after the exact real refresh message. The catalog now uses Hypixel's canonical `Hunting Fortune` name instead of the incorrect `Hunter Fortune` label.

Verified in this workspace:

- Java 25 `clean test build prepareRelease` completed successfully: 192 current tests in 37 suites, 0 failures, 0 errors, and 0 skips.
- The parser accepts the exact line `Big Yum! You refresh +1<private stat glyph> Hunting Fortune for 48 hours!` after removing formatting and the private-use stat glyph.
- Exact first activation with `Yum! You gain +1<private stat glyph> Hunting Fortune for 48 hours!` remains supported.
- The old incorrect `Hunter Fortune` spelling is explicitly rejected so it cannot silently re-enter the catalog.
- Starborn Century Cake now displays `Hunting Fortune` and `+1 Hunting Fortune` in the effects screen and tooltip.
- Century Cake state remains keyed by `EPOCH_CAKE_STARBORN`; correcting the display/match name requires no persisted-data migration.
- The existing 20-cake catalog, absolute real-world expiry persistence, unified alert switch, effects screen, expiry title/sound, merged chat notification, and click-only `/visit northwestcloudy` renewal action are unchanged.
- Alpha 2.8.26's Power Orb and confirmed Flare lifecycle remain present and unchanged.
- English and Simplified Chinese each contain 534 identical language keys.
- Expanded metadata declares `2.8.28-alpha+26.1.2`, Minecraft 26.1.2 and a client-only environment.
- The playable JAR contains the corrected Starborn catalog entry. Binary and Sources artifacts in `build/libs/` are byte-identical to their `release/` copies, and both release artifacts pass JDK 25 `jar --validate` and `unzip -t`.
- No Alpha 2.8.28 artifact for Minecraft 26.2 was produced; current Alpha policy targets only Minecraft 26.1.2.

Outstanding Alpha regression boundary:

- Automated parser, catalog, state, archive, and metadata checks do not replace an authenticated Hypixel test. Install 2.8.28, refresh or eat Starborn Century Cake again, and confirm that `/cake` changes it from inactive to a running 48-hour timer.
- A Starborn activation or refresh missed by an older build cannot be recovered retrospectively from chat history. The player must trigger the message again while 2.8.28 is running.
- Natural Power Orb and Warning/Alert/SOS Flare expiry still require the live regression recorded under Alpha 2.8.26.

---

# QCloudy_Addition Alpha 2.8.27 single-target validation

Date: 2026-08-17<br>
Minecraft: 26.1.2<br>
Java: 25

Validated artifacts:

- `release/QCloudy_Addition-Alpha-2.8.27+26.1.2.jar`
- `release/QCloudy_Addition-Alpha-2.8.27+26.1.2-sources.jar`

Playable SHA-256: `7fab6015f1ccfdeea55cc5b3cc6cf3905448e5faa38f3b563edb4b59fb8a863a`<br>
Sources SHA-256: `84b181de1a42a409f3bb1008b2eab9c3beb4455d8b6d70a0b2673f248852a226`

Alpha 2.8.27 fixes first-time Century Cake activation tracking. The real first-use message now starts the 48-hour timer immediately, including Starborn Century Cake's private-use Hunter Fortune stat glyph, while the existing exact refresh path remains intact.

Verified in this workspace:

- Java 25 `clean test build prepareRelease` completed successfully: 191 current tests in 37 suites, 0 failures, 0 errors, and 0 skips.
- The parser accepts the exact first-activation form `Yum! You gain <known bonus> for 48 hours!` and the exact refresh form `Big Yum! You refresh <known bonus> for 48 hours!`.
- Formatting codes and the private-use glyph embedded in `+1 Hunter Fortune` are normalized before catalog matching, so Starborn Century Cake resolves to the existing Hunter Fortune entry.
- Invented combinations such as `Big Yum! You gain ...` and `Yum! You refresh ...`, unknown effects, unrelated lines, and non-48-hour durations fail closed.
- The existing 20-cake catalog, absolute real-world expiry persistence, unified alert switch, effects screen, expiry title/sound, merged chat notification, and click-only `/visit northwestcloudy` renewal action are unchanged.
- Alpha 2.8.26's Power Orb and confirmed Flare lifecycle remain present and unchanged.
- English and Simplified Chinese each contain 534 identical language keys.
- Expanded metadata declares `2.8.27-alpha+26.1.2`, Minecraft 26.1.2 and a client-only environment. Class files use Java major version 69.
- Binary and Sources artifacts in `build/libs/` are byte-identical to their `release/` copies. Both release artifacts pass JDK 25 `jar --validate` and `unzip -t`.
- No Alpha 2.8.27 artifact for Minecraft 26.2 was produced; current Alpha policy targets only Minecraft 26.1.2.

Outstanding Alpha regression boundary:

- Automated parser, state, archive, and metadata checks do not replace an authenticated Hypixel test. After installing 2.8.27, eat one currently inactive cake—especially Starborn Century Cake—and confirm that `/cake` immediately changes it from inactive to a running 48-hour timer. Then refresh an already-active cake and confirm that its expiry is replaced.
- A first-activation message missed by an older build cannot be recovered retrospectively from chat history. The player must eat or refresh that cake again while 2.8.27 is running.
- Natural Power Orb and Warning/Alert/SOS Flare expiry still require the live regression recorded under Alpha 2.8.26.

---

# QCloudy_Addition Alpha 2.8.26 single-target validation

Date: 2026-08-17<br>
Minecraft: 26.1.2<br>
Java: 25

Validated artifacts:

- `release/QCloudy_Addition-Alpha-2.8.26+26.1.2.jar`
- `release/QCloudy_Addition-Alpha-2.8.26+26.1.2-sources.jar`

Playable SHA-256: `265d271600603b433ac4560a2d576397979056c21590c6595290bcb32043e7e6`<br>
Sources SHA-256: `42b4ef983a94d3a83245bb71f5a5c6d030e23c49cbeefff1660244c26ead53b4`

Alpha 2.8.26 replaces the incomplete Flare chat assumption with a confirmed local lifecycle while preserving exact player-owned Power Orb despawn chat alerts. The feature is explicitly named **Power Orb & SOS Despawn Alert**.

Verified in this workspace:

- Java 25 `clean test build prepareRelease` completed successfully: 190 current tests in 37 suites, 0 failures, 0 errors, and 0 skips.
- The parser accepts only the four exact received Power Orb lines and explicitly rejects the former `Your Warning/Alert/SOS Flare despawned.` assumptions.
- Warning, Alert, and SOS Flare use their exact SkyBlock item IDs. An item-use attempt opens only a two-second candidate window; the exact successful Firework Rocket launch sound at the reviewed pitch and volume must confirm placement before the three-minute monotonic lifecycle begins.
- Failed or cooldown-blocked Flare uses cannot start a timer. A confirmed new Flare silently replaces the previous lifecycle. World/server changes and disconnects clear active and pending state without an alert.
- Entity unload, render distance, player distance, buff range, and an 80-block threshold are absent from the decision path. A confirmed lifecycle produces one alert at expiry and is then cleared.
- Power Orb, Flare, center-screen text, local sound, and 0–100% volume are separate secondary settings. The feature and sound default on, and sound volume defaults to 64%.
- English and Simplified Chinese each contain 534 identical language keys.
- Expanded metadata declares `2.8.26-alpha+26.1.2`, Minecraft 26.1.2 and a client-only environment. Class files use Java major version 69.
- The playable JAR contains the three deployable-expiry classes, the Sound Engine hook, exactly 320 catalog Shards, 320 Shard item definitions, 320 Shard models, and 320 Shard textures.
- Binary and Sources artifacts in `build/libs/` are byte-identical to their `release/` copies. Both release artifacts pass JDK 25 `jar --validate` and `unzip -t`.
- No Alpha 2.8.26 artifact for Minecraft 26.2 was produced; current Alpha policy targets only Minecraft 26.1.2.

Outstanding Alpha regression boundary:

- Automated state, parser, configuration, archive, and static data-flow checks do not replace an authenticated Hypixel test. Before wider publication, let one Power Orb and each of Warning, Alert, and SOS Flare expire naturally. Confirm that successful placement starts one lifecycle, failed/cooldown-blocked use starts none, replacement is silent, and expiry creates exactly one configured title/sound.
- The exact successful-placement sound signature is based on the reviewed SkyHanni implementation and current client behavior. If Hypixel changes that signal, QCA fails closed instead of starting an unconfirmed timer.

---

# QCloudy_Addition Alpha 2.8.25 single-target validation

Date: 2026-08-17<br>
Minecraft: 26.1.2<br>
Java: 25

Validated artifacts:

- `release/QCloudy_Addition-Alpha-2.8.25+26.1.2.jar`
- `release/QCloudy_Addition-Alpha-2.8.25+26.1.2-sources.jar`

Playable SHA-256: `74dd1e2ccedebb93ee4ac02d75057ddc77548fc47ba4b3f2d94e2f8b1d4f4f69`<br>
Sources SHA-256: `b9fafaee07402d20bb578b0259807c874bd2417c62512cec593a6c6b58c9226c`

Alpha 2.8.25 is intentionally built only for Minecraft 26.1.2. It adds one unified Century Cake expiry-alert switch for all twenty effects, persists absolute 48-hour real-world expiry times across offline periods, and preserves the Power Orb/Flare expiry work from Alpha 2.8.24.

Verified in this workspace:

- Java 25 `clean test build prepareRelease` completed successfully: 186 current tests in 36 suites, 0 failures, 0 errors, and 0 skips.
- The committed Century Cake catalog contains exactly 20 unique internal IDs and 20 unique effects. Every entry has the expected `UNCOMMON` rarity, bonus text, and a cake-head texture property.
- The parser accepts exact formatted or unformatted `Big Yum! You refresh/gain <known bonus> for 48 hours!` lines and rejects unknown effects and non-48-hour durations.
- One default-on `expiryAlerts` field controls every cake expiry. There is no per-cake or per-effect alert switch. Its separate local sound is enabled by default at the project-standard 64% volume.
- Timers store an absolute expiry timestamp, so offline real-world time continues to count. A refresh replaces the saved expiry for that account/profile and effect.
- Expiries discovered in one tick are collected once and merged. The single message is exactly `[QC] Century Cake <Effect> Expired! Click Here For Cake Eating`; a batch uses `[QC] <count> Century Cake Effect Expired! Click Here For Cake Eating`.
- Tests verify that `Click Here For Cake Eating` is underlined and carries exactly one Minecraft `RUN_COMMAND` click action for `/visit northwestcloudy`. The timer never executes that command automatically.
- `/cake` and `/centurycakeeffect` are collision-guarded local client commands that only open the effects-style timer screen. They send no chat or server command.
- English and Simplified Chinese each contain 531 identical language keys.
- Expanded metadata declares `2.8.25-alpha+26.1.2` and a client-only environment. Class files use Java major version 69.
- Binary and Sources artifacts in `build/libs/` are byte-identical to their `release/` copies. All four copies pass JDK 25 `jar --validate` and `unzip -t`.
- The playable JAR contains `CenturyCakeManager`, the committed Century Cake data resource, and the expected Fabric metadata.
- No Alpha 2.8.25 artifact for Minecraft 26.2 was produced; current Alpha policy targets only Minecraft 26.1.2.

Outstanding Alpha regression boundary:

- Automated tests and archive validation do not replace an authenticated Hypixel test. Before wider publication, refresh at least one real Century Cake effect, verify its saved remaining time after reconnect/restart, and use a controlled expired test profile to confirm the title, local sound, tooltip, single/batch chat wording, underline, and clicked `/visit northwestcloudy` behavior.
- Hypixel-visible cake refresh wording may change. The parser deliberately fails closed instead of guessing an unknown message.
- The Power Orb/Flare expiry feature also still needs a natural in-game expiry regression as recorded under Alpha 2.8.24.

---

# QCloudy_Addition Alpha 2.8.24 single-target validation

Date: 2026-08-16<br>
Minecraft: 26.1.2<br>
Java: 25

Validated artifacts:

- `release/QCloudy_Addition-Alpha-2.8.24+26.1.2.jar`
- `release/QCloudy_Addition-Alpha-2.8.24+26.1.2-sources.jar`

Playable SHA-256: `12d511b898c43d066b9dd498162fd0e33a7c167440cfb8499af175321d1919c8`<br>
Sources SHA-256: `7fb0fa2ed9bd0f78a7a153663be8e409e2e61b0e209e8f0affee45fada5d732c`

Alpha 2.8.24 is an archived historical build for Minecraft 26.1.2. Its Power Orb chat path was valid, but its original Flare chat assumption was incomplete and has been removed and replaced in Alpha 2.8.26.

Verified in this workspace:

- Java 25 `clean test build prepareRelease` completed successfully: 179 current tests in 32 suites, 0 failures, 0 errors, and 0 skips.
- The parser reliably accepted the four exact Power Orb lines. The former test-only Warning/Alert/SOS Flare chat assertions did not represent a confirmed live server signal and are not part of the current implementation.
- Configuration migration version 22 enables the feature and its sound for existing installs, gives the sound the project-standard 64% default, and keeps sound/volume on the feature's own **Combat → Deployables** secondary settings page.
- Static data-flow review confirms that the new handler only reads received game chat, displays a local title, and plays a local client sound. It sends no chat, command, packet, interaction, or network request.
- The playable JAR contains exactly 320 catalog Shards, 320 Shard item-model definitions, and 320 Shard textures. English and Simplified Chinese each contain 518 identical language keys.
- Expanded metadata declares `2.8.24-alpha+26.1.2`, Minecraft 26.1.2, Fabric API 0.155.2+26.1.2, Fabric Loader 0.19.3+, Java 25+, and a client-only environment. Class files use major version 69.
- Binary and Sources artifacts in `build/26.1.2/libs/` are byte-identical to their `release/` copies. Both pass JDK 25 `jar --validate` and `unzip -t`.
- No Alpha 2.8.24 artifact for Minecraft 26.2 was produced. `tools/build_all_versions.sh` skips 26.2 whenever the release channel is Alpha.

Outstanding Alpha regression boundary:

- Do not use this historical artifact to validate Flare expiry behavior. Alpha 2.8.26 replaces the invalid Flare chat branch with a confirmed local placement lifecycle and requires a fresh natural-expiry regression.
- The current fixed Power Orb allow-list deliberately fails closed. If Hypixel adds or renames a Power Orb, it will not trigger until the client-visible name is reviewed and added.

---

# QCloudy_Addition Alpha 2.8.23 single-target validation

Date: 2026-08-16<br>
Minecraft: 26.1.2<br>
Java: 25

Validated artifacts:

- `release/QCloudy_Addition-Alpha-2.8.23+26.1.2.jar`
- `release/QCloudy_Addition-Alpha-2.8.23+26.1.2-sources.jar`

Playable SHA-256: `36fa8ddee7900758ea29f4a4932fd029d3207d0cd38e82dd150ec4735b37ac5b`<br>
Sources SHA-256: `0d6afd54988ea346f1cc5870b0b374063a02b7bd9baad9d9f50d9a6ed4dd5a0b`

Alpha 2.8.23 is intentionally built only for Minecraft 26.1.2. It fixes the false mining HUD on The Park's `Jungle Island`, removes unavailable settings categories from the sidebar, and renames the redundant Fishing subgroup to `Bite Alerts` / `咬钩提示`.

Verified in this workspace:

- Java 25 `clean test build prepareRelease` completed successfully: 176 current tests in 31 suites, 0 failures, 0 errors, and 0 skips.
- Location parsing treats exact `Jungle` as Crystal Hollows while rejecting `Jungle Island`, so Park evidence no longer enables the mining tracker.
- Settings category availability is derived from the actual feature set; empty categories such as Dungeons are hidden instead of opening an empty page.
- The Fishing top-level category remains, while its nested group uses the distinct `Bite Alerts` / `咬钩提示` label.
- The playable JAR contains exactly 320 catalog Shards, 320 Shard item-model definitions, and 320 Shard textures. English and Simplified Chinese each contain 515 identical language keys.
- Expanded metadata declares Minecraft 26.1.2, Fabric API 0.155.2+26.1.2, Fabric Loader 0.19.3+, Java 25+, and a client-only environment.
- Binary and Sources artifacts in `build/26.1.2/libs/` are byte-identical to their `release/` copies. Both pass JDK 25 `jar --validate` and `unzip -t`.
- No Alpha 2.8.23 artifact for Minecraft 26.2 was produced. `tools/build_all_versions.sh` skips 26.2 whenever the release channel is Alpha.

Outstanding Alpha regression boundary:

- Automated tests and archive checks do not replace authenticated in-game visual testing. Verify The Park/Jungle Island, an actually empty provider category, and the Fishing page at the GUI scales used by the target modpack before wider publication.
- Capability-detected provider integration remains best-effort compatibility. Future provider changes may stay unclassified or appear in the compatibility gaps report until QCA can identify them safely.

---

# QCloudy_Addition Alpha 2.8.22 dual-version validation

Date: 2026-08-15<br>
Minecraft: 26.1.2 and 26.2<br>
Java: 25

Validated artifacts:

- `release/QCloudy_Addition-Alpha-2.8.22+26.1.2.jar`
- `release/QCloudy_Addition-Alpha-2.8.22+26.1.2-sources.jar`
- `release/QCloudy_Addition-Alpha-2.8.22+26.2.jar`
- `release/QCloudy_Addition-Alpha-2.8.22+26.2-sources.jar`

26.1.2 playable SHA-256: `dd578074410305767e04be06b4550b981440c90095a3435c5169c44130bedf83`<br>
26.1.2 Sources SHA-256: `58504336df7878ee9b1b9f0c5949db3a96e2ab37e4a6cea1c9d11ede195bd96d`<br>
26.2 playable SHA-256: `bf4402961f8ca489e5b9f026d954da5ca3128de90a94bd3f509986886324a76c`<br>
26.2 Sources SHA-256: `d0d738c4d3fbdb38b6dcebcb8ad90c465ffb73e5395f55fc18fb5488342ef45a`

Alpha 2.8.22 preserves the five optional capability-detected providers and adds an explicit second confirmation before every provider scan. First enable without a valid session snapshot and every Refresh identify their Settings or HUD scope before a job can be created. Cancelling initial confirmation leaves the master off; cancelling Refresh preserves the previous validated snapshot; restoring an enabled master after restart does not silently scan. Provider discovery remains staged, deterministic, local, and read-only.

Verified in this workspace:

- Java 25 `clean test build prepareRelease` completed successfully for both targets: 175 current tests in 31 suites per target, 0 failures, 0 errors, 0 skips.
- Both playable JARs contain exactly 320 catalog Shards, 320 Shard item-model definitions, and 320 Shard textures; English and Simplified Chinese each contain 515 identical language keys.
- Dwarven projection tests cover continuous one-axis movement, identical X/Z on The Mist and an overhead bridge, representative overview areas, and safe edge clamping. The official `C&C Minecarts Co.` sub-location is also classified as Dwarven Mines.
- Deterministic geometry tests cover wide and narrow Shard detail columns, separated rate controls, compact Plan controls, narrow Settings stacking and safe short-screen fallback, Recipe field widths, Fusion Lines canvas growth, secondary-setting sliders, RGB bars, and preset swatches.
- Main and test compilation completed from a clean output directory.
- Expanded metadata declares the exact target in each artifact: 26.1.2 uses Fabric API 0.155.2+26.1.2; 26.2 uses Fabric API 0.154.2+26.2. Both remain client-only and require Fabric Loader 0.19.3+ and Java 25+.
- Binary and Sources artifacts in `build/<target>/libs/` are byte-identical to the corresponding `release/` artifacts.
- All four JARs pass JDK 25 `jar --validate` and `unzip -t`; English and Chinese language files remain shared between targets.
- Static interface inspection of the supplied latest MC 26.1.2 provider JARs confirmed the recognised entry contracts used by this build: SkyHanni 7.45.0 exposes `SkyHanniMod.feature` and `SkyHanniConfig.saveNow()`; Skyblocker 6.9.1 exposes `SkyblockerConfigManager.get()` and `update(Consumer)`; BabyZombieAddons 3.5.1 exposes `ModConfigManager.get()` and `save()`; Firmament 44.3.0 exposes the ManagedConfig registry/options path. This verifies entry contracts, not every individual setting or visual HUD behavior.
- Static inspection of official Feesh source commit `9a5f9e074492a0f6c0eb4f6251ac361b7afb3992` confirms the public `Settings` singleton/save method, seven category singletons, `FeeshGui.getAllRegisteredGuis()`, overlay visibility/content accessors, native alignment recalculation, and `PersistentDataManager.updateOverlayCoordsData(...)`. QCA copies no Feesh code or resource and has no Feesh compile/runtime dependency.
- Focused tests verify deterministic Feesh secondary-setting grouping, exclusion of unrelated setting names, removal of implementation-only `Overlay` suffixes, and exact LEFT/CENTER/RIGHT anchor round trips. The adapter uses public accessors instead of generated delegate fields, saves after a successful setting write, and reports unsupported non-toggle or complex values rather than inventing enable cards.
- Capability tests cover prefixed future-version toggle names, semantic association of only same-function settings, prefixed X/Y/scale recognition, and deterministic merging of Settings/HUD gaps for one provider function while filtering fully supported and QCA-owned rows. Source/resource checks confirm that `/qca` and `/qc` are the only QCA settings-command aliases.
- Configuration and feature tests verify that both provider-integration masters default off, remain independently toggleable, and are the only feature cards that can open the initial scan confirmation. Confirmation-copy tests distinguish Settings from HUD scope and allow only Enter/Numpad Enter as keyboard confirmation. Their separate secondary pages expose only their own total, current scan activity and manual Refresh action.
- Static UI/data-flow inspection confirms that the Compatibility Gaps card is separate from the feature enum and normal toggle path, both left and right click open the same report, report rows are provider-grouped, and the report path invokes neither value setters nor provider save/update methods.
- Static and test review confirms the cached report layout is invalidated when its content width changes. The report reads the latest completed immutable snapshot and does not silently rescan or write provider state when opened.
- Classifier tests confirm that verified native/path classification has priority, an unknown fishing-like option can be classified locally, and ambiguous low-confidence text remains unclassified. Static data-flow review confirms that the only job-creation calls are inside callbacks reached from the confirmation screen for initial enable or Refresh. `IntegrationScanService.tick()` only advances an existing job and never bootstraps one, so startup does not scan even when a master was restored as enabled. Opening ordinary settings pages does not scan.
- Shard reverse-index tests still verify exact/output/input consistency after removal of the unused duplicate all-recipes list. Lasso detection retains the same local leash, exact `REEL` label, two-block association, and false-to-true sound gate while using one loaded-entity traversal.
- Both clean target builds completed without a Gradle deprecation warning from the project script. Both resource-processing tasks explicitly exclude `.DS_Store`.
- The optional integration has no compile-time dependency on any provider and adds no HTTP client, packet, chat command, container click, gameplay input, or server-data request. QCA does not invoke Feesh API, chat, command, sharing, or gameplay actions. Normal opt-in editor writes use only the selected installed provider's live object and own save/update path; the report is read-only and QCA does not edit provider configuration files directly.
- Static matrix/scissor review confirms balanced push/pop and enable/disable pairs in the edited configuration and inventory screens.

Outstanding Alpha regression boundary:

- Geometry tests and archive checks do not replace visual testing in a live Minecraft renderer. Before wider publication, open the main and secondary settings pages and HUD editor with each installed provider at every GUI Scale used by the target modpack; verify both languages, resize/re-init, long names, mouse hitboxes, native persistence, and scrolling.
- Feesh was inspected from its official source contract but was not loaded into an authenticated Minecraft/Hypixel session during this build. Before wider publication, test representative Boolean/enum/numeric Feesh settings and several LEFT/CENTER/RIGHT overlays, then reopen Feesh's native editor and restart the client to confirm identical values and persistence.
- Capability discovery is best-effort structural compatibility, not proof that every future provider release is compatible. Readable but low-confidence structures remain out of functional categories and appear as classification gaps; completely unreadable structures can produce only a generic provider-level integration gap until QCA learns a safe named structure.
- The supplied provider JARs are MC 26.1.x builds. The MC 26.2 QCA artifact is compiled and archive-validated, but third-party editing on 26.2 still requires separate provider builds for 26.2 and an authenticated in-game test with those builds. No 26.1 provider JAR should be installed into a 26.2 instance merely to exercise this adapter.

---

# QCloudy_Addition Beta 2.6.12 Fishing cue and settings validation

Validation date: 2026-08-11

Validated artifacts:

- `release/QCloudy_Addition-Beta-2.6.12+26.1.2.jar`
- `release/QCloudy_Addition-Beta-2.6.12+26.1.2-sources.jar`

## Result

Beta 2.6.12 prevents the Ciallo bite cue from replaying during the player's reel action. The physical rod-use path now distinguishes a confirmed new cast from reeling an active directly owned or associated fallback hook; only the new-cast path re-arms `FishingBiteSession`. The exact nearby `!!!` requirement and once-per-hook gate remain unchanged. Fishing is now an independent top-level settings category between Foraging and Hunting, and the eight-category sidebar adapts its row spacing on short layouts.

## Automated and artifact checks

- Java 25 `clean test build prepareRelease` completed successfully. Fresh XML reports 27 suites and 149 tests, with 0 failures, 0 errors, and 0 skips.
- Focused state tests confirm that an already-played hook remains blocked after a reel use, a confirmed new cast re-arms playback, a directly owned active hook is classified as reeling, and an active ownerless fallback hook is not mistaken for another cast.
- Settings tests confirm the exact eight-category order and verify that the compressed minimum-height sidebar keeps every category above the footer controls.
- English and Simplified Chinese resources each contain 450 keys with identical key sets and valid JSON.
- Expanded Fabric metadata declares `Beta-2.6.12+26.1.2`, client-only environment, Minecraft 26.1.2, Fabric Loader 0.19.3+, Fabric API 0.155.2+26.1.2+, and Java 25+; class files use major version 69.
- The binary still contains exactly 320 Shard textures, 320 Shard model definitions, and 320 Shard item definitions.
- Both binary and Sources JARs pass JDK 25 `jar --validate` and `unzip -t`; their `release` copies are byte-identical to `build/libs`.
- Static review confirms that the changed callback still returns the original use unchanged and contains no automatic cast/reel, input cancellation, click, movement, chat send, command, HTTP, or additional packet path.

## Validation boundary

This audit verifies compilation, the covered cast-versus-reel state transitions, settings ownership/order, short-layout geometry, language parity, client-only metadata, archive integrity, filenames, checksums, and build/release identity. It does not claim an authenticated Hypixel timing regression. Before wider publication, test one real water bite and one real lava bite: each should play once at `!!!`, remain silent on reeling, and re-arm on the next cast.

## SHA-256

- Binary JAR: `843787db14501266f0be693be62be6b894998a6b1b2ea6edb3f9daca78fef06b`
- Sources JAR: `8e73bcfb22040584738328df309c63c7fb127061411c236fc119ddbeb442e2d4`

---

# QCloudy_Addition Beta 2.6.11 Shard planner validation

Validation date: 2026-08-11

Validated artifacts:

- `release/QCloudy_Addition-Beta-2.6.11+26.1.2.jar`
- `release/QCloudy_Addition-Beta-2.6.11+26.1.2-sources.jar`

## Result

Beta 2.6.11 preserves the original 320-Shard recipe guide and adds a fully local multi-step planner. It provides fastest and cheapest route modes, Fusion Trees, Materials Only totals, alternative direct recipes, per-Shard acquisition-rate editing, a draggable Fusion Lines view, Kraken/Kuudra inputs, and a profile-scoped Shard warehouse assembled only from Hunting Box pages the player actually opens.

Normal-mode Bazaar calculations are optional. This build contains no Bazaar HTTP client and currently reads prices only through Skyblocker's public `ItemUtils.getItemPrice` API when a compatible Skyblocker version is already loaded. SkyHanni and Firmament are not dependencies and are not accessed through private fields; if no compatible public provider is present, price-based routes are visibly unavailable while Ironman and rate-based planning remain functional.

## Automated, data, and artifact checks

- Java 25 `clean test build prepareRelease` completed successfully. Fresh XML reports 27 suites and 146 tests, with 0 failures, 0 errors, and 0 skips.
- The packaged catalog contains 320 unique Shard IDs and 320 unique Bazaar IDs. Its 320 acquisition-rate entries match the catalog ID set exactly; all values are finite and non-negative.
- The binary contains 320 per-ID item models and 320 Shard texture resources. No stale suffixed duplicate resource survives the clean build.
- English and Simplified Chinese resources each contain 449 keys with identical key sets and valid JSON.
- Expanded Fabric metadata declares `Beta-2.6.11+26.1.2`, client-only environment, Minecraft 26.1.2, Fabric Loader 0.19.3+, Fabric API 0.155.2+26.1.2+, and Java 25+; class files use major version 69.
- Both binary and Sources JARs pass JDK 25 `jar --validate` and `unzip -t`; their `release` copies are byte-identical to `build/libs`.
- Static data-flow review confirms that the planner performs no HTTP request, automatic `/hb`, inventory click, Fusion, chat send, command, movement, packet, or hidden-server-data request. The warehouse parser accepts only exact visible `Owned: N Shard(s)` lore inside a screen titled `Hunting Box`.

## Validation boundary

This audit verifies compilation, planner calculations covered by tests, catalog/rate/resource completeness, language parity, client-only data flow, archive integrity, metadata, filenames, checksums, and build/release identity. It does not claim an authenticated Hypixel Hunting Box regression, visual approval at every GUI scale, compatibility with every future Skyblocker price API, or a full installed-modpack performance run. Before wider publication, open every `/hb` page on a real profile, compare several recorded counts, compare representative multi-step routes with the live Fusion preview, and test Normal mode once with and once without a compatible Skyblocker build.

## SHA-256

- Binary JAR: `12044c22054f9af08038e6569d95e043e013fc47f39621ec4b98b4a531f3a0a2`
- Sources JAR: `21b33ca81ae0d3359591a07c5c82b5805736c1ad2bd5d1e56cb2259aaca32fb2`

---

# QCloudy_Addition Beta 2.6.10 Tree Gift creature-alert validation

Validation date: 2026-08-11

Validated artifacts:

- `release/QCloudy_Addition-Beta-2.6.10+26.1.2.jar`
- `release/QCloudy_Addition-Beta-2.6.10+26.1.2-sources.jar`

## Result

Beta 2.6.10 fixes Tree Gift creature lines that were correctly recognized but discarded by the old ownership gate. The player-only `+N rewards gained!` summary now proves ownership without requiring one legacy contribution sentence. Exact creature rows are supported before or after that summary, in a single multi-line component, and for five seconds after a proven block's closing border. A nearby player's public creature line remains inert without the local player's summary.

## Automated and artifact checks

- Java 25 `clean test build prepareRelease` completed successfully. Fresh XML reports 25 suites and 137 tests, with 0 failures, 0 errors, and 0 skips.
- Eight focused session tests cover the normal personal block, nearby public rejection, buffered rewards, post-border creature delivery, post-border expiry, missing legacy contribution text, a complete multi-line block, and a compacted borderless multi-line value.
- Expanded Fabric metadata declares `Beta-2.6.10+26.1.2`, client-only environment, Minecraft 26.1.2, Fabric Loader 0.19.3+, Fabric API 0.155.2+26.1.2+, and Java 25+.
- Both binary and Sources JARs pass JDK 25 `jar --validate` and `unzip -t`; their `release` copies are byte-identical to `build/libs`.
- Static data-flow review confirms the changed session only consumes received chat components and hover text, deduplicates each loot within the bounded session, and contains no packet, chat send, command, click, movement, HTTP, or server-query path.

## Validation boundary

This audit verifies compilation, state-machine behavior, false-positive rejection in the covered message orders, metadata, archive integrity, filenames, checksums, and build/release identity. It does not claim an authenticated Hypixel Tree Gift regression. The remaining acceptance check is to earn a real Tree Gift creature and confirm one center-screen alert and one configured local sound, then stand beside another player's Tree Gift and confirm their public creature line stays silent.

## SHA-256

- Binary JAR: `25382321625a5be940e97ab0e42cd36d6a41ed6366f69f354170f979bb67ad99`
- Sources JAR: `3d6b8c8cf171e21e75be01e79965cd1124117ea0b90d73253d2693e12bc4a2cd`

---

# QCloudy_Addition Beta 2.6.9 lava-fishing sound validation

Validation date: 2026-08-11

Validated artifacts:

- `release/QCloudy_Addition-Beta-2.6.9+26.1.2.jar`
- `release/QCloudy_Addition-Beta-2.6.9+26.1.2-sources.jar`

## Result

Beta 2.6.9 fixes the missing bite cue on Hypixel lava-fishing casts whose Fishing Hook does not populate the local player's direct owner link. Directly owned water/lava hooks still take priority. A physical local fishing-rod use now opens a bounded 40-tick association window for one newly loaded local-owned or ownerless hook, while excluding every hook already present and every hook explicitly owned by another player. The existing exact nearby `!!!` marker and once-per-hook sound gate remain unchanged.

## Automated, resource, and artifact checks

- Java 25 `clean test build prepareRelease` completed successfully. Fresh XML reports 25 suites and 132 tests, with 0 failures, 0 errors, and 0 skips.
- Focused resolver tests cover direct-water-hook priority, a newly loaded ownerless lava hook, preference for a locally owned candidate, rejection of pre-cast and other-player hooks, reel/reset behavior, the 40-tick expiry, and the no-idle-scan state.
- English and Simplified Chinese resources each contain 378 keys with identical key sets and valid JSON.
- Expanded Fabric metadata declares `Beta-2.6.9+26.1.2`, client-only environment, Minecraft 26.1.2, Fabric Loader 0.19.3+, Fabric API 0.155.2+26.1.2+, and Java 25+.
- The binary JAR contains `FishingBiteAlert`, `FishingBiteSession`, `FishingHookResolver`, `assets/qcloudy_addition/sounds.json`, and `assets/qcloudy_addition/sounds/fishing/ciallo.ogg`.
- Both binary and Sources JARs pass JDK 25 `jar --validate` and `unzip -t`; their `release` copies are byte-identical to `build/libs`.
- Static data-flow review confirms that the item-use callback returns `PASS`, the broader hook query is inactive while idle, and the feature contains no automatic cast/reel, click, movement, command, chat, packet, HTTP, or audio-download path.

## Validation boundary

This audit verifies compilation, resolver behavior, resource presence, bilingual configuration, archive integrity, metadata, filenames, checksums, and build/release identity. It does not claim an authenticated Hypixel lava-fishing regression. Before wider publication, the owner should test one real water bite and one real lava bite, confirm the Ciallo cue occurs once in each case, and confirm redistribution rights for the supplied recording.

## SHA-256

- Binary JAR: `b3ebf47ef848f629782784b22ef14e6d7e03fb9bbe86bb0222a8ab725518e3e9`
- Sources JAR: `3d79108989509ffa1c02f4e663d33c067d1082f74b52741c2c52b4fb24e42e3f`

---

# QCloudy_Addition Beta 2.6.8 Fishing Bite Sound validation

Validation date: 2026-08-11

Validated artifacts:

- `release/QCloudy_Addition-Beta-2.6.8+26.1.2.jar`
- `release/QCloudy_Addition-Beta-2.6.8+26.1.2-sources.jar`

## Result

Beta 2.6.8 adds an opt-in Fishing Bite Sound under General > Fishing. It watches only the local player's own loaded Fishing Hook for Hypixel's exact nearby visible `!!!` ArmorStand and plays the bundled Ciallo OGG at most once per hook. The feature defaults off and has an independent continuous 0–100% volume slider at the project-wide 64% default.

## Automated, resource, and artifact checks

- Java 25 `clean test build prepareRelease` completed successfully. Fresh XML reports 24 suites and 127 tests, with 0 failures, 0 errors, and 0 skips; class files use major version 69.
- Focused tests verify once-per-hook playback gating, re-arming after the hook is gone or its entity ID changes, the `sounds.json` registration, and the bundled resource's OggS signature.
- English and Simplified Chinese resources each contain 378 keys with identical key sets and valid JSON.
- Expanded Fabric metadata declares `Beta-2.6.8+26.1.2`, client-only environment, Minecraft 26.1.2, Fabric Loader 0.19.3+, Fabric API 0.155.2+26.1.2+, and Java 25+.
- The binary JAR contains `assets/qcloudy_addition/sounds.json`, `assets/qcloudy_addition/sounds/fishing/ciallo.ogg`, and both fishing detector/session classes. The packaged audio is Ogg Vorbis stereo at 44.1 kHz and is loaded from QCA's own client resource pack; no separate pack or runtime download is required.
- Both binary and Sources JARs pass JDK 25 `jar --validate` and `unzip -t`; their `release` copies are byte-identical to `build/libs`.
- Static data-flow review confirms the detector scans only the four-block neighborhood of `Player.fishing`, plays a local sound, and contains no cast, reel, click, movement, command, chat, packet, HTTP, or texture/audio download path.

## Validation boundary

This audit verifies compilation, automated behavior, resource presence and format, bilingual configuration, archive integrity, metadata, filenames, checksums, and build/release identity. It does not claim an authenticated Hypixel timing/audio regression. Before wider publication, the owner should test one real bite at the intended GUI/audio settings and confirm redistribution rights for the supplied `Ciallo.mp3` recording.

## SHA-256

- Binary JAR: `e8806bfd92c6b4629e968dc636d3fc5e4af546d3b6361cb3a1237be83fdeb4e7`
- Sources JAR: `aa9491473810f148f7cb15522e2119317416b922ec59477213bdfd8f634abb01`

---

# QCloudy_Addition Beta 2.6.7 Dwarven map validation

Validation date: 2026-08-10

Validated artifacts:

- `release/QCloudy_Addition-Beta-2.6.7+26.1.2.jar`
- `release/QCloudy_Addition-Beta-2.6.7+26.1.2-sources.jar`

## Result

Beta 2.6.7 replaces the Dwarven Mines texture with the supplied one-layer 12-region map and recalibrates the marker projection to its image geometry. Dwarven projection now reads X/Z, yaw, and the already-visible sub-location only; Y is absent from both the projection API and fallback calculation.

## Automated, coordinate, and artifact checks

- Java 25 `clean test build prepareRelease` completed successfully. Fresh XML reports 123 tests, 0 failures, 0 errors, and 0 skips; class files use major version 69.
- The supplied `2000×2000` PNG (`cb714dc325ae4971088ade84846d9ad97af0e3966553d7d1f63931c3be1ef15a`) was resampled to the HUD's native `200×200` RGBA texture. The packaged texture hash is `639492c458d4acd232cf57fd250cf1d2548f4c07f95ca48bcc83a96417fb85c0` in source, binary JAR, and Sources JAR.
- Projection tests cover all 12 named regions on the replacement image, explicit sub-location selection, X/Z-only generic-location fallback between Royal Mines and Royal Palace, clamping at region bounds, and a coordinate grid that confirms every calibrated marker centre remains on opaque map content. Each region uses an inset bilinear X/Z calibration rather than a Y layer or a single global rectangle.
- Resource tests verify the 200×200 texture dimensions, transparent outside corner, and the exact fill colours for Village, Upper Mines, Rampart Quarry, Forge, Lava Springs, Cliffside, Far Reserve, Goblin Burrows, The Mist, Ice Wall, Royal Mines, and Royal Palace.
- Expanded Fabric metadata declares `Beta-2.6.7+26.1.2`, client-only environment, Minecraft 26.1.2, Fabric Loader 0.19.3+, Fabric API 0.155.2+26.1.2+, and Java 25+.
- Both binary and Sources JARs pass JDK 25 `jar --validate` and `unzip -t`; their `release` copies are byte-identical to `build/libs`.
- The Dwarven map path remains `assets/qcloudy_addition/textures/gui/dwarven_mines.png`; the map generator deliberately leaves this maintained supplied asset untouched.

## Validation boundary

This audit verifies source/configuration consistency, all 12 projection calibrations, automated behavior, archive integrity, metadata, filenames, checksums, and build/release identity. It does not claim an authenticated Hypixel visual regression. The replacement should still be checked in-game at the user's GUI scale and in each named region; any real-server offset report should include the displayed sub-location and player X/Z.

## SHA-256

- Binary JAR: `97d7a9df937075eb071a77bb80c700cf865a91eac909a8b7982aac4e57c895ef`
- Sources JAR: `55bc0b309c7faafab5f19bcbb434e22f0f69da70607b404083f826b9deea8905`

---

# QCloudy_Addition Beta 2.6.6 promotion validation

Validation date: 2026-08-10

Validated artifacts:

- `release/QCloudy_Addition-Beta-2.6.6+26.1.2.jar`
- `release/QCloudy_Addition-Beta-2.6.6+26.1.2-sources.jar`

## Result

Beta 2.6.6 promotes the reviewed Alpha 2.5.6 implementation without changing Java feature behavior. The pre-promotion Alpha 2.5.6 baseline and the renamed Beta 2.6.6 build both completed the full Java 25 test/build pipeline. The Beta change is limited to the release channel, version, artifact naming, and publication documentation.

## Automated, data, and artifact checks

- Java 25 `clean test build prepareRelease` completed successfully for the original Alpha 2.5.6 baseline and again after the Beta promotion. The final XML reports 120 tests, 0 failures, 0 errors, and 0 skips; class files use major version 69.
- The final playable artifact is exactly `QCloudy_Addition-Beta-2.6.6+26.1.2.jar`; the source artifact is exactly `QCloudy_Addition-Beta-2.6.6+26.1.2-sources.jar`.
- Expanded Fabric metadata declares `Beta-2.6.6+26.1.2`, client-only environment, Minecraft 26.1.2, Fabric Loader 0.19.3+, Fabric API 0.155.2+26.1.2+, and Java 25+.
- Both binary and Sources JARs pass JDK 25 `jar --validate` and `unzip -t`; their `release` copies are byte-identical to `build/libs`.
- The binary includes `LICENSE_QCloudy_Addition`, `THIRD_PARTY_NOTICES.md`, and `SHARD_DATA_NOTICE.txt`.
- The binary contains exactly 320 Shard PNGs, 320 item definitions, and 320 item-model definitions. The catalog/resource invariants remain covered by the passing test suite.
- English and Simplified Chinese resources each contain 373 keys with identical key sets and valid JSON.
- No Java source or runtime resource behavior changed between Alpha 2.5.6 and this Beta; required and optional dependencies remain unchanged.

## Validation boundary

This audit verifies source/configuration consistency, automated behavior, generated data invariants, archive integrity, metadata, filenames, checksums, and build/release identity. It does not claim a fresh authenticated Hypixel regression or pixel-level visual acceptance with every GUI scale, resource pack, operating system, and modpack. Beta status is an owner-approved testing channel, not a claim of official Hypixel approval or stable-release completeness.

## SHA-256

- Binary JAR: `0871774cfa47641d220d18d53f9235ee1b02ff2abfc9ac586dd2a55a0adbc2fd`
- Sources JAR: `2baa8c557826d2bdf69816576ba7891261d7cde48bdeb12fcf6ebcc480f75137`

---

# QCloudy_Addition Alpha 2.5.6 Shard details and semantic-colour validation

Validation date: 2026-08-10

Validated artifacts:

- `release/QCloudy_Addition-alpha-2.5.6-26.1.2.jar`
- `release/QCloudy_Addition-alpha-2.5.6-26.1.2-sources.jar`

## Result

Alpha 2.5.6 adds a dedicated Details view to all 320 Shards. It shows the Wiki-listed effect, semantic classification, and documented acquisition methods without replacing missing facts with guesses. Epic uses Minecraft's `§5` dark purple; stats, categories, mob types, acquisition methods, and rarities use their corresponding semantic colours. Clickable Shard names darken and underline only while the visible text is hovered. Recipes remain indexed independently from natural acquisition: Queen Bee, for example, keeps its Honeyhive/Honeycomb Collection acquisition details and also exposes every verified ordered Fusion recipe that can produce it.

## Automated, data, and artifact checks

- Java 25 `clean test build prepareRelease` completed successfully. Fresh XML reports 120 tests, 0 failures, 0 errors, and 0 skips; class files use major version 69.
- The catalog contains 320 unique Shard IDs, names, Bazaar IDs, internal IDs, detail records, and Shard-specific icon resource sets. Rainbug/L49 is absent.
- Every Shard has a non-empty effect and acquisition display. The current Wiki tables provide a documented acquisition for 319 catalog Shards; Wild Hog is the only current table gap and is explicitly labelled as not documented instead of receiving a fabricated source.
- Gemzie is regression-tested as Epic, with `+0.25–2.5 Gemstone Spread`, a yellow Gemstone Spread label, and the Critter Capsule/Cavern Biome capture source. Defense and Animal/Aquatic semantic colours are covered by catalog tests.
- Pandarai is regression-tested as Fusion-only. Queen Bee is regression-tested as having both natural acquisition data and non-empty reverse Fusion recipes. The same reverse index powers the Recipes view for every possible output Shard.
- Search is regression-tested across canonical name, ID, family/category metadata, effect text, acquisition text, and mob type. Generated detail text contains no residual Wiki templates, links, HTML tags, or bold markers.
- English and Simplified Chinese resources each contain 373 keys with identical key sets. The bundled `SHARD_DATA_NOTICE.txt` and third-party notices document the Wiki-data and icon-source licences.
- Both binary and Sources JARs pass JDK 25 `jar --validate` and `unzip -t`; their `release` copies are byte-identical to `build/libs`.
- Metadata is client-only and declares `alpha-2.5.6-26.1.2`, Minecraft 26.1.2, Fabric Loader 0.19.3+, Fabric API 0.155.2+26.1.2+, and Java 25+.
- Static inspection finds no Shard-guide runtime HTTP/API client, packet send, chat/command send, inventory click, Fusion action, or automation. Wiki/API data generation happens offline before packaging.

## Validation boundary

This pass validates source, generated data, unit tests, build outputs, archive integrity, and static client-only boundaries. It does not claim an authenticated Hypixel regression or pixel-level acceptance at every GUI scale/resource pack. Those live checks remain required before promoting this alpha to beta or release. The 2.5.5 report below remains historical evidence only.

## SHA-256

- Binary JAR: `d4ed9ba609a64787b4de247f6561c1e5d1961f8359bdf9f25df3ba053a9b82ce`
- Sources JAR: `13251eaafe50c00ab4f10554dd8ca1b78dca6d65ca011191d5d5f7ffbf41fca0`

---

# QCloudy_Addition Alpha 2.5.5 Shard icon and interaction validation

Validation date: 2026-08-10

Validated artifacts:

- `release/QCloudy_Addition-alpha-2.5.5-26.1.2.jar`
- `release/QCloudy_Addition-alpha-2.5.5-26.1.2-sources.jar`

## Scope

Alpha 2.5.5 replaces the amethyst fallback with a bundled, Shard-specific icon for every one of the 320 catalog IDs, while keeping an already-received native ItemStack as the session-cached priority. It also releases search focus on outside click, `Esc`, or `Tab`, restores focus on a direct search-field click, and centers each input pair/output set as a compact group whose hitboxes follow the rendered bounds. Six pairs intentionally share the same PNG because the reviewed upstream Shard icon set gives those Shards the same in-game appearance.

## Automated, data, and artifact checks

- Java 25 `clean test build prepareRelease` completed successfully. Fresh XML reports 116 tests, 0 failures, 0 errors, and 0 skips across 22 suites; class files use major version 69.
- The 320 catalog IDs, 320 bundled Shard PNGs, 320 item models, and 320 item definitions have exactly equal ID sets. Every PNG decoded successfully, every dimension is between 16 and 64 pixels with alpha, and Rainbug/L49 is absent.
- The generic amethyst fallback was removed. Static inspection confirms that an already-received native Shard ItemStack is cached by Shard ID for the session and takes priority over the bundled local model; the fallback itself is a Shard-specific `PLAYER_HEAD` with an overrideable `qcloudy_addition:shards/<id>` model.
- Regression tests cover search-focus exit keys, compact input/output geometry at wide and constrained widths, catalog/icon completeness, recipe invariants, and responsive-layout bounds. The outside-click/refocus branches and rendered-hitbox wiring were also inspected directly.
- Both renumbered binary and Sources JARs pass JDK 25 `jar --validate` and `unzip -t`. A full extracted-payload comparison against the corresponding pre-renumbering archives confirms that only `fabric.mod.json` version metadata changed.
- Metadata is client-only and declares `alpha-2.5.5-26.1.2`, Minecraft 26.1.2, Fabric Loader 0.19.3+, Fabric API 0.155.2+26.1.2+, and Java 25+.
- English and Simplified Chinese resources both contain 362 keys with identical key sets. `git diff --check`, JSON parsing, and a static scan of the Shard package for network clients, packets, commands, chat, inventory clicks, fusion automation, and the removed amethyst fallback found no match.
- A fresh combined development-client smoke launch initialized QCloudy_Addition alongside BabyzombieAddons, Firmament, Skyblocker, SkyHanni, and Mod Menu, completed the combined resource reload, created the item atlas, and started the sound engine. The log contains no missing or failed `qcloudy_addition:shards/*` model/texture load and no QCloudy exception. The observed errors came from the unauthenticated development account and SkyHanni rejecting current NEU constants (`HUNTING_FORTUNE` and `FISHING_NET`), not from QCloudy_Addition.

## Validation boundary

This pass includes a fresh combined initialization/resource smoke launch, but it does not claim an authenticated Hypixel regression or an in-game pixel-level acceptance check at every GUI scale/resource pack. Those live visual and server checks remain required before promoting this alpha to beta or release. The 2.5.4 report below remains historical evidence only.

## SHA-256

- Binary JAR: `b7ca1fa7477e31f86bd4f97c045e17238d3a7920138ebe6364d1a63689042f56`
- Sources JAR: `ba050233e7dabe0ee8c65d5784f38ca40fec8ca00e6aac446a0c33d539f09095`

---

# QCloudy_Addition Alpha 2.5.4 Shard Fusion validation addendum

Validation date: 2026-08-10

Validated artifacts:

- `release/QCloudy_Addition-alpha-2.5.4-26.1.2.jar`
- `release/QCloudy_Addition-alpha-2.5.4-26.1.2-sources.jar`

## Result

The standalone, client-only Shard Fusion guide is included in Alpha 2.5.4. It provides JEI-inspired search, Recipes and Uses views, ordered-input swapping, one-to-three output slots, responsive narrow-screen layouts, bilingual UI labels, and resource-pack-aware observed item icons without depending on JEI or another SkyBlock mod. The runtime reads only its bundled, versioned catalog and client-visible item data; it performs no Wiki/API request, packet send, inventory click, chat send, command send, or fusion automation.

## Automated, data, and artifact checks

- Java 25 clean testing passed 108 tests with zero failures, errors, or skips.
- Java 25 `./gradlew clean build prepareRelease` completed successfully; the new class files use major version 69.
- The catalog contains 320 unique Shard IDs, names, and Bazaar IDs and exactly matches the 320 `SHARD_*` products in the reviewed official Bazaar snapshot. Anteater, Zombuddy, Troodon, Goldolot (`R92`), and Ghost Crab are present; Rainbug is absent.
- Fusion invariants, ordered input behavior, first-input quantities, Chameleon stepping/exclusions, reverse Recipes/Uses indexes, shared recipe-instance indexing, and separate same-Shard ID/Special output slots are covered by tests.
- Both renumbered JARs pass JDK 25 `jar --validate` and `unzip -t`. A full extracted-payload comparison against the corresponding pre-renumbering archives confirms that only `fabric.mod.json` version metadata changed.
- Metadata is client-only and declares `alpha-2.5.4-26.1.2`, Minecraft 26.1.2, Fabric Loader 0.19.3+, Fabric API 0.155.2+26.1.2+, and Java 25+.
- English and Simplified Chinese resources both contain 362 keys with identical key sets.
- `git diff --check`, JSON parsing, and static scans of the new Shard code completed without an error.

## Validation boundary

This addendum does not claim an authenticated Hypixel regression, pixel-level acceptance at every GUI scale/resource pack, or a fresh combined launch with all four supplied reference mods. Those live checks remain required before promoting the alpha to beta or release. The older 1.5.1 report below is retained as historical evidence and must not be read as a 2.5.4 live-test result.

## SHA-256

- Binary JAR: `4a26801c3d63cfb2cf4ae10f0249efd761fe6e1264caedb239133e9a698fb773`
- Sources JAR: `a2a3232c5d6342da89037225e3ec78302d8a0910a72c3cbe56a313f409720025`

---

# QCloudy_Addition 1.5.1 release validation

Validation date: 2026-08-06

Validated artifacts:

- `release/QCloudy_Addition-1.5.1+26.1.2.jar`
- `release/QCloudy_Addition-1.5.1+26.1.2-sources.jar`

## Result

The 1.5.1 release adds a player-clicked reconnect button, spatial Beeheemoth sound controls, and a bounded personal Tree Gift alert state machine that remains observable when a chat-compaction mod cancels normal display. It passes local source, unit-test, archive, reproducibility, standalone initialization, and a fresh supplied-reference-mod compatibility launch for Minecraft 26.1.2. This is a release-readiness result, not a claim that every live Hypixel entity, message, sound, or screen was exercised on an authenticated account.

## Exact environment

- Minecraft 26.1.2
- Fabric Loader 0.19.3
- Fabric API 0.155.2+26.1.2
- Eclipse Temurin Java 25.0.4; class-file major version 69
- Gradle Wrapper 9.6.1
- Fabric Loom 1.17.17

## Automated and artifact checks

- 98 JUnit tests passed across 23 suites, including the real Helia overview/detail layouts, bounded Tab/scoreboard Chapter parsing, rejection and repair of cached `SB Level` false tasks, official multi-day Benefactor donation chat, Tab countdown and inactive Temple menu blocks, strict local-player Tree Gift ownership and bonus-block buffering, personal-versus-teammate Loot Share Wumpa capture policy, Snoozle mixed-material/size boundaries, all four Safari Milestone layouts, account/profile normalization, Chapter-switch isolation, Cold campfire eligibility, the complete 37-Critter rarity table, all eight Wumpa prerequisites, Warden cooldown boundaries, Tree Protection Order countdown, Lasso REEL parsing, Beeheemoth sound-path isolation, manual reconnect address reconstruction, the scale-9 Beeheemoth signature, unique category ownership, Hunting defaults/settings routing, and all 16 official Fairy Soul coordinates.
- Java compilation enables deprecation lint and completes without a warning; the Snoozle scanner uses `ClientLevel.hasChunk(chunkX, chunkZ)` so it retains the already-loaded-chunk boundary without the retired `hasChunkAt` API.
- Two final Java 25 `clean test build` runs produced byte-identical binary and Sources JARs.
- JDK 25 `jar --validate` and `unzip -t` passed for both final artifacts.
- Binary and Sources metadata contain version `1.5.1+26.1.2`.
- The release-directory copies are byte-identical to the final `build/libs` artifacts.
- Both artifacts contain `LICENSE_QCloudy_Addition` and `THIRD_PARTY_NOTICES.md`.
- No reference-mod class, test class, legacy pet-icon PNG directory, `PetIconRegistry`, or removed lair-finder implementation is present in either release JAR.
- Static inspection found no `sendChat`, HTTP, WebSocket, packet-sender, automatic movement, or chunk-request code.
- The only outbound command payloads are the documented, physically user-triggered Storage navigation commands (`storage`, `enderchest <1-9>`, and `backpack <1-18>`), `/th` payload `warp torrhus`, and `/helia` payload `chapter torrhus`.

## Launch and compatibility matrix

| Instance | Result |
|---|---|
| QCA 1.4.6 + Fabric/API only | Standalone Loom launch loaded 51 modules, initialized QCA, reloaded resources, and started the sound engine without Firmament or another SkyBlock mod |
| QCA 1.4.6 + four supplied reference mods | A fresh 94-mod launch with BabyzombieAddons 3.4.1, SkyHanni 7.41.0, Skyblocker 6.8.2, Firmament 44.3.0, and Mod Menu 18.0 initialized QCA, all reference mods, combined resources, and the sound engine without a QCA exception |
| QCA 1.4.9 transparent-icon delta | The selected original emblem was retained, converted to 128×128 RGBA with transparent corners, and included in two byte-identical clean builds; metadata, class version 69, archive integrity, tests, and release-copy hashes passed |
| QCA 1.5.0 + Fabric/API | Standalone Loom loaded 51 modules; QCA initialized, resources and sound engine completed, and no QCA exception appeared before the client was intentionally stopped after the main-menu load |
| QCA 1.5.1 + Fabric/API | Standalone Loom loaded 51 modules; QCA initialized, resources and the sound engine completed, and no QCA exception appeared before the client was intentionally stopped after the main-menu load |
| QCA 1.5.1 + four supplied reference mods | A fresh 94-mod launch with BabyzombieAddons 3.4.1, SkyHanni 7.41.0, Skyblocker 6.8.2, Firmament 44.3.0, and Mod Menu 18.0 initialized QCA, combined resources, and the sound engine; it remained alive for about 33 minutes and stopped cleanly. A second launch after the final loaded-chunk API cleanup reached the same initialization boundary. Neither run reported a QCA or mixin-injection exception |

The combined instance warnings/errors concerned reference-mod refmaps/resources, an optional ModernUI class, missing BabyzombieAddons custom-disc files, SkyHanni/Skyblocker remote-repository requests, unauthenticated profile/Realms activity, and SkyHanni 7.41.0 rejecting the current NEU-repository constants `HUNTING_FORTUNE` and `FISHING_NET`. They were not thrown from QCA and did not stop client/resource/sound initialization. QCA has no Firmament runtime dependency; its optional duplicate-feature handoff checks only whether the mod id is loaded and leaves QCA fully available when Firmament is absent.

## Final integrity fixes

- Replaced the previous mod icon with the selected original cloud-ring/orange-core/cyan-locator emblem. Only the background was converted to alpha; the subject was not redrawn. The shipped PNG is 128×128 RGBA, all four corner alpha values are zero, and a 32×32/checkerboard preview remains legible without a black square.
- Added the categorized Torrhus Canyon and Critter Safari module: combined Chapter/resource HUD, Miria Contest calculations inside that HUD, Critter behavior guidance, Benefactor status, configurable Tree Gift alerts, Safari run/biome Critterdex dashboard, Sparkling alerts/highlight, Floor Drop and Quest Item assistance, Wumpa encounter state, and Safari Belt milestone tooltip rows. QCA neither modifies the right scoreboard nor duplicates its contest timer.
- Added a default-on, separately switchable Tree Critter Timer to the combined Torrhus HUD. Every 10 client ticks it strictly parses the nearest loaded `Critter in: <duration>` entity display name, following SkyHanni's passive tree-progress acquisition pattern; it never guesses which Pot was used or starts a synthetic timer. This covers all four currently indexed Pot of Honeycomb sizes and server-applied speed/instant-attraction modifiers without hard-coded drift.
- Removed Safari Critter/Sparkling outline assignment from Armor Stand backed capture props. The prior marker-state workaround still allowed the support body to enter the outline pass on this renderer version; the safe fallback now excludes those stands entirely while preserving rarity/configured outlines for real non-Armor-Stand Critter entities. The entity itself remains untouched.
- Fixed intermittent Critter Behavior replay after a Lasso capture. Removed entities are excluded, and an exact received `CAPTURE! You caught ...` confirmation now suppresses only that captured behavior-Critter name for three seconds; other Critter types remain promptable and normal same-type behavior resumes after the bounded window.
- Added a default-on Beeheemoth helper using the supplied BabyzombieAddons scale-9 Bee signature. Its vanilla outline color uses QCA's RGB/HSV picker; a fixed yellow first-observed-position beacon dismisses on a 10-block approach, the player's exact capture confirmation, or entity disappearance and cannot respawn for the same UUID after dismissal.
- Added a separate default-on Beeheemoth sound control with a 64% default volume. It scales only non-relative Bee event/resolved sounds within 12 blocks of the loaded scale-9 Beeheemoth or its just-observed position; unrelated Bees and all other sounds are unchanged. Disabling this sub-option makes only matching Beeheemoth sounds silent.
- Added a separate default-on Lasso REEL sound at 64% volume. It uses SkyHanni's local-player leash plus nearby exact-ArmorStand relation and plays only on the false-to-true REEL transition; the secondary settings page exposes a continuous 0–100% volume slider without a redundant enable switch.
- The Hunting parser uses anchored or bounded formats and only locally received scoreboard, Tab, chat, title, entity-name, inventory, and already-loaded block-state data. The module contains no new command, chat, network, inventory-click, movement, combat, or interaction sender.
- All Hunting alerts use the shared center-title rendering path, while each alert feature owns its own sound switch and continuous 0–100 volume slider defaulting to 64%. The General sound switch is master mute only. Long task and Critter names wrap; no ellipsis fallback exists in the Hunting renderer.
- The official list of 37 Safari Critters, quest items, contest thresholds, ticket tiers, and documented behavior were covered by local parser/config tests. Safari Belt bonus values are deliberately read from received lore instead of hard-coded totals.
- Fixed Safari Belt Milestones by using one contextual parser for the already-open Milestone menu and the belt tooltip. Combined rows and split title/lore rows now populate Cavern, Forest, Haunted, and Icy independently; locked entries and capture fractions are rejected. The four confirmed levels are stored per Minecraft account/SkyBlock Profile and update only on a higher observed level.
- Added account/profile-scoped persistence for received Forest/Desert Whispers, Forest/Safari Essence, Forest Fortune, Sweep, Helia Chapter/task/progress, and Safari Belt Milestones. Repeated Tab, scoreboard, and menu snapshots are treated as absolute values and do not accumulate; only exact received chat gain messages are additive. Switching to a newly observed Chapter clears stale previous-task fields.
- Repaired Helia Chapter acquisition by parsing Tab and scoreboard independently, recognizing the actual Chapter overview/detail inventory shapes, joining only a four-second/12-line received-chat block, and removing previously cached non-Chapter tasks such as `SB Level`. Repaired Benefactor acquisition from bounded Tab/scoreboard blocks, Forest/Desert Temple menus, and the official donation chat form; day units, same-temple extension, cross-temple replacement, stale-menu protection, expiration, and account/profile persistence are covered.
- Safari Essence was removed from the Safari Dashboard and is now shown only in the Torrhus resource section, with an independent Torrhus toggle.
- Added ordered, configurable Cold warnings (strictly above 80/90 by default), a dedicated default-on 64% Cold-alert sound setting, and a nearest-loaded-campfire red beacon. The first above-threshold observation now triggers an immediate scan and the active state refreshes every 40 ticks; the beacon stops immediately when the next received Cold value falls.
- Added a one-shot Doomspiral readiness warning at the Wiki-documented requirement of at least four `Soothing Incense`, default-off red Wumpa motion/collision projection, default-off pink beams at the 12 Torrhus and four Safari Fairy Soul coordinates, and default-on outlines for all 37 capturable Critters using their official Shard rarity colors.
- Added a default-on Warden capture-ready alert in the bounded Doomspiral arena. It follows the supplied BabyzombieAddons 140-client-tick rule, compensates with received local-player latency, rejects emerging/digging poses, and uses a dedicated center alert plus default-on 64% sound without sending a capture action.
- Split Wumpa party prerequisites from the personal Safari Critterdex. Anchored personal captures and received `LOOT SHARE ... catching a <Critter>` teammate confirmations update the eight-item Wumpa set, while Loot Share remains excluded from personal Critterdex rows. Once spawned, the checklist is replaced by `Wumpa: Spawned` plus live phase. Movement/projection resolves the actual Ravager body near the Wumpa label and uses short movement/stillness confirmation windows; 8/8 and massive-footsteps/awoken still share one per-run alert flag.
- Added a separate default-on Snoozle Wall Overlay feature in the Safari category. A once-per-second bounded scan checks only already-loaded nearby blocks, accepts small connected components containing both Wiki-documented `Cobbled Deepslate` and `Tuff`, and submits translucent quads only on air-exposed faces. Oversized formations and single-material patches are rejected; the default green color uses the standard RGB/HSV picker.

- The two supplied 2026-08-04 crash ZIPs were byte-identical (`8abff84c45b6b2ecb8ffada8de514a446755c70fc2d1ff6f853d47a24811a5d7`) and identify one QCA 1.2.5 Storage-cache failure: a cached Efficiency enchantment Holder belonged to an older dynamic registry set and was serialized on the render thread without an exception boundary. QCA now detects registry replacement, rebinds normal/stored enchantments by resource key, isolates load/search/hash/encode/write failures per item, preserves a failed item's slot as empty, and prevents any Storage snapshot encoding failure from escaping to the render thread. See `CRASH_ANALYSIS_2026-08-04.md`.
- Removed the catch-all `ALL` settings category. Foraging, Hunting, and Safari now have separate sidebar categories with a single enum owner for every card: Torrhus/tree progression lives in Foraging, cross-island capture utilities live in Hunting, and Critter Safari systems live in Safari. No feature is registered in more than one category. The combined HUD gear routes to Foraging in Torrhus, Safari in Critter Safari, and Hunting elsewhere.
- Collapsed the old two-tab header into one `Features` tab. `General` is now the first sidebar category and contains `UI animations` plus the alert-sound master mute; the old appearance/layout sidebar and duplicate layout card were removed because the bottom-left `Edit HUD` button already opens the loaded-HUD editor.
- Removed secondary-page feature switches and empty secondary pages. Left-click on a feature card is the only feature toggle; right-click opens only meaningful feature-specific settings.
- Every HUD background color picker now has an explicit Transparent preset in addition to RGB selection.
- Registered `/th` as a client command with no setting or disable path; a physical `/th` input sends exactly `warp torrhus` unless another client command already owns the root name.
- Registered `/helia` as a client command with no setting; a physical `/helia` input sends exactly `chapter torrhus` unless another client command already owns the root name.
- Reworked Tree Gift alerts into a 15-second, border-bounded received-chat block. The local player's exact `+N rewards gained! (hover)` summary remains sufficient for its own `SHOW_TEXT`; separate percentage and `A <loot> fell from the Tree!` bonus rows become eligible only when the same block also contains the Tree Gift header, the local `You helped cut...` contribution line, and the personal reward summary. Early bonus rows are buffered until that ownership proof arrives, duplicate loot is emitted once, public/nearby-player blocks and lasso messages are rejected, and `GAME_CANCELED` observation preserves the parser when another client mod compacts the visible chat.
- Added a default-on manual reconnect card in General and a vanilla-width button on the disconnect screen. It remembers only the current session's last explicit multiplayer target and resource-pack preference; one physical button click starts one normal Minecraft connection attempt. It has no timer, automatic loop, server bypass, persisted address, chat payload, or command payload.
- Fairy Soul success and already-found confirmations now hide the nearest listed Soul within 10 blocks immediately and persist that island-coordinate key per received SkyBlock profile. A failed/unconfirmed click does not hide a waypoint.
- Removed every feature card's duplicate top-right switch and bottom-right right-click label. Left-click still toggles the feature, the left blue strip remains the enabled-state indicator, and right-click still opens the complete secondary settings page.
- The custom search frame and vanilla borderless `EditBox` now use separate but shared geometry: the editable text baseline is vertically centered from the real font line height, horizontal padding is symmetric, the complete visible frame remains clickable, and navigation tabs shrink before they can overlap the field on narrow GUI widths.
- QCA hotkeys now edit inline on their existing feature-settings page. Keyboard and mouse buttons, including buttons 1–5/side buttons, support modifier chords. `Esc` clears the active row to unbound, and the removed `KeyChordScreen` is absent from the source and release. Runtime paths were kept for mouse-bound Open Settings and Chat Peek.
- Completely removed the Golden Dragon/Dragon's Lair finder from config, feature cards, HUD types, renderer, scanner, translations, and release artifacts. The text `Dragon's Lair` may still occur only as an ordinary Crystal Hollows location name used by island classification.
- Pet held-item details confirmed from the received Pets menu, Tab widget, or chat are retained per pet in QCA's own config. A max-level pet hides only the redundant progress-to-max line; it does not suppress the held-item row.
- Replaced the generated PNG fallback path with normal verified player-head profiles. QCA writes no synthetic `petInfo`, so external item-model predicates cannot replace the HUD icon with an unrelated orb or pet model.
- Generated metadata contains 88 base profiles, 352 skin profiles, 5,422 pet-owned current/animated texture mappings, and 87 accessory definitions. Baby Spinosaurus has 60 recognized current/animation textures assigned to its exact skin family.
- Legacy pet PNG folders and `PetIconRegistry` are excluded from both binary and Sources JARs and cannot be selected at runtime.
- Pet text, held-item text, commission names, and progress values use complete measured width, including bold style; no ellipsis fallback is used.
- All adjacent UI, map, Storage, key-chord, middle-click, Chat Peek, teleport sound, and slider behavior remains covered by the existing test/build and launch checks.

## Remaining live-test boundary

No authenticated Hypixel account was available in the local instance, and SkyBlock was under maintenance during this validation. The desktop UI controller also could not attach to Loom's unbundled Java process, so the reconnect button was verified against the exact 26.1.2 `DisconnectedScreen` layout and mixin target, configuration/unit tests, archive contents, and client initialization, but not by an automated pixel-level click-through. Real Torrhus/Safari messages and entity states, Tree Gift ownership/order variants, Beeheemoth spawn/capture sound events, teammate Loot Share capture sequencing, Wumpa Ravager/name-carrier association and projected line accuracy, the exact Snoozle wall component geometry/overlay appearance, Armor Stand capture-prop suppression, Tree countdown labels, Lasso timing and sound feel, Cold text variants and campfire selection, Doomspiral Warden timing, Fairy Soul beams, live widgets, the user's resource pack, pet transitions, Ender Dragon outlines, GUI-scale combinations, reconnect-screen appearance, and physical input feel still need an in-game user regression. The launch check establishes initialization, not screenshot-level correctness or a zero-bug/zero-anti-cheat-risk guarantee. See `COMPLIANCE.md`.

## SHA-256

- Binary JAR: `e3d3131d4f1d40e7859b655aed56aa72ef9a5dae2bd045710d4bde9daf705536`
- Sources JAR: `ab825c382b6f672cfc6ce2381db0a904ea60b23e593fa5254bd7e87722442ada`

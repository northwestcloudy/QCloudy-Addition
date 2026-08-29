# Release 0.3.9 publication checklist

> This file preserves the Release 0.3.9 publication baseline. Release 0.3.9 did not contain the Release-update checker. Checker-specific items below are forward-looking requirements for the first later Beta/Release that contains it and must not be used to claim that the published 0.3.9 JARs already provide update notices.

## Shared metadata

| Field | Value |
|---|---|
| Version | `0.3.9` |
| Channel | Release / stable |
| Loader | Fabric |
| Minecraft | 26.1.2 and 26.2 |
| Java | 25 |
| Environment | Client only |
| License | LGPL-3.0-or-later |
| Required | Matching Fabric API |
| Optional | Mod Menu; SkyHanni, Skyblocker, Firmament, BabyZombieAddons, Feesh |

Suggested title: `QCloudy_Addition 0.3.9 Release`

Suggested summary: `Client-only Hypixel SkyBlock maps, content-aware HUDs, pets, alerts, Century Cake timers, and an offline 320-Shard Fusion Lab for Fabric.`

## Mandatory experimental warning

Place this near the top of GitHub, Modrinth, Wiki, and website release copy:

> Unified Settings Editor and Unified HUD Editor are experimental concept tests. They are disabled by default and are not yet stable. Back up provider configuration and verify changes in each provider's native editor.

## Fresh verification

- Run `bash tools/build_all_versions.sh` with Java 25 after every release-affecting change.
- Require all tests and both target builds to pass.
- Validate all four archives with `jar --validate` and `unzip -t`.
- Inspect `fabric.mod.json` in both playable JARs for `0.3.9`, the correct Minecraft range, and the expected Fabric API dependency.
- Inspect packaged `fabric.mod.json` for `contact.homepage`, `contact.sources`, and `contact.issues`, and for `custom.modmenu.links` entries for Website, Downloads, and Source. Confirm HMCL exposes the official page from `contact.homepage` and Mod Menu shows every project link.
- For the first later eligible Beta/Release, verify the packaged Release-check build metadata: Alpha must exit before scheduling/network access; Beta and Release must make at most one manifest request per client process. A Beta embeds the currently published stable `releaseSequence`; a new Release embeds the exact new sequence that will be published for that same Release, so it can never notify the player about itself.
- For that later eligible build, test the checker with newer Release, equal/older sequence, Beta/Alpha channel, malformed JSON, wrong Minecraft, Sources-only asset, invalid SHA-256, untrusted URL, duplicate matching asset, redirect, non-200, timeout, and oversized-response fixtures. Only the valid newer unique matching Release may produce the one toast and local chat message.
- For that later eligible build, confirm the toast/chat links are exactly `https://qcloudy.net/download/` and `https://qcloudy.net/changelog/`, and that no path downloads, installs, replaces, or launches a JAR.
- Verify exact catalog/model/texture sets for all 320 Shards and confirm Rainbug is absent.
- Recalculate SHA-256 from the final copied files and record it in both validation reports and the website manifest.
- Perform standalone smoke testing and, before calling the experimental editors stable, repeat authenticated five-provider and multi-GUI-scale testing. Do not imply that local automated tests prove live compatibility.

## Files to publish

- `release/QCloudy_Addition-0.3.9+26.1.2-Release.jar`
- `release/QCloudy_Addition-0.3.9+26.1.2-Release-sources.jar`
- `release/QCloudy_Addition-0.3.9+26.2-Release.jar`
- `release/QCloudy_Addition-0.3.9+26.2-Release-sources.jar`

Only playable JARs belong in users' `mods` folders. Sources JARs are optional developer attachments.

## GitHub Release

- Tag: `v0.3.9`
- Title: `QCloudy_Addition 0.3.9 Release`
- Body: `docs/GITHUB_RELEASE_0.3.9.md` (optionally append/link the Chinese companion).
- Attach all four files above.
- Do **not** mark the release as a pre-release.
- After publishing, download both playable assets once and compare their hashes with `docs/VALIDATION.md`.

## Modrinth

- Project description: `docs/MODRINTH_DESCRIPTION.md`.
- Version changelog: `docs/MODRINTH_RELEASE_0.3.9.md`.
- Version type: **Release**.
- Upload the two playable JARs as two Minecraft-version files under the same version.
- Mark matching Fabric API as required and Mod Menu as optional; all provider mods remain optional.
- Set client environment required and server environment unsupported.

## Website

- Deployment package: `release/QCloudy_Addition_Website-0.3.9-20260825.zip` (website only; do not attach it as a mod file).
- Upload the contents of the final `website/` package to the current site root; do not upload the ZIP as a public page.
- Confirm `/`, `/download/`, `/features/`, `/compliance/`, and `/changelog/` load directly and after refresh.
- Confirm the download page shows only Release 0.3.9 and uses the exact GitHub release-asset URLs, sizes, and SHA-256 values.
- Before the first eligible Beta, fetch and validate the existing live `/assets/data/release-manifest.json`, then embed that currently published stable `releaseSequence` in the Beta; publishing a Beta must not replace or increment the stable manifest. Before a new Release, choose the next positive monotonic sequence, build every Release JAR with that same sequence embedded, publish the matching GitHub assets, and only then deploy the manifest with that exact sequence, `channel: "Release"`, exact `v<version>` tag, and one exact playable asset per supported Minecraft version.
- Confirm an ordinary Release-manifest request is the only QCA-owned runtime web request. The disclosure must state no identifiers, telemetry, mod list, gameplay data, token, cookie, or automatic download, while acknowledging normal IP/User-Agent exposure.
- Confirm English/Chinese switching, mobile layout, repeatable reveal animations, FAQ animations, icon aspect ratio, and navigation highlighting.

## GitHub Wiki

- Publish `wiki/Home.md`, `wiki/Home-zh-CN.md`, and `wiki/_Sidebar.md` to the separate `QCloudy-Addition.wiki.git` repository.
- Confirm both language pages show Release 0.3.9, both Minecraft targets, the cumulative changes since 2.5.3, and the experimental-editor warning.

## Source repository

- Review `git status --short` and stage only intended project files; do not include local maps, PSDs, extracted packs, caches, runtime folders, or reference JARs.
- Commit the Release change separately from any unrelated work.
- Push source with a normal branch push. Tagging and release-asset publication are separate actions.

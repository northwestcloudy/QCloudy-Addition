# Beta 0.4.9 publication checklist

## Shared metadata

- Version: `0.4.9`
- Channel: Beta / pre-release
- Tag: `v0.4.9-beta`
- Minecraft: 26.1.2 and 26.2
- Loader: Fabric
- Environment: client only
- Java: 25
- Stable update baseline: Release sequence `1`

## Verification

- Run `bash tools/build_all_versions.sh` with Java 25.
- Require both target test/build runs to pass.
- Validate the exact four 0.4.9 Beta archives with `jar --validate` and `unzip -t`.
- Confirm playable metadata declares `0.4.9-beta+<minecraft>`, client-only, Java 25, and the exact target/Fabric API dependency.
- Confirm Mod Menu contains Website, Downloads, and Source Code links, and top-level contact contains homepage, sources, and issues.
- Confirm the packaged release properties declare `Beta`, version core `0.4.9`, the exact Minecraft target, and baseline sequence `1`.
- Recalculate file sizes and SHA-256 after the final build.
- Keep authenticated Hypixel, HMCL, full-provider-modpack, and visual GUI-scale testing clearly separate from automated verification.

## GitHub

- Push the explicit source-only release commit to `main`.
- Create tag `v0.4.9-beta` on that commit.
- Create `QCloudy_Addition 0.4.9 Beta` as a GitHub **Pre-release**.
- Use `docs/GITHUB_RELEASE_0.4.9_BETA.md` as the main body.
- Attach the two playable and two Sources JARs only.

## Modrinth

- Version type: Beta.
- Loader: Fabric.
- Client: required; server: unsupported.
- Use `docs/MODRINTH_RELEASE_0.4.9_BETA.md` as changelog.
- Upload the 26.1.2 playable JAR to Minecraft 26.1.2 and the 26.2 playable JAR to Minecraft 26.2.
- Mark matching Fabric API required and Mod Menu optional; provider mods remain optional.

## Website

- Prepare matching Beta 0.4.9 download/changelog content separately from the source commit.
- Do not replace or increment the stable Release 0.3.9 manifest; Beta is not an update-check target.

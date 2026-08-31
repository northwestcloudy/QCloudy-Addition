# QCloudy_Addition 0.3.10 Beta

Public client-only Fabric Beta for Minecraft 26.1.2 and 26.2. This release contains every completed change since stable Release 0.3.9.

> **Beta notice:** This is a pre-release for public testing. Unified Settings Editor and Unified HUD Editor remain experimental, default-off concept tests. Back up provider configuration and verify changes in provider-native editors.

## Highlights since Release 0.3.9

- Added opt-in death-save centre alerts and independent Spirit Mask, Bonzo's Mask, and Phoenix cooldown HUDs; all default to off.
- Added opt-in friend/whitelist Party Auto Accept, exact private-message party requests, and local private-message helpers.
- Added Fast Party Commands for supported Party Chat `!` aliases and default-on local double-slash Party Commands for Warp, All Invite, transfer, kick, coordinates, promote, Stream, Dungeons, and Kuudra.
- Added exact command aliases, sender controls, cooldowns, arbitrary decimal Stream limits, and full-name or unique-prefix player completion.
- Added a stable-Release-only update notice. Beta 0.3.10 can notify about a newer compatible stable Release, but never downloads, installs, or replaces a JAR.
- Added launcher/HMCL homepage, source and issue metadata, plus Website, Downloads and Source Code links in Mod Menu.

## Fixes and interface improvements

- Fixed upgraded Bonzo's Mask save messages not starting the cooldown.
- Fixed multi-page `/friend list` synchronisation used by Party Auto Accept, including split chat components and strict rejection of unrelated chat.
- Fixed the General catalog being unable to reach its bottom content.
- Supported Mods now starts collapsed.
- Added browser-style draggable scrollbars to the feature catalog, feature settings, compatibility report, and party whitelist.
- Removed misleading HUD appearance controls from features without HUDs and prevented empty settings pages.
- Removed the obsolete Firmament duplicate-inventory delegation setting and related dead configuration/code remnants.

## Compatibility

- Minecraft 26.1.2: Fabric API `0.155.2+26.1.2` or a newer compatible build.
- Minecraft 26.2: Fabric API `0.154.2+26.2` or a newer compatible build.
- Fabric Loader 0.19.3 or newer and Java 25.
- QCA remains standalone and client-only. Mod Menu and recognised provider mods are optional.

## Files

- `QCloudy_Addition-0.3.10+26.1.2-Beta.jar`
- `QCloudy_Addition-0.3.10+26.1.2-Beta-sources.jar`
- `QCloudy_Addition-0.3.10+26.2-Beta.jar`
- `QCloudy_Addition-0.3.10+26.2-Beta-sources.jar`

Install exactly one playable JAR matching your Minecraft version. Do not install a `-sources.jar` as the mod.

Full details: [CHANGELOG.md](https://github.com/northwestcloudy/QCloudy-Addition/blob/main/CHANGELOG.md)

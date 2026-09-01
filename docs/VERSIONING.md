# Version and artifact naming

QCloudy_Addition separates the core mod version, Minecraft target, release channel, and Alpha iteration. Every artifact handoff must state and verify both the channel and complete version string.

## Channel selection

- Use Beta or Release only when the user explicitly requests that channel for the current output.
- If neither Beta nor Release is explicitly requested, the output is Alpha. Do not inherit the previous task's channel.
- Alpha is an unpublished development output and defaults to Minecraft 26.1.2 only. Do not build, test, package, or deliver a 26.2 Alpha unless it is explicitly requested.
- Beta is a public test channel but is not a stable-update candidate. Release is the stable channel tracked by the in-game updater.

## Alpha iteration reset

The Alpha counter belongs to the current core-version cycle; it is not a lifetime counter. Whenever the leading/core version iterates for the next Beta or Release cycle, Alpha restarts at `alpha1`, then advances to `alpha2`, `alpha3`, and so on until the next core-version iteration.

Project numbering examples supplied by the project owner:

- Beta update: `0.3.9` → `0.3.10`; the corresponding development cycle starts at `0.3.10-alpha1`.
- Release update: `0.3.9` → `0.4.9`; the corresponding development cycle starts at `0.4.9-alpha1`.

## Artifact format

- Alpha: `QCloudy_Addition-0.3.10-alpha1+26.1.2.jar`.
- Beta: `QCloudy_Addition-0.3.10+26.1.2-Beta.jar`.
- Release: `QCloudy_Addition-0.3.9+26.1.2-Release.jar`.
- Sources use the same version with `-sources` immediately before `.jar`.

## Build properties

`gradle.properties` is the single source of truth for the current output:

```properties
minecraft_version=26.1.2
release_channel=Alpha
mod_version=0.3.10
alpha_iteration=1
```

`release_channel` accepts only `Alpha`, `Beta`, or `Release`. `alpha_iteration` is required only by Alpha builds and is ignored in Beta and Release artifact names.

Fabric metadata uses version-comparison-friendly forms:

- Alpha: `0.3.10-alpha1+26.1.2`
- Beta: `0.3.10-beta+26.1.2`
- Release: `0.3.9+26.1.2`

The Gradle build constructs both the artifact name and metadata version from these properties. Do not rename a built JAR manually.

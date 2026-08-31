# Version and artifact naming

QCloudy_Addition separates the core mod version, Minecraft target, release channel, and Alpha iteration.

## Artifact format

`QCloudy_Addition-<core version>+<Minecraft version>-<channel>[-<Alpha iteration>].jar`

- Alpha includes a separate positive iteration number: `QCloudy_Addition-0.2.9+26.1.2-Alpha-30.jar`.
- Beta has no separate Beta iteration number: `QCloudy_Addition-0.2.10+26.1.2-Beta.jar`.
- Release has no separate Release iteration number: `QCloudy_Addition-0.3.9+26.1.2-Release.jar`.
- Sources use the same name with `-sources` immediately before `.jar`.

## Build properties

`gradle.properties` is the single source of truth:

```properties
release_channel=Beta
mod_version=0.4.9
alpha_iteration=37
```

`release_channel` accepts only `Alpha`, `Beta`, or `Release`. `alpha_iteration` is required only by Alpha builds and is ignored in Beta and Release artifact names.

Fabric metadata uses version-comparison-friendly forms:

- Alpha: `0.2.9-alpha.30+26.1.2`
- Beta: `0.2.10-beta+26.1.2`
- Release: `0.3.9+26.1.2`

The Gradle build constructs both the artifact name and metadata version from these properties. Do not rename a built JAR manually.

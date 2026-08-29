package cloudy.autume.addition.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ReleaseManifestTest {
    private static final ReleaseBuildInfo BETA =
            new ReleaseBuildInfo("Beta", "0.4.0", "26.1.2", 1);

    @Test
    void acceptsOnlyANewerStableSequenceWithExactPlayableAsset() {
        var update = ReleaseManifest.findUpdate(manifest("Release", 2,
                "26.1.2", releaseName("26.1.2"), officialUrl("26.1.2")), BETA);
        assertEquals("0.4.0", update.orElseThrow().version());
        assertEquals(2, update.orElseThrow().sequence());

        assertTrue(ReleaseManifest.findUpdate(manifest("Release", 1,
                "26.1.2", releaseName("26.1.2"), officialUrl("26.1.2")), BETA).isEmpty());
    }

    @Test
    void ignoresBetaManifestsAndAllUpdatesInAlphaBuilds() {
        assertTrue(ReleaseManifest.findUpdate(manifest("Beta", 2,
                "26.1.2", releaseName("26.1.2"), officialUrl("26.1.2")), BETA).isEmpty());

        ReleaseBuildInfo alpha = new ReleaseBuildInfo("Alpha", "0.4.0", "26.1.2", 1);
        assertTrue(ReleaseManifest.findUpdate(manifest("Release", 2,
                "26.1.2", releaseName("26.1.2"), officialUrl("26.1.2")), alpha).isEmpty());
    }

    @Test
    void requiresAnExactCurrentMinecraftReleaseJar() {
        assertTrue(ReleaseManifest.findUpdate(manifest("Release", 2,
                "26.2", releaseName("26.2"), officialUrl("26.2")), BETA).isEmpty());
        assertTrue(ReleaseManifest.findUpdate(manifest("Release", 2,
                "26.1.2", releaseName("26.1.2").replace("-Release.jar", "-Release-sources.jar"),
                officialUrl("26.1.2")), BETA).isEmpty());
    }

    @Test
    void rejectsUntrustedAssetUrlsAndMalformedRemoteData() {
        assertTrue(ReleaseManifest.findUpdate(manifest("Release", 2,
                "26.1.2", releaseName("26.1.2"),
                "https://example.com/" + releaseName("26.1.2")), BETA).isEmpty());
        assertTrue(ReleaseManifest.findUpdate(manifest("Release", 2,
                "26.1.2", releaseName("26.1.2"),
                officialUrl("26.1.2").replace("/v0.4.0/", "/v0.3.9/")), BETA).isEmpty());
        assertTrue(ReleaseManifest.findUpdate("{not-json", BETA).isEmpty());
        assertTrue(ReleaseManifest.findUpdate("{}", BETA).isEmpty());
    }

    @Test
    void rejectsAmbiguousDuplicateMatchingAssets() {
        String asset = """
                {
                  "minecraft": "26.1.2",
                  "name": "%s",
                  "digest": "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "url": "%s"
                }
                """.formatted(releaseName("26.1.2"), officialUrl("26.1.2"));
        String manifest = """
                {
                  "schemaVersion": 1,
                  "releaseSequence": 2,
                  "channel": "Release",
                  "version": "0.4.0",
                  "tag": "v0.4.0",
                  "assets": [%s, %s]
                }
                """.formatted(asset, asset);
        assertTrue(ReleaseManifest.findUpdate(manifest, BETA).isEmpty());
    }

    private static String manifest(String channel, long sequence, String minecraft,
                                   String name, String url) {
        return """
                {
                  "schemaVersion": 1,
                  "releaseSequence": %d,
                  "channel": "%s",
                  "version": "0.4.0",
                  "tag": "v0.4.0",
                  "assets": [{
                    "minecraft": "%s",
                    "name": "%s",
                    "digest": "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "url": "%s"
                  }]
                }
                """.formatted(sequence, channel, minecraft, name, url);
    }

    private static String releaseName(String minecraft) {
        return "QCloudy_Addition-0.4.0+" + minecraft + "-Release.jar";
    }

    private static String officialUrl(String minecraft) {
        return "https://github.com/northwestcloudy/QCloudy-Addition/releases/download/v0.4.0/"
                + releaseName(minecraft).replace("+", "%2B");
    }
}

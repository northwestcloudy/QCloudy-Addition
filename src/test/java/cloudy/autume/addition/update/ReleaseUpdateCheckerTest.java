package cloudy.autume.addition.update;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ReleaseUpdateCheckerTest {
    private static final ReleaseBuildInfo BETA =
            new ReleaseBuildInfo("Beta", "0.4.0", "26.1.2", 1);

    @Test
    void usesDirectEndpointNoRedirectsAndBoundedTimeouts() {
        HttpClient client = ReleaseUpdateChecker.defaultHttpClient();
        assertEquals(HttpClient.Redirect.NEVER, client.followRedirects());
        assertEquals(Duration.ofSeconds(5), client.connectTimeout().orElseThrow());

        var request = new ReleaseUpdateChecker(BETA, client).buildRequest();
        assertEquals("https://www.qcloudy.net/assets/data/release-manifest.json",
                request.uri().toString());
        assertEquals(Duration.ofSeconds(10), request.timeout().orElseThrow());
        assertEquals("GET", request.method());
    }

    @Test
    void ignoresNon200AndOversizedResponses() {
        assertTrue(ReleaseUpdateChecker.parseResponse(503,
                new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)), BETA).isEmpty());
        byte[] oversized = new byte[ReleaseUpdateChecker.MAX_RESPONSE_BYTES + 1];
        assertTrue(ReleaseUpdateChecker.parseResponse(200,
                new ByteArrayInputStream(oversized), BETA).isEmpty());
    }

    @Test
    void acceptsValidBoundedManifestResponse() {
        String json = """
                {
                  "schemaVersion": 1,
                  "releaseSequence": 2,
                  "channel": "Release",
                  "version": "0.4.0",
                  "tag": "v0.4.0",
                  "assets": [{
                    "minecraft": "26.1.2",
                    "name": "QCloudy_Addition-0.4.0+26.1.2-Release.jar",
                    "digest": "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "url": "https://github.com/northwestcloudy/QCloudy-Addition/releases/download/v0.4.0/QCloudy_Addition-0.4.0%2B26.1.2-Release.jar"
                  }]
                }
                """;
        var update = ReleaseUpdateChecker.parseResponse(200,
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), BETA);
        assertEquals("0.4.0", update.orElseThrow().version());
    }
}

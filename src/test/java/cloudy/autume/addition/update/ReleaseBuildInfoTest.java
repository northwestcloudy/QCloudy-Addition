package cloudy.autume.addition.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ReleaseBuildInfoTest {
    @Test
    void onlyBetaAndReleaseBuildsCheckStableReleases() {
        assertFalse(new ReleaseBuildInfo("Alpha", "0.4.0", "26.1.2", 1)
                .checksStableReleases());
        assertTrue(new ReleaseBuildInfo("Beta", "0.4.0", "26.1.2", 1)
                .checksStableReleases());
        assertTrue(new ReleaseBuildInfo("Release", "0.4.0", "26.1.2", 1)
                .checksStableReleases());
        assertFalse(new ReleaseBuildInfo("Invalid", "0.4.0", "26.1.2", 1)
                .checksStableReleases());
        assertFalse(new ReleaseBuildInfo("Release", "0.4.0", "26.1.2", 0)
                .checksStableReleases());
    }

    @Test
    void processedBuildResourceContainsConcreteMetadata() throws Exception {
        ReleaseBuildInfo build = ReleaseBuildInfo.load();
        assertEquals("Alpha", build.channel());
        assertEquals("0.3.10", build.version());
        assertEquals("26.1.2", build.minecraftVersion());
        assertEquals(1, build.releaseBaselineSequence());
    }
}

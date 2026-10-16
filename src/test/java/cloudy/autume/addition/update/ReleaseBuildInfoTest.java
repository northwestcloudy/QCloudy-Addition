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
        assertEquals(System.getProperty("qca.test.releaseChannel"), build.channel());
        assertEquals(System.getProperty("qca.test.versionCore"), build.version());
        assertEquals(System.getProperty("qca.test.minecraftVersion"), build.minecraftVersion());
        assertEquals(Long.parseLong(System.getProperty("qca.test.releaseBaselineSequence")),
                build.releaseBaselineSequence());
    }
}

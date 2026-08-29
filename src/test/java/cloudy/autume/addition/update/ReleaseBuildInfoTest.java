package cloudy.autume.addition.update;

import org.junit.jupiter.api.Test;

import java.util.Set;

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
        assertTrue(build.channel().matches("Alpha|Beta|Release"));
        assertEquals("0.3.9", build.version());
        assertTrue(Set.of("26.1.2", "26.2").contains(build.minecraftVersion()));
        assertEquals(1, build.releaseBaselineSequence());
    }
}

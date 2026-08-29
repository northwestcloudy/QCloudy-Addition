package cloudy.autume.addition.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ReleaseUpdateStateTest {
    @Test
    void alphaNeverStartsAndBetaStartsOnlyOncePerProcess() {
        ReleaseUpdateState state = new ReleaseUpdateState();
        assertFalse(state.beginRequest(new ReleaseBuildInfo("Alpha", "0.4.0", "26.1.2", 1)));
        assertTrue(state.beginRequest(new ReleaseBuildInfo("Beta", "0.4.0", "26.1.2", 1)));
        assertFalse(state.beginRequest(new ReleaseBuildInfo("Beta", "0.4.0", "26.1.2", 1)));
    }

    @Test
    void keepsPendingUpdateUntilPlayerExistsAndDisplaysOnlyOnce() {
        ReleaseUpdateState state = new ReleaseUpdateState();
        ReleaseManifest.AvailableRelease release =
                new ReleaseManifest.AvailableRelease("0.4.0", 2);
        state.queue(release);
        assertTrue(state.takeForDisplay(false).isEmpty());
        assertTrue(state.takeForDisplay(true).isPresent());
        assertTrue(state.takeForDisplay(true).isEmpty());
        state.queue(release);
        assertTrue(state.takeForDisplay(true).isEmpty());
    }
}

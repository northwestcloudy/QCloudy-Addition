package cloudy.autume.addition.update;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/** Process-lifetime state kept separate so request and notification limits are testable. */
final class ReleaseUpdateState {
    private final AtomicBoolean requestStarted = new AtomicBoolean();
    private final AtomicBoolean notificationShown = new AtomicBoolean();
    private volatile ReleaseManifest.AvailableRelease pending;

    boolean beginRequest(ReleaseBuildInfo build) {
        return build != null && build.checksStableReleases()
                && requestStarted.compareAndSet(false, true);
    }

    void queue(ReleaseManifest.AvailableRelease release) {
        if (release != null && !notificationShown.get()) pending = release;
    }

    Optional<ReleaseManifest.AvailableRelease> takeForDisplay(boolean playerAvailable) {
        ReleaseManifest.AvailableRelease release = pending;
        if (!playerAvailable || release == null) return Optional.empty();
        if (!notificationShown.compareAndSet(false, true)) {
            pending = null;
            return Optional.empty();
        }
        pending = null;
        return Optional.of(release);
    }
}

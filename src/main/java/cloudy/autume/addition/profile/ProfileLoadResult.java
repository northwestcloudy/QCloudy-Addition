package cloudy.autume.addition.profile;

import java.util.Objects;

/** Successful result and its local-cache provenance. */
public record ProfileLoadResult(ProfileSnapshot snapshot,
                                boolean fromSessionCache,
                                ProfileLoadStatus status) {
    public ProfileLoadResult {
        snapshot = Objects.requireNonNull(snapshot, "snapshot");
        status = Objects.requireNonNull(status, "status");
    }

    public static ProfileLoadResult network(ProfileSnapshot snapshot) {
        return new ProfileLoadResult(snapshot, false, statusOf(snapshot));
    }

    ProfileLoadResult asSessionCacheHit() {
        return new ProfileLoadResult(snapshot, true, status);
    }

    private static ProfileLoadStatus statusOf(ProfileSnapshot snapshot) {
        if (snapshot.stale()) return ProfileLoadStatus.STALE;
        if (snapshot.partial()) return ProfileLoadStatus.PARTIAL;
        return ProfileLoadStatus.READY;
    }
}

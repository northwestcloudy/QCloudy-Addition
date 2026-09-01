package cloudy.autume.addition.profile;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Immutable freshness information for one independently cached source. */
public final class SourceMetadata {
    private final ProfileDataSource source;
    private final ProfileSourceStatus status;
    private final Instant fetchedAt;
    private final Instant expiresAt;
    private final Instant staleUntil;
    private final Instant nextRefreshAt;
    private final String sourceVersion;

    public SourceMetadata(ProfileDataSource source,
                          ProfileSourceStatus status,
                          Instant fetchedAt,
                          Instant expiresAt,
                          Instant staleUntil,
                          Instant nextRefreshAt,
                          String sourceVersion) {
        this.source = Objects.requireNonNull(source, "source");
        this.status = Objects.requireNonNull(status, "status");
        this.fetchedAt = fetchedAt;
        this.expiresAt = expiresAt;
        this.staleUntil = staleUntil;
        this.nextRefreshAt = nextRefreshAt;
        this.sourceVersion = sourceVersion == null ? "" : sourceVersion;
        if (fetchedAt != null && expiresAt != null && expiresAt.isBefore(fetchedAt)) {
            throw new IllegalArgumentException("expiresAt cannot precede fetchedAt");
        }
        if (expiresAt != null && staleUntil != null && staleUntil.isBefore(expiresAt)) {
            throw new IllegalArgumentException("staleUntil cannot precede expiresAt");
        }
    }

    public static SourceMetadata notRequested(ProfileDataSource source) {
        return new SourceMetadata(source, ProfileSourceStatus.NOT_REQUESTED,
                null, null, null, null, "");
    }

    public ProfileDataSource source() {
        return source;
    }

    public ProfileSourceStatus status() {
        return status;
    }

    public Optional<Instant> fetchedAt() {
        return Optional.ofNullable(fetchedAt);
    }

    public Optional<Instant> expiresAt() {
        return Optional.ofNullable(expiresAt);
    }

    public Optional<Instant> staleUntil() {
        return Optional.ofNullable(staleUntil);
    }

    public Optional<Instant> nextRefreshAt() {
        return Optional.ofNullable(nextRefreshAt);
    }

    public String sourceVersion() {
        return sourceVersion;
    }

    public boolean stale() {
        return status == ProfileSourceStatus.STALE;
    }

    public boolean available() {
        return status == ProfileSourceStatus.FRESH || status == ProfileSourceStatus.STALE;
    }

    /** True for a computed source that did not perform or cache an upstream lookup. */
    boolean instantaneousFreshBoundary() {
        return status == ProfileSourceStatus.FRESH
                && fetchedAt != null
                && expiresAt != null
                && expiresAt.equals(fetchedAt)
                && (staleUntil == null || staleUntil.equals(fetchedAt))
                && (nextRefreshAt == null || nextRefreshAt.equals(fetchedAt));
    }

    /** Returns the next instant at which a local session cache may re-query this source. */
    Optional<Instant> localCacheBoundary(Instant now) {
        Instant boundary;
        if (status == ProfileSourceStatus.FRESH) {
            if (expiresAt == null || !expiresAt.isAfter(now)) return Optional.empty();
            boundary = expiresAt;
        } else if (status == ProfileSourceStatus.STALE) {
            if (staleUntil == null || !staleUntil.isAfter(now)) return Optional.empty();
            boundary = staleUntil;
        } else {
            return Optional.empty();
        }
        if (nextRefreshAt != null && nextRefreshAt.isAfter(now)
                && nextRefreshAt.isBefore(boundary)) {
            boundary = nextRefreshAt;
        }
        return Optional.of(boundary);
    }
}

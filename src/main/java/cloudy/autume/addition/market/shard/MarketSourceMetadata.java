package cloudy.autume.addition.market.shard;

import java.time.Instant;
import java.util.Optional;

/** Immutable freshness information for one Shard Bazaar snapshot. */
public record MarketSourceMetadata(MarketSourceStatus status,
                                   Instant fetchedAt,
                                   Instant expiresAt,
                                   Instant staleUntil,
                                   Instant nextRefreshAt,
                                   String sourceVersion) {
    public MarketSourceMetadata {
        status = java.util.Objects.requireNonNull(status, "status");
        sourceVersion = sourceVersion == null ? "" : sourceVersion;
        if (fetchedAt != null && expiresAt != null && expiresAt.isBefore(fetchedAt)) {
            throw new IllegalArgumentException("expiresAt cannot precede fetchedAt");
        }
        if (expiresAt != null && staleUntil != null && staleUntil.isBefore(expiresAt)) {
            throw new IllegalArgumentException("staleUntil cannot precede expiresAt");
        }
    }

    public boolean stale() {
        return status == MarketSourceStatus.STALE;
    }

    Optional<Instant> localCacheBoundary(Instant now) {
        Instant boundary = status == MarketSourceStatus.FRESH ? expiresAt : staleUntil;
        if (boundary == null || !boundary.isAfter(now)) return Optional.empty();
        if (nextRefreshAt != null && nextRefreshAt.isAfter(now) && nextRefreshAt.isBefore(boundary)) {
            boundary = nextRefreshAt;
        }
        return Optional.of(boundary);
    }
}

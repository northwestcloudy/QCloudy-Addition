package cloudy.autume.addition.profile;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Bounded in-process cache that never changes server-provided freshness metadata. */
final class SessionProfileCache {
    static final Duration MAX_AGE = Duration.ofMinutes(10);
    private static final int MAX_ENTRIES = 128;

    private final Clock clock;
    private final LinkedHashMap<Key, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);

    SessionProfileCache(Clock clock) {
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    synchronized Optional<ProfileLoadResult> get(String target, String profileId) {
        Key key = Key.of(target, profileId);
        Entry entry = entries.get(key);
        if (entry == null) return Optional.empty();
        if (!clock.instant().isBefore(entry.validUntil)) {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.result.asSessionCacheHit());
    }

    synchronized void put(String target, String profileId, ProfileLoadResult result) {
        Instant now = clock.instant();
        Instant maximum = now.plus(MAX_AGE);
        Instant validUntil = result.snapshot().sessionCacheBoundary(now, maximum);
        if (!validUntil.isAfter(now)) return;
        entries.put(Key.of(target, profileId), new Entry(result, validUntil));
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(entries.keySet().iterator().next());
        }
    }

    synchronized void clear() {
        entries.clear();
    }

    synchronized int size() {
        return entries.size();
    }

    private record Entry(ProfileLoadResult result, Instant validUntil) {
    }

    private record Key(String target, String profileId) {
        static Key of(String target, String profileId) {
            String normalizedTarget = target == null ? "" : target.trim()
                    .replace("-", "").toLowerCase(Locale.ROOT);
            String normalizedProfile = profileId == null ? "" : profileId.trim()
                    .replace("-", "").toLowerCase(Locale.ROOT);
            return new Key(normalizedTarget, normalizedProfile);
        }
    }
}

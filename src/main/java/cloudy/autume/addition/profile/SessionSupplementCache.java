package cloudy.autume.addition.profile;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Optional;

/** Independent bounded session cache for lazily loaded Museum and Garden sections. */
final class SessionSupplementCache {
    private static final Duration MAX_AGE = Duration.ofMinutes(10);
    private static final int MAX_ENTRIES = 128;

    private final Clock clock;
    private final LinkedHashMap<Key, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);

    SessionSupplementCache(Clock clock) {
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    synchronized Optional<ProfileSupplement> get(String playerUuid,
                                                  String profileId,
                                                  ProfileSectionId sectionId) {
        Key key = Key.of(playerUuid, profileId, sectionId);
        Entry entry = entries.get(key);
        if (entry == null) return Optional.empty();
        if (!clock.instant().isBefore(entry.validUntil)) {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.supplement);
    }

    synchronized void put(ProfileSupplement supplement) {
        Instant now = clock.instant();
        Optional<Instant> serverBoundary = supplement.metadata().localCacheBoundary(now);
        if (serverBoundary.isEmpty()) return;
        Instant maximum = now.plus(MAX_AGE);
        Instant validUntil = serverBoundary.get().isBefore(maximum)
                ? serverBoundary.get() : maximum;
        if (!validUntil.isAfter(now)) return;
        Key key = Key.of(supplement.playerUuid(), supplement.profileId(),
                supplement.section().id());
        entries.put(key, new Entry(supplement, validUntil));
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

    private record Entry(ProfileSupplement supplement, Instant validUntil) {
    }

    private record Key(String playerUuid, String profileId, ProfileSectionId sectionId) {
        static Key of(String playerUuid, String profileId, ProfileSectionId sectionId) {
            return new Key(canonical(playerUuid), canonical(profileId),
                    java.util.Objects.requireNonNull(sectionId, "sectionId"));
        }

        private static String canonical(String value) {
            return value == null ? "" : value.trim().replace("-", "")
                    .toLowerCase(Locale.ROOT);
        }
    }
}

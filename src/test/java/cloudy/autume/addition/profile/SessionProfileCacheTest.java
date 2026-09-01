package cloudy.autume.addition.profile;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SessionProfileCacheTest {
    @Test
    void cachesForAtMostTenMinutes() {
        MutableClock clock = new MutableClock(ProfileFixtures.NOW);
        SessionProfileCache cache = new SessionProfileCache(clock);
        cache.put("NorthwestCloudy", "", ProfileLoadResult.network(
                ProfileFixtures.snapshot(ProfileFixtures.NOW.plus(Duration.ofHours(1)))));

        clock.advance(Duration.ofMinutes(9).plusSeconds(59));
        assertTrue(cache.get("northwestcloudy", "").orElseThrow().fromSessionCache());
        clock.advance(Duration.ofSeconds(1));
        assertFalse(cache.get("northwestcloudy", "").isPresent());
    }

    @Test
    void neverExtendsTheServerExpiry() {
        MutableClock clock = new MutableClock(ProfileFixtures.NOW);
        SessionProfileCache cache = new SessionProfileCache(clock);
        cache.put("NorthwestCloudy", "", ProfileLoadResult.network(
                ProfileFixtures.snapshot(ProfileFixtures.NOW.plus(Duration.ofMinutes(2)))));

        clock.advance(Duration.ofMinutes(1).plusSeconds(59));
        assertTrue(cache.get("NorthwestCloudy", "").isPresent());
        clock.advance(Duration.ofSeconds(1));
        assertFalse(cache.get("NorthwestCloudy", "").isPresent());
    }

    @Test
    void doesNotCacheAResponseWhoseFreshBoundaryAlreadyExpired() {
        MutableClock clock = new MutableClock(ProfileFixtures.NOW.plus(Duration.ofMinutes(2)));
        SessionProfileCache cache = new SessionProfileCache(clock);
        cache.put("NorthwestCloudy", "", ProfileLoadResult.network(
                ProfileFixtures.snapshot(ProfileFixtures.NOW.plus(Duration.ofMinutes(1)))));

        assertFalse(cache.get("NorthwestCloudy", "").isPresent());
    }

    @Test
    void directUuidIdentityMetadataDoesNotDisableTheSessionCache() {
        MutableClock clock = new MutableClock(ProfileFixtures.NOW);
        SessionProfileCache cache = new SessionProfileCache(clock);
        cache.put(ProfileFixtures.PLAYER_UUID, "", ProfileLoadResult.network(
                ProfileFixtures.snapshotWithDirectIdentity(
                        ProfileFixtures.NOW.plus(Duration.ofHours(1)))));

        clock.advance(Duration.ofMinutes(9).plusSeconds(59));
        assertTrue(cache.get(ProfileFixtures.PLAYER_UUID, "").orElseThrow().fromSessionCache());
        clock.advance(Duration.ofSeconds(1));
        assertFalse(cache.get(ProfileFixtures.PLAYER_UUID, "").isPresent());
    }
}

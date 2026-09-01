package cloudy.autume.addition.profile;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShardBazaarSnapshotTest {
    @Test
    void parsesTheFrozenSchemaIncludingNonPrefixShardIds() {
        ShardBazaarSnapshot snapshot = ShardBazaarSnapshot.parse(ProfileFixtures.shardJson(
                ShardBazaarSide.INSTANT_BUY,
                ProfileFixtures.NOW.plus(Duration.ofMinutes(1))));

        assertEquals(1, snapshot.schemaVersion());
        assertEquals("hypixel-bazaar", snapshot.source());
        assertEquals(ShardBazaarSide.INSTANT_BUY, snapshot.side());
        assertEquals(125.5, snapshot.price("SHARD_TEST").orElseThrow());
        assertEquals(ProfileSourceStatus.FRESH, snapshot.metadata().status());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.prices().put("SHARD_MUTATION", 1.0));
    }

    @Test
    void rejectsWrongSchemaAndNonPositivePrices() {
        String valid = ProfileFixtures.shardJson(ShardBazaarSide.INSTANT_SELL,
                ProfileFixtures.NOW.plus(Duration.ofMinutes(1)));
        ProfileException schema = assertThrows(ProfileException.class,
                () -> ShardBazaarSnapshot.parse(valid.replaceFirst(
                        "\"schemaVersion\": 1", "\"schemaVersion\": 2")));
        ProfileException price = assertThrows(ProfileException.class,
                () -> ShardBazaarSnapshot.parse(valid.replaceFirst("125.5", "0")));

        assertEquals(ProfileException.Code.UNSUPPORTED_SCHEMA, schema.code());
        assertEquals(ProfileException.Code.INVALID_RESPONSE, price.code());
    }

    @Test
    void requiresSourceVersionToBeAJsonString() {
        String numericSourceVersion = ProfileFixtures.shardJson(
                ShardBazaarSide.INSTANT_BUY,
                ProfileFixtures.NOW.plus(Duration.ofMinutes(1)))
                .replace("\"sourceVersion\": \"12345\"", "\"sourceVersion\": 12345");

        ProfileException exception = assertThrows(ProfileException.class,
                () -> ShardBazaarSnapshot.parse(numericSourceVersion));

        assertEquals(ProfileException.Code.INVALID_RESPONSE, exception.code());
    }

    @Test
    void cacheBoundaryUsesTheEarlierOfServerExpiryAndTenMinutes() {
        ShardBazaarSnapshot shortLived = ShardBazaarSnapshot.parse(
                ProfileFixtures.shardJson(ShardBazaarSide.INSTANT_BUY,
                        ProfileFixtures.NOW.plus(Duration.ofMinutes(1))));
        ShardBazaarSnapshot longLived = ShardBazaarSnapshot.parse(
                ProfileFixtures.shardJson(ShardBazaarSide.INSTANT_BUY,
                        ProfileFixtures.NOW.plus(Duration.ofMinutes(30))));

        assertEquals(ProfileFixtures.NOW.plus(Duration.ofMinutes(1)),
                shortLived.sessionCacheBoundary(ProfileFixtures.NOW));
        assertEquals(ProfileFixtures.NOW.plus(Duration.ofMinutes(10)),
                longLived.sessionCacheBoundary(ProfileFixtures.NOW));
        assertTrue(shortLived.price("missing").isEmpty());
    }
}

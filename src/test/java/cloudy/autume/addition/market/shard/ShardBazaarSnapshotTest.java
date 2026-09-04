package cloudy.autume.addition.market.shard;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShardBazaarSnapshotTest {
    private static final Instant NOW = Instant.parse("2026-09-04T00:00:00Z");

    @Test
    void remainsIndependentFromTheRemovedProfileViewer() {
        ShardBazaarSnapshot snapshot = ShardBazaarSnapshot.parse(json(
                ShardBazaarSide.INSTANT_BUY, NOW.plus(Duration.ofMinutes(1))));
        assertEquals(1, snapshot.schemaVersion());
        assertEquals("hypixel-bazaar", snapshot.source());
        assertEquals(ShardBazaarSide.INSTANT_BUY, snapshot.side());
        assertEquals(125.5, snapshot.price("SHARD_TEST").orElseThrow());
        assertEquals(MarketSourceStatus.FRESH, snapshot.metadata().status());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.prices().put("SHARD_MUTATION", 1.0));
    }

    @Test
    void rejectsWrongSchemaAndNonPositivePrices() {
        String valid = json(ShardBazaarSide.INSTANT_SELL, NOW.plus(Duration.ofMinutes(1)));
        MarketDataException schema = assertThrows(MarketDataException.class,
                () -> ShardBazaarSnapshot.parse(valid.replaceFirst(
                        "\"schemaVersion\":1", "\"schemaVersion\":2")));
        MarketDataException price = assertThrows(MarketDataException.class,
                () -> ShardBazaarSnapshot.parse(valid.replaceFirst("125.5", "0")));
        assertEquals(MarketDataException.Code.UNSUPPORTED_SCHEMA, schema.code());
        assertEquals(MarketDataException.Code.INVALID_RESPONSE, price.code());
    }

    @Test
    void requiresSourceVersionToBeAJsonString() {
        String invalid = json(ShardBazaarSide.INSTANT_BUY, NOW.plus(Duration.ofMinutes(1)))
                .replace("\"sourceVersion\":\"12345\"", "\"sourceVersion\":12345");
        MarketDataException exception = assertThrows(MarketDataException.class,
                () -> ShardBazaarSnapshot.parse(invalid));
        assertEquals(MarketDataException.Code.INVALID_RESPONSE, exception.code());
    }

    @Test
    void cacheBoundaryUsesTheEarlierOfServerExpiryAndTenMinutes() {
        ShardBazaarSnapshot shortLived = ShardBazaarSnapshot.parse(json(
                ShardBazaarSide.INSTANT_BUY, NOW.plus(Duration.ofMinutes(1))));
        ShardBazaarSnapshot longLived = ShardBazaarSnapshot.parse(json(
                ShardBazaarSide.INSTANT_BUY, NOW.plus(Duration.ofMinutes(30))));
        assertEquals(NOW.plus(Duration.ofMinutes(1)), shortLived.sessionCacheBoundary(NOW));
        assertEquals(NOW.plus(Duration.ofMinutes(10)), longLived.sessionCacheBoundary(NOW));
        assertTrue(shortLived.price("missing").isEmpty());
    }

    static String json(ShardBazaarSide side, Instant expiry) {
        return """
                {"schemaVersion":1,"source":"hypixel-bazaar","metadata":{
                "status":"fresh","fetchedAt":%d,"expiresAt":%d,"staleUntil":%d,
                "nextRefreshAt":%d,"sourceVersion":"12345"},
                "side":"%s","prices":{"SHARD_TEST":125.5}}
                """.formatted(NOW.toEpochMilli(), expiry.toEpochMilli(),
                expiry.plus(Duration.ofMinutes(10)).toEpochMilli(), expiry.toEpochMilli(),
                side.wireName());
    }
}

package cloudy.autume.addition.market.shard;

import cloudy.autume.addition.network.QcaApiClient;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShardBazaarServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-04T00:00:00Z");

    @Test
    void cachesEachSideWithoutCrossingTheServerExpiry() {
        MutableClock clock = new MutableClock(NOW);
        FakeGateway gateway = new FakeGateway();
        gateway.response = CompletableFuture.completedFuture(ok(ShardBazaarSnapshotTest.json(
                ShardBazaarSide.INSTANT_BUY, NOW.plus(Duration.ofMinutes(1)))));
        ShardBazaarService service = new ShardBazaarService(gateway, clock);

        ShardBazaarLoadResult first = service.load(ShardBazaarSide.INSTANT_BUY).join();
        clock.advance(Duration.ofSeconds(59));
        ShardBazaarLoadResult cached = service.load(ShardBazaarSide.INSTANT_BUY).join();
        clock.advance(Duration.ofSeconds(1));
        gateway.response = CompletableFuture.completedFuture(ok(ShardBazaarSnapshotTest.json(
                ShardBazaarSide.INSTANT_BUY, clock.instant().plus(Duration.ofMinutes(1)))));
        ShardBazaarLoadResult refreshed = service.load(ShardBazaarSide.INSTANT_BUY).join();

        assertFalse(first.fromSessionCache());
        assertTrue(cached.fromSessionCache());
        assertFalse(refreshed.fromSessionCache());
        assertEquals(2, gateway.calls);
    }

    @Test
    void coalescesConcurrentRequestsForTheSameSide() {
        MutableClock clock = new MutableClock(NOW);
        FakeGateway gateway = new FakeGateway();
        gateway.response = new CompletableFuture<>();
        ShardBazaarService service = new ShardBazaarService(gateway, clock);

        CompletableFuture<ShardBazaarLoadResult> first = service.load(ShardBazaarSide.INSTANT_SELL);
        CompletableFuture<ShardBazaarLoadResult> second = service.load(ShardBazaarSide.INSTANT_SELL);
        assertSame(first, second);
        assertEquals(1, gateway.calls);
        gateway.response.complete(ok(ShardBazaarSnapshotTest.json(
                ShardBazaarSide.INSTANT_SELL, NOW.plus(Duration.ofMinutes(1)))));
        assertEquals(ShardBazaarSide.INSTANT_SELL, first.join().snapshot().side());
    }

    @Test
    void validatesRequestedSideAndMapsRateLimitMetadata() {
        MutableClock clock = new MutableClock(NOW);
        FakeGateway wrongSide = new FakeGateway();
        wrongSide.response = CompletableFuture.completedFuture(ok(ShardBazaarSnapshotTest.json(
                ShardBazaarSide.INSTANT_SELL, NOW.plus(Duration.ofMinutes(1)))));
        MarketDataException mismatch = failure(new ShardBazaarService(wrongSide, clock)
                .load(ShardBazaarSide.INSTANT_BUY));

        FakeGateway limited = new FakeGateway();
        limited.response = CompletableFuture.completedFuture(new QcaApiClient.Response(429, "{}",
                Map.of("Retry-After", List.of("9"))));
        MarketDataException rateLimit = failure(new ShardBazaarService(limited, clock)
                .load(ShardBazaarSide.INSTANT_BUY));

        assertEquals(MarketDataException.Code.INVALID_RESPONSE, mismatch.code());
        assertEquals(MarketDataException.Code.RATE_LIMITED, rateLimit.code());
        assertEquals(Duration.ofSeconds(9), rateLimit.retryAfter());
    }

    private static MarketDataException failure(CompletableFuture<ShardBazaarLoadResult> future) {
        CompletionException wrapper = assertThrows(CompletionException.class, future::join);
        return (MarketDataException) wrapper.getCause();
    }

    private static QcaApiClient.Response ok(String body) {
        return new QcaApiClient.Response(200, body, Map.of());
    }

    private static final class FakeGateway implements ShardBazaarService.Gateway {
        private CompletableFuture<QcaApiClient.Response> response = new CompletableFuture<>();
        private int calls;

        @Override
        public CompletableFuture<QcaApiClient.Response> fetch(ShardBazaarSide side) {
            calls++;
            return response;
        }
    }
}

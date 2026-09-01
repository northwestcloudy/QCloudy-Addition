package cloudy.autume.addition.profile;

import cloudy.autume.addition.network.QcaApiClient;
import org.junit.jupiter.api.Test;

import java.time.Duration;
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
    @Test
    void cachesEachSideWithoutCrossingTheServerExpiry() {
        MutableClock clock = new MutableClock(ProfileFixtures.NOW);
        FakeGateway gateway = new FakeGateway();
        gateway.response = CompletableFuture.completedFuture(ok(ProfileFixtures.shardJson(
                ShardBazaarSide.INSTANT_BUY,
                ProfileFixtures.NOW.plus(Duration.ofMinutes(1)))));
        ShardBazaarService service = new ShardBazaarService(gateway, clock);

        ShardBazaarLoadResult first = service.load(ShardBazaarSide.INSTANT_BUY).join();
        clock.advance(Duration.ofSeconds(59));
        ShardBazaarLoadResult cached = service.load(ShardBazaarSide.INSTANT_BUY).join();
        clock.advance(Duration.ofSeconds(1));
        gateway.response = CompletableFuture.completedFuture(ok(ProfileFixtures.shardJson(
                ShardBazaarSide.INSTANT_BUY,
                clock.instant().plus(Duration.ofMinutes(1)))));
        ShardBazaarLoadResult refreshed = service.load(ShardBazaarSide.INSTANT_BUY).join();

        assertFalse(first.fromSessionCache());
        assertTrue(cached.fromSessionCache());
        assertFalse(refreshed.fromSessionCache());
        assertEquals(2, gateway.calls);
    }

    @Test
    void coalescesConcurrentRequestsForTheSameSide() {
        MutableClock clock = new MutableClock(ProfileFixtures.NOW);
        FakeGateway gateway = new FakeGateway();
        gateway.response = new CompletableFuture<>();
        ShardBazaarService service = new ShardBazaarService(gateway, clock);

        CompletableFuture<ShardBazaarLoadResult> first =
                service.load(ShardBazaarSide.INSTANT_SELL);
        CompletableFuture<ShardBazaarLoadResult> second =
                service.load(ShardBazaarSide.INSTANT_SELL);

        assertSame(first, second);
        assertEquals(1, gateway.calls);
        gateway.response.complete(ok(ProfileFixtures.shardJson(
                ShardBazaarSide.INSTANT_SELL,
                ProfileFixtures.NOW.plus(Duration.ofMinutes(1)))));
        assertEquals(ShardBazaarSide.INSTANT_SELL, first.join().snapshot().side());
    }

    @Test
    void validatesRequestedSideAndMapsRateLimitMetadata() {
        MutableClock clock = new MutableClock(ProfileFixtures.NOW);
        FakeGateway wrongSide = new FakeGateway();
        wrongSide.response = CompletableFuture.completedFuture(ok(ProfileFixtures.shardJson(
                ShardBazaarSide.INSTANT_SELL,
                ProfileFixtures.NOW.plus(Duration.ofMinutes(1)))));
        ProfileException mismatch = failure(new ShardBazaarService(wrongSide, clock)
                .load(ShardBazaarSide.INSTANT_BUY));

        FakeGateway limited = new FakeGateway();
        limited.response = CompletableFuture.completedFuture(new QcaApiClient.Response(429, """
                {"schemaVersion":1,"error":{
                  "code":"RATE_LIMITED","message":"Slow down."
                }}
                """, Map.of("Retry-After", List.of("9"))));
        ProfileException rateLimit = failure(new ShardBazaarService(limited, clock)
                .load(ShardBazaarSide.INSTANT_BUY));

        assertEquals(ProfileException.Code.INVALID_RESPONSE, mismatch.code());
        assertEquals(ProfileException.Code.RATE_LIMITED, rateLimit.code());
        assertEquals(Duration.ofSeconds(9), rateLimit.retryAfter());
    }

    private static ProfileException failure(CompletableFuture<ShardBazaarLoadResult> future) {
        CompletionException wrapper = assertThrows(CompletionException.class, future::join);
        return (ProfileException) wrapper.getCause();
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

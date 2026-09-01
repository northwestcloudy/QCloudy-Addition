package cloudy.autume.addition.profile.market;

import cloudy.autume.addition.network.QcaApiClient;
import cloudy.autume.addition.profile.ProfileException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MarketTooltipPriceServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-01T08:00:00Z");

    @Test
    void foldsDuplicatesAndCachesForExactlySixtySeconds() {
        MutableTestClock clock = new MutableTestClock(NOW);
        FakeGateway gateway = new FakeGateway(true);
        MarketTooltipPriceService service = new MarketTooltipPriceService(gateway, clock);
        MarketTooltipQuery query = new MarketTooltipQuery("HYPERION", null, 1);

        MarketTooltipPriceBatch first = service.load(List.of(query, query)).join();
        clock.advance(Duration.ofSeconds(59));
        MarketTooltipPriceBatch cached = service.load(List.of(query)).join();
        clock.advance(Duration.ofSeconds(1));
        MarketTooltipPriceBatch refreshed = service.load(List.of(query)).join();

        assertEquals(1, first.items().size());
        assertFalse(first.entirelyFromCache());
        assertTrue(cached.entirelyFromCache());
        assertFalse(refreshed.entirelyFromCache());
        assertEquals(2, gateway.calls);
        assertEquals(1, gateway.lastItems.size());
    }

    @Test
    void coalescesSameQueryAndCallerCancellationIsIsolated() {
        MutableTestClock clock = new MutableTestClock(NOW);
        FakeGateway gateway = new FakeGateway(false);
        MarketTooltipPriceService service = new MarketTooltipPriceService(gateway, clock);
        MarketTooltipQuery query = new MarketTooltipQuery("HYPERION", "upgrade=10", 2);

        CompletableFuture<MarketTooltipPriceBatch> first = service.load(List.of(query));
        CompletableFuture<MarketTooltipPriceBatch> second = service.load(List.of(query));

        assertEquals(1, gateway.calls);
        assertTrue(first.cancel(true));
        assertFalse(second.isCancelled());
        gateway.pending.complete(ok(responseFor(gateway.lastItems)));
        assertEquals(query, second.join().items().getFirst().query());
        assertTrue(first.isCancelled());
    }

    @Test
    void rejectsAnyResponseThatDoesNotEchoTheExactQuery() {
        MutableTestClock clock = new MutableTestClock(NOW);
        MarketTooltipPriceService.Gateway gateway = requests -> {
            MarketTooltipRequestItem sent = requests.getFirst();
            String wrong = MarketTooltipPriceJsonParserTest.response(sent.requestId(),
                    sent.query().key().itemId(), sent.query().key().variantKey(),
                    sent.query().quantity() + 1, "none",
                    MarketTooltipPriceJsonParserTest.allUnavailableQuotes());
            return CompletableFuture.completedFuture(ok(wrong));
        };
        MarketTooltipPriceService service = new MarketTooltipPriceService(gateway, clock);

        CompletionException wrapper = assertThrows(CompletionException.class,
                () -> service.load(List.of(new MarketTooltipQuery("A", null, 1))).join());

        ProfileException failure = (ProfileException) wrapper.getCause();
        assertEquals(ProfileException.Code.INVALID_RESPONSE, failure.code());
    }

    @Test
    void clearingCachePreventsAnOlderInflightRequestFromRepopulatingIt() {
        MutableTestClock clock = new MutableTestClock(NOW);
        FakeGateway gateway = new FakeGateway(false);
        MarketTooltipPriceService service = new MarketTooltipPriceService(gateway, clock);
        MarketTooltipQuery query = new MarketTooltipQuery("A", null, 1);

        CompletableFuture<MarketTooltipPriceBatch> old = service.load(List.of(query));
        service.clearSessionCache();
        gateway.pending.complete(ok(responseFor(gateway.lastItems)));
        old.join();
        gateway.pending = new CompletableFuture<>();
        CompletableFuture<MarketTooltipPriceBatch> next = service.load(List.of(query));

        assertEquals(2, gateway.calls);
        assertFalse(next.isDone());
        gateway.pending.complete(ok(responseFor(gateway.lastItems)));
        next.join();
    }

    private static String responseFor(List<MarketTooltipRequestItem> requests) {
        List<String> items = new ArrayList<>();
        for (MarketTooltipRequestItem request : requests) {
            MarketTooltipQuery query = request.query();
            items.add(MarketTooltipPriceJsonParserTest.item(request.requestId(),
                    query.key().itemId(), query.key().variantKey(), query.quantity(), "none",
                    MarketTooltipPriceJsonParserTest.allUnavailableQuotes()));
        }
        return "{\"schemaVersion\":1,\"currency\":\"SKYBLOCK_COINS\",\"items\":["
                + String.join(",", items) + "],\"source\":\"qca-market\",\"metadata\":{}}";
    }

    private static QcaApiClient.Response ok(String body) {
        return new QcaApiClient.Response(200, body, Map.of());
    }

    private static final class FakeGateway implements MarketTooltipPriceService.Gateway {
        private final boolean immediate;
        private CompletableFuture<QcaApiClient.Response> pending = new CompletableFuture<>();
        private List<MarketTooltipRequestItem> lastItems = List.of();
        private int calls;

        private FakeGateway(boolean immediate) {
            this.immediate = immediate;
        }

        @Override
        public CompletableFuture<QcaApiClient.Response> fetch(
                List<MarketTooltipRequestItem> items) {
            calls++;
            lastItems = List.copyOf(items);
            return immediate
                    ? CompletableFuture.completedFuture(ok(responseFor(lastItems)))
                    : pending;
        }
    }

    private static final class MutableTestClock extends Clock {
        private Instant now;

        private MutableTestClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}

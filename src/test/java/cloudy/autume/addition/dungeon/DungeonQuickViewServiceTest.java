package cloudy.autume.addition.dungeon;

import cloudy.autume.addition.network.QcaApiClient;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DungeonQuickViewServiceTest {
    @Test
    void coalescesConcurrentRequestsForTheSamePlayerAndFloor() {
        FakeGateway gateway = new FakeGateway();
        DungeonQuickViewService service = new DungeonQuickViewService(gateway,
                Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneOffset.UTC));
        CompletableFuture<DungeonQuickViewSnapshot> first = service.load("GhostsTM", "M7");
        CompletableFuture<DungeonQuickViewSnapshot> second = service.load("GhostsTM", "M7");
        assertSame(first, second);
        assertEquals(1, gateway.calls);
        gateway.future.complete(new QcaApiClient.Response(503, "{}", Map.of()));
    }

    @Test
    void resetCancelsTheUnderlyingTransport() {
        FakeGateway gateway = new FakeGateway();
        DungeonQuickViewService service = new DungeonQuickViewService(gateway,
                Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneOffset.UTC));
        CompletableFuture<DungeonQuickViewSnapshot> result = service.load("GhostsTM", "F7");

        service.reset();

        assertTrue(result.isCancelled());
        assertTrue(gateway.future.isCancelled());
    }

    @Test
    void rejectsAValidButDifferentResponsePlayerBeforeCreatingTheKickAction() {
        FakeGateway gateway = new FakeGateway();
        gateway.future.complete(new QcaApiClient.Response(200,
                DungeonQuickViewSnapshotTest.JSON.replace(
                        "\"name\":\"GhostsTM\"", "\"name\":\"OtherPlayer\""), Map.of()));
        DungeonQuickViewService service = new DungeonQuickViewService(gateway,
                Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneOffset.UTC));

        CompletionException failure = assertThrows(CompletionException.class,
                () -> service.load("GhostsTM", "F7").join());
        assertTrue(failure.getCause() instanceof DungeonQuickViewException);
    }

    private static final class FakeGateway implements DungeonQuickViewService.Gateway {
        private final CompletableFuture<QcaApiClient.Response> future = new CompletableFuture<>();
        private int calls;
        @Override
        public CompletableFuture<QcaApiClient.Response> fetch(String target, String floor) {
            calls++;
            return future;
        }
    }
}

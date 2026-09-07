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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void classifiesKnownMissingPlayerResponsesWithoutOpeningAServiceOutage() {
        DungeonQuickViewService service = serviceReturning(404, """
                {"error":{"code":"PLAYER_NOT_FOUND","message":"That Minecraft player does not exist."}}
                """);

        CompletionException failure = assertThrows(CompletionException.class,
                () -> service.load("GhostsTM", "F7").join());
        DungeonQuickViewException problem = (DungeonQuickViewException) failure.getCause();
        assertFalse(problem.isServiceFailure());
        assertEquals("That Minecraft player does not exist.", problem.getMessage());
    }

    @Test
    void treatsAnUnknown404AsAServiceContractFailure() {
        DungeonQuickViewService service = serviceReturning(404, "{\"detail\":\"Not Found\"}");

        CompletionException failure = assertThrows(CompletionException.class,
                () -> service.load("GhostsTM", "M7").join());
        DungeonQuickViewException problem = (DungeonQuickViewException) failure.getCause();
        assertTrue(problem.isServiceFailure());
    }

    @Test
    void exposesFreshSessionCacheWithoutStartingAnotherRequest() {
        DungeonQuickViewService service = serviceReturning(200, DungeonQuickViewSnapshotTest.JSON);

        DungeonQuickViewSnapshot loaded = service.load("GhostsTM", "M7").join();

        assertSame(loaded, service.cached("ghoststm", "m7"));
        assertNull(service.cached("GhostsTM", "F7"));
    }

    private static DungeonQuickViewService serviceReturning(int status, String body) {
        return new DungeonQuickViewService(
                (target, floor) -> CompletableFuture.completedFuture(
                        new QcaApiClient.Response(status, body, Map.of())),
                Clock.fixed(Instant.parse("2026-09-07T00:00:00Z"), ZoneOffset.UTC));
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

package cloudy.autume.addition.dungeon;

import cloudy.autume.addition.network.QcaApiClient;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
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
    void rejectsAResponseBoundToADifferentQueryName() {
        FakeGateway gateway = new FakeGateway();
        gateway.future.complete(new QcaApiClient.Response(200,
                DungeonQuickViewSnapshotTest.JSON.replace(
                        "\"queryName\":\"GhostsTM\"", "\"queryName\":\"OtherPlayer\""), Map.of()));
        DungeonQuickViewService service = new DungeonQuickViewService(gateway,
                Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneOffset.UTC));

        CompletionException failure = assertThrows(CompletionException.class,
                () -> service.load("GhostsTM", "M7").join());
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

    @Test
    void admissionLoadBypassesProfileCacheSoAnOldFailureCannotAuthorizeAnAction() {
        String cachedFailure = DungeonQuickViewSnapshotTest.JSON_WITH_REQUIREMENTS.replace(
                "\"floorCompletions\":{\"state\":\"KNOWN\",\"value\":312}",
                "\"floorCompletions\":{\"state\":\"KNOWN\",\"value\":1}");
        CompletableFuture<QcaApiClient.Response> first = CompletableFuture.completedFuture(
                new QcaApiClient.Response(200, cachedFailure, Map.of()));
        CompletableFuture<QcaApiClient.Response> second = new CompletableFuture<>();
        SequencedGateway gateway = new SequencedGateway(first, second);
        DungeonQuickViewService service = new DungeonQuickViewService(gateway,
                Clock.fixed(Instant.parse("2026-09-07T00:00:00Z"), ZoneOffset.UTC));

        DungeonQuickViewSnapshot displaySnapshot = service.load("GhostsTM", "M7").join();
        assertEquals(1L, displaySnapshot.requirementsEvidence().floorCompletions().value());
        assertSame(displaySnapshot, service.cached("GhostsTM", "M7"));

        CompletableFuture<DungeonQuickViewSnapshot> admission =
                service.loadForAdmission("GhostsTM", "M7");

        assertEquals(2, gateway.calls);
        assertFalse(admission.isDone());
        // The old result remains usable for Profile presentation, but the
        // admission path is waiting for a new acquisition instead of using it.
        assertSame(displaySnapshot, service.cached("GhostsTM", "M7"));

        second.complete(new QcaApiClient.Response(200,
                DungeonQuickViewSnapshotTest.JSON_WITH_REQUIREMENTS, Map.of()));
        DungeonQuickViewSnapshot decisionSnapshot = admission.join();
        assertEquals(312L,
                decisionSnapshot.requirementsEvidence().floorCompletions().value());
        assertSame(decisionSnapshot, service.cached("GhostsTM", "M7"));
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

    private static final class SequencedGateway implements DungeonQuickViewService.Gateway {
        private final Queue<CompletableFuture<QcaApiClient.Response>> responses;
        private int calls;

        @SafeVarargs
        private SequencedGateway(CompletableFuture<QcaApiClient.Response>... responses) {
            this.responses = new ArrayDeque<>(java.util.List.of(responses));
        }

        @Override
        public CompletableFuture<QcaApiClient.Response> fetch(String target, String floor) {
            calls++;
            return responses.remove();
        }
    }
}

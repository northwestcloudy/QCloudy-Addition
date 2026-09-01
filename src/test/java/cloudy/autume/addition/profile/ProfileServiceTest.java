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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProfileServiceTest {
    @Test
    void loadsAsynchronouslyThenReusesTheBoundedSessionCache() {
        MutableClock clock = new MutableClock(ProfileFixtures.NOW);
        FakeGateway gateway = new FakeGateway();
        gateway.profile = CompletableFuture.completedFuture(ok(ProfileFixtures.validJson()));
        ProfileService service = new ProfileService(gateway, clock);

        ProfileLoadResult first = service.load("NorthwestCloudy", null).future().join();
        ProfileLoadResult second = service.load("northwestcloudy", "").future().join();

        assertFalse(first.fromSessionCache());
        assertTrue(second.fromSessionCache());
        assertEquals(1, gateway.profileCalls);
        assertEquals(ProfileLoadStatus.READY, second.status());
    }

    @Test
    void lazyMuseumLoadMergesWithoutMutatingBaseAndUsesItsOwnCache() {
        MutableClock clock = new MutableClock(ProfileFixtures.NOW);
        FakeGateway gateway = new FakeGateway();
        gateway.profile = CompletableFuture.completedFuture(ok(ProfileFixtures.validJson()));
        gateway.museum = CompletableFuture.completedFuture(ok(ProfileFixtures.supplementJson(
                ProfileSectionId.MUSEUM, ProfileFixtures.NOW.plus(Duration.ofHours(6)))));
        ProfileService service = new ProfileService(gateway, clock);
        ProfileSnapshot base = service.load("NorthwestCloudy", null).future().join().snapshot();

        ProfileLoadResult loaded = service.loadSection(base, ProfileSectionId.MUSEUM)
                .future().join();
        ProfileLoadResult cached = service.loadSection(base, ProfileSectionId.MUSEUM)
                .future().join();
        ProfileLoadResult refreshedMain = service.load("NorthwestCloudy", null).future().join();

        assertEquals(ProfileSectionStatus.NOT_LOADED,
                base.section(ProfileSectionId.MUSEUM).orElseThrow().status());
        assertEquals(ProfileSectionStatus.AVAILABLE,
                loaded.snapshot().section(ProfileSectionId.MUSEUM).orElseThrow().status());
        assertTrue(loaded.snapshot().section(ProfileSectionId.MUSEUM).orElseThrow()
                .payload().get("loaded").getAsBoolean());
        assertFalse(loaded.fromSessionCache());
        assertTrue(cached.fromSessionCache());
        assertEquals(1, gateway.museumCalls);
        assertEquals(ProfileSectionStatus.AVAILABLE,
                refreshedMain.snapshot().section(ProfileSectionId.MUSEUM).orElseThrow().status());
    }

    @Test
    void failedSupplementLeavesTheMainSnapshotUsable() {
        MutableClock clock = new MutableClock(ProfileFixtures.NOW);
        FakeGateway gateway = new FakeGateway();
        gateway.museum = CompletableFuture.completedFuture(new QcaApiClient.Response(503, """
                {"schemaVersion":1,"error":{
                  "code":"SERVICE_UNAVAILABLE","message":"Try later."
                }}
                """, Map.of()));
        ProfileService service = new ProfileService(gateway, clock);
        ProfileSnapshot base = ProfileJsonParser.parse(ProfileFixtures.validJson());

        ProfileException failure = failure(service.loadSection(base, ProfileSectionId.MUSEUM));

        assertEquals(ProfileException.Code.SERVICE_UNAVAILABLE, failure.code());
        assertEquals(ProfileSectionStatus.NOT_LOADED,
                base.section(ProfileSectionId.MUSEUM).orElseThrow().status());
        assertEquals("NorthwestCloudy", base.identity().name());
    }

    @Test
    void replacingARequestCancelsItAndDropsItsLateResponse() {
        MutableClock clock = new MutableClock(ProfileFixtures.NOW);
        FakeGateway gateway = new FakeGateway();
        NonCancellableFuture<QcaApiClient.Response> firstNetwork = new NonCancellableFuture<>();
        gateway.profile = firstNetwork;
        ProfileService service = new ProfileService(gateway, clock);

        ProfileRequest first = service.load("NorthwestCloudy", null);
        CompletableFuture<QcaApiClient.Response> secondNetwork = new CompletableFuture<>();
        gateway.profile = secondNetwork;
        ProfileRequest second = service.load("NorthwestCloudy", ProfileFixtures.PROFILE_ID);
        firstNetwork.complete(ok(ProfileFixtures.validJson()));
        secondNetwork.complete(ok(ProfileFixtures.validJson()));

        assertEquals(ProfileException.Code.CANCELLED, failure(first).code());
        assertEquals(ProfileFixtures.PROFILE_ID,
                second.future().join().snapshot().selectedProfileId());
        assertTrue(service.isCurrent(second.generation()));
        assertEquals(2, gateway.profileCalls);
    }

    @Test
    void canonicalizesBackendAliasesAndPreservesRetryAfter() {
        FakeGateway gateway = new FakeGateway();
        gateway.profile = CompletableFuture.completedFuture(new QcaApiClient.Response(404, """
                {"schemaVersion":1,"error":{
                  "code":"SKYBLOCK_PROFILES_NOT_FOUND","message":"No visible profiles."
                }}
                """, Map.of("Retry-After", List.of("12"))));
        ProfileService service = new ProfileService(gateway,
                new MutableClock(ProfileFixtures.NOW));

        ProfileException failure = failure(service.load("NorthwestCloudy", null));

        assertEquals(ProfileException.Code.NO_SKYBLOCK_PROFILES, failure.code());
        assertEquals(Duration.ofSeconds(12), failure.retryAfter());
        assertEquals(404, failure.httpStatus());
    }

    @Test
    void rejectsInvalidTargetBeforeCallingTheGateway() {
        FakeGateway gateway = new FakeGateway();
        ProfileService service = new ProfileService(gateway,
                new MutableClock(ProfileFixtures.NOW));

        ProfileException failure = failure(service.load("bad player!", null));

        assertEquals(ProfileException.Code.INVALID_TARGET, failure.code());
        assertEquals(0, gateway.profileCalls);
    }

    private static ProfileException failure(ProfileRequest request) {
        CompletionException wrapper = assertThrows(CompletionException.class,
                () -> request.future().join());
        return (ProfileException) wrapper.getCause();
    }

    private static QcaApiClient.Response ok(String body) {
        return new QcaApiClient.Response(200, body, Map.of());
    }

    private static final class NonCancellableFuture<T> extends CompletableFuture<T> {
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }
    }

    private static final class FakeGateway implements ProfileService.Gateway {
        private CompletableFuture<QcaApiClient.Response> profile = new CompletableFuture<>();
        private CompletableFuture<QcaApiClient.Response> museum = new CompletableFuture<>();
        private CompletableFuture<QcaApiClient.Response> garden = new CompletableFuture<>();
        private int profileCalls;
        private int museumCalls;

        @Override
        public CompletableFuture<QcaApiClient.Response> fetchProfile(
                String target, String profileId) {
            profileCalls++;
            return profile;
        }

        @Override
        public CompletableFuture<QcaApiClient.Response> fetchMuseum(
                String playerUuid, String profileId) {
            museumCalls++;
            return museum;
        }

        @Override
        public CompletableFuture<QcaApiClient.Response> fetchGarden(
                String playerUuid, String profileId) {
            return garden;
        }
    }
}

package cloudy.autume.addition.profile;

import cloudy.autume.addition.network.QcaApiClient;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/** Coordinates validation, generation cancellation, parsing and the ten-minute session cache. */
public final class ProfileService {
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern UUID = Pattern.compile(
            "(?:[0-9A-Fa-f]{32}|[0-9A-Fa-f]{8}(?:-[0-9A-Fa-f]{4}){3}-[0-9A-Fa-f]{12})");

    private final Gateway gateway;
    private final SessionProfileCache cache;
    private final SessionSupplementCache supplementCache;
    private final AtomicLong generation = new AtomicLong();
    private final AtomicReference<ProfileRequest> current = new AtomicReference<>();

    public ProfileService(QcaApiClient apiClient, Clock clock) {
        this(adapt(Objects.requireNonNull(apiClient, "apiClient")), clock);
    }

    ProfileService(Gateway gateway, Clock clock) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        Clock checkedClock = Objects.requireNonNull(clock, "clock");
        this.cache = new SessionProfileCache(checkedClock);
        this.supplementCache = new SessionSupplementCache(checkedClock);
    }

    /** Construction is local-only; it performs no request and does not block. */
    public static ProfileService createDefault(String userAgent) {
        return new ProfileService(QcaApiClient.createDefault(userAgent), Clock.systemUTC());
    }

    public ProfileRequest load(String playerOrUuid, String profileIdOrNull) {
        PendingRequest pending = beginRequest();
        long requestGeneration = pending.generation();
        CompletableFuture<ProfileLoadResult> output = pending.output();
        ProfileRequest request = pending.request();

        final String target;
        final String profileId;
        try {
            target = normalizeTarget(playerOrUuid);
            profileId = normalizeProfileId(profileIdOrNull);
        } catch (ProfileException exception) {
            output.completeExceptionally(exception);
            return request;
        }

        Optional<ProfileLoadResult> cached = cache.get(target, profileId);
        if (cached.isPresent()) {
            if (isCurrent(requestGeneration)) {
                output.complete(mergeCachedSupplements(cached.get()));
            } else {
                completeReplaced(output);
            }
            return request;
        }

        CompletableFuture<QcaApiClient.Response> network;
        try {
            network = gateway.fetchProfile(target, profileId);
        } catch (RuntimeException exception) {
            output.completeExceptionally(mapTransportFailure(exception));
            return request;
        }
        pending.upstream().set(network);
        network.whenComplete((response, failure) -> {
            if (!isCurrent(requestGeneration)) {
                output.completeExceptionally(new ProfileException(ProfileException.Code.CANCELLED,
                        "A newer profile request replaced this request."));
                return;
            }
            if (failure != null) {
                output.completeExceptionally(mapTransportFailure(failure));
                return;
            }
            try {
                ProfileLoadResult result = parseResponse(response);
                if (!isCurrent(requestGeneration)) {
                    throw new ProfileException(ProfileException.Code.CANCELLED,
                            "A newer profile request replaced this request.");
                }
                cache.put(target, profileId, result);
                output.complete(mergeCachedSupplements(result));
            } catch (RuntimeException exception) {
                output.completeExceptionally(exception);
            }
        });
        return request;
    }

    /**
     * Lazily loads and merges one supplementary section. A failed future never
     * mutates the supplied main snapshot, so callers can keep displaying it.
     */
    public ProfileRequest loadSection(ProfileSnapshot baseSnapshot,
                                      ProfileSectionId sectionId) {
        Objects.requireNonNull(baseSnapshot, "baseSnapshot");
        if (sectionId != ProfileSectionId.MUSEUM && sectionId != ProfileSectionId.GARDEN) {
            throw new IllegalArgumentException("Only Museum and Garden support lazy loading");
        }

        PendingRequest pending = beginRequest();
        long requestGeneration = pending.generation();
        CompletableFuture<ProfileLoadResult> output = pending.output();
        ProfileRequest request = pending.request();
        String playerUuid = baseSnapshot.identity().uuid();
        String profileId = baseSnapshot.selectedProfileId();

        Optional<ProfileSupplement> cached = supplementCache.get(
                playerUuid, profileId, sectionId);
        if (cached.isPresent()) {
            if (isCurrent(requestGeneration)) {
                output.complete(ProfileLoadResult.network(
                        baseSnapshot.withSupplement(cached.get())).asSessionCacheHit());
            } else {
                completeReplaced(output);
            }
            return request;
        }

        CompletableFuture<QcaApiClient.Response> network;
        try {
            network = sectionId == ProfileSectionId.MUSEUM
                    ? gateway.fetchMuseum(playerUuid, profileId)
                    : gateway.fetchGarden(playerUuid, profileId);
        } catch (RuntimeException exception) {
            output.completeExceptionally(mapTransportFailure(exception));
            return request;
        }
        pending.upstream().set(network);
        network.whenComplete((response, failure) -> {
            if (!isCurrent(requestGeneration)) {
                completeReplaced(output);
                return;
            }
            if (failure != null) {
                output.completeExceptionally(mapTransportFailure(failure));
                return;
            }
            try {
                ProfileSupplement supplement = parseSupplementResponse(response,
                        playerUuid, profileId, sectionId);
                if (!isCurrent(requestGeneration)) {
                    throw replacedException();
                }
                supplementCache.put(supplement);
                output.complete(ProfileLoadResult.network(
                        baseSnapshot.withSupplement(supplement)));
            } catch (RuntimeException exception) {
                output.completeExceptionally(exception);
            }
        });
        return request;
    }

    public void cancelCurrent() {
        ProfileRequest request = current.get();
        if (request != null) request.cancel();
    }

    public boolean isCurrent(long requestGeneration) {
        ProfileRequest request = current.get();
        return request != null && request.generation() == requestGeneration
                && generation.get() == requestGeneration;
    }

    public void clearSessionCache() {
        cache.clear();
        supplementCache.clear();
    }

    private static ProfileLoadResult parseResponse(QcaApiClient.Response response) {
        int status = response.statusCode();
        if (status == 200) return ProfileLoadResult.network(ProfileJsonParser.parse(response.body()));

        throw responseFailure(response, ProfileException.Code.PLAYER_NOT_FOUND);
    }

    private static ProfileSupplement parseSupplementResponse(QcaApiClient.Response response,
                                                              String playerUuid,
                                                              String profileId,
                                                              ProfileSectionId sectionId) {
        if (response.statusCode() == 200) {
            return ProfileJsonParser.parseSupplement(response.body(),
                    playerUuid, profileId, sectionId);
        }
        throw responseFailure(response, ProfileException.Code.PROFILE_NOT_FOUND);
    }

    private static ProfileException responseFailure(QcaApiClient.Response response,
                                                    ProfileException.Code notFoundCode) {
        int status = response.statusCode();

        Duration retryAfter = retryAfter(response);
        if (response.body().contains("\"error\"")) {
            ProfileException parsed = ProfileJsonParser.parseError(
                    response.body(), status, retryAfter);
            if (parsed.code() != ProfileException.Code.INVALID_RESPONSE) return parsed;
        }
        return switch (status) {
            case 400, 422 -> new ProfileException(ProfileException.Code.INVALID_TARGET,
                    "Enter a valid Minecraft player name or UUID.", retryAfter, status, null);
            case 401, 403 -> new ProfileException(ProfileException.Code.UNAUTHORIZED,
                    "The QCA profile service is not authorised.", retryAfter, status, null);
            case 404 -> new ProfileException(notFoundCode,
                    notFoundCode == ProfileException.Code.PROFILE_NOT_FOUND
                            ? "That SkyBlock profile could not be found."
                            : "That Minecraft player could not be found.",
                    retryAfter, status, null);
            case 413 -> new ProfileException(ProfileException.Code.RESPONSE_TOO_LARGE,
                    "The profile response was too large.", retryAfter, status, null);
            case 429 -> new ProfileException(ProfileException.Code.RATE_LIMITED,
                    "The profile service is rate limited. Try again later.",
                    retryAfter, status, null);
            case 502, 504 -> new ProfileException(ProfileException.Code.UPSTREAM_UNAVAILABLE,
                    "Hypixel profile data is temporarily unavailable.",
                    retryAfter, status, null);
            case 503 -> new ProfileException(ProfileException.Code.SERVICE_UNAVAILABLE,
                    "The QCA profile service is temporarily unavailable.",
                    retryAfter, status, null);
            default -> new ProfileException(ProfileException.Code.INVALID_RESPONSE,
                    "The profile service returned HTTP " + status + ".",
                    retryAfter, status, null);
        };
    }

    private PendingRequest beginRequest() {
        ProfileRequest previous = current.get();
        if (previous != null) previous.cancel();

        long requestGeneration = generation.incrementAndGet();
        CompletableFuture<ProfileLoadResult> output = new CompletableFuture<>();
        AtomicReference<CompletableFuture<?>> upstream = new AtomicReference<>();
        AtomicReference<ProfileRequest> self = new AtomicReference<>();
        ProfileRequest request = new ProfileRequest(requestGeneration, output, () -> {
            generation.compareAndSet(requestGeneration, requestGeneration + 1);
            CompletableFuture<?> active = upstream.get();
            if (active != null) active.cancel(true);
            output.completeExceptionally(new ProfileException(ProfileException.Code.CANCELLED,
                    "The profile request was cancelled."));
            ProfileRequest own = self.get();
            if (own != null) current.compareAndSet(own, null);
        });
        self.set(request);
        current.set(request);
        return new PendingRequest(requestGeneration, output, upstream, request);
    }

    private ProfileLoadResult mergeCachedSupplements(ProfileLoadResult result) {
        ProfileSnapshot snapshot = result.snapshot();
        boolean changed = false;
        for (ProfileSectionId sectionId : new ProfileSectionId[]{
                ProfileSectionId.MUSEUM, ProfileSectionId.GARDEN}) {
            Optional<ProfileSupplement> supplement = supplementCache.get(
                    snapshot.identity().uuid(), snapshot.selectedProfileId(), sectionId);
            if (supplement.isPresent()) {
                snapshot = snapshot.withSupplement(supplement.get());
                changed = true;
            }
        }
        if (!changed) return result;
        ProfileLoadResult merged = ProfileLoadResult.network(snapshot);
        return result.fromSessionCache() ? merged.asSessionCacheHit() : merged;
    }

    private static void completeReplaced(CompletableFuture<ProfileLoadResult> output) {
        output.completeExceptionally(replacedException());
    }

    private static ProfileException replacedException() {
        return new ProfileException(ProfileException.Code.CANCELLED,
                "A newer profile request replaced this request.");
    }

    private static ProfileException mapTransportFailure(Throwable failure) {
        Throwable cause = unwrap(failure);
        if (cause instanceof ProfileException profileException) return profileException;
        if (cause instanceof QcaApiClient.ResponseTooLargeException) {
            return new ProfileException(ProfileException.Code.RESPONSE_TOO_LARGE,
                    "The profile response was too large.", Duration.ZERO, 0, cause);
        }
        if (cause instanceof CancellationException) {
            return new ProfileException(ProfileException.Code.CANCELLED,
                    "The profile request was cancelled.", Duration.ZERO, 0, cause);
        }
        if (cause instanceof HttpTimeoutException || cause instanceof IOException) {
            return new ProfileException(ProfileException.Code.SERVICE_UNAVAILABLE,
                    "The QCA profile service is temporarily unavailable.",
                    Duration.ZERO, 0, cause);
        }
        return new ProfileException(ProfileException.Code.SERVICE_UNAVAILABLE,
                "The QCA profile service request failed.", Duration.ZERO, 0, cause);
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static Duration retryAfter(QcaApiClient.Response response) {
        String raw = response.firstHeader("Retry-After");
        if (raw.isBlank()) raw = response.firstHeader("RateLimit-Reset");
        try {
            long seconds = Long.parseLong(raw.trim());
            if (seconds <= 0) return Duration.ZERO;
            return Duration.ofSeconds(Math.min(seconds, 3600));
        } catch (RuntimeException ignored) {
            return Duration.ZERO;
        }
    }

    static String normalizeTarget(String value) {
        if (value == null) throw invalidTarget();
        String trimmed = value.trim();
        if (PLAYER_NAME.matcher(trimmed).matches()) return trimmed;
        if (UUID.matcher(trimmed).matches()) {
            return trimmed.replace("-", "").toLowerCase(Locale.ROOT);
        }
        throw invalidTarget();
    }

    static String normalizeProfileId(String value) {
        if (value == null || value.isBlank()) return "";
        String trimmed = value.trim();
        if (!UUID.matcher(trimmed).matches()) {
            throw new ProfileException(ProfileException.Code.INVALID_TARGET,
                    "The selected SkyBlock profile ID is invalid.");
        }
        return trimmed.replace("-", "").toLowerCase(Locale.ROOT);
    }

    private static ProfileException invalidTarget() {
        return new ProfileException(ProfileException.Code.INVALID_TARGET,
                "Enter a valid Minecraft player name or UUID.");
    }

    private static Gateway adapt(QcaApiClient apiClient) {
        return new Gateway() {
            @Override
            public CompletableFuture<QcaApiClient.Response> fetchProfile(
                    String target, String profileId) {
                return apiClient.fetchProfile(target, profileId);
            }

            @Override
            public CompletableFuture<QcaApiClient.Response> fetchMuseum(
                    String playerUuid, String profileId) {
                return apiClient.fetchMuseum(playerUuid, profileId);
            }

            @Override
            public CompletableFuture<QcaApiClient.Response> fetchGarden(
                    String playerUuid, String profileId) {
                return apiClient.fetchGarden(playerUuid, profileId);
            }
        };
    }

    interface Gateway {
        CompletableFuture<QcaApiClient.Response> fetchProfile(String target, String profileId);

        CompletableFuture<QcaApiClient.Response> fetchMuseum(String playerUuid, String profileId);

        CompletableFuture<QcaApiClient.Response> fetchGarden(String playerUuid, String profileId);
    }

    private record PendingRequest(long generation,
                                  CompletableFuture<ProfileLoadResult> output,
                                  AtomicReference<CompletableFuture<?>> upstream,
                                  ProfileRequest request) {
    }
}

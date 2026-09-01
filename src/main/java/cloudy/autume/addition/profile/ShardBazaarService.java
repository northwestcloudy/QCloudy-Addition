package cloudy.autume.addition.profile;

import cloudy.autume.addition.network.QcaApiClient;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Two-entry in-process cache for Shard Bazaar prices. */
public final class ShardBazaarService {
    private final Gateway gateway;
    private final Clock clock;
    private final Map<ShardBazaarSide, Entry> cache = new EnumMap<>(ShardBazaarSide.class);
    private final Map<ShardBazaarSide, CompletableFuture<ShardBazaarLoadResult>> inFlight =
            new EnumMap<>(ShardBazaarSide.class);

    public ShardBazaarService(QcaApiClient apiClient, Clock clock) {
        this(Objects.requireNonNull(apiClient, "apiClient")::fetchShardBazaarPrices, clock);
    }

    ShardBazaarService(Gateway gateway, Clock clock) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized CompletableFuture<ShardBazaarLoadResult> load(ShardBazaarSide side) {
        Objects.requireNonNull(side, "side");
        Instant now = clock.instant();
        Entry cached = cache.get(side);
        if (cached != null && now.isBefore(cached.validUntil)) {
            return CompletableFuture.completedFuture(
                    new ShardBazaarLoadResult(cached.snapshot, true));
        }
        if (cached != null) cache.remove(side);
        CompletableFuture<ShardBazaarLoadResult> existing = inFlight.get(side);
        if (existing != null) return existing;

        CompletableFuture<QcaApiClient.Response> network;
        try {
            network = gateway.fetch(side);
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(mapTransportFailure(exception));
        }
        CompletableFuture<ShardBazaarLoadResult> result = network.handle((response, failure) -> {
            if (failure != null) throw new CompletionException(mapTransportFailure(failure));
            return response;
        }).thenApply(response -> {
            if (response.statusCode() != 200) {
                throw responseFailure(response);
            }
            ShardBazaarSnapshot snapshot = ShardBazaarSnapshot.parse(response.body());
            if (snapshot.side() != side) {
                throw new ProfileException(ProfileException.Code.INVALID_RESPONSE,
                        "The Shard Bazaar response used the wrong side.");
            }
            Instant cacheUntil = snapshot.sessionCacheBoundary(clock.instant());
            synchronized (ShardBazaarService.this) {
                if (cacheUntil.isAfter(clock.instant())) {
                    cache.put(side, new Entry(snapshot, cacheUntil));
                }
            }
            return new ShardBazaarLoadResult(snapshot, false);
        });
        inFlight.put(side, result);
        result.whenComplete((ignored, failure) -> {
            synchronized (ShardBazaarService.this) {
                if (inFlight.get(side) == result) inFlight.remove(side);
            }
        });
        return result;
    }

    public synchronized void clearSessionCache() {
        cache.clear();
    }

    private static ProfileException responseFailure(QcaApiClient.Response response) {
        int status = response.statusCode();
        Duration retryAfter = retryAfter(response);
        if (response.body().contains("\"error\"")) {
            ProfileException parsed = ProfileJsonParser.parseError(
                    response.body(), status, retryAfter);
            if (parsed.code() != ProfileException.Code.INVALID_RESPONSE) return parsed;
        }
        ProfileException.Code code = switch (status) {
            case 429 -> ProfileException.Code.RATE_LIMITED;
            case 502, 504 -> ProfileException.Code.UPSTREAM_UNAVAILABLE;
            default -> ProfileException.Code.SERVICE_UNAVAILABLE;
        };
        return new ProfileException(code,
                "Shard Bazaar prices are temporarily unavailable.",
                retryAfter, status, null);
    }

    private static ProfileException mapTransportFailure(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof ProfileException profileException) return profileException;
        if (cause instanceof QcaApiClient.ResponseTooLargeException) {
            return new ProfileException(ProfileException.Code.RESPONSE_TOO_LARGE,
                    "The Shard Bazaar response was too large.", Duration.ZERO, 0, cause);
        }
        if (cause instanceof CancellationException) {
            return new ProfileException(ProfileException.Code.CANCELLED,
                    "The Shard Bazaar request was cancelled.", Duration.ZERO, 0, cause);
        }
        if (cause instanceof HttpTimeoutException || cause instanceof IOException) {
            return new ProfileException(ProfileException.Code.SERVICE_UNAVAILABLE,
                    "Shard Bazaar prices are temporarily unavailable.",
                    Duration.ZERO, 0, cause);
        }
        return new ProfileException(ProfileException.Code.SERVICE_UNAVAILABLE,
                "The Shard Bazaar request failed.", Duration.ZERO, 0, cause);
    }

    private static Duration retryAfter(QcaApiClient.Response response) {
        String raw = response.firstHeader("Retry-After");
        try {
            long seconds = Long.parseLong(raw.trim());
            return seconds <= 0 ? Duration.ZERO
                    : Duration.ofSeconds(Math.min(seconds, 3600));
        } catch (RuntimeException ignored) {
            return Duration.ZERO;
        }
    }

    private record Entry(ShardBazaarSnapshot snapshot, Instant validUntil) {
    }

    @FunctionalInterface
    interface Gateway {
        CompletableFuture<QcaApiClient.Response> fetch(ShardBazaarSide side);
    }
}

package cloudy.autume.addition.dungeon;

import cloudy.autume.addition.network.QcaApiClient;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** One-request Dungeon snapshot loader with short session caching and coalescing. */
public final class DungeonQuickViewService {
    static final Duration SESSION_CACHE_TTL = Duration.ofSeconds(60);

    private final Gateway gateway;
    private final Clock clock;
    private final Map<String, Entry> cache = new HashMap<>();
    private final Map<String, CompletableFuture<DungeonQuickViewSnapshot>> inFlight = new HashMap<>();

    public DungeonQuickViewService(QcaApiClient apiClient, Clock clock) {
        this(Objects.requireNonNull(apiClient, "apiClient")::fetchDungeonQuickView, clock);
    }

    DungeonQuickViewService(Gateway gateway, Clock clock) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized CompletableFuture<DungeonQuickViewSnapshot> load(String playerName, String floor) {
        String target = normalizedPlayer(playerName);
        String normalizedFloor = normalizedFloor(floor);
        String key = target.toLowerCase(java.util.Locale.ROOT) + '|' + normalizedFloor;
        Instant now = clock.instant();
        Entry existingCache = cache.get(key);
        if (existingCache != null && now.isBefore(existingCache.validUntil)) {
            return CompletableFuture.completedFuture(existingCache.snapshot);
        }
        cache.remove(key);
        CompletableFuture<DungeonQuickViewSnapshot> existing = inFlight.get(key);
        if (existing != null) return existing;

        CompletableFuture<QcaApiClient.Response> network;
        try {
            network = gateway.fetch(target, normalizedFloor);
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(mapFailure(exception));
        }
        CompletableFuture<DungeonQuickViewSnapshot> result = network.handle((response, failure) -> {
            if (failure != null) throw new CompletionException(mapFailure(failure));
            if (response.statusCode() != 200) throw responseFailure(response);
            return DungeonQuickViewSnapshot.parse(response.body());
        }).thenApply(snapshot -> {
            if (!snapshot.playerName().equalsIgnoreCase(target)) {
                throw new DungeonQuickViewException(
                        "The Dungeon quick-view response named a different player.");
            }
            synchronized (DungeonQuickViewService.this) {
                cache.put(key, new Entry(snapshot, clock.instant().plus(SESSION_CACHE_TTL)));
            }
            return snapshot;
        });
        inFlight.put(key, result);
        result.whenComplete((ignored, failure) -> {
            if (result.isCancelled()) network.cancel(true);
            synchronized (DungeonQuickViewService.this) {
                if (inFlight.get(key) == result) inFlight.remove(key);
            }
        });
        return result;
    }

    public synchronized void reset() {
        cache.clear();
        for (CompletableFuture<DungeonQuickViewSnapshot> future : inFlight.values()) future.cancel(true);
        inFlight.clear();
    }

    private static DungeonQuickViewException responseFailure(QcaApiClient.Response response) {
        String message = "Dungeon profile data is temporarily unavailable.";
        try {
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonObject error = root.getAsJsonObject("error");
            JsonElement rawMessage = error == null ? null : error.get("message");
            if (rawMessage != null && rawMessage.isJsonPrimitive()
                    && rawMessage.getAsJsonPrimitive().isString()) message = rawMessage.getAsString();
        } catch (RuntimeException ignored) { }
        return new DungeonQuickViewException(message);
    }

    private static DungeonQuickViewException mapFailure(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
        if (cause instanceof DungeonQuickViewException exception) return exception;
        if (cause instanceof QcaApiClient.ResponseTooLargeException) {
            return new DungeonQuickViewException("Dungeon profile response was too large.", cause);
        }
        if (cause instanceof CancellationException) {
            return new DungeonQuickViewException("Dungeon profile request was cancelled.", cause);
        }
        if (cause instanceof HttpTimeoutException || cause instanceof IOException) {
            return new DungeonQuickViewException("Dungeon profile service is temporarily unavailable.", cause);
        }
        return new DungeonQuickViewException("Dungeon profile request failed.", cause);
    }

    private static String normalizedPlayer(String value) {
        String player = value == null ? "" : value.trim();
        if (!player.matches("[A-Za-z0-9_]{3,16}")) throw new IllegalArgumentException("Invalid player name");
        return player;
    }

    private static String normalizedFloor(String value) {
        String floor = value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
        if (!floor.isEmpty() && !floor.matches("(?:E|[FM][1-7])")) {
            throw new IllegalArgumentException("Invalid Dungeon floor");
        }
        return floor;
    }

    private record Entry(DungeonQuickViewSnapshot snapshot, Instant validUntil) { }

    @FunctionalInterface
    interface Gateway {
        CompletableFuture<QcaApiClient.Response> fetch(String target, String floor);
    }
}

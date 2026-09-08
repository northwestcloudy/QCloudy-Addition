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
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** One-request Dungeon snapshot loader with short session caching and coalescing. */
public final class DungeonQuickViewService {
    static final Duration SESSION_CACHE_TTL = Duration.ofSeconds(60);
    private static final Set<String> PLAYER_UNAVAILABLE_CODES = Set.of(
            "PLAYER_NOT_FOUND",
            "HYPIXEL_PLAYER_NOT_FOUND",
            "SKYBLOCK_PROFILES_NOT_FOUND",
            "SKYBLOCK_MEMBER_NOT_FOUND");

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
        return load(playerName, floor, true);
    }

    /**
     * Loads evidence for an automatic admission decision without reusing the
     * successful Profile-display cache. A request already in flight may still
     * be coalesced because it represents the same current network acquisition.
     */
    synchronized CompletableFuture<DungeonQuickViewSnapshot> loadForAdmission(
            String playerName, String floor) {
        return load(playerName, floor, false);
    }

    private CompletableFuture<DungeonQuickViewSnapshot> load(
            String playerName, String floor, boolean allowSuccessfulCache) {
        String target = normalizedPlayer(playerName);
        String normalizedFloor = normalizedFloor(floor);
        String key = cacheKey(target, normalizedFloor);
        Instant now = clock.instant();
        if (allowSuccessfulCache) {
            Entry existingCache = cache.get(key);
            if (existingCache != null && now.isBefore(existingCache.validUntil)) {
                return CompletableFuture.completedFuture(existingCache.snapshot);
            }
            cache.remove(key);
        }
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
            if (!snapshot.queryName().isBlank()
                    && !snapshot.queryName().equalsIgnoreCase(target)) {
                throw new DungeonQuickViewException(
                        "The Dungeon quick-view response was for a different query.");
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

    /** Returns fresh session data without starting network work. */
    synchronized DungeonQuickViewSnapshot cached(String playerName, String floor) {
        String key = cacheKey(normalizedPlayer(playerName), normalizedFloor(floor));
        Entry existing = cache.get(key);
        if (existing == null) return null;
        if (clock.instant().isBefore(existing.validUntil)) return existing.snapshot;
        cache.remove(key);
        return null;
    }

    public synchronized void reset() {
        cache.clear();
        for (CompletableFuture<DungeonQuickViewSnapshot> future : inFlight.values()) future.cancel(true);
        inFlight.clear();
    }

    private static DungeonQuickViewException responseFailure(QcaApiClient.Response response) {
        String message = "Dungeon profile data is temporarily unavailable.";
        String code = "";
        try {
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonObject error = root.getAsJsonObject("error");
            JsonElement rawMessage = error == null ? null : error.get("message");
            if (rawMessage != null && rawMessage.isJsonPrimitive()
                    && rawMessage.getAsJsonPrimitive().isString()) message = rawMessage.getAsString();
            JsonElement rawCode = error == null ? null : error.get("code");
            if (rawCode != null && rawCode.isJsonPrimitive()
                    && rawCode.getAsJsonPrimitive().isString()) code = rawCode.getAsString();
        } catch (RuntimeException ignored) { }
        DungeonQuickViewException.Scope scope = response.statusCode() == 404
                && PLAYER_UNAVAILABLE_CODES.contains(code)
                ? DungeonQuickViewException.Scope.PLAYER
                : DungeonQuickViewException.Scope.SERVICE;
        return new DungeonQuickViewException(message, null, scope);
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

    private static String cacheKey(String playerName, String floor) {
        return playerName.toLowerCase(java.util.Locale.ROOT) + '|' + floor;
    }

    private record Entry(DungeonQuickViewSnapshot snapshot, Instant validUntil) { }

    @FunctionalInterface
    interface Gateway {
        CompletableFuture<QcaApiClient.Response> fetch(String target, String floor);
    }
}

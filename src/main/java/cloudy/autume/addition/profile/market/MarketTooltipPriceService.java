package cloudy.autume.addition.profile.market;

import cloudy.autume.addition.network.QcaApiClient;
import cloudy.autume.addition.profile.ProfileException;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Screen-local friendly PV price loader: 60-second cache, per-query in-flight
 * coalescing, bounded batches, and cancellation-isolated caller futures.
 */
public final class MarketTooltipPriceService {
    public static final Duration CACHE_TTL = Duration.ofSeconds(60);
    public static final int MAX_BATCH_ITEMS = QcaApiClient.MAX_MARKET_TOOLTIP_ITEMS;

    private final Gateway gateway;
    private final Clock clock;
    private final Map<MarketTooltipQuery, CacheEntry> cache = new LinkedHashMap<>();
    private final Map<MarketTooltipQuery, CompletableFuture<MarketTooltipPrice>> inFlight =
            new LinkedHashMap<>();
    private long batchSequence;
    private long cacheGeneration;

    public MarketTooltipPriceService(QcaApiClient apiClient, Clock clock) {
        this(Objects.requireNonNull(apiClient, "apiClient")::fetchMarketTooltipPrices, clock);
    }

    MarketTooltipPriceService(Gateway gateway, Clock clock) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Loads at most 256 entries. Duplicate queries are folded while preserving
     * first-seen order. Cancelling the returned future never cancels another
     * screen/hover subscriber sharing the same request.
     */
    public CompletableFuture<MarketTooltipPriceBatch> load(List<MarketTooltipQuery> queries) {
        List<MarketTooltipQuery> ordered = distinctQueries(queries);
        LinkedHashMap<MarketTooltipQuery, CompletableFuture<MarketTooltipPrice>> dependencies =
                new LinkedHashMap<>();
        List<NewRequest> newRequests = new ArrayList<>();
        boolean entirelyFromCache = true;
        long generation;

        synchronized (this) {
            Instant now = clock.instant();
            generation = cacheGeneration;
            for (MarketTooltipQuery query : ordered) {
                CacheEntry cached = cache.get(query);
                if (cached != null && now.isBefore(cached.validUntil())) {
                    dependencies.put(query, CompletableFuture.completedFuture(cached.price()));
                    continue;
                }
                if (cached != null) cache.remove(query);
                entirelyFromCache = false;
                CompletableFuture<MarketTooltipPrice> shared = inFlight.get(query);
                if (shared == null) {
                    shared = new CompletableFuture<>();
                    inFlight.put(query, shared);
                    String requestId = "pv-" + (++batchSequence) + "-" + newRequests.size();
                    newRequests.add(new NewRequest(
                            new MarketTooltipRequestItem(requestId, query), shared));
                }
                dependencies.put(query, shared);
            }
        }

        if (!newRequests.isEmpty()) startBatch(newRequests, generation);
        boolean cacheHit = entirelyFromCache;
        CompletableFuture<?>[] all = dependencies.values().toArray(CompletableFuture[]::new);
        CompletableFuture<Void> sharedCompletion = CompletableFuture.allOf(all);
        // thenApply creates a cancellation-isolated view; cancelling it does not
        // propagate into allOf or into per-query shared work.
        return sharedCompletion.thenApply(ignored -> {
            List<MarketTooltipPrice> prices = new ArrayList<>(ordered.size());
            for (MarketTooltipQuery query : ordered) prices.add(dependencies.get(query).join());
            return new MarketTooltipPriceBatch(prices, cacheHit);
        });
    }

    public synchronized void clearSessionCache() {
        cache.clear();
        cacheGeneration++;
    }

    private void startBatch(List<NewRequest> newRequests, long generation) {
        List<MarketTooltipRequestItem> requestItems = newRequests.stream()
                .map(NewRequest::item).toList();
        CompletableFuture<QcaApiClient.Response> network;
        try {
            network = gateway.fetch(requestItems);
        } catch (RuntimeException exception) {
            completeFailed(newRequests, mapTransportFailure(exception));
            return;
        }
        network.whenComplete((response, failure) -> {
            if (failure != null) {
                completeFailed(newRequests, mapTransportFailure(failure));
                return;
            }
            try {
                if (response == null) throw new IllegalArgumentException("Missing response");
                if (response.statusCode() != 200) throw responseFailure(response);
                List<MarketTooltipPrice> parsed = MarketTooltipPriceJsonParser.parse(response.body());
                Map<String, MarketTooltipPrice> byRequestId = validateEcho(requestItems, parsed);
                Instant validUntil = clock.instant().plus(CACHE_TTL);
                synchronized (this) {
                    for (NewRequest request : newRequests) {
                        MarketTooltipPrice price = byRequestId.get(request.item().requestId());
                        if (generation == cacheGeneration) {
                            cache.put(request.item().query(), new CacheEntry(price, validUntil));
                        }
                        removeInFlight(request);
                        request.future().complete(price);
                    }
                }
            } catch (RuntimeException exception) {
                completeFailed(newRequests, mapTransportFailure(exception));
            }
        });
    }

    private static Map<String, MarketTooltipPrice> validateEcho(
            List<MarketTooltipRequestItem> requests,
            List<MarketTooltipPrice> prices) {
        if (prices.size() != requests.size()) {
            throw invalidResponse("The market response omitted or added items.");
        }
        LinkedHashMap<String, MarketTooltipRequestItem> expected = new LinkedHashMap<>();
        for (MarketTooltipRequestItem request : requests) expected.put(request.requestId(), request);
        LinkedHashMap<String, MarketTooltipPrice> result = new LinkedHashMap<>();
        for (MarketTooltipPrice price : prices) {
            MarketTooltipRequestItem request = expected.get(price.requestId());
            if (request == null || !request.query().equals(price.query())
                    || result.put(price.requestId(), price) != null) {
                throw invalidResponse("The market response did not match its request.");
            }
        }
        if (result.size() != expected.size()) {
            throw invalidResponse("The market response omitted requested items.");
        }
        return result;
    }

    private void completeFailed(List<NewRequest> requests, ProfileException failure) {
        synchronized (this) {
            for (NewRequest request : requests) {
                removeInFlight(request);
                request.future().completeExceptionally(failure);
            }
        }
    }

    private void removeInFlight(NewRequest request) {
        if (inFlight.get(request.item().query()) == request.future()) {
            inFlight.remove(request.item().query());
        }
    }

    private static List<MarketTooltipQuery> distinctQueries(List<MarketTooltipQuery> queries) {
        if (queries == null || queries.isEmpty() || queries.size() > MAX_BATCH_ITEMS) {
            throw new IllegalArgumentException("Invalid market tooltip query count");
        }
        LinkedHashMap<MarketTooltipQuery, Boolean> unique = new LinkedHashMap<>();
        for (MarketTooltipQuery query : queries) {
            unique.put(Objects.requireNonNull(query, "query"), Boolean.TRUE);
        }
        return List.copyOf(unique.keySet());
    }

    private static ProfileException responseFailure(QcaApiClient.Response response) {
        ProfileException.Code code = switch (response.statusCode()) {
            case 429 -> ProfileException.Code.RATE_LIMITED;
            case 502, 504 -> ProfileException.Code.UPSTREAM_UNAVAILABLE;
            default -> ProfileException.Code.SERVICE_UNAVAILABLE;
        };
        return new ProfileException(code, "Market tooltip prices are temporarily unavailable.",
                retryAfter(response), response.statusCode(), null);
    }

    private static ProfileException mapTransportFailure(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof ProfileException exception) return exception;
        if (cause instanceof QcaApiClient.ResponseTooLargeException) {
            return new ProfileException(ProfileException.Code.RESPONSE_TOO_LARGE,
                    "The market tooltip response was too large.", Duration.ZERO, 0, cause);
        }
        if (cause instanceof CancellationException) {
            return new ProfileException(ProfileException.Code.CANCELLED,
                    "The market tooltip request was cancelled.", Duration.ZERO, 0, cause);
        }
        if (cause instanceof HttpTimeoutException || cause instanceof IOException) {
            return new ProfileException(ProfileException.Code.SERVICE_UNAVAILABLE,
                    "Market tooltip prices are temporarily unavailable.",
                    Duration.ZERO, 0, cause);
        }
        return new ProfileException(ProfileException.Code.INVALID_RESPONSE,
                "The market tooltip request failed.", Duration.ZERO, 0, cause);
    }

    private static ProfileException invalidResponse(String message) {
        return new ProfileException(ProfileException.Code.INVALID_RESPONSE, message);
    }

    private static Duration retryAfter(QcaApiClient.Response response) {
        try {
            long seconds = Long.parseLong(response.firstHeader("Retry-After").trim());
            return seconds <= 0 ? Duration.ZERO : Duration.ofSeconds(Math.min(seconds, 3600));
        } catch (RuntimeException ignored) {
            return Duration.ZERO;
        }
    }

    private record CacheEntry(MarketTooltipPrice price, Instant validUntil) {
    }

    private record NewRequest(MarketTooltipRequestItem item,
                              CompletableFuture<MarketTooltipPrice> future) {
    }

    @FunctionalInterface
    interface Gateway {
        CompletableFuture<QcaApiClient.Response> fetch(List<MarketTooltipRequestItem> items);
    }
}

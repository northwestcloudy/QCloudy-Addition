package cloudy.autume.addition.network;

import cloudy.autume.addition.market.shard.ShardBazaarSide;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bounded asynchronous client for transformed QCA data. It never accepts an
 * arbitrary origin and cannot connect directly to Hypixel.
 */
public final class QcaApiClient {
    public static final URI BASE_URI = URI.create("https://api.qcloudy.net/");
    public static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final ScheduledThreadPoolExecutor DEADLINES = deadlineExecutor();

    private final HttpClient httpClient;
    private final String userAgent;
    private final int maxResponseBytes;
    private final Duration requestTimeout;

    public static QcaApiClient createDefault(String userAgent) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new QcaApiClient(client, userAgent, MAX_RESPONSE_BYTES);
    }

    public QcaApiClient(HttpClient httpClient, String userAgent, int maxResponseBytes) {
        this(httpClient, userAgent, maxResponseBytes, REQUEST_TIMEOUT);
    }

    QcaApiClient(HttpClient httpClient, String userAgent, int maxResponseBytes,
                 Duration requestTimeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        if (httpClient.followRedirects() != HttpClient.Redirect.NEVER) {
            throw new IllegalArgumentException("QCA API client must not follow redirects");
        }
        this.userAgent = safeUserAgent(userAgent);
        if (maxResponseBytes < 1024 || maxResponseBytes > 16 * 1024 * 1024) {
            throw new IllegalArgumentException("Invalid maximum response size");
        }
        this.maxResponseBytes = maxResponseBytes;
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        if (requestTimeout.isZero() || requestTimeout.isNegative()
                || requestTimeout.compareTo(Duration.ofMinutes(2)) > 0) {
            throw new IllegalArgumentException("Invalid request timeout");
        }
    }

    public CompletableFuture<Response> fetchDungeonQuickView(String target, String floor) {
        String path = "v1/dungeons/quick-view/" + encodeSegment(target, "target");
        if (floor != null && !floor.isBlank()) path += "?floor=" + encodeQuery(floor);
        return send(buildGet(path));
    }

    public CompletableFuture<Response> fetchShardBazaarPrices(ShardBazaarSide side) {
        Objects.requireNonNull(side, "side");
        return send(buildGet("v1/market/bazaar/shards?side=" + side.wireName()));
    }

    public HttpRequest buildDungeonQuickViewRequest(String target, String floor) {
        String path = "v1/dungeons/quick-view/" + encodeSegment(target, "target");
        if (floor != null && !floor.isBlank()) path += "?floor=" + encodeQuery(floor);
        return buildGet(path);
    }

    private HttpRequest buildGet(String relativePath) {
        URI uri = checkedUri(BASE_URI.resolve(relativePath));
        return HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .GET()
                .build();
    }


    private CompletableFuture<Response> send(HttpRequest request) {
        checkedUri(request.uri());
        CompletableFuture<Response> result = new CompletableFuture<>();
        AtomicBoolean settled = new AtomicBoolean();
        AtomicReference<BoundedBodySubscriber> activeBody = new AtomicReference<>();
        CompletableFuture<HttpResponse<byte[]>> transport;
        try {
            transport = httpClient.sendAsync(request, responseInfo -> {
                BoundedBodySubscriber subscriber = new BoundedBodySubscriber(maxResponseBytes);
                activeBody.set(subscriber);
                if (result.isDone()) {
                    subscriber.abort(new IOException("QCA API request already finished"));
                    activeBody.compareAndSet(subscriber, null);
                }
                return subscriber;
            });
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }

        ScheduledFuture<?> deadline = DEADLINES.schedule(() -> {
            java.net.http.HttpTimeoutException timeout =
                    new java.net.http.HttpTimeoutException("QCA API request timed out");
            if (!settled.compareAndSet(false, true)) return;
            BoundedBodySubscriber subscriber = activeBody.getAndSet(null);
            transport.cancel(true);
            if (subscriber != null) subscriber.abort(timeout);
            result.completeExceptionally(timeout);
        }, requestTimeout.toNanos(), TimeUnit.NANOSECONDS);

        transport.whenComplete((response, failure) -> {
            activeBody.set(null);
            if (!settled.compareAndSet(false, true)) return;
            if (failure != null) {
                result.completeExceptionally(failure);
                return;
            }
            try {
                result.complete(readResponse(request.uri(), response));
            } catch (RuntimeException exception) {
                result.completeExceptionally(exception);
            }
        });
        result.whenComplete((ignored, failure) -> {
            deadline.cancel(false);
            if (result.isCancelled()) {
                if (!settled.compareAndSet(false, true)) return;
                transport.cancel(true);
                BoundedBodySubscriber subscriber = activeBody.getAndSet(null);
                if (subscriber != null) subscriber.abort(
                        new java.util.concurrent.CancellationException(
                                "QCA API request was cancelled"));
            }
        });
        return result;
    }

    static Response readResponse(URI requestedUri, HttpResponse<byte[]> response) {
        if (response == null) {
            throw new CompletionException(new IOException("QCA API response was missing"));
        }
        URI finalUri = checkedUri(response.uri());
        if (!requestedUri.equals(finalUri)) {
            throw new CompletionException(new IOException("QCA API response URI changed"));
        }
        byte[] body = response.body();
        if (body == null) {
            throw new CompletionException(new IOException("QCA API response had no body"));
        }
        return new Response(response.statusCode(),
                new String(body, StandardCharsets.UTF_8), response.headers().map());
    }

    private static URI checkedUri(URI uri) {
        if (uri == null
                || !"https".equalsIgnoreCase(uri.getScheme())
                || !"api.qcloudy.net".equalsIgnoreCase(uri.getHost())
                || (uri.getPort() != -1 && uri.getPort() != 443)
                || uri.getUserInfo() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("Only https://api.qcloudy.net is allowed");
        }
        return uri;
    }

    private static String encodeSegment(String value, String label) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException("Invalid " + label);
        }
        return encodeQuery(value.trim()).replace("+", "%20");
    }

    private static String encodeQuery(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String safeUserAgent(String value) {
        String candidate = value == null ? "" : value.trim();
        if (candidate.isEmpty()) candidate = "QCloudy_Addition/unknown";
        if (candidate.length() > 128 || candidate.chars().anyMatch(character ->
                character < 0x20 || character == 0x7f)) {
            throw new IllegalArgumentException("Invalid User-Agent");
        }
        return candidate;
    }

    private static ScheduledThreadPoolExecutor deadlineExecutor() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "qca-api-deadline");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        executor.setKeepAliveTime(1, TimeUnit.SECONDS);
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    /** Collects response buffers without blocking an HTTP callback thread. */
    private static final class BoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final int maxBytes;
        private final ByteArrayOutputStream output;
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;
        private boolean finished;

        private BoundedBodySubscriber(int maxBytes) {
            this.maxBytes = maxBytes;
            this.output = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription newSubscription) {
            Objects.requireNonNull(newSubscription, "subscription");
            synchronized (this) {
                if (subscription != null || finished) {
                    newSubscription.cancel();
                    return;
                }
                subscription = newSubscription;
            }
            newSubscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            Objects.requireNonNull(items, "items");
            Flow.Subscription current;
            ResponseTooLargeException tooLarge = null;
            synchronized (this) {
                if (finished) return;
                long incoming = 0;
                for (ByteBuffer item : items) {
                    incoming += Objects.requireNonNull(item, "body buffer").remaining();
                }
                if ((long) output.size() + incoming > maxBytes) {
                    finished = true;
                    tooLarge = new ResponseTooLargeException();
                } else {
                    byte[] copyBuffer = new byte[Math.min(8192,
                            Math.max(1, (int) incoming))];
                    for (ByteBuffer item : items) {
                        while (item.hasRemaining()) {
                            int length = Math.min(item.remaining(), copyBuffer.length);
                            item.get(copyBuffer, 0, length);
                            output.write(copyBuffer, 0, length);
                        }
                    }
                }
                current = subscription;
            }
            if (tooLarge != null) {
                if (current != null) current.cancel();
                body.completeExceptionally(tooLarge);
            } else if (current != null) {
                current.request(1);
            }
        }

        @Override
        public void onError(Throwable failure) {
            abort(Objects.requireNonNull(failure, "failure"));
        }

        @Override
        public void onComplete() {
            byte[] bytes;
            synchronized (this) {
                if (finished) return;
                finished = true;
                bytes = output.toByteArray();
            }
            body.complete(bytes);
        }

        private void abort(Throwable failure) {
            Flow.Subscription current;
            synchronized (this) {
                if (finished) return;
                finished = true;
                current = subscription;
            }
            if (current != null) current.cancel();
            body.completeExceptionally(failure);
        }
    }

    public record Response(int statusCode,
                           String body,
                           Map<String, List<String>> headers) {
        public Response {
            body = body == null ? "" : body;
            headers = headers == null ? Map.of() : Map.copyOf(headers);
        }

        public String firstHeader(String name) {
            if (name == null) return "";
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getKey().toLowerCase(Locale.ROOT)
                        .equals(name.toLowerCase(Locale.ROOT)) && !entry.getValue().isEmpty()) {
                    return entry.getValue().getFirst();
                }
            }
            return "";
        }
    }

    public static final class ResponseTooLargeException extends IOException {
        public ResponseTooLargeException() {
            super("QCA API response exceeded the configured size limit");
        }
    }
}

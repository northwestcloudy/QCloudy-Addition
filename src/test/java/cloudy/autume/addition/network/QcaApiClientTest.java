package cloudy.autume.addition.network;

import cloudy.autume.addition.market.shard.ShardBazaarSide;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class QcaApiClientTest {
    @Test
    void buildsOnlyTheFrozenHttpsOriginAndDungeonFloorQuery() {
        HttpClient transport = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER).build();
        QcaApiClient client = new QcaApiClient(transport, "QCA-Test/1", 1024);

        HttpRequest request = client.buildDungeonQuickViewRequest("NorthwestCloudy", "M7");

        assertEquals("https", request.uri().getScheme());
        assertEquals("api.qcloudy.net", request.uri().getHost());
        assertEquals("/v1/dungeons/quick-view/NorthwestCloudy", request.uri().getPath());
        assertEquals("floor=M7", request.uri().getRawQuery());
        assertEquals(QcaApiClient.REQUEST_TIMEOUT, request.timeout().orElseThrow());
        assertEquals("QCA-Test/1", request.headers().firstValue("User-Agent").orElseThrow());
    }

    @Test
    void shardRequestUsesTheExactGetContract() {
        CapturingClient transport = new CapturingClient();
        QcaApiClient client = new QcaApiClient(transport, "QCA-Test/1", 1024);

        QcaApiClient.Response response = client.fetchShardBazaarPrices(
                ShardBazaarSide.INSTANT_SELL).join();

        assertEquals(200, response.statusCode());
        assertEquals("/v1/market/bazaar/shards", transport.request.uri().getPath());
        assertEquals("side=instant_sell", transport.request.uri().getRawQuery());
        assertEquals("GET", transport.request.method());
    }

    @Test
    void rejectsRedirectFollowingTransportsAndUnsafeUserAgents() {
        HttpClient redirects = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS).build();
        HttpClient safe = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER).build();

        assertThrows(IllegalArgumentException.class,
                () -> new QcaApiClient(redirects, "QCA-Test/1", 1024));
        assertThrows(IllegalArgumentException.class,
                () -> new QcaApiClient(safe, "bad\nheader", 1024));
    }

    @Test
    void boundedSubscriberRejectsOversizeResponsesAndCancelsTheBody() {
        URI requested = URI.create(
                "https://api.qcloudy.net/v1/dungeons/quick-view/Test?floor=F7");
        StreamingClient transport = new StreamingClient(new byte[1025], true, null);
        QcaApiClient client = new QcaApiClient(transport, "QCA-Test/1", 1024);

        CompletionException tooLarge = assertThrows(CompletionException.class,
                () -> client.fetchDungeonQuickView("Test", "F7").join());

        assertInstanceOf(QcaApiClient.ResponseTooLargeException.class, rootCause(tooLarge));
        assertTrue(transport.subscription.cancelled);
        assertEquals(requested, transport.request.uri());
    }

    @Test
    void rejectsChangedResponseUris() {
        URI requested = URI.create("https://api.qcloudy.net/v1/dungeons/quick-view/Test");
        CompletionException redirected = assertThrows(CompletionException.class,
                () -> QcaApiClient.readResponse(requested,
                        new StubResponse<>(URI.create("https://api.qcloudy.net/other"),
                                "{}".getBytes(StandardCharsets.UTF_8))));
        assertInstanceOf(IOException.class, redirected.getCause());
    }

    @Test
    void hangingResponseBodyFailsAtTheFullRequestDeadline() throws Exception {
        StreamingClient transport = new StreamingClient(
                "{".getBytes(StandardCharsets.UTF_8), false, null);
        QcaApiClient client = new QcaApiClient(
                transport, "QCA-Test/1", 1024, Duration.ofMillis(75));

        CompletableFuture<QcaApiClient.Response> request =
                client.fetchDungeonQuickView("NorthwestCloudy", "M7");
        ExecutionException timeout = assertThrows(ExecutionException.class,
                () -> request.get(2, TimeUnit.SECONDS));

        assertInstanceOf(HttpTimeoutException.class, rootCause(timeout));
        assertTrue(transport.subscription.cancelled);
        assertTrue(transport.future.isCancelled());
    }

    @Test
    void responseHeaderLookupIsCaseInsensitive() {
        QcaApiClient.Response response = new QcaApiClient.Response(429, "",
                Map.of("retry-after", List.of("7")));
        assertEquals("7", response.firstHeader("Retry-After"));
        assertEquals("", response.firstHeader("missing"));
    }

    @Test
    void cancellingReturnedFutureCancelsTheUnderlyingHttpRequest() {
        CapturingClient transport = new CapturingClient(true);
        QcaApiClient client = new QcaApiClient(transport, "QCA-Test/1", 1024);

        CompletableFuture<QcaApiClient.Response> request =
                client.fetchDungeonQuickView("NorthwestCloudy", "F7");

        assertTrue(request.cancel(true));
        assertTrue(transport.future.isCancelled());
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable result = failure;
        while (result.getCause() != null) result = result.getCause();
        return result;
    }

    private static final class StubResponse<T> implements HttpResponse<T> {
        private final URI uri;
        private final T body;

        private StubResponse(URI uri, T body) {
            this.uri = uri;
            this.body = body;
        }

        @Override
        public int statusCode() {
            return 200;
        }

        @Override
        public HttpRequest request() {
            return HttpRequest.newBuilder(uri).GET().build();
        }

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (name, value) -> true);
        }

        @Override
        public T body() {
            return body;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return uri;
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_2;
        }
    }

    private static final class CapturingClient extends TestHttpClient {
        private HttpRequest request;
        private final CompletableFuture<HttpResponse<byte[]>> pendingFuture;
        private CompletableFuture<?> future;

        private CapturingClient() {
            this(false);
        }

        private CapturingClient(boolean pending) {
            this.pendingFuture = pending ? new CompletableFuture<>() : null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            this.request = request;
            if (pendingFuture != null) {
                future = pendingFuture;
                return (CompletableFuture<HttpResponse<T>>) (CompletableFuture<?>) pendingFuture;
            }
            CompletableFuture<HttpResponse<byte[]>> completed = CompletableFuture.completedFuture(
                    new StubResponse<>(request.uri(), "{}".getBytes(StandardCharsets.UTF_8)));
            future = completed;
            return (CompletableFuture<HttpResponse<T>>) (CompletableFuture<?>) completed;
        }
    }

    private static final class StreamingClient extends TestHttpClient {
        private static final HttpResponse.ResponseInfo RESPONSE_INFO =
                new HttpResponse.ResponseInfo() {
                    @Override
                    public int statusCode() {
                        return 200;
                    }

                    @Override
                    public HttpHeaders headers() {
                        return HttpHeaders.of(Map.of(), (name, value) -> true);
                    }

                    @Override
                    public Version version() {
                        return Version.HTTP_2;
                    }
                };

        private final byte[] bytes;
        private final boolean completes;
        private final URI finalUri;
        private HttpRequest request;
        private TrackingSubscription subscription;
        private CompletableFuture<?> future;

        private StreamingClient(byte[] bytes, boolean completes, URI finalUri) {
            this.bytes = bytes;
            this.completes = completes;
            this.finalUri = finalUri;
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            this.request = request;
            HttpResponse.BodySubscriber<T> subscriber = responseBodyHandler.apply(RESPONSE_INFO);
            subscription = new TrackingSubscription();
            subscriber.onSubscribe(subscription);
            if (bytes.length > 0) subscriber.onNext(List.of(ByteBuffer.wrap(bytes)));
            if (completes) subscriber.onComplete();
            CompletableFuture<HttpResponse<T>> response = subscriber.getBody().toCompletableFuture()
                    .thenApply(body -> new StubResponse<>(
                            finalUri == null ? request.uri() : finalUri, body));
            future = response;
            return response;
        }
    }

    private static final class TrackingSubscription implements Flow.Subscription {
        private volatile boolean cancelled;

        @Override
        public void request(long amount) { }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }

    private abstract static class TestHttpClient extends HttpClient {
        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.of(QcaApiClient.CONNECT_TIMEOUT);
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            try {
                return SSLContext.getDefault();
            } catch (java.security.NoSuchAlgorithmException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_2;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request,
                                        HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, responseBodyHandler);
        }
    }
}

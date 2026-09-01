package cloudy.autume.addition.network;

import cloudy.autume.addition.profile.ShardBazaarSide;
import cloudy.autume.addition.profile.market.MarketTooltipRequestItem;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class QcaApiClientTest {
    @Test
    void buildsOnlyTheFrozenHttpsOriginAndProfileIdQuery() {
        HttpClient transport = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER).build();
        QcaApiClient client = new QcaApiClient(transport, "QCA-Test/1", 1024);

        HttpRequest request = client.buildProfileRequest(
                "NorthwestCloudy", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

        assertEquals("https", request.uri().getScheme());
        assertEquals("api.qcloudy.net", request.uri().getHost());
        assertEquals("/v1/pv/NorthwestCloudy", request.uri().getPath());
        assertEquals("profileId=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                request.uri().getRawQuery());
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
    void marketTooltipRequestUsesTheFrozenBoundedPostContract() {
        String body = QcaApiClient.marketTooltipRequestBody(List.of(
                new MarketTooltipRequestItem("slot-1",
                        new cloudy.autume.addition.profile.market.MarketTooltipQuery(
                                "hyperion", "upgrade=10", 2)),
                new MarketTooltipRequestItem("slot-2",
                        new cloudy.autume.addition.profile.market.MarketTooltipQuery(
                                "ENCHANTED_DIAMOND", null, 1))));
        CapturingClient transport = new CapturingClient();
        QcaApiClient client = new QcaApiClient(transport, "QCA-Test/1", 1024);

        HttpRequest request = client.buildMarketTooltipPricesRequest(List.of(
                new MarketTooltipRequestItem("slot-1",
                        new cloudy.autume.addition.profile.market.MarketTooltipQuery(
                                "HYPERION", "upgrade=10", 2))));

        assertEquals("POST", request.method());
        assertEquals("/v1/market/tooltip-prices", request.uri().getPath());
        assertEquals("application/json",
                request.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("{\"items\":[{\"requestId\":\"slot-1\",\"itemId\":\"HYPERION\","
                        + "\"variantKey\":\"upgrade=10\",\"quantity\":2},"
                        + "{\"requestId\":\"slot-2\",\"itemId\":\"ENCHANTED_DIAMOND\","
                        + "\"quantity\":1}]}", body);
        assertThrows(IllegalArgumentException.class,
                () -> QcaApiClient.marketTooltipRequestBody(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> QcaApiClient.marketTooltipRequestBody(List.of(
                        new MarketTooltipRequestItem("same",
                                new cloudy.autume.addition.profile.market.MarketTooltipQuery("A", null, 1)),
                        new MarketTooltipRequestItem("same",
                                new cloudy.autume.addition.profile.market.MarketTooltipQuery("B", null, 1)))));
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
    void boundedReaderRejectsOversizeAndChangedResponseUrisAndClosesBodies() {
        URI requested = URI.create("https://api.qcloudy.net/v1/pv/Test");
        TrackingInputStream oversized = new TrackingInputStream(new byte[17]);
        CompletionException tooLarge = assertThrows(CompletionException.class,
                () -> QcaApiClient.readResponse(requested,
                        new StubResponse(requested, oversized), 16));
        assertInstanceOf(QcaApiClient.ResponseTooLargeException.class, tooLarge.getCause());
        assertTrue(oversized.closed);

        TrackingInputStream changed = new TrackingInputStream("{}".getBytes(StandardCharsets.UTF_8));
        CompletionException redirected = assertThrows(CompletionException.class,
                () -> QcaApiClient.readResponse(requested,
                        new StubResponse(URI.create("https://api.qcloudy.net/other"), changed), 16));
        assertInstanceOf(IOException.class, redirected.getCause());
        assertTrue(changed.closed);
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

        CompletableFuture<QcaApiClient.Response> request = client.fetchProfile("NorthwestCloudy", null);

        assertTrue(request.cancel(true));
        assertTrue(transport.future.isCancelled());
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        private TrackingInputStream(byte[] buffer) {
            super(buffer);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static final class StubResponse implements HttpResponse<InputStream> {
        private final URI uri;
        private final InputStream body;

        private StubResponse(URI uri, InputStream body) {
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
        public Optional<HttpResponse<InputStream>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (name, value) -> true);
        }

        @Override
        public InputStream body() {
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

    private static final class CapturingClient extends HttpClient {
        private HttpRequest request;
        private final CompletableFuture<HttpResponse<InputStream>> future;

        private CapturingClient() {
            this(false);
        }

        private CapturingClient(boolean pending) {
            HttpResponse<InputStream> response = new StubResponse(
                    URI.create("https://api.qcloudy.net/"),
                    new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));
            this.future = pending
                    ? new CompletableFuture<>()
                    : CompletableFuture.completedFuture(response);
        }

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
        @SuppressWarnings("unchecked")
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            this.request = request;
            if (!future.isDone()) {
                return (CompletableFuture<HttpResponse<T>>) (CompletableFuture<?>) future;
            }
            HttpResponse<InputStream> response = new StubResponse(request.uri(),
                    new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));
            return (CompletableFuture<HttpResponse<T>>) (CompletableFuture<?>)
                    CompletableFuture.completedFuture(response);
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

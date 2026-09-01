package cloudy.autume.addition.network;

import cloudy.autume.addition.profile.ShardBazaarSide;
import cloudy.autume.addition.profile.market.MarketTooltipRequestItem;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bounded asynchronous client for transformed QCA data. It never accepts an
 * arbitrary origin and cannot connect directly to Hypixel.
 */
public final class QcaApiClient {
    public static final URI BASE_URI = URI.create("https://api.qcloudy.net/");
    public static final int MAX_PROFILE_RESPONSE_BYTES = 4 * 1024 * 1024;
    public static final int MAX_MARKET_TOOLTIP_ITEMS = 256;
    public static final int MAX_MARKET_TOOLTIP_REQUEST_BYTES = 128 * 1024;
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient httpClient;
    private final String userAgent;
    private final int maxResponseBytes;

    public static QcaApiClient createDefault(String userAgent) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new QcaApiClient(client, userAgent, MAX_PROFILE_RESPONSE_BYTES);
    }

    public QcaApiClient(HttpClient httpClient, String userAgent, int maxResponseBytes) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        if (httpClient.followRedirects() != HttpClient.Redirect.NEVER) {
            throw new IllegalArgumentException("QCA API client must not follow redirects");
        }
        this.userAgent = safeUserAgent(userAgent);
        if (maxResponseBytes < 1024 || maxResponseBytes > 16 * 1024 * 1024) {
            throw new IllegalArgumentException("Invalid maximum response size");
        }
        this.maxResponseBytes = maxResponseBytes;
    }

    public CompletableFuture<Response> fetchProfile(String target, String profileId) {
        String path = "v1/pv/" + encodeSegment(target, "target");
        if (profileId != null && !profileId.isBlank()) {
            path += "?profileId=" + encodeQuery(profileId);
        }
        return send(buildGet(path));
    }

    public CompletableFuture<Response> fetchMuseum(String uuid, String profileId) {
        String path = "v1/pv/" + encodeSegment(uuid, "uuid") + "/"
                + encodeSegment(profileId, "profileId") + "/museum";
        return send(buildGet(path));
    }

    public CompletableFuture<Response> fetchGarden(String uuid, String profileId) {
        String path = "v1/pv/" + encodeSegment(uuid, "uuid") + "/"
                + encodeSegment(profileId, "profileId") + "/garden";
        return send(buildGet(path));
    }

    public CompletableFuture<Response> fetchShardBazaarPrices(ShardBazaarSide side) {
        Objects.requireNonNull(side, "side");
        return send(buildGet("v1/market/bazaar/shards?side=" + side.wireName()));
    }

    public CompletableFuture<Response> fetchMarketTooltipPrices(
            List<MarketTooltipRequestItem> items) {
        return send(buildMarketTooltipPricesRequest(items));
    }

    public HttpRequest buildProfileRequest(String target, String profileId) {
        String path = "v1/pv/" + encodeSegment(target, "target");
        if (profileId != null && !profileId.isBlank()) {
            path += "?profileId=" + encodeQuery(profileId);
        }
        return buildGet(path);
    }

    public HttpRequest buildMarketTooltipPricesRequest(
            List<MarketTooltipRequestItem> items) {
        String body = marketTooltipRequestBody(items);
        return buildPost("v1/market/tooltip-prices", body);
    }

    private HttpRequest buildGet(String relativePath) {
        URI uri = checkedUri(BASE_URI.resolve(relativePath));
        return HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .GET()
                .build();
    }

    private HttpRequest buildPost(String relativePath, String jsonBody) {
        byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_MARKET_TOOLTIP_REQUEST_BYTES) {
            throw new IllegalArgumentException("QCA API request body is too large");
        }
        URI uri = checkedUri(BASE_URI.resolve(relativePath));
        return HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", userAgent)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();
    }

    static String marketTooltipRequestBody(List<MarketTooltipRequestItem> items) {
        if (items == null || items.isEmpty() || items.size() > MAX_MARKET_TOOLTIP_ITEMS) {
            throw new IllegalArgumentException("Invalid market tooltip item count");
        }
        Set<String> requestIds = new LinkedHashSet<>();
        Set<MarketTooltipRequestItem> uniqueItems = new LinkedHashSet<>();
        for (MarketTooltipRequestItem item : items) {
            MarketTooltipRequestItem checked = Objects.requireNonNull(item, "item");
            if (!requestIds.add(checked.requestId())) {
                throw new IllegalArgumentException("Duplicate market tooltip requestId");
            }
            uniqueItems.add(checked);
        }

        StringBuilder json = new StringBuilder(64 + uniqueItems.size() * 96);
        json.append("{\"items\":[");
        int index = 0;
        for (MarketTooltipRequestItem item : uniqueItems) {
            if (index++ > 0) json.append(',');
            json.append('{');
            jsonString(json, "requestId").append(':');
            jsonString(json, item.requestId()).append(',');
            jsonString(json, "itemId").append(':');
            jsonString(json, item.query().key().itemId());
            if (item.query().key().variantKey() != null) {
                json.append(',');
                jsonString(json, "variantKey").append(':');
                jsonString(json, item.query().key().variantKey());
            }
            json.append(',');
            jsonString(json, "quantity").append(':').append(item.query().quantity());
            json.append('}');
        }
        return json.append("]}").toString();
    }

    private static StringBuilder jsonString(StringBuilder output, String value) {
        output.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (character < 0x20) {
                        output.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    } else {
                        output.append(character);
                    }
                }
            }
        }
        return output.append('"');
    }

    private CompletableFuture<Response> send(HttpRequest request) {
        checkedUri(request.uri());
        CompletableFuture<HttpResponse<InputStream>> transport = httpClient.sendAsync(
                request, HttpResponse.BodyHandlers.ofInputStream());
        CompletableFuture<Response> result = new CompletableFuture<>();
        AtomicReference<InputStream> activeBody = new AtomicReference<>();
        transport.whenComplete((response, failure) -> {
            if (failure != null) {
                result.completeExceptionally(failure);
                return;
            }
            InputStream body = response == null ? null : response.body();
            activeBody.set(body);
            if (result.isCancelled()) {
                closeQuietly(body);
                return;
            }
            try {
                result.complete(readResponse(request.uri(), response, maxResponseBytes));
            } catch (RuntimeException exception) {
                result.completeExceptionally(exception);
            } finally {
                activeBody.set(null);
            }
        });
        result.whenComplete((ignored, failure) -> {
            if (!result.isCancelled()) return;
            transport.cancel(true);
            closeQuietly(activeBody.getAndSet(null));
        });
        return result;
    }

    static Response readResponse(URI requestedUri,
                                 HttpResponse<InputStream> response,
                                 int maxResponseBytes) {
        URI finalUri = checkedUri(response.uri());
        if (!requestedUri.equals(finalUri)) {
            closeQuietly(response.body());
            throw new CompletionException(new IOException("QCA API response URI changed"));
        }
        try (InputStream body = response.body()) {
            if (body == null) throw new IOException("QCA API response had no body");
            byte[] bytes = body.readNBytes(maxResponseBytes + 1);
            if (bytes.length > maxResponseBytes) throw new ResponseTooLargeException();
            return new Response(response.statusCode(),
                    new String(bytes, StandardCharsets.UTF_8), response.headers().map());
        } catch (IOException exception) {
            throw new CompletionException(exception);
        }
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

    private static void closeQuietly(InputStream body) {
        if (body == null) return;
        try {
            body.close();
        } catch (IOException ignored) {
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

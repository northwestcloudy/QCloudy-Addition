package cloudy.autume.addition.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/** Schema-v1 Shard-only Bazaar prices returned by the QCA backend. */
public final class ShardBazaarSnapshot {
    public static final int SCHEMA_VERSION = 1;
    private static final int MAX_PRICES = 4096;

    private final String source;
    private final SourceMetadata metadata;
    private final ShardBazaarSide side;
    private final Map<String, Double> prices;

    public ShardBazaarSnapshot(int schemaVersion,
                               String source,
                               SourceMetadata metadata,
                               ShardBazaarSide side,
                               Map<String, Double> prices) {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Shard Bazaar schema: " + schemaVersion);
        }
        this.source = source == null ? "" : source.trim();
        if (this.source.isEmpty() || this.source.length() > 128) {
            throw new IllegalArgumentException("Invalid Shard Bazaar source");
        }
        this.metadata = java.util.Objects.requireNonNull(metadata, "metadata");
        if (metadata.source() != ProfileDataSource.MARKET) {
            throw new IllegalArgumentException("Shard Bazaar metadata must use MARKET source");
        }
        if (!metadata.available()) {
            throw new IllegalArgumentException("Shard Bazaar snapshot must be fresh or stale");
        }
        this.side = java.util.Objects.requireNonNull(side, "side");
        if (prices == null || prices.size() > MAX_PRICES) {
            throw new IllegalArgumentException("Invalid Shard Bazaar price count");
        }
        LinkedHashMap<String, Double> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : prices.entrySet()) {
            String product = entry.getKey();
            Double price = entry.getValue();
            if (product == null || !product.matches("SHARD_[A-Z0-9_:-]{1,122}")
                    || price == null || !Double.isFinite(price) || price <= 0) {
                throw new IllegalArgumentException("Invalid Shard Bazaar price");
            }
            copy.put(product, price);
        }
        this.prices = Collections.unmodifiableMap(copy);
    }

    public int schemaVersion() {
        return SCHEMA_VERSION;
    }

    public String source() {
        return source;
    }

    public SourceMetadata metadata() {
        return metadata;
    }

    public ShardBazaarSide side() {
        return side;
    }

    public Map<String, Double> prices() {
        return prices;
    }

    public OptionalDouble price(String productId) {
        Double value = prices.get(productId);
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
    }

    public boolean stale() {
        return metadata.stale();
    }

    public static ShardBazaarSnapshot parse(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            int schema = exactInt(root.get("schemaVersion"), "schemaVersion");
            if (schema != SCHEMA_VERSION) {
                throw new ProfileException(ProfileException.Code.UNSUPPORTED_SCHEMA,
                        "Unsupported Shard Bazaar response schema: " + schema);
            }
            String source = requiredString(root, "source", 128);
            ShardBazaarSide side = ShardBazaarSide.parse(requiredString(root, "side", 32));
            JsonObject rawMetadata = root.getAsJsonObject("metadata");
            if (rawMetadata == null) throw new IllegalArgumentException("Missing market metadata");
            ProfileSourceStatus status = ProfileSourceStatus.parse(
                    requiredString(rawMetadata, "status", 32));
            String sourceVersion = optionalString(rawMetadata, "sourceVersion", 128);
            if (sourceVersion.isBlank()) sourceVersion = source;
            SourceMetadata metadata = new SourceMetadata(ProfileDataSource.MARKET, status,
                    instant(rawMetadata, "fetchedAt"),
                    instant(rawMetadata, "expiresAt"),
                    instant(rawMetadata, "staleUntil"),
                    instant(rawMetadata, "nextRefreshAt"), sourceVersion);

            JsonObject rawPrices = root.getAsJsonObject("prices");
            if (rawPrices == null || rawPrices.size() > MAX_PRICES) {
                throw new IllegalArgumentException("Invalid prices object");
            }
            LinkedHashMap<String, Double> prices = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : rawPrices.entrySet()) {
                JsonElement value = entry.getValue();
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                    throw new IllegalArgumentException("Invalid price for " + entry.getKey());
                }
                double price = value.getAsDouble();
                if (!Double.isFinite(price) || price <= 0) {
                    throw new IllegalArgumentException("Invalid price for " + entry.getKey());
                }
                prices.put(entry.getKey(), price);
            }
            return new ShardBazaarSnapshot(schema, source, metadata, side, prices);
        } catch (ProfileException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ProfileException(ProfileException.Code.INVALID_RESPONSE,
                    "The Shard Bazaar service returned invalid data.",
                    Duration.ZERO, 0, exception);
        }
    }

    Instant sessionCacheBoundary(Instant now) {
        Instant maximum = now.plus(Duration.ofMinutes(10));
        Optional<Instant> boundary = metadata.localCacheBoundary(now);
        if (boundary.isEmpty()) return now;
        return boundary.get().isBefore(maximum) ? boundary.get() : maximum;
    }

    private static Instant instant(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) return null;
        long value = exactLong(element, key);
        if (value < 0) throw new IllegalArgumentException("Negative timestamp: " + key);
        return Instant.ofEpochMilli(value);
    }

    private static int exactInt(JsonElement element, String key) {
        long value = exactLong(element, key);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Integer out of range: " + key);
        }
        return (int) value;
    }

    private static long exactLong(JsonElement element, String key) {
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Expected number: " + key);
        }
        BigDecimal value = element.getAsBigDecimal().stripTrailingZeros();
        if (value.scale() > 0) throw new IllegalArgumentException("Expected integer: " + key);
        return value.longValueExact();
    }

    private static String requiredString(JsonObject object, String key, int maxLength) {
        String value = optionalString(object, key, maxLength);
        if (value.isEmpty()) throw new IllegalArgumentException("Missing string: " + key);
        return value;
    }

    private static String optionalString(JsonObject object, String key, int maxLength) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) return "";
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Expected string: " + key);
        }
        String value = element.getAsString().trim();
        if (value.length() > maxLength) throw new IllegalArgumentException("String too long: " + key);
        return value;
    }
}

package cloudy.autume.addition.profile.market;

import cloudy.autume.addition.profile.ProfileException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict schema-v1 parser for the bounded PV tooltip-price response. */
public final class MarketTooltipPriceJsonParser {
    public static final int SCHEMA_VERSION = 1;
    private static final int MAX_ITEMS = 256;
    private static final Set<String> ROOT_KEYS = Set.of(
            "schemaVersion", "currency", "items", "source", "metadata");
    private static final Set<String> ITEM_KEYS = Set.of(
            "requestId", "itemId", "variantKey", "quantity", "marketType", "quotes");

    private MarketTooltipPriceJsonParser() {
    }

    public static List<MarketTooltipPrice> parse(String json) {
        try {
            JsonObject root = object(JsonParser.parseString(json), "response");
            rejectUnknown(root, ROOT_KEYS, "response");
            int schema = exactInt(required(root, "schemaVersion"), "schemaVersion");
            if (schema != SCHEMA_VERSION) {
                throw new ProfileException(ProfileException.Code.UNSUPPORTED_SCHEMA,
                        "Unsupported market tooltip schema: " + schema);
            }
            if (root.has("currency")
                    && !"SKYBLOCK_COINS".equals(string(root.get("currency"), "currency", 32))) {
                throw new IllegalArgumentException("Unexpected market currency");
            }
            JsonArray items = array(required(root, "items"), "items");
            if (items.isEmpty() || items.size() > MAX_ITEMS) {
                throw new IllegalArgumentException("Invalid tooltip item count");
            }
            List<MarketTooltipPrice> result = new ArrayList<>(items.size());
            Set<String> requestIds = new HashSet<>();
            for (JsonElement raw : items) {
                MarketTooltipPrice price = parseItem(object(raw, "item"));
                if (!requestIds.add(price.requestId())) {
                    throw new IllegalArgumentException("Duplicate response requestId");
                }
                result.add(price);
            }
            return List.copyOf(result);
        } catch (ProfileException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ProfileException(ProfileException.Code.INVALID_RESPONSE,
                    "The market tooltip service returned invalid data.",
                    Duration.ZERO, 0, exception);
        }
    }

    private static MarketTooltipPrice parseItem(JsonObject object) {
        rejectUnknown(object, ITEM_KEYS, "item");
        String requestId = string(required(object, "requestId"), "requestId", 64);
        String itemId = string(required(object, "itemId"), "itemId", 128);
        String variantKey = optionalString(object.get("variantKey"), "variantKey", 128);
        int quantity = exactInt(required(object, "quantity"), "quantity");
        MarketTooltipQuery query = new MarketTooltipQuery(itemId, variantKey, quantity);
        MarketType marketType = MarketType.parse(
                string(required(object, "marketType"), "marketType", 16));
        JsonObject rawQuotes = object(required(object, "quotes"), "quotes");
        EnumMap<MarketQuoteKind, MarketQuote> quotes = new EnumMap<>(MarketQuoteKind.class);
        for (Map.Entry<String, JsonElement> entry : rawQuotes.entrySet()) {
            MarketQuoteKind kind = MarketQuoteKind.parse(entry.getKey());
            if (entry.getValue() != null && !entry.getValue().isJsonNull()) {
                quotes.put(kind, parseQuote(object(entry.getValue(), entry.getKey())));
            }
        }
        for (MarketQuoteKind kind : MarketQuoteKind.values()) {
            if (!rawQuotes.has(kind.wireName())) {
                throw new IllegalArgumentException("Missing quote key: " + kind.wireName());
            }
        }
        return new MarketTooltipPrice(requestId, query, marketType, quotes);
    }

    private static MarketQuote parseQuote(JsonObject object) {
        String status = string(required(object, "status"), "status", 64);
        if (!object.has("unitCoins") || !object.has("totalCoins")) {
            throw new IllegalArgumentException("Quote omitted price fields");
        }
        BigDecimal unit = optionalPositiveDecimal(object.get("unitCoins"), "unitCoins");
        BigDecimal total = optionalPositiveDecimal(object.get("totalCoins"), "totalCoins");
        Instant sourceUpdatedAt = optionalInstant(object.get("sourceUpdatedAt"), "sourceUpdatedAt");
        Instant fetchedAt = optionalInstant(object.get("fetchedAt"), "fetchedAt");
        boolean stale = optionalBoolean(object.get("stale"), "stale");
        String confidence = optionalString(object.get("confidence"), "confidence", 64);
        return new MarketQuote(status, unit, total, sourceUpdatedAt, fetchedAt, stale, confidence);
    }

    private static JsonElement required(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            throw new IllegalArgumentException("Missing field: " + key);
        }
        return element;
    }

    private static JsonObject object(JsonElement element, String label) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("Expected object: " + label);
        }
        return element.getAsJsonObject();
    }

    private static JsonArray array(JsonElement element, String label) {
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException("Expected array: " + label);
        }
        return element.getAsJsonArray();
    }

    private static String string(JsonElement element, String label, int maxLength) {
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Expected string: " + label);
        }
        String value = element.getAsString().trim();
        if (value.isEmpty() || value.length() > maxLength
                || value.chars().anyMatch(MarketTooltipPriceJsonParser::unsafeCharacter)) {
            throw new IllegalArgumentException("Invalid string: " + label);
        }
        return value;
    }

    private static String optionalString(JsonElement element, String label, int maxLength) {
        if (element == null || element.isJsonNull()) return null;
        return string(element, label, maxLength);
    }

    private static int exactInt(JsonElement element, String label) {
        long value = exactLong(element, label);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Integer out of range: " + label);
        }
        return (int) value;
    }

    private static long exactLong(JsonElement element, String label) {
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Expected number: " + label);
        }
        BigDecimal value = element.getAsBigDecimal().stripTrailingZeros();
        if (value.scale() > 0) throw new IllegalArgumentException("Expected integer: " + label);
        return value.longValueExact();
    }

    private static BigDecimal optionalPositiveDecimal(JsonElement element, String label) {
        if (element == null || element.isJsonNull()) return null;
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Expected number: " + label);
        }
        BigDecimal value = element.getAsBigDecimal();
        if (value.signum() <= 0) throw new IllegalArgumentException("Invalid price: " + label);
        return value;
    }

    private static Instant optionalInstant(JsonElement element, String label) {
        if (element == null || element.isJsonNull()) return null;
        long value = exactLong(element, label);
        if (value < 0) throw new IllegalArgumentException("Negative timestamp: " + label);
        return Instant.ofEpochMilli(value);
    }

    private static boolean optionalBoolean(JsonElement element, String label) {
        if (element == null || element.isJsonNull()) return false;
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("Expected boolean: " + label);
        }
        return element.getAsBoolean();
    }

    private static void rejectUnknown(JsonObject object, Set<String> known, String label) {
        for (String key : object.keySet()) {
            if (!known.contains(key)) throw new IllegalArgumentException(
                    "Unknown " + label + " field: " + key);
        }
    }

    private static boolean unsafeCharacter(int character) {
        return character < 0x20 || character == 0x7f;
    }
}

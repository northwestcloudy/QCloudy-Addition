package cloudy.autume.addition.profile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Strict schema-v1 parser for transformed QCA profile responses. */
public final class ProfileJsonParser {
    private static final int MAX_PROFILES = 64;
    private static final int MAX_MESSAGE_LENGTH = 512;
    private static final int MAX_VERSION_LENGTH = 128;

    private ProfileJsonParser() {
    }

    public static ProfileSnapshot parse(String json) {
        try {
            JsonObject root = object(JsonParser.parseString(json), "response");
            int schema = integer(root, "schemaVersion", true);
            if (schema != ProfileSnapshot.SCHEMA_VERSION) {
                throw new ProfileException(ProfileException.Code.UNSUPPORTED_SCHEMA,
                        "Unsupported profile response schema: " + schema);
            }
            if (root.has("error")) {
                throw errorFromEnvelope(root, 0, Duration.ZERO);
            }

            ProfileIdentity identity = parseIdentity(object(root.get("identity"), "identity"));
            List<ProfileDescriptor> profiles = parseProfiles(array(root.get("profiles"), "profiles"));
            String selectedProfileId = string(root, "selectedProfileId", true, 64);
            Map<ProfileDataSource, SourceMetadata> metadata = parseSources(root.get("sources"));
            Map<ProfileSectionId, ProfileSection> sections = parseSections(root.get("sections"));
            boolean partial = bool(root, "partial", false);
            return new ProfileSnapshot(schema, identity, profiles, selectedProfileId,
                    metadata, sections, partial);
        } catch (ProfileException exception) {
            throw exception;
        } catch (JsonParseException | IllegalArgumentException | IllegalStateException exception) {
            throw new ProfileException(ProfileException.Code.INVALID_RESPONSE,
                    "The profile service returned invalid data.", Duration.ZERO, 0, exception);
        }
    }

    static ProfileException parseError(String json, int httpStatus, Duration retryAfter) {
        try {
            JsonObject root = object(JsonParser.parseString(json), "error response");
            return errorFromEnvelope(root, httpStatus, retryAfter);
        } catch (RuntimeException exception) {
            return new ProfileException(ProfileException.Code.INVALID_RESPONSE,
                    "The profile service returned an invalid error response.",
                    retryAfter, httpStatus, exception);
        }
    }

    static ProfileSupplement parseSupplement(String json,
                                              String expectedPlayerUuid,
                                              String expectedProfileId,
                                              ProfileSectionId expectedSection) {
        if (expectedSection != ProfileSectionId.MUSEUM
                && expectedSection != ProfileSectionId.GARDEN) {
            throw new IllegalArgumentException("Only Museum and Garden are supplementary sections");
        }
        try {
            JsonObject root = object(JsonParser.parseString(json), "supplement response");
            int schema = integer(root, "schemaVersion", true);
            if (schema != ProfileSnapshot.SCHEMA_VERSION) {
                throw new ProfileException(ProfileException.Code.UNSUPPORTED_SCHEMA,
                        "Unsupported profile response schema: " + schema);
            }
            if (root.has("error")) {
                throw errorFromEnvelope(root, 0, Duration.ZERO);
            }
            JsonObject identity = object(root.get("identity"), "identity");
            String playerUuid = canonicalId(string(identity, "uuid", true, 64));
            String profileId = canonicalId(string(root, "profileId", true, 64));
            if (!playerUuid.equals(canonicalId(expectedPlayerUuid))
                    || !profileId.equals(canonicalId(expectedProfileId))) {
                throw new IllegalArgumentException("Supplement identifiers do not match request");
            }

            JsonObject sections = object(root.get("sections"), "sections");
            JsonObject rawSection = object(sections.get(expectedSection.wireName()),
                    expectedSection.wireName() + " section");
            ProfileSectionStatus sectionStatus = ProfileSectionStatus.parse(
                    string(rawSection, "status", true, 32));
            String message = string(rawSection, "message", false, MAX_MESSAGE_LENGTH);
            JsonElement rawPayload = rawSection.get("payload");
            JsonObject payload = rawPayload == null || rawPayload.isJsonNull()
                    ? new JsonObject() : object(rawPayload,
                    expectedSection.wireName() + " payload");

            ProfileDataSource expectedSource = expectedSection == ProfileSectionId.MUSEUM
                    ? ProfileDataSource.MUSEUM : ProfileDataSource.GARDEN;
            JsonObject sources = object(root.get("sources"), "sources");
            JsonObject rawSource = object(sources.get(expectedSource.wireName()),
                    expectedSource.wireName() + " source metadata");
            ProfileSourceStatus sourceStatus = ProfileSourceStatus.parse(
                    string(rawSource, "status", true, 32));
            SourceMetadata metadata = new SourceMetadata(expectedSource, sourceStatus,
                    instant(rawSource, "fetchedAt"),
                    instant(rawSource, "expiresAt"),
                    instant(rawSource, "staleUntil"),
                    instant(rawSource, "nextRefreshAt"),
                    string(rawSource, "sourceVersion", false, MAX_VERSION_LENGTH));
            return new ProfileSupplement(playerUuid, profileId,
                    new ProfileSection(expectedSection, sectionStatus, message, payload), metadata);
        } catch (ProfileException exception) {
            throw exception;
        } catch (JsonParseException | IllegalArgumentException | IllegalStateException exception) {
            throw new ProfileException(ProfileException.Code.INVALID_RESPONSE,
                    "The supplementary profile service returned invalid data.",
                    Duration.ZERO, 0, exception);
        }
    }

    static ProfileException errorFromEnvelope(JsonObject root,
                                              int httpStatus,
                                              Duration retryAfter) {
        try {
            int schema = integer(root, "schemaVersion", true);
            if (schema != ProfileSnapshot.SCHEMA_VERSION) {
                return new ProfileException(ProfileException.Code.UNSUPPORTED_SCHEMA,
                        "Unsupported profile error schema: " + schema,
                        retryAfter, httpStatus, null);
            }
            JsonObject error = object(root.get("error"), "error");
            String code = string(error, "code", true, 64);
            String message = string(error, "message", false, 256);
            ProfileException.Code mapped;
            try {
                mapped = canonicalErrorCode(code);
            } catch (IllegalArgumentException ignored) {
                mapped = ProfileException.Code.INVALID_RESPONSE;
            }
            return new ProfileException(mapped,
                    message.isBlank() ? defaultMessage(mapped) : message,
                    retryAfter, httpStatus, null);
        } catch (RuntimeException exception) {
            return new ProfileException(ProfileException.Code.INVALID_RESPONSE,
                    "The profile service returned an invalid error response.",
                    retryAfter, httpStatus, exception);
        }
    }

    private static ProfileIdentity parseIdentity(JsonObject identity) {
        return new ProfileIdentity(
                string(identity, "query", true, 64),
                string(identity, "uuid", true, 64),
                string(identity, "name", true, 16),
                string(identity, "skinTextureUrl", false, 2048));
    }

    private static List<ProfileDescriptor> parseProfiles(JsonArray array) {
        if (array.isEmpty() || array.size() > MAX_PROFILES) {
            throw new IllegalArgumentException("Invalid profile count");
        }
        List<ProfileDescriptor> profiles = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            JsonObject value = object(element, "profile descriptor");
            profiles.add(new ProfileDescriptor(
                    string(value, "profileId", true, 64),
                    string(value, "cuteName", true, 64),
                    bool(value, "selected", false),
                    string(value, "gameMode", false, 64),
                    integer(value, "memberCount", true)));
        }
        return List.copyOf(profiles);
    }

    private static Map<ProfileDataSource, SourceMetadata> parseSources(JsonElement element) {
        JsonObject sources = element == null || element.isJsonNull()
                ? new JsonObject() : object(element, "sources");
        EnumMap<ProfileDataSource, SourceMetadata> result =
                new EnumMap<>(ProfileDataSource.class);
        for (ProfileDataSource source : ProfileDataSource.values()) {
            JsonElement raw = sources.get(source.wireName());
            if (raw == null || raw.isJsonNull()) {
                result.put(source, SourceMetadata.notRequested(source));
                continue;
            }
            JsonObject value = object(raw, source.wireName() + " source metadata");
            ProfileSourceStatus status = ProfileSourceStatus.parse(
                    string(value, "status", true, 32));
            result.put(source, new SourceMetadata(source, status,
                    instant(value, "fetchedAt"),
                    instant(value, "expiresAt"),
                    instant(value, "staleUntil"),
                    instant(value, "nextRefreshAt"),
                    string(value, "sourceVersion", false, MAX_VERSION_LENGTH)));
        }
        return result;
    }

    private static Map<ProfileSectionId, ProfileSection> parseSections(JsonElement element) {
        JsonObject sections = element == null || element.isJsonNull()
                ? new JsonObject() : object(element, "sections");
        LinkedHashMap<ProfileSectionId, ProfileSection> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : sections.entrySet()) {
            ProfileSectionId id = ProfileSectionId.fromWireName(entry.getKey()).orElse(null);
            // Additive fields from a newer backend are ignored under schema v1. In particular,
            // there is deliberately no Dungeon section/model in this implementation.
            if (id == null) continue;
            JsonObject value = object(entry.getValue(), entry.getKey() + " section");
            ProfileSectionStatus status = ProfileSectionStatus.parse(
                    string(value, "status", true, 32));
            String message = string(value, "message", false, MAX_MESSAGE_LENGTH);
            JsonElement rawPayload = value.get("payload");
            JsonObject payload = rawPayload == null || rawPayload.isJsonNull()
                    ? new JsonObject() : object(rawPayload, entry.getKey() + " payload");
            result.put(id, new ProfileSection(id, status, message, payload));
        }
        return result;
    }

    private static Instant instant(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) return null;
        long value = longInteger(element, key);
        if (value < 0) throw new IllegalArgumentException("Negative timestamp: " + key);
        return Instant.ofEpochMilli(value);
    }

    private static int integer(JsonObject object, String key, boolean required) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            if (!required) return 0;
            throw new IllegalArgumentException("Missing integer: " + key);
        }
        long value = longInteger(element, key);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Integer out of range: " + key);
        }
        return (int) value;
    }

    private static long longInteger(JsonElement element, String key) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Expected number: " + key);
        }
        BigDecimal number = element.getAsBigDecimal().stripTrailingZeros();
        if (number.scale() > 0) throw new IllegalArgumentException("Expected integer: " + key);
        return number.longValueExact();
    }

    private static String string(JsonObject object,
                                 String key,
                                 boolean required,
                                 int maxLength) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            if (required) throw new IllegalArgumentException("Missing string: " + key);
            return "";
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Expected string: " + key);
        }
        String result = element.getAsString().trim();
        if ((required && result.isEmpty()) || result.length() > maxLength) {
            throw new IllegalArgumentException("Invalid string: " + key);
        }
        return result;
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) return fallback;
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("Expected boolean: " + key);
        }
        return element.getAsBoolean();
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

    private static String defaultMessage(ProfileException.Code code) {
        return switch (code) {
            case INVALID_TARGET -> "Enter a valid Minecraft player name or UUID.";
            case PLAYER_NOT_FOUND -> "That Minecraft player could not be found.";
            case NO_SKYBLOCK_PROFILES -> "That player has no SkyBlock profiles.";
            case PROFILE_NOT_FOUND -> "That SkyBlock profile could not be found.";
            case RATE_LIMITED -> "The profile service is rate limited. Try again later.";
            case UPSTREAM_UNAVAILABLE -> "Hypixel profile data is temporarily unavailable.";
            case SERVICE_UNAVAILABLE -> "The QCA profile service is temporarily unavailable.";
            case UNAUTHORIZED -> "The QCA profile service is not authorised.";
            case UNSUPPORTED_SCHEMA -> "This QCA version does not support the profile response.";
            case RESPONSE_TOO_LARGE -> "The profile response was too large.";
            case CANCELLED -> "The profile request was cancelled.";
            case INVALID_RESPONSE -> "The profile service returned invalid data.";
        };
    }

    private static ProfileException.Code canonicalErrorCode(String value) {
        String code = value.toUpperCase(Locale.ROOT);
        return switch (code) {
            case "INVALID_UUID" -> ProfileException.Code.INVALID_TARGET;
            case "HYPIXEL_PLAYER_NOT_FOUND" -> ProfileException.Code.PLAYER_NOT_FOUND;
            case "SKYBLOCK_PROFILES_NOT_FOUND" -> ProfileException.Code.NO_SKYBLOCK_PROFILES;
            case "UPSTREAM_TEMPORARY_FAILURE", "UPSTREAM_AUTHENTICATION_FAILURE",
                    "UPSTREAM_REJECTED_REQUEST", "UPSTREAM_NOT_FOUND" ->
                    ProfileException.Code.UPSTREAM_UNAVAILABLE;
            case "NOT_READY", "BAZAAR_NOT_READY", "AUCTIONS_NOT_READY" ->
                    ProfileException.Code.SERVICE_UNAVAILABLE;
            default -> ProfileException.Code.valueOf(code);
        };
    }

    private static String canonicalId(String value) {
        if (value == null) throw new IllegalArgumentException("Missing identifier");
        String canonical = value.replace("-", "").toLowerCase(Locale.ROOT);
        if (!canonical.matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException("Invalid identifier");
        }
        return canonical;
    }
}

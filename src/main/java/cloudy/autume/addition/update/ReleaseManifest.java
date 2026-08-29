package cloudy.autume.addition.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Pattern;

/** Strict parser and policy gate for QCA's stable-release manifest. */
public final class ReleaseManifest {
    public static final int SCHEMA_VERSION = 1;
    private static final Pattern VERSION = Pattern.compile("[0-9]+(?:\\.[0-9]+){2}");
    private static final Pattern SHA_256 = Pattern.compile("sha256:[0-9a-f]{64}");
    private static final String ASSET_PREFIX = "QCloudy_Addition-";
    private static final String RELEASE_PATH = "/northwestcloudy/QCloudy-Addition/releases/download/";

    private ReleaseManifest() {
    }

    public static Optional<AvailableRelease> findUpdate(String json, ReleaseBuildInfo build) {
        if (json == null || build == null || !build.checksStableReleases()) return Optional.empty();
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (integer(root, "schemaVersion") != SCHEMA_VERSION) return Optional.empty();
            if (!"Release".equals(string(root, "channel"))) return Optional.empty();

            long sequence = longInteger(root, "releaseSequence");
            if (sequence <= build.releaseBaselineSequence()) return Optional.empty();

            String version = string(root, "version");
            if (!VERSION.matcher(version).matches()) return Optional.empty();
            if (!("v" + version).equals(string(root, "tag"))) return Optional.empty();

            String expectedName = ASSET_PREFIX + version + "+" + build.minecraftVersion() + "-Release.jar";
            JsonElement assetsElement = root.get("assets");
            if (assetsElement == null || !assetsElement.isJsonArray()) return Optional.empty();
            AvailableRelease match = null;
            for (JsonElement element : assetsElement.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                JsonObject asset = element.getAsJsonObject();
                if (!build.minecraftVersion().equals(string(asset, "minecraft"))) continue;
                if (!expectedName.equals(string(asset, "name"))) continue;
                if (!SHA_256.matcher(string(asset, "digest")).matches()) continue;
                if (!officialReleaseAsset(string(asset, "url"), expectedName, "v" + version)) continue;
                // Ambiguous duplicate assets are invalid rather than relying on array order.
                if (match != null) return Optional.empty();
                match = new AvailableRelease(version, sequence);
            }
            return Optional.ofNullable(match);
        } catch (RuntimeException ignored) {
            // Malformed or incomplete remote data must fail closed.
        }
        return Optional.empty();
    }

    private static boolean officialReleaseAsset(String value, String expectedName, String tag) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme())) return false;
            if (!"github.com".equalsIgnoreCase(uri.getHost())) return false;
            if (uri.getPort() != -1 || uri.getUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) return false;
            String path = uri.getPath();
            return (RELEASE_PATH + tag + "/" + expectedName).equals(path);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : "";
    }

    private static int integer(JsonObject object, String key) {
        long value = longInteger(object, key);
        return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE ? (int) value : Integer.MIN_VALUE;
    }

    private static long longInteger(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return Long.MIN_VALUE;
        }
        String raw = value.getAsString();
        if (!raw.matches("-?[0-9]+")) return Long.MIN_VALUE;
        return Long.parseLong(raw);
    }

    public record AvailableRelease(String version, long sequence) {
    }
}

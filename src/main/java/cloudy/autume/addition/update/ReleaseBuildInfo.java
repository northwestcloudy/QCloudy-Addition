package cloudy.autume.addition.update;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/** Build-time release metadata embedded in every QCA JAR. */
public record ReleaseBuildInfo(
        String channel,
        String version,
        String minecraftVersion,
        long releaseBaselineSequence) {
    private static final String RESOURCE = "/qcloudy_addition.release.properties";

    public boolean checksStableReleases() {
        return releaseBaselineSequence > 0
                && ("Beta".equals(channel) || "Release".equals(channel));
    }

    public String displayVersion() {
        return channel + " " + version;
    }

    public static ReleaseBuildInfo load() throws IOException {
        Properties properties = new Properties();
        try (var stream = ReleaseBuildInfo.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IOException("Missing " + RESOURCE);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }

        String channel = required(properties, "channel");
        String version = required(properties, "version");
        String minecraft = required(properties, "minecraft");
        long baseline;
        try {
            baseline = Long.parseLong(required(properties, "releaseBaselineSequence"));
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid releaseBaselineSequence", exception);
        }
        if (baseline <= 0) throw new IOException("releaseBaselineSequence must be positive");
        return new ReleaseBuildInfo(channel, version, minecraft, baseline);
    }

    private static String required(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) throw new IOException("Missing release metadata: " + key);
        return value.trim();
    }
}

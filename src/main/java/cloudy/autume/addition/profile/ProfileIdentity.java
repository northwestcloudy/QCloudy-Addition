package cloudy.autume.addition.profile;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Canonical player identity returned by the QCA backend. */
public record ProfileIdentity(String query, String uuid, String name, String skinTextureUrl) {
    private static final Pattern UUID = Pattern.compile("[0-9a-f]{32}");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    public ProfileIdentity {
        query = Objects.requireNonNull(query, "query").trim();
        uuid = Objects.requireNonNull(uuid, "uuid")
                .replace("-", "").toLowerCase(Locale.ROOT);
        name = Objects.requireNonNull(name, "name").trim();
        skinTextureUrl = skinTextureUrl == null ? "" : skinTextureUrl.trim();
        if (query.isEmpty() || query.length() > 64) {
            throw new IllegalArgumentException("Invalid identity query");
        }
        if (!UUID.matcher(uuid).matches()) {
            throw new IllegalArgumentException("Invalid canonical UUID");
        }
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid canonical player name");
        }
    }
}

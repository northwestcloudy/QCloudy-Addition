package cloudy.autume.addition.profile;

import java.util.Locale;
import java.util.Optional;

/** Independent upstream/cache sources used by the profile viewer. */
public enum ProfileDataSource {
    IDENTITY,
    PLAYER,
    PROFILES,
    MUSEUM,
    GARDEN,
    MARKET;

    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<ProfileDataSource> fromWireName(String value) {
        if (value == null) return Optional.empty();
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}

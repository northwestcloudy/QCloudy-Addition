package cloudy.autume.addition.profile;

import java.util.Locale;

/** Display status for one independently renderable section. */
public enum ProfileSectionStatus {
    AVAILABLE,
    STALE,
    PRIVATE,
    NOT_FOUND,
    NOT_LOADED,
    ERROR;

    static ProfileSectionStatus parse(String value) {
        if (value == null) throw new IllegalArgumentException("Missing section status");
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown section status: " + value, exception);
        }
    }
}

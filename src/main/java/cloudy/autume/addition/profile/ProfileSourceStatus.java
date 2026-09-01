package cloudy.autume.addition.profile;

import java.util.Locale;

/** Cache/source state reported independently for each profile data source. */
public enum ProfileSourceStatus {
    FRESH,
    STALE,
    PRIVATE,
    NOT_FOUND,
    NOT_REQUESTED,
    ERROR;

    static ProfileSourceStatus parse(String value) {
        if (value == null) throw new IllegalArgumentException("Missing source status");
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown source status: " + value, exception);
        }
    }
}

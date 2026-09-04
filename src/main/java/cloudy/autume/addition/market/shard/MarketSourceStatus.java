package cloudy.autume.addition.market.shard;

import java.util.Locale;

/** Availability state for a published market snapshot. */
public enum MarketSourceStatus {
    FRESH,
    STALE;

    static MarketSourceStatus parse(String value) {
        if (value == null) throw new IllegalArgumentException("Missing source status");
        try {
            MarketSourceStatus status = valueOf(value.trim().toUpperCase(Locale.ROOT));
            if (status != FRESH && status != STALE) throw new IllegalArgumentException();
            return status;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported market source status: " + value, exception);
        }
    }
}

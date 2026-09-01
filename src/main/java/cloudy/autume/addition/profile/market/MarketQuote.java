package cloudy.autume.addition.profile.market;

import java.math.BigDecimal;
import java.time.Instant;

/** One quantity-aware quote. Non-displayable statuses never masquerade as zero. */
public record MarketQuote(String status,
                          BigDecimal unitCoins,
                          BigDecimal totalCoins,
                          Instant sourceUpdatedAt,
                          Instant fetchedAt,
                          boolean stale,
                          String confidence) {
    public MarketQuote {
        if (status == null) throw new IllegalArgumentException("Missing quote status");
        status = status.trim();
        if (!status.matches("[a-z][a-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("Invalid quote status");
        }
        boolean displayable = status.equals("available") || status.equals("stale");
        if (displayable) {
            if (!positive(unitCoins) || !positive(totalCoins)) {
                throw new IllegalArgumentException("Displayable quotes require positive prices");
            }
        } else if (unitCoins != null || totalCoins != null) {
            throw new IllegalArgumentException("Unavailable quotes must not contain prices");
        }
        if (sourceUpdatedAt != null && sourceUpdatedAt.isBefore(Instant.EPOCH)) {
            throw new IllegalArgumentException("Invalid sourceUpdatedAt");
        }
        if (fetchedAt != null && fetchedAt.isBefore(Instant.EPOCH)) {
            throw new IllegalArgumentException("Invalid fetchedAt");
        }
        stale = stale || status.equals("stale");
        if (confidence != null) {
            confidence = confidence.trim();
            if (confidence.isEmpty()) confidence = null;
            if (confidence != null && (confidence.length() > 64
                    || confidence.chars().anyMatch(MarketQuote::unsafeCharacter))) {
                throw new IllegalArgumentException("Invalid confidence");
            }
        }
    }

    public boolean displayable() {
        return status.equals("available") || status.equals("stale");
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static boolean unsafeCharacter(int character) {
        return character < 0x20 || character == 0x7f;
    }
}

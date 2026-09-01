package cloudy.autume.addition.profile.market;

import java.util.Objects;

/** Unpadded tooltip text; the screen aligns columns in pixels. */
public record PriceTooltipRow(MarketQuoteKind quoteKind,
                              String label,
                              String value,
                              boolean stale) {
    public PriceTooltipRow {
        quoteKind = Objects.requireNonNull(quoteKind, "quoteKind");
        label = Objects.requireNonNull(label, "label");
        value = Objects.requireNonNull(value, "value");
        if (label.isBlank() || value.isBlank()) {
            throw new IllegalArgumentException("Tooltip row text must not be blank");
        }
    }
}

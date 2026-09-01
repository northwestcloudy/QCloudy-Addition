package cloudy.autume.addition.profile.market;

import java.util.Objects;

/** One quantity-aware tooltip valuation request. */
public record MarketTooltipQuery(MarketItemKey key, int quantity) {
    public MarketTooltipQuery {
        key = Objects.requireNonNull(key, "key");
        if (quantity < 1) throw new IllegalArgumentException("quantity must be positive");
    }

    public MarketTooltipQuery(String itemId, String variantKey, int quantity) {
        this(new MarketItemKey(itemId, variantKey), quantity);
    }
}

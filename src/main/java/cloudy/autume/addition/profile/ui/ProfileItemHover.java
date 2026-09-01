package cloudy.autume.addition.profile.ui;

/** Stable integration hook for a future market-backed item tooltip provider. */
public record ProfileItemHover(String itemId, String variantKey, int count,
                               String displayName, String rarity) {
    public ProfileItemHover {
        itemId = itemId == null ? "" : itemId;
        variantKey = variantKey == null ? "" : variantKey;
        count = Math.max(1, count);
        displayName = displayName == null ? "" : displayName;
        rarity = rarity == null ? "" : rarity;
    }
}

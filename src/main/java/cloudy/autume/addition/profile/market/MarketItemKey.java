package cloudy.autume.addition.profile.market;

import java.util.Locale;

/** Canonical item/variant identity used by the PV market client. */
public record MarketItemKey(String itemId, String variantKey) {
    private static final int MAX_ITEM_ID_LENGTH = 128;
    public static final int MAX_VARIANT_KEY_LENGTH = 128;

    public MarketItemKey {
        if (itemId == null) throw new IllegalArgumentException("Missing itemId");
        itemId = itemId.trim().toUpperCase(Locale.ROOT);
        if (itemId.isEmpty() || itemId.length() > MAX_ITEM_ID_LENGTH
                || !itemId.matches("[A-Z0-9_:-]+")) {
            throw new IllegalArgumentException("Invalid itemId");
        }
        if (variantKey != null) {
            variantKey = variantKey.trim();
            if (variantKey.isEmpty()) variantKey = null;
            if (variantKey != null && (variantKey.length() > MAX_VARIANT_KEY_LENGTH
                    || variantKey.chars().anyMatch(MarketItemKey::isUnsafeCharacter))) {
                throw new IllegalArgumentException("Invalid variantKey");
            }
        }
    }

    private static boolean isUnsafeCharacter(int character) {
        return character < 0x20 || character == 0x7f;
    }
}

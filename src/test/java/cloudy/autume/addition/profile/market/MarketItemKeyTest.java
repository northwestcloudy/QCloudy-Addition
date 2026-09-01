package cloudy.autume.addition.profile.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class MarketItemKeyTest {
    @Test
    void normalizesItemIdAndMatchesTheBackendVariantLimit() {
        MarketItemKey key = new MarketItemKey(" hyperion ", "x".repeat(128));

        assertEquals("HYPERION", key.itemId());
        assertEquals(128, key.variantKey().length());
        assertThrows(IllegalArgumentException.class,
                () -> new MarketItemKey("HYPERION", "x".repeat(129)));
    }
}

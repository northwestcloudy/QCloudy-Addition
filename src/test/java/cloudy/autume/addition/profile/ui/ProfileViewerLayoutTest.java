package cloudy.autume.addition.profile.ui;

import cloudy.autume.addition.profile.ProfileSectionId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProfileViewerLayoutTest {
    @Test
    void normalWindowUsesTheMaximumCanvasAndKeepsColumnsDisjoint() {
        ProfileViewerLayout.Layout layout = ProfileViewerLayout.calculate(1_920, 1_080);

        assertEquals(900, layout.windowWidth());
        assertEquals(520, layout.windowHeight());
        assertTrue(layout.sidebarX() + layout.sidebarWidth() <= layout.contentX());
        assertEquals(layout.windowRight(), layout.contentX() + layout.contentWidth());
        assertEquals(layout.windowBottom(), layout.contentY() + layout.contentHeight());
        assertTrue(layout.hasBody());
    }

    @Test
    void smallWindowNeverProducesNegativeOrOffScreenGeometry() {
        ProfileViewerLayout.Layout layout = ProfileViewerLayout.calculate(100, 200);

        assertTrue(layout.windowX() >= 0);
        assertTrue(layout.windowY() >= 0);
        assertTrue(layout.windowRight() <= 100);
        assertTrue(layout.windowBottom() <= 200);
        assertTrue(layout.sidebarWidth() >= 0);
        assertTrue(layout.sidebarHeight() >= 0);
        assertTrue(layout.contentWidth() >= 0);
        assertTrue(layout.contentHeight() >= 0);
        assertTrue(layout.contentWidth() > 0);
        assertTrue(layout.hasBody());
    }

    @Test
    void onePixelViewportStillProducesValidBounds() {
        ProfileViewerLayout.Layout layout = ProfileViewerLayout.calculate(0, 0);

        assertEquals(1, layout.windowWidth());
        assertEquals(1, layout.windowHeight());
        assertEquals(0, layout.windowX());
        assertEquals(0, layout.windowY());
        assertEquals(0, layout.contentWidth());
        assertEquals(0, layout.contentHeight());
    }

    @Test
    void opaqueItemPayloadsAreRedactedBeforeRendering() {
        String encoded = "A".repeat(160);

        assertTrue(ProfileViewerScreen.shouldRedactOpaqueItemData("data", encoded));
        assertTrue(ProfileViewerScreen.shouldRedactOpaqueItemData("item_bytes", encoded));
        assertTrue(ProfileViewerScreen.shouldRedactOpaqueItemData("ITEM-BYTES", encoded));
        assertTrue(ProfileViewerScreen.shouldRedactOpaqueItemData("itemBytes", encoded));
        assertFalse(ProfileViewerScreen.shouldRedactOpaqueItemData("item_name", encoded));
        assertFalse(ProfileViewerScreen.shouldRedactOpaqueItemData("data", "short value"));
    }

    @Test
    void unloadedMarketDataUsesTheExplicitNoValuationState() {
        assertTrue(ProfileViewerScreen.marketPricesNotLoaded(
                ProfileSectionId.MARKET, "NOT_LOADED"));
        assertFalse(ProfileViewerScreen.marketPricesNotLoaded(
                ProfileSectionId.MARKET, "AVAILABLE"));
        assertFalse(ProfileViewerScreen.marketPricesNotLoaded(
                ProfileSectionId.OVERVIEW, "NOT_LOADED"));
    }
}

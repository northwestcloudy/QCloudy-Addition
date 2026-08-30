package cloudy.autume.addition.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VerticalScrollbarTest {
    @Test
    void geometryTracksViewportRatioAndReachesBothEndsExactly() {
        VerticalScrollbar.Geometry top = VerticalScrollbar.calculateGeometry(7, 10, 300, 700, 0);
        assertTrue(top.visible());
        assertEquals(90, top.thumbHeight());
        assertEquals(10, top.thumbY());

        VerticalScrollbar.Geometry bottom = VerticalScrollbar.calculateGeometry(7, 10, 300, 700, 700);
        assertEquals(220, bottom.thumbY());
        assertEquals(310, bottom.thumbBottom());
        assertEquals(bottom.trackBottom(), bottom.thumbBottom());
    }

    @Test
    void geometryUsesReachableMinimumThumbAndHidesWithoutOverflow() {
        VerticalScrollbar.Geometry longPage = VerticalScrollbar.calculateGeometry(2, 3, 100, 100_000, 0);
        assertEquals(VerticalScrollbar.MINIMUM_THUMB_HEIGHT, longPage.thumbHeight());

        VerticalScrollbar.Geometry noOverflow = VerticalScrollbar.calculateGeometry(2, 3, 100, 0, 0);
        assertFalse(noOverflow.visible());
        assertEquals(100, noOverflow.thumbHeight());
    }

    @Test
    void hiddenScrollbarIsCompletelyInert() {
        VerticalScrollbar scrollbar = new VerticalScrollbar();
        scrollbar.update(10, 20, 200, 0, 0);

        assertFalse(scrollbar.contains(12, 40));
        assertFalse(scrollbar.mouseClicked(0, 12, 40, 0).consumed());
        assertFalse(scrollbar.mouseDragged(0, 100, 0).consumed());
        assertFalse(scrollbar.mouseReleased(0, 100, 0).consumed());
        assertFalse(scrollbar.mouseScrolled(-1.0, 24, 0).consumed());
    }

    @Test
    void grabbingThumbPreservesThePointerOffsetAndClampsOutsideTrack() {
        VerticalScrollbar scrollbar = new VerticalScrollbar();
        scrollbar.update(10, 20, 200, 800, 400);

        VerticalScrollbar.Interaction clicked = scrollbar.mouseClicked(0, 12, 120, 400);
        assertTrue(clicked.consumed());
        assertTrue(scrollbar.dragging());
        assertEquals(400, clicked.scroll());

        VerticalScrollbar.Interaction stationary = scrollbar.mouseDragged(0, 120, 400);
        assertEquals(400, stationary.scroll());
        assertEquals(0, scrollbar.mouseDragged(0, -500, stationary.scroll()).scroll());
        assertEquals(800, scrollbar.mouseDragged(0, 2_000, 0).scroll());

        VerticalScrollbar.Interaction released = scrollbar.mouseReleased(0, 2_000, 800);
        assertTrue(released.consumed());
        assertEquals(800, released.scroll());
        assertFalse(scrollbar.dragging());
    }

    @Test
    void trackClicksPageWithoutStartingADrag() {
        VerticalScrollbar scrollbar = new VerticalScrollbar();
        scrollbar.update(10, 20, 200, 800, 400);
        int expectedPage = 200 - VerticalScrollbar.PAGE_OVERLAP;

        VerticalScrollbar.Interaction above = scrollbar.mouseClicked(0, 12, 50, 400);
        assertTrue(above.consumed());
        assertEquals(400 - expectedPage, above.scroll());
        assertFalse(scrollbar.dragging());

        scrollbar.update(10, 20, 200, 800, 400);
        VerticalScrollbar.Interaction below = scrollbar.mouseClicked(0, 12, 190, 400);
        assertEquals(400 + expectedPage, below.scroll());
        assertFalse(scrollbar.dragging());
    }

    @Test
    void nearEndTrackClicksClampToExactLimits() {
        VerticalScrollbar scrollbar = new VerticalScrollbar();
        scrollbar.update(10, 20, 200, 800, 790);
        assertEquals(800, scrollbar.mouseClicked(0, 12, 219, 790).scroll());

        scrollbar.update(10, 20, 200, 800, 10);
        assertEquals(0, scrollbar.mouseClicked(0, 12, 20, 10).scroll());
    }

    @Test
    void wheelIsIgnoredDuringGrabAndWorksAgainAfterRelease() {
        VerticalScrollbar scrollbar = new VerticalScrollbar();
        scrollbar.update(10, 20, 200, 800, 400);
        assertTrue(scrollbar.mouseClicked(0, 12, 120, 400).consumed());

        VerticalScrollbar.Interaction heldWheel = scrollbar.mouseScrolled(1.0, 24, 400);
        assertTrue(heldWheel.consumed());
        assertEquals(400, heldWheel.scroll());

        assertTrue(scrollbar.mouseReleased(0, 120, 400).consumed());
        VerticalScrollbar.Interaction releasedWheel = scrollbar.mouseScrolled(1.0, 24, 400);
        assertTrue(releasedWheel.consumed());
        assertEquals(376, releasedWheel.scroll());
    }

    @Test
    void configContentReservesTheFullInteractiveScrollbarGutter() {
        assertTrue(ConfigScreen.CONTENT_SCROLLBAR_GUTTER >= VerticalScrollbar.WIDTH);
    }

    @Test
    void nonPrimaryAndOutsideClicksPassThrough() {
        VerticalScrollbar scrollbar = new VerticalScrollbar();
        scrollbar.update(10, 20, 200, 800, 400);

        assertFalse(scrollbar.mouseClicked(1, 12, 120, 400).consumed());
        assertFalse(scrollbar.mouseClicked(0, 9, 120, 400).consumed());
        assertFalse(scrollbar.mouseClicked(0, 12, 220, 400).consumed());
    }

    @Test
    void contentOrViewportChangesCancelAnActiveGrab() {
        VerticalScrollbar scrollbar = new VerticalScrollbar();
        scrollbar.update(10, 20, 200, 800, 400);
        assertTrue(scrollbar.mouseClicked(0, 12, 120, 400).consumed());
        assertTrue(scrollbar.dragging());

        scrollbar.update(10, 20, 180, 600, 400);
        assertFalse(scrollbar.dragging());
        assertFalse(scrollbar.mouseDragged(0, 80, 400).consumed());
    }
}

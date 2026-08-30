package cloudy.autume.addition.config;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Shared browser-style vertical scrollbar used by QCA's hand-built screens.
 * Geometry and input mapping live here so every screen gets the same grab,
 * page-click and clamping behaviour.
 */
final class VerticalScrollbar {
    static final int WIDTH = 7;
    static final int MINIMUM_THUMB_HEIGHT = 20;
    static final int PAGE_OVERLAP = 24;

    private Geometry geometry = Geometry.hidden();
    private boolean dragging;
    private double grabOffsetY;

    void update(int trackX, int trackY, int trackHeight, int maximumScroll, int scroll) {
        Geometry next = calculateGeometry(trackX, trackY, trackHeight, maximumScroll, scroll);
        if (dragging && !geometry.sameLayout(next)) cancelDrag();
        geometry = next;
    }

    void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int accentColor) {
        if (!geometry.visible()) return;
        boolean hovered = geometry.containsTrack(mouseX, mouseY);
        boolean active = hovered || dragging;
        graphics.fill(geometry.trackX(), geometry.trackY(), geometry.trackRight(), geometry.trackBottom(),
                active ? AcaUiTheme.BORDER_SOFT : AcaUiTheme.CONTROL);
        int inset = active ? 0 : 1;
        graphics.fill(geometry.trackX() + inset, geometry.thumbY(), geometry.trackRight() - inset,
                geometry.thumbBottom(), active ? accentColor : AcaUiTheme.ACCENT_DARK);
        if (dragging) {
            graphics.outline(geometry.trackX(), geometry.thumbY(), geometry.trackWidth(),
                    geometry.thumbHeight(), accentColor);
        }
    }

    Interaction mouseClicked(int button, double mouseX, double mouseY, int currentScroll) {
        if (button != 0 || !geometry.containsTrack(mouseX, mouseY)) {
            return Interaction.notConsumed(currentScroll);
        }
        int clamped = geometry.clampScroll(currentScroll);
        if (geometry.containsThumb(mouseX, mouseY)) {
            dragging = true;
            grabOffsetY = mouseY - geometry.thumbY();
            return Interaction.consumed(clamped);
        }
        int page = Math.max(1, geometry.trackHeight() - PAGE_OVERLAP);
        int target = mouseY < geometry.thumbY() ? clamped - page : clamped + page;
        return Interaction.consumed(geometry.clampScroll(target));
    }

    Interaction mouseDragged(int button, double mouseY, int currentScroll) {
        if (button != 0 || !dragging || !geometry.visible()) {
            return Interaction.notConsumed(currentScroll);
        }
        return Interaction.consumed(geometry.scrollForThumbTop(mouseY - grabOffsetY));
    }

    Interaction mouseReleased(int button, double mouseY, int currentScroll) {
        if (button != 0 || !dragging || !geometry.visible()) {
            return Interaction.notConsumed(currentScroll);
        }
        int target = geometry.scrollForThumbTop(mouseY - grabOffsetY);
        cancelDrag();
        return Interaction.consumed(target);
    }

    Interaction mouseScrolled(double vertical, int step, int currentScroll) {
        if (!geometry.visible()) return Interaction.notConsumed(currentScroll);
        int clamped = geometry.clampScroll(currentScroll);
        // The grab offset is tied to the current thumb. Moving scroll by wheel
        // while it is held would make the next drag event jump unexpectedly.
        if (dragging) return Interaction.consumed(clamped);
        int target = clamped - (int) Math.round(vertical * Math.max(1, step));
        return Interaction.consumed(geometry.clampScroll(target));
    }

    boolean contains(double mouseX, double mouseY) {
        return geometry.containsTrack(mouseX, mouseY);
    }

    boolean dragging() {
        return dragging;
    }

    void cancelDrag() {
        dragging = false;
        grabOffsetY = 0.0;
    }

    static Geometry calculateGeometry(int trackX, int trackY, int trackHeight,
                                      int maximumScroll, int scroll) {
        int safeHeight = Math.max(0, trackHeight);
        int safeMaximum = Math.max(0, maximumScroll);
        if (safeHeight == 0 || safeMaximum == 0) {
            return new Geometry(trackX, trackY, WIDTH, safeHeight, safeHeight,
                    trackY, safeMaximum);
        }
        int minimum = Math.min(MINIMUM_THUMB_HEIGHT, safeHeight);
        int proportional = (int) Math.round(safeHeight * (double) safeHeight
                / (safeHeight + (double) safeMaximum));
        int thumbHeight = Math.clamp(proportional, minimum, safeHeight);
        int travel = safeHeight - thumbHeight;
        int clampedScroll = Math.clamp(scroll, 0, safeMaximum);
        int thumbY = trackY + (int) Math.round(travel * (double) clampedScroll / safeMaximum);
        return new Geometry(trackX, trackY, WIDTH, safeHeight, thumbHeight, thumbY, safeMaximum);
    }

    record Interaction(boolean consumed, int scroll) {
        static Interaction consumed(int scroll) {
            return new Interaction(true, scroll);
        }

        static Interaction notConsumed(int scroll) {
            return new Interaction(false, scroll);
        }
    }

    record Geometry(int trackX, int trackY, int trackWidth, int trackHeight,
                    int thumbHeight, int thumbY, int maximumScroll) {
        static Geometry hidden() {
            return new Geometry(0, 0, WIDTH, 0, 0, 0, 0);
        }

        boolean visible() {
            return trackHeight > 0 && maximumScroll > 0 && thumbHeight > 0;
        }

        int trackRight() {
            return trackX + trackWidth;
        }

        int trackBottom() {
            return trackY + trackHeight;
        }

        int thumbBottom() {
            return thumbY + thumbHeight;
        }

        int travel() {
            return Math.max(0, trackHeight - thumbHeight);
        }

        boolean containsTrack(double mouseX, double mouseY) {
            return visible() && mouseX >= trackX && mouseX < trackRight()
                    && mouseY >= trackY && mouseY < trackBottom();
        }

        boolean containsThumb(double mouseX, double mouseY) {
            return containsTrack(mouseX, mouseY) && mouseY >= thumbY && mouseY < thumbBottom();
        }

        int clampScroll(int scroll) {
            return Math.clamp(scroll, 0, maximumScroll);
        }

        int scrollForThumbTop(double requestedThumbTop) {
            if (!visible() || travel() == 0) return 0;
            double clampedTop = Math.clamp(requestedThumbTop, trackY, trackY + travel());
            return clampScroll((int) Math.round((clampedTop - trackY)
                    * maximumScroll / (double) travel()));
        }

        boolean sameLayout(Geometry other) {
            return trackX == other.trackX && trackY == other.trackY
                    && trackWidth == other.trackWidth && trackHeight == other.trackHeight
                    && thumbHeight == other.thumbHeight && maximumScroll == other.maximumScroll;
        }
    }
}

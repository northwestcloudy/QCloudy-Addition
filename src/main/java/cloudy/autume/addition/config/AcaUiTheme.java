package cloudy.autume.addition.config;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class AcaUiTheme {
    public static final int SCRIM = 0x92070B0E;
    public static final int WINDOW = 0xF21A1F23;
    public static final int HEADER = 0xFA20262A;
    public static final int SIDEBAR = 0xF522292D;
    public static final int CONTENT = 0xF51D2327;
    public static final int CARD = 0xFF272E32;
    public static final int CARD_HOVER = 0xFF30383D;
    public static final int CONTROL = 0xFF151A1D;
    public static final int BORDER = 0xFF3B454A;
    public static final int BORDER_SOFT = 0xFF30383D;
    public static final int ACCENT = 0xFF28BCEB;
    public static final int ACCENT_DARK = 0xFF147A99;
    public static final int TEXT = 0xFFF3F7F8;
    public static final int TEXT_MUTED = 0xFFA5B0B5;
    public static final int TEXT_DIM = 0xFF748087;
    public static final int SUCCESS = 0xFF42D38A;
    public static final int DANGER = 0xFFE45158;

    private AcaUiTheme() {
    }

    public static void surface(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + height, color);
        graphics.outline(x, y, width, height, BORDER);
    }

    public static void button(GuiGraphicsExtractor graphics, Font font, String label, int x, int y, int width, int height,
                       boolean hovered, boolean selected) {
        int fill = selected ? ACCENT : hovered ? CARD_HOVER : CONTROL;
        int textColor = selected ? 0xFF071014 : TEXT;
        graphics.fill(x, y, x + width, y + height, fill);
        graphics.outline(x, y, width, height, selected ? ACCENT : BORDER);
        int textX = x + (width - font.width(label)) / 2;
        int textY = y + (height - font.lineHeight) / 2;
        graphics.text(font, label, textX, textY, textColor, false);
    }

    public static void toggle(GuiGraphicsExtractor graphics, int x, int y, boolean enabled) {
        toggle(graphics, x, y, enabled, true);
    }

    public static void toggle(GuiGraphicsExtractor graphics, int x, int y, boolean enabled, boolean available) {
        int width = 30;
        int height = 14;
        int fill = available ? (enabled ? ACCENT_DARK : CONTROL) : (enabled ? 0xFF29434B : 0xFF1B2023);
        int outline = available ? (enabled ? ACCENT : BORDER) : (enabled ? 0xFF41636E : BORDER_SOFT);
        int knob = available ? (enabled ? 0xFFFFFFFF : 0xFF87949A) : TEXT_DIM;
        graphics.fill(x, y, x + width, y + height, fill);
        graphics.outline(x, y, width, height, outline);
        int knobX = enabled ? x + width - 11 : x + 3;
        graphics.fill(knobX, y + 3, knobX + 8, y + 11, knob);
    }

    public static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}

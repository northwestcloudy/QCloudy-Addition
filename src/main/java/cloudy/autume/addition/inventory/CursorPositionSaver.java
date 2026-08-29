package cloudy.autume.addition.inventory;

import cloudy.autume.addition.config.ConfigManager;
import cloudy.autume.addition.config.ModConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

/** Primitive-only cursor transition cache; no per-frame allocation or persistence is needed. */
public final class CursorPositionSaver {
    private static double originalX;
    private static double originalY;
    private static boolean hasOriginal;
    private static double savedCenterX;
    private static double savedCenterY;
    private static double savedCursorX;
    private static double savedCursorY;
    private static long savedAtNanos;
    private static boolean hasSaved;

    private CursorPositionSaver() {
    }

    public static void captureOriginal(double x, double y) {
        if (!enabled()) return;
        originalX = x;
        originalY = y;
        hasOriginal = true;
    }

    public static void captureCentered(double centerX, double centerY) {
        if (!enabled() || !hasOriginal) return;
        savedCenterX = centerX;
        savedCenterY = centerY;
        savedCursorX = originalX;
        savedCursorY = originalY;
        savedAtNanos = System.nanoTime();
        hasSaved = true;
        hasOriginal = false;
    }

    public static RestoredPosition restore(double centerX, double centerY) {
        if (!enabled() || !hasSaved) return null;
        hasSaved = false;
        long tolerance = ConfigManager.get().inventory.cursorToleranceMs * 1_000_000L;
        if (System.nanoTime() - savedAtNanos > tolerance) return null;
        if (Math.abs(savedCenterX - centerX) >= 1.0 || Math.abs(savedCenterY - centerY) >= 1.0) return null;
        Minecraft client = Minecraft.getInstance();
        InputConstants.grabOrReleaseMouse(client.getWindow(), InputConstants.CURSOR_NORMAL, savedCursorX, savedCursorY);
        return new RestoredPosition(savedCursorX, savedCursorY);
    }

    private static boolean enabled() {
        return enabled(ConfigManager.get().inventory);
    }

    static boolean enabled(ModConfig.Inventory config) {
        return config.saveCursorPosition;
    }

    public record RestoredPosition(double x, double y) {
    }
}

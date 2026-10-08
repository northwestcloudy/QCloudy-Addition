package cloudy.autume.addition.hud;

import cloudy.autume.addition.config.ConfigManager;
import cloudy.autume.addition.config.ModConfig;
import cloudy.autume.addition.i18n.ModText;
import cloudy.autume.addition.tracker.LocationTracker;
import cloudy.autume.addition.weather.IslandWeatherCatalog;
import cloudy.autume.addition.weather.IslandWeatherTracker;
import cloudy.autume.addition.weather.WeatherSnapshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Rendering-only Island Weather panel; positioning and enablement remain central. */
public final class WeatherHudRenderer {
    public static final int WIDTH = 320;
    private static final int PADDING = 7;
    private static final int LINE_HEIGHT = 11;

    private WeatherHudRenderer() {
    }

    public static boolean loaded() {
        WeatherSnapshot snapshot = IslandWeatherTracker.current().orElse(null);
        return snapshot != null && LocationTracker.area() == snapshot.island();
    }

    public static int currentHeight() {
        WeatherSnapshot snapshot = visibleSnapshot();
        return snapshot == null ? 0 : height(lines(snapshot,
                ConfigManager.get().weather.showBonuses).size());
    }

    /** The caller supplies the independently configured Weather panel style. */
    public static void render(GuiGraphicsExtractor graphics, ModConfig.PanelStyle style) {
        WeatherSnapshot snapshot = visibleSnapshot();
        if (snapshot == null || graphics == null || style == null) return;
        List<Line> lines = lines(snapshot, ConfigManager.get().weather.showBonuses);
        HudPanel.background(graphics, 0, 0, WIDTH, height(lines.size()), style);
        int y = 5;
        for (int index = 0; index < lines.size(); index++) {
            Line line = lines.get(index);
            if (index == 0) HudPanel.title(graphics, line.text(), PADDING, y, style);
            else HudPanel.text(graphics, line.text(), PADDING + line.indent(), y,
                    line.color(), style);
            y += LINE_HEIGHT;
        }
    }

    static List<Line> lines(WeatherSnapshot snapshot) {
        return lines(snapshot, true);
    }

    static List<Line> lines(WeatherSnapshot snapshot, boolean showBonuses) {
        IslandWeatherCatalog.IslandWeather island = IslandWeatherCatalog.instance()
                .island(snapshot.island()).orElseThrow();
        List<Line> result = new ArrayList<>();
        result.add(new Line(ModText.get("hud.weather"), 0xFF7FDBFF, 0));
        int weatherColor = snapshot.weather().severity() == IslandWeatherCatalog.Severity.EXTREME
                ? 0xFFFF6868 : 0xFF74D9FF;
        result.add(new Line(island.name() + " · " + snapshot.weather().name() + " · "
                + ModText.get(snapshot.weather().severity() == IslandWeatherCatalog.Severity.EXTREME
                        ? "hud.weather.extreme" : "hud.weather.mild"), weatherColor, 0));
        if (snapshot.hasExplicitRemainingTime()) {
            result.add(new Line(ModText.get("hud.weather.time_left") + ": "
                    + duration(snapshot.explicitRemainingSeconds()),
                    0xFFFFD45A, 3));
        }
        if (showBonuses) {
            for (String effect : snapshot.weather().effects()) {
                result.add(new Line("• " + effect, 0xFFD8E4EB, 3));
            }
        }
        return List.copyOf(result);
    }

    static String duration(long seconds) {
        long safe = Math.max(0L, seconds);
        if (safe >= 3_600L) {
            return String.format(Locale.ROOT, "%d:%02d:%02d",
                    safe / 3_600L, safe / 60L % 60L, safe % 60L);
        }
        return String.format(Locale.ROOT, "%d:%02d", safe / 60L, safe % 60L);
    }

    private static WeatherSnapshot visibleSnapshot() {
        if (!loaded()) return null;
        return IslandWeatherTracker.current().orElse(null);
    }

    private static int height(int lineCount) {
        return Math.max(28, 10 + lineCount * LINE_HEIGHT);
    }

    record Line(String text, int color, int indent) {
    }
}

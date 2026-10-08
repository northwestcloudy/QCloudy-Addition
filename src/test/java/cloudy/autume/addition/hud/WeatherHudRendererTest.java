package cloudy.autume.addition.hud;

import cloudy.autume.addition.tracker.IslandArea;
import cloudy.autume.addition.weather.IslandWeatherCatalog;
import cloudy.autume.addition.weather.WeatherSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class WeatherHudRendererTest {
    @Test
    void formatsOnlyAnExplicitTimerAndCanHideTheEffectList() {
        var weather = IslandWeatherCatalog.instance().find(IslandArea.DWARVEN_MINES, "Breeze")
                .orElseThrow();
        var snapshot = new WeatherSnapshot(IslandArea.DWARVEN_MINES, weather, 3_661,
                WeatherSnapshot.EvidenceSource.MENU, "Weather: Breeze; Ends in 1h 1m 1s");

        assertEquals("1:01:01", WeatherHudRenderer.duration(3_661));
        assertEquals(3, WeatherHudRenderer.lines(snapshot, false).size());
        assertEquals(6, WeatherHudRenderer.lines(snapshot, true).size());
    }

    @Test
    void unknownRemainingTimeDoesNotCreateAClockLine() {
        var weather = IslandWeatherCatalog.instance().find(IslandArea.CRYSTAL_HOLLOWS, "Rockfall")
                .orElseThrow();
        var snapshot = new WeatherSnapshot(IslandArea.CRYSTAL_HOLLOWS, weather, -1,
                WeatherSnapshot.EvidenceSource.CHAT, "Weather changed to Rockfall");

        assertEquals(2, WeatherHudRenderer.lines(snapshot, false).size());
    }
}

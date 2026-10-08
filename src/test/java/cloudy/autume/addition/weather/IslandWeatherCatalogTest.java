package cloudy.autume.addition.weather;

import cloudy.autume.addition.tracker.IslandArea;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class IslandWeatherCatalogTest {
    @AfterEach
    void resetTracker() {
        IslandWeatherTracker.reset();
    }

    @Test
    void pinsTheCompleteOfficial0272IslandTableAndExcludesMineshafts() {
        IslandWeatherCatalog catalog = IslandWeatherCatalog.instance();
        assertEquals(1, catalog.schemaVersion());
        assertEquals("hypixel-skyblock-0.27.2", catalog.contentVersion());
        assertEquals(11, catalog.islands().size());
        assertEquals(22, catalog.islands().stream().flatMap(entry -> entry.weather().stream()).count());
        assertFalse(IslandArea.MINESHAFT.isWeatherIsland());
        assertTrue(catalog.island(IslandArea.MINESHAFT).isEmpty());
    }

    @Test
    void parsesOnlyExplicitCurrentIslandEvidenceAndAnExplicitTimer() {
        var parsed = IslandWeatherParser.parse(IslandArea.DWARVEN_MINES,
                WeatherSnapshot.EvidenceSource.TAB,
                List.of("Island Weather: Breeze - Ends in 12:34"));

        assertTrue(parsed.active());
        assertEquals("dwarven_mines.breeze", parsed.snapshot().weather().id());
        assertEquals(754L, parsed.snapshot().explicitRemainingSeconds());
        assertEquals(WeatherSnapshot.EvidenceSource.TAB, parsed.snapshot().evidenceSource());
    }

    @Test
    void rejectsCrossIslandNamesAndNeverInfersTheDocumentedCadence() {
        var crossIsland = IslandWeatherParser.parse(IslandArea.DWARVEN_MINES,
                WeatherSnapshot.EvidenceSource.SCOREBOARD, List.of("Weather: Ashfall"));
        var noEvidence = IslandWeatherParser.parse(IslandArea.DWARVEN_MINES,
                WeatherSnapshot.EvidenceSource.SCOREBOARD, List.of("Next weather in 20 minutes"));
        var mineshaft = IslandWeatherParser.parse(IslandArea.MINESHAFT,
                WeatherSnapshot.EvidenceSource.CHAT, List.of("Weather: Breeze"));

        assertFalse(crossIsland.recognized());
        assertFalse(noEvidence.recognized());
        assertFalse(mineshaft.recognized());
    }

    @Test
    void explicitClearAndIslandChangesInvalidateTheSnapshotImmediately() {
        assertTrue(IslandWeatherTracker.observe(IslandArea.THE_END,
                WeatherSnapshot.EvidenceSource.CHAT, List.of("Weather is Wispful.")));
        assertTrue(IslandWeatherTracker.current().isPresent());

        IslandWeatherTracker.onIslandChanged(IslandArea.CRIMSON_ISLE);
        assertTrue(IslandWeatherTracker.current().isEmpty());
        assertTrue(IslandWeatherTracker.observe(IslandArea.CRIMSON_ISLE,
                WeatherSnapshot.EvidenceSource.ACTION_BAR, List.of("Weather: Ashfall")));
        assertTrue(IslandWeatherTracker.observe(IslandArea.CRIMSON_ISLE,
                WeatherSnapshot.EvidenceSource.ACTION_BAR, List.of("Weather: Clear")));
        assertTrue(IslandWeatherTracker.current().isEmpty());
    }
}

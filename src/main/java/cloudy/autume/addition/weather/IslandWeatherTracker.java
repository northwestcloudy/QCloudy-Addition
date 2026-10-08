package cloudy.autume.addition.weather;

import cloudy.autume.addition.tracker.IslandArea;

import java.util.Optional;

/** Session-only current-island weather state. */
public final class IslandWeatherTracker {
    private static IslandArea currentIsland = IslandArea.NONE;
    private static WeatherSnapshot current;

    private IslandWeatherTracker() {
    }

    /**
     * Applies one explicit received-text observation. Unrecognized or
     * cross-island text cannot overwrite a previously proven snapshot.
     */
    public static boolean observe(IslandArea island,
                                  WeatherSnapshot.EvidenceSource source,
                                  Iterable<String> receivedTexts) {
        onIslandChanged(island);
        IslandWeatherParser.ParseResult parsed =
                IslandWeatherParser.parse(currentIsland, source, receivedTexts);
        if (!parsed.recognized()) return false;
        current = parsed.snapshot();
        return true;
    }

    public static Optional<WeatherSnapshot> current() {
        if (current == null || current.island() != currentIsland) return Optional.empty();
        return Optional.of(current);
    }

    /** Immediately rejects state retained from a different world or island. */
    public static void onIslandChanged(IslandArea island) {
        IslandArea next = island == null ? IslandArea.NONE : island;
        if (next != currentIsland) current = null;
        currentIsland = next;
        if (!currentIsland.isWeatherIsland()) current = null;
    }

    public static void clear() {
        current = null;
    }

    public static void reset() {
        currentIsland = IslandArea.NONE;
        current = null;
    }
}

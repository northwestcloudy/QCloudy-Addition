package cloudy.autume.addition.weather;

import cloudy.autume.addition.tracker.IslandArea;

import java.util.Objects;

/** Immutable state backed only by text that the vanilla client already received. */
public record WeatherSnapshot(
        IslandArea island,
        IslandWeatherCatalog.Weather weather,
        long explicitRemainingSeconds,
        EvidenceSource evidenceSource,
        String receivedText) {

    public WeatherSnapshot {
        if (island == null || !island.isWeatherIsland()) {
            throw new IllegalArgumentException("A supported weather island is required");
        }
        Objects.requireNonNull(weather, "Weather is required");
        Objects.requireNonNull(evidenceSource, "Evidence source is required");
        receivedText = Objects.requireNonNullElse(receivedText, "");
        if (explicitRemainingSeconds < -1L) {
            throw new IllegalArgumentException("Remaining seconds must be explicit or unknown");
        }
        IslandWeatherCatalog.Weather expected = IslandWeatherCatalog.instance()
                .find(island, weather.name()).orElse(null);
        if (expected == null || !expected.id().equals(weather.id())) {
            throw new IllegalArgumentException("Weather does not belong to island " + island);
        }
    }

    public boolean hasExplicitRemainingTime() {
        return explicitRemainingSeconds >= 0L;
    }

    public enum EvidenceSource {
        TAB,
        SCOREBOARD,
        CHAT,
        ACTION_BAR,
        MENU
    }
}

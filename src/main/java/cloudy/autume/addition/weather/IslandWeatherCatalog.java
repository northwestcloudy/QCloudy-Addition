package cloudy.autume.addition.weather;

import cloudy.autume.addition.tracker.IslandArea;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Versioned offline copy of Hypixel's 0.27.2 Island Weather table. */
public final class IslandWeatherCatalog {
    private static final String RESOURCE =
            "/assets/qcloudy_addition/data/island_weather_v1.json";
    private static final int EXPECTED_SCHEMA_VERSION = 1;
    private static final int EXPECTED_ISLANDS = 11;
    private static final IslandWeatherCatalog INSTANCE = load();

    private final int schemaVersion;
    private final String contentVersion;
    private final String source;
    private final List<IslandWeather> islands;
    private final Map<IslandArea, IslandWeather> byIsland;

    private IslandWeatherCatalog(int schemaVersion, String contentVersion, String source,
                                 List<IslandWeather> islands) {
        this.schemaVersion = schemaVersion;
        this.contentVersion = contentVersion;
        this.source = source;
        this.islands = List.copyOf(islands);
        EnumMap<IslandArea, IslandWeather> indexed = new EnumMap<>(IslandArea.class);
        for (IslandWeather island : islands) {
            if (indexed.put(island.island(), island) != null) {
                throw new IllegalStateException("Duplicate weather island " + island.island());
            }
        }
        this.byIsland = Map.copyOf(indexed);
    }

    public static IslandWeatherCatalog instance() {
        return INSTANCE;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public String contentVersion() {
        return contentVersion;
    }

    public String source() {
        return source;
    }

    public List<IslandWeather> islands() {
        return islands;
    }

    public Optional<IslandWeather> island(IslandArea area) {
        return Optional.ofNullable(byIsland.get(area));
    }

    /** Resolves a weather name only inside its current island. */
    public Optional<Weather> find(IslandArea area, String receivedName) {
        IslandWeather island = byIsland.get(area);
        if (island == null) return Optional.empty();
        String expected = normalize(receivedName);
        if (normalize(island.mild().name()).equals(expected)) return Optional.of(island.mild());
        if (normalize(island.extreme().name()).equals(expected)) return Optional.of(island.extreme());
        return Optional.empty();
    }

    /** True when the name belongs to some other official weather island. */
    boolean knownOnAnotherIsland(IslandArea currentIsland, String receivedName) {
        String expected = normalize(receivedName);
        if (expected.isBlank()) return false;
        return islands.stream()
                .filter(island -> island.island() != currentIsland)
                .flatMap(island -> java.util.stream.Stream.of(island.mild(), island.extreme()))
                .anyMatch(weather -> normalize(weather.name()).equals(expected));
    }

    private static IslandWeatherCatalog load() {
        try (var stream = IslandWeatherCatalog.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing " + RESOURCE);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                int schemaVersion = root.get("schemaVersion").getAsInt();
                String contentVersion = root.get("contentVersion").getAsString();
                String source = root.get("source").getAsString();
                List<IslandWeather> islands = new ArrayList<>();
                for (var element : root.getAsJsonArray("islands")) {
                    JsonObject value = element.getAsJsonObject();
                    IslandArea island = IslandArea.valueOf(value.get("island").getAsString());
                    islands.add(new IslandWeather(island, value.get("name").getAsString(),
                            weather(value.getAsJsonObject("mild"), Severity.MILD),
                            weather(value.getAsJsonObject("extreme"), Severity.EXTREME)));
                }
                IslandWeatherCatalog catalog = new IslandWeatherCatalog(
                        schemaVersion, contentVersion, source, islands);
                catalog.validate();
                return catalog;
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load Island Weather catalog", exception);
        }
    }

    private static Weather weather(JsonObject value, Severity severity) {
        List<String> effects = new ArrayList<>();
        value.getAsJsonArray("effects").forEach(effect -> effects.add(effect.getAsString()));
        return new Weather(value.get("id").getAsString(), value.get("name").getAsString(),
                severity, effects);
    }

    private void validate() {
        if (schemaVersion != EXPECTED_SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported Island Weather schema " + schemaVersion);
        }
        if (!"hypixel-skyblock-0.27.2".equals(contentVersion)) {
            throw new IllegalStateException("Unexpected Island Weather content version " + contentVersion);
        }
        if (islands.size() != EXPECTED_ISLANDS) {
            throw new IllegalStateException("Island Weather catalog must contain 11 islands");
        }
        Set<String> ids = new LinkedHashSet<>();
        for (IslandWeather island : islands) {
            if (!island.island().isWeatherIsland() || island.island() == IslandArea.MINESHAFT) {
                throw new IllegalStateException("Invalid weather island " + island.island());
            }
            if (island.name().isBlank() || island.mild().severity() != Severity.MILD
                    || island.extreme().severity() != Severity.EXTREME
                    || island.mild().effects().isEmpty() || island.extreme().effects().isEmpty()
                    || !ids.add(island.mild().id()) || !ids.add(island.extreme().id())) {
                throw new IllegalStateException("Incomplete or duplicate weather entry for "
                        + island.island());
            }
        }
        if (ids.size() != EXPECTED_ISLANDS * 2) {
            throw new IllegalStateException("Island Weather catalog must contain 22 unique weather ids");
        }
        Set<IslandArea> enumWeatherIslands = new LinkedHashSet<>();
        for (IslandArea area : IslandArea.values()) {
            if (area.isWeatherIsland()) enumWeatherIslands.add(area);
        }
        if (!enumWeatherIslands.equals(byIsland.keySet())) {
            throw new IllegalStateException("IslandArea weather set does not match the catalog");
        }
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
                .replace('’', '\'')
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    public enum Severity {
        MILD,
        EXTREME
    }

    public record IslandWeather(IslandArea island, String name, Weather mild, Weather extreme) {
        public IslandWeather {
            if (island == null || name == null || mild == null || extreme == null) {
                throw new IllegalArgumentException("Island Weather entry is incomplete");
            }
        }

        public List<Weather> weather() {
            return List.of(mild, extreme);
        }
    }

    public record Weather(String id, String name, Severity severity, List<String> effects) {
        public Weather {
            if (id == null || id.isBlank() || name == null || name.isBlank() || severity == null) {
                throw new IllegalArgumentException("Weather entry is incomplete");
            }
            effects = effects == null ? List.of() : List.copyOf(effects);
        }
    }
}

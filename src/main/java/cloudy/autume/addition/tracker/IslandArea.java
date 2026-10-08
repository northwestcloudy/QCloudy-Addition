package cloudy.autume.addition.tracker;

public enum IslandArea {
    NONE,
    DWARVEN_MINES,
    CRYSTAL_HOLLOWS,
    GLACITE_TUNNELS,
    MINESHAFT,
    TORRHUS_CANYON,
    GALATEA,
    CRITTER_SAFARI,
    CRIMSON_ISLE,
    THE_END,
    SPIDERS_DEN,
    MOONGLADE_MARSH,
    BACKWATER_BAYOU,
    LOTUS_ATOLL,
    GARDEN,
    JERRYS_WORKSHOP;

    public boolean isMiningIsland() {
        return this == DWARVEN_MINES || this == CRYSTAL_HOLLOWS || this == GLACITE_TUNNELS || this == MINESHAFT;
    }

    /** Islands listed in Hypixel's 0.27.2 Island Weather table. */
    public boolean isWeatherIsland() {
        return switch (this) {
            case CRIMSON_ISLE, SPIDERS_DEN, MOONGLADE_MARSH,
                    DWARVEN_MINES, CRYSTAL_HOLLOWS, GLACITE_TUNNELS,
                    THE_END, BACKWATER_BAYOU, LOTUS_ATOLL, GARDEN,
                    JERRYS_WORKSHOP -> true;
            // A Glacite Mineshaft is deliberately not a Glacite Tunnels
            // weather island. Weather evidence may never cross this boundary.
            case NONE, MINESHAFT, TORRHUS_CANYON, GALATEA, CRITTER_SAFARI -> false;
        };
    }
}

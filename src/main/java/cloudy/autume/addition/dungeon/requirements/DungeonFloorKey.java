package cloudy.autume.addition.dungeon.requirements;

import java.util.Locale;
import java.util.Optional;

/** A Catacombs floor that can own an independent QCA admission policy. */
public enum DungeonFloorKey {
    F1, F2, F3, F4, F5, F6, F7,
    M1, M2, M3, M4, M5, M6, M7;

    /**
     * Parses only the fourteen configurable floors. Entrance and unknown
     * values intentionally return an empty result instead of falling back.
     */
    public static Optional<DungeonFloorKey> parse(String value) {
        if (value == null) return Optional.empty();
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[FM][1-7]")) return Optional.empty();
        return Optional.of(valueOf(normalized));
    }
}

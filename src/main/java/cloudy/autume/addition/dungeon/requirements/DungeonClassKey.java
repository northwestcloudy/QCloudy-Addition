package cloudy.autume.addition.dungeon.requirements;

import java.util.Locale;
import java.util.Optional;

/** Canonical Dungeon class shared by join parsing, party evidence and evaluation. */
public enum DungeonClassKey {
    ARCHER("Archer"),
    BERSERK("Berserk"),
    HEALER("Healer"),
    MAGE("Mage"),
    TANK("Tank");

    private final String displayName;

    DungeonClassKey(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<DungeonClassKey> parse(String value) {
        if (value == null) return Optional.empty();
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}

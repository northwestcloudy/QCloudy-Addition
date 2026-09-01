package cloudy.autume.addition.profile;

import java.util.Locale;
import java.util.Optional;

/** Supported non-party profile sections. Dungeon models are intentionally absent. */
public enum ProfileSectionId {
    OVERVIEW,
    GEAR,
    ACCESSORIES,
    PETS,
    INVENTORY,
    SKILLS,
    SLAYER,
    MINIONS,
    BESTIARY,
    COLLECTIONS,
    MINING,
    CRIMSON_ISLE,
    RIFT,
    MISC,
    MUSEUM,
    GARDEN,
    MARKET;

    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<ProfileSectionId> fromWireName(String value) {
        if (value == null) return Optional.empty();
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}

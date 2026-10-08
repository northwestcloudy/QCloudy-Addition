package cloudy.autume.addition.tracker;

import java.util.Locale;

/**
 * The tier received for one concrete pet.
 *
 * <p>SPECIAL and VERY_SPECIAL deliberately have no inferred leveling curve.
 * Hypixel reused the red display color for both tiers, and the 0.27.2 Phoenix
 * migration did not publish enough evidence to safely map either tier onto an
 * older XP curve.</p>
 */
public enum PetTier {
    COMMON(0),
    UNCOMMON(6),
    RARE(11),
    EPIC(16),
    LEGENDARY(20),
    MYTHIC(20),
    DIVINE(-1),
    SPECIAL(-1),
    VERY_SPECIAL(-1),
    UNKNOWN(-1);

    private final int levelingOffset;

    PetTier(int levelingOffset) {
        this.levelingOffset = levelingOffset;
    }

    public boolean hasKnownLevelingCurve() {
        return levelingOffset >= 0;
    }

    int levelingOffset() {
        return levelingOffset;
    }

    /** Stable value stored in config memory. */
    public String wireValue() {
        return name();
    }

    public static PetTier fromWire(String raw) {
        if (raw == null || raw.isBlank()) return UNKNOWN;
        String normalized = raw.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return UNKNOWN;
        }
    }

    /**
     * Uses a color only when it was actually received on the pet name. A red
     * name remains UNKNOWN because it cannot distinguish SPECIAL from
     * VERY_SPECIAL.
     */
    static PetTier fromObservedColor(int color) {
        return switch (color & 0xFFFFFF) {
            case 0xFFFFFF -> COMMON;
            case 0x55FF55 -> UNCOMMON;
            case 0x5555FF -> RARE;
            case 0xAA00AA -> EPIC;
            case 0xFFAA00 -> LEGENDARY;
            case 0xFF55FF -> MYTHIC;
            default -> UNKNOWN;
        };
    }
}

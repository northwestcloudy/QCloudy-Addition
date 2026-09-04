package cloudy.autume.addition.dungeon;

/** Safe failure from the dedicated Dungeon quick-view endpoint. */
public final class DungeonQuickViewException extends RuntimeException {
    public DungeonQuickViewException(String message) {
        this(message, null);
    }

    public DungeonQuickViewException(String message, Throwable cause) {
        super(safe(message), cause);
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) return "Dungeon profile data is unavailable.";
        String normalized = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return normalized.length() <= 192 ? normalized : normalized.substring(0, 192);
    }
}

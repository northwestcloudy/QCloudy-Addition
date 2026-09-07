package cloudy.autume.addition.dungeon;

/** Safe failure from the dedicated Dungeon quick-view endpoint. */
public final class DungeonQuickViewException extends RuntimeException {
    enum Scope {
        PLAYER,
        SERVICE
    }

    private final Scope scope;

    public DungeonQuickViewException(String message) {
        this(message, null, Scope.SERVICE);
    }

    public DungeonQuickViewException(String message, Throwable cause) {
        this(message, cause, Scope.SERVICE);
    }

    DungeonQuickViewException(String message, Throwable cause, Scope scope) {
        super(safe(message), cause);
        this.scope = scope == null ? Scope.SERVICE : scope;
    }

    boolean isServiceFailure() {
        return scope == Scope.SERVICE;
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) return "Dungeon profile data is unavailable.";
        String normalized = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return normalized.length() <= 192 ? normalized : normalized.substring(0, 192);
    }
}

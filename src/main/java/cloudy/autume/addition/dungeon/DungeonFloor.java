package cloudy.autume.addition.dungeon;

import java.util.Locale;
import java.util.Optional;

/** The floor advertised by the local player's active Dungeon Finder queue. */
public record DungeonFloor(String id) {
    private static final java.util.regex.Pattern ACTIVE_FLOOR = java.util.regex.Pattern.compile(
            "(?i)^The Catacombs\\s*\\([FM][1-7]\\)$");

    public DungeonFloor {
        id = id == null ? "" : id.toUpperCase(Locale.ROOT);
        if (!id.matches("(?:E|[FM][1-7])")) throw new IllegalArgumentException("Invalid Dungeon floor");
    }

    public static Optional<DungeonFloor> fromScoreboard(Iterable<String> lines) {
        return observe(lines).floor();
    }

    /**
     * Keeps a valid floor across a transient partial scoreboard update, but
     * clears it as soon as the Catacombs queue itself disappears or changes
     * between normal and Master Mode.
     */
    static Optional<DungeonFloor> retainWhileQueued(DungeonFloor previous, Iterable<String> lines) {
        Observation observation = observe(lines);
        if (!observation.queued()) return Optional.empty();
        if (observation.floor().isPresent()) return observation.floor();
        if (previous == null || previous.id().startsWith("M") != observation.master()) {
            return Optional.empty();
        }
        return Optional.of(previous);
    }

    static Observation observe(Iterable<String> lines) {
        boolean queued = false;
        boolean master = false;
        boolean activeDungeon = false;
        boolean meaningful = false;
        String tier = null;
        if (lines == null) {
            return new Observation(false, false, false, false, Optional.empty());
        }
        for (String raw : lines) {
            String line = clean(raw);
            if (!line.isEmpty()) meaningful = true;
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.startsWith("queued:") && lower.contains("catacombs")) {
                queued = true;
                master = lower.contains("master") || lower.contains(" mm");
            }
            if (ACTIVE_FLOOR.matcher(line).matches()
                    || lower.startsWith("cleared:")
                    || lower.startsWith("dungeon cleared:")) {
                activeDungeon = true;
            }
            if (lower.startsWith("tier:")) tier = line.substring(line.indexOf(':') + 1).trim();
        }
        if (!queued || tier == null) {
            return new Observation(queued, master, activeDungeon, meaningful, Optional.empty());
        }
        String normalized = tier.toUpperCase(Locale.ROOT);
        if (normalized.contains("ENTRANCE")) {
            return new Observation(true, master, activeDungeon, meaningful,
                    Optional.of(new DungeonFloor("E")));
        }
        java.util.regex.Matcher number = java.util.regex.Pattern
                .compile("(?:FLOOR\\s*)?([IVX]+|[1-7])$").matcher(normalized);
        if (!number.find()) {
            return new Observation(true, master, activeDungeon, meaningful, Optional.empty());
        }
        int value = roman(number.group(1));
        if (value < 1 || value > 7) {
            return new Observation(true, master, activeDungeon, meaningful, Optional.empty());
        }
        return new Observation(true, master, activeDungeon, meaningful,
                Optional.of(new DungeonFloor((master ? "M" : "F") + value)));
    }

    private static int roman(String value) {
        return switch (value) {
            case "I", "1" -> 1;
            case "II", "2" -> 2;
            case "III", "3" -> 3;
            case "IV", "4" -> 4;
            case "V", "5" -> 5;
            case "VI", "6" -> 6;
            case "VII", "7" -> 7;
            default -> -1;
        };
    }

    private static String clean(String raw) {
        String stripped = net.minecraft.ChatFormatting.stripFormatting(raw == null ? "" : raw);
        return stripped == null ? "" : stripped.trim();
    }

    record Observation(boolean queued, boolean master, boolean activeDungeon,
                       boolean meaningful, Optional<DungeonFloor> floor) { }
}

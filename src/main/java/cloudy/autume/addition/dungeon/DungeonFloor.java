package cloudy.autume.addition.dungeon;

import java.util.Locale;
import java.util.Optional;

/** The floor advertised by the local player's active Dungeon Finder queue. */
public record DungeonFloor(String id) {
    public DungeonFloor {
        id = id == null ? "" : id.toUpperCase(Locale.ROOT);
        if (!id.matches("(?:E|[FM][1-7])")) throw new IllegalArgumentException("Invalid Dungeon floor");
    }

    public static Optional<DungeonFloor> fromScoreboard(Iterable<String> lines) {
        boolean queued = false;
        boolean master = false;
        String tier = null;
        for (String raw : lines) {
            String line = clean(raw);
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.startsWith("queued:") && lower.contains("catacombs")) {
                queued = true;
                master = lower.contains("master") || lower.contains(" mm");
            }
            if (lower.startsWith("tier:")) tier = line.substring(line.indexOf(':') + 1).trim();
        }
        if (!queued || tier == null) return Optional.empty();
        String normalized = tier.toUpperCase(Locale.ROOT);
        if (normalized.contains("ENTRANCE")) return Optional.of(new DungeonFloor("E"));
        java.util.regex.Matcher number = java.util.regex.Pattern
                .compile("(?:FLOOR\\s*)?([IVX]+|[1-7])$").matcher(normalized);
        if (!number.find()) return Optional.empty();
        int value = roman(number.group(1));
        if (value < 1 || value > 7) return Optional.empty();
        return Optional.of(new DungeonFloor((master ? "M" : "F") + value));
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
}

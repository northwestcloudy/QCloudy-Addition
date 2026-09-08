package cloudy.autume.addition.dungeon;

import cloudy.autume.addition.dungeon.requirements.DungeonClassKey;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Exact parser for a new member joining this player's Dungeon Finder group. */
public final class DungeonJoinParser {
    private static final Pattern JOIN = Pattern.compile(
            "^Party Finder\\s*>\\s*(?:\\[[^]]+]\\s*)?([A-Za-z0-9_]{3,16})\\s+"
                    + "joined the dungeon group!\\s*\\(([^()]+?)\\s+Level\\s+(\\d+)\\)\\s*$");
    private static final List<Pattern> DEPARTURES = List.of(
            Pattern.compile("^(?:\\[[^]\\r\\n]+]\\s*)?([A-Za-z0-9_]{3,16}) "
                    + "(?:has left the party|has been removed from the party|"
                    + "was removed from your party because they disconnected)\\.$"),
            Pattern.compile("^Kicked (?:\\[[^]\\r\\n]+]\\s*)?([A-Za-z0-9_]{3,16}) "
                    + "because they were offline\\.$")
    );

    private DungeonJoinParser() { }

    public static Optional<String> newcomer(String raw) {
        return event(raw).map(DungeonJoinEvent::playerName);
    }

    /** Returns all admission evidence carried by the exact Party Finder line. */
    public static Optional<DungeonJoinEvent> event(String raw) {
        Matcher matcher = JOIN.matcher(clean(raw));
        if (!matcher.matches()) return Optional.empty();
        Integer level;
        try {
            level = Integer.valueOf(matcher.group(3));
        } catch (NumberFormatException ignored) {
            level = null;
        }
        return Optional.of(new DungeonJoinEvent(
                matcher.group(1), DungeonClassKey.parse(matcher.group(2)).orElse(null), level));
    }

    /**
     * Returns the departed player's exact username for known Hypixel party
     * lifecycle lines. Full-line matching deliberately rejects party chat and
     * lookalike prose so ordinary player messages cannot reset admission state.
     */
    public static Optional<String> departure(String raw) {
        String text = clean(raw);
        for (Pattern departure : DEPARTURES) {
            Matcher matcher = departure.matcher(text);
            if (matcher.matches()) return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }

    private static String clean(String raw) {
        String stripped = net.minecraft.ChatFormatting.stripFormatting(raw == null ? "" : raw);
        return stripped == null ? "" : stripped.trim();
    }

    public record DungeonJoinEvent(String playerName, DungeonClassKey dungeonClass,
                                   Integer classLevel) {
        public DungeonJoinEvent {
            if (playerName == null || !playerName.matches("[A-Za-z0-9_]{3,16}")) {
                throw new IllegalArgumentException("Invalid Dungeon newcomer name");
            }
            if (classLevel != null && classLevel < 0) classLevel = null;
        }
    }
}

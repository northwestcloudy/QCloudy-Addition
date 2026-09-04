package cloudy.autume.addition.dungeon;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Exact parser for a new member joining this player's Dungeon Finder group. */
public final class DungeonJoinParser {
    private static final Pattern JOIN = Pattern.compile(
            "^Party Finder\\s*>\\s*(?:\\[[^]]+]\\s*)?([A-Za-z0-9_]{3,16})\\s+"
                    + "joined the dungeon group!\\s*\\([^)]*\\bLevel\\s+\\d+\\)\\s*$");

    private DungeonJoinParser() { }

    public static Optional<String> newcomer(String raw) {
        String stripped = net.minecraft.ChatFormatting.stripFormatting(raw == null ? "" : raw);
        Matcher matcher = JOIN.matcher(stripped == null ? "" : stripped.trim());
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }
}

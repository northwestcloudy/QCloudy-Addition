package cloudy.autume.addition.party;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A strictly parsed English Hypixel party-chat echo. */
public record PartyChatLine(String sender, String message) {
    private static final Pattern PARTY_CHAT = Pattern.compile(
            "^Party > (?:\\[[^]\\r\\n]+]\\s+)?([A-Za-z0-9_]{1,16})"
                    + "(?:\\s+[^:\\r\\n]{1,32})?:\\s*(.*)$");

    public PartyChatLine {
        if (!PartyRosterTracker.validUsername(sender)) {
            throw new IllegalArgumentException("Invalid party-chat sender");
        }
        message = message == null ? "" : message.trim();
    }

    /**
     * Parses only the server's English {@code Party > ...: ...} shape. Public,
     * guild, direct-message and system text deliberately fail closed.
     */
    public static Optional<PartyChatLine> parse(String raw) {
        if (raw == null) return Optional.empty();
        String clean = PartyText.clean(raw);
        if (clean.indexOf('\n') >= 0) return Optional.empty();
        Matcher matcher = PARTY_CHAT.matcher(clean);
        if (!matcher.matches()) return Optional.empty();
        return Optional.of(new PartyChatLine(matcher.group(1), matcher.group(2)));
    }
}

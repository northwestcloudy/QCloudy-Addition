package cloudy.autume.addition.party;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure parsers for direct-message party requests and local quick-DM syntax. */
public final class PrivatePartyRequestCommands {
    public static final long DM_DEDUPE_NANOS = 2_000_000_000L;
    private static final Pattern INCOMING_DIRECT_MESSAGE = Pattern.compile(
            "^From (?:\\[[^]\\r\\n]+]\\s+)?([A-Za-z0-9_]{1,16}):\\s*(.*)$");
    private final Map<String, Long> recentIncoming = new LinkedHashMap<>();

    public PrivatePartyRequestCommands() {
    }

    /**
     * Converts an exact English incoming DM containing {@code !p},
     * {@code !party}, or {@code !invite} to a party-invite payload.
     */
    public Optional<String> handleIncomingDirectMessage(String raw, long nowNanos) {
        String text = PartyText.clean(raw);
        if (text.indexOf('\n') >= 0) return Optional.empty();
        Matcher matcher = INCOMING_DIRECT_MESSAGE.matcher(text);
        if (!matcher.matches()) return Optional.empty();
        String sender = matcher.group(1);
        String keyword = matcher.group(2).trim().toLowerCase(Locale.ROOT);
        if (!keyword.equals("!p") && !keyword.equals("!party") && !keyword.equals("!invite")) {
            return Optional.empty();
        }

        expireDedupe(nowNanos);
        String key = sender.toLowerCase(Locale.ROOT) + '\u0000' + keyword;
        Long previous = recentIncoming.putIfAbsent(key, nowNanos);
        if (previous != null && nowNanos - previous >= 0L
                && nowNanos - previous < DM_DEDUPE_NANOS) return Optional.empty();
        recentIncoming.put(key, nowNanos);
        return Optional.of("party invite " + sender);
    }

    public void resetSession() {
        recentIncoming.clear();
    }

    /**
     * Handles {@code //invited by <player>}, {@code //invited <player>}, and
     * {@code //i <player>}. Unknown double-slash commands are deliberately ignored.
     */
    public static LocalResult fromLocalDoubleSlash(String raw) {
        if (raw == null) return LocalResult.ignored();
        String input = raw.trim();
        if (!input.startsWith("//")) return LocalResult.ignored();
        String body = input.substring(2).trim();
        if (body.isEmpty()) return LocalResult.ignored();
        String[] tokens = body.split("\\s+");
        String verb = tokens[0].toLowerCase(Locale.ROOT);

        String target;
        if (verb.equals("i")) {
            if (tokens.length != 2) return LocalResult.error(ErrorKind.INVALID_ARGUMENTS);
            target = tokens[1];
        } else if (verb.equals("invited")) {
            if (tokens.length == 2 && !tokens[1].equalsIgnoreCase("by")) {
                target = tokens[1];
            } else if (tokens.length == 3 && tokens[1].equalsIgnoreCase("by")) {
                target = tokens[2];
            } else {
                return LocalResult.error(ErrorKind.INVALID_ARGUMENTS);
            }
        } else {
            return LocalResult.ignored();
        }

        if (!PartyRosterTracker.validUsername(target)) {
            return LocalResult.error(ErrorKind.INVALID_PLAYER);
        }
        return LocalResult.command("msg " + target + " !p");
    }

    private void expireDedupe(long nowNanos) {
        Iterator<Map.Entry<String, Long>> iterator = recentIncoming.entrySet().iterator();
        while (iterator.hasNext()) {
            long elapsed = nowNanos - iterator.next().getValue();
            if (elapsed < 0L || elapsed >= DM_DEDUPE_NANOS) iterator.remove();
        }
    }

    public enum Status {
        IGNORED,
        COMMAND,
        ERROR
    }

    public enum ErrorKind {
        NONE,
        INVALID_ARGUMENTS,
        INVALID_PLAYER
    }

    public record LocalResult(Status status, String payload, ErrorKind error) {
        public boolean shouldCancelInput() {
            return status != Status.IGNORED;
        }

        private static LocalResult ignored() {
            return new LocalResult(Status.IGNORED, null, ErrorKind.NONE);
        }

        private static LocalResult command(String payload) {
            return new LocalResult(Status.COMMAND, payload, ErrorKind.NONE);
        }

        private static LocalResult error(ErrorKind error) {
            return new LocalResult(Status.ERROR, null, error);
        }
    }
}

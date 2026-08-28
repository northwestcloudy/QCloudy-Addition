package cloudy.autume.addition.party;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Session-only party roster used for safe, deterministic player-name completion.
 * It learns from English Hypixel party chat, lifecycle messages and /party list.
 */
public final class PartyRosterTracker {
    private static final int MAX_MEMBERS = 128;
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern PLAYER_TOKEN = Pattern.compile(
            "^(?:\\[[^]\\r\\n]+]\\s*)?([A-Za-z0-9_]{1,16})(?:\\s*●)?$");
    private static final Pattern YOU_JOINED = Pattern.compile(
            "^You have joined (.+)'s? party!$");
    private static final Pattern OTHER_JOINED = Pattern.compile("^(.+) joined the party\\.$");
    private static final Pattern INITIAL_MEMBERS = Pattern.compile("^You'll be partying with: (.+)$");
    private static final Pattern OTHER_LEFT = Pattern.compile("^(.+) has left the party\\.$");
    private static final Pattern OTHER_REMOVED = Pattern.compile("^(.+) has been removed from the party\\.$");
    private static final Pattern OFFLINE_KICKED = Pattern.compile("^Kicked (.+) because they were offline\\.$");
    private static final Pattern DISCONNECTED = Pattern.compile(
            "^(.+) was removed from your party because they disconnected\\.$");
    private static final Pattern PARTY_LIST_HEADER = Pattern.compile("^Party Members \\(\\d+\\)$");
    private static final Pattern PARTY_LIST_ROW = Pattern.compile(
            "^Party (?:Leader|Moderators|Members): (.+)$");
    private static final Pattern DISBANDED_BY_PLAYER = Pattern.compile("^.+ has disbanded the party!$");
    private static final List<String> RESET_MESSAGES = List.of(
            "You left the party.",
            "The party was disbanded because all invites expired and the party was empty.",
            "The party was disbanded because the party leader disconnected.",
            "You are not currently in a party.",
            "You are not in a party."
    );

    private final LinkedHashMap<String, String> members = new LinkedHashMap<>();

    public void observePartyChat(PartyChatLine line) {
        if (line != null) add(line.sender());
    }

    /** Observes one stripped or legacy-formatted English Hypixel system message. */
    public void observeSystemMessage(String raw) {
        String text = PartyText.clean(raw);
        if (text.isEmpty()) return;

        if (RESET_MESSAGES.contains(text)
                || text.startsWith("You have been kicked from the party by ")
                || DISBANDED_BY_PLAYER.matcher(text).matches()) {
            resetSession();
            return;
        }

        Matcher matcher = PARTY_LIST_HEADER.matcher(text);
        if (matcher.matches()) {
            members.clear();
            return;
        }

        matcher = YOU_JOINED.matcher(text);
        if (matcher.matches()) {
            members.clear();
            extractPlayer(matcher.group(1)).ifPresent(this::add);
            return;
        }
        matcher = OTHER_JOINED.matcher(text);
        if (matcher.matches()) {
            extractPlayer(matcher.group(1)).ifPresent(this::add);
            return;
        }
        matcher = INITIAL_MEMBERS.matcher(text);
        if (matcher.matches()) {
            for (String token : matcher.group(1).split(",\\s*")) {
                extractPlayer(token).ifPresent(this::add);
            }
            return;
        }
        matcher = PARTY_LIST_ROW.matcher(text);
        if (matcher.matches()) {
            for (String token : matcher.group(1).split("\\s*●\\s*")) {
                extractPlayer(token).ifPresent(this::add);
            }
            return;
        }

        removeMatching(OTHER_LEFT, text);
        removeMatching(OTHER_REMOVED, text);
        removeMatching(OFFLINE_KICKED, text);
        removeMatching(DISCONNECTED, text);
    }

    public void resetSession() {
        members.clear();
    }

    public List<String> members() {
        return List.copyOf(members.values());
    }

    /**
     * Resolves exact case-insensitive names first, then a unique prefix. If no
     * cached prefix exists, a complete syntactically valid username is allowed
     * through unchanged so a cold roster does not reject a full name.
     */
    public Resolution resolve(String query, String localPlayer) {
        String candidate = query == null ? "" : query.trim();
        if (!validUsername(candidate)) return Resolution.invalid();

        LinkedHashMap<String, String> available = available(localPlayer);
        String exact = available.get(key(candidate));
        if (exact != null) return Resolution.exact(exact);

        String normalized = key(candidate);
        List<String> prefixes = available.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(normalized))
                .map(java.util.Map.Entry::getValue)
                .toList();
        if (prefixes.size() == 1) return Resolution.uniquePrefix(prefixes.getFirst());
        if (prefixes.size() > 1) return Resolution.ambiguous(prefixes);
        return Resolution.passthrough(candidate);
    }

    public List<String> suggestions(String fragment, String localPlayer) {
        String normalized = key(fragment == null ? "" : fragment.trim());
        return available(localPlayer).entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(normalized))
                .map(java.util.Map.Entry::getValue)
                .toList();
    }

    public static boolean validUsername(String name) {
        return name != null && USERNAME.matcher(name).matches();
    }

    private void add(String name) {
        if (!validUsername(name) || members.size() >= MAX_MEMBERS) return;
        members.putIfAbsent(key(name), name);
    }

    private void removeMatching(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.matches()) return;
        extractPlayer(matcher.group(1)).ifPresent(name -> members.remove(key(name)));
    }

    private LinkedHashMap<String, String> available(String localPlayer) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>(members);
        if (validUsername(localPlayer)) result.putIfAbsent(key(localPlayer), localPlayer);
        return result;
    }

    private static Optional<String> extractPlayer(String value) {
        Matcher matcher = PLAYER_TOKEN.matcher(value == null ? "" : value.trim());
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static String key(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    public enum ResolutionKind {
        EXACT,
        UNIQUE_PREFIX,
        PASSTHROUGH,
        AMBIGUOUS,
        INVALID
    }

    public record Resolution(ResolutionKind kind, String name, List<String> candidates) {
        public Resolution {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }

        public boolean resolved() {
            return kind == ResolutionKind.EXACT || kind == ResolutionKind.UNIQUE_PREFIX
                    || kind == ResolutionKind.PASSTHROUGH;
        }

        private static Resolution exact(String name) {
            return new Resolution(ResolutionKind.EXACT, name, List.of());
        }

        private static Resolution uniquePrefix(String name) {
            return new Resolution(ResolutionKind.UNIQUE_PREFIX, name, List.of());
        }

        private static Resolution passthrough(String name) {
            return new Resolution(ResolutionKind.PASSTHROUGH, name, List.of());
        }

        private static Resolution ambiguous(List<String> candidates) {
            return new Resolution(ResolutionKind.AMBIGUOUS, null, candidates);
        }

        private static Resolution invalid() {
            return new Resolution(ResolutionKind.INVALID, null, List.of());
        }
    }
}

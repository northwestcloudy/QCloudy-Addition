package cloudy.autume.addition.party;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A client-observed, account-scoped Hypixel friend roster.
 *
 * <p>Friend-list rows are accepted only when the received component contains
 * the same structured signals Hypixel uses: a valid {@code /viewprofile UUID}
 * click action and a matching profile hover line. Plain lookalike chat text is
 * deliberately ignored.</p>
 */
public final class FriendRoster {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern VIEW_PROFILE = Pattern.compile("/viewprofile ([0-9a-fA-F-]{36})");
    private static final Pattern PROFILE_HOVER = Pattern.compile(
            "(?s).*Click here to view ([A-Za-z0-9_]{1,16})'s profile.*");
    private static final Pattern FRIENDS_HEADER = Pattern.compile("(?im)^\\s*Friends(?:\\s*\\(|\\s*$)");
    private static final Pattern FRIENDS_PAGE = Pattern.compile(
            "(?i)\\bFriends\\s*\\(\\s*Page\\s+(\\d+)\\s+of\\s+(\\d+)\\s*\\)");
    private static final Pattern PAGE_MARKER = Pattern.compile(
            "(?i)\\bPage\\s+(\\d+)\\s+of\\s+(\\d+)\\b");
    private static final Pattern FRIEND_ADDED = Pattern.compile(
            "(?m)^You are now friends with (?:\\[[^]\\r\\n]+]\\s*)?([A-Za-z0-9_]{1,16})[.!]?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FRIEND_REMOVED = Pattern.compile(
            "(?m)^You removed (?:\\[[^]\\r\\n]+]\\s*)?([A-Za-z0-9_]{1,16}) from your friends list![ \\t]*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BEST_ADDED = Pattern.compile(
            "(?m)^(?:\\[[^]\\r\\n]+]\\s*)?([A-Za-z0-9_]{1,16}) is now a best friend![ \\t]*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BEST_REMOVED = Pattern.compile(
            "(?m)^(?:\\[[^]\\r\\n]+]\\s*)?([A-Za-z0-9_]{1,16}) is no longer a best friend![ \\t]*$",
            Pattern.CASE_INSENSITIVE);

    private final LinkedHashMap<String, Entry> friends = new LinkedHashMap<>();
    private boolean known;
    private PendingSnapshot pendingSnapshot;

    public boolean isKnown() {
        return known;
    }

    public FriendKind kindOf(String username) {
        if (!known || !validUsername(username)) return null;
        Entry entry = friends.get(key(username));
        return entry == null ? null : entry.kind();
    }

    /** Observes a server chat component and returns whether persisted state changed. */
    public boolean observe(Component message) {
        if (message == null) return false;
        String text = clean(message.getString());
        MutationObservation mutation = observeMutations(text);
        boolean changed = mutation.changed();
        if (mutation.matched() && pendingSnapshot != null) {
            pendingSnapshot = null;
            changed |= markUnknown();
        }
        if (FRIENDS_HEADER.matcher(text).find()) {
            LinkedHashMap<String, ParsedFriend> parsed = new LinkedHashMap<>();
            for (Component part : message.toFlatList()) {
                if (!(part.getStyle().getClickEvent() instanceof ClickEvent.RunCommand run)) continue;
                Matcher command = VIEW_PROFILE.matcher(run.command());
                if (!command.matches() || !validUuid(command.group(1))) continue;
                if (!(part.getStyle().getHoverEvent() instanceof HoverEvent.ShowText show)) continue;
                Matcher hover = PROFILE_HOVER.matcher(clean(show.value().getString()));
                if (!hover.matches()) continue;
                String name = hover.group(1);
                boolean special = part.getStyle().isBold() || containsLegacyBold(part.getString());
                parsed.merge(key(name), new ParsedFriend(name, special),
                        (previous, next) -> new ParsedFriend(previous.name(), previous.special() || next.special()));
            }
            changed |= observeSnapshotPage(text, parsed);
        }
        return changed;
    }

    /** Discards any incomplete, in-memory page transaction without trusting the old roster again. */
    void resetPendingSnapshot() {
        pendingSnapshot = null;
    }

    Map<String, String> serializedFriends() {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        friends.forEach((name, entry) -> result.put(entry.name(), entry.kind().name()));
        return result;
    }

    void restore(boolean rosterKnown, Map<String, String> serialized) {
        friends.clear();
        known = rosterKnown;
        pendingSnapshot = null;
        if (serialized == null) return;
        for (Map.Entry<String, String> entry : serialized.entrySet()) {
            if (friends.size() >= 10_000 || !validUsername(entry.getKey())) continue;
            try {
                put(entry.getKey(), FriendKind.valueOf(entry.getValue()));
            } catch (IllegalArgumentException | NullPointerException ignored) {
                // Ignore damaged or future enum values without weakening the roster.
            }
        }
    }

    private MutationObservation observeMutations(String text) {
        boolean matched = false;
        boolean changed = false;
        Matcher added = FRIEND_ADDED.matcher(text);
        while (added.find()) {
            matched = true;
            changed |= put(added.group(1), FriendKind.NORMAL);
        }
        Matcher removed = FRIEND_REMOVED.matcher(text);
        while (removed.find()) {
            matched = true;
            changed |= remove(removed.group(1));
        }
        Matcher bestAdded = BEST_ADDED.matcher(text);
        while (bestAdded.find()) {
            matched = true;
            changed |= put(bestAdded.group(1), FriendKind.SPECIAL);
        }
        Matcher bestRemoved = BEST_REMOVED.matcher(text);
        while (bestRemoved.find()) {
            matched = true;
            changed |= put(bestRemoved.group(1), FriendKind.NORMAL);
        }
        return new MutationObservation(matched, changed);
    }

    /**
     * Applies a friend-list refresh only after every explicitly numbered page has been observed in order.
     * An unnumbered or partial list invalidates the cached classification instead of treating one page as
     * the whole roster. This deliberately favours missing an auto-accept over accepting an ex-friend.
     */
    private boolean observeSnapshotPage(String text, LinkedHashMap<String, ParsedFriend> parsed) {
        Page page = parsePage(text);
        if (page == null) {
            pendingSnapshot = null;
            return markUnknown();
        }

        if (page.number() == 1) {
            if (page.total() == 1) return replaceWith(parsed);
            pendingSnapshot = new PendingSnapshot(page.total());
            pendingSnapshot.add(parsed);
            return markUnknown();
        }

        if (pendingSnapshot == null || pendingSnapshot.totalPages() != page.total()
                || pendingSnapshot.nextPage() != page.number()) {
            pendingSnapshot = null;
            return markUnknown();
        }

        pendingSnapshot.add(parsed);
        if (page.number() == page.total()) {
            LinkedHashMap<String, ParsedFriend> completed = pendingSnapshot.friends();
            pendingSnapshot = null;
            return replaceWith(completed);
        }
        return false;
    }

    private boolean replaceWith(Map<String, ParsedFriend> parsed) {
        LinkedHashMap<String, Entry> replacement = new LinkedHashMap<>();
        for (ParsedFriend friend : parsed.values()) {
            if (replacement.size() >= 10_000 || !validUsername(friend.name())) continue;
            replacement.put(key(friend.name()), new Entry(friend.name(),
                    friend.special() ? FriendKind.SPECIAL : FriendKind.NORMAL));
        }
        boolean changed = !known || !friends.equals(replacement);
        friends.clear();
        friends.putAll(replacement);
        known = true;
        pendingSnapshot = null;
        return changed;
    }

    private boolean markUnknown() {
        if (!known) return false;
        known = false;
        return true;
    }

    private static Page parsePage(String text) {
        Matcher matcher = FRIENDS_PAGE.matcher(text);
        if (!matcher.find()) {
            matcher = PAGE_MARKER.matcher(text);
            if (!matcher.find()) return null;
        }
        try {
            int number = Integer.parseInt(matcher.group(1));
            int total = Integer.parseInt(matcher.group(2));
            if (number < 1 || total < 1 || total > 1_000 || number > total) return null;
            return new Page(number, total);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean put(String name, FriendKind kind) {
        if (!validUsername(name) || kind == null) return false;
        String normalized = key(name);
        Entry next = new Entry(name, kind);
        Entry previous = friends.put(normalized, next);
        return !next.equals(previous);
    }

    private boolean remove(String name) {
        return validUsername(name) && friends.remove(key(name)) != null;
    }

    static boolean validUsername(String name) {
        return name != null && USERNAME.matcher(name).matches();
    }

    private static boolean validUuid(String raw) {
        try {
            UUID.fromString(raw);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static String key(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static boolean containsLegacyBold(String value) {
        if (value == null) return false;
        for (int i = 0; i + 1 < value.length(); i++) {
            if (value.charAt(i) == '\u00a7' && Character.toLowerCase(value.charAt(i + 1)) == 'l') return true;
        }
        return false;
    }

    static String clean(String value) {
        if (value == null) return "";
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '\u00a7' && i + 1 < value.length()) {
                i++;
                continue;
            }
            result.append(current);
        }
        return result.toString().replace('\r', '\n').trim();
    }

    public enum FriendKind {
        NORMAL,
        SPECIAL
    }

    private record Entry(String name, FriendKind kind) {
    }

    private record ParsedFriend(String name, boolean special) {
    }

    private record Page(int number, int total) {
    }

    private record MutationObservation(boolean matched, boolean changed) {
    }

    private static final class PendingSnapshot {
        private final int totalPages;
        private int nextPage = 1;
        private final LinkedHashMap<String, ParsedFriend> friends = new LinkedHashMap<>();

        private PendingSnapshot(int totalPages) {
            this.totalPages = totalPages;
        }

        private void add(Map<String, ParsedFriend> page) {
            for (ParsedFriend friend : page.values()) {
                if (friends.size() >= 10_000 && !friends.containsKey(key(friend.name()))) continue;
                friends.merge(key(friend.name()), friend,
                        (previous, next) -> new ParsedFriend(previous.name(),
                                previous.special() || next.special()));
            }
            nextPage++;
        }

        private int totalPages() {
            return totalPages;
        }

        private int nextPage() {
            return nextPage;
        }

        private LinkedHashMap<String, ParsedFriend> friends() {
            return new LinkedHashMap<>(friends);
        }
    }
}

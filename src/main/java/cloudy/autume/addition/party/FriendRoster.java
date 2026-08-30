package cloudy.autume.addition.party;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    private static final Pattern HORIZONTAL_RULE = Pattern.compile(
            "(?m)^\\s*[-\\u2500\\u2501]{10,}\\s*$");
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
    /** Names proven by structured rows during the current, not-yet-complete refresh. */
    private final Set<String> verifiedCurrent = new LinkedHashSet<>();
    private boolean known;
    private PendingSnapshot pendingSnapshot;
    private ActivePage activePage;

    public boolean isKnown() {
        return known;
    }

    public FriendKind kindOf(String username) {
        if (!validUsername(username)) return null;
        Entry entry = friends.get(key(username));
        if (entry == null || !known && !verifiedCurrent.contains(key(username))) return null;
        return entry.kind();
    }

    /** Observes a server chat component and returns whether persisted state changed. */
    public boolean observe(Component message) {
        if (message == null) return false;
        String text = clean(message.getString());
        MutationObservation mutation = observeMutations(text);
        boolean changed = mutation.changed();
        if (mutation.matched()) {
            if (pendingSnapshot != null || activePage != null) {
                pendingSnapshot = null;
                activePage = null;
                verifiedCurrent.clear();
                changed |= markUnknown();
            }
            for (String touched : mutation.touched()) {
                if (friends.containsKey(touched)) verifiedCurrent.add(touched);
                else verifiedCurrent.remove(touched);
            }
        }

        if (FRIENDS_HEADER.matcher(text).find()) {
            if (activePage != null) changed |= finishActivePage();
            Page page = parsePage(text);
            if (page == null) {
                changed |= abortRefresh(true);
                return changed;
            }
            changed |= beginPage(page);
            LinkedHashMap<String, ParsedFriend> parsed = parseStructuredFriends(message);
            changed |= applyVerified(parsed);
            activePage.add(parsed);
            // Aggregate list messages contain their structured rows in the
            // same component. A streamed empty page is completed by its footer.
            if (!parsed.isEmpty() || hasRuleAfterPageHeader(text)) changed |= finishActivePage();
            return changed;
        }

        if (activePage != null) {
            LinkedHashMap<String, ParsedFriend> parsed = parseStructuredFriends(message);
            if (!parsed.isEmpty()) {
                LinkedHashMap<String, ParsedFriend> streamed = verifiedStreamedRows(text, parsed);
                if (streamed.isEmpty()) {
                    // A profile link can also appear in public, guild or party
                    // chat. It must not keep a friend-list transaction alive.
                    changed |= abortRefresh(false);
                    return changed;
                }
                changed |= applyVerified(streamed);
                activePage.add(streamed);
                return changed;
            }
            if (HORIZONTAL_RULE.matcher(text).matches()) {
                changed |= finishActivePage();
                return changed;
            }
            if (!text.isBlank()) changed |= abortRefresh(false);
        }
        return changed;
    }

    private static LinkedHashMap<String, ParsedFriend> parseStructuredFriends(Component message) {
        LinkedHashMap<String, ParsedFriend> parsed = new LinkedHashMap<>();
        for (Component part : message.toFlatList()) {
            if (!(part.getStyle().getClickEvent() instanceof ClickEvent.RunCommand run)) continue;
            Matcher command = VIEW_PROFILE.matcher(run.command());
            if (!command.matches() || !validUuid(command.group(1))) continue;
            if (!(part.getStyle().getHoverEvent() instanceof HoverEvent.ShowText show)) continue;
            Matcher hover = PROFILE_HOVER.matcher(clean(show.value().getString()));
            if (!hover.matches()) continue;
            String name = hover.group(1);
            String visible = clean(part.getString());
            if (!visible.equalsIgnoreCase(name)
                    && !visible.toLowerCase(Locale.ROOT).startsWith(key(name) + " ")) continue;
            boolean special = part.getStyle().isBold() || containsLegacyBold(part.getString());
            parsed.merge(key(name), new ParsedFriend(name, special),
                    (previous, next) -> new ParsedFriend(previous.name(), previous.special() || next.special()));
        }
        return parsed;
    }

    private static LinkedHashMap<String, ParsedFriend> verifiedStreamedRows(
            String text, Map<String, ParsedFriend> parsed) {
        LinkedHashMap<String, ParsedFriend> verified = new LinkedHashMap<>();
        if (text == null || text.indexOf('\n') >= 0) return verified;
        for (Map.Entry<String, ParsedFriend> entry : parsed.entrySet()) {
            if (isExactFriendStatusLine(text, entry.getValue().name())) {
                verified.put(entry.getKey(), entry.getValue());
            }
        }
        return verified;
    }

    private static boolean isExactFriendStatusLine(String text, String name) {
        String prefix = name + " ";
        if (!text.startsWith(prefix)) return false;
        String status = text.substring(prefix.length());
        if (status.equals("is currently offline")) return true;
        if (!status.startsWith("is in ")) return false;
        String location = status.substring("is in ".length());
        return !location.isBlank() && location.equals(location.trim()) && location.length() <= 160;
    }

    private boolean beginPage(Page page) {
        boolean continuing = page.number() > 1 && pendingSnapshot != null && pendingSnapshot.expects(page);
        if (!continuing) {
            pendingSnapshot = null;
            verifiedCurrent.clear();
            if (page.number() == 1) pendingSnapshot = new PendingSnapshot(page.total());
        }
        activePage = new ActivePage(page);
        return markUnknown();
    }

    /** Discards incomplete page transactions and current-session proofs. */
    void resetPendingSnapshot() {
        pendingSnapshot = null;
        activePage = null;
        verifiedCurrent.clear();
    }

    Map<String, String> serializedFriends() {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        friends.forEach((name, entry) -> result.put(entry.name(), entry.kind().name()));
        return result;
    }

    void restore(boolean rosterKnown, Map<String, String> serialized) {
        friends.clear();
        verifiedCurrent.clear();
        known = rosterKnown;
        pendingSnapshot = null;
        activePage = null;
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
        LinkedHashSet<String> touched = new LinkedHashSet<>();
        Matcher added = FRIEND_ADDED.matcher(text);
        while (added.find()) {
            matched = true;
            changed |= put(added.group(1), FriendKind.NORMAL);
            touched.add(key(added.group(1)));
        }
        Matcher removed = FRIEND_REMOVED.matcher(text);
        while (removed.find()) {
            matched = true;
            changed |= remove(removed.group(1));
            touched.add(key(removed.group(1)));
        }
        Matcher bestAdded = BEST_ADDED.matcher(text);
        while (bestAdded.find()) {
            matched = true;
            changed |= put(bestAdded.group(1), FriendKind.SPECIAL);
            touched.add(key(bestAdded.group(1)));
        }
        Matcher bestRemoved = BEST_REMOVED.matcher(text);
        while (bestRemoved.find()) {
            matched = true;
            changed |= put(bestRemoved.group(1), FriendKind.NORMAL);
            touched.add(key(bestRemoved.group(1)));
        }
        return new MutationObservation(matched, changed, touched);
    }

    private boolean applyVerified(Map<String, ParsedFriend> parsed) {
        boolean changed = false;
        for (ParsedFriend friend : parsed.values()) {
            if (friends.size() >= 10_000 && !friends.containsKey(key(friend.name()))) continue;
            changed |= put(friend.name(), friend.special() ? FriendKind.SPECIAL : FriendKind.NORMAL);
            verifiedCurrent.add(key(friend.name()));
        }
        return changed;
    }

    private boolean finishActivePage() {
        if (activePage == null) return false;
        ActivePage completedPage = activePage;
        activePage = null;
        if (pendingSnapshot == null || !pendingSnapshot.expects(completedPage.page())) return false;
        pendingSnapshot.add(completedPage.friends());
        if (completedPage.page().number() != completedPage.page().total()) return false;
        LinkedHashMap<String, ParsedFriend> completed = pendingSnapshot.friends();
        pendingSnapshot = null;
        return replaceWith(completed);
    }

    private boolean abortRefresh(boolean clearCurrentProofs) {
        pendingSnapshot = null;
        activePage = null;
        if (clearCurrentProofs) verifiedCurrent.clear();
        return markUnknown();
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
        activePage = null;
        verifiedCurrent.clear();
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

    private static boolean hasRuleAfterPageHeader(String text) {
        Matcher header = FRIENDS_PAGE.matcher(text);
        if (!header.find()) return false;
        return HORIZONTAL_RULE.matcher(text.substring(header.end())).find();
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

    private record MutationObservation(boolean matched, boolean changed, Set<String> touched) {
    }

    private static final class ActivePage {
        private final Page page;
        private final LinkedHashMap<String, ParsedFriend> friends = new LinkedHashMap<>();

        private ActivePage(Page page) {
            this.page = page;
        }

        private void add(Map<String, ParsedFriend> rows) {
            mergeInto(friends, rows);
        }

        private Page page() {
            return page;
        }

        private LinkedHashMap<String, ParsedFriend> friends() {
            return new LinkedHashMap<>(friends);
        }
    }

    private static final class PendingSnapshot {
        private final int totalPages;
        private int nextPage = 1;
        private final LinkedHashMap<String, ParsedFriend> friends = new LinkedHashMap<>();

        private PendingSnapshot(int totalPages) {
            this.totalPages = totalPages;
        }

        private void add(Map<String, ParsedFriend> page) {
            mergeInto(friends, page);
            nextPage++;
        }

        private boolean expects(Page page) {
            return totalPages == page.total() && nextPage == page.number();
        }

        private LinkedHashMap<String, ParsedFriend> friends() {
            return new LinkedHashMap<>(friends);
        }
    }

    private static void mergeInto(LinkedHashMap<String, ParsedFriend> destination,
                                  Map<String, ParsedFriend> source) {
        for (ParsedFriend friend : source.values()) {
            if (destination.size() >= 10_000 && !destination.containsKey(key(friend.name()))) continue;
            destination.merge(key(friend.name()), friend,
                    (previous, next) -> new ParsedFriend(previous.name(),
                            previous.special() || next.special()));
        }
    }
}

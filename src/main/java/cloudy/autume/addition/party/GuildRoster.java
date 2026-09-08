package cloudy.autume.addition.party;

import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Account-scoped, fail-closed snapshot observed from Hypixel's full guild list. */
public final class GuildRoster {
    private static final Pattern HEADER = Pattern.compile("^Guild Name:\\s+.+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ONLINE = Pattern.compile(
            "^Online Members:\\s*(\\d+)(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern OFFLINE = Pattern.compile(
            "^Offline Members:\\s*(\\d+)(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOTAL = Pattern.compile(
            "^Total Members:\\s*(\\d+).*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RULE = Pattern.compile("^[-\\u2500\\u2501]{10,}$");
    private static final Pattern MEMBER = Pattern.compile(
            "^(?:\\[[^]\\r\\n]+]\\s*)*([A-Za-z0-9_]{1,16})(?:\\s+\\[[^]\\r\\n]+])?$");
    private static final Pattern JOINED = Pattern.compile(
            "^(?:\\[[^]\\r\\n]+]\\s*)?([A-Za-z0-9_]{1,16}) joined the guild!$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LEFT = Pattern.compile(
            "^(?:\\[[^]\\r\\n]+]\\s*)?([A-Za-z0-9_]{1,16}) (?:left|was kicked from) the guild!$",
            Pattern.CASE_INSENSITIVE);

    private final LinkedHashMap<String, String> members = new LinkedHashMap<>();
    private LinkedHashMap<String, String> pending;
    private boolean known;
    private boolean sawOnline;
    private boolean sawOffline;
    private int expectedOnline = -1;
    private int expectedOffline = -1;
    private int expectedTotal = -1;

    public boolean isKnown() {
        return known;
    }

    public boolean contains(String username) {
        return known && FriendRoster.validUsername(username)
                && members.containsKey(key(username));
    }

    /** Returns true when persistent state changed. */
    public boolean observe(Component message) {
        if (message == null) return false;
        boolean changed = false;
        for (String rawLine : message.getString().split("\\R")) {
            String line = FriendRoster.clean(rawLine);
            if (line.isBlank()) continue;

            if (HEADER.matcher(line).matches()) {
                pending = new LinkedHashMap<>();
                sawOnline = false;
                sawOffline = false;
                expectedOnline = -1;
                expectedOffline = -1;
                expectedTotal = -1;
                if (known) {
                    known = false;
                    changed = true;
                }
                continue;
            }

            if (pending != null) {
                Matcher online = ONLINE.matcher(line);
                if (online.matches()) {
                    sawOnline = true;
                    expectedOnline = count(online.group(1));
                    parseMembers(online.group(2), pending);
                    continue;
                }
                Matcher offline = OFFLINE.matcher(line);
                if (offline.matches()) {
                    sawOffline = true;
                    expectedOffline = count(offline.group(1));
                    parseMembers(offline.group(2), pending);
                    continue;
                }
                Matcher total = TOTAL.matcher(line);
                if (total.matches()) {
                    expectedTotal = count(total.group(1));
                    continue;
                }
                if (RULE.matcher(line).matches()) {
                    // Hypixel may draw a separator between the Online and
                    // Offline blocks. Do not mistake that for a complete
                    // all-members transaction.
                    if (sawOnline && !sawOffline) continue;
                    if (sawOnline && sawOffline && memberCountsConsistent()) {
                        changed |= !known || !members.equals(pending);
                        members.clear();
                        members.putAll(pending);
                        known = true;
                    }
                    pending = null;
                    sawOnline = false;
                    sawOffline = false;
                    continue;
                }
                if ((sawOnline || sawOffline) && line.contains("●")) {
                    parseMembers(line, pending);
                    continue;
                }
                // A new unrelated chat line means the list was truncated or
                // /g onlinemode hid the offline section. Keep fail-closed.
                if (sawOnline || sawOffline) {
                    pending = null;
                    sawOnline = false;
                    sawOffline = false;
                }
            }

            if (!known) continue;
            Matcher joined = JOINED.matcher(line);
            if (joined.matches()) changed |= put(joined.group(1));
            Matcher left = LEFT.matcher(line);
            if (left.matches()) changed |= members.remove(key(left.group(1))) != null;
        }
        return changed;
    }

    void resetPendingSnapshot() {
        pending = null;
        sawOnline = false;
        sawOffline = false;
        expectedOnline = -1;
        expectedOffline = -1;
        expectedTotal = -1;
    }

    Map<String, String> serializedMembers() {
        return new LinkedHashMap<>(members);
    }

    void restore(boolean rosterKnown, Map<String, String> serialized) {
        members.clear();
        known = rosterKnown;
        resetPendingSnapshot();
        if (serialized == null) return;
        for (String name : serialized.values()) {
            if (members.size() >= 10_000) break;
            put(name);
        }
    }

    private boolean put(String name) {
        if (!FriendRoster.validUsername(name)) return false;
        String previous = members.put(key(name), name);
        return !name.equals(previous);
    }

    private static void parseMembers(String text, Map<String, String> destination) {
        if (text == null) return;
        LinkedHashMap<String, String> parsed = new LinkedHashMap<>();
        for (String segment : text.split("●")) {
            String candidate = FriendRoster.clean(segment);
            if (candidate.isBlank()) continue;
            if (candidate.matches("[0-9]+")) continue;
            Matcher member = MEMBER.matcher(candidate);
            // Reject the entire row if any segment is not a guild-list member.
            // This prevents an interleaved chat line containing a decorative
            // bullet from contributing a forged trailing name.
            if (!member.matches()) return;
            String name = member.group(1);
            if (FriendRoster.validUsername(name) && parsed.size() < 10_000) {
                parsed.put(key(name), name);
            }
        }
        for (Map.Entry<String, String> entry : parsed.entrySet()) {
            if (destination.size() >= 10_000) break;
            destination.put(entry.getKey(), entry.getValue());
        }
    }

    private boolean memberCountsConsistent() {
        if (pending == null || expectedOnline < 0 || expectedOffline < 0) return false;
        int expected = expectedOnline + expectedOffline;
        return expected <= 10_000 && pending.size() == expected
                && (expectedTotal < 0 || expectedTotal == expected);
    }

    private static int count(String raw) {
        try {
            int value = Integer.parseInt(raw);
            return value >= 0 && value <= 10_000 ? value : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}

package cloudy.autume.addition.party;

import cloudy.autume.addition.config.ModConfig;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Determines safe, exact party-accept commands from client-received Hypixel chat. */
public final class PartyAutoAcceptManager {
    static final long DEDUPE_MS = 5_000L;
    private static final Pattern ENGLISH_INVITE = Pattern.compile(
            "^(?:\\[[^]\\r\\n]+]\\s*)?([A-Za-z0-9_]{1,16}) has invited you to join .+ party!$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CHINESE_INVITE = Pattern.compile(
            "^(?:\\[[^]\\r\\n]+]\\s*)?([A-Za-z0-9_]{1,16})\\s*邀请你加入.+组队！$");

    private final FriendRosterStore store;
    private final Map<String, Long> accepted = new ConcurrentHashMap<>();

    public PartyAutoAcceptManager(FriendRosterStore store) {
        this.store = store;
    }

    public void load() {
        store.load();
    }

    /**
     * Returns the validated command without a leading slash, or {@code null}.
     * Roster observations are retained even while auto-accept is disabled.
     */
    public String onMessage(Component message, boolean overlay, boolean onHypixel, String accountKey,
                            boolean enabled, ModConfig.PartyAcceptFriendMode mode,
                            List<String> whitelist, long nowMs) {
        if (overlay || !onHypixel || message == null || accountKey == null || accountKey.isBlank()) return null;

        FriendRoster roster = store.roster(accountKey);
        if (roster.observe(message)) store.save(accountKey, roster);
        if (!enabled || mode == null) return null;

        String inviter = directInviter(message.getString());
        if (inviter == null) return null;
        String normalized = inviter.toLowerCase(Locale.ROOT);
        boolean whitelisted = normalizedWhitelist(whitelist).contains(normalized);
        if (!whitelisted) {
            FriendRoster.FriendKind kind = roster.kindOf(inviter);
            if (kind == null) return null;
            if (mode == ModConfig.PartyAcceptFriendMode.NORMAL_ONLY
                    && kind != FriendRoster.FriendKind.NORMAL) return null;
            if (mode == ModConfig.PartyAcceptFriendMode.SPECIAL_ONLY
                    && kind != FriendRoster.FriendKind.SPECIAL) return null;
        }

        expireDedupe(nowMs);
        String invitationKey = normalized + '\u0000' + FriendRoster.clean(message.getString()).toLowerCase(Locale.ROOT);
        Long previous = accepted.putIfAbsent(invitationKey, nowMs);
        if (previous != null && nowMs - previous < DEDUPE_MS) return null;
        accepted.put(invitationKey, nowMs);
        return "party accept " + inviter;
    }

    public void resetSession() {
        accepted.clear();
        store.resetPendingSnapshots();
    }

    public static boolean isHypixelAddress(String rawAddress) {
        if (rawAddress == null || rawAddress.isBlank()) return false;
        String host;
        try {
            host = ServerAddress.parseString(rawAddress).getHost().toLowerCase(Locale.ROOT);
        } catch (RuntimeException ignored) {
            return false;
        }
        return host.equals("hypixel.net") || host.endsWith(".hypixel.net")
                || host.equals("hypixel.io") || host.endsWith(".hypixel.io");
    }

    static String directInviter(String raw) {
        if (raw == null) return null;
        for (String rawLine : raw.split("\\R")) {
            String line = FriendRoster.clean(rawLine);
            Matcher english = ENGLISH_INVITE.matcher(line);
            if (english.matches() && FriendRoster.validUsername(english.group(1))) return english.group(1);
            Matcher chinese = CHINESE_INVITE.matcher(line);
            if (chinese.matches() && FriendRoster.validUsername(chinese.group(1))) return chinese.group(1);
        }
        return null;
    }

    static LinkedHashSet<String> normalizedWhitelist(List<String> input) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (input == null) return result;
        for (String rawName : input) {
            if (result.size() >= 16) break;
            String name = rawName == null ? "" : rawName.trim();
            if (FriendRoster.validUsername(name)) result.add(name.toLowerCase(Locale.ROOT));
        }
        return result;
    }

    private void expireDedupe(long nowMs) {
        Iterator<Map.Entry<String, Long>> iterator = accepted.entrySet().iterator();
        while (iterator.hasNext()) {
            if (nowMs - iterator.next().getValue() >= DEDUPE_MS) iterator.remove();
        }
    }
}

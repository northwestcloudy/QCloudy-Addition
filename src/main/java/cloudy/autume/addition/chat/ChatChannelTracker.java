package cloudy.autume.addition.chat;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Session-only state machine for Hypixel's selected chat channel.
 *
 * <p>A physical click creates only a pending request. The active channel changes
 * after an exact server acknowledgement; it is never persisted across sessions.</p>
 */
public final class ChatChannelTracker {
    public static final long DEFAULT_TIMEOUT_NANOS = 3_000_000_000L;

    private static final Pattern CHANNEL_SWITCH_EN = Pattern.compile(
            "^You (?:are|'re) now in the "
                    + "(GUILD|OFFICER|PARTY|SKYBLOCK CO-OP|ALL|公会|公会管理频道|组队|空岛生存合作模式|所有)"
                    + " channel!?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CHANNEL_SWITCH_CN = Pattern.compile(
            "^你正处于(公会|公会管理频道|组队|空岛生存合作模式|所有)频道中[！!]?$");
    private static final Pattern MOVED_TO_ALL = Pattern.compile(
            "^(?:You (?:are no longer in .+? and have been moved back to the (?:ALL|所有)"
                    + "|are not in a party and were moved to the (?:ALL|所有)) channel[.!]"
                    + "|The conversation you were in expired and you have been moved back to the "
                    + "(?:ALL|所有) channel[.!])$",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> REQUEST_REJECTIONS = Set.of(
            "you are not currently in a party.",
            "you are not in a party.",
            "you are not in a guild.",
            "you are not in a guild!",
            "you are not currently in a co-op.",
            "you are not in a co-op.",
            "you do not have a co-op.",
            "you must be a guild officer to use this channel.",
            "you must be an officer or guild master to use this channel.",
            "你当前不在组队中。",
            "你不在组队中。",
            "你不在公会中。",
            "你没有空岛生存合作模式。"
    );

    private final long timeoutNanos;
    private @Nullable ChatChannel confirmed;
    private @Nullable ChatChannel pending;
    private long pendingSinceNanos;

    public ChatChannelTracker() {
        this(DEFAULT_TIMEOUT_NANOS);
    }

    ChatChannelTracker(long timeoutNanos) {
        if (timeoutNanos <= 0L) throw new IllegalArgumentException("timeoutNanos must be positive");
        this.timeoutNanos = timeoutNanos;
    }

    public synchronized boolean beginRequest(ChatChannel target, long nowNanos) {
        expire(nowNanos);
        if (target == null || pending != null || target == confirmed) return false;
        pending = target;
        pendingSinceNanos = nowNanos;
        return true;
    }

    public synchronized void cancelRequest() {
        pending = null;
        pendingSinceNanos = 0L;
    }

    public synchronized void observe(String rawText, long nowNanos) {
        expire(nowNanos);
        Feedback feedback = parse(rawText);
        if (feedback.channel() != null) {
            confirmed = feedback.channel();
            pending = null;
            pendingSinceNanos = 0L;
        } else if (feedback.rejected() && pending != null) {
            pending = null;
            pendingSinceNanos = 0L;
        }
    }

    public synchronized Snapshot snapshot(long nowNanos) {
        expire(nowNanos);
        Phase phase = pending != null ? Phase.PENDING
                : confirmed != null ? Phase.CONFIRMED : Phase.UNKNOWN;
        return new Snapshot(phase, confirmed, pending);
    }

    public synchronized void reset() {
        confirmed = null;
        pending = null;
        pendingSinceNanos = 0L;
    }

    private void expire(long nowNanos) {
        if (pending == null || nowNanos - pendingSinceNanos < timeoutNanos) return;
        // The command may have reached the server even when its acknowledgement
        // was hidden or delayed. Forget the old confirmation instead of lying.
        confirmed = null;
        pending = null;
        pendingSinceNanos = 0L;
    }

    static Feedback parse(String rawText) {
        String text = normalize(rawText);
        if (text.isEmpty()) return Feedback.NONE;

        Matcher matcher = CHANNEL_SWITCH_EN.matcher(text);
        if (matcher.matches()) return Feedback.confirmed(channel(matcher.group(1)));
        matcher = CHANNEL_SWITCH_CN.matcher(text);
        if (matcher.matches()) return Feedback.confirmed(channel(matcher.group(1)));
        if (MOVED_TO_ALL.matcher(text).matches()) return Feedback.confirmed(ChatChannel.ALL);
        if (REQUEST_REJECTIONS.contains(text.toLowerCase(Locale.ROOT))) return Feedback.REJECTED;
        return Feedback.NONE;
    }

    static String normalize(String rawText) {
        if (rawText == null) return "";
        return rawText.replaceAll("§.", "")
                .replaceAll("[\\uE000-\\uF8FF]", "")
                .trim().replaceAll("\\s+", " ");
    }

    private static @Nullable ChatChannel channel(String name) {
        if (name == null) return null;
        return switch (name.toUpperCase(Locale.ROOT)) {
            case "ALL", "所有" -> ChatChannel.ALL;
            case "PARTY", "组队" -> ChatChannel.PARTY;
            case "GUILD", "公会" -> ChatChannel.GUILD;
            case "OFFICER", "公会管理频道" -> ChatChannel.OFFICER;
            case "SKYBLOCK CO-OP", "空岛生存合作模式" -> ChatChannel.COOP;
            default -> null;
        };
    }

    public enum Phase {
        UNKNOWN,
        PENDING,
        CONFIRMED
    }

    public record Snapshot(Phase phase, @Nullable ChatChannel confirmed,
                           @Nullable ChatChannel pending) {
    }

    record Feedback(@Nullable ChatChannel channel, boolean rejected) {
        private static final Feedback NONE = new Feedback(null, false);
        private static final Feedback REJECTED = new Feedback(null, true);

        private static Feedback confirmed(@Nullable ChatChannel channel) {
            return channel == null ? NONE : new Feedback(channel, false);
        }
    }
}

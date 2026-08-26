package cloudy.autume.addition.combat;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Pure client-side state for the three server-confirmed death-save messages.
 * Presentation is deliberately left to the caller so configuration, titles
 * and independently positioned HUDs can consume the same event.
 */
public final class DeathSaveAlertManager {
    public static final long DUPLICATE_WINDOW_NANOS = Duration.ofSeconds(2).toNanos();
    private static final long NANOS_PER_MILLISECOND = 1_000_000L;
    private static final DeathSaveAlertManager RUNTIME = new DeathSaveAlertManager();

    private static final Pattern FORMATTING_CODE = Pattern.compile("§.");
    private static final Map<String, Ability> SERVER_MESSAGES = Map.of(
            "Second Wind Activated! Your Spirit Mask saved your life!", Ability.SPIRIT_MASK,
            "Your Bonzo's Mask saved your life!", Ability.BONZO_MASK,
            "Your Phoenix Pet saved you from certain death!", Ability.PHOENIX);

    private final EnumMap<Ability, Long> readyAtNanos = new EnumMap<>(Ability.class);
    private final EnumMap<Ability, Long> lastAcceptedAtNanos = new EnumMap<>(Ability.class);

    /**
     * Accepts only one of the three exact server messages after removing
     * Minecraft formatting codes and surrounding whitespace.
     *
     * @return an alert event for a newly accepted activation, otherwise null
     */
    public Alert onMessage(String rawMessage, long nowNanos) {
        Ability ability = abilityFromMessage(rawMessage);
        if (ability == null) return null;

        Long previous = lastAcceptedAtNanos.get(ability);
        if (previous != null) {
            long elapsed = nowNanos - previous;
            if (elapsed >= 0L && elapsed < DUPLICATE_WINDOW_NANOS) return null;
        }

        long readyAt = nowNanos + ability.baseCooldownNanos();
        lastAcceptedAtNanos.put(ability, nowNanos);
        readyAtNanos.put(ability, readyAt);
        return new Alert(ability, ability.centerTitle(), readyAt);
    }

    /** Uses the monotonic client clock for the normal runtime path. */
    public Alert onMessage(String rawMessage) {
        return onMessage(rawMessage, System.nanoTime());
    }

    /** Shared runtime entry used by the two mutually exclusive Fabric chat callbacks. */
    public static Alert handle(String rawMessage) {
        return RUNTIME.onMessage(rawMessage);
    }

    /**
     * Shared runtime snapshot for the three independently positioned HUDs.
     * Rounding up keeps a final partial millisecond visible instead of reporting
     * ready before the monotonic deadline has actually elapsed.
     */
    public static long remainingMillis(Ability ability) {
        long remaining = RUNTIME.remainingNanos(ability);
        return remaining == 0L ? 0L : (remaining + NANOS_PER_MILLISECOND - 1L) / NANOS_PER_MILLISECOND;
    }

    /** Silently clears the shared runtime on disconnect. */
    public static void resetRuntime() {
        RUNTIME.reset();
    }

    /** Returns zero when this ability has no active local cooldown. */
    public long remainingNanos(Ability ability, long nowNanos) {
        if (ability == null) return 0L;
        Long readyAt = readyAtNanos.get(ability);
        if (readyAt == null || nowNanos - readyAt >= 0L) return 0L;
        return readyAt - nowNanos;
    }

    /** Uses the monotonic client clock for the normal runtime path. */
    public long remainingNanos(Ability ability) {
        return remainingNanos(ability, System.nanoTime());
    }

    /** Exposes the accepted deadline for HUD snapshots and integration tests. */
    public long readyAtNanos(Ability ability) {
        if (ability == null) return 0L;
        return readyAtNanos.getOrDefault(ability, 0L);
    }

    /** Silently clears all cooldowns and duplicate guards on disconnect. */
    public void reset() {
        readyAtNanos.clear();
        lastAcceptedAtNanos.clear();
    }

    static Ability abilityFromMessage(String rawMessage) {
        if (rawMessage == null) return null;
        String plain = FORMATTING_CODE.matcher(rawMessage).replaceAll("").trim();
        return SERVER_MESSAGES.get(plain);
    }

    public enum Ability {
        SPIRIT_MASK(30, "!You've Been Saved By Spirit Mask!"),
        BONZO_MASK(360, "!You've Been Saved By Bonzo's Mask!"),
        PHOENIX(60, "!You've Been Saved By Phoenix!");

        private final long baseCooldownSeconds;
        private final String centerTitle;

        Ability(long baseCooldownSeconds, String centerTitle) {
            this.baseCooldownSeconds = baseCooldownSeconds;
            this.centerTitle = centerTitle;
        }

        public long baseCooldownSeconds() {
            return baseCooldownSeconds;
        }

        public long baseCooldownNanos() {
            return Duration.ofSeconds(baseCooldownSeconds).toNanos();
        }

        public String centerTitle() {
            return centerTitle;
        }
    }

    public record Alert(Ability ability, String centerTitle, long readyAtNanos) {
    }
}

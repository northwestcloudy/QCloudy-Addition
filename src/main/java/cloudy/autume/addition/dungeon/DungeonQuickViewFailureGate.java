package cloudy.autume.addition.dungeon;

import java.time.Duration;

/** Suppresses repeated requests and chat warnings during a quick-view service outage. */
final class DungeonQuickViewFailureGate {
    private final long cooldownNanos;
    private boolean blocked;
    private long blockedAtNanos;

    DungeonQuickViewFailureGate(Duration cooldown) {
        if (cooldown == null || cooldown.isNegative() || cooldown.isZero()) {
            throw new IllegalArgumentException("cooldown must be positive");
        }
        cooldownNanos = cooldown.toNanos();
    }

    boolean allowRequest(long nowNanos) {
        if (!blocked) return true;
        if (nowNanos - blockedAtNanos < cooldownNanos) return false;
        blocked = false;
        return true;
    }

    /** Returns whether this failure should produce a player-facing warning. */
    boolean recordFailure(boolean serviceFailure, long nowNanos) {
        if (!serviceFailure) return true;
        if (!allowRequest(nowNanos)) return false;
        blocked = true;
        blockedAtNanos = nowNanos;
        return true;
    }

    void recordSuccess() {
        blocked = false;
    }

    void reset() {
        blocked = false;
        blockedAtNanos = 0L;
    }
}

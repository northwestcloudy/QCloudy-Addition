package cloudy.autume.addition.dungeon;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DungeonQuickViewFailureGateTest {
    private static final long SECOND = 1_000_000_000L;

    @Test
    void suppressesRepeatedServiceFailuresUntilTheCooldownExpires() {
        DungeonQuickViewFailureGate gate = new DungeonQuickViewFailureGate(Duration.ofSeconds(30));

        assertTrue(gate.allowRequest(10 * SECOND));
        assertTrue(gate.recordFailure(true, 11 * SECOND));
        assertFalse(gate.allowRequest(20 * SECOND));
        assertFalse(gate.recordFailure(true, 21 * SECOND));
        assertTrue(gate.allowRequest(41 * SECOND));
        assertTrue(gate.recordFailure(true, 41 * SECOND));
    }

    @Test
    void playerSpecificFailuresDoNotBlockOtherPlayers() {
        DungeonQuickViewFailureGate gate = new DungeonQuickViewFailureGate(Duration.ofSeconds(30));

        assertTrue(gate.recordFailure(false, 10 * SECOND));
        assertTrue(gate.allowRequest(11 * SECOND));
        assertTrue(gate.recordFailure(false, 12 * SECOND));
    }

    @Test
    void successAndResetClearTheCooldown() {
        DungeonQuickViewFailureGate gate = new DungeonQuickViewFailureGate(Duration.ofSeconds(30));
        gate.recordFailure(true, 10 * SECOND);
        gate.recordSuccess();
        assertTrue(gate.allowRequest(11 * SECOND));

        gate.recordFailure(true, 12 * SECOND);
        gate.reset();
        assertTrue(gate.allowRequest(13 * SECOND));
    }
}

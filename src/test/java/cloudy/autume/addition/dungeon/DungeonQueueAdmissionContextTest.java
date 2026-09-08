package cloudy.autume.addition.dungeon;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DungeonQueueAdmissionContextTest {
    private static final DungeonFloor F7 = new DungeonFloor("F7");

    @Test
    void ownListingIsAllowedInHubAndWhileMatchingQueueIsVisible() {
        DungeonQueueAdmissionContext context = new DungeonQueueAdmissionContext();

        context.update(List.of("SKYBLOCK", "Dungeon Hub"));
        assertTrue(context.allows(F7));
        long hubGeneration = context.generation();

        context.update(List.of("SKYBLOCK", "Queued: The Catacombs", "Tier: Floor VII"));
        assertTrue(context.allows(F7));
        assertTrue(context.unchanged(hubGeneration));
        assertFalse(context.allows(new DungeonFloor("F6")));

        context.update(List.of("SKYBLOCK", "Queued: The Catacombs"));
        assertTrue(context.allows(F7));
        context.update(List.of("SKYBLOCK", "Queued: Master Mode The Catacombs"));
        assertFalse(context.allows(F7));
    }

    @Test
    void queueDeparturePermanentlyInvalidatesTheOldAdmissionContext() {
        DungeonQueueAdmissionContext context = new DungeonQueueAdmissionContext();
        context.update(List.of("SKYBLOCK", "Queued: The Catacombs", "Tier: Floor VII"));
        long queuedGeneration = context.generation();

        context.update(List.of("SKYBLOCK", "Dungeon Hub"));

        assertFalse(context.allows(F7));
        assertNotEquals(queuedGeneration, context.generation());
    }

    @Test
    void aFreshLiveObservationOverridesAnEarlierCachedAllowDecision() {
        DungeonQueueAdmissionContext context = new DungeonQueueAdmissionContext();
        context.update(List.of("SKYBLOCK", "Dungeon Hub"));
        long capturedGeneration = context.generation();
        assertTrue(context.allows(F7));

        // This models the final guard reading vanilla's live scoreboard before
        // the slower periodic cache has refreshed.
        context.update(List.of("SKYBLOCK", "The Catacombs (F7)", "Cleared: 0%"));

        assertFalse(context.unchanged(capturedGeneration));
        assertFalse(context.allows(F7));
    }

    @Test
    void activeDungeonAndPartialScoreboardsNeverAuthorizeAKick() {
        DungeonQueueAdmissionContext context = new DungeonQueueAdmissionContext();
        context.update(List.of("SKYBLOCK", "Dungeon Hub"));
        long hubGeneration = context.generation();

        context.update(List.of("SKYBLOCK", "The Catacombs (F7)", "Cleared: 0%"));
        assertFalse(context.allows(F7));
        assertNotEquals(hubGeneration, context.generation());

        context.reset();
        context.update(List.of());
        assertFalse(context.allows(F7));
    }

    @Test
    void activeDungeonEvidenceWinsDuringAMixedScoreboardTransition() {
        DungeonQueueAdmissionContext context = new DungeonQueueAdmissionContext();
        context.update(List.of("Queued: The Catacombs", "Tier: Floor VII"));
        long queuedGeneration = context.generation();

        context.update(List.of("Queued: The Catacombs", "Tier: Floor VII",
                "The Catacombs (F7)", "Cleared: 0%"));

        assertFalse(context.allows(F7));
        assertNotEquals(queuedGeneration, context.generation());
    }

    @Test
    void aNewVisibleQueueStartsANewSafeContextAfterDeparture() {
        DungeonQueueAdmissionContext context = new DungeonQueueAdmissionContext();
        context.update(List.of("Queued: The Catacombs", "Tier: Floor VII"));
        context.update(List.of("The Catacombs (F7)", "Cleared: 0%"));
        long departedGeneration = context.generation();

        context.update(List.of("Queued: The Catacombs", "Tier: Floor VII"));

        assertTrue(context.allows(F7));
        assertNotEquals(departedGeneration, context.generation());
    }
}

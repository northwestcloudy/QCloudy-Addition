package cloudy.autume.addition.dungeon;

import java.util.Optional;

/**
 * Fail-closed scoreboard context for automatic Party Finder admission actions.
 * A cached GUI listing alone is not enough after the queue visibly disappears
 * or an active Catacombs scoreboard appears.
 */
final class DungeonQueueAdmissionContext {
    private Phase phase = Phase.PARTIAL;
    private DungeonFloor currentFloor;
    private long generation;

    void update(Iterable<String> lines) {
        DungeonFloor.Observation observation = DungeonFloor.observe(lines);
        Phase previous = phase;

        // During a scoreboard transition old queue rows and new in-run rows can
        // coexist briefly. Strong active-dungeon evidence always wins.
        if (observation.activeDungeon()) {
            currentFloor = null;
            if (previous != Phase.ACTIVE_OR_DEPARTED) generation++;
            phase = Phase.ACTIVE_OR_DEPARTED;
            return;
        }
        if (observation.queued()) {
            if (previous == Phase.ACTIVE_OR_DEPARTED) generation++;
            phase = Phase.QUEUED;
            Optional<DungeonFloor> retained = DungeonFloor.retainWhileQueued(currentFloor, lines);
            currentFloor = retained.orElse(null);
            return;
        }

        currentFloor = null;
        if (!observation.meaningful()) {
            // A temporarily empty scoreboard cannot authorize an action, but it
            // also cannot by itself prove that the queue lifecycle ended.
            phase = previous == Phase.ACTIVE_OR_DEPARTED
                    ? Phase.ACTIVE_OR_DEPARTED : Phase.PARTIAL;
            return;
        }
        if (previous == Phase.QUEUED) {
            generation++;
            phase = Phase.ACTIVE_OR_DEPARTED;
            return;
        }
        phase = previous == Phase.ACTIVE_OR_DEPARTED
                ? Phase.ACTIVE_OR_DEPARTED : Phase.HUB;
    }

    boolean allows(DungeonFloor listingFloor) {
        if (listingFloor == null || phase == Phase.PARTIAL
                || phase == Phase.ACTIVE_OR_DEPARTED) return false;
        if (phase == Phase.QUEUED) {
            return currentFloor != null && currentFloor.equals(listingFloor);
        }
        return true;
    }

    boolean unchanged(long expectedGeneration) {
        return generation == expectedGeneration;
    }

    long generation() {
        return generation;
    }

    void reset() {
        generation++;
        currentFloor = null;
        phase = Phase.PARTIAL;
    }

    DungeonFloor currentFloor() {
        return currentFloor;
    }

    private enum Phase {
        PARTIAL,
        HUB,
        QUEUED,
        ACTIVE_OR_DEPARTED
    }
}

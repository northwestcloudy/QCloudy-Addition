package cloudy.autume.addition.fishing;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Associates ownerless Hypixel lava hooks with a recent physical local cast. */
final class FishingHookResolver {
    static final int NO_HOOK = Integer.MIN_VALUE;

    private final int associationTicks;
    private final Set<Integer> hooksBeforeCast = new HashSet<>();
    private int remainingAssociationTicks;
    private int fallbackHookId = NO_HOOK;

    FishingHookResolver(int associationTicks) {
        this.associationTicks = associationTicks;
    }

    boolean onRodUse(Set<Integer> visibleHookIds, boolean directHookPresent) {
        if (directHookPresent || visibleHookIds.contains(fallbackHookId)) {
            reset();
            return false;
        }
        hooksBeforeCast.clear();
        hooksBeforeCast.addAll(visibleHookIds);
        fallbackHookId = NO_HOOK;
        remainingAssociationTicks = associationTicks;
        return true;
    }

    int resolve(int directHookId, List<Candidate> candidates) {
        return resolve(directHookId, candidates, true);
    }

    /** Resolves from an event without consuming the tick-based association window on a miss. */
    int observe(int directHookId, List<Candidate> candidates) {
        return resolve(directHookId, candidates, false);
    }

    private int resolve(int directHookId, List<Candidate> candidates, boolean advanceWindow) {
        if (directHookId != NO_HOOK) {
            fallbackHookId = NO_HOOK;
            remainingAssociationTicks = 0;
            return directHookId;
        }

        if (fallbackHookId != NO_HOOK) {
            boolean stillLoaded = candidates.stream().anyMatch(candidate -> candidate.id() == fallbackHookId);
            if (stillLoaded) return fallbackHookId;
            fallbackHookId = NO_HOOK;
        }

        if (remainingAssociationTicks <= 0) return NO_HOOK;
        int resolved = candidates.stream()
                .filter(candidate -> !hooksBeforeCast.contains(candidate.id()))
                .filter(candidate -> candidate.ownership() != Ownership.OTHER_PLAYER)
                .min(Comparator.comparingInt((Candidate candidate) -> candidate.ownership().priority)
                        .thenComparingDouble(Candidate::distanceSquared)
                        .thenComparingInt(Candidate::id))
                .map(Candidate::id)
                .orElse(NO_HOOK);
        if (resolved != NO_HOOK) {
            fallbackHookId = resolved;
            remainingAssociationTicks = 0;
            return resolved;
        }

        if (advanceWindow) remainingAssociationTicks--;
        return NO_HOOK;
    }

    boolean needsCandidates() {
        return fallbackHookId != NO_HOOK || remainingAssociationTicks > 0;
    }

    void reset() {
        hooksBeforeCast.clear();
        remainingAssociationTicks = 0;
        fallbackHookId = NO_HOOK;
    }

    enum Ownership {
        LOCAL_PLAYER(0),
        UNKNOWN(1),
        OTHER_PLAYER(2);

        private final int priority;

        Ownership(int priority) {
            this.priority = priority;
        }
    }

    record Candidate(int id, Ownership ownership, double distanceSquared) {
    }
}

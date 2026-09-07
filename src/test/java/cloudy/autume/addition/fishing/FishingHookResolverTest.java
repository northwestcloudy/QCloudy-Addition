package cloudy.autume.addition.fishing;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static cloudy.autume.addition.fishing.FishingHookResolver.NO_HOOK;
import static cloudy.autume.addition.fishing.FishingHookResolver.Ownership.LOCAL_PLAYER;
import static cloudy.autume.addition.fishing.FishingHookResolver.Ownership.OTHER_PLAYER;
import static cloudy.autume.addition.fishing.FishingHookResolver.Ownership.UNKNOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FishingHookResolverTest {
    @Test
    void directWaterHookAlwaysWins() {
        FishingHookResolver resolver = new FishingHookResolver(40);
        assertTrue(resolver.onRodUse(Set.of(), false));

        assertEquals(12, resolver.resolve(12, List.of(candidate(21, UNKNOWN, 1.0))));
    }

    @Test
    void newOwnerlessLavaHookIsAssociatedAfterTheLocalCast() {
        FishingHookResolver resolver = new FishingHookResolver(40);
        assertFalse(resolver.needsCandidates());
        assertTrue(resolver.onRodUse(Set.of(30, 31), false));
        assertTrue(resolver.needsCandidates());

        int hook = resolver.resolve(NO_HOOK, List.of(
                candidate(30, UNKNOWN, 1.0),
                candidate(32, OTHER_PLAYER, 0.5),
                candidate(33, UNKNOWN, 4.0)));

        assertEquals(33, hook);
        assertTrue(resolver.needsCandidates());
        assertEquals(33, resolver.resolve(NO_HOOK, List.of(candidate(33, UNKNOWN, 4.0))));
    }

    @Test
    void locallyOwnedCandidateBeatsAnUnknownCandidate() {
        FishingHookResolver resolver = new FishingHookResolver(40);
        assertTrue(resolver.onRodUse(Set.of(), false));

        assertEquals(42, resolver.resolve(NO_HOOK, List.of(
                candidate(41, UNKNOWN, 1.0),
                candidate(42, LOCAL_PLAYER, 16.0))));
    }

    @Test
    void secondPhysicalUseReelsTheTrackedFallbackInsteadOfArmingAnotherCast() {
        FishingHookResolver resolver = new FishingHookResolver(40);
        assertTrue(resolver.onRodUse(Set.of(), false));
        assertEquals(51, resolver.resolve(NO_HOOK, List.of(candidate(51, UNKNOWN, 2.0))));

        assertFalse(resolver.onRodUse(Set.of(51), false));

        assertFalse(resolver.needsCandidates());
        assertEquals(NO_HOOK, resolver.resolve(NO_HOOK, List.of(candidate(52, UNKNOWN, 1.0))));
    }

    @Test
    void associationWindowExpiresWithoutClaimingOldOrOtherPlayerHooks() {
        FishingHookResolver resolver = new FishingHookResolver(2);
        assertTrue(resolver.onRodUse(Set.of(61), false));

        assertEquals(NO_HOOK, resolver.resolve(NO_HOOK, List.of(
                candidate(61, UNKNOWN, 1.0), candidate(62, OTHER_PLAYER, 1.0))));
        assertEquals(NO_HOOK, resolver.resolve(NO_HOOK, List.of()));
        assertEquals(NO_HOOK, resolver.resolve(NO_HOOK, List.of(candidate(63, UNKNOWN, 1.0))));
    }

    @Test
    void directHookUseIsClassifiedAsReeling() {
        FishingHookResolver resolver = new FishingHookResolver(40);

        assertFalse(resolver.onRodUse(Set.of(71), true));
        assertFalse(resolver.needsCandidates());
    }

    @Test
    void metadataObservationDoesNotConsumeTheTickBasedAssociationWindow() {
        FishingHookResolver resolver = new FishingHookResolver(2);
        assertTrue(resolver.onRodUse(Set.of(), false));

        assertEquals(NO_HOOK, resolver.observe(NO_HOOK, List.of()));
        assertEquals(NO_HOOK, resolver.observe(NO_HOOK, List.of()));
        assertEquals(81, resolver.resolve(NO_HOOK, List.of(candidate(81, UNKNOWN, 1.0))));
    }

    @Test
    void metadataObservationCanAssociateANewOwnerlessHookImmediately() {
        FishingHookResolver resolver = new FishingHookResolver(40);
        assertTrue(resolver.onRodUse(Set.of(90), false));

        assertEquals(91, resolver.observe(NO_HOOK, List.of(
                candidate(90, UNKNOWN, 0.5), candidate(91, UNKNOWN, 1.0))));
        assertEquals(91, resolver.resolve(NO_HOOK, List.of(candidate(91, UNKNOWN, 1.0))));
    }

    private static FishingHookResolver.Candidate candidate(
            int id, FishingHookResolver.Ownership ownership, double distanceSquared) {
        return new FishingHookResolver.Candidate(id, ownership, distanceSquared);
    }
}

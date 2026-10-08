package cloudy.autume.addition.hunting;

import cloudy.autume.addition.tracker.PetTier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SafariEagleParserTest {
    @AfterEach
    void reset() {
        SafariEagleTracker.reset();
    }

    @Test
    void readsOnlyExplicitCurrentShopTierAndUnlockState() {
        SafariEagleState state = SafariEagleParser.safariEssenceShop(List.of(
                "Eagle Pet",
                "Current Tier: LEGENDARY",
                "Next upgrade: MYTHIC",
                "Click to upgrade!"));

        assertEquals(SafariEagleState.UnlockStatus.UNLOCKED, state.unlockStatus());
        assertEquals(SafariEagleState.Presence.UNKNOWN, state.presence());
        assertEquals(PetTier.LEGENDARY, state.tier());
        assertTrue(state.evidence().contains(SafariEagleState.Evidence.SAFARI_ESSENCE_SHOP));
    }

    @Test
    void lockedShopEntryDoesNotPretendTheEagleIsPresent() {
        SafariEagleState state = SafariEagleParser.safariEssenceShop(List.of(
                "Eagle Pet", "LOCKED", "Requires 5,000 Safari Essence"));

        assertEquals(SafariEagleState.UnlockStatus.LOCKED, state.unlockStatus());
        assertEquals(SafariEagleState.Presence.ABSENT, state.presence());
        assertEquals(PetTier.UNKNOWN, state.tier());
    }

    @Test
    void currentSafariWidgetConfirmsPresenceWithoutGuessingTier() {
        SafariEagleState state = SafariEagleParser.safariWidget(List.of(
                "Critter Safari", "Eagle Pet: Active"));

        assertEquals(SafariEagleState.UnlockStatus.UNLOCKED, state.unlockStatus());
        assertEquals(SafariEagleState.Presence.PRESENT, state.presence());
        assertEquals(PetTier.UNKNOWN, state.tier());
    }

    @Test
    void rejectsNearbyEagleUntilTheEntityIsBoundToTheLocalPlayer() {
        SafariEagleState nearby = SafariEagleParser.ownerBoundEntity(
                "[LEGENDARY] Eagle Pet", false);
        assertFalse(nearby.observed());

        SafariEagleState owned = SafariEagleParser.ownerBoundEntity(
                "[LEGENDARY] Eagle Pet", true);
        assertTrue(owned.observed());
        assertEquals(SafariEagleState.Presence.PRESENT, owned.presence());
        assertEquals(PetTier.LEGENDARY, owned.tier());
    }

    @Test
    void trackerResetPreventsWorldOrProfileStateLeakage() {
        SafariEagleTracker.observeSafariWidget(List.of("Eagle Pet: MYTHIC"));
        assertEquals(SafariEagleState.Presence.PRESENT,
                SafariEagleTracker.current().presence());

        SafariEagleTracker.reset();
        assertEquals(SafariEagleState.EMPTY, SafariEagleTracker.current());
    }
}

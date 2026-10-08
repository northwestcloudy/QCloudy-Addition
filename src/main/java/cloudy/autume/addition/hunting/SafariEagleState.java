package cloudy.autume.addition.hunting;

import cloudy.autume.addition.tracker.PetTier;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Evidence-bounded state for the separate Critter Safari Eagle companion. */
public record SafariEagleState(UnlockStatus unlockStatus, Presence presence, PetTier tier,
                               Set<Evidence> evidence) {
    public static final SafariEagleState EMPTY = new SafariEagleState(
            UnlockStatus.UNKNOWN, Presence.UNKNOWN, PetTier.UNKNOWN, Set.of());

    public SafariEagleState {
        unlockStatus = unlockStatus == null ? UnlockStatus.UNKNOWN : unlockStatus;
        presence = presence == null ? Presence.UNKNOWN : presence;
        tier = tier == null ? PetTier.UNKNOWN : tier;
        if (evidence == null || evidence.isEmpty()) {
            evidence = Set.of();
        } else {
            EnumSet<Evidence> copy = EnumSet.noneOf(Evidence.class);
            copy.addAll(evidence);
            evidence = Collections.unmodifiableSet(copy);
        }
    }

    public boolean observed() {
        return !evidence.isEmpty();
    }

    SafariEagleState merge(SafariEagleState newer) {
        if (newer == null || !newer.observed()) return this;
        UnlockStatus mergedUnlock = newer.unlockStatus != UnlockStatus.UNKNOWN
                ? newer.unlockStatus : unlockStatus;
        Presence mergedPresence = newer.presence != Presence.UNKNOWN ? newer.presence : presence;
        PetTier mergedTier = newer.tier != PetTier.UNKNOWN ? newer.tier : tier;
        if (mergedUnlock == UnlockStatus.LOCKED) {
            mergedPresence = Presence.ABSENT;
            mergedTier = PetTier.UNKNOWN;
        }
        EnumSet<Evidence> mergedEvidence = EnumSet.noneOf(Evidence.class);
        mergedEvidence.addAll(evidence);
        mergedEvidence.addAll(newer.evidence);
        return new SafariEagleState(mergedUnlock, mergedPresence, mergedTier, mergedEvidence);
    }

    public enum UnlockStatus {
        UNKNOWN,
        LOCKED,
        UNLOCKED
    }

    public enum Presence {
        UNKNOWN,
        ABSENT,
        PRESENT
    }

    public enum Evidence {
        SAFARI_ESSENCE_SHOP,
        SAFARI_WIDGET,
        OWNER_BOUND_ENTITY
    }
}

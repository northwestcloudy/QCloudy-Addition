package cloudy.autume.addition.dungeon.requirements;

import static cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidenceValue.DecimalValue;
import static cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidenceValue.DuplicateClass;
import static cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidenceValue.LongValue;
import static cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidenceValue.PresenceEvidence;

/** Immutable evidence snapshot corresponding to one floor and one admission attempt. */
public record DungeonRequirementEvidence(
        DungeonFloorKey floor,
        LongValue floorCompletions,
        DuplicateClass duplicateClass,
        LongValue fastestCompletionMs,
        DecimalValue averageSecrets,
        LongValue magicalPower,
        PresenceEvidence witherBlade,
        PresenceEvidence terminator,
        PresenceEvidence goldenDragon,
        PresenceEvidence enderDragon) {

    public DungeonRequirementEvidence {
        if (floor == null) throw new IllegalArgumentException("Floor is required");
        floorCompletions = floorCompletions == null
                ? LongValue.unknown("Floor completions unavailable") : floorCompletions;
        duplicateClass = duplicateClass == null
                ? DuplicateClass.unknown(null, null, "Party classes unavailable") : duplicateClass;
        fastestCompletionMs = fastestCompletionMs == null
                ? LongValue.unknown("Fastest completion unavailable") : fastestCompletionMs;
        averageSecrets = averageSecrets == null
                ? DecimalValue.unknown("Average secrets unavailable") : averageSecrets;
        magicalPower = magicalPower == null
                ? LongValue.unknown("Magical Power unavailable") : magicalPower;
        witherBlade = witherBlade == null
                ? PresenceEvidence.unknown("Wither Blade ownership unavailable") : witherBlade;
        terminator = terminator == null
                ? PresenceEvidence.unknown("Terminator ownership unavailable") : terminator;
        goldenDragon = goldenDragon == null
                ? PresenceEvidence.unknown("Golden Dragon ownership unavailable") : goldenDragon;
        enderDragon = enderDragon == null
                ? PresenceEvidence.unknown("Ender Dragon ownership unavailable") : enderDragon;
    }

    public static DungeonRequirementEvidence unavailable(DungeonFloorKey floor, String reason) {
        return new DungeonRequirementEvidence(floor,
                LongValue.unknown(reason),
                DuplicateClass.unknown(null, null, reason),
                LongValue.unknown(reason),
                DecimalValue.unknown(reason),
                LongValue.unknown(reason),
                PresenceEvidence.unknown(reason), PresenceEvidence.unknown(reason),
                PresenceEvidence.unknown(reason), PresenceEvidence.unknown(reason));
    }
}

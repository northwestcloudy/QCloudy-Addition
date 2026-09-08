package cloudy.autume.addition.dungeon.requirements;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidenceValue.DecimalValue;
import static cloudy.autume.addition.dungeon.requirements.DungeonClassKey.ARCHER;
import static cloudy.autume.addition.dungeon.requirements.DungeonClassKey.MAGE;
import static cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidenceValue.DuplicateClass;
import static cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidenceValue.LongValue;
import static cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidenceValue.PartyMemberClass;
import static cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidenceValue.PresenceEvidence;
import static cloudy.autume.addition.dungeon.requirements.DungeonRequirementPolicy.DecimalRule;
import static cloudy.autume.addition.dungeon.requirements.DungeonRequirementPolicy.LongRule;
import static cloudy.autume.addition.dungeon.requirements.RequirementStatus.FAIL;
import static cloudy.autume.addition.dungeon.requirements.RequirementStatus.PASS;
import static cloudy.autume.addition.dungeon.requirements.RequirementStatus.UNKNOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DungeonRequirementEvaluatorTest {
    @Test
    void allNineRulesPassAtTheirInclusiveBoundaries() {
        DungeonRequirementEvaluation result = DungeonRequirementEvaluator.evaluate(
                allEnabled(DungeonFloorKey.F7),
                evidence(DungeonFloorKey.F7,
                        LongValue.known(50),
                        DuplicateClass.known(ARCHER, List.of(new PartyMemberClass("MagePlayer", MAGE))),
                        LongValue.known(450_000),
                        DecimalValue.known(8.0),
                        LongValue.known(1_400),
                        PresenceEvidence.present(), PresenceEvidence.present(),
                        PresenceEvidence.present(), PresenceEvidence.present()));

        assertEquals(PASS, result.status());
        assertTrue(result.passed());
        assertEquals(List.of(DungeonRequirement.values()),
                result.findings().stream().map(RequirementFinding::requirement).toList());
        assertEquals(9, result.findings().size());
        assertTrue(result.failures().isEmpty());
        assertTrue(result.unknowns().isEmpty());
    }

    @Test
    void confirmedFailuresAreCompleteAndStayInCanonicalOrder() {
        DungeonRequirementEvaluation result = DungeonRequirementEvaluator.evaluate(
                allEnabled(DungeonFloorKey.M7),
                evidence(DungeonFloorKey.M7,
                        LongValue.known(49),
                        DuplicateClass.known(ARCHER, List.of(
                                new PartyMemberClass("Zulu", ARCHER),
                                new PartyMemberClass("alpha", ARCHER),
                                new PartyMemberClass("ALPHA", ARCHER))),
                        LongValue.known(450_001),
                        DecimalValue.known(7.999),
                        LongValue.known(1_399),
                        PresenceEvidence.confirmedAbsent(), PresenceEvidence.confirmedAbsent(),
                        PresenceEvidence.confirmedAbsent(), PresenceEvidence.confirmedAbsent()));

        assertEquals(FAIL, result.status());
        assertFalse(result.passed());
        assertEquals(List.of(DungeonRequirement.values()),
                result.failures().stream().map(RequirementFinding::requirement).toList());
        DuplicateClass dupe = (DuplicateClass) result.failures().get(1).evidence();
        assertEquals(List.of("alpha", "Zulu"), dupe.conflictingPlayers());
    }

    @Test
    void averageSecretsComparisonDoesNotRoundCloseDecimalValuesTogether() {
        DungeonRequirementPolicy policy = new DungeonRequirementPolicy(DungeonFloorKey.F7,
                LongRule.disabled(0), false,
                LongRule.disabled(0), DecimalRule.enabled(8.04),
                LongRule.disabled(0), false, false, false, false);
        DungeonRequirementEvidence base = DungeonRequirementEvidence.unavailable(
                DungeonFloorKey.F7, "Unused disabled evidence");
        DungeonRequirementEvidence below = new DungeonRequirementEvidence(
                DungeonFloorKey.F7,
                base.floorCompletions(), base.duplicateClass(), base.fastestCompletionMs(),
                DecimalValue.known(8.03), base.magicalPower(), base.witherBlade(),
                base.terminator(), base.goldenDragon(), base.enderDragon());
        DungeonRequirementEvidence equal = new DungeonRequirementEvidence(
                DungeonFloorKey.F7,
                base.floorCompletions(), base.duplicateClass(), base.fastestCompletionMs(),
                DecimalValue.known(8.04), base.magicalPower(), base.witherBlade(),
                base.terminator(), base.goldenDragon(), base.enderDragon());

        DungeonRequirementEvaluation failed = DungeonRequirementEvaluator.evaluate(policy, below);
        DungeonRequirementEvaluation passed = DungeonRequirementEvaluator.evaluate(policy, equal);

        assertEquals(FAIL, failed.status());
        assertEquals(8.03, ((DecimalValue) failed.failures().getFirst().evidence()).value());
        assertEquals(8.04,
                ((DungeonRequirementCriterion.DecimalThreshold)
                        failed.failures().getFirst().criterion()).value());
        assertEquals(PASS, passed.status());
    }

    @Test
    void unknownEvidenceNeverTurnsIntoZeroOrConfirmedAbsence() {
        DungeonRequirementEvidence unavailable = DungeonRequirementEvidence.unavailable(
                DungeonFloorKey.F3, "Profile data is stale");
        DungeonRequirementEvaluation result = DungeonRequirementEvaluator.evaluate(
                allEnabled(DungeonFloorKey.F3), unavailable);

        assertEquals(UNKNOWN, result.status());
        assertTrue(result.failures().isEmpty());
        assertEquals(List.of(DungeonRequirement.values()),
                result.unknowns().stream().map(RequirementFinding::requirement).toList());
        assertTrue(result.unknowns().stream()
                .allMatch(finding -> finding.unavailableReason().equals("Profile data is stale")));
    }

    @Test
    void oneConfirmedFailureWinsEvenWhenEveryOtherRuleIsUnknown() {
        DungeonRequirementEvidence base = DungeonRequirementEvidence.unavailable(
                DungeonFloorKey.F1, "Unavailable");
        DungeonRequirementEvidence mixed = new DungeonRequirementEvidence(DungeonFloorKey.F1,
                LongValue.known(0), base.duplicateClass(), base.fastestCompletionMs(),
                base.averageSecrets(), base.magicalPower(), base.witherBlade(),
                base.terminator(), base.goldenDragon(), base.enderDragon());

        DungeonRequirementEvaluation result = DungeonRequirementEvaluator.evaluate(
                allEnabled(DungeonFloorKey.F1), mixed);

        assertEquals(FAIL, result.status());
        assertEquals(List.of(DungeonRequirement.MINIMUM_FLOOR_COMPLETIONS),
                result.failures().stream().map(RequirementFinding::requirement).toList());
        assertEquals(8, result.unknowns().size());
    }

    @Test
    void incompleteRosterIsUnknownUnlessADuplicateIsAlreadyConfirmed() {
        DungeonRequirementPolicy policy = dupeOnly(DungeonFloorKey.M4);
        DungeonRequirementEvidence noDuplicate = dupeEvidence(DungeonFloorKey.M4,
                DuplicateClass.unknown(ARCHER,
                        List.of(new PartyMemberClass("MagePlayer", MAGE)), "One class is missing"));
        DungeonRequirementEvidence duplicate = dupeEvidence(DungeonFloorKey.M4,
                DuplicateClass.unknown(ARCHER,
                        List.of(new PartyMemberClass("ArcherPlayer", ARCHER)), "Roster incomplete"));
        DungeonRequirementEvidence newcomerUnknown = dupeEvidence(DungeonFloorKey.M4,
                DuplicateClass.unknown(null,
                        List.of(new PartyMemberClass("ArcherPlayer", ARCHER)), "Newcomer class missing"));

        assertEquals(UNKNOWN, DungeonRequirementEvaluator.evaluate(policy, noDuplicate).status());
        assertEquals(FAIL, DungeonRequirementEvaluator.evaluate(policy, duplicate).status());
        assertEquals(UNKNOWN, DungeonRequirementEvaluator.evaluate(policy, newcomerUnknown).status());
    }

    @Test
    void completeRosterWithAnUnknownMemberClassStillCannotPassDupe() {
        DuplicateClass contradictory = new DuplicateClass(ARCHER,
                List.of(new PartyMemberClass("UnknownClass", null)), true, "Member class missing");
        DungeonRequirementEvaluation result = DungeonRequirementEvaluator.evaluate(
                dupeOnly(DungeonFloorKey.F5), dupeEvidence(DungeonFloorKey.F5, contradictory));

        assertEquals(UNKNOWN, result.status());
        assertEquals("Member class missing", result.unknowns().getFirst().unavailableReason());
    }

    @Test
    void disabledRulesProduceNoFindingsAndRetainTheirValues() {
        DungeonRequirementPolicy policy = new DungeonRequirementPolicy(DungeonFloorKey.M1,
                LongRule.disabled(123), false,
                LongRule.disabled(456_000), DecimalRule.disabled(9.5),
                LongRule.disabled(1_234), false, false, false, false);

        DungeonRequirementEvaluation result = DungeonRequirementEvaluator.evaluate(policy,
                DungeonRequirementEvidence.unavailable(DungeonFloorKey.M1, "Missing"));

        assertEquals(PASS, result.status());
        assertTrue(result.findings().isEmpty());
        assertFalse(policy.hasEnabledRules());
        assertEquals(123, policy.minimumFloorCompletions().value());
        assertEquals(9.5, policy.minimumAverageSecrets().value());
    }

    @Test
    void rejectsCrossFloorEvaluationAndInvalidNumbers() {
        assertThrows(IllegalArgumentException.class, () -> DungeonRequirementEvaluator.evaluate(
                allEnabled(DungeonFloorKey.F7),
                DungeonRequirementEvidence.unavailable(DungeonFloorKey.M7, "Missing")));
        assertThrows(IllegalArgumentException.class, () -> new LongRule(true, -1));
        assertThrows(IllegalArgumentException.class, () -> new DecimalRule(true, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> DecimalValue.known(Double.POSITIVE_INFINITY));
    }

    @Test
    void modelsOnlyFourteenIndependentFloorsAndNeverEntrance() {
        assertEquals(14, DungeonFloorKey.values().length);
        assertEquals(DungeonFloorKey.F1, DungeonFloorKey.parse(" f1 ").orElseThrow());
        assertEquals(DungeonFloorKey.M7, DungeonFloorKey.parse("m7").orElseThrow());
        assertTrue(DungeonFloorKey.parse("E").isEmpty());
        assertTrue(DungeonFloorKey.parse("Entrance").isEmpty());
        assertTrue(DungeonFloorKey.parse("F8").isEmpty());
        assertTrue(DungeonFloorKey.parse(null).isEmpty());
    }

    @Test
    void constructorsDefensivelyCopyPartyEvidence() {
        List<PartyMemberClass> members = new ArrayList<>();
        members.add(new PartyMemberClass("First", MAGE));
        DuplicateClass evidence = DuplicateClass.known(ARCHER, members);
        members.add(new PartyMemberClass("LateMutation", ARCHER));

        assertTrue(evidence.conflictingPlayers().isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> evidence.existingMembers().add(new PartyMemberClass("Other", MAGE)));
    }

    private static DungeonRequirementPolicy allEnabled(DungeonFloorKey floor) {
        return new DungeonRequirementPolicy(floor,
                LongRule.enabled(50), true,
                LongRule.enabled(450_000), DecimalRule.enabled(8.0),
                LongRule.enabled(1_400), true, true, true, true);
    }

    private static DungeonRequirementPolicy dupeOnly(DungeonFloorKey floor) {
        return new DungeonRequirementPolicy(floor,
                LongRule.disabled(0), true,
                LongRule.disabled(0), DecimalRule.disabled(0),
                LongRule.disabled(0), false, false, false, false);
    }

    private static DungeonRequirementEvidence evidence(
            DungeonFloorKey floor,
            LongValue runs,
            DuplicateClass dupe,
            LongValue fastest,
            DecimalValue secrets,
            LongValue magicalPower,
            PresenceEvidence witherBlade,
            PresenceEvidence terminator,
            PresenceEvidence goldenDragon,
            PresenceEvidence enderDragon) {
        return new DungeonRequirementEvidence(floor, runs, dupe, fastest, secrets,
                magicalPower, witherBlade, terminator, goldenDragon, enderDragon);
    }

    private static DungeonRequirementEvidence dupeEvidence(
            DungeonFloorKey floor, DuplicateClass dupe) {
        DungeonRequirementEvidence base = DungeonRequirementEvidence.unavailable(floor, "Unused");
        return new DungeonRequirementEvidence(floor, base.floorCompletions(), dupe,
                base.fastestCompletionMs(), base.averageSecrets(), base.magicalPower(),
                base.witherBlade(), base.terminator(), base.goldenDragon(), base.enderDragon());
    }
}

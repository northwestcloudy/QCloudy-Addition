package cloudy.autume.addition.dungeon;

import cloudy.autume.addition.config.ConfigManager;
import cloudy.autume.addition.dungeon.requirements.DungeonFloorKey;
import cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvaluation;
import cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvaluator;
import cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidence;
import cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidenceValue.DecimalValue;
import cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidenceValue.DuplicateClass;
import cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidenceValue.LongValue;
import cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidenceValue.PartyMemberClass;
import cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidenceValue.PresenceEvidence;
import cloudy.autume.addition.dungeon.requirements.DungeonRequirementPolicy;
import cloudy.autume.addition.dungeon.requirements.DungeonRequirementPolicy.DecimalRule;
import cloudy.autume.addition.dungeon.requirements.DungeonRequirementPolicy.LongRule;
import cloudy.autume.addition.dungeon.requirements.RequirementStatus;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static cloudy.autume.addition.dungeon.requirements.DungeonClassKey.ARCHER;
import static cloudy.autume.addition.dungeon.requirements.DungeonClassKey.MAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DungeonQuickViewMessageRequirementsTest {
    private String previousLanguage;

    @BeforeEach
    void useStableEnglishMessages() {
        previousLanguage = ConfigManager.get().language;
        ConfigManager.get().language = "en_us";
    }

    @AfterEach
    void restoreLanguage() {
        ConfigManager.get().language = previousLanguage;
    }

    @Test
    void failureCardListsEveryConfirmedReasonInRuleOrderAndDoesNotRenderProfile() {
        DungeonRequirementPolicy policy = allEnabled(DungeonFloorKey.M7);
        DungeonRequirementEvidence evidence = new DungeonRequirementEvidence(
                DungeonFloorKey.M7,
                LongValue.known(312),
                DuplicateClass.known(ARCHER,
                        List.of(new PartyMemberClass("ExistingArcher", ARCHER))),
                LongValue.known(298_321),
                DecimalValue.known(8.0),
                LongValue.known(1_330),
                PresenceEvidence.confirmedAbsent(), PresenceEvidence.confirmedAbsent(),
                PresenceEvidence.confirmedAbsent(), PresenceEvidence.confirmedAbsent());
        DungeonRequirementEvaluation evaluation =
                DungeonRequirementEvaluator.evaluate(policy, evidence, ARCHER);

        Component message = DungeonQuickViewMessage.failures("GhostsTM", "M7", evaluation);
        String text = message.getString();

        assertEquals(RequirementStatus.FAIL, evaluation.status());
        assertEquals(9, evaluation.failures().size());
        assertTrue(text.startsWith("[QCA] GhostsTM does not meet the M7 requirements:"));
        assertInOrder(text,
                "M7 Completions: 312 / Required ≥ 400",
                "Duplicate Class: Archer is already used by ExistingArcher",
                "Fastest Completion: 04:58.321 / Required ≤ 04:10",
                "Average Secrets: 8.0 / Required ≥ 12.0",
                "Highest Magical Power: 1,330 / Required ≥ 1,400",
                "Wither Blade: Not owned / Required: Owned",
                "Terminator: Not owned / Required: Owned",
                "Golden Dragon: Not owned / Required: Owned",
                "Ender Dragon: Not owned / Required: Owned");
        assertFalse(text.contains("QCA Player Quick View"));
        assertFalse(text.contains("Catacombs:"));
        assertFalse(text.contains("CLICK HERE TO KICK"));
        assertTrue(flatten(message).stream()
                .noneMatch(part -> part.getStyle().getClickEvent() != null));
        assertRemovedStatusLinesAreAbsent(text);
    }

    @Test
    void averageSecretsFailureShowsDistinctUnroundedActualAndThresholdValues() {
        DungeonRequirementPolicy policy = new DungeonRequirementPolicy(DungeonFloorKey.F7,
                LongRule.disabled(0), false,
                LongRule.disabled(0), DecimalRule.enabled(8.04),
                LongRule.disabled(0), false, false, false, false);
        DungeonRequirementEvidence base = DungeonRequirementEvidence.unavailable(
                DungeonFloorKey.F7, "Unused disabled evidence");
        DungeonRequirementEvidence evidence = new DungeonRequirementEvidence(
                DungeonFloorKey.F7,
                base.floorCompletions(), base.duplicateClass(), base.fastestCompletionMs(),
                DecimalValue.known(8.03), base.magicalPower(), base.witherBlade(),
                base.terminator(), base.goldenDragon(), base.enderDragon());

        DungeonRequirementEvaluation evaluation =
                DungeonRequirementEvaluator.evaluate(policy, evidence, ARCHER);
        String text = DungeonQuickViewMessage.failures("GhostsTM", "F7", evaluation).getString();

        assertEquals(RequirementStatus.FAIL, evaluation.status());
        assertTrue(text.contains("Average Secrets: 8.03 / Required ≥ 8.04"));
        assertFalse(text.contains("Average Secrets: 8.0 / Required ≥ 8.0"));
    }

    @Test
    void confirmedFailureAlsoPrintsConcreteDupeAndActionErrorsBeforeAnyCommand() {
        DungeonRequirementPolicy policy = new DungeonRequirementPolicy(DungeonFloorKey.F7,
                LongRule.enabled(100), true,
                LongRule.disabled(0), DecimalRule.disabled(0),
                LongRule.disabled(0), false, false, false, false);
        DungeonRequirementEvidence base = DungeonRequirementEvidence.unavailable(
                DungeonFloorKey.F7, "Unused");
        DungeonRequirementEvidence evidence = new DungeonRequirementEvidence(
                DungeonFloorKey.F7, LongValue.known(50),
                DuplicateClass.unknown(ARCHER,
                        List.of(new PartyMemberClass("LocalPlayer", MAGE)),
                        "PARTY_ROSTER_COUNT_MISMATCH|3|1"),
                base.fastestCompletionMs(), base.averageSecrets(), base.magicalPower(),
                base.witherBlade(), base.terminator(), base.goldenDragon(), base.enderDragon());
        DungeonRequirementEvaluation evaluation = DungeonRequirementEvaluator.evaluate(
                policy, evidence, ARCHER);

        String text = DungeonQuickViewMessage.failures(
                "GhostsTM", "F7", evaluation, "LOCAL_PLAYER_NOT_PARTY_LEADER").getString();

        assertEquals(RequirementStatus.FAIL, evaluation.status());
        assertInOrder(text,
                "F7 Completions: 50 / Required ≥ 100",
                "Duplicate Class: PartyInfo has 3 existing members, but QCA has 1 class records",
                "Automatic party kick: You are no longer the party leader");
        assertFalse(text.contains("QCA Player Quick View"));
        assertRemovedStatusLinesAreAbsent(text);
    }

    @Test
    void profileAverageSecretsKeepsAllMeaningfulDecimalPlaces() {
        DungeonQuickViewSnapshot snapshot = DungeonQuickViewSnapshot.parse(
                DungeonQuickViewSnapshotTest.JSON.replace("\"averagePerRun\":11.4",
                        "\"averagePerRun\":8.0375"));

        String text = DungeonQuickViewMessage.build(snapshot, String::length,
                (item, kind) -> new HoverEvent.ShowText(Component.literal(item.name()))).getString();

        assertTrue(text.contains("Secrets: 2,432 | 8.0375"));
    }

    @Test
    void unknownCardKeepsTheFullProfileAndShowsOnlyHumanReadableUnknownReasons() {
        DungeonRequirementPolicy policy = new DungeonRequirementPolicy(DungeonFloorKey.M7,
                LongRule.disabled(0), false,
                LongRule.disabled(0), DecimalRule.enabled(8.0),
                LongRule.disabled(0), false, true, false, false);
        DungeonRequirementEvidence base = DungeonRequirementEvidence.unavailable(
                DungeonFloorKey.M7, "Unused disabled evidence");
        DungeonRequirementEvidence evidence = new DungeonRequirementEvidence(
                DungeonFloorKey.M7,
                base.floorCompletions(),
                DuplicateClass.unknown(ARCHER,
                        List.of(new PartyMemberClass("MagePlayer", MAGE)), "Unused"),
                base.fastestCompletionMs(),
                DecimalValue.unknown("SCOPE_MISMATCH"),
                base.magicalPower(),
                base.witherBlade(),
                PresenceEvidence.unknown("INVENTORY_INCOMPLETE"),
                base.goldenDragon(), base.enderDragon());
        DungeonRequirementEvaluation evaluation =
                DungeonRequirementEvaluator.evaluate(policy, evidence, ARCHER);
        DungeonQuickViewSnapshot snapshot = DungeonQuickViewSnapshot.parse(
                DungeonQuickViewSnapshotTest.JSON_WITH_REQUIREMENTS);

        Component message = DungeonQuickViewMessage.buildWithUnknowns(
                snapshot, evaluation, String::length,
                (item, kind) -> new HoverEvent.ShowText(Component.literal(item.name())));
        String text = message.getString();

        assertEquals(RequirementStatus.UNKNOWN, evaluation.status());
        assertTrue(text.contains("QCA Player Quick View"));
        assertTrue(text.contains("Catacombs:"));
        assertTrue(text.contains("Unable to verify:"));
        assertInOrder(text,
                "Average Secrets: Calculation unavailable",
                "Terminator: Inventory data is incomplete");
        assertFalse(text.contains("SCOPE_MISMATCH"));
        assertFalse(text.contains("INVENTORY_INCOMPLETE"));
        assertTrue(text.contains("CLICK HERE TO KICK THE PLAYER OUT"));
        assertRemovedStatusLinesAreAbsent(text);
    }

    @Test
    void globalEvidenceBlockersKeepTheirSpecificUnknownMessages() {
        assertGlobalUnknownMessage(DungeonQuickViewSnapshot.parse(
                        DungeonQuickViewSnapshotTest.JSON),
                "Requirements evidence is unavailable");
        assertGlobalUnknownMessage(DungeonQuickViewSnapshot.parse(
                        DungeonQuickViewSnapshotTest.JSON_WITH_REQUIREMENTS.replace(
                                "\"selectionCertain\":true", "\"selectionCertain\":false")),
                "Selected SkyBlock Profile is uncertain");
        assertGlobalUnknownMessage(DungeonQuickViewSnapshot.parse(
                        DungeonQuickViewSnapshotTest.JSON_WITH_REQUIREMENTS.replace(
                                "\"request\":{\"floor\":\"M7\",\"responseFloor\":\"M7\",\"floorMatches\":true}",
                                "\"request\":{\"floor\":\"M7\",\"responseFloor\":\"F7\",\"floorMatches\":false}")),
                "Floor could not be verified");
        assertGlobalUnknownMessage(DungeonQuickViewSnapshot.parse(
                        DungeonQuickViewSnapshotTest.JSON_WITH_REQUIREMENTS.replace(
                                "\"source\":\"requirements\",\"queryName\":\"GhostsTM\",\"uuid\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"name\":\"GhostsTM\"",
                                "\"source\":\"requirements\",\"queryName\":\"GhostsTM\",\"uuid\":\"cccccccccccccccccccccccccccccccc\",\"name\":\"GhostsTM\"")),
                "Player identity could not be verified");
        assertGlobalUnknownMessage(DungeonQuickViewSnapshot.parse(
                        DungeonQuickViewSnapshotTest.JSON_WITH_REQUIREMENTS.replace(
                                "\"metadata\":{\"status\":\"fresh\",\"fetchedAt\":1000}",
                                "\"metadata\":{\"status\":\"stale\",\"fetchedAt\":1000}")),
                "Data is stale");
    }

    @Test
    void passRendersOnlyTheExistingProfileWithoutAPassBanner() {
        DungeonRequirementPolicy policy = new DungeonRequirementPolicy(DungeonFloorKey.M7,
                LongRule.enabled(312), false,
                LongRule.enabled(298_321), DecimalRule.disabled(0),
                LongRule.enabled(1_330), true, false, false, false);
        DungeonRequirementEvidence evidence = new DungeonRequirementEvidence(
                DungeonFloorKey.M7,
                LongValue.known(312),
                DuplicateClass.unknown(null, null, "Disabled"),
                LongValue.known(298_321),
                DecimalValue.unknown("Disabled"),
                LongValue.known(1_330),
                PresenceEvidence.present(),
                PresenceEvidence.unknown("Disabled"),
                PresenceEvidence.unknown("Disabled"),
                PresenceEvidence.unknown("Disabled"));
        DungeonRequirementEvaluation evaluation =
                DungeonRequirementEvaluator.evaluate(policy, evidence);
        DungeonQuickViewSnapshot snapshot = DungeonQuickViewSnapshot.parse(
                DungeonQuickViewSnapshotTest.JSON_WITH_REQUIREMENTS);

        Component message = DungeonQuickViewMessage.buildWithUnknowns(
                snapshot, evaluation, String::length,
                (item, kind) -> new HoverEvent.ShowText(Component.literal(item.name())));
        String text = message.getString();

        assertEquals(RequirementStatus.PASS, evaluation.status());
        assertTrue(text.contains("QCA Player Quick View"));
        assertTrue(text.contains("Catacombs:"));
        assertFalse(text.contains("Requirements"));
        assertFalse(text.contains("Unable to verify:"));
        assertRemovedStatusLinesAreAbsent(text);
    }

    private static DungeonRequirementPolicy allEnabled(DungeonFloorKey floor) {
        return new DungeonRequirementPolicy(floor,
                LongRule.enabled(400), true,
                LongRule.enabled(250_000), DecimalRule.enabled(12.0),
                LongRule.enabled(1_400), true, true, true, true);
    }

    private static void assertGlobalUnknownMessage(
            DungeonQuickViewSnapshot snapshot, String expectedReason) {
        DungeonRequirementPolicy policy = new DungeonRequirementPolicy(DungeonFloorKey.M7,
                LongRule.enabled(400), false,
                LongRule.disabled(0), DecimalRule.disabled(0),
                LongRule.disabled(0), false, false, false, false);
        String blocker = snapshot.requirementsEvidenceBlocker();
        DungeonRequirementEvaluation evaluation = DungeonRequirementEvaluator.evaluate(
                policy, DungeonRequirementEvidence.unavailable(policy.floor(), blocker));
        String text = DungeonQuickViewMessage.buildWithUnknowns(
                snapshot, evaluation, String::length,
                (item, kind) -> new HoverEvent.ShowText(Component.literal(item.name())))
                .getString();

        assertEquals(RequirementStatus.UNKNOWN, evaluation.status());
        assertTrue(text.contains("M7 Completions: " + expectedReason));
        if (!"Data is stale".equals(expectedReason)) {
            assertFalse(text.contains("M7 Completions: Data is stale"));
        }
    }

    private static void assertInOrder(String text, String... expected) {
        int previous = -1;
        for (String value : expected) {
            int current = text.indexOf(value);
            assertTrue(current > previous, () -> "Missing or out of order: " + value + "\n" + text);
            previous = current;
        }
    }

    private static void assertRemovedStatusLinesAreAbsent(String text) {
        assertFalse(text.contains("PASSED"));
        assertFalse(text.contains("No kick command was sent"));
    }

    private static List<Component> flatten(Component root) {
        List<Component> result = new ArrayList<>();
        walk(root, result);
        return result;
    }

    private static void walk(Component component, List<Component> target) {
        target.add(component);
        for (Component sibling : component.getSiblings()) walk(sibling, target);
    }
}

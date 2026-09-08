package cloudy.autume.addition.dungeon;

import cloudy.autume.addition.dungeon.requirements.DungeonFloorKey;
import cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidence;
import cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidenceValue.PresenceState;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DungeonQuickViewSnapshotTest {
    static final String JSON = """
            {
              "schemaVersion":1,
              "identity":{"queryName":"GhostsTM","uuid":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","name":"GhostsTM"},
              "catacombs":{"level":40.2,"xp":51359640},
              "classes":{
                "healer":{"level":30.1,"xp":3084640},
                "mage":{"level":41.0,"xp":66359640},
                "berserk":{"level":39.5,"xp":45000000},
                "archer":{"level":42.0,"xp":85359640},
                "tank":{"level":31.0,"xp":4149640}
              },
              "floor":{"id":"M7","runs":312,"fastestMs":298321},
              "secrets":{"total":2432,"averagePerRun":11.4},
              "magicalPower":1330,
              "armor":[
                {"itemId":"GOLDEN_NECRON_HEAD","name":"§6Ancient Golden Necron Head","lore":["§7Health: +100"],"rarity":"LEGENDARY"},
                null,null,null
              ],
              "weapons":{
                "witherBlade":{"present":true,"item":{"itemId":"HYPERION","name":"§dHyperion","lore":[],"rarity":"MYTHIC"}},
                "terminator":{"present":false,"item":null},"complete":true
              },
              "pets":{
                "goldenDragon":{"present":true,"item":{"itemId":"PET","name":"§6Golden Dragon","lore":["§7XP: §b1,000"],"rarity":"LEGENDARY"}},
                "enderDragon":{"present":false,"item":null},"complete":true
              },
              "metadata":{"status":"fresh","fetchedAt":1000}
            }
            """;

    static final String REQUIREMENTS_EVIDENCE = """
            {
                "version":1,
                "fresh":true,
                "fetchedAt":900,
                "identity":{"source":"requirements","queryName":"GhostsTM","uuid":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","name":"GhostsTM"},
                "profile":{"id":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","selection":"SELECTED","selectionCertain":true},
                "request":{"floor":"M7","responseFloor":"M7","floorMatches":true},
                "sources":{},
                "floorCompletions":{"state":"KNOWN","value":312},
                "fastestCompletion":{"state":"KNOWN","valueMs":298321,"kind":"ANY_COMPLETION"},
                "averageSecrets":{"state":"UNAVAILABLE","value":null,"numerator":2432,"denominator":213,"scope":"ACCOUNT_SECRETS_SELECTED_PROFILE_RUNS","complete":false,"reason":"SCOPE_MISMATCH"},
                "magicalPower":{"state":"KNOWN","value":1330,"kind":"HIGHEST"},
                "weapons":{
                    "complete":true,
                    "witherBlade":{"state":"PRESENT"},
                    "terminator":{"state":"ABSENT"}
                },
                "pets":{
                    "complete":true,
                    "goldenDragon":{"state":"PRESENT"},
                    "enderDragon":{"state":"ABSENT"}
                }
            }
            """;

    static final String JSON_WITH_REQUIREMENTS = JSON.replace(
            "\"metadata\":",
            "\"requirementsEvidence\":" + REQUIREMENTS_EVIDENCE + ",\n\"metadata\":");

    @Test
    void parsesTheDedicatedBoundedContractAndTriState() {
        DungeonQuickViewSnapshot view = DungeonQuickViewSnapshot.parse(JSON);
        assertEquals("GhostsTM", view.playerName());
        assertEquals(40.2, view.catacombs().level());
        assertEquals("M7", view.floor().id());
        assertEquals(312, view.floor().runs());
        assertEquals(DungeonQuickViewSnapshot.PresenceState.PRESENT, view.witherBlade().state());
        assertEquals(DungeonQuickViewSnapshot.PresenceState.ABSENT, view.terminator().state());
        assertEquals("§6Ancient Golden Necron Head", view.armor().getFirst().name());
    }

    @Test
    void parsesCompleteTypedRequirementsEvidenceWithoutPromotingUnavailableValues() {
        DungeonQuickViewSnapshot view = DungeonQuickViewSnapshot.parse(JSON_WITH_REQUIREMENTS);
        DungeonRequirementEvidence evidence = view.requirementsEvidence();

        assertNotNull(evidence);
        assertEquals(DungeonFloorKey.M7, evidence.floor());
        assertEquals(312L, evidence.floorCompletions().value());
        assertEquals(298_321L, evidence.fastestCompletionMs().value());
        assertEquals(1_330L, evidence.magicalPower().value());
        assertNull(evidence.averageSecrets().value());
        assertEquals("SCOPE_MISMATCH", evidence.averageSecrets().unavailableReason());
        assertEquals(PresenceState.PRESENT, evidence.witherBlade().state());
        assertEquals(PresenceState.CONFIRMED_ABSENT, evidence.terminator().state());
        assertEquals(PresenceState.PRESENT, evidence.goldenDragon().state());
        assertEquals(PresenceState.CONFIRMED_ABSENT, evidence.enderDragon().state());
        assertNull(evidence.duplicateClass().newcomerClass());
        assertFalse(evidence.duplicateClass().authoritativeWithoutDuplicate());
        assertEquals("PARTY_CLASSES_INCOMPLETE",
                evidence.duplicateClass().unavailableReason());
        assertEquals("GhostsTM", view.queryName());
        assertEquals("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", view.playerUuid().toString());
        assertEquals(900L, view.evidenceFetchedAt());
        assertTrue(view.requirementsEvidenceTrusted());
        assertEquals("", view.requirementsEvidenceBlocker());
    }

    @Test
    void missingLegacyRequirementsProtocolBecomesUnknownInsteadOfAbsentOrZero() {
        DungeonQuickViewSnapshot view = DungeonQuickViewSnapshot.parse(JSON);

        assertAllRequirementsUnknown(view.requirementsEvidence(),
                "UNSUPPORTED_EVIDENCE");
        assertEquals(0L, view.evidenceFetchedAt());
        assertFalse(view.requirementsEvidenceTrusted());
        assertEquals("UNSUPPORTED_EVIDENCE", view.requirementsEvidenceBlocker());
    }

    @Test
    void staleProfileDowngradesEveryRequirementToUnknown() {
        String stale = JSON_WITH_REQUIREMENTS.replace(
                "\"metadata\":{\"status\":\"fresh\",\"fetchedAt\":1000}",
                "\"metadata\":{\"status\":\"stale\",\"fetchedAt\":1000}");
        DungeonQuickViewSnapshot view = DungeonQuickViewSnapshot.parse(stale);

        assertTrue(view.stale());
        assertAllRequirementsUnknown(view.requirementsEvidence(), "SOURCE_STALE");
        assertFalse(view.requirementsEvidenceTrusted());
        assertEquals("SOURCE_STALE", view.requirementsEvidenceBlocker());
    }

    @Test
    void identityMismatchDowngradesEveryRequirementToUnknown() {
        String mismatched = JSON_WITH_REQUIREMENTS.replace(
                "\"source\":\"requirements\",\"queryName\":\"GhostsTM\",\"uuid\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"name\":\"GhostsTM\"",
                "\"source\":\"requirements\",\"queryName\":\"GhostsTM\",\"uuid\":\"cccccccccccccccccccccccccccccccc\",\"name\":\"GhostsTM\"");
        DungeonQuickViewSnapshot view = DungeonQuickViewSnapshot.parse(mismatched);

        assertAllRequirementsUnknown(view.requirementsEvidence(),
                "IDENTITY_MISMATCH");
        assertFalse(view.requirementsEvidenceTrusted());
        assertEquals("IDENTITY_MISMATCH", view.requirementsEvidenceBlocker());
    }

    @Test
    void floorMismatchDowngradesEveryRequirementToUnknown() {
        String mismatched = JSON_WITH_REQUIREMENTS.replace(
                "\"request\":{\"floor\":\"M7\",\"responseFloor\":\"M7\",\"floorMatches\":true}",
                "\"request\":{\"floor\":\"M7\",\"responseFloor\":\"F7\",\"floorMatches\":false}");
        DungeonQuickViewSnapshot view = DungeonQuickViewSnapshot.parse(mismatched);

        assertAllRequirementsUnknown(view.requirementsEvidence(),
                "FLOOR_MISMATCH");
        assertFalse(view.requirementsEvidenceTrusted());
        assertEquals("FLOOR_MISMATCH", view.requirementsEvidenceBlocker());
    }

    @Test
    void uncertainProfileSelectionKeepsItsOwnGlobalBlocker() {
        String uncertain = JSON_WITH_REQUIREMENTS.replace(
                "\"selectionCertain\":true", "\"selectionCertain\":false");
        DungeonQuickViewSnapshot view = DungeonQuickViewSnapshot.parse(uncertain);

        assertAllRequirementsUnknown(view.requirementsEvidence(),
                "PROFILE_SELECTION_UNCERTAIN");
        assertFalse(view.requirementsEvidenceTrusted());
        assertEquals("PROFILE_SELECTION_UNCERTAIN",
                view.requirementsEvidenceBlocker());
    }

    @Test
    void usesOdinStyleClassLevelsAndPreservesManualKickClick() {
        DungeonQuickViewSnapshot view = DungeonQuickViewSnapshot.parse(JSON);
        Component message = DungeonQuickViewMessage.build(view, String::length,
                (item, kind) -> new HoverEvent.ShowText(Component.literal(item.name())));
        List<Component> parts = flatten(message);
        Component archer = parts.stream().filter(part -> part.getString().equals("42.0")
                        && part.getStyle().getColor() != null
                        && part.getStyle().getColor().getValue() == 0xFFAA00)
                .findFirst().orElseThrow();
        Component kick = parts.stream().filter(part -> part.getString().equals(
                "CLICK HERE TO KICK THE PLAYER OUT")).findFirst().orElseThrow();
        assertTrue(message.getString().contains(
                "Classes: 42.0/39.5/30.1/41.0/31.0 | Class Average: 36.7"));
        assertFalse(archer.getStyle().isUnderlined());
        HoverEvent.ShowText classHover = assertInstanceOf(HoverEvent.ShowText.class,
                archer.getStyle().getHoverEvent());
        assertEquals("Archer Level\nXP: 85,359,640", classHover.value().getString());
        assertTrue(kick.getStyle().isUnderlined());
        assertTrue(kick.getStyle().isBold());
        ClickEvent.RunCommand click = assertInstanceOf(ClickEvent.RunCommand.class,
                kick.getStyle().getClickEvent());
        assertEquals("/party kick GhostsTM", click.command());
        assertNotNull(parts.stream().filter(part -> part.getString().equals("Withered Blade ✔"))
                .findFirst().orElseThrow().getStyle().getHoverEvent());
    }

    @Test
    void separatorEndpointsUseMeasuredWidth() {
        DungeonQuickViewMessage.Lines lines = DungeonQuickViewMessage.separators(String::length);
        assertTrue(Math.abs(lines.topWidth() - lines.bottomWidth()) <= 1);
    }

    @Test
    void separatorEndpointsIncludeTheBoldTitleWidth() {
        DungeonQuickViewMessage.Lines lines = DungeonQuickViewMessage.separators(
                String::length, 40);
        assertTrue(Math.abs(lines.topWidth() - lines.bottomWidth()) <= 1);
    }

    @Test
    void separatorUsesBoldLineGlyphsToMatchAnOtherwiseUnreachablePixelWidth() {
        DungeonQuickViewMessage.Lines lines = DungeonQuickViewMessage.separators(
                text -> text.length() * 6, 121, 7);

        assertEquals(331, lines.topWidth());
        assertEquals(lines.topWidth(), lines.bottomWidth());
        assertEquals(1, lines.bottomBold().length());
    }

    @Test
    void odinStyleClassLineKeepsMissingDataExplicit() {
        DungeonQuickViewSnapshot view = DungeonQuickViewSnapshot.parse(JSON.replace(
                "\"archer\":{\"level\":42.0,\"xp\":85359640}",
                "\"archer\":{\"level\":null,\"xp\":null}"));
        Component message = DungeonQuickViewMessage.build(view, String::length,
                (item, kind) -> new HoverEvent.ShowText(Component.literal(item.name())));
        Component missingArcher = flatten(message).stream()
                .filter(part -> part.getString().equals("Missing"))
                .filter(part -> part.getStyle().getHoverEvent() instanceof HoverEvent.ShowText hover
                        && hover.value().getString().equals("Archer Level\nXP: Missing"))
                .findFirst().orElseThrow();

        assertEquals(0xFF5555, missingArcher.getStyle().getColor().getValue());
        assertTrue(message.getString().contains(
                "Classes: Missing/39.5/30.1/41.0/31.0 | Class Average: Missing"));
    }

    @Test
    void rejectsAResponseNameThatCouldChangeTheKickCommand() {
        String unsafe = JSON.replace("\"name\":\"GhostsTM\"", "\"name\":\"GhostsTM kick Other\"");
        assertThrows(DungeonQuickViewException.class, () -> DungeonQuickViewSnapshot.parse(unsafe));
    }

    @Test
    void requestFailureUsesACompactWarningInsteadOfAnAllMissingCard() {
        Component message = DungeonQuickViewMessage.unavailable(
                "GhostsTM", "Dungeon profile service is temporarily unavailable.");
        List<Component> parts = flatten(message);

        assertEquals("[QCA] Dungeon Quick View unavailable for GhostsTM ⚠", message.getString());
        assertTrue(parts.stream().noneMatch(part -> part.getString().contains("Catacombs:")));
        assertTrue(parts.stream().noneMatch(part -> part.getString().contains("Missing")));
        assertTrue(parts.stream().noneMatch(part -> part.getStyle().getClickEvent() != null));
        Component warning = parts.stream().filter(part -> part.getString().equals(" ⚠"))
                .findFirst().orElseThrow();
        assertNotNull(warning.getStyle().getHoverEvent());
        assertRemovedStatusLinesAreAbsent(message);
    }

    private static void assertAllRequirementsUnknown(
            DungeonRequirementEvidence evidence, String reason) {
        assertNotNull(evidence);
        assertNull(evidence.floorCompletions().value());
        assertNull(evidence.fastestCompletionMs().value());
        assertNull(evidence.averageSecrets().value());
        assertNull(evidence.magicalPower().value());
        assertEquals(PresenceState.UNKNOWN, evidence.witherBlade().state());
        assertEquals(PresenceState.UNKNOWN, evidence.terminator().state());
        assertEquals(PresenceState.UNKNOWN, evidence.goldenDragon().state());
        assertEquals(PresenceState.UNKNOWN, evidence.enderDragon().state());
        assertFalse(evidence.duplicateClass().authoritativeWithoutDuplicate());
        assertTrue(evidence.duplicateClass().conflictingPlayers().isEmpty());
        assertEquals(reason, evidence.floorCompletions().unavailableReason());
        assertEquals(reason, evidence.duplicateClass().unavailableReason());
        assertEquals(reason, evidence.witherBlade().unavailableReason());
    }

    private static void assertRemovedStatusLinesAreAbsent(Component message) {
        assertFalse(message.getString().contains("PASSED"));
        assertFalse(message.getString().contains("No kick command was sent"));
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

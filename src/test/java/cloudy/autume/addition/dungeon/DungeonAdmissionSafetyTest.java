package cloudy.autume.addition.dungeon;

import cloudy.autume.addition.config.ModConfig;
import cloudy.autume.addition.dungeon.requirements.DungeonFloorKey;
import cloudy.autume.addition.dungeon.requirements.DungeonClassKey;
import cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidenceValue.DuplicateClass;
import cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidenceValue.PartyMemberClass;
import cloudy.autume.addition.dungeon.requirements.DungeonRequirementPolicy;
import net.hypixel.modapi.packet.impl.clientbound.ClientboundPartyInfoPacket.PartyRole;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DungeonAdmissionSafetyTest {
    private static final UUID LOCAL_PLAYER =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TARGET_PLAYER =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void overlappingPartyInfoRefreshesAreStrictSingleFlightFifo() {
        List<Long> sentTickets = new ArrayList<>();
        DungeonPartyAuthorityTracker.useSenderForTesting(ticket -> {
            sentTickets.add(ticket);
            return true;
        });
        DungeonPartyAuthorityTracker.onWorldChange();
        try {
            long authoritySession = DungeonPartyAuthorityTracker.snapshot().sessionEpoch();
            long firstInitial = DungeonPartyAuthorityTracker.requestRefresh();
            long secondInitial = DungeonPartyAuthorityTracker.requestRefresh();

            assertTrue(firstInitial > 0L);
            assertTrue(secondInitial > firstInitial);
            assertEquals(List.of(firstInitial), sentTickets);
            assertEquals(firstInitial, DungeonPartyAuthorityTracker.activeTicketForTesting());
            assertEquals(List.of(secondInitial),
                    DungeonPartyAuthorityTracker.pendingTicketsForTesting());

            DungeonPartyAuthorityTracker.acceptForTesting(true,
                    Map.of(LOCAL_PLAYER, PartyRole.LEADER,
                            TARGET_PLAYER, PartyRole.MEMBER), 100L);
            DungeonPartyAuthorityTracker.Snapshot afterFirst =
                    DungeonPartyAuthorityTracker.snapshot();
            assertEquals(firstInitial, afterFirst.responseSerial());
            assertEquals(DungeonPartyAuthorityTracker.Readiness.READY,
                    afterFirst.readiness(authoritySession, firstInitial,
                            LOCAL_PLAYER, TARGET_PLAYER));
            assertEquals(DungeonPartyAuthorityTracker.Readiness.PENDING,
                    afterFirst.readiness(authoritySession, secondInitial,
                            LOCAL_PLAYER, TARGET_PLAYER));
            assertEquals(List.of(firstInitial, secondInitial), sentTickets);
            assertEquals(secondInitial, DungeonPartyAuthorityTracker.activeTicketForTesting());

            // A final refresh requested while B's initial refresh is active must
            // queue behind B and cannot reuse either earlier response.
            long firstFinal = DungeonPartyAuthorityTracker.requestRefresh();
            assertTrue(firstFinal > secondInitial);
            assertEquals(List.of(firstFinal),
                    DungeonPartyAuthorityTracker.pendingTicketsForTesting());

            DungeonPartyAuthorityTracker.acceptForTesting(true,
                    Map.of(LOCAL_PLAYER, PartyRole.LEADER,
                            TARGET_PLAYER, PartyRole.MEMBER), 200L);
            DungeonPartyAuthorityTracker.Snapshot afterSecond =
                    DungeonPartyAuthorityTracker.snapshot();
            assertEquals(secondInitial, afterSecond.responseSerial());
            assertEquals(DungeonPartyAuthorityTracker.Readiness.PENDING,
                    afterSecond.readiness(authoritySession, firstFinal,
                            LOCAL_PLAYER, TARGET_PLAYER));
            assertEquals(List.of(firstInitial, secondInitial, firstFinal), sentTickets);
            assertEquals(firstFinal, DungeonPartyAuthorityTracker.activeTicketForTesting());

            DungeonPartyAuthorityTracker.acceptForTesting(true,
                    Map.of(LOCAL_PLAYER, PartyRole.LEADER,
                            TARGET_PLAYER, PartyRole.MEMBER), 300L);
            DungeonPartyAuthorityTracker.Snapshot afterFinal =
                    DungeonPartyAuthorityTracker.snapshot();
            assertEquals(firstFinal, afterFinal.responseSerial());
            assertEquals(DungeonPartyAuthorityTracker.Readiness.READY,
                    afterFinal.readiness(authoritySession, firstFinal,
                            LOCAL_PLAYER, TARGET_PLAYER));
            assertEquals(0L, DungeonPartyAuthorityTracker.activeTicketForTesting());
            assertTrue(DungeonPartyAuthorityTracker.pendingTicketsForTesting().isEmpty());
        } finally {
            DungeonPartyAuthorityTracker.onWorldChange();
            DungeonPartyAuthorityTracker.restoreSenderAfterTesting();
        }
    }

    @Test
    void partyAuthorityRequiresTheRequestedSessionAndAFreshResponseSerial() {
        DungeonPartyAuthorityTracker.Snapshot snapshot = authoritySnapshot(
                12L, 8L, true, PartyRole.LEADER, true);

        assertEquals(DungeonPartyAuthorityTracker.Readiness.PENDING,
                snapshot.readiness(11L, 8L, LOCAL_PLAYER, TARGET_PLAYER));
        assertEquals(DungeonPartyAuthorityTracker.Readiness.PENDING,
                snapshot.readiness(12L, 9L, LOCAL_PLAYER, TARGET_PLAYER));
        assertEquals(DungeonPartyAuthorityTracker.Readiness.READY,
                snapshot.readiness(12L, 8L, LOCAL_PLAYER, TARGET_PLAYER));
    }

    @Test
    void finalAuthorityRequiresASecondRecentResponse() {
        DungeonPartyAuthorityTracker.Snapshot initial = new DungeonPartyAuthorityTracker.Snapshot(
                12L, 8L, true,
                Map.of(LOCAL_PLAYER, PartyRole.LEADER, TARGET_PLAYER, PartyRole.MEMBER),
                100L);
        DungeonPartyAuthorityTracker.Snapshot refreshed = new DungeonPartyAuthorityTracker.Snapshot(
                12L, 9L, true,
                Map.of(LOCAL_PLAYER, PartyRole.LEADER, TARGET_PLAYER, PartyRole.MEMBER),
                150L);

        assertEquals(DungeonPartyAuthorityTracker.Readiness.PENDING,
                initial.readiness(12L, 9L, LOCAL_PLAYER, TARGET_PLAYER));
        assertEquals(DungeonPartyAuthorityTracker.Readiness.READY,
                refreshed.readiness(12L, 9L, LOCAL_PLAYER, TARGET_PLAYER));
        assertTrue(refreshed.freshAt(200L, 50L));
        assertFalse(refreshed.freshAt(201L, 50L));
        assertFalse(refreshed.freshAt(149L, 50L));
    }

    @Test
    void partyAuthorityCannotCrossItsIngressOrProcessingDeadline() {
        DungeonPartyAuthorityTracker.Snapshot onTime =
                new DungeonPartyAuthorityTracker.Snapshot(
                        12L, 9L, true,
                        Map.of(LOCAL_PLAYER, PartyRole.LEADER,
                                TARGET_PLAYER, PartyRole.MEMBER),
                        199L);
        DungeonPartyAuthorityTracker.Snapshot arrivedLate =
                new DungeonPartyAuthorityTracker.Snapshot(
                        12L, 9L, true,
                        Map.of(LOCAL_PLAYER, PartyRole.LEADER,
                                TARGET_PLAYER, PartyRole.MEMBER),
                        201L);

        assertTrue(DungeonQuickViewManager.authorityResponseUsable(onTime, 200L, 200L));
        assertFalse(DungeonQuickViewManager.authorityResponseUsable(onTime, 201L, 200L));
        assertFalse(DungeonQuickViewManager.authorityResponseUsable(arrivedLate, 201L, 200L));
        assertFalse(DungeonQuickViewManager.authorityResponseUsable(onTime, 198L, 200L));
        assertFalse(DungeonQuickViewManager.authorityResponseUsable(null, 200L, 200L));
    }

    @Test
    void partyAuthorityRequiresTheLocalPlayerToBeCurrentLeader() {
        assertEquals(DungeonPartyAuthorityTracker.Readiness.NOT_IN_PARTY,
                authoritySnapshot(3L, 4L, false, PartyRole.LEADER, true)
                        .readiness(3L, 4L, LOCAL_PLAYER, TARGET_PLAYER));
        assertEquals(DungeonPartyAuthorityTracker.Readiness.NOT_LEADER,
                authoritySnapshot(3L, 4L, true, PartyRole.MEMBER, true)
                        .readiness(3L, 4L, LOCAL_PLAYER, TARGET_PLAYER));
        assertEquals(DungeonPartyAuthorityTracker.Readiness.NOT_LEADER,
                new DungeonPartyAuthorityTracker.Snapshot(
                        3L, 4L, true, Map.of(TARGET_PLAYER, PartyRole.MEMBER))
                        .readiness(3L, 4L, LOCAL_PLAYER, TARGET_PLAYER));
    }

    @Test
    void partyAuthorityRequiresTheTargetToStillBePresent() {
        assertEquals(DungeonPartyAuthorityTracker.Readiness.TARGET_ABSENT,
                authoritySnapshot(5L, 6L, true, PartyRole.LEADER, false)
                        .readiness(5L, 6L, LOCAL_PLAYER, TARGET_PLAYER));
        assertEquals(DungeonPartyAuthorityTracker.Readiness.TARGET_ABSENT,
                authoritySnapshot(5L, 6L, true, PartyRole.LEADER, true)
                        .readiness(5L, 6L, LOCAL_PLAYER, null));
        assertEquals(DungeonPartyAuthorityTracker.Readiness.READY,
                authoritySnapshot(5L, 6L, true, PartyRole.LEADER, true)
                        .readiness(5L, 6L, LOCAL_PLAYER, TARGET_PLAYER));
    }

    @Test
    void dupeReconciliationStopsOnCountIdentityAndClassGaps() {
        UUID other = UUID.fromString("33333333-3333-3333-3333-333333333333");
        List<PartyMemberClass> oneClass = List.of(
                new PartyMemberClass("LocalPlayer", DungeonClassKey.MAGE));

        DuplicateClass countMismatch = DungeonQuickViewManager.reconcileDuplicateClass(
                DungeonClassKey.ARCHER, oneClass,
                Map.of("localplayer", LOCAL_PLAYER), Set.of(LOCAL_PLAYER, other));
        assertEquals("PARTY_ROSTER_COUNT_MISMATCH|2|1",
                countMismatch.unavailableReason());
        assertFalse(countMismatch.authoritativeRoster());

        List<PartyMemberClass> twoClasses = List.of(
                new PartyMemberClass("LocalPlayer", DungeonClassKey.MAGE),
                new PartyMemberClass("StalePlayer", DungeonClassKey.TANK));
        DuplicateClass identityMismatch = DungeonQuickViewManager.reconcileDuplicateClass(
                DungeonClassKey.ARCHER, twoClasses,
                Map.of("localplayer", LOCAL_PLAYER, "staleplayer", TARGET_PLAYER),
                Set.of(LOCAL_PLAYER, other));
        assertTrue(identityMismatch.unavailableReason()
                .startsWith("PARTY_ROSTER_IDENTITY_MISMATCH|UUID-33333333|StalePlayer"));
        assertFalse(identityMismatch.authoritativeRoster());

        DuplicateClass unmapped = DungeonQuickViewManager.reconcileDuplicateClass(
                DungeonClassKey.ARCHER, oneClass, Map.of(), Set.of(LOCAL_PLAYER));
        assertEquals("PARTY_CLASS_IDENTITIES_UNAVAILABLE|LocalPlayer",
                unmapped.unavailableReason());

        DuplicateClass classMissing = DungeonQuickViewManager.reconcileDuplicateClass(
                DungeonClassKey.ARCHER,
                List.of(new PartyMemberClass("LocalPlayer", null)),
                Map.of("localplayer", LOCAL_PLAYER), Set.of(LOCAL_PLAYER));
        assertEquals("PARTY_CLASSES_MISSING|LocalPlayer",
                classMissing.unavailableReason());
        assertFalse(classMissing.authoritativeRoster());
    }

    @Test
    void dupeReconciliationRequiresFullIdentityMatchBeforeFindingADuplicate() {
        DuplicateClass duplicate = DungeonQuickViewManager.reconcileDuplicateClass(
                DungeonClassKey.ARCHER,
                List.of(new PartyMemberClass("LocalPlayer", DungeonClassKey.ARCHER)),
                Map.of("localplayer", LOCAL_PLAYER), Set.of(LOCAL_PLAYER));

        assertTrue(duplicate.authoritativeRoster());
        assertEquals(List.of("LocalPlayer"), duplicate.conflictingPlayers());
    }

    @Test
    void policyIsUnavailableUntilTheAutomaticActionMasterSwitchIsEnabled() {
        ModConfig config = new ModConfig();

        assertNull(DungeonQuickViewManager.policyFor(config, "F7"));

        config.dungeons.partyFinderAutoKick.enabled = true;
        DungeonRequirementPolicy allDisabled =
                DungeonQuickViewManager.policyFor(config, "F7");

        assertEquals(DungeonFloorKey.F7, allDisabled.floor());
        assertFalse(allDisabled.hasEnabledRules());

        config.dungeons.playerQuickView = false;
        assertNull(DungeonQuickViewManager.policyFor(config, "F7"));
    }

    @Test
    void everyAdmissionPolicyMutationAdvancesAnIrreversibleRevision() {
        long before = DungeonQuickViewManager.admissionPolicyRevisionForTesting();

        DungeonQuickViewManager.onAdmissionPolicyChanged();
        long afterDisable = DungeonQuickViewManager.admissionPolicyRevisionForTesting();
        DungeonQuickViewManager.onAdmissionPolicyChanged();
        long afterReenable = DungeonQuickViewManager.admissionPolicyRevisionForTesting();

        assertTrue(afterDisable > before);
        assertTrue(afterReenable > afterDisable);
    }

    @Test
    void policyNeverCreatesRulesForEntranceOrUnknownFloors() {
        ModConfig config = enabledConfig();

        assertNull(DungeonQuickViewManager.policyFor(config, "Entrance"));
        assertNull(DungeonQuickViewManager.policyFor(config, "E"));
        assertNull(DungeonQuickViewManager.policyFor(config, "F0"));
        assertNull(DungeonQuickViewManager.policyFor(config, null));
    }

    @Test
    void f1F7AndM7PoliciesKeepIndependentValuesAndSwitches() {
        ModConfig config = enabledConfig();
        configure(config.dungeons.partyFinderAutoKick.rulesFor("F1"),
                11L, 111_000L, 1.1, 111L);
        config.dungeons.partyFinderAutoKick.rulesFor("F1").disallowDuplicateClass = true;

        configure(config.dungeons.partyFinderAutoKick.rulesFor("F7"),
                77L, 777_000L, 7.7, 777L);
        config.dungeons.partyFinderAutoKick.rulesFor("F7").requireWitherBlade = true;
        config.dungeons.partyFinderAutoKick.rulesFor("F7").requireTerminator = true;

        configure(config.dungeons.partyFinderAutoKick.rulesFor("M7"),
                707L, 707_000L, 17.7, 1_777L);
        config.dungeons.partyFinderAutoKick.rulesFor("M7").requireGoldenDragon = true;
        config.dungeons.partyFinderAutoKick.rulesFor("M7").requireEnderDragon = true;

        DungeonRequirementPolicy f1 = DungeonQuickViewManager.policyFor(config, "F1");
        DungeonRequirementPolicy f7 = DungeonQuickViewManager.policyFor(config, "F7");
        DungeonRequirementPolicy m7 = DungeonQuickViewManager.policyFor(config, "M7");

        assertPolicyValues(f1, DungeonFloorKey.F1, 11L, 111_000L, 1.1, 111L);
        assertTrue(f1.duplicateClassDisallowed());
        assertFalse(f1.witherBladeRequired());
        assertFalse(f1.terminatorRequired());

        assertPolicyValues(f7, DungeonFloorKey.F7, 77L, 777_000L, 7.7, 777L);
        assertFalse(f7.duplicateClassDisallowed());
        assertTrue(f7.witherBladeRequired());
        assertTrue(f7.terminatorRequired());
        assertFalse(f7.goldenDragonRequired());

        assertPolicyValues(m7, DungeonFloorKey.M7, 707L, 707_000L, 17.7, 1_777L);
        assertFalse(m7.witherBladeRequired());
        assertFalse(m7.terminatorRequired());
        assertTrue(m7.goldenDragonRequired());
        assertTrue(m7.enderDragonRequired());
    }

    @Test
    void outgoingCommandCancellationHookAcceptsUnrelatedAndPartyCommandsSafely() {
        assertDoesNotThrow(() -> DungeonQuickViewManager.onOutgoingCommand(null));
        assertDoesNotThrow(() -> DungeonQuickViewManager.onOutgoingCommand(""));
        assertDoesNotThrow(() -> DungeonQuickViewManager.onOutgoingCommand("/"));
        assertDoesNotThrow(() -> DungeonQuickViewManager.onOutgoingCommand("hello world"));
        assertDoesNotThrow(() -> DungeonQuickViewManager.onOutgoingCommand("/party kick GhostsTM"));
        assertDoesNotThrow(() -> DungeonQuickViewManager.onOutgoingCommand("p remove GhostsTM"));
        assertDoesNotThrow(() -> DungeonQuickViewManager.onOutgoingCommand("/party leave"));
        assertDoesNotThrow(() -> DungeonQuickViewManager.onOutgoingCommand("p disband"));
    }

    @Test
    void exactDepartureStartsANewMembershipEpochEvenInsideJoinDebounceWindow() {
        DungeonQuickViewManager.reset();
        try {
            long first = DungeonQuickViewManager.claimMembershipEpoch("GhostsTM", 1_000_000_000L);
            assertTrue(first > 0L);
            assertEquals(-1L, DungeonQuickViewManager.claimMembershipEpoch(
                    "ghoststm", 1_100_000_000L));

            // Chat-like lookalikes cannot clear the debounce or membership period.
            assertFalse(DungeonQuickViewManager.observeDeparture(
                    "Party > GhostsTM: I heard GhostsTM has left the party."));
            assertEquals(-1L, DungeonQuickViewManager.claimMembershipEpoch(
                    "GhostsTM", 1_200_000_000L));

            assertTrue(DungeonQuickViewManager.observeDeparture(
                    "GhostsTM has left the party."));
            long second = DungeonQuickViewManager.claimMembershipEpoch(
                    "GhostsTM", 1_300_000_000L);
            assertTrue(second > first);

            assertTrue(DungeonQuickViewManager.observeDeparture(
                    "Kicked [MVP+] GhostsTM because they were offline."));
            long third = DungeonQuickViewManager.claimMembershipEpoch(
                    "GhostsTM", 1_400_000_000L);
            assertTrue(third > second);
        } finally {
            DungeonQuickViewManager.reset();
        }
    }

    @Test
    void admissionEvidenceUsesSeparateEpochAndMonotonicFreshnessGates() {
        long receivedAtEpoch = Instant.parse("2026-09-08T00:00:00Z").toEpochMilli();
        long receivedAtNanos = 1_000_000_000L;
        DungeonQuickViewSnapshot valid = evidenceFetchedAt(
                receivedAtEpoch - Duration.ofHours(71).toMillis());

        assertTrue(DungeonQuickViewManager.admissionEvidenceFresh(valid,
                receivedAtNanos,
                receivedAtNanos + Duration.ofSeconds(10).toNanos(),
                receivedAtEpoch));
        assertFalse(DungeonQuickViewManager.admissionEvidenceFresh(valid,
                receivedAtNanos,
                receivedAtNanos + Duration.ofSeconds(10).toNanos() + 1L,
                receivedAtEpoch));
        assertFalse(DungeonQuickViewManager.admissionEvidenceFresh(valid,
                receivedAtNanos, receivedAtNanos - 1L, receivedAtEpoch));

        DungeonQuickViewSnapshot tooOld = evidenceFetchedAt(
                receivedAtEpoch - Duration.ofHours(72).toMillis() - 1L);
        assertFalse(DungeonQuickViewManager.admissionEvidenceFresh(tooOld,
                receivedAtNanos, receivedAtNanos, receivedAtEpoch));

        DungeonQuickViewSnapshot allowedClockSkew = evidenceFetchedAt(
                receivedAtEpoch + Duration.ofMinutes(5).toMillis());
        assertTrue(DungeonQuickViewManager.admissionEvidenceFresh(allowedClockSkew,
                receivedAtNanos, receivedAtNanos, receivedAtEpoch));
        DungeonQuickViewSnapshot impossibleFuture = evidenceFetchedAt(
                receivedAtEpoch + Duration.ofMinutes(5).toMillis() + 1L);
        assertFalse(DungeonQuickViewManager.admissionEvidenceFresh(impossibleFuture,
                receivedAtNanos, receivedAtNanos, receivedAtEpoch));

        assertFalse(DungeonQuickViewManager.admissionEvidenceFresh(
                DungeonQuickViewSnapshot.parse(DungeonQuickViewSnapshotTest.JSON),
                receivedAtNanos, receivedAtNanos, receivedAtEpoch));
    }

    private static DungeonQuickViewSnapshot evidenceFetchedAt(long fetchedAt) {
        return DungeonQuickViewSnapshot.parse(
                DungeonQuickViewSnapshotTest.JSON_WITH_REQUIREMENTS.replace(
                        "\"fetchedAt\":900", "\"fetchedAt\":" + fetchedAt));
    }

    private static DungeonPartyAuthorityTracker.Snapshot authoritySnapshot(
            long session, long serial, boolean inParty, PartyRole localRole,
            boolean includeTarget) {
        java.util.LinkedHashMap<UUID, PartyRole> members = new java.util.LinkedHashMap<>();
        if (localRole != null) members.put(LOCAL_PLAYER, localRole);
        if (includeTarget) members.put(TARGET_PLAYER, PartyRole.MEMBER);
        return new DungeonPartyAuthorityTracker.Snapshot(session, serial, inParty, members);
    }

    private static ModConfig enabledConfig() {
        ModConfig config = new ModConfig();
        config.dungeons.partyFinderAutoKick.enabled = true;
        return config;
    }

    private static void configure(ModConfig.DungeonFloorRequirements rules,
                                  long completions, long fastestMs,
                                  double averageSecrets, long magicalPower) {
        rules.minFloorCompletions = new ModConfig.NumericLongRule(true, completions);
        rules.maxFastestCompletionMs = new ModConfig.NumericLongRule(true, fastestMs);
        rules.minAverageSecrets = new ModConfig.NumericDoubleRule(true, averageSecrets);
        rules.minMagicalPower = new ModConfig.NumericLongRule(true, magicalPower);
    }

    private static void assertPolicyValues(DungeonRequirementPolicy policy,
                                           DungeonFloorKey floor,
                                           long completions, long fastestMs,
                                           double averageSecrets, long magicalPower) {
        assertEquals(floor, policy.floor());
        assertEquals(completions, policy.minimumFloorCompletions().value());
        assertTrue(policy.minimumFloorCompletions().enabled());
        assertEquals(fastestMs, policy.maximumFastestCompletionMs().value());
        assertTrue(policy.maximumFastestCompletionMs().enabled());
        assertEquals(averageSecrets, policy.minimumAverageSecrets().value());
        assertTrue(policy.minimumAverageSecrets().enabled());
        assertEquals(magicalPower, policy.minimumMagicalPower().value());
        assertTrue(policy.minimumMagicalPower().enabled());
    }
}

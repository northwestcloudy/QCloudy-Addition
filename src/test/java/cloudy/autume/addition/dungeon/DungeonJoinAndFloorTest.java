package cloudy.autume.addition.dungeon;

import cloudy.autume.addition.config.ModConfig;
import cloudy.autume.addition.dungeon.requirements.DungeonClassKey;
import cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidenceValue.PartyMemberClass;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DungeonJoinAndFloorTest {
    @Test
    void acceptsOnlyDungeonFinderJoinMessages() {
        assertEquals("GhostsTM", DungeonJoinParser.newcomer(
                "§dParty Finder §f> §b[MVP+] GhostsTM §ejoined the dungeon group! (§bArcher Level 9§e)")
                .orElseThrow());
        assertTrue(DungeonJoinParser.newcomer("GhostsTM joined the party.").isEmpty());
        assertTrue(DungeonJoinParser.newcomer(
                "Party Finder > GhostsTM joined the group! (Combat Level 50)").isEmpty());
    }

    @Test
    void distinguishesAQueuedListingAndOrdinaryTrustedPartyJoin() {
        assertTrue(DungeonJoinParser.partyFinderQueued(
                "§dParty Finder §f> §eYour party has been queued in the dungeon finder!"));
        assertFalse(DungeonJoinParser.partyFinderQueued(
                "Party > Your party has been queued in the dungeon finder!"));
        assertEquals("ManualPlayer", DungeonJoinParser.ordinaryPartyJoin(
                "[MVP+] ManualPlayer joined the party.").orElseThrow());
        assertTrue(DungeonJoinParser.ordinaryPartyJoin(
                "Party Finder > ManualPlayer joined the dungeon group! (Mage Level 40)").isEmpty());
    }

    @Test
    void retainsTheNewcomersDungeonClassAndClassLevel() {
        DungeonJoinParser.DungeonJoinEvent event = DungeonJoinParser.event(
                "§dParty Finder §f> §b[MVP+] GhostsTM §ejoined the dungeon group! (§bArcher Level 9§e)")
                .orElseThrow();

        assertEquals("GhostsTM", event.playerName());
        assertEquals(DungeonClassKey.ARCHER, event.dungeonClass());
        assertEquals(9, event.classLevel());
        assertTrue(DungeonJoinParser.event(
                "Party Finder > GhostsTM joined the dungeon group! (Combat Level 50)").isPresent());
        assertEquals(null, DungeonJoinParser.event(
                "Party Finder > GhostsTM joined the dungeon group! (Combat Level 50)")
                .orElseThrow().dungeonClass());
    }

    @Test
    void acceptsOnlyExactHypixelPartyDepartureMessages() {
        assertEquals("GhostsTM", DungeonJoinParser.departure(
                "§b[MVP+] GhostsTM §ehas left the party.").orElseThrow());
        assertEquals("GhostsTM", DungeonJoinParser.departure(
                "GhostsTM has been removed from the party.").orElseThrow());
        assertEquals("GhostsTM", DungeonJoinParser.departure(
                "GhostsTM was removed from your party because they disconnected.")
                .orElseThrow());
        assertEquals("GhostsTM", DungeonJoinParser.departure(
                "Kicked [MVP+] GhostsTM because they were offline.").orElseThrow());

        assertTrue(DungeonJoinParser.departure(
                "Party > GhostsTM: Kicked Player123 because they were offline.").isEmpty());
        assertTrue(DungeonJoinParser.departure(
                "GhostsTM has left the party. I think").isEmpty());
        assertTrue(DungeonJoinParser.departure(
                "Kicked GhostsTM because they were offline. maybe").isEmpty());
        assertTrue(DungeonJoinParser.departure(
                "Kicked xx because they were offline.").isEmpty());
    }

    @Test
    void admissionRulesExistForExactlyFourteenFloorsAndNeverEntrance() {
        ModConfig config = new ModConfig();
        config.normalize();

        assertEquals(14, config.dungeons.partyFinderAutoKick.floors.size());
        assertEquals(List.of("F1", "F2", "F3", "F4", "F5", "F6", "F7",
                        "M1", "M2", "M3", "M4", "M5", "M6", "M7"),
                List.copyOf(config.dungeons.partyFinderAutoKick.floors.keySet()));
        assertEquals(null, config.dungeons.partyFinderAutoKick.rulesFor("Entrance"));
        assertEquals(null, config.dungeons.partyFinderAutoKick.rulesFor("E"));
        assertFalse(config.dungeons.partyFinderAutoKick.floors.containsKey("Entrance"));
        assertNotSame(config.dungeons.partyFinderAutoKick.rulesFor("F7"),
                config.dungeons.partyFinderAutoKick.rulesFor("M7"));
    }

    @Test
    void readsTheQueuedFloorWithoutBrowsingPartyListings() {
        assertEquals("F7", DungeonFloor.fromScoreboard(List.of(
                "SKYBLOCK", "Queued: The Catacombs", "Tier: Floor VII", "Position: #2 Since: 00:01"))
                .orElseThrow().id());
        assertEquals("M6", DungeonFloor.fromScoreboard(List.of(
                "Queued: Master Mode The Catacombs", "Tier: Floor VI"))
                .orElseThrow().id());
        assertTrue(DungeonFloor.fromScoreboard(List.of("Tier: Floor VII")).isEmpty());
    }

    @Test
    void retainsFloorOnlyForAPartialUpdateOfTheSameQueueMode() {
        DungeonFloor normal = new DungeonFloor("F7");
        DungeonFloor master = new DungeonFloor("M7");

        assertEquals(normal, DungeonFloor.retainWhileQueued(normal,
                List.of("Queued: The Catacombs")).orElseThrow());
        assertEquals(master, DungeonFloor.retainWhileQueued(normal,
                List.of("Queued: Master Mode The Catacombs", "Tier: Floor VII"))
                .orElseThrow());
        assertTrue(DungeonFloor.retainWhileQueued(normal,
                List.of("Queued: Master Mode The Catacombs")).isEmpty());
        assertTrue(DungeonFloor.retainWhileQueued(normal,
                List.of("SKYBLOCK", "Purse: 1,000")).isEmpty());
    }

    @Test
    void readsOnlyTheLocalPlayersOwnAdvertisedPartyFinderFloor() {
        List<DungeonPartyFinderFloorTracker.MenuEntry> entries = List.of(
                new DungeonPartyFinderFloorTracker.MenuEntry(10, "OtherPlayer's Party",
                        List.of("Dungeon: The Catacombs", "Floor: Floor V"), true, false),
                new DungeonPartyFinderFloorTracker.MenuEntry(48, "LocalPlayer's Party",
                        List.of("Dungeon: Master Mode The Catacombs", "Floor: Floor VII"), true, false),
                new DungeonPartyFinderFloorTracker.MenuEntry(50, "Delist Group", List.of(), false, true));

        assertEquals("M7", DungeonPartyFinderFloorTracker.floorFromPartyFinder(
                "Party Finder", entries, "LocalPlayer").orElseThrow().id());
    }

    @Test
    void rejectsSearchFiltersAndOtherPlayersListingsWithoutLocalDelistProof() {
        List<DungeonPartyFinderFloorTracker.MenuEntry> entries = List.of(
                new DungeonPartyFinderFloorTracker.MenuEntry(10, "OtherPlayer's Party",
                        List.of("Dungeon: The Catacombs", "Floor: Floor VII"), true, false),
                new DungeonPartyFinderFloorTracker.MenuEntry(50, "Refresh", List.of(), false, false));

        assertTrue(DungeonPartyFinderFloorTracker.floorFromPartyFinder(
                "Party Finder", entries, "LocalPlayer").isEmpty());
        assertTrue(DungeonPartyFinderFloorTracker.floorFromPartyFinder(
                "Select Floor", entries, "LocalPlayer").isEmpty());
    }

    @Test
    void rejectsAnUnownedBottomHeadAndAnUnlabelledBookshelf() {
        List<DungeonPartyFinderFloorTracker.MenuEntry> entries = List.of(
                new DungeonPartyFinderFloorTracker.MenuEntry(48, "OtherPlayer's Party",
                        List.of("Dungeon: Master Mode The Catacombs", "Floor: Floor VII",
                                "LocalPlayer has joined"), true, false),
                new DungeonPartyFinderFloorTracker.MenuEntry(50, "Settings", List.of(), false, true));

        assertTrue(DungeonPartyFinderFloorTracker.floorFromPartyFinder(
                "Party Finder", entries, "LocalPlayer").isEmpty());
        assertTrue(DungeonPartyFinderFloorTracker.classesFromOwnParty(
                entries, "LocalPlayer").isEmpty());
    }

    @Test
    void rejectsALocallyOwnedHeadWhenTheOnlyControlProofIsAnUnlabelledBookshelf() {
        List<DungeonPartyFinderFloorTracker.MenuEntry> entries = List.of(
                new DungeonPartyFinderFloorTracker.MenuEntry(48, "LocalPlayer's Party",
                        List.of("Dungeon: Master Mode The Catacombs", "Floor: Floor VII",
                                "You are in this party"), true, false),
                new DungeonPartyFinderFloorTracker.MenuEntry(53, "Bookshelf",
                        List.of(), false, true));

        assertTrue(DungeonPartyFinderFloorTracker.floorFromPartyFinder(
                "Party Finder", entries, "LocalPlayer").isEmpty());
    }

    @Test
    void findsOwnSearchResultWhenDelistControlAppearsBeforeBottomPartyHead() {
        List<DungeonPartyFinderFloorTracker.MenuEntry> entries = List.of(
                new DungeonPartyFinderFloorTracker.MenuEntry(11, "LocalPlayer's Party",
                        List.of("Dungeon: The Catacombs", "Floor: Entrance",
                                "You are in this party"), true, false),
                new DungeonPartyFinderFloorTracker.MenuEntry(53, "Delist Group", List.of(), false, true));

        assertEquals("E", DungeonPartyFinderFloorTracker.floorFromPartyFinder(
                "Party Finder", entries, "LocalPlayer").orElseThrow().id());
    }

    @Test
    void acceptsNamedDelistControlWhenHypixelChangesItsItemType() {
        List<DungeonPartyFinderFloorTracker.MenuEntry> entries = List.of(
                new DungeonPartyFinderFloorTracker.MenuEntry(10, "LocalPlayer's Party",
                        List.of("Dungeon: The Catacombs", "Floor: Floor VI",
                                "You are in this party"), true, false),
                new DungeonPartyFinderFloorTracker.MenuEntry(53, "Delist Group",
                        List.of(), false, false));

        assertEquals("F6", DungeonPartyFinderFloorTracker.floorFromPartyFinder(
                "Party Finder", entries, "LocalPlayer").orElseThrow().id());
    }

    @Test
    void acceptsDelistControlRecognizedBySemanticLore() {
        List<DungeonPartyFinderFloorTracker.MenuEntry> entries = List.of(
                new DungeonPartyFinderFloorTracker.MenuEntry(10, "LocalPlayer's Party",
                        List.of("Dungeon: The Catacombs", "Floor: Floor VI",
                                "You are in this party"), true, false),
                new DungeonPartyFinderFloorTracker.MenuEntry(53, "Party Finder Control",
                        List.of("Click to delist your group!"), false, true));

        assertEquals("F6", DungeonPartyFinderFloorTracker.floorFromPartyFinder(
                "Party Finder", entries, "LocalPlayer").orElseThrow().id());
    }

    @Test
    void capturesPartyClassesFromTheLocalListingLoreForDupeChecks() {
        List<DungeonPartyFinderFloorTracker.MenuEntry> entries = List.of(
                new DungeonPartyFinderFloorTracker.MenuEntry(48, "LocalPlayer's Party",
                        List.of("Dungeon: The Catacombs", "Floor: Floor VII",
                                "LocalPlayer: Mage (Level 42)",
                                "GhostsTM: Archer (Level 9)"), true, false),
                new DungeonPartyFinderFloorTracker.MenuEntry(53, "Delist Group", List.of(), false, true));

        Map<String, PartyMemberClass> classes =
                DungeonPartyFinderFloorTracker.classesFromOwnParty(entries, "LocalPlayer");

        assertEquals(2, classes.size());
        assertEquals(DungeonClassKey.MAGE, classes.get("localplayer").dungeonClass());
        assertEquals(DungeonClassKey.ARCHER, classes.get("ghoststm").dungeonClass());
    }

    @Test
    void partyFinderClassStaysPendingUntilAdmissionPassesAndIsReleasedOnLeave() {
        DungeonPartyFinderFloorTracker.reset();
        DungeonJoinParser.DungeonJoinEvent event = DungeonJoinParser.event(
                "Party Finder > GhostsTM joined the dungeon group! (Archer Level 9)")
                .orElseThrow();

        DungeonPartyFinderFloorTracker.observeJoin(event);
        assertTrue(DungeonPartyFinderFloorTracker.existingClasses("GhostsTM").isEmpty());
        assertTrue(DungeonPartyFinderFloorTracker.existingClasses("SomeoneElse").isEmpty());

        DungeonPartyFinderFloorTracker.acceptPartyFinderMember(event);
        assertEquals(DungeonClassKey.ARCHER,
                DungeonPartyFinderFloorTracker.existingClasses("SomeoneElse").getFirst().dungeonClass());

        DungeonPartyFinderFloorTracker.observeSystemMessage("GhostsTM has left the party.");
        assertTrue(DungeonPartyFinderFloorTracker.existingClasses("SomeoneElse").isEmpty());
        DungeonPartyFinderFloorTracker.reset();
    }

    @Test
    void offlineKickAlsoInvalidatesTheCachedMemberClassAndGuiRoster() {
        DungeonPartyFinderFloorTracker.reset();
        try {
            assertTrue(DungeonPartyFinderFloorTracker.observeOwnListingMenu(
                    "Party Finder", ownRoster("The Catacombs", "Floor VII"),
                    "LocalPlayer", 100L));
            assertTrue(DungeonPartyFinderFloorTracker.authoritativeGuiRoster().isPresent());

            DungeonPartyFinderFloorTracker.observeSystemMessage(
                    "Kicked [MVP+] GhostsTM because they were offline.");

            assertTrue(DungeonPartyFinderFloorTracker.authoritativeGuiRoster().isEmpty());
            assertFalse(DungeonPartyFinderFloorTracker.existingClasses("SomeoneElse").stream()
                    .anyMatch(member -> member.playerName().equalsIgnoreCase("GhostsTM")));
        } finally {
            DungeonPartyFinderFloorTracker.reset();
        }
    }

    @Test
    void pendingPartyFinderClassNeverBecomesAnActiveDupeMemberByItself() {
        DungeonPartyFinderFloorTracker.reset();
        try {
            DungeonJoinParser.DungeonJoinEvent event = DungeonJoinParser.event(
                    "Party Finder > GhostsTM joined the dungeon group! (Archer Level 9)")
                    .orElseThrow();

            DungeonPartyFinderFloorTracker.observeJoin(event);

            assertTrue(DungeonPartyFinderFloorTracker.existingClasses("SomeoneElse").isEmpty());
            assertTrue(DungeonPartyFinderFloorTracker
                    .authoritativeGuiRoster("SomeoneElse").isEmpty());
        } finally {
            DungeonPartyFinderFloorTracker.reset();
        }
    }

    @Test
    void partyFinderNewcomerArrivingBeforeFirstListingReadIsNeverTrusted() {
        DungeonPartyFinderFloorTracker.reset();
        try {
            DungeonPartyFinderFloorTracker.observeSystemMessage(
                    "Party Finder > Your party has been queued in the dungeon finder!");
            DungeonJoinParser.DungeonJoinEvent event = DungeonJoinParser.event(
                    "Party Finder > GhostsTM joined the dungeon group! (Archer Level 9)")
                    .orElseThrow();
            DungeonPartyFinderFloorTracker.observeJoin(event);

            assertTrue(DungeonPartyFinderFloorTracker.observeOwnListingMenu(
                    "Party Finder", ownRoster("The Catacombs", "Floor VII"),
                    "LocalPlayer", 100L));

            assertEquals(List.of("LocalPlayer", "ExistingTank"),
                    DungeonPartyFinderFloorTracker.existingClasses("SomeoneElse").stream()
                            .map(PartyMemberClass::playerName).toList());
            assertTrue(DungeonPartyFinderFloorTracker.authoritativeGuiRoster().orElseThrow()
                    .members().stream().anyMatch(member ->
                            member.playerName().equals("GhostsTM")));
        } finally {
            DungeonPartyFinderFloorTracker.reset();
        }
    }

    @Test
    void queuedConfirmationRetainsThePreviouslyProvenFloorWithoutAnotherGuiRead() {
        DungeonPartyFinderFloorTracker.reset();
        try {
            assertTrue(DungeonPartyFinderFloorTracker.observeOwnListingMenu(
                    "Party Finder", ownRoster("The Catacombs", "Floor VII"),
                    "LocalPlayer", 100L));
            DungeonPartyFinderFloorTracker.ListingContext beforeQueue =
                    DungeonPartyFinderFloorTracker.currentListing();

            DungeonPartyFinderFloorTracker.observeSystemMessage(
                    "Party Finder > Your party has been queued in the dungeon finder!");

            DungeonPartyFinderFloorTracker.ListingContext queuedListing =
                    DungeonPartyFinderFloorTracker.currentListing();
            assertEquals("F7", queuedListing.floor().id());
            assertTrue(queuedListing.generation() > beforeQueue.generation());
            assertTrue(DungeonPartyFinderFloorTracker.authoritativeGuiRoster().isEmpty());
            assertTrue(DungeonPartyFinderFloorTracker.existingClasses("NewPlayer").isEmpty());
        } finally {
            DungeonPartyFinderFloorTracker.reset();
        }
    }

    @Test
    void queuedConfirmationWithoutAProvenFloorStillHasNoListing() {
        DungeonPartyFinderFloorTracker.reset();
        try {
            DungeonPartyFinderFloorTracker.observeSystemMessage(
                    "Party Finder > Your party has been queued in the dungeon finder!");

            assertEquals(null, DungeonPartyFinderFloorTracker.currentListing());
            assertEquals(null, DungeonPartyFinderFloorTracker.currentFloor());
        } finally {
            DungeonPartyFinderFloorTracker.reset();
        }
    }

    @Test
    void groupBuilderConfirmSuppliesNormalFloorOnlyAfterExactQueueSuccess() {
        DungeonPartyFinderFloorTracker.reset();
        try {
            assertTrue(DungeonPartyFinderFloorTracker.observeGroupBuilderConfirm(
                    "Group Builder", groupBuilder("The Catacombs", "Floor VII"),
                    49, 1_000L));
            assertEquals(null, DungeonPartyFinderFloorTracker.currentFloor());

            DungeonPartyFinderFloorTracker.observeSystemMessage(
                    "Party Finder > Your party has been queued in the dungeon finder!",
                    1_001L);

            assertEquals("F7", DungeonPartyFinderFloorTracker.currentFloor().id());
            assertEquals("F7", DungeonPartyFinderFloorTracker.currentListing().floor().id());
        } finally {
            DungeonPartyFinderFloorTracker.reset();
        }
    }

    @Test
    void groupBuilderConfirmDistinguishesMasterModeAndEntrance() {
        DungeonPartyFinderFloorTracker.reset();
        try {
            assertTrue(DungeonPartyFinderFloorTracker.observeGroupBuilderConfirm(
                    "Group Builder", groupBuilder("Master Mode The Catacombs", "Floor VI"),
                    49, 2_000L));
            DungeonPartyFinderFloorTracker.observeSystemMessage(
                    "Party Finder > Your party has been queued in the dungeon finder!",
                    2_001L);
            assertEquals("M6", DungeonPartyFinderFloorTracker.currentFloor().id());

            DungeonPartyFinderFloorTracker.reset();
            assertTrue(DungeonPartyFinderFloorTracker.observeGroupBuilderConfirm(
                    "Group Builder", groupBuilder("The Catacombs", "Entrance"),
                    49, 3_000L));
            DungeonPartyFinderFloorTracker.observeSystemMessage(
                    "Party Finder > Your party has been queued in the dungeon finder!",
                    3_001L);
            assertEquals("E", DungeonPartyFinderFloorTracker.currentFloor().id());
        } finally {
            DungeonPartyFinderFloorTracker.reset();
        }
    }

    @Test
    void groupBuilderRejectsSearchSettingsWrongClickAndMalformedProof() {
        List<DungeonPartyFinderFloorTracker.MenuEntry> valid =
                groupBuilder("The Catacombs", "Floor VII");

        assertTrue(DungeonPartyFinderFloorTracker.floorFromGroupBuilder(
                "Search Settings", valid, 49).isEmpty());
        assertTrue(DungeonPartyFinderFloorTracker.floorFromGroupBuilder(
                "Group Builder", valid, 11).isEmpty());
        assertTrue(DungeonPartyFinderFloorTracker.floorFromGroupBuilder(
                "Group Builder", groupBuilder("The Rift", "Floor VII"), 49).isEmpty());
        assertTrue(DungeonPartyFinderFloorTracker.floorFromGroupBuilder(
                "Group Builder", List.of(
                        new DungeonPartyFinderFloorTracker.MenuEntry(11, "Select Dungeon Type",
                                List.of("Currently Selected: The Catacombs"), false, false),
                        new DungeonPartyFinderFloorTracker.MenuEntry(13, "Select Floor",
                                List.of("Currently Selected: Floor VII"), false, false),
                        new DungeonPartyFinderFloorTracker.MenuEntry(49, "Confirm Group",
                                List.of("Click to confirm!"), false, false)), 49).isEmpty());
        assertTrue(DungeonPartyFinderFloorTracker.floorFromGroupBuilder(
                "Group Builder", List.of(
                        new DungeonPartyFinderFloorTracker.MenuEntry(11, "Select Dungeon Type",
                                List.of("Currently Selected: The Catacombs"), false, false),
                        new DungeonPartyFinderFloorTracker.MenuEntry(13, "Select Floor",
                                List.of("Floor VII"), false, false),
                        new DungeonPartyFinderFloorTracker.MenuEntry(49, "Confirm Group",
                                List.of("Click to confirm!"), false, false, true)), 49).isEmpty());
    }

    @Test
    void groupBuilderCandidateExpiresAndIsClearedByReset() {
        DungeonPartyFinderFloorTracker.reset();
        try {
            assertTrue(DungeonPartyFinderFloorTracker.observeGroupBuilderConfirm(
                    "Group Builder", groupBuilder("The Catacombs", "Floor VII"),
                    49, 1_000L));
            DungeonPartyFinderFloorTracker.observeSystemMessage(
                    "Party Finder > Your party has been queued in the dungeon finder!",
                    1_000L + java.time.Duration.ofSeconds(15).toNanos() + 1L);
            assertEquals(null, DungeonPartyFinderFloorTracker.currentFloor());

            assertTrue(DungeonPartyFinderFloorTracker.observeGroupBuilderConfirm(
                    "Group Builder", groupBuilder("The Catacombs", "Floor VI"),
                    49, 2_000L));
            DungeonPartyFinderFloorTracker.reset();
            DungeonPartyFinderFloorTracker.observeSystemMessage(
                    "Party Finder > Your party has been queued in the dungeon finder!",
                    2_001L);
            assertEquals(null, DungeonPartyFinderFloorTracker.currentFloor());
        } finally {
            DungeonPartyFinderFloorTracker.reset();
        }
    }

    @Test
    void queuedListingFreezesExistingPlayersAndManualJoinsAsTrusted() {
        DungeonPartyFinderFloorTracker.reset();
        try {
            assertFalse(DungeonPartyFinderFloorTracker.observeSystemMessage(
                    "Party Finder > Your party has been queued in the dungeon finder!"));
            assertTrue(DungeonPartyFinderFloorTracker.observeOwnListingMenu(
                    "Party Finder", ownRoster("The Catacombs", "Floor VII"),
                    "LocalPlayer", 100L));
            assertTrue(DungeonPartyFinderFloorTracker.trustedBaselineCaptured());
            assertEquals(List.of("LocalPlayer", "GhostsTM", "ExistingTank"),
                    DungeonPartyFinderFloorTracker.existingClasses("NewPlayer").stream()
                            .map(PartyMemberClass::playerName).toList());

            DungeonPartyFinderFloorTracker.observeSystemMessage(
                    "[MVP+] ManualPlayer joined the party.");
            PartyMemberClass manual = DungeonPartyFinderFloorTracker
                    .existingClasses("NewPlayer").stream()
                    .filter(member -> member.playerName().equals("ManualPlayer"))
                    .findFirst().orElseThrow();
            assertEquals(null, manual.dungeonClass());

            DungeonPartyFinderFloorTracker.observeSystemMessage(
                    "ManualPlayer has left the party.");
            assertTrue(DungeonPartyFinderFloorTracker.existingClasses("NewPlayer").stream()
                    .noneMatch(member -> member.playerName().equals("ManualPlayer")));
        } finally {
            DungeonPartyFinderFloorTracker.reset();
        }
    }

    @Test
    void rejectedPartyFinderMemberNeverEntersTheDupeRoster() {
        DungeonPartyFinderFloorTracker.reset();
        try {
            DungeonJoinParser.DungeonJoinEvent event = DungeonJoinParser.event(
                    "Party Finder > GhostsTM joined the dungeon group! (Archer Level 9)")
                    .orElseThrow();
            DungeonPartyFinderFloorTracker.observeJoin(event);
            DungeonPartyFinderFloorTracker.rejectPartyFinderMember("GhostsTM");

            assertTrue(DungeonPartyFinderFloorTracker.existingClasses("SomeoneElse").isEmpty());
        } finally {
            DungeonPartyFinderFloorTracker.reset();
        }
    }

    @Test
    void localKickCommandImmediatelyDropsTheTrackedMember() {
        DungeonQuickViewManager.reset();
        try {
            assertTrue(DungeonPartyFinderFloorTracker.observeOwnListingMenu(
                    "Party Finder", ownRoster("The Catacombs", "Floor VII"),
                    "LocalPlayer", 100L));
            assertTrue(DungeonPartyFinderFloorTracker.existingClasses("SomeoneElse").stream()
                    .anyMatch(member -> member.playerName().equals("GhostsTM")));

            DungeonQuickViewManager.onOutgoingCommand("/party kick GhostsTM");

            assertTrue(DungeonPartyFinderFloorTracker.existingClasses("SomeoneElse").stream()
                    .noneMatch(member -> member.playerName().equals("GhostsTM")));
        } finally {
            DungeonQuickViewManager.reset();
        }
    }

    @Test
    void listingResetAndFloorChangeInvalidateThePreviousGuiRosterSnapshot() {
        DungeonPartyFinderFloorTracker.reset();
        try {
            assertTrue(DungeonPartyFinderFloorTracker.observeOwnListingMenu(
                    "Party Finder", ownRoster("The Catacombs", "Floor VII"),
                    "LocalPlayer", 100L));
            DungeonPartyFinderFloorTracker.ListingContext firstListing =
                    DungeonPartyFinderFloorTracker.currentListing();
            DungeonPartyFinderFloorTracker.GuiRosterSnapshot firstSnapshot =
                    DungeonPartyFinderFloorTracker.authoritativeGuiRoster("GhostsTM")
                            .orElseThrow();

            DungeonPartyFinderFloorTracker.observeSystemMessage(
                    "You removed your group from the Party Finder.");
            assertEquals(null, DungeonPartyFinderFloorTracker.currentListing());
            assertTrue(DungeonPartyFinderFloorTracker
                    .authoritativeGuiRoster("GhostsTM").isEmpty());

            assertTrue(DungeonPartyFinderFloorTracker.observeOwnListingMenu(
                    "Party Finder", ownRoster("Master Mode The Catacombs", "Floor VII"),
                    "LocalPlayer", 200L));
            DungeonPartyFinderFloorTracker.ListingContext changedListing =
                    DungeonPartyFinderFloorTracker.currentListing();
            DungeonPartyFinderFloorTracker.GuiRosterSnapshot changedSnapshot =
                    DungeonPartyFinderFloorTracker.authoritativeGuiRoster("GhostsTM")
                            .orElseThrow();

            assertFalse(firstListing.equals(changedListing));
            assertFalse(firstSnapshot.listingGeneration()
                    == changedSnapshot.listingGeneration());
            assertEquals("M7", changedSnapshot.floor().id());
            assertFalse(changedSnapshot.usableFor(firstListing, 150L, 201L, 10L));
        } finally {
            DungeonPartyFinderFloorTracker.reset();
        }
    }

    @Test
    void partyDisbandAndJoiningAnotherPartyInvalidateTheListingGeneration() {
        DungeonPartyFinderFloorTracker.reset();
        try {
            assertTrue(DungeonPartyFinderFloorTracker.observeOwnListingMenu(
                    "Party Finder", ownRoster("The Catacombs", "Floor VII"),
                    "LocalPlayer", 100L));
            long firstGeneration = DungeonPartyFinderFloorTracker.currentListing().generation();

            assertTrue(DungeonPartyFinderFloorTracker.observeSystemMessage(
                    "[MVP+] Leader has disbanded the party!"));
            assertEquals(null, DungeonPartyFinderFloorTracker.currentListing());
            assertTrue(DungeonPartyFinderFloorTracker.authoritativeGuiRoster().isEmpty());

            assertTrue(DungeonPartyFinderFloorTracker.observeOwnListingMenu(
                    "Party Finder", ownRoster("The Catacombs", "Floor VII"),
                    "LocalPlayer", 200L));
            assertTrue(DungeonPartyFinderFloorTracker.currentListing().generation()
                    > firstGeneration);

            assertTrue(DungeonPartyFinderFloorTracker.observeSystemMessage(
                    "You have joined [MVP+] Jess' party!"));
            assertEquals(null, DungeonPartyFinderFloorTracker.currentListing());
            assertTrue(DungeonPartyFinderFloorTracker.authoritativeGuiRoster().isEmpty());
        } finally {
            DungeonPartyFinderFloorTracker.reset();
        }
    }

    @Test
    void freshPostAdmissionOwnListingParseProducesACompleteFilteredSnapshot() {
        DungeonPartyFinderFloorTracker.reset();
        try {
            long admissionStartedAt = 1_000L;
            assertTrue(DungeonPartyFinderFloorTracker.observeOwnListingMenu(
                    "Party Finder", ownRoster("The Catacombs", "Floor VII"),
                    "LocalPlayer", admissionStartedAt + 1L));

            DungeonPartyFinderFloorTracker.ListingContext listing =
                    DungeonPartyFinderFloorTracker.currentListing();
            DungeonPartyFinderFloorTracker.GuiRosterSnapshot snapshot =
                    DungeonPartyFinderFloorTracker.authoritativeGuiRoster("GhostsTM")
                            .orElseThrow();
            DungeonPartyFinderFloorTracker.GuiRosterSnapshot fullSnapshot =
                    DungeonPartyFinderFloorTracker.authoritativeGuiRoster().orElseThrow();

            assertEquals("F7", snapshot.floor().id());
            assertEquals(listing.generation(), snapshot.listingGeneration());
            assertEquals(admissionStartedAt + 1L, snapshot.observedAtNanos());
            assertTrue(snapshot.complete());
            assertEquals(List.of("LocalPlayer", "ExistingTank"), snapshot.members().stream()
                    .map(PartyMemberClass::playerName).toList());
            assertEquals(DungeonClassKey.ARCHER, fullSnapshot.members().stream()
                    .filter(member -> member.playerName().equals("GhostsTM"))
                    .findFirst().orElseThrow().dungeonClass());
            assertTrue(snapshot.usableFor(listing, admissionStartedAt,
                    admissionStartedAt + 2L, 10L));
            assertFalse(snapshot.usableFor(listing, admissionStartedAt + 1L,
                    admissionStartedAt + 2L, 10L));
            assertFalse(snapshot.usableFor(listing, admissionStartedAt,
                    admissionStartedAt + 12L, 10L));
        } finally {
            DungeonPartyFinderFloorTracker.reset();
        }
    }

    @Test
    void conflictingSelectedClassMakesTheGuiRosterNonAuthoritative() {
        DungeonPartyFinderFloorTracker.reset();
        try {
            List<DungeonPartyFinderFloorTracker.MenuEntry> entries = List.of(
                    new DungeonPartyFinderFloorTracker.MenuEntry(45, "Class Selector",
                            List.of("Currently Selected: Archer"), false, false),
                    new DungeonPartyFinderFloorTracker.MenuEntry(48, "LocalPlayer's Party",
                            List.of("Dungeon: The Catacombs", "Floor: Floor VII",
                                    "LocalPlayer: Mage (Level 42)",
                                    "GhostsTM: Archer (Level 9)"), true, false),
                    new DungeonPartyFinderFloorTracker.MenuEntry(
                            53, "Delist Group", List.of(), false, true));

            assertTrue(DungeonPartyFinderFloorTracker.observeOwnListingMenu(
                    "Party Finder", entries, "LocalPlayer", 1_001L));
            DungeonPartyFinderFloorTracker.GuiRosterSnapshot snapshot =
                    DungeonPartyFinderFloorTracker.authoritativeGuiRoster().orElseThrow();

            assertFalse(snapshot.complete());
            assertFalse(snapshot.usableFor(
                    DungeonPartyFinderFloorTracker.currentListing(), 1_000L, 1_002L, 10L));
        } finally {
            DungeonPartyFinderFloorTracker.reset();
        }
    }

    private static List<DungeonPartyFinderFloorTracker.MenuEntry> ownRoster(
            String dungeon, String floor) {
        return List.of(
                new DungeonPartyFinderFloorTracker.MenuEntry(48, "LocalPlayer's Party",
                        List.of("Dungeon: " + dungeon, "Floor: " + floor,
                                "LocalPlayer: Mage (Level 42)",
                                "GhostsTM: Archer (Level 9)",
                                "ExistingTank: Tank (Level 35)"), true, false),
                new DungeonPartyFinderFloorTracker.MenuEntry(
                        53, "Delist Group", List.of(), false, true));
    }

    private static List<DungeonPartyFinderFloorTracker.MenuEntry> groupBuilder(
            String dungeon, String floor) {
        return List.of(
                new DungeonPartyFinderFloorTracker.MenuEntry(11, "Select Dungeon Type",
                        List.of("Currently Selected: " + dungeon), false, false),
                new DungeonPartyFinderFloorTracker.MenuEntry(13, "Select Floor",
                        List.of("Currently Selected: " + floor), false, false),
                new DungeonPartyFinderFloorTracker.MenuEntry(49, "Confirm Group",
                        List.of("Click to confirm!"), false, false, true));
    }
}

package cloudy.autume.addition.dungeon;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}

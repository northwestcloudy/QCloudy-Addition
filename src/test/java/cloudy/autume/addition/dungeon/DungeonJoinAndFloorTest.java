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
}

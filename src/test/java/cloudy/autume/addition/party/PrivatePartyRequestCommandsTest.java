package cloudy.autume.addition.party;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PrivatePartyRequestCommandsTest {
    @Test
    void acceptsOnlyExactEnglishIncomingDmKeywords() {
        PrivatePartyRequestCommands commands = new PrivatePartyRequestCommands();
        long now = 0L;
        for (String keyword : List.of("!p", "!party", "!invite", "!PARTY")) {
            assertEquals("party invite NorthWestCloudy",
                    commands.handleIncomingDirectMessage(
                            "From [MVP+] NorthWestCloudy: " + keyword, now).orElseThrow());
            now += PrivatePartyRequestCommands.DM_DEDUPE_NANOS;
        }

        assertTrue(commands.handleIncomingDirectMessage("To NorthWestCloudy: !p", now).isEmpty());
        assertTrue(commands.handleIncomingDirectMessage("Party > NorthWestCloudy: !p", now).isEmpty());
        assertTrue(commands.handleIncomingDirectMessage("From NorthWestCloudy: !p please", now).isEmpty());
        assertEquals("party invite stash",
                commands.handleIncomingDirectMessage("From stash: !p", now).orElseThrow());
        assertTrue(commands.handleIncomingDirectMessage("From Bad-Name: !p", now).isEmpty());
    }

    @Test
    void deduplicatesBySenderAndKeywordForTwoSecondsAndResetStartsANewSession() {
        PrivatePartyRequestCommands commands = new PrivatePartyRequestCommands();
        assertEquals("party invite Alice",
                commands.handleIncomingDirectMessage("From Alice: !p", 0L).orElseThrow());
        assertTrue(commands.handleIncomingDirectMessage("From Alice: !p", 1_999_999_999L).isEmpty());

        // A different keyword has its own key even for the same sender.
        assertEquals("party invite Alice",
                commands.handleIncomingDirectMessage("From Alice: !party", 1L).orElseThrow());
        assertEquals("party invite Alice",
                commands.handleIncomingDirectMessage("From Alice: !p", 2_000_000_000L).orElseThrow());

        commands.resetSession();
        assertEquals("party invite Alice",
                commands.handleIncomingDirectMessage("From Alice: !p", 2_000_000_001L).orElseThrow());
    }

    @Test
    void mapsAllThreeQuickDmShapesAndNormalizesWhitespace() {
        assertLocal("//invited by NorthWestCloudy", "msg NorthWestCloudy !p");
        assertLocal(" //INVITED   NorthWestCloudy ", "msg NorthWestCloudy !p");
        assertLocal("//i NorthWestCloudy", "msg NorthWestCloudy !p");
    }

    @Test
    void unknownCommandsPassThroughButRecognizedMalformedInputIsCanceled() {
        PrivatePartyRequestCommands.LocalResult unknown =
                PrivatePartyRequestCommands.fromLocalDoubleSlash("//invite NorthWestCloudy");
        assertEquals(PrivatePartyRequestCommands.Status.IGNORED, unknown.status());
        assertFalse(unknown.shouldCancelInput());

        for (String invalid : List.of(
                "//i", "//i First Second", "//invited by", "//invited by First Second")) {
            PrivatePartyRequestCommands.LocalResult result =
                    PrivatePartyRequestCommands.fromLocalDoubleSlash(invalid);
            assertEquals(PrivatePartyRequestCommands.Status.ERROR, result.status());
            assertEquals(PrivatePartyRequestCommands.ErrorKind.INVALID_ARGUMENTS, result.error());
            assertTrue(result.shouldCancelInput());
        }

        PrivatePartyRequestCommands.LocalResult invalidPlayer =
                PrivatePartyRequestCommands.fromLocalDoubleSlash("//i bad-name");
        assertEquals(PrivatePartyRequestCommands.ErrorKind.INVALID_PLAYER, invalidPlayer.error());
    }

    private static void assertLocal(String input, String payload) {
        PrivatePartyRequestCommands.LocalResult result =
                PrivatePartyRequestCommands.fromLocalDoubleSlash(input);
        assertEquals(PrivatePartyRequestCommands.Status.COMMAND, result.status());
        assertEquals(payload, result.payload());
        assertTrue(result.shouldCancelInput());
    }
}

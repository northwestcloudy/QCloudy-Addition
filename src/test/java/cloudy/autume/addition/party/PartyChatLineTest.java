package cloudy.autume.addition.party;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PartyChatLineTest {
    @Test
    void parsesOnlyExactEnglishPartyChatWithOptionalRank() {
        PartyChatLine ranked = PartyChatLine.parse("Party > [MVP+] NorthWestCloudy: !warp").orElseThrow();
        assertEquals("NorthWestCloudy", ranked.sender());
        assertEquals("!warp", ranked.message());

        PartyChatLine plain = PartyChatLine.parse("§9Party > §aAlice§f:   !PT   north  ").orElseThrow();
        assertEquals("Alice", plain.sender());
        assertEquals("!PT   north", plain.message());

        PartyChatLine badged = PartyChatLine.parse(
                "Party > [MVP++] NorthWestCloudy ♲: !stream 25").orElseThrow();
        assertEquals("NorthWestCloudy", badged.sender());
        assertEquals("!stream 25", badged.message());
    }

    @Test
    void rejectsOtherChannelsPublicMessagesDirectMessagesAndMultilineLookalikes() {
        assertTrue(PartyChatLine.parse("Guild > [MVP+] NorthWestCloudy: !warp").isEmpty());
        assertTrue(PartyChatLine.parse("Officer > NorthWestCloudy: !warp").isEmpty());
        assertTrue(PartyChatLine.parse("From NorthWestCloudy: !warp").isEmpty());
        assertTrue(PartyChatLine.parse("[MVP+] NorthWestCloudy: Party > Alice: !warp").isEmpty());
        assertTrue(PartyChatLine.parse("Party > Bad-Name: !warp").isEmpty());
        assertTrue(PartyChatLine.parse("Party > Alice: !warp\nParty > Bob: !warp").isEmpty());
    }
}

package cloudy.autume.addition.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ChatChannelLayoutTest {
    @Test
    void preferredLayoutAnchorsAboveInputAndStaysInsideItsWidth() {
        List<ChatChannelLayout.Slot> slots = ChatChannelLayout.calculate(
                4, 188, 312, ChatChannel.visibleChannels(false), channel -> 40, channel -> 20);

        assertEquals(4, slots.size());
        assertFalse(slots.getFirst().compact());
        assertEquals(4, slots.getFirst().x());
        assertEquals(170, slots.getFirst().y());
        assertTrue(slots.getLast().x() + slots.getLast().width() <= 316);
    }

    @Test
    void compactLayoutKeepsOfficerOrderAndNeverOverflows() {
        List<ChatChannelLayout.Slot> slots = ChatChannelLayout.calculate(
                8, 100, 108, ChatChannel.visibleChannels(true), channel -> 40, channel -> 20);

        assertEquals(5, slots.size());
        assertTrue(slots.getFirst().compact());
        assertEquals(ChatChannel.OFFICER, slots.get(3).channel());
        assertEquals(ChatChannel.COOP, slots.get(4).channel());
        assertTrue(slots.getLast().x() + slots.getLast().width() <= 116);
    }

    @Test
    void impossiblyNarrowInputHidesTheWholeRowInsteadOfClipping() {
        assertEquals(List.of(), ChatChannelLayout.calculate(
                4, 20, 60, ChatChannel.visibleChannels(false), channel -> 40, channel -> 18));
    }
}

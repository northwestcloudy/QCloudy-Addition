package cloudy.autume.addition.chat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ChatChannelTrackerTest {
    @AfterEach
    void resetSharedTracker() {
        ChatChannelSwitcher.trackerForTests().reset();
    }

    @Test
    void commandsAreFullServerPayloadsWithoutLeadingSlash() {
        assertEquals("chat all", ChatChannel.ALL.command());
        assertEquals("chat party", ChatChannel.PARTY.command());
        assertEquals("chat guild", ChatChannel.GUILD.command());
        assertEquals("chat officer", ChatChannel.OFFICER.command());
        assertEquals("chat coop", ChatChannel.COOP.command());
        assertEquals(java.util.List.of(ChatChannel.ALL, ChatChannel.PARTY,
                        ChatChannel.GUILD, ChatChannel.COOP),
                ChatChannel.visibleChannels(false));
        assertEquals(java.util.List.of(ChatChannel.ALL, ChatChannel.PARTY,
                        ChatChannel.GUILD, ChatChannel.OFFICER, ChatChannel.COOP),
                ChatChannel.visibleChannels(true));
    }

    @Test
    void parsesExactEnglishChineseMixedAndForcedAllFeedback() {
        assertEquals(ChatChannel.OFFICER,
                ChatChannelTracker.parse("§aYou are now in the §6OFFICER§a channel").channel());
        assertEquals(ChatChannel.GUILD,
                ChatChannelTracker.parse("You are now in the 公会 channel!").channel());
        assertEquals(ChatChannel.COOP,
                ChatChannelTracker.parse("你正处于空岛生存合作模式频道中！").channel());
        assertEquals(ChatChannel.ALL,
                ChatChannelTracker.parse("You are not in a party and were moved to the ALL channel.").channel());
        assertEquals(ChatChannel.ALL,
                ChatChannelTracker.parse("The conversation you were in expired and you have been moved back to the ALL channel.").channel());
        assertNull(ChatChannelTracker.parse("Guild > Player: You are now in the PARTY channel").channel());
    }

    @Test
    void requestNeedsAcknowledgementAndRejectRetainsPreviousConfirmation() {
        ChatChannelTracker tracker = new ChatChannelTracker(1_000L);
        tracker.observe("You are now in the ALL channel", 10L);
        assertEquals(ChatChannel.ALL, tracker.snapshot(10L).confirmed());

        assertTrue(tracker.beginRequest(ChatChannel.PARTY, 20L));
        assertEquals(ChatChannel.PARTY, tracker.snapshot(20L).pending());
        assertEquals(ChatChannel.ALL, tracker.snapshot(20L).confirmed());
        assertFalse(tracker.beginRequest(ChatChannel.GUILD, 21L));

        tracker.observe("You are not currently in a party.", 30L);
        ChatChannelTracker.Snapshot rejected = tracker.snapshot(30L);
        assertEquals(ChatChannelTracker.Phase.CONFIRMED, rejected.phase());
        assertEquals(ChatChannel.ALL, rejected.confirmed());
        assertNull(rejected.pending());
    }

    @Test
    void timeoutBecomesUnknownAndLateAuthoritativeAckStillWins() {
        ChatChannelTracker tracker = new ChatChannelTracker(100L);
        tracker.observe("You are now in the GUILD channel", 0L);
        assertTrue(tracker.beginRequest(ChatChannel.PARTY, 10L));

        ChatChannelTracker.Snapshot timedOut = tracker.snapshot(110L);
        assertEquals(ChatChannelTracker.Phase.UNKNOWN, timedOut.phase());
        assertNull(timedOut.confirmed());
        assertNull(timedOut.pending());

        tracker.observe("You are now in the PARTY channel", 120L);
        assertEquals(ChatChannel.PARTY, tracker.snapshot(120L).confirmed());
    }

    @Test
    void unrelatedAndOutOfOrderMessagesCannotConfirmRequestedTarget() {
        ChatChannelTracker tracker = new ChatChannelTracker(1_000L);
        assertTrue(tracker.beginRequest(ChatChannel.PARTY, 0L));
        tracker.observe("Party > Player: hello", 10L);
        assertEquals(ChatChannel.PARTY, tracker.snapshot(10L).pending());

        tracker.observe("You are now in the GUILD channel", 20L);
        ChatChannelTracker.Snapshot authoritative = tracker.snapshot(20L);
        assertEquals(ChatChannel.GUILD, authoritative.confirmed());
        assertNull(authoritative.pending());
    }

    @Test
    void pendingPlainChatIsBlockedButCommandsAndBlankInputAreAllowed() {
        long now = System.nanoTime();
        ChatChannelSwitcher.trackerForTests().beginRequest(ChatChannel.PARTY, now);

        assertTrue(ChatChannelSwitcher.shouldBlockPlainMessage("sensitive draft"));
        assertFalse(ChatChannelSwitcher.shouldBlockPlainMessage("  /party list"));
        assertFalse(ChatChannelSwitcher.shouldBlockPlainMessage("  "));
    }
}

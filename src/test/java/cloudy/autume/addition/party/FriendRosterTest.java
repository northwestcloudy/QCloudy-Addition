package cloudy.autume.addition.party;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FriendRosterTest {
    private static final UUID PROFILE_ID = UUID.fromString("503450fc-72c2-4e87-8243-94e264977437");

    @Test
    void parsesOnlyStructuredFriendListRowsAndUsesEffectiveBoldStyle() {
        FriendRoster roster = new FriendRoster();
        Component list = Component.literal("Friends (Page 1 of 1)\n")
                .append(friendRow("OrdinaryFriend", false, false))
                .append(Component.literal("\n"))
                .append(friendRow("BestFriend", true, false));

        assertTrue(roster.observe(list));
        assertTrue(roster.isKnown());
        assertEquals(FriendRoster.FriendKind.NORMAL, roster.kindOf("ordinaryfriend"));
        assertEquals(FriendRoster.FriendKind.SPECIAL, roster.kindOf("BESTFRIEND"));
    }

    @Test
    void aggregatesFragmentsAndRecognizesLegacyBoldWithoutDowngradingSpecialFriend() {
        FriendRoster roster = new FriendRoster();
        Component list = Component.literal("Friends (Page 1 of 1)\n")
                .append(friendRow("Cloudy", true, false))
                .append(friendRow("Cloudy", false, false))
                .append(friendRow("Legacy", false, true));

        assertTrue(roster.observe(list));
        assertEquals(FriendRoster.FriendKind.SPECIAL, roster.kindOf("Cloudy"));
        assertEquals(FriendRoster.FriendKind.SPECIAL, roster.kindOf("Legacy"));
    }

    @Test
    void rejectsPlainTextLookalikesAndRowsWithoutMatchingStructuredSignals() {
        FriendRoster roster = new FriendRoster();

        assertFalse(roster.observe(Component.literal(
                "Friends\nClick here to view FakeFriend's profile /viewprofile " + PROFILE_ID)));
        assertFalse(roster.isKnown());
        assertNull(roster.kindOf("FakeFriend"));

        Component invalidProfile = Component.literal("WrongHover").withStyle(style -> style
                .withClickEvent(new ClickEvent.RunCommand("/viewprofile not-a-uuid"))
                .withHoverEvent(new HoverEvent.ShowText(
                        Component.literal("Click here to view OtherName's profile"))));
        assertFalse(roster.observe(Component.literal("Friends\n").append(invalidProfile)));
        assertNull(roster.kindOf("WrongHover"));
    }

    @Test
    void confirmedFriendAndBestFriendMessagesUpdateCachedClassification() {
        FriendRoster roster = new FriendRoster();
        assertTrue(roster.observe(Component.literal("Friends (Page 1 of 1)")));

        assertTrue(roster.observe(Component.literal("You are now friends with [MVP+] NewFriend!")));
        assertEquals(FriendRoster.FriendKind.NORMAL, roster.kindOf("newfriend"));

        assertTrue(roster.observe(Component.literal("[MVP+] NewFriend is now a best friend!")));
        assertEquals(FriendRoster.FriendKind.SPECIAL, roster.kindOf("NEWFRIEND"));

        assertTrue(roster.observe(Component.literal("NewFriend is no longer a best friend!")));
        assertEquals(FriendRoster.FriendKind.NORMAL, roster.kindOf("NewFriend"));

        assertTrue(roster.observe(Component.literal(
                "You removed [MVP+] NewFriend from your friends list!")));
        assertNull(roster.kindOf("NewFriend"));
    }

    @Test
    void completeSnapshotRemovesFriendsMissingFromTheRefreshedRoster() {
        FriendRoster roster = new FriendRoster();
        assertTrue(roster.observe(Component.literal("Friends (Page 1 of 1)\n")
                .append(friendRow("FormerFriend", false, false))));
        assertEquals(FriendRoster.FriendKind.NORMAL, roster.kindOf("FormerFriend"));

        assertTrue(roster.observe(Component.literal("Friends (Page 1 of 1)\n")
                .append(friendRow("CurrentFriend", false, false))));
        assertTrue(roster.isKnown());
        assertNull(roster.kindOf("FormerFriend"));
        assertEquals(FriendRoster.FriendKind.NORMAL, roster.kindOf("CurrentFriend"));
    }

    @Test
    void paginatedSnapshotIsUntrustedUntilEveryPageArrivesInOrder() {
        FriendRoster roster = new FriendRoster();
        assertTrue(roster.observe(Component.literal("Friends (Page 1 of 1)\n")
                .append(friendRow("FormerFriend", false, false))));

        assertTrue(roster.observe(Component.literal("Friends (Page 1 of 2)\n")
                .append(friendRow("FirstPage", false, false))));
        assertFalse(roster.isKnown());
        assertNull(roster.kindOf("FormerFriend"));
        assertNull(roster.kindOf("FirstPage"));

        assertTrue(roster.observe(Component.literal("Friends (Page 2 of 2)\n")
                .append(friendRow("SecondPage", true, false))));
        assertTrue(roster.isKnown());
        assertNull(roster.kindOf("FormerFriend"));
        assertEquals(FriendRoster.FriendKind.NORMAL, roster.kindOf("FirstPage"));
        assertEquals(FriendRoster.FriendKind.SPECIAL, roster.kindOf("SecondPage"));
    }

    @Test
    void partialOutOfOrderAndUnnumberedListsNeverBecomeAuthoritative() {
        FriendRoster roster = new FriendRoster();
        assertTrue(roster.observe(Component.literal("Friends (Page 1 of 1)\n")
                .append(friendRow("CachedFriend", false, false))));

        assertTrue(roster.observe(Component.literal("Friends (Page 2 of 2)\n")
                .append(friendRow("OnlySecondPage", false, false))));
        assertFalse(roster.isKnown());
        assertNull(roster.kindOf("CachedFriend"));
        assertNull(roster.kindOf("OnlySecondPage"));

        assertFalse(roster.observe(Component.literal("Friends\n")
                .append(friendRow("UnnumberedFriend", false, false))));
        assertFalse(roster.isKnown());
        assertNull(roster.kindOf("UnnumberedFriend"));
    }

    @Test
    void confirmedRemovalInvalidatesPendingSnapshotSoTailPageCannotResurrectFriend() {
        FriendRoster roster = new FriendRoster();
        assertTrue(roster.observe(Component.literal("Friends (Page 1 of 1)\n")
                .append(friendRow("RemovedInSync", false, false))));
        assertTrue(roster.observe(Component.literal("Friends (Page 1 of 2)\n")
                .append(friendRow("RemovedInSync", false, false))));

        assertTrue(roster.observe(Component.literal(
                "You removed RemovedInSync from your friends list!")));
        assertFalse(roster.isKnown());
        assertNull(roster.kindOf("RemovedInSync"));

        assertFalse(roster.observe(Component.literal("Friends (Page 2 of 2)\n")
                .append(friendRow("TailPageFriend", false, false))));
        assertFalse(roster.isKnown());
        assertNull(roster.kindOf("RemovedInSync"));
        assertNull(roster.kindOf("TailPageFriend"));
        assertFalse(roster.serializedFriends().containsKey("RemovedInSync"));
    }

    @Test
    void confirmedBestStatusInvalidatesPendingSnapshotSoTailPageCannotOverwriteIt() {
        FriendRoster roster = new FriendRoster();
        assertTrue(roster.observe(Component.literal("Friends (Page 1 of 1)\n")
                .append(friendRow("ChangedInSync", false, false))));
        assertTrue(roster.observe(Component.literal("Friends (Page 1 of 2)\n")
                .append(friendRow("ChangedInSync", false, false))));

        assertTrue(roster.observe(Component.literal(
                "ChangedInSync is now a best friend!")));
        assertFalse(roster.isKnown());
        assertEquals("SPECIAL", roster.serializedFriends().get("ChangedInSync"));

        assertFalse(roster.observe(Component.literal("Friends (Page 2 of 2)\n")
                .append(friendRow("TailPageFriend", false, false))));
        assertFalse(roster.isKnown());
        assertEquals("SPECIAL", roster.serializedFriends().get("ChangedInSync"));
        assertFalse(roster.serializedFriends().containsKey("TailPageFriend"));
    }

    @Test
    void usernamesAreStrictlyValidatedForSafeCommandConstruction() {
        assertTrue(FriendRoster.validUsername("a"));
        assertTrue(FriendRoster.validUsername("Player_Name_123"));
        assertFalse(FriendRoster.validUsername(""));
        assertFalse(FriendRoster.validUsername("seventeen_chars_x"));
        assertFalse(FriendRoster.validUsername("Player Name"));
        assertFalse(FriendRoster.validUsername("Player/party"));
    }

    private static Component friendRow(String name, boolean bold, boolean legacyBold) {
        String visible = legacyBold ? "§l" + name : name;
        return Component.literal(visible).withStyle(style -> style
                .withBold(bold)
                .withClickEvent(new ClickEvent.RunCommand("/viewprofile " + PROFILE_ID))
                .withHoverEvent(new HoverEvent.ShowText(
                        Component.literal("Click here to view " + name + "'s profile"))));
    }
}

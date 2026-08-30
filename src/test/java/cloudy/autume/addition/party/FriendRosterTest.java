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

        Component mismatchedName = Component.literal("VisibleName").withStyle(style -> style
                .withClickEvent(new ClickEvent.RunCommand("/viewprofile " + PROFILE_ID))
                .withHoverEvent(new HoverEvent.ShowText(
                        Component.literal("Click here to view OtherName's profile"))));
        assertFalse(roster.observe(Component.literal("Friends (Page 1 of 1)\n")
                .append(mismatchedName)));
        assertNull(roster.kindOf("OtherName"));
    }

    @Test
    void confirmedFriendAndBestFriendMessagesUpdateCachedClassification() {
        FriendRoster roster = new FriendRoster();
        assertTrue(roster.observe(Component.literal(
                "Friends (Page 1 of 1)\n-----------------------------------------------------")));

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
    void verifiedCurrentPageIsImmediatelyUsableButOnlyTheFullSnapshotPrunes() {
        FriendRoster roster = new FriendRoster();
        assertTrue(roster.observe(Component.literal("Friends (Page 1 of 1)\n")
                .append(friendRow("FormerFriend", false, false))));

        assertTrue(roster.observe(Component.literal("Friends (Page 1 of 2)\n")
                .append(friendRow("FirstPage", false, false))));
        assertFalse(roster.isKnown());
        assertNull(roster.kindOf("FormerFriend"));
        assertEquals(FriendRoster.FriendKind.NORMAL, roster.kindOf("FirstPage"));

        assertTrue(roster.observe(Component.literal("Friends (Page 2 of 2)\n")
                .append(friendRow("SecondPage", true, false))));
        assertTrue(roster.isKnown());
        assertNull(roster.kindOf("FormerFriend"));
        assertEquals(FriendRoster.FriendKind.NORMAL, roster.kindOf("FirstPage"));
        assertEquals(FriendRoster.FriendKind.SPECIAL, roster.kindOf("SecondPage"));
    }

    @Test
    void outOfOrderPageCanProveItsRowsButCannotBecomeAuthoritative() {
        FriendRoster roster = new FriendRoster();
        assertTrue(roster.observe(Component.literal("Friends (Page 1 of 1)\n")
                .append(friendRow("CachedFriend", false, false))));

        assertTrue(roster.observe(Component.literal("Friends (Page 2 of 2)\n")
                .append(friendRow("OnlySecondPage", false, false))));
        assertFalse(roster.isKnown());
        assertNull(roster.kindOf("CachedFriend"));
        assertEquals(FriendRoster.FriendKind.NORMAL, roster.kindOf("OnlySecondPage"));

        assertFalse(roster.observe(Component.literal("Friends\n")
                .append(friendRow("UnnumberedFriend", false, false))));
        assertFalse(roster.isKnown());
        assertNull(roster.kindOf("UnnumberedFriend"));
        assertNull(roster.kindOf("OnlySecondPage"));
    }

    @Test
    void streamedHeaderRowsAndFooterTrustEachStructuredRowAndPreserveBoldKind() {
        FriendRoster roster = new FriendRoster();

        assertFalse(roster.observe(Component.literal("Friends (Page 1 of 3) >>")));
        assertTrue(roster.observe(friendStatusRow("_Hoto_cocoa_", false,
                " is in SkyBlock - Crimson Isle")));
        assertEquals(FriendRoster.FriendKind.NORMAL, roster.kindOf("_hoto_cocoa_"));
        assertTrue(roster.observe(friendStatusRow("TangXin_Vlog", true,
                " is currently offline")));
        assertEquals(FriendRoster.FriendKind.SPECIAL, roster.kindOf("tangxin_vlog"));
        assertFalse(roster.observe(Component.literal("-----------------------------------------------------")));
        assertFalse(roster.isKnown());

        assertFalse(roster.observe(Component.literal("Friends (Page 2 of 3) >>")));
        assertTrue(roster.observe(friendStatusRow("EtzHaChayim", false,
                " is in SkyBlock - Moonglade Marsh")));
        assertFalse(roster.observe(Component.literal("-----------------------------------------------------")));
        assertFalse(roster.observe(Component.literal("Friends (Page 3 of 3) >>")));
        assertTrue(roster.observe(friendStatusRow("NIHTT", false,
                " is in a Prototype Lobby")));
        assertTrue(roster.observe(Component.literal("-----------------------------------------------------")));

        assertTrue(roster.isKnown());
        assertEquals(FriendRoster.FriendKind.NORMAL, roster.kindOf("_Hoto_cocoa_"));
        assertEquals(FriendRoster.FriendKind.SPECIAL, roster.kindOf("TangXin_Vlog"));
        assertEquals(FriendRoster.FriendKind.NORMAL, roster.kindOf("EtzHaChayim"));
        assertEquals(FriendRoster.FriendKind.NORMAL, roster.kindOf("NIHTT"));
    }

    @Test
    void streamedCaptureAbortsSnapshotButKeepsProvenRowsAndRejectsPlainLookalikes() {
        FriendRoster roster = new FriendRoster();

        assertFalse(roster.observe(Component.literal("Friends (Page 1 of 2) >>")));
        assertTrue(roster.observe(friendStatusRow("ProvenFriend", false, " is currently offline")));
        assertEquals(FriendRoster.FriendKind.NORMAL, roster.kindOf("ProvenFriend"));
        assertFalse(roster.observe(Component.literal("unrelated chat interrupted the list")));
        assertEquals(FriendRoster.FriendKind.NORMAL, roster.kindOf("ProvenFriend"));
        assertFalse(roster.observe(Component.literal("FakeFriend is currently offline")));
        assertNull(roster.kindOf("FakeFriend"));
    }

    @Test
    void structuredGuildChatLookalikeAbortsStreamAndCannotPolluteRoster() {
        FriendRoster roster = new FriendRoster();
        assertFalse(roster.observe(Component.literal("Friends (Page 1 of 2) >>")));

        Component guildChat = Component.literal("Guild > ")
                .append(friendRow("GuildMember", false, false))
                .append(Component.literal(": is in SkyBlock - Hub"));
        assertFalse(roster.observe(guildChat));
        assertFalse(roster.isKnown());
        assertNull(roster.kindOf("GuildMember"));
        assertFalse(roster.serializedFriends().containsKey("GuildMember"));

        assertFalse(roster.observe(Component.literal("Friends (Page 1 of 2) >>")));
        Component publicChat = Component.literal("[MVP+] ")
                .append(friendRow("PublicPlayer", false, false))
                .append(Component.literal(": is currently offline"));
        assertFalse(roster.observe(publicChat));
        assertNull(roster.kindOf("PublicPlayer"));
        assertFalse(roster.serializedFriends().containsKey("PublicPlayer"));

        // The invalid structured line ended the page transaction, so a later
        // row cannot silently resume it without another valid /fl header.
        assertFalse(roster.observe(friendStatusRow("LaterFriend", false,
                " is currently offline")));
        assertNull(roster.kindOf("LaterFriend"));
    }

    @Test
    void streamedStatusRowsAreAnchoredBoundedAndSingleLine() {
        FriendRoster roster = new FriendRoster();
        assertFalse(roster.observe(Component.literal("Friends (Page 1 of 2) >>")));
        assertFalse(roster.observe(friendStatusRow("Prefixed", false,
                ": is currently offline")));
        assertNull(roster.kindOf("Prefixed"));

        assertFalse(roster.observe(Component.literal("Friends (Page 1 of 2) >>")));
        assertFalse(roster.observe(friendStatusRow("Multiline", false,
                " is in SkyBlock - Hub\nextra text")));
        assertNull(roster.kindOf("Multiline"));

        assertFalse(roster.observe(Component.literal("Friends (Page 1 of 2) >>")));
        assertFalse(roster.observe(friendStatusRow("TooLong", false,
                " is in " + "x".repeat(161))));
        assertNull(roster.kindOf("TooLong"));
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

        assertTrue(roster.observe(Component.literal("Friends (Page 2 of 2)\n")
                .append(friendRow("TailPageFriend", false, false))));
        assertFalse(roster.isKnown());
        assertNull(roster.kindOf("RemovedInSync"));
        assertEquals(FriendRoster.FriendKind.NORMAL, roster.kindOf("TailPageFriend"));
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

        assertTrue(roster.observe(Component.literal("Friends (Page 2 of 2)\n")
                .append(friendRow("TailPageFriend", false, false))));
        assertFalse(roster.isKnown());
        assertNull(roster.kindOf("ChangedInSync"));
        assertEquals("SPECIAL", roster.serializedFriends().get("ChangedInSync"));
        assertEquals(FriendRoster.FriendKind.NORMAL, roster.kindOf("TailPageFriend"));
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

    private static Component friendStatusRow(String name, boolean bold, String status) {
        return Component.empty().append(friendRow(name, bold, false)).append(Component.literal(status));
    }
}

package cloudy.autume.addition.party;

import cloudy.autume.addition.config.ModConfig;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PartyAutoAcceptManagerTest {
    private static final String ACCOUNT = "7f79772a-f432-4725-833a-1945b180e567";
    private static final UUID PROFILE_ID = UUID.fromString("503450fc-72c2-4e87-8243-94e264977437");

    @TempDir
    Path temporaryDirectory;

    @Test
    void ordinaryAndSpecialModesAcceptOnlyTheirExactFriendClassification() {
        PartyAutoAcceptManager manager = manager();
        observeRoster(manager);

        assertEquals("party accept OrdinaryFriend", invite(manager, "OrdinaryFriend",
                ModConfig.PartyAcceptFriendMode.NORMAL_ONLY, List.of(), 1_000L));
        assertNull(invite(manager, "BestFriend",
                ModConfig.PartyAcceptFriendMode.NORMAL_ONLY, List.of(), 2_000L));

        assertEquals("party accept BestFriend", invite(manager, "BestFriend",
                ModConfig.PartyAcceptFriendMode.SPECIAL_ONLY, List.of(), 3_000L));
        assertNull(invite(manager, "OrdinaryFriend",
                ModConfig.PartyAcceptFriendMode.SPECIAL_ONLY, List.of(), 4_000L));
        assertNull(invite(manager, "UnknownPlayer",
                ModConfig.PartyAcceptFriendMode.NORMAL_ONLY, List.of(), 5_000L));
    }

    @Test
    void whitelistHasPriorityOverFriendModeAndIsCaseInsensitiveAndBounded() {
        PartyAutoAcceptManager manager = manager();
        observeRoster(manager);

        assertEquals("party accept BestFriend", invite(manager, "BestFriend",
                ModConfig.PartyAcceptFriendMode.NORMAL_ONLY, List.of("bestfriend"), 1_000L));
        assertEquals("party accept UnknownPlayer", invite(manager, "UnknownPlayer",
                ModConfig.PartyAcceptFriendMode.SPECIAL_ONLY, List.of(" unknownPLAYER "), 2_000L));

        List<String> oversized = new ArrayList<>();
        for (int index = 0; index < 16; index++) oversized.add("Allowed" + index);
        oversized.add("Seventeenth");
        oversized.add("Bad Name");
        assertEquals(16, PartyAutoAcceptManager.normalizedWhitelist(oversized).size());
        assertNull(invite(manager, "Seventeenth",
                ModConfig.PartyAcceptFriendMode.NORMAL_ONLY, oversized, 3_000L));
    }

    @Test
    void acceptsOnlyConfirmedDirectEnglishAndChineseInviteShapes() {
        PartyAutoAcceptManager manager = manager();
        List<String> whitelist = List.of("Cloudy", "ChineseUser");

        assertEquals("party accept Cloudy", manager.onMessage(
                Component.literal("[MVP+] Cloudy has invited you to join their party!"),
                false, true, ACCOUNT, true, ModConfig.PartyAcceptFriendMode.NORMAL_ONLY,
                whitelist, 1_000L));
        assertEquals("party accept ChineseUser", manager.onMessage(
                Component.literal("[VIP] ChineseUser 邀请你加入他的组队！"),
                false, true, ACCOUNT, true, ModConfig.PartyAcceptFriendMode.NORMAL_ONLY,
                whitelist, 2_000L));

        assertNull(manager.onMessage(Component.literal("Cloudy invited everyone to join the party!"),
                false, true, ACCOUNT, true, ModConfig.PartyAcceptFriendMode.NORMAL_ONLY,
                whitelist, 3_000L));
        assertNull(manager.onMessage(Component.literal("You have been invited to join the party!"),
                false, true, ACCOUNT, true, ModConfig.PartyAcceptFriendMode.NORMAL_ONLY,
                whitelist, 4_000L));
        assertNull(manager.onMessage(Component.literal(
                        "Cloudy has invited you to join their party! /party accept Attacker"),
                false, true, ACCOUNT, true, ModConfig.PartyAcceptFriendMode.NORMAL_ONLY,
                whitelist, 5_000L));
    }

    @Test
    void deduplicatesGameAndGameCanceledAndDisconnectResetAllowsANewSession() {
        PartyAutoAcceptManager manager = manager();
        Component invite = Component.literal("Cloudy has invited you to join their party!");
        List<String> whitelist = List.of("Cloudy");

        assertEquals("party accept Cloudy", manager.onMessage(invite, false, true, ACCOUNT,
                true, ModConfig.PartyAcceptFriendMode.NORMAL_ONLY, whitelist, 1_000L));
        assertNull(manager.onMessage(invite, false, true, ACCOUNT,
                true, ModConfig.PartyAcceptFriendMode.NORMAL_ONLY, whitelist, 1_001L));

        manager.resetSession();
        assertEquals("party accept Cloudy", manager.onMessage(invite, false, true, ACCOUNT,
                true, ModConfig.PartyAcceptFriendMode.NORMAL_ONLY, whitelist, 1_002L));
    }

    @Test
    void featureEnvironmentAndOverlayChecksFailClosed() {
        PartyAutoAcceptManager manager = manager();
        Component invite = Component.literal("Cloudy has invited you to join their party!");
        List<String> whitelist = List.of("Cloudy");

        assertNull(manager.onMessage(invite, true, true, ACCOUNT, true,
                ModConfig.PartyAcceptFriendMode.NORMAL_ONLY, whitelist, 1_000L));
        assertNull(manager.onMessage(invite, false, false, ACCOUNT, true,
                ModConfig.PartyAcceptFriendMode.NORMAL_ONLY, whitelist, 2_000L));
        assertNull(manager.onMessage(invite, false, true, ACCOUNT, false,
                ModConfig.PartyAcceptFriendMode.NORMAL_ONLY, whitelist, 3_000L));
        assertNull(manager.onMessage(invite, false, true, "", true,
                ModConfig.PartyAcceptFriendMode.NORMAL_ONLY, whitelist, 4_000L));
    }

    @Test
    void persistsFriendKindsByAccountAndAppliesObservedMutationMessages() {
        Path cache = temporaryDirectory.resolve("friends.json");
        PartyAutoAcceptManager first = new PartyAutoAcceptManager(new FriendRosterStore(cache));
        first.load();
        observeRoster(first);

        PartyAutoAcceptManager reloaded = new PartyAutoAcceptManager(new FriendRosterStore(cache));
        reloaded.load();
        assertEquals("party accept BestFriend", invite(reloaded, "BestFriend",
                ModConfig.PartyAcceptFriendMode.SPECIAL_ONLY, List.of(), 1_000L));
        assertNull(reloaded.onMessage(Component.literal(
                        "OtherAccount has invited you to join their party!"),
                false, true, "another-account", true,
                ModConfig.PartyAcceptFriendMode.NORMAL_ONLY, List.of(), 2_000L));

        reloaded.onMessage(Component.literal("BestFriend is no longer a best friend!"),
                false, true, ACCOUNT, false,
                ModConfig.PartyAcceptFriendMode.NORMAL_ONLY, List.of(), 3_000L);
        reloaded.resetSession();
        assertEquals("party accept BestFriend", invite(reloaded, "BestFriend",
                ModConfig.PartyAcceptFriendMode.NORMAL_ONLY, List.of(), 4_000L));

        reloaded.onMessage(Component.literal(
                        "You removed BestFriend from your friends list!"),
                false, true, ACCOUNT, false,
                ModConfig.PartyAcceptFriendMode.NORMAL_ONLY, List.of(), 5_000L);
        reloaded.resetSession();
        assertNull(invite(reloaded, "BestFriend",
                ModConfig.PartyAcceptFriendMode.NORMAL_ONLY, List.of(), 6_000L));
    }

    @Test
    void incompleteRefreshFailsClosedAndCompleteRefreshDropsFormerFriends() {
        Path cache = temporaryDirectory.resolve("friends.json");
        PartyAutoAcceptManager manager = new PartyAutoAcceptManager(new FriendRosterStore(cache));
        manager.load();
        observeFriendPage(manager, 1, 1, "FormerFriend", false);
        assertEquals("party accept FormerFriend", invite(manager, "FormerFriend",
                ModConfig.PartyAcceptFriendMode.NORMAL_ONLY, List.of(), 1_000L));

        observeFriendPage(manager, 1, 2, "FirstPage", false);
        manager.resetSession();
        assertNull(invite(manager, "FormerFriend",
                ModConfig.PartyAcceptFriendMode.NORMAL_ONLY, List.of(), 2_000L));
        observeFriendPage(manager, 2, 2, "StaleSecondPage", true);
        assertNull(invite(manager, "StaleSecondPage",
                ModConfig.PartyAcceptFriendMode.SPECIAL_ONLY, List.of(), 2_500L));

        PartyAutoAcceptManager reloaded = new PartyAutoAcceptManager(new FriendRosterStore(cache));
        reloaded.load();
        assertNull(invite(reloaded, "FormerFriend",
                ModConfig.PartyAcceptFriendMode.NORMAL_ONLY, List.of(), 3_000L));

        // A later page from an incomplete/previous traversal cannot finish the transaction.
        observeFriendPage(reloaded, 2, 2, "SecondPage", true);
        assertNull(invite(reloaded, "SecondPage",
                ModConfig.PartyAcceptFriendMode.SPECIAL_ONLY, List.of(), 4_000L));

        observeFriendPage(reloaded, 1, 2, "FirstPage", false);
        observeFriendPage(reloaded, 2, 2, "SecondPage", true);
        assertNull(invite(reloaded, "FormerFriend",
                ModConfig.PartyAcceptFriendMode.NORMAL_ONLY, List.of(), 5_000L));
        assertEquals("party accept FirstPage", invite(reloaded, "FirstPage",
                ModConfig.PartyAcceptFriendMode.NORMAL_ONLY, List.of(), 6_000L));
        assertEquals("party accept SecondPage", invite(reloaded, "SecondPage",
                ModConfig.PartyAcceptFriendMode.SPECIAL_ONLY, List.of(), 7_000L));
    }

    @Test
    void legacySinglePageCacheIsInvalidatedDuringSchemaMigration() throws Exception {
        Path cache = temporaryDirectory.resolve("friends.json");
        Files.writeString(cache, """
                {
                  "schemaVersion": 1,
                  "accounts": {
                    "7f79772a-f432-4725-833a-1945b180e567": {
                      "known": true,
                      "friends": {"FormerFriend": "NORMAL"}
                    }
                  }
                }
                """);
        PartyAutoAcceptManager manager = new PartyAutoAcceptManager(new FriendRosterStore(cache));
        manager.load();

        assertNull(invite(manager, "FormerFriend",
                ModConfig.PartyAcceptFriendMode.NORMAL_ONLY, List.of(), 1_000L));
    }

    @Test
    void recognizesOnlyRealHypixelHosts() {
        assertTrue(PartyAutoAcceptManager.isHypixelAddress("mc.hypixel.net"));
        assertTrue(PartyAutoAcceptManager.isHypixelAddress("hypixel.net:25565"));
        assertTrue(PartyAutoAcceptManager.isHypixelAddress("alpha.hypixel.io"));
        assertFalse(PartyAutoAcceptManager.isHypixelAddress("hypixel.net.evil.example"));
        assertFalse(PartyAutoAcceptManager.isHypixelAddress("fakehypixel.net"));
        assertFalse(PartyAutoAcceptManager.isHypixelAddress(null));
    }

    private PartyAutoAcceptManager manager() {
        PartyAutoAcceptManager manager = new PartyAutoAcceptManager(
                new FriendRosterStore(temporaryDirectory.resolve("friends.json")));
        manager.load();
        return manager;
    }

    private static String invite(PartyAutoAcceptManager manager, String inviter,
                                 ModConfig.PartyAcceptFriendMode mode,
                                 List<String> whitelist, long nowMs) {
        return manager.onMessage(
                Component.literal(inviter + " has invited you to join their party!"),
                false, true, ACCOUNT, true, mode, whitelist, nowMs);
    }

    private static void observeRoster(PartyAutoAcceptManager manager) {
        Component friends = Component.literal("Friends (Page 1 of 1)\n")
                .append(friendRow("OrdinaryFriend", false))
                .append(Component.literal("\n"))
                .append(friendRow("BestFriend", true));
        manager.onMessage(friends, false, true, ACCOUNT, false,
                ModConfig.PartyAcceptFriendMode.NORMAL_ONLY, List.of(), 0L);
    }

    private static void observeFriendPage(PartyAutoAcceptManager manager, int page, int total,
                                          String name, boolean bold) {
        Component friends = Component.literal("Friends (Page " + page + " of " + total + ")\n")
                .append(friendRow(name, bold));
        manager.onMessage(friends, false, true, ACCOUNT, false,
                ModConfig.PartyAcceptFriendMode.NORMAL_ONLY, List.of(), 0L);
    }

    private static Component friendRow(String name, boolean bold) {
        return Component.literal(name).withStyle(style -> style
                .withBold(bold)
                .withClickEvent(new ClickEvent.RunCommand("/viewprofile " + PROFILE_ID))
                .withHoverEvent(new HoverEvent.ShowText(
                        Component.literal("Click here to view " + name + "'s profile"))));
    }
}

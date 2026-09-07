package cloudy.autume.addition.tracker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HypixelSessionTrackerTest {
    @AfterEach
    void reset() {
        HypixelSessionTracker.reset();
    }

    @Test
    void helloConfirmsHypixelWithoutGuessingTheGameType() {
        HypixelSessionTracker.markHello();

        assertTrue(HypixelSessionTracker.isHypixelConfirmed());
        assertTrue(HypixelSessionTracker.canSendHypixelCommand());
        assertFalse(HypixelSessionTracker.canUseDungeonQuickView());
        assertFalse(HypixelSessionTracker.hasAuthoritativeLocation());
        assertFalse(HypixelSessionTracker.isSkyBlockConfirmed());
    }

    @Test
    void locationPacketIsTheAuthoritativeSkyBlockDecision() {
        HypixelSessionTracker.markLocation("mini123", "SKYBLOCK", "", "mining_3", "Dwarven Mines");

        assertTrue(HypixelSessionTracker.isHypixelConfirmed());
        assertTrue(HypixelSessionTracker.hasAuthoritativeLocation());
        assertTrue(HypixelSessionTracker.isSkyBlockConfirmed());
        assertTrue(HypixelSessionTracker.canUseDungeonQuickView());
        assertEquals("mini123", HypixelSessionTracker.serverName());
        assertEquals("mining_3", HypixelSessionTracker.mode());
        assertEquals("Dwarven Mines", HypixelSessionTracker.map());
    }

    @Test
    void authoritativeOtherGameCannotBeOverriddenByASkyBlockLookingSidebar() {
        HypixelSessionTracker.markLocation("mini321", "BEDWARS", "", "", "");

        assertFalse(HypixelSessionTracker.canUseDungeonQuickView());
        assertFalse(HypixelSessionTracker.allowsPassiveSkyBlock("Hypixel BungeeCord", strictSidebar()));
    }

    @Test
    void worldChangeClearsLocationButPreservesConfirmedProxyIdentity() {
        HypixelSessionTracker.markLocation("mini123", "SKYBLOCK", "", "mining_3", "Dwarven Mines");

        HypixelSessionTracker.onWorldChange();

        assertTrue(HypixelSessionTracker.isHypixelConfirmed());
        assertTrue(HypixelSessionTracker.canSendHypixelCommand());
        assertFalse(HypixelSessionTracker.hasAuthoritativeLocation());
        assertFalse(HypixelSessionTracker.isSkyBlockConfirmed());
        assertEquals("", HypixelSessionTracker.map());
    }

    @Test
    void exactBrandAndStrictSidebarAllowOnlyPassiveFallback() {
        assertTrue(HypixelSessionTracker.allowsPassiveSkyBlock("Hypixel BungeeCord", strictSidebar()));
        assertFalse(HypixelSessionTracker.canSendHypixelCommand());

        assertFalse(HypixelSessionTracker.allowsPassiveSkyBlock("Fake Hypixel BungeeCord", strictSidebar()));
        assertFalse(HypixelSessionTracker.allowsPassiveSkyBlock("Hypixel BungeeCord", List.of(
                "HYPIXEL", "Play SKYBLOCK today", "Purse: 1,000")));
        assertFalse(HypixelSessionTracker.allowsPassiveSkyBlock("Hypixel BungeeCord", List.of(
                "SKYBLOCK", "Advertisement only")));
    }

    @Test
    void confirmedHelloCanBridgeAShortLocationPacketDelay() {
        HypixelSessionTracker.markHello();

        assertTrue(HypixelSessionTracker.allowsPassiveSkyBlock("", strictSidebar()));
    }

    @Test
    void strictSidebarSupportsDungeonAndRiftShapesWithoutLooseSubstringMatching() {
        assertTrue(HypixelSessionTracker.hasStrictSkyBlockScoreboard(List.of(
                "SKYBLOCK", "The Catacombs (F7)", "Cleared: 0%")));
        assertTrue(HypixelSessionTracker.hasStrictSkyBlockScoreboard(List.of(
                "SKYBLOCK", "Rift Time: 12:34")));
        assertFalse(HypixelSessionTracker.hasStrictSkyBlockScoreboard(List.of(
                "MY SKYBLOCK SERVER", "Purse: 1,000")));
    }

    private static List<String> strictSidebar() {
        return List.of("SKYBLOCK", "Profile: Apple", "⏣ Hub", "Purse: 1,000");
    }
}

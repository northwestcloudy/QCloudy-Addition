package cloudy.autume.addition.party;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static cloudy.autume.addition.party.PartyCommandEngine.EntryPoint.LOCAL_DOUBLE_SLASH;
import static cloudy.autume.addition.party.PartyCommandEngine.EntryPoint.PARTY_CHAT;
import static cloudy.autume.addition.party.PartyCommandEngine.Feature;
import static cloudy.autume.addition.party.PartyCommandEngine.Status;
import static cloudy.autume.addition.party.PartyCommandEngine.TriggerScope;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PartyCommandEngineTest {
    private static final String LOCAL = "LocalPlayer";
    private static final Predicate<Feature> ALL_ENABLED = feature -> true;
    private static final Function<Feature, TriggerScope> EVERYONE = feature -> TriggerScope.EVERYONE;
    private static final PartyCommandEngine.BlockCoordinates COORDINATES =
            new PartyCommandEngine.BlockCoordinates(-11, 72, 305);

    @Test
    void mapsAllSimpleAliasesWithoutLeadingSlashes() {
        assertAliases(List.of("warp", "w"), "party warp");
        assertAliases(List.of("allinvite", "all", "allinv"), "party settings allinvite");
        assertAliases(List.of("sc", "sendcoords", "c"), "pc x: -11, y: 72, z: 305");

        assertEquals("party kick TargetPlayer", partyPayload("!k TargetPlayer"));
        assertEquals("party promote TargetPlayer", partyPayload("!pp TargetPlayer"));
    }

    @Test
    void noArgumentTransferTargetsPartySenderAndLocalDoubleSlashTargetsLocalPlayer() {
        assertEquals("party transfer RemotePlayer", partyPayload("!pt"));
        assertEquals("party transfer RemotePlayer", partyPayload("!ptme"));
        assertEquals("party transfer LocalPlayer", localPayload("//pt"));
        assertEquals("party transfer LocalPlayer", localPayload("//ptme"));
    }

    @Test
    void resolvesNamedTargetsByExactOrUniquePrefixAndReportsAmbiguity() {
        PartyCommandEngine engine = new PartyCommandEngine();
        engine.observePartySystemMessage("NorthWestCloudy joined the party.");
        engine.observePartySystemMessage("Alice joined the party.");

        assertEquals("party transfer NorthWestCloudy",
                party(engine, "!pt north", 0L).payload());
        assertEquals("party kick NorthWestCloudy",
                local(engine, "//k northwestcloudy", 1L).payload());

        engine.observePartySystemMessage("NorthWind joined the party.");
        PartyCommandEngine.Result ambiguous = party(engine, "!pp north", 2L);
        assertEquals(Status.ERROR, ambiguous.status());
        assertEquals(PartyCommandEngine.ErrorKind.AMBIGUOUS_PLAYER, ambiguous.error());
        assertEquals(List.of("NorthWestCloudy", "NorthWind"), ambiguous.candidates());

        PartyCommandEngine.Result uncached = local(new PartyCommandEngine(), "//pt FullPlayerName", 0L);
        assertEquals(Status.COMMAND, uncached.status());
        assertEquals("party transfer FullPlayerName", uncached.payload());
    }

    @Test
    void streamAcceptsAnyPureDigitSequenceAndAllCloseAliases() {
        assertStream("stream", "stream");
        assertStream("st", "stream");
        assertStream("s", "stream");
        assertStream("stream 3", "stream open 3");
        assertStream("st 0", "stream open 0");
        assertStream("s 999999999999999999999999999", "stream open 999999999999999999999999999");
        assertStream("s CLOSE", "stream close");
        for (String verb : List.of("stream", "st", "s")) {
            for (String close : List.of("c", "close", "off")) {
                assertEquals("stream close", partyPayload("!" + verb + " " + close));
                assertEquals("stream close", localPayload("//" + verb + " " + close));
            }
        }

        PartyCommandEngine.Result decimal = party(new PartyCommandEngine(), "!s 3.5", 0L);
        assertEquals(Status.ERROR, decimal.status());
        assertEquals(PartyCommandEngine.ErrorKind.INVALID_ARGUMENTS, decimal.error());
    }

    @Test
    void mapsEveryDungeonAndKuudraAliasForBothEntryPoints() {
        assertBoth("fe", "joininstance CATACOMBS_ENTRANCE");
        assertBoth("f0", "joininstance CATACOMBS_ENTRANCE");
        assertBoth("me", "joininstance MASTER_CATACOMBS_ENTRANCE");
        assertBoth("m0", "joininstance MASTER_CATACOMBS_ENTRANCE");

        List<String> floors = List.of("", "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN");
        for (int floor = 1; floor <= 7; floor++) {
            assertBoth("f" + floor, "joininstance CATACOMBS_FLOOR_" + floors.get(floor));
            assertBoth("m" + floor, "joininstance MASTER_CATACOMBS_FLOOR_" + floors.get(floor));
        }

        List<String> kuudra = List.of("", "NORMAL", "HOT", "BURNING", "FIERY", "INFERNAL");
        for (int tier = 1; tier <= 5; tier++) {
            assertBoth("t" + tier, "joininstance KUUDRA_" + kuudra.get(tier));
        }
    }

    @Test
    void commandsAreCaseInsensitiveTrimmedAndRejectExtraOrMissingArguments() {
        assertEquals("party warp", partyPayload("   !WARP   "));
        assertEquals("party transfer TargetPlayer", partyPayload("!PT    TargetPlayer"));

        assertError("!warp extra", PartyCommandEngine.ErrorKind.INVALID_ARGUMENTS);
        assertError("!k", PartyCommandEngine.ErrorKind.INVALID_ARGUMENTS);
        assertError("!pp First Second", PartyCommandEngine.ErrorKind.INVALID_ARGUMENTS);
        assertError("!ptme Other", PartyCommandEngine.ErrorKind.INVALID_ARGUMENTS);
        assertError("!k bad-name", PartyCommandEngine.ErrorKind.INVALID_PLAYER);
        assertError("!f7 extra", PartyCommandEngine.ErrorKind.INVALID_ARGUMENTS);
        assertError("!t5 extra", PartyCommandEngine.ErrorKind.INVALID_ARGUMENTS);
    }

    @Test
    void masterChildAndPerFeatureScopeAllFailClosed() {
        PartyCommandEngine engine = new PartyCommandEngine();
        PartyCommandEngine.Result masterOff = engine.handlePartyChat(
                "Party > RemotePlayer: !warp", LOCAL, false, ALL_ENABLED, EVERYONE,
                COORDINATES, 0L);
        assertEquals(Status.IGNORED, masterOff.status());

        Predicate<Feature> onlyWarp = feature -> feature == Feature.WARP;
        PartyCommandEngine.Result childOff = engine.handlePartyChat(
                "Party > RemotePlayer: !pp Target", LOCAL, true, onlyWarp, EVERYONE,
                COORDINATES, 0L);
        assertEquals(Status.IGNORED, childOff.status());

        assertEquals(Status.IGNORED, engine.handlePartyChat(
                "Party > RemotePlayer: !warp", LOCAL, true, ALL_ENABLED,
                feature -> TriggerScope.SELF_ONLY, COORDINATES, 0L).status());
        assertEquals(Status.COMMAND, engine.handlePartyChat(
                "Party > LocalPlayer: !warp", LOCAL, true, ALL_ENABLED,
                feature -> TriggerScope.SELF_ONLY, COORDINATES, 0L).status());

        PartyCommandEngine otherEngine = new PartyCommandEngine();
        assertEquals(Status.IGNORED, otherEngine.handlePartyChat(
                "Party > LocalPlayer: !pp Target", LOCAL, true, ALL_ENABLED,
                feature -> TriggerScope.OTHERS_ONLY, COORDINATES, 0L).status());
        assertEquals(Status.COMMAND, otherEngine.handlePartyChat(
                "Party > RemotePlayer: !pp Target", LOCAL, true, ALL_ENABLED,
                feature -> TriggerScope.OTHERS_ONLY, COORDINATES, 0L).status());
    }

    @Test
    void ignoresEveryNonPartyChannelAndUnknownDoubleSlashButCancelsRecognizedErrors() {
        PartyCommandEngine engine = new PartyCommandEngine();
        for (String text : List.of(
                "Guild > RemotePlayer: !warp",
                "From RemotePlayer: !warp",
                "RemotePlayer: !warp",
                "Party Members: RemotePlayer ●")) {
            assertEquals(Status.IGNORED, engine.handlePartyChat(
                    text, LOCAL, true, ALL_ENABLED, EVERYONE, COORDINATES, 0L).status());
        }

        PartyCommandEngine.Result unknown = local(engine, "//not_a_qca_command", 0L);
        assertEquals(Status.IGNORED, unknown.status());
        assertFalse(unknown.shouldCancelInput());

        PartyCommandEngine.Result malformed = local(engine, "//k", 0L);
        assertEquals(Status.ERROR, malformed.status());
        assertTrue(malformed.shouldCancelInput());
        assertNull(malformed.payload());
    }

    @Test
    void consumesRecognizedLocalCommandsWhenParentOrChildSwitchIsDisabled() {
        PartyCommandEngine engine = new PartyCommandEngine();
        PartyCommandEngine.Result parentOff = engine.handleLocalDoubleSlash(
                "//warp", LOCAL, false, ALL_ENABLED, COORDINATES, 0L);
        assertEquals(Status.DISABLED, parentOff.status());
        assertEquals(Feature.WARP, parentOff.feature());
        assertTrue(parentOff.shouldCancelInput());
        assertNull(parentOff.payload());

        PartyCommandEngine.Result childOff = engine.handleLocalDoubleSlash(
                "//warp", LOCAL, true, feature -> false, COORDINATES, 0L);
        assertEquals(Status.DISABLED, childOff.status());
        assertEquals(Feature.WARP, childOff.feature());
        assertTrue(childOff.shouldCancelInput());

        PartyCommandEngine.Result unknown = engine.handleLocalDoubleSlash(
                "//not_a_qca_command", LOCAL, false, feature -> false, COORDINATES, 0L);
        assertEquals(Status.IGNORED, unknown.status());
        assertFalse(unknown.shouldCancelInput());
    }

    @Test
    void sharesActionCooldownsAcrossAliasesAndBothEntryPoints() {
        PartyCommandEngine engine = new PartyCommandEngine();
        assertEquals(Status.COMMAND, party(engine, "!warp", 0L).status());

        PartyCommandEngine.Result blocked = local(engine, "//w", 1_000_000_000L);
        assertEquals(Status.COOLDOWN, blocked.status());
        assertEquals(4_000_000_000L, blocked.retryAfterNanos());
        assertNull(blocked.payload());

        assertEquals(Status.COMMAND, local(engine, "//warp", 5_000_000_000L).status());
        assertEquals(Status.COMMAND, party(engine, "!all", 5_000_000_001L).status());
        assertEquals(Status.COOLDOWN, local(engine, "//allinv", 6_000_000_001L).status());

        engine.resetSession();
        assertEquals(Status.COMMAND, local(engine, "//allinvite", 6_000_000_002L).status());
    }

    @Test
    void deduplicatesImmediateDualPartyEventsWithoutSuppressingLaterMessages() {
        PartyCommandEngine engine = new PartyCommandEngine();
        assertEquals(Status.COMMAND, party(engine, "!sc", 0L).status());
        assertEquals(Status.IGNORED, party(engine, "!sc", 1L).status());
        assertEquals(Status.COMMAND, party(engine, "!sc", 500_000_000L).status());
    }

    @Test
    void exposesEnabledCommandAndPlayerSuggestionsForBothPrefixes() {
        PartyCommandEngine engine = new PartyCommandEngine();
        EnumSet<Feature> enabled = EnumSet.of(Feature.TRANSFER, Feature.PROMOTE);
        Predicate<Feature> policy = enabled::contains;

        assertEquals(List.of("!pt", "!ptme", "!pp"),
                engine.suggestCommands("!p", PARTY_CHAT, true, policy));
        assertEquals(List.of("//pt", "//ptme", "//pp"),
                engine.suggestCommands("//p", LOCAL_DOUBLE_SLASH, true, policy));
        assertTrue(engine.suggestCommands("!p", PARTY_CHAT, false, policy).isEmpty());
        assertTrue(engine.suggestCommands("!p extra", PARTY_CHAT, true, policy).isEmpty());

        engine.observePartySystemMessage("NorthWestCloudy joined the party.");
        assertEquals(List.of("NorthWestCloudy"), engine.suggestPlayers("north", LOCAL));
    }

    @Test
    void coordinatesRequireAnIntegerSnapshotFromTheCaller() {
        PartyCommandEngine engine = new PartyCommandEngine();
        PartyCommandEngine.Result unavailable = engine.handleLocalDoubleSlash(
                "//sc", LOCAL, true, ALL_ENABLED, null, 0L);
        assertEquals(Status.ERROR, unavailable.status());
        assertEquals(PartyCommandEngine.ErrorKind.COORDINATES_UNAVAILABLE, unavailable.error());
    }

    private static void assertAliases(List<String> aliases, String payload) {
        for (String alias : aliases) {
            assertEquals(payload, partyPayload("!" + alias));
            assertEquals(payload, localPayload("//" + alias));
        }
    }

    private static void assertStream(String input, String expected) {
        assertEquals(expected, partyPayload("!" + input));
        assertEquals(expected, localPayload("//" + input));
    }

    private static void assertBoth(String alias, String payload) {
        assertEquals(payload, partyPayload("!" + alias));
        assertEquals(payload, localPayload("//" + alias));
    }

    private static void assertError(String input, PartyCommandEngine.ErrorKind expected) {
        PartyCommandEngine.Result result = party(new PartyCommandEngine(), input, 0L);
        assertEquals(Status.ERROR, result.status());
        assertEquals(expected, result.error());
    }

    private static String partyPayload(String message) {
        return party(new PartyCommandEngine(), message, 0L).payload();
    }

    private static String localPayload(String input) {
        return local(new PartyCommandEngine(), input, 0L).payload();
    }

    private static PartyCommandEngine.Result party(PartyCommandEngine engine, String message,
                                                   long nowNanos) {
        return engine.handlePartyChat("Party > RemotePlayer: " + message, LOCAL, true,
                ALL_ENABLED, EVERYONE, COORDINATES, nowNanos);
    }

    private static PartyCommandEngine.Result local(PartyCommandEngine engine, String input,
                                                   long nowNanos) {
        return engine.handleLocalDoubleSlash(input, LOCAL, true, ALL_ENABLED,
                COORDINATES, nowNanos);
    }
}

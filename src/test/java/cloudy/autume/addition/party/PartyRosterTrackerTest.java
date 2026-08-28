package cloudy.autume.addition.party;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PartyRosterTrackerTest {
    @Test
    void observesPartyChatJoinLeavePartyListAndSessionResetMessages() {
        PartyRosterTracker roster = new PartyRosterTracker();
        roster.observePartyChat(new PartyChatLine("ChatMember", "hello"));
        roster.observeSystemMessage("[MVP+] JoinedMember joined the party.");
        roster.observeSystemMessage("You'll be partying with: [VIP] InitialOne, InitialTwo");
        assertEquals(List.of("ChatMember", "JoinedMember", "InitialOne", "InitialTwo"), roster.members());

        roster.observeSystemMessage("JoinedMember has left the party.");
        roster.observeSystemMessage("InitialTwo has been removed from the party.");
        assertEquals(List.of("ChatMember", "InitialOne"), roster.members());

        roster.observeSystemMessage("Party Members (3)");
        roster.observeSystemMessage("Party Leader: [MVP+] Leader ●");
        roster.observeSystemMessage("Party Members: MemberOne ● [VIP] MemberTwo ●");
        assertEquals(List.of("Leader", "MemberOne", "MemberTwo"), roster.members());

        roster.observeSystemMessage("Leader has disbanded the party!");
        assertTrue(roster.members().isEmpty());
    }

    @Test
    void joiningAnotherPartyStartsAFreshRosterAndHandlesNamesEndingInS() {
        PartyRosterTracker roster = new PartyRosterTracker();
        roster.observePartyChat(new PartyChatLine("OldMember", "hello"));
        roster.observeSystemMessage("You have joined [MVP+] Jess' party!");
        assertEquals(List.of("Jess"), roster.members());

        roster.observeSystemMessage("You have been kicked from the party by Jess");
        assertTrue(roster.members().isEmpty());
    }

    @Test
    void resolvesExactThenUniquePrefixAndRejectsAmbiguity() {
        PartyRosterTracker roster = new PartyRosterTracker();
        roster.observePartyChat(new PartyChatLine("NorthWestCloudy", "hello"));
        roster.observePartyChat(new PartyChatLine("Alice", "hello"));

        PartyRosterTracker.Resolution exact = roster.resolve("northwestcloudy", "LocalPlayer");
        assertEquals(PartyRosterTracker.ResolutionKind.EXACT, exact.kind());
        assertEquals("NorthWestCloudy", exact.name());

        PartyRosterTracker.Resolution prefix = roster.resolve("north", "LocalPlayer");
        assertEquals(PartyRosterTracker.ResolutionKind.UNIQUE_PREFIX, prefix.kind());
        assertEquals("NorthWestCloudy", prefix.name());

        roster.observePartyChat(new PartyChatLine("NorthWind", "hello"));
        PartyRosterTracker.Resolution ambiguous = roster.resolve("north", "LocalPlayer");
        assertEquals(PartyRosterTracker.ResolutionKind.AMBIGUOUS, ambiguous.kind());
        assertEquals(List.of("NorthWestCloudy", "NorthWind"), ambiguous.candidates());
        assertFalse(ambiguous.resolved());
    }

    @Test
    void permitsAValidFullNameWhenRosterIsColdButRejectsInvalidSyntax() {
        PartyRosterTracker roster = new PartyRosterTracker();
        PartyRosterTracker.Resolution passthrough = roster.resolve("UncachedPlayer", "LocalPlayer");
        assertEquals(PartyRosterTracker.ResolutionKind.PASSTHROUGH, passthrough.kind());
        assertEquals("UncachedPlayer", passthrough.name());
        assertTrue(passthrough.resolved());

        assertEquals(PartyRosterTracker.ResolutionKind.INVALID,
                roster.resolve("bad-name", "LocalPlayer").kind());
        assertEquals(PartyRosterTracker.ResolutionKind.EXACT,
                roster.resolve("localplayer", "LocalPlayer").kind());
    }

    @Test
    void suggestsCanonicalPartyAndLocalNamesByPrefix() {
        PartyRosterTracker roster = new PartyRosterTracker();
        roster.observePartyChat(new PartyChatLine("NorthWestCloudy", "hello"));
        roster.observePartyChat(new PartyChatLine("Nobody", "hello"));
        assertEquals(List.of("NorthWestCloudy"), roster.suggestions("north", "LocalPlayer"));
        assertEquals(List.of("NorthWestCloudy", "Nobody", "LocalPlayer"),
                roster.suggestions("", "LocalPlayer"));
    }
}

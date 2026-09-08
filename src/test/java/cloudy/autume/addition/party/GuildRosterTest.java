package cloudy.autume.addition.party;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GuildRosterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsOnlyACompleteOnlineAndOfflineGuildSnapshot() {
        GuildRoster roster = new GuildRoster();
        roster.observe(Component.literal("Guild Name: QCloudy\nOnline Members: 2\n"
                + "[MVP+] OnlineOne ● [VIP] OnlineTwo\n--------------------"));

        assertFalse(roster.isKnown());
        assertFalse(roster.contains("OnlineOne"));

        roster.observe(Component.literal("Guild Name: QCloudy\nOnline Members: 2\n"
                + "[MVP+] OnlineOne ● [VIP] OnlineTwo\n--------------------\nOffline Members: 1\n"
                + "[MVP+] OfflineOne ●\nTotal Members: 3\nOnline Members: 2\n"
                + "Offline Members: 1\n--------------------"));

        assertTrue(roster.isKnown());
        assertTrue(roster.contains("onlineone"));
        assertTrue(roster.contains("OfflineOne"));
        assertFalse(roster.contains("2"));
        assertFalse(roster.contains("NotMember"));
    }

    @Test
    void appliesExactGuildMembershipMutationsAfterACompleteSnapshot() {
        GuildRoster roster = completeRoster();

        roster.observe(Component.literal("[MVP+] NewMember joined the guild!"));
        assertTrue(roster.contains("NewMember"));
        roster.observe(Component.literal("OfflineOne left the guild!"));
        assertFalse(roster.contains("OfflineOne"));
    }

    @Test
    void rejectsInterleavedChatWithADecorativeBullet() {
        GuildRoster roster = new GuildRoster();
        roster.observe(Component.literal("Guild Name: QCloudy"));
        roster.observe(Component.literal("Online Members: 1"));
        roster.observe(Component.literal("Guild > Attacker: hello ● ForgedMember"));
        roster.observe(Component.literal("Offline Members: 0"));
        roster.observe(Component.literal("--------------------"));

        assertFalse(roster.isKnown());
        assertFalse(roster.contains("ForgedMember"));
    }

    @Test
    void rejectsTruncatedRowsEvenWhenBothSectionHeadersArrive() {
        GuildRoster roster = new GuildRoster();
        roster.observe(Component.literal("Guild Name: QCloudy\nOnline Members: 2\n"
                + "OnlyOne ●\nOffline Members: 1\nOfflineOne ●\n"
                + "Total Members: 3\n--------------------"));

        assertFalse(roster.isKnown());
        assertFalse(roster.contains("OnlyOne"));
    }

    @Test
    void storePersistsAccountScopedCompleteRoster() {
        Path file = temporaryDirectory.resolve("guild.json");
        GuildRosterStore first = new GuildRosterStore(file);
        first.load();
        first.observe("Account-A", Component.literal("Guild Name: QCloudy\nOnline Members: 1\n"
                + "OnlineOne ●\nOffline Members: 1\nOfflineOne ●\n--------------------"));

        GuildRosterStore reloaded = new GuildRosterStore(file);
        reloaded.load();
        assertTrue(reloaded.isGuildMember("account-a", "OnlineOne"));
        assertTrue(reloaded.isGuildMember("ACCOUNT-A", "OfflineOne"));
        assertFalse(reloaded.isGuildMember("Account-B", "OfflineOne"));
    }

    private static GuildRoster completeRoster() {
        GuildRoster roster = new GuildRoster();
        roster.observe(Component.literal("Guild Name: QCloudy\nOnline Members: 1\n"
                + "OnlineOne ●\nOffline Members: 1\nOfflineOne ●\n--------------------"));
        return roster;
    }
}

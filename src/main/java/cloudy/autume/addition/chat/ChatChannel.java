package cloudy.autume.addition.chat;

import java.util.ArrayList;
import java.util.List;

/** Hypixel chat channels exposed by the chat-screen switcher. */
public enum ChatChannel {
    ALL("chat.channel.all", "chat.channel.all.short", "chat all"),
    PARTY("chat.channel.party", "chat.channel.party.short", "chat party"),
    GUILD("chat.channel.guild", "chat.channel.guild.short", "chat guild"),
    OFFICER("chat.channel.officer", "chat.channel.officer.short", "chat officer"),
    COOP("chat.channel.coop", "chat.channel.coop.short", "chat coop");

    private final String labelKey;
    private final String compactLabelKey;
    private final String command;

    ChatChannel(String labelKey, String compactLabelKey, String command) {
        this.labelKey = labelKey;
        this.compactLabelKey = compactLabelKey;
        this.command = command;
    }

    public String labelKey() {
        return labelKey;
    }

    public String compactLabelKey() {
        return compactLabelKey;
    }

    public String command() {
        return command;
    }

    public static List<ChatChannel> visibleChannels(boolean showOfficer) {
        List<ChatChannel> result = new ArrayList<>(List.of(ALL, PARTY, GUILD));
        if (showOfficer) result.add(OFFICER);
        result.add(COOP);
        return List.copyOf(result);
    }
}

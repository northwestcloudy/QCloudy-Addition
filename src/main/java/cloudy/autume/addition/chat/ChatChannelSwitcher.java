package cloudy.autume.addition.chat;

import cloudy.autume.addition.QCloudyAdditionClient;
import cloudy.autume.addition.config.ConfigManager;
import cloudy.autume.addition.i18n.ModText;
import cloudy.autume.addition.tracker.HypixelSessionTracker;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Installs and coordinates the safe chat-channel row on vanilla ChatScreen. */
public final class ChatChannelSwitcher {
    private static final ChatChannelTracker TRACKER = new ChatChannelTracker();
    private static final Map<ChatScreen, Row> ROWS = new WeakHashMap<>();
    private static boolean initialized;

    private ChatChannelSwitcher() {
    }

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof ChatScreen chatScreen)) return;
            EditBox input = Screens.getWidgets(screen).stream()
                    .filter(EditBox.class::isInstance)
                    .map(EditBox.class::cast)
                    .findFirst().orElse(null);
            if (input == null) return;

            Row row = new Row(client, input);
            ROWS.put(chatScreen, row);
            Screens.getWidgets(screen).addAll(row.buttons);
            ScreenEvents.beforeExtract(screen).register((ignored, graphics, mouseX, mouseY, tickDelta) ->
                    row.update());
            ScreenEvents.remove(screen).register(ignored -> ROWS.remove(chatScreen));
        });
    }

    public static void observe(Component message, boolean overlay) {
        if (message == null || overlay || !HypixelSessionTracker.isHypixelConfirmed()) return;
        TRACKER.observe(message.getString(), System.nanoTime());
    }

    public static void reset() {
        TRACKER.reset();
    }

    public static boolean handleClick(ChatScreen screen, MouseButtonEvent click, boolean doubled) {
        if (screen == null || click == null || click.button() != 0) return false;
        Row row = ROWS.get(screen);
        if (row == null) return false;
        row.update();
        for (ChatChannelButton button : row.buttons) {
            if (!button.visible || !button.isMouseOver(click.x(), click.y())) continue;
            // Consume the complete visible button rectangle even when this is
            // the current, pending, or unavailable channel. Otherwise vanilla
            // ChatScreen may activate clickable chat text beneath the row.
            if (button.active) button.mouseClicked(click, doubled);
            return true;
        }
        return false;
    }

    public static boolean shouldBlockPlainMessage(String message) {
        String normalized = message == null ? "" : message.stripLeading();
        if (normalized.isEmpty() || normalized.startsWith("/")) return false;
        return TRACKER.snapshot(System.nanoTime()).pending() != null;
    }

    static ChatChannelTracker trackerForTests() {
        return TRACKER;
    }

    private static void request(Minecraft client, ChatChannel channel) {
        if (!ConfigManager.get().chat.chatChannelSwitcher
                || !HypixelSessionTracker.canSendHypixelCommand()
                || client == null || client.getConnection() == null) return;
        if (channel == ChatChannel.COOP && HypixelSessionTracker.hasAuthoritativeLocation()
                && !HypixelSessionTracker.isSkyBlockConfirmed()) return;

        long now = System.nanoTime();
        if (!TRACKER.beginRequest(channel, now)) return;
        try {
            client.getConnection().sendCommand(channel.command());
        } catch (RuntimeException exception) {
            TRACKER.cancelRequest();
            QCloudyAdditionClient.LOGGER.warn("Could not request Hypixel chat channel {}", channel, exception);
        }
    }

    private static final class Row {
        private final Minecraft client;
        private final EditBox input;
        private final List<ChatChannelButton> buttons = new ArrayList<>();

        private Row(Minecraft client, EditBox input) {
            this.client = client;
            this.input = input;
            for (ChatChannel channel : ChatChannel.values()) {
                ChatChannelButton button = new ChatChannelButton(channel,
                        selected -> request(client, selected));
                button.setTooltipDelay(Duration.ofMillis(250));
                buttons.add(button);
            }
            update();
        }

        private void update() {
            boolean enabled = ConfigManager.get().chat.chatChannelSwitcher;
            String inputText = input.getValue().stripLeading();
            boolean visible = enabled && client != null
                    && HypixelSessionTracker.canSendHypixelCommand()
                    && !inputText.startsWith("/");
            boolean showOfficer = ConfigManager.get().chat.chatChannelShowOfficer;
            List<ChatChannel> channels = ChatChannel.visibleChannels(showOfficer);
            List<ChatChannelLayout.Slot> layout = visible ? ChatChannelLayout.calculate(
                    input.getX(), input.getY(), input.getWidth(), channels,
                    channel -> client.font.width(ModText.get(channel.labelKey())) + 12,
                    channel -> client.font.width(ModText.get(channel.compactLabelKey())) + 10)
                    : List.of();
            ChatChannelTracker.Snapshot snapshot = TRACKER.snapshot(System.nanoTime());
            boolean coopUnavailable = HypixelSessionTracker.hasAuthoritativeLocation()
                    && !HypixelSessionTracker.isSkyBlockConfirmed();

            for (ChatChannelButton button : buttons) {
                ChatChannelLayout.Slot slot = layout.stream()
                        .filter(candidate -> candidate.channel() == button.channel())
                        .findFirst().orElse(null);
                button.visible = slot != null;
                if (slot == null) continue;
                button.setRectangle(slot.x(), slot.y(), slot.width(), slot.height());
                boolean confirmed = snapshot.confirmed() == button.channel();
                boolean pending = snapshot.pending() == button.channel();
                boolean available = button.channel() != ChatChannel.COOP || !coopUnavailable;
                button.active = available && snapshot.pending() == null && !confirmed;
                button.updateState(confirmed, pending);
                String labelKey = slot.compact()
                        ? button.channel().compactLabelKey() : button.channel().labelKey();
                button.setMessage(Component.literal(ModText.get(labelKey)
                        + (pending ? " …" : confirmed ? " ✓" : "")));
                String tooltip = ModText.get(button.channel().labelKey()) + " · /"
                        + button.channel().command();
                if (!available) tooltip += "\n" + ModText.get("chat.channel.unavailable.skyblock");
                else if (confirmed) tooltip += "\n" + ModText.get("chat.channel.state.current");
                else if (pending) tooltip += "\n" + ModText.get("chat.channel.state.pending");
                button.setTooltip(Tooltip.create(Component.literal(tooltip),
                        button.createNarrationMessage()));
            }
        }
    }
}

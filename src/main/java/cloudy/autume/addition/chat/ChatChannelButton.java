package cloudy.autume.addition.chat;

import cloudy.autume.addition.i18n.ModText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.function.Consumer;

/** A standard narratable button with explicit confirmed and pending states. */
final class ChatChannelButton extends AbstractButton {
    private static final int ACCENT = 0xFF35C7E8;
    private static final int PENDING = 0xFFFFB02E;
    private static final int TEXT = 0xFFF2FAFC;
    private static final int TEXT_DIM = 0xFF9DA9AE;

    private final ChatChannel channel;
    private final Consumer<ChatChannel> onPress;
    private boolean confirmed;
    private boolean pending;

    ChatChannelButton(ChatChannel channel, Consumer<ChatChannel> onPress) {
        super(0, 0, 18, ChatChannelLayout.HEIGHT, Component.empty());
        this.channel = channel;
        this.onPress = onPress;
    }

    ChatChannel channel() {
        return channel;
    }

    void updateState(boolean confirmed, boolean pending) {
        this.confirmed = confirmed;
        this.pending = pending;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        onPress.accept(channel);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   float partialTick) {
        extractDefaultSprite(graphics);
        int color = active || confirmed || pending ? TEXT : TEXT_DIM;
        int textY = getY() + Math.max(1,
                (getHeight() - Minecraft.getInstance().font.lineHeight) / 2);
        graphics.centeredText(Minecraft.getInstance().font, getMessage(),
                getX() + getWidth() / 2, textY, color);
        if (confirmed) {
            graphics.outline(getX(), getY(), getWidth(), getHeight(), ACCENT);
            graphics.fill(getX() + 2, getBottom() - 2, getRight() - 2, getBottom() - 1, ACCENT);
        } else if (pending) {
            graphics.outline(getX(), getY(), getWidth(), getHeight(), PENDING);
            graphics.fill(getRight() - 5, getY() + 2, getRight() - 3, getY() + 4, PENDING);
        }
    }

    @Override
    protected MutableComponent createNarrationMessage() {
        String state = confirmed ? ModText.get("chat.channel.state.current")
                : pending ? ModText.get("chat.channel.state.pending")
                : ModText.get("chat.channel.state.switch");
        return Component.literal(ModText.get(channel.labelKey()) + ". " + state);
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}

package cloudy.autume.addition.config;

import cloudy.autume.addition.compat.MinecraftClientCompat;
import cloudy.autume.addition.dungeon.DungeonQuickViewManager;
import cloudy.autume.addition.i18n.ModText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** One-time explicit confirmation before the automatic party command master is enabled. */
final class DungeonAutoKickConfirmScreen extends Screen {
    private final Screen parent;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    DungeonAutoKickConfirmScreen(Screen parent) {
        super(ModText.component("config.dungeon_requirements.confirm.title"));
        this.parent = parent;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        layout();
        graphics.fill(0, 0, width, height, AcaUiTheme.SCRIM);
        graphics.fill(panelX + 4, panelY + 5, panelX + panelWidth + 5, panelY + panelHeight + 6,
                0x66000000);
        AcaUiTheme.surface(graphics, panelX, panelY, panelWidth, panelHeight, AcaUiTheme.WINDOW);
        graphics.fill(panelX + 1, panelY + 1, panelX + panelWidth - 1, panelY + 34, AcaUiTheme.HEADER);
        graphics.centeredText(font, Component.literal(ModText.get("config.dungeon_requirements.confirm.title"))
                        .withStyle(ChatFormatting.BOLD), panelX + panelWidth / 2, panelY + 12,
                AcaUiTheme.DANGER);
        drawCenteredFitted(graphics, ModText.get("config.dungeon_requirements.confirm.body"),
                panelY + 53, panelWidth - 32, AcaUiTheme.TEXT);
        drawCenteredFitted(graphics, ModText.get("config.dungeon_requirements.confirm.detail"),
                panelY + 76, panelWidth - 32, AcaUiTheme.TEXT_MUTED);

        int buttonWidth = Math.max(80, Math.min(150, (panelWidth - 42) / 2));
        int buttonY = panelY + panelHeight - 38;
        int cancelX = panelX + panelWidth / 2 - buttonWidth - 5;
        int confirmX = panelX + panelWidth / 2 + 5;
        AcaUiTheme.button(graphics, font, ModText.get("config.dungeon_requirements.confirm.cancel"),
                cancelX, buttonY, buttonWidth, 24,
                AcaUiTheme.contains(mouseX, mouseY, cancelX, buttonY, buttonWidth, 24), false);
        AcaUiTheme.button(graphics, font, ModText.get("config.dungeon_requirements.confirm.action"),
                confirmX, buttonY, buttonWidth, 24,
                AcaUiTheme.contains(mouseX, mouseY, confirmX, buttonY, buttonWidth, 24), false);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void layout() {
        panelWidth = Math.max(1, Math.min(470, width - Math.min(28, Math.max(0, width - 1))));
        panelHeight = Math.max(1, Math.min(170, height - Math.min(28, Math.max(0, height - 1))));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() != 0) return super.mouseClicked(click, doubled);
        layout();
        int buttonWidth = Math.max(80, Math.min(150, (panelWidth - 42) / 2));
        int buttonY = panelY + panelHeight - 38;
        int cancelX = panelX + panelWidth / 2 - buttonWidth - 5;
        int confirmX = panelX + panelWidth / 2 + 5;
        if (AcaUiTheme.contains(click.x(), click.y(), cancelX, buttonY, buttonWidth, 24)) {
            onClose();
            return true;
        }
        if (AcaUiTheme.contains(click.x(), click.y(), confirmX, buttonY, buttonWidth, 24)) {
            ConfigManager.get().dungeons.partyFinderAutoKick.enabled = true;
            DungeonQuickViewManager.onAdmissionPolicyChanged();
            ConfigManager.save();
            onClose();
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private void drawCenteredFitted(GuiGraphicsExtractor graphics, String text, int y,
                                    int availableWidth, int color) {
        int textWidth = font.width(text);
        float scale = textWidth <= availableWidth ? 1.0f : availableWidth / (float) Math.max(1, textWidth);
        graphics.pose().pushMatrix();
        graphics.pose().translate(panelX + panelWidth / 2.0f - textWidth * scale / 2.0f,
                y + Math.round((1.0f - scale) * 4.0f));
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, 0, 0, color, false);
        graphics.pose().popMatrix();
    }

    @Override
    public void onClose() {
        MinecraftClientCompat.setScreen(minecraft, parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

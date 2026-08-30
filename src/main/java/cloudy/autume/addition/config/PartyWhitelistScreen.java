package cloudy.autume.addition.config;

import cloudy.autume.addition.compat.MinecraftClientCompat;
import cloudy.autume.addition.i18n.ModText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Ordered, case-insensitive whitelist editor for friend party auto-accept. */
final class PartyWhitelistScreen extends Screen {
    private static final int ROW_HEIGHT = 30;
    private static final int ROW_GAP = 5;

    private final Screen parent;
    private final long openedAt = System.nanoTime();
    private final List<Hit> hits = new ArrayList<>();
    private final VerticalScrollbar rowsScrollbar = new VerticalScrollbar();
    private EditorMode editorMode = EditorMode.NONE;
    private String editingOriginal = "";
    private String editorText = "";
    private String errorKey = "";
    private EditBox nameBox;
    private int windowX;
    private int windowY;
    private int windowWidth;
    private int windowHeight;
    private int contentX;
    private int contentWidth;
    private int rowsY;
    private int rowsHeight;
    private int scroll;
    private int maxScroll;

    PartyWhitelistScreen(Screen parent) {
        super(ModText.component("config.party.whitelist.screen_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rowsScrollbar.cancelDrag();
        layout();
        nameBox = null;
        if (editorMode == EditorMode.NONE) return;
        int editorY = windowY + 104;
        nameBox = new EditBox(font, contentX + 8, editorY + 18,
                Math.max(1, contentWidth - 164), font.lineHeight,
                ModText.component("config.party.whitelist.placeholder"));
        nameBox.setBordered(false);
        nameBox.setTextShadow(false);
        nameBox.setMaxLength(16);
        nameBox.setHint(ModText.component("config.party.whitelist.placeholder"));
        nameBox.setTextColor(AcaUiTheme.TEXT);
        nameBox.setValue(editorText);
        nameBox.setResponder(value -> {
            editorText = value;
            errorKey = "";
        });
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);
    }

    private void layout() {
        windowWidth = Math.max(1, Math.min(520, width - Math.min(24, Math.max(0, width - 1))));
        windowHeight = Math.max(1, Math.min(390, height - Math.min(24, Math.max(0, height - 1))));
        windowX = (width - windowWidth) / 2;
        windowY = (height - windowHeight) / 2;
        contentX = windowX + 12;
        contentWidth = Math.max(1, windowWidth - 24);
        rowsY = windowY + (editorMode == EditorMode.NONE ? 105 : 166);
        rowsHeight = Math.max(1, windowY + windowHeight - rowsY - 12);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        layout();
        graphics.fill(0, 0, width, height, AcaUiTheme.SCRIM);
        UiAnimation.push(graphics, UiAnimation.scale(openedAt), width / 2.0f, height / 2.0f);
        graphics.fill(windowX + 4, windowY + 5, windowX + windowWidth + 5,
                windowY + windowHeight + 6, 0x66000000);
        AcaUiTheme.surface(graphics, windowX, windowY, windowWidth, windowHeight, AcaUiTheme.WINDOW);
        graphics.fill(windowX + 1, windowY + 1, windowX + windowWidth - 1,
                windowY + 34, AcaUiTheme.HEADER);
        AcaUiTheme.button(graphics, font, "‹", windowX + 10, windowY + 8, 24, 18,
                AcaUiTheme.contains(mouseX, mouseY, windowX + 10, windowY + 8, 24, 18), false);
        drawFitted(graphics, ModText.get("config.party.whitelist.screen_title"),
                windowX + 42, windowY + 10, windowWidth - 54, AcaUiTheme.TEXT, true);
        drawFitted(graphics, ModText.get("config.party.whitelist.sync_hint"),
                contentX, windowY + 43, contentWidth, AcaUiTheme.TEXT_MUTED, false);

        hits.clear();
        drawTitleCard(graphics, mouseX, mouseY);
        if (editorMode != EditorMode.NONE) drawEditorCard(graphics, mouseX, mouseY);
        drawRows(graphics, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        UiAnimation.pop(graphics);
    }

    private void drawTitleCard(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        ModConfig.Chat chat = ConfigManager.get().chat;
        int y = windowY + 66;
        graphics.fill(contentX, y, contentX + contentWidth, y + 32, AcaUiTheme.CARD);
        graphics.outline(contentX, y, contentWidth, 32, AcaUiTheme.BORDER_SOFT);
        graphics.text(font, Component.literal(ModText.get("config.party.whitelist.title"))
                        .withStyle(ChatFormatting.BOLD), contentX + 10, y + 11, AcaUiTheme.TEXT, false);
        String count = ModText.get("config.party.whitelist.count", chat.partyAutoAcceptWhitelist.size(),
                ModConfig.Chat.PARTY_AUTO_ACCEPT_WHITELIST_LIMIT);
        int addX = contentX + contentWidth - 28;
        int countRight = addX - 8;
        graphics.text(font, count, countRight - font.width(count), y + 11, AcaUiTheme.TEXT_DIM, false);
        boolean enabled = editorMode == EditorMode.NONE
                && chat.partyAutoAcceptWhitelist.size() < ModConfig.Chat.PARTY_AUTO_ACCEPT_WHITELIST_LIMIT;
        boolean hovered = enabled && AcaUiTheme.contains(mouseX, mouseY, addX, y + 5, 22, 22);
        graphics.fill(addX, y + 5, addX + 22, y + 27,
                enabled ? (hovered ? AcaUiTheme.ACCENT : AcaUiTheme.CONTROL) : AcaUiTheme.CARD);
        graphics.outline(addX, y + 5, 22, 22, enabled ? AcaUiTheme.ACCENT_DARK : AcaUiTheme.BORDER_SOFT);
        graphics.centeredText(font, "+", addX + 11, y + 12,
                hovered ? 0xFF071014 : enabled ? AcaUiTheme.TEXT : AcaUiTheme.TEXT_DIM);
        if (enabled) hits.add(new Hit(Action.ADD, "", addX, y + 5, 22, 22));
    }

    private void drawEditorCard(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int y = windowY + 104;
        graphics.fill(contentX, y, contentX + contentWidth, y + 56, AcaUiTheme.CARD);
        graphics.outline(contentX, y, contentWidth, 56,
                errorKey.isBlank() ? AcaUiTheme.ACCENT_DARK : AcaUiTheme.DANGER);
        graphics.text(font, ModText.get(editorMode == EditorMode.ADD
                        ? "config.party.whitelist.add" : "config.party.whitelist.edit"),
                contentX + 8, y + 5, AcaUiTheme.TEXT_MUTED, false);
        int fieldWidth = Math.max(1, contentWidth - 154);
        graphics.fill(contentX + 5, y + 15, contentX + 5 + fieldWidth, y + 35, AcaUiTheme.CONTROL);
        graphics.outline(contentX + 5, y + 15, fieldWidth, 20,
                nameBox != null && nameBox.isFocused() ? AcaUiTheme.ACCENT : AcaUiTheme.BORDER);
        int confirmX = contentX + contentWidth - 142;
        int cancelX = contentX + contentWidth - 70;
        AcaUiTheme.button(graphics, font, ModText.get("config.party.whitelist.confirm"),
                confirmX, y + 15, 66, 20,
                AcaUiTheme.contains(mouseX, mouseY, confirmX, y + 15, 66, 20), true);
        AcaUiTheme.button(graphics, font, ModText.get("config.party.whitelist.cancel"),
                cancelX, y + 15, 64, 20,
                AcaUiTheme.contains(mouseX, mouseY, cancelX, y + 15, 64, 20), false);
        hits.add(new Hit(Action.CONFIRM, "", confirmX, y + 15, 66, 20));
        hits.add(new Hit(Action.CANCEL, "", cancelX, y + 15, 64, 20));
        if (!errorKey.isBlank()) {
            drawFitted(graphics, ModText.get(errorKey), contentX + 8, y + 41,
                    contentWidth - 16, AcaUiTheme.DANGER, false);
        }
    }

    private void drawRows(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<String> names = ConfigManager.get().chat.partyAutoAcceptWhitelist;
        int totalHeight = names.isEmpty() ? ROW_HEIGHT
                : names.size() * (ROW_HEIGHT + ROW_GAP) - ROW_GAP;
        maxScroll = Math.max(0, totalHeight - rowsHeight);
        scroll = Math.clamp(scroll, 0, maxScroll);
        graphics.enableScissor(contentX, rowsY, contentX + contentWidth, rowsY + rowsHeight);
        if (names.isEmpty()) {
            graphics.centeredText(font, ModText.get("config.party.whitelist.empty"),
                    contentX + contentWidth / 2, rowsY + 10, AcaUiTheme.TEXT_DIM);
        } else {
            int y = rowsY - scroll;
            for (String name : names) {
                drawEntry(graphics, name, y, mouseX, mouseY);
                y += ROW_HEIGHT + ROW_GAP;
            }
        }
        graphics.disableScissor();
        rowsScrollbar.update(contentX + contentWidth - VerticalScrollbar.WIDTH,
                rowsY, rowsHeight, maxScroll, scroll);
        rowsScrollbar.draw(graphics, mouseX, mouseY, AcaUiTheme.ACCENT);
    }

    private void drawEntry(GuiGraphicsExtractor graphics, String name, int y, int mouseX, int mouseY) {
        if (y + ROW_HEIGHT <= rowsY || y >= rowsY + rowsHeight) return;
        graphics.fill(contentX, y, contentX + contentWidth, y + ROW_HEIGHT, AcaUiTheme.CARD);
        graphics.outline(contentX, y, contentWidth, ROW_HEIGHT, AcaUiTheme.BORDER_SOFT);
        graphics.text(font, name, contentX + 10, y + 11, AcaUiTheme.TEXT, false);
        int deleteX = contentX + contentWidth - 31;
        int editX = deleteX - 27;
        boolean mouseInsideRows = AcaUiTheme.contains(mouseX, mouseY,
                contentX, rowsY, contentWidth, rowsHeight);
        boolean editHovered = mouseInsideRows
                && AcaUiTheme.contains(mouseX, mouseY, editX, y + 4, 23, 22);
        boolean deleteHovered = mouseInsideRows
                && AcaUiTheme.contains(mouseX, mouseY, deleteX, y + 4, 23, 22);
        drawIconButton(graphics, Action.EDIT, editX, y + 4, editHovered);
        drawIconButton(graphics, Action.DELETE, deleteX, y + 4, deleteHovered);
        addRowHit(Action.EDIT, name, editX, y + 4, 23, 22);
        addRowHit(Action.DELETE, name, deleteX, y + 4, 23, 22);
        if (editHovered) drawFitted(graphics, ModText.get("config.party.whitelist.edit_hint"),
                contentX + 10, rowsY + rowsHeight - font.lineHeight - 2,
                contentWidth - 20, AcaUiTheme.TEXT_MUTED, false);
        if (deleteHovered) drawFitted(graphics, ModText.get("config.party.whitelist.delete_hint"),
                contentX + 10, rowsY + rowsHeight - font.lineHeight - 2,
                contentWidth - 20, AcaUiTheme.DANGER, false);
    }

    /**
     * Keeps row actions inside the same viewport used to scissor their visuals. A partially
     * hidden row therefore cannot leave an invisible edit/delete target over controls above or
     * below the list.
     */
    private void addRowHit(Action action, String name, int x, int y, int width, int height) {
        int clippedX = Math.max(x, contentX);
        int clippedY = Math.max(y, rowsY);
        int clippedRight = Math.min(x + width, contentX + contentWidth);
        int clippedBottom = Math.min(y + height, rowsY + rowsHeight);
        if (clippedRight <= clippedX || clippedBottom <= clippedY) return;
        hits.add(new Hit(action, name, clippedX, clippedY,
                clippedRight - clippedX, clippedBottom - clippedY));
    }

    /** Shared icon-button language for list editors: accent edit, red destructive delete. */
    private void drawIconButton(GuiGraphicsExtractor graphics, Action action, int x, int y, boolean hovered) {
        int fill = hovered ? AcaUiTheme.CARD_HOVER : AcaUiTheme.CONTROL;
        int color = action == Action.DELETE && hovered ? AcaUiTheme.DANGER
                : action == Action.EDIT && hovered ? AcaUiTheme.ACCENT : AcaUiTheme.TEXT_MUTED;
        graphics.fill(x, y, x + 23, y + 22, fill);
        graphics.outline(x, y, 23, 22,
                action == Action.DELETE && hovered ? AcaUiTheme.DANGER : AcaUiTheme.BORDER);
        if (action == Action.EDIT) {
            for (int offset = 0; offset < 7; offset++) {
                graphics.fill(x + 7 + offset, y + 14 - offset,
                        x + 9 + offset, y + 16 - offset, color);
            }
            graphics.fill(x + 6, y + 15, x + 9, y + 18, color);
        } else {
            graphics.fill(x + 7, y + 7, x + 16, y + 9, color);
            graphics.fill(x + 9, y + 5, x + 14, y + 7, color);
            graphics.outline(x + 8, y + 9, 7, 8, color);
        }
    }

    private void drawFitted(GuiGraphicsExtractor graphics, String text, int x, int y,
                            int availableWidth, int color, boolean bold) {
        Component component = bold ? Component.literal(text).withStyle(ChatFormatting.BOLD)
                : Component.literal(text);
        int textWidth = font.width(component);
        if (textWidth <= availableWidth) {
            graphics.text(font, component, x, y, color, false);
            return;
        }
        float scale = availableWidth / (float) Math.max(1, textWidth);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y + Math.round((1.0f - scale) * 4.0f));
        graphics.pose().scale(scale, scale);
        graphics.text(font, component, 0, 0, color, false);
        graphics.pose().popMatrix();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() != 0) return super.mouseClicked(click, doubled);
        if (AcaUiTheme.contains(click.x(), click.y(), windowX + 10, windowY + 8, 24, 18)) {
            onClose();
            return true;
        }
        VerticalScrollbar.Interaction scrollbarClick = rowsScrollbar.mouseClicked(
                click.button(), click.x(), click.y(), scroll);
        if (scrollbarClick.consumed()) {
            scroll = scrollbarClick.scroll();
            return true;
        }
        if (super.mouseClicked(click, doubled)) return true;
        for (int index = hits.size() - 1; index >= 0; index--) {
            Hit hit = hits.get(index);
            if (!hit.contains(click.x(), click.y())) continue;
            activate(hit);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        VerticalScrollbar.Interaction scrollbarDrag = rowsScrollbar.mouseDragged(
                click.button(), click.y(), scroll);
        if (scrollbarDrag.consumed()) {
            scroll = scrollbarDrag.scroll();
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        VerticalScrollbar.Interaction scrollbarRelease = rowsScrollbar.mouseReleased(
                click.button(), click.y(), scroll);
        if (scrollbarRelease.consumed()) {
            scroll = scrollbarRelease.scroll();
            return true;
        }
        return super.mouseReleased(click);
    }

    private void activate(Hit hit) {
        switch (hit.action) {
            case ADD -> openEditor(EditorMode.ADD, "");
            case EDIT -> openEditor(EditorMode.EDIT, hit.name);
            case DELETE -> {
                ConfigManager.get().chat.removePartyAutoAcceptWhitelist(hit.name);
                if (editingOriginal.equalsIgnoreCase(hit.name)) closeEditor();
                ConfigManager.save();
            }
            case CONFIRM -> confirmEditor();
            case CANCEL -> closeEditor();
        }
    }

    private void openEditor(EditorMode mode, String original) {
        editorMode = mode;
        editingOriginal = original == null ? "" : original;
        editorText = editingOriginal;
        errorKey = "";
        scroll = 0;
        rebuildWidgets();
    }

    private void closeEditor() {
        editorMode = EditorMode.NONE;
        editingOriginal = "";
        editorText = "";
        errorKey = "";
        setFocused(null);
        rebuildWidgets();
    }

    private void confirmEditor() {
        ModConfig.Chat chat = ConfigManager.get().chat;
        String candidate = ModConfig.Chat.normalizePartyAutoAcceptName(editorText);
        if (!ModConfig.Chat.isValidMinecraftUsername(candidate)) {
            errorKey = "config.party.whitelist.invalid";
            return;
        }
        boolean duplicate = chat.partyAutoAcceptWhitelist.stream()
                .anyMatch(name -> name.equalsIgnoreCase(candidate)
                        && (editorMode != EditorMode.EDIT || !name.equalsIgnoreCase(editingOriginal)));
        if (duplicate) {
            errorKey = "config.party.whitelist.duplicate";
            return;
        }
        if (editorMode == EditorMode.ADD
                && chat.partyAutoAcceptWhitelist.size() >= ModConfig.Chat.PARTY_AUTO_ACCEPT_WHITELIST_LIMIT) {
            errorKey = "config.party.whitelist.limit";
            return;
        }
        boolean changed = editorMode == EditorMode.ADD
                ? chat.addPartyAutoAcceptWhitelist(candidate)
                : chat.replacePartyAutoAcceptWhitelist(editingOriginal, candidate);
        if (!changed) {
            errorKey = "config.party.whitelist.duplicate";
            return;
        }
        ConfigManager.save();
        closeEditor();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (editorMode != EditorMode.NONE) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                closeEditor();
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                confirmEditor();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (rowsScrollbar.dragging()) {
            VerticalScrollbar.Interaction scrollbarWheel = rowsScrollbar.mouseScrolled(vertical, 22, scroll);
            scroll = scrollbarWheel.scroll();
            return true;
        }
        if (AcaUiTheme.contains(mouseX, mouseY, contentX, rowsY, contentWidth, rowsHeight)
                || rowsScrollbar.contains(mouseX, mouseY)) {
            VerticalScrollbar.Interaction scrollbarWheel = rowsScrollbar.mouseScrolled(vertical, 22, scroll);
            if (scrollbarWheel.consumed()) scroll = scrollbarWheel.scroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void onClose() {
        ConfigManager.save();
        MinecraftClientCompat.setScreen(minecraft, parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum EditorMode { NONE, ADD, EDIT }

    private enum Action { ADD, EDIT, DELETE, CONFIRM, CANCEL }

    private record Hit(Action action, String name, int x, int y, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return AcaUiTheme.contains(mouseX, mouseY, x, y, width, height);
        }
    }
}

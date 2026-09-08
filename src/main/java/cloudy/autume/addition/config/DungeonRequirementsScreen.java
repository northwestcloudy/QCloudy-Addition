package cloudy.autume.addition.config;

import cloudy.autume.addition.compat.MinecraftClientCompat;
import cloudy.autume.addition.dungeon.DungeonQuickViewManager;
import cloudy.autume.addition.i18n.ModText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

/** Per-floor editor for the fourteen independent Party Finder admission policies. */
final class DungeonRequirementsScreen extends Screen {
    private static final int ROW_HEIGHT = 29;
    private static final int ROW_GAP = 4;
    private static final int FLOOR_GAP = 4;
    private static final int FLOOR_HEIGHT = 20;

    private final Screen parent;
    private final long openedAt = System.nanoTime();
    private final VerticalScrollbar rowsScrollbar = new VerticalScrollbar();
    private final List<Hit> hits = new ArrayList<>();
    private final EnumMap<RequirementOption, EditBox> fields = new EnumMap<>(RequirementOption.class);
    private final EnumMap<RequirementOption, Boolean> validFields = new EnumMap<>(RequirementOption.class);
    private ModConfig.DungeonFloor selectedFloor = ModConfig.DungeonFloor.F7;
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

    DungeonRequirementsScreen(Screen parent) {
        super(ModText.component("config.dungeon_requirements.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rowsScrollbar.cancelDrag();
        layout();
        fields.clear();
        validFields.clear();
        for (RequirementOption option : RequirementOption.values()) {
            if (!option.numeric()) continue;
            EditBox box = new EditBox(font, 0, 0, 96, font.lineHeight,
                    ModText.component(option.labelKey));
            box.setBordered(false);
            box.setTextShadow(false);
            box.setTextColor(AcaUiTheme.TEXT);
            box.setMaxLength(option == RequirementOption.MIN_AVERAGE_SECRETS ? 32 : 12);
            box.setValue(option.valueText(rules()));
            box.setResponder(value -> updateNumericValue(option, value));
            fields.put(option, box);
            validFields.put(option, true);
            addRenderableWidget(box);
        }
        updateFieldGeometry();
    }

    private void layout() {
        windowWidth = Math.max(1, Math.min(560, width - Math.min(20, Math.max(0, width - 1))));
        windowHeight = Math.max(1, Math.min(440, height - Math.min(20, Math.max(0, height - 1))));
        windowX = (width - windowWidth) / 2;
        windowY = (height - windowHeight) / 2;
        contentX = windowX + 12;
        contentWidth = Math.max(1, windowWidth - 29);
        rowsY = windowY + 140;
        // Keep a small footer clear for invalid-input feedback instead of
        // painting it over the final rule row.
        rowsHeight = Math.max(1, windowY + windowHeight - rowsY - 28);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        layout();
        updateFieldGeometry();
        graphics.fill(0, 0, width, height, AcaUiTheme.SCRIM);
        UiAnimation.push(graphics, UiAnimation.scale(openedAt), width / 2.0f, height / 2.0f);
        graphics.fill(windowX + 4, windowY + 5, windowX + windowWidth + 5,
                windowY + windowHeight + 6, 0x66000000);
        AcaUiTheme.surface(graphics, windowX, windowY, windowWidth, windowHeight, AcaUiTheme.WINDOW);
        graphics.fill(windowX + 1, windowY + 1, windowX + windowWidth - 1,
                windowY + 34, AcaUiTheme.HEADER);
        AcaUiTheme.button(graphics, font, "‹", windowX + 10, windowY + 8, 24, 18,
                AcaUiTheme.contains(mouseX, mouseY, windowX + 10, windowY + 8, 24, 18), false);
        drawFitted(graphics, Component.literal(ModText.get("config.dungeon_requirements.title"))
                        .withStyle(ChatFormatting.BOLD), windowX + 42, windowY + 10,
                Math.max(1, windowWidth - 54), AcaUiTheme.TEXT);

        hits.clear();
        drawMaster(graphics, mouseX, mouseY);
        drawFloorSelector(graphics, mouseX, mouseY);
        drawRules(graphics, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        UiAnimation.pop(graphics);
    }

    private void drawMaster(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        ModConfig.PartyFinderAutoKick policy = policy();
        int y = windowY + 42;
        boolean hovered = AcaUiTheme.contains(mouseX, mouseY, contentX, y, contentWidth, 30);
        graphics.fill(contentX, y, contentX + contentWidth, y + 30,
                hovered ? AcaUiTheme.CARD_HOVER : AcaUiTheme.CARD);
        graphics.outline(contentX, y, contentWidth, 30,
                hovered ? AcaUiTheme.ACCENT_DARK : AcaUiTheme.BORDER_SOFT);
        drawFitted(graphics, ModText.component("config.dungeon_requirements.auto_kick"),
                contentX + 10, y + 10, Math.max(1, contentWidth - 96), AcaUiTheme.TEXT);
        AcaUiTheme.toggle(graphics, contentX + contentWidth - 39, y + 8, policy.enabled);
        hits.add(new Hit(HitType.MASTER, null, null, contentX, y, contentWidth, 30));

        int hintColor = policy.enabled ? AcaUiTheme.DANGER : AcaUiTheme.TEXT_MUTED;
        drawFitted(graphics, ModText.component(policy.enabled
                        ? "config.dungeon_requirements.auto_kick_active"
                        : "config.dungeon_requirements.auto_kick_inactive"),
                contentX, y + 36, contentWidth, hintColor);
    }

    private void drawFloorSelector(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int y = windowY + 96;
        int floorWidth = Math.max(1, (contentWidth - FLOOR_GAP * 6) / 7);
        ModConfig.DungeonFloor[] floors = ModConfig.DungeonFloor.values();
        for (int index = 0; index < floors.length; index++) {
            ModConfig.DungeonFloor floor = floors[index];
            int column = index % 7;
            int row = index / 7;
            int x = contentX + column * (floorWidth + FLOOR_GAP);
            int tabY = y + row * (FLOOR_HEIGHT + FLOOR_GAP);
            boolean selected = floor == selectedFloor;
            boolean hovered = AcaUiTheme.contains(mouseX, mouseY, x, tabY, floorWidth, FLOOR_HEIGHT);
            AcaUiTheme.button(graphics, font, floor.id(), x, tabY, floorWidth, FLOOR_HEIGHT,
                    hovered, selected);
            hits.add(new Hit(HitType.FLOOR, floor, null, x, tabY, floorWidth, FLOOR_HEIGHT));
        }
    }

    private void drawRules(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int contentHeight = RequirementOption.values().length * (ROW_HEIGHT + ROW_GAP) - ROW_GAP;
        maxScroll = Math.max(0, contentHeight - rowsHeight);
        scroll = Math.clamp(scroll, 0, maxScroll);
        updateFieldGeometry();
        graphics.enableScissor(contentX, rowsY, contentX + contentWidth, rowsY + rowsHeight);
        int y = rowsY - scroll;
        ModConfig.DungeonFloorRequirements rules = rules();
        for (RequirementOption option : RequirementOption.values()) {
            boolean hovered = AcaUiTheme.contains(mouseX, mouseY, contentX, y, contentWidth, ROW_HEIGHT)
                    && AcaUiTheme.contains(mouseX, mouseY, contentX, rowsY, contentWidth, rowsHeight);
            graphics.fill(contentX, y, contentX + contentWidth, y + ROW_HEIGHT,
                    hovered ? AcaUiTheme.CARD_HOVER : AcaUiTheme.CARD);
            graphics.outline(contentX, y, contentWidth, ROW_HEIGHT,
                    hovered ? AcaUiTheme.ACCENT_DARK : AcaUiTheme.BORDER_SOFT);
            boolean enabled = option.enabled(rules);
            AcaUiTheme.toggle(graphics, contentX + 8, y + 8, enabled);
            int fieldWidth = option.numeric() ? Math.min(112, Math.max(62, contentWidth / 4)) : 0;
            int labelRight = option.numeric() ? contentX + contentWidth - fieldWidth - 17
                    : contentX + contentWidth - 54;
            drawFitted(graphics, ModText.component(option.labelKey), contentX + 48, y + 10,
                    Math.max(1, labelRight - contentX - 53), enabled ? AcaUiTheme.TEXT : AcaUiTheme.TEXT_MUTED);
            if (option.numeric()) {
                EditBox field = fields.get(option);
                if (field != null && field.visible) {
                    int boxX = contentX + contentWidth - fieldWidth - 8;
                    graphics.fill(boxX, y + 5, boxX + fieldWidth, y + ROW_HEIGHT - 5,
                            AcaUiTheme.CONTROL);
                    graphics.outline(boxX, y + 5, fieldWidth, ROW_HEIGHT - 10,
                            validFields.getOrDefault(option, true) ? AcaUiTheme.BORDER : AcaUiTheme.DANGER);
                }
            } else {
                String value = ModText.get(enabled ? "config.enabled" : "config.disabled");
                graphics.text(font, value, contentX + contentWidth - 10 - font.width(value), y + 10,
                        enabled ? AcaUiTheme.ACCENT : AcaUiTheme.TEXT_DIM, false);
            }
            if (y + ROW_HEIGHT > rowsY && y < rowsY + rowsHeight) {
                hits.add(new Hit(HitType.RULE, null, option,
                        contentX, y, option.numeric() ? Math.max(1, contentWidth - fieldWidth - 12) : contentWidth,
                        ROW_HEIGHT));
            }
            y += ROW_HEIGHT + ROW_GAP;
        }
        graphics.disableScissor();
        rowsScrollbar.update(contentX + contentWidth + 2, rowsY, rowsHeight, maxScroll, scroll);
        rowsScrollbar.draw(graphics, mouseX, mouseY, AcaUiTheme.ACCENT);
        if (validFields.containsValue(false)) {
            String error = ModText.get("config.dungeon_requirements.invalid_value");
            graphics.text(font, error, contentX, windowY + windowHeight - 9 - font.lineHeight,
                    AcaUiTheme.DANGER, false);
        }
    }

    private void updateFieldGeometry() {
        if (fields.isEmpty()) return;
        int y = rowsY - scroll;
        int fieldWidth = Math.min(112, Math.max(62, contentWidth / 4));
        for (RequirementOption option : RequirementOption.values()) {
            EditBox field = fields.get(option);
            if (field != null) {
                field.setX(contentX + contentWidth - fieldWidth - 3);
                field.setY(y + 10);
                field.setWidth(Math.max(1, fieldWidth - 10));
                field.setVisible(y >= rowsY && y + ROW_HEIGHT <= rowsY + rowsHeight);
            }
            y += ROW_HEIGHT + ROW_GAP;
        }
    }

    private void updateNumericValue(RequirementOption option, String text) {
        Number parsed = option.parse(text);
        boolean valid = parsed != null;
        validFields.put(option, valid);
        if (!valid) return;
        option.setValue(rules(), parsed);
        DungeonQuickViewManager.onAdmissionPolicyChanged();
        ConfigManager.save();
    }

    private ModConfig.PartyFinderAutoKick policy() {
        return ConfigManager.get().dungeons.partyFinderAutoKick;
    }

    private ModConfig.DungeonFloorRequirements rules() {
        ModConfig.DungeonFloorRequirements rules = policy().rulesFor(selectedFloor);
        if (rules == null) {
            policy().normalize();
            rules = policy().rulesFor(selectedFloor);
        }
        return rules;
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
            switch (hit.type) {
                case MASTER -> toggleMaster();
                case FLOOR -> selectFloor(hit.floor);
                case RULE -> toggleRule(hit.option);
            }
            return true;
        }
        return false;
    }

    private void toggleMaster() {
        if (policy().enabled) {
            policy().enabled = false;
            DungeonQuickViewManager.onAdmissionPolicyChanged();
            ConfigManager.save();
            return;
        }
        MinecraftClientCompat.setScreen(minecraft, new DungeonAutoKickConfirmScreen(this));
    }

    private void selectFloor(ModConfig.DungeonFloor floor) {
        if (floor == null || floor == selectedFloor) return;
        selectedFloor = floor;
        scroll = 0;
        rebuildWidgets();
    }

    private void toggleRule(RequirementOption option) {
        if (option == null) return;
        option.setEnabled(rules(), !option.enabled(rules()));
        DungeonQuickViewManager.onAdmissionPolicyChanged();
        ConfigManager.save();
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        VerticalScrollbar.Interaction interaction = rowsScrollbar.mouseDragged(click.button(), click.y(), scroll);
        if (interaction.consumed()) {
            scroll = interaction.scroll();
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        VerticalScrollbar.Interaction interaction = rowsScrollbar.mouseReleased(click.button(), click.y(), scroll);
        if (interaction.consumed()) {
            scroll = interaction.scroll();
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (AcaUiTheme.contains(mouseX, mouseY, contentX, rowsY, contentWidth, rowsHeight)
                || rowsScrollbar.contains(mouseX, mouseY)) {
            VerticalScrollbar.Interaction interaction = rowsScrollbar.mouseScrolled(vertical, ROW_HEIGHT, scroll);
            if (interaction.consumed()) scroll = interaction.scroll();
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

    static Long parseWholeNumber(String value, long minimum, long maximum) {
        if (value == null || !value.matches("[0-9]+")) return null;
        try {
            long parsed = Long.parseLong(value);
            return parsed >= minimum && parsed <= maximum ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static Long parseDurationMs(String value) {
        if (value == null || !value.matches("[0-9]{1,4}:[0-5][0-9]")) return null;
        int separator = value.indexOf(':');
        try {
            long minutes = Long.parseLong(value.substring(0, separator));
            long seconds = Long.parseLong(value.substring(separator + 1));
            long milliseconds = (minutes * 60L + seconds) * 1_000L;
            return milliseconds >= 1_000L && milliseconds <= 86_400_000L ? milliseconds : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static Double parseDecimal(String value, double minimum, double maximum) {
        if (value == null || !value.matches("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)")) return null;
        try {
            BigDecimal parsed = new BigDecimal(value);
            if (parsed.compareTo(BigDecimal.valueOf(minimum)) < 0
                    || parsed.compareTo(BigDecimal.valueOf(maximum)) > 0) return null;
            double stored = parsed.doubleValue();
            return Double.isFinite(stored) ? stored : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static String formatDuration(long milliseconds) {
        long totalSeconds = Math.max(0L, milliseconds) / 1_000L;
        return String.format(Locale.ROOT, "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L);
    }

    private void drawFitted(GuiGraphicsExtractor graphics, Component text, int x, int y,
                            int availableWidth, int color) {
        if (availableWidth <= 0) return;
        int textWidth = font.width(text);
        if (textWidth <= availableWidth) {
            graphics.text(font, text, x, y, color, false);
            return;
        }
        float scale = availableWidth / (float) Math.max(1, textWidth);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y + Math.round((1.0f - scale) * 4.0f));
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, 0, 0, color, false);
        graphics.pose().popMatrix();
    }

    private enum RequirementOption {
        MIN_FLOOR_COMPLETIONS("config.dungeon_requirements.min_completions", ValueKind.WHOLE),
        DISALLOW_DUPLICATE_CLASS("config.dungeon_requirements.no_dupe", ValueKind.NONE),
        MAX_FASTEST_COMPLETION("config.dungeon_requirements.max_fastest", ValueKind.DURATION),
        MIN_AVERAGE_SECRETS("config.dungeon_requirements.min_average_secrets", ValueKind.DECIMAL),
        MIN_MAGICAL_POWER("config.dungeon_requirements.min_magical_power", ValueKind.WHOLE),
        REQUIRE_WITHER_BLADE("config.dungeon_requirements.require_wither_blade", ValueKind.NONE),
        REQUIRE_TERMINATOR("config.dungeon_requirements.require_terminator", ValueKind.NONE),
        REQUIRE_GOLDEN_DRAGON("config.dungeon_requirements.require_golden_dragon", ValueKind.NONE),
        REQUIRE_ENDER_DRAGON("config.dungeon_requirements.require_ender_dragon", ValueKind.NONE);

        private final String labelKey;
        private final ValueKind kind;

        RequirementOption(String labelKey, ValueKind kind) {
            this.labelKey = labelKey;
            this.kind = kind;
        }

        boolean numeric() {
            return kind != ValueKind.NONE;
        }

        boolean enabled(ModConfig.DungeonFloorRequirements rules) {
            return switch (this) {
                case MIN_FLOOR_COMPLETIONS -> rules.minFloorCompletions.enabled;
                case DISALLOW_DUPLICATE_CLASS -> rules.disallowDuplicateClass;
                case MAX_FASTEST_COMPLETION -> rules.maxFastestCompletionMs.enabled;
                case MIN_AVERAGE_SECRETS -> rules.minAverageSecrets.enabled;
                case MIN_MAGICAL_POWER -> rules.minMagicalPower.enabled;
                case REQUIRE_WITHER_BLADE -> rules.requireWitherBlade;
                case REQUIRE_TERMINATOR -> rules.requireTerminator;
                case REQUIRE_GOLDEN_DRAGON -> rules.requireGoldenDragon;
                case REQUIRE_ENDER_DRAGON -> rules.requireEnderDragon;
            };
        }

        void setEnabled(ModConfig.DungeonFloorRequirements rules, boolean enabled) {
            switch (this) {
                case MIN_FLOOR_COMPLETIONS -> rules.minFloorCompletions.enabled = enabled;
                case DISALLOW_DUPLICATE_CLASS -> rules.disallowDuplicateClass = enabled;
                case MAX_FASTEST_COMPLETION -> rules.maxFastestCompletionMs.enabled = enabled;
                case MIN_AVERAGE_SECRETS -> rules.minAverageSecrets.enabled = enabled;
                case MIN_MAGICAL_POWER -> rules.minMagicalPower.enabled = enabled;
                case REQUIRE_WITHER_BLADE -> rules.requireWitherBlade = enabled;
                case REQUIRE_TERMINATOR -> rules.requireTerminator = enabled;
                case REQUIRE_GOLDEN_DRAGON -> rules.requireGoldenDragon = enabled;
                case REQUIRE_ENDER_DRAGON -> rules.requireEnderDragon = enabled;
            }
        }

        String valueText(ModConfig.DungeonFloorRequirements rules) {
            return switch (this) {
                case MIN_FLOOR_COMPLETIONS -> Long.toString(rules.minFloorCompletions.value);
                case MAX_FASTEST_COMPLETION -> formatDuration(rules.maxFastestCompletionMs.value);
                case MIN_AVERAGE_SECRETS -> BigDecimal.valueOf(rules.minAverageSecrets.value)
                        .stripTrailingZeros().toPlainString();
                case MIN_MAGICAL_POWER -> Long.toString(rules.minMagicalPower.value);
                default -> "";
            };
        }

        Number parse(String value) {
            return switch (kind) {
                case NONE -> null;
                case WHOLE -> this == MIN_FLOOR_COMPLETIONS
                        ? parseWholeNumber(value, 0, 1_000_000)
                        : parseWholeNumber(value, 0, 100_000);
                case DURATION -> parseDurationMs(value);
                case DECIMAL -> parseDecimal(value, 0.0, 1_000.0);
            };
        }

        void setValue(ModConfig.DungeonFloorRequirements rules, Number value) {
            switch (this) {
                case MIN_FLOOR_COMPLETIONS -> rules.minFloorCompletions.value = value.longValue();
                case MAX_FASTEST_COMPLETION -> rules.maxFastestCompletionMs.value = value.longValue();
                case MIN_AVERAGE_SECRETS -> rules.minAverageSecrets.value = value.doubleValue();
                case MIN_MAGICAL_POWER -> rules.minMagicalPower.value = value.longValue();
                default -> { }
            }
        }
    }

    private enum ValueKind { NONE, WHOLE, DURATION, DECIMAL }

    private enum HitType { MASTER, FLOOR, RULE }

    private record Hit(HitType type, ModConfig.DungeonFloor floor, RequirementOption option,
                       int x, int y, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return AcaUiTheme.contains(mouseX, mouseY, x, y, width, height);
        }
    }
}

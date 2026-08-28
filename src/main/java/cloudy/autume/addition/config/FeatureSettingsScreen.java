package cloudy.autume.addition.config;

import cloudy.autume.addition.compat.MinecraftClientCompat;
import cloudy.autume.addition.QCloudyAdditionClient;
import cloudy.autume.addition.i18n.ModText;
import cloudy.autume.addition.inventory.CenturyCakeEffectsScreen;
import cloudy.autume.addition.inventory.ShardPlanningScreen;
import cloudy.autume.addition.input.HotkeyInputs;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

final class FeatureSettingsScreen extends Screen {
    private static final int ROW_HEIGHT = 27;
    private final Screen parent;
    private final ConfigScreen.@Nullable Feature feature;
    private final UnifiedModIntegration.UnifiedFeature unifiedFeature;
    private final long openedAt = System.nanoTime();
    private final List<Hit> hits = new ArrayList<>();
    private int windowX;
    private int windowY;
    private int windowWidth;
    private int windowHeight;
    private int contentX;
    private int contentY;
    private int contentWidth;
    private int scroll;
    private int maxScroll;
    private Hit draggingSlider;
    private QCloudyAdditionClient.ChordAction listeningChord;

    FeatureSettingsScreen(Screen parent, ConfigScreen.Feature feature) {
        this(parent, java.util.Objects.requireNonNull(UnifiedModIntegration.forQCloudy(feature)));
    }

    FeatureSettingsScreen(Screen parent, UnifiedModIntegration.UnifiedFeature unifiedFeature) {
        super(Component.literal(unifiedFeature.title));
        this.parent = parent;
        this.feature = unifiedFeature.qcloudyFeature;
        this.unifiedFeature = unifiedFeature;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        layout();
        graphics.fill(0, 0, width, height, AcaUiTheme.SCRIM);
        UiAnimation.push(graphics, UiAnimation.scale(openedAt), width / 2.0f, height / 2.0f);
        graphics.fill(windowX + 4, windowY + 5, windowX + windowWidth + 5, windowY + windowHeight + 6, 0x66000000);
        AcaUiTheme.surface(graphics, windowX, windowY, windowWidth, windowHeight, AcaUiTheme.WINDOW);
        graphics.fill(windowX + 1, windowY + 1, windowX + windowWidth - 1, windowY + 34, AcaUiTheme.HEADER);
        drawFittedText(graphics, Component.literal(unifiedFeature.title).withStyle(ChatFormatting.BOLD),
                windowX + 42, windowY + 10, Math.max(1, windowWidth - 54), AcaUiTheme.TEXT);
        AcaUiTheme.button(graphics, font, "‹", windowX + 10, windowY + 8, 24, 18,
                AcaUiTheme.contains(mouseX, mouseY, windowX + 10, windowY + 8, 24, 18), false);
        String hint = ModText.get("config.feature_settings_hint");
        drawFittedText(graphics, hint, windowX + 12, windowY + 43,
                Math.max(1, windowWidth - 24));

        hits.clear();
        List<Setting> settings = settings();
        int viewportY = contentY;
        int viewportHeight = viewportHeight();
        maxScroll = Math.max(0, settings.size() * (ROW_HEIGHT + 4) - 4 - viewportHeight);
        scroll = Math.clamp(scroll, 0, maxScroll);
        graphics.enableScissor(contentX, viewportY, contentX + contentWidth, viewportY + viewportHeight);
        int y = contentY - scroll;
        for (Setting setting : settings) {
            drawRow(graphics, setting, contentX, y, contentWidth, mouseX, mouseY);
            if (y + ROW_HEIGHT > viewportY && y < viewportY + viewportHeight) {
                hits.add(new Hit(setting, contentX, y, contentWidth, ROW_HEIGHT));
            }
            y += ROW_HEIGHT + 4;
        }
        graphics.disableScissor();
        if (maxScroll > 0) {
            int thumbHeight = Math.max(18, viewportHeight * viewportHeight / (viewportHeight + maxScroll));
            int thumbY = viewportY + (viewportHeight - thumbHeight) * scroll / maxScroll;
            graphics.fill(contentX + contentWidth + 3, viewportY, contentX + contentWidth + 5,
                    viewportY + viewportHeight, AcaUiTheme.CONTROL);
            graphics.fill(contentX + contentWidth + 3, thumbY, contentX + contentWidth + 5,
                    thumbY + thumbHeight, AcaUiTheme.ACCENT);
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        UiAnimation.pop(graphics);
    }

    private void layout() {
        windowWidth = Math.max(1, Math.min(520, width - Math.min(24, Math.max(0, width - 1))));
        windowHeight = Math.max(1, Math.min(390, height - Math.min(24, Math.max(0, height - 1))));
        windowX = (width - windowWidth) / 2;
        windowY = (height - windowHeight) / 2;
        contentX = windowX + 12;
        contentY = windowY + 60;
        contentWidth = Math.max(1, windowWidth - 29);
    }

    private void drawRow(GuiGraphicsExtractor graphics, Setting setting, int x, int y, int rowWidth,
                         int mouseX, int mouseY) {
        boolean available = setting.available();
        boolean hovered = available && AcaUiTheme.contains(mouseX, mouseY, x, y, rowWidth, ROW_HEIGHT);
        boolean listening = setting.chordAction() != null && setting.chordAction() == listeningChord;
        graphics.fill(x, y, x + rowWidth, y + ROW_HEIGHT,
                listening ? 0xFF263D47 : hovered ? AcaUiTheme.CARD_HOVER : AcaUiTheme.CARD);
        graphics.outline(x, y, rowWidth, ROW_HEIGHT,
                listening ? AcaUiTheme.ACCENT : hovered ? AcaUiTheme.ACCENT_DARK : AcaUiTheme.BORDER_SOFT);
        String value = setting.value();
        if (setting.kind == Kind.INTEGRATION_SCAN_PROGRESS) {
            UnifiedModIntegration.ScanStatus status = UnifiedModIntegration.scanStatus(integrationScanView());
            drawFittedText(graphics, setting.label(), x + 10, y + 4, Math.max(1, rowWidth - 75));
            drawFittedTextRight(graphics, value, x + rowWidth - 10, y + 4, 60, AcaUiTheme.TEXT_MUTED);
            int trackX = x + 10;
            int trackY = y + 18;
            int trackWidth = Math.max(1, rowWidth - 20);
            int filled = Math.round(trackWidth * Math.clamp(status.percent(), 0, 100) / 100.0f);
            graphics.fill(trackX, trackY, trackX + trackWidth, trackY + 4, AcaUiTheme.CONTROL);
            graphics.fill(trackX, trackY, trackX + filled, trackY + 4,
                    status.state() == UnifiedModIntegration.ScanState.FAILED ? 0xFFE14D4D : AcaUiTheme.ACCENT);
            return;
        }
        if (setting.chordAction() != null) {
            int maximumValueWidth = Math.max(1, Math.min(rowWidth / 2, rowWidth - 21));
            int labelWidth = Math.max(1, rowWidth - maximumValueWidth - 30);
            drawFittedText(graphics, setting.label(), x + 10, y + 9, labelWidth);
            drawFittedTextRight(graphics, value, x + rowWidth - 10, y + 9, maximumValueWidth,
                    listening ? AcaUiTheme.ACCENT : AcaUiTheme.TEXT_MUTED);
            return;
        }
        if (setting.slider()) {
            SliderLayout slider = sliderLayout(x, rowWidth);
            int trackEnd = slider.trackX() + slider.trackWidth();
            int labelWidth = Math.max(1, slider.trackX() - x - 18);
            drawFittedText(graphics, setting.label(), x + 10, y + 9, labelWidth);
            int trackY = y + 12;
            int knobX = slider.trackX() + (int) Math.round(setting.sliderFraction() * slider.trackWidth());
            graphics.fill(slider.trackX(), trackY, trackEnd, trackY + 3, 0xFF69747A);
            graphics.fill(slider.trackX(), trackY, knobX, trackY + 3, AcaUiTheme.ACCENT);
            graphics.fill(knobX - 4, y + 7, knobX + 5, y + 21, 0xFFBCEEFF);
            graphics.outline(knobX - 4, y + 7, 9, 14, AcaUiTheme.ACCENT_DARK);
            drawFittedTextRight(graphics, value, x + rowWidth - 10, y + 9,
                    Math.max(1, rowWidth - trackEnd + x - 10), AcaUiTheme.TEXT_MUTED);
            return;
        }
        int valueWidth = Math.max(1, Math.min(rowWidth / 2,
                rowWidth - (setting.color() ? 40 : 20)));
        int swatchX = setting.color()
                ? Math.max(x + 2, x + rowWidth - 10 - valueWidth - 18)
                : x + rowWidth;
        drawFittedText(graphics, Component.literal(setting.label()), x + 10, y + 9,
                Math.max(1, (setting.color() ? swatchX : x + rowWidth - valueWidth - 10) - x - 14),
                available ? AcaUiTheme.TEXT : AcaUiTheme.TEXT_DIM);
        if (setting.color()) {
            if (setting.kind == Kind.BACKGROUND_COLOR && panelStyle().backgroundOpacity == 0) {
                graphics.fill(swatchX, y + 7, swatchX + 13, y + 20, 0xFFE6E6E6);
                graphics.fill(swatchX, y + 7, swatchX + 6, y + 13, 0xFF999999);
                graphics.fill(swatchX + 6, y + 13, swatchX + 13, y + 20, 0xFF999999);
            } else {
                graphics.fill(swatchX, y + 7, swatchX + 13, y + 20, 0xFF000000 | setting.colorValue());
            }
            graphics.outline(swatchX, y + 7, 13, 13, AcaUiTheme.BORDER);
        }
        drawFittedTextRight(graphics, value, x + rowWidth - 10, y + 9,
                valueWidth, available ? AcaUiTheme.TEXT_MUTED : AcaUiTheme.TEXT_DIM);
    }

    private void drawFittedText(GuiGraphicsExtractor graphics, String text, int x, int y, int availableWidth) {
        drawFittedText(graphics, Component.literal(text), x, y, availableWidth, AcaUiTheme.TEXT);
    }

    private void drawFittedText(GuiGraphicsExtractor graphics, Component text, int x, int y,
                                int availableWidth, int color) {
        if (availableWidth <= 0) return;
        int textWidth = font.width(text);
        if (textWidth <= availableWidth) {
            graphics.text(font, text, x, y, color, false);
            return;
        }
        float scale = Math.min(1.0f, availableWidth / (float) Math.max(1, textWidth));
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y + Math.round((1.0f - scale) * 4.0f));
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, 0, 0, color, false);
        graphics.pose().popMatrix();
    }

    private void drawFittedTextRight(GuiGraphicsExtractor graphics, String text, int rightX, int y,
                                     int availableWidth, int color) {
        int textWidth = font.width(text);
        float scale = textWidth <= availableWidth ? 1.0f : availableWidth / (float) textWidth;
        graphics.pose().pushMatrix();
        graphics.pose().translate(rightX - textWidth * scale, y + Math.round((1.0f - scale) * 4.0f));
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, 0, 0, color, false);
        graphics.pose().popMatrix();
    }

    private UnifiedModIntegration.ScanView integrationScanView() {
        return feature == ConfigScreen.Feature.UNIFIED_HUD_EDITOR
                ? UnifiedModIntegration.ScanView.HUD : UnifiedModIntegration.ScanView.SETTINGS;
    }

    private List<Setting> settings() {
        List<Setting> rows = new ArrayList<>();
        // The death-save alert card is a plain master toggle, not a HUD. Keep a
        // defensive empty settings page so it can never fall through to the
        // generic appearance controls and mutate the map HUD style.
        if (feature == ConfigScreen.Feature.DEATH_SAVE_ALERTS) return rows;
        if (feature == ConfigScreen.Feature.UNIFIED_SETTINGS_EDITOR
                || feature == ConfigScreen.Feature.UNIFIED_HUD_EDITOR) {
            rows.add(new Setting(Kind.INTEGRATION_SCAN_PROGRESS, "config.integration.scan.progress"));
            rows.add(new Setting(Kind.INTEGRATION_SCAN_CURRENT, "config.integration.scan.current"));
            rows.add(new Setting(Kind.INTEGRATION_SCAN_SUMMARY, "config.integration.scan.summary"));
            rows.add(new Setting(Kind.INTEGRATION_SCAN_PROVIDERS, "config.integration.scan.providers"));
            UnifiedModIntegration.ScanStatus status = UnifiedModIntegration.scanStatus(integrationScanView());
            int events = Math.min(3, status.recentItems().size());
            if (events >= 1) rows.add(new Setting(Kind.INTEGRATION_SCAN_EVENT_1, "config.integration.scan.recent"));
            if (events >= 2) rows.add(new Setting(Kind.INTEGRATION_SCAN_EVENT_2, "config.integration.scan.recent"));
            if (events >= 3) rows.add(new Setting(Kind.INTEGRATION_SCAN_EVENT_3, "config.integration.scan.recent"));
            rows.add(new Setting(Kind.INTEGRATION_SCAN_REFRESH, "config.integration.scan.refresh"));
            return rows;
        }
        if (feature == ConfigScreen.Feature.PARTY_AUTO_ACCEPT) {
            rows.add(new Setting(Kind.PARTY_FRIEND_MODE, "config.setting.party_friend_mode"));
            rows.add(new Setting(Kind.OPEN_PARTY_WHITELIST, "config.setting.party_whitelist"));
            return rows;
        }
        if (feature == ConfigScreen.Feature.FAST_PARTY_COMMANDS) {
            for (PartyCommandOption option : PartyCommandOption.values()) {
                rows.add(new Setting(option, false, false));
                rows.add(new Setting(option, true, false));
            }
            return rows;
        }
        if (feature == ConfigScreen.Feature.PARTY_COMMANDS) {
            for (PartyCommandOption option : PartyCommandOption.values()) {
                rows.add(new Setting(option, false, true));
            }
            return rows;
        }
        rows.add(new Setting(Kind.PROVIDER, "config.integration.provider"));
        UnifiedModIntegration.Provider provider = unifiedFeature.selectedProvider();
        if (provider != UnifiedModIntegration.Provider.QCLOUDY) {
            UnifiedModIntegration.NativeFeature binding = unifiedFeature.binding(provider);
            if (binding == null) {
                rows.add(new Setting(Kind.EXTERNAL_STATUS, "config.integration.status"));
                return rows;
            }
            for (UnifiedModIntegration.NativeSetting setting : binding.settings) {
                if (setting.editable()) rows.add(new Setting(setting));
            }
            if (rows.size() == 1) {
                rows.add(new Setting(Kind.EXTERNAL_STATUS, "config.integration.no_secondary_settings"));
            }
            return rows;
        }
        if (feature == null) {
            rows.add(new Setting(Kind.EXTERNAL_STATUS, "config.integration.no_secondary_settings"));
            return rows;
        }
        if (feature.huntingFeature()) {
            for (HuntingOption option : HuntingOption.forFeature(feature)) rows.add(new Setting(option));
            if (feature.hudType() != null) {
                rows.add(new Setting(Kind.OPACITY, "config.setting.opacity"));
                rows.add(new Setting(Kind.BACKGROUND_COLOR, "config.setting.background_color"));
                rows.add(new Setting(Kind.BORDER, "config.setting.border"));
                rows.add(new Setting(Kind.BORDER_SIZE, "config.setting.border_size"));
                rows.add(new Setting(Kind.BORDER_COLOR, "config.setting.border_color"));
                rows.add(new Setting(Kind.TITLE_COLOR, "config.setting.title_color"));
                rows.add(new Setting(Kind.BOLD, "config.setting.bold"));
                rows.add(new Setting(Kind.SHADOW, "config.setting.shadow"));
                rows.add(new Setting(Kind.SCALE, "config.setting.scale"));
                rows.add(new Setting(Kind.EDIT_LAYOUT, "config.layout"));
            }
            return rows;
        }
        if (feature == ConfigScreen.Feature.SHARD_FUSION_HELPER) {
            rows.add(new Setting(Kind.OPEN_SHARD_PLANNER, "config.setting.open_shard_planner"));
            rows.add(new Setting(Kind.OPEN_SHARD_GUIDE, "config.setting.open_shard_guide"));
            rows.add(new Setting(Kind.SHARD_GUIDE_KEY, "config.setting.shard_guide_key"));
            return rows;
        }
        if (feature == ConfigScreen.Feature.FISHING_BITE_ALERT) {
            rows.add(new Setting(Kind.FISHING_BITE_VOLUME, "config.setting.fishing_bite_volume"));
            return rows;
        }
        if (feature == ConfigScreen.Feature.DEPLOYABLE_EXPIRY_ALERT) {
            rows.add(new Setting(Kind.DEPLOYABLE_POWER_ORB_ALERTS,
                    "config.setting.deployable_power_orb_alerts"));
            rows.add(new Setting(Kind.DEPLOYABLE_FLARE_ALERTS,
                    "config.setting.deployable_flare_alerts"));
            rows.add(new Setting(Kind.DEPLOYABLE_EXPIRY_CENTER_TEXT,
                    "config.setting.deployable_center_text"));
            rows.add(new Setting(Kind.DEPLOYABLE_EXPIRY_SOUND, "config.alert.sound"));
            rows.add(new Setting(Kind.DEPLOYABLE_EXPIRY_VOLUME, "config.alert.volume"));
            return rows;
        }
        if (feature == ConfigScreen.Feature.CENTURY_CAKE_EFFECTS) {
            rows.add(new Setting(Kind.OPEN_CENTURY_CAKES, "config.setting.open_century_cakes"));
            rows.add(new Setting(Kind.CENTURY_CAKE_SOUND, "config.setting.century_cake_sound"));
            rows.add(new Setting(Kind.CENTURY_CAKE_VOLUME, "config.setting.century_cake_volume"));
            return rows;
        }
        if (feature.inventoryFeature()) {
            rows.add(new Setting(Kind.YIELD_FIRMAMENT, "config.setting.yield_firmament"));
            switch (feature) {
                case ITEM_TIMESTAMPS -> {
                    rows.add(new Setting(Kind.SHOW_CREATION, "config.setting.show_creation"));
                    rows.add(new Setting(Kind.SHOW_COUNTDOWNS, "config.setting.show_countdowns"));
                    rows.add(new Setting(Kind.TIMESTAMP_FORMAT, "config.setting.timestamp_format"));
                }
                case CURSOR_MEMORY -> rows.add(new Setting(Kind.CURSOR_TOLERANCE, "config.setting.cursor_tolerance"));
                case TELEPORT_SOUNDS -> {
                    ModConfig.Inventory inventory = ConfigManager.get().inventory;
                    rows.add(new Setting(Kind.INSTANT_SOUND_MODE, "config.setting.instant_sound_mode"));
                    if ("CUSTOM".equals(inventory.instantTransmissionSoundMode)) {
                        rows.add(new Setting(Kind.INSTANT_CUSTOM_SOUND, "config.setting.instant_custom_sound"));
                        rows.add(new Setting(Kind.INSTANT_SOUND_VOLUME, "config.setting.instant_sound_volume"));
                    }
                    rows.add(new Setting(Kind.ETHERWARP_SOUND_MODE, "config.setting.etherwarp_sound_mode"));
                    if ("CUSTOM".equals(inventory.etherwarpSoundMode)) {
                        rows.add(new Setting(Kind.ETHERWARP_CUSTOM_SOUND, "config.setting.etherwarp_custom_sound"));
                        rows.add(new Setting(Kind.ETHERWARP_SOUND_VOLUME, "config.setting.etherwarp_sound_volume"));
                    }
                }
                default -> { }
            }
            return rows;
        }
        if (feature == ConfigScreen.Feature.DRAGON_HIGHLIGHT) {
            rows.add(new Setting(Kind.DRAGON_COLOR, "config.setting.highlight_color"));
            return rows;
        }
        if (feature == ConfigScreen.Feature.CHAT_PEEK) {
            rows.add(new Setting(Kind.CHAT_PEEK_KEY, "config.setting.chat_peek_key"));
            rows.add(new Setting(Kind.CHAT_SCROLL_TARGET, "config.setting.chat_scroll_target"));
            return rows;
        }
        rows.add(new Setting(Kind.OPACITY, "config.setting.opacity"));
        rows.add(new Setting(Kind.BACKGROUND_COLOR, "config.setting.background_color"));
        rows.add(new Setting(Kind.BORDER, "config.setting.border"));
        rows.add(new Setting(Kind.BORDER_SIZE, "config.setting.border_size"));
        rows.add(new Setting(Kind.BORDER_COLOR, "config.setting.border_color"));
        rows.add(new Setting(Kind.TITLE_COLOR, "config.setting.title_color"));
        rows.add(new Setting(Kind.BOLD, "config.setting.bold"));
        rows.add(new Setting(Kind.SHADOW, "config.setting.shadow"));
        rows.add(new Setting(Kind.SCALE, "config.setting.scale"));
        if (feature == ConfigScreen.Feature.MINING_TRACKER) {
            rows.add(new Setting(Kind.COMMISSION_PROGRESS, "config.setting.commission_progress"));
            rows.add(new Setting(Kind.HOTM_SLOT, "config.setting.hotm_slot"));
        }
        if (feature == ConfigScreen.Feature.PET_HUD) {
            rows.add(new Setting(Kind.PET_ICON, "config.setting.pet_icon"));
            rows.add(new Setting(Kind.PET_LEVEL_XP, "config.setting.pet_level_xp"));
            rows.add(new Setting(Kind.PET_MAX_XP, "config.setting.pet_max_xp"));
            rows.add(new Setting(Kind.PET_OVERFLOW_LEVEL, "config.setting.pet_overflow_level"));
            rows.add(new Setting(Kind.PET_SKIN_NAME, "config.setting.pet_skin_name"));
            rows.add(new Setting(Kind.PET_ACCESSORY, "config.setting.pet_accessory"));
        }
        rows.add(new Setting(Kind.EDIT_LAYOUT, "config.layout"));
        return rows;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (listeningChord != null) {
            if (HotkeyInputs.supportedMouseButton(click.button())) {
                QCloudyAdditionClient.setMouseChord(listeningChord, click.button(), click.modifiers());
                saveVanillaOptions();
                listeningChord = null;
            }
            return true;
        }
        if (click.button() != 0) return super.mouseClicked(click, doubled);
        if (AcaUiTheme.contains(click.x(), click.y(), windowX + 10, windowY + 8, 24, 18)) {
            onClose();
            return true;
        }
        int viewportHeight = viewportHeight();
        if (!AcaUiTheme.contains(click.x(), click.y(), contentX, contentY, contentWidth, viewportHeight)) {
            return super.mouseClicked(click, doubled);
        }
        for (Hit hit : hits) {
            if (hit.contains(click.x(), click.y())) {
                if (!hit.setting.available()) return true;
                if (hit.setting.slider() && hit.sliderContains(click.x(), click.y())) {
                    draggingSlider = hit;
                    updateSlider(hit, click.x());
                    return true;
                }
                if (hit.setting.slider()) return true;
                activate(hit.setting);
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (listeningChord == null) return super.keyPressed(event);
        if (event.key() == GLFW.GLFW_KEY_ESCAPE
                || event.key() == GLFW.GLFW_KEY_BACKSPACE
                || event.key() == GLFW.GLFW_KEY_DELETE) {
            QCloudyAdditionClient.clearChord(listeningChord);
            saveVanillaOptions();
            listeningChord = null;
            return true;
        }
        if (isModifier(event.key())) return true;
        QCloudyAdditionClient.setKeyboardChord(listeningChord, event.key(), event.modifiers());
        saveVanillaOptions();
        listeningChord = null;
        return true;
    }

    private void saveVanillaOptions() {
        if (minecraft != null) minecraft.options.save();
    }

    private static boolean isModifier(int key) {
        return key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT
                || key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL
                || key == GLFW.GLFW_KEY_LEFT_ALT || key == GLFW.GLFW_KEY_RIGHT_ALT
                || key == GLFW.GLFW_KEY_LEFT_SUPER || key == GLFW.GLFW_KEY_RIGHT_SUPER;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        if (draggingSlider != null && click.button() == 0) {
            updateSlider(draggingSlider, click.x());
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (draggingSlider != null && click.button() == 0) {
            updateSlider(draggingSlider, click.x());
            draggingSlider = null;
            ConfigManager.save();
            return true;
        }
        return super.mouseReleased(click);
    }

    private void updateSlider(Hit hit, double mouseX) {
        SliderLayout slider = sliderLayout(hit.x, hit.width);
        hit.setting.setSliderFraction(Math.clamp(
                (mouseX - slider.trackX()) / Math.max(1, slider.trackWidth()), 0.0, 1.0));
    }

    static SliderLayout sliderLayout(int x, int rowWidth) {
        int safeWidth = Math.max(1, rowWidth);
        int valueWidth = Math.min(48, Math.max(1, safeWidth / 5));
        int trackEnd = x + safeWidth - valueWidth - 10;
        int maximumTrack = Math.max(1, trackEnd - (x + 20));
        int trackWidth = Math.min(150, Math.max(1, Math.min(safeWidth / 3, maximumTrack)));
        return new SliderLayout(trackEnd - trackWidth, trackWidth);
    }

    private void activate(Setting setting) {
        ModConfig config = ConfigManager.get();
        if (setting.externalSetting != null) {
            setting.externalSetting.toggleOrCycle();
            return;
        }
        if (setting.huntingOption != null) {
            HuntingOption option = setting.huntingOption;
            if (option.type == HuntingOption.Type.BOOLEAN) option.toggle(config.hunting);
            else if (option.type == HuntingOption.Type.COLOR) {
                openColor(option.intValue(config.hunting), value -> option.setInt(config.hunting, value));
            }
            ConfigManager.save();
            return;
        }
        if (setting.partyCommandOption != null) {
            if (setting.partyCommandTrigger) {
                setting.partyCommandOption.cycleFastTrigger(config.chat);
            } else if (setting.localPartyCommand) {
                setting.partyCommandOption.toggleLocal(config.chat);
            } else {
                setting.partyCommandOption.toggleFast(config.chat);
            }
            ConfigManager.save();
            return;
        }
        ModConfig.PanelStyle style = panelStyle();
        switch (setting.kind) {
            case PROVIDER -> {
                unifiedFeature.cycleProvider();
                scroll = 0;
            }
            case EXTERNAL_STATUS -> { }
            case INTEGRATION_SCAN_REFRESH -> {
                UnifiedModIntegration.ScanView view = integrationScanView();
                MinecraftClientCompat.setScreen(minecraft, new IntegrationScanConfirmScreen(this, view, () -> {
                    UnifiedModIntegration.requestConfirmedScan(true);
                    MinecraftClientCompat.setScreen(minecraft, this);
                }));
            }
            case INTEGRATION_SCAN_PROGRESS, INTEGRATION_SCAN_CURRENT, INTEGRATION_SCAN_SUMMARY,
                    INTEGRATION_SCAN_PROVIDERS, INTEGRATION_SCAN_EVENT_1,
                    INTEGRATION_SCAN_EVENT_2, INTEGRATION_SCAN_EVENT_3 -> { }
            case PARTY_FRIEND_MODE -> config.chat.partyAutoAcceptFriendMode =
                    config.chat.partyAutoAcceptFriendMode == ModConfig.PartyAcceptFriendMode.NORMAL_ONLY
                            ? ModConfig.PartyAcceptFriendMode.SPECIAL_ONLY
                            : ModConfig.PartyAcceptFriendMode.NORMAL_ONLY;
            case OPEN_PARTY_WHITELIST -> MinecraftClientCompat.setScreen(minecraft,
                    new PartyWhitelistScreen(this));
            case OPEN_SHARD_GUIDE -> QCloudyAdditionClient.openShardFusionGuide(minecraft, this, "");
            case OPEN_SHARD_PLANNER -> MinecraftClientCompat.setScreen(minecraft, new ShardPlanningScreen(this,
                    ConfigManager.get().inventory.shardPlannerTarget));
            case SHARD_GUIDE_KEY -> listeningChord = QCloudyAdditionClient.ChordAction.OPEN_SHARD_FUSION;
            case YIELD_FIRMAMENT -> config.inventory.yieldToFirmament = !config.inventory.yieldToFirmament;
            case OPEN_CONFIG_KEY -> listeningChord = QCloudyAdditionClient.ChordAction.OPEN_CONFIG;
            case SHOW_CREATION -> config.inventory.showCreationTimestamp = !config.inventory.showCreationTimestamp;
            case SHOW_COUNTDOWNS -> config.inventory.showCountdownCompletion = !config.inventory.showCountdownCompletion;
            case TIMESTAMP_FORMAT -> config.inventory.timestampFormat = next(config.inventory.timestampFormat,
                    "LOCAL_24H", "LOCAL_12H", "ISO", "RFC");
            case CURSOR_TOLERANCE -> config.inventory.cursorToleranceMs = config.inventory.cursorToleranceMs >= 5000
                    ? 50 : Math.min(5000, config.inventory.cursorToleranceMs + 50);
            case INSTANT_SOUND_MODE -> config.inventory.instantTransmissionSoundMode = next(
                    config.inventory.instantTransmissionSoundMode, "VANILLA", "CUSTOM");
            case INSTANT_CUSTOM_SOUND -> config.inventory.instantTransmissionCustomSound = nextTeleportSound(
                    config.inventory.instantTransmissionCustomSound);
            case INSTANT_SOUND_VOLUME -> config.inventory.instantTransmissionSoundVolume = nextPercent(
                    config.inventory.instantTransmissionSoundVolume, 0, 100);
            case ETHERWARP_SOUND_MODE -> config.inventory.etherwarpSoundMode = next(
                    config.inventory.etherwarpSoundMode, "VANILLA", "CUSTOM");
            case ETHERWARP_CUSTOM_SOUND -> config.inventory.etherwarpCustomSound = nextTeleportSound(
                    config.inventory.etherwarpCustomSound);
            case ETHERWARP_SOUND_VOLUME -> config.inventory.etherwarpSoundVolume = nextPercent(
                    config.inventory.etherwarpSoundVolume, 0, 100);
            case FISHING_BITE_VOLUME -> config.fishing.biteAlertVolume = nextPercent(
                    config.fishing.biteAlertVolume, 0, 100);
            case DEPLOYABLE_POWER_ORB_ALERTS -> config.combat.deployablePowerOrbAlerts =
                    !config.combat.deployablePowerOrbAlerts;
            case DEPLOYABLE_FLARE_ALERTS -> config.combat.deployableFlareAlerts =
                    !config.combat.deployableFlareAlerts;
            case DEPLOYABLE_EXPIRY_CENTER_TEXT -> config.combat.deployableExpiryCenterText =
                    !config.combat.deployableExpiryCenterText;
            case DEPLOYABLE_EXPIRY_SOUND -> config.combat.deployableExpiryAudio.sound =
                    !config.combat.deployableExpiryAudio.sound;
            case DEPLOYABLE_EXPIRY_VOLUME -> config.combat.deployableExpiryAudio.volume = nextPercent(
                    config.combat.deployableExpiryAudio.volume, 0, 100);
            case OPEN_CENTURY_CAKES -> MinecraftClientCompat.setScreen(minecraft,
                    new CenturyCakeEffectsScreen(this));
            case CENTURY_CAKE_SOUND -> config.centuryCakes.expiryAudio.sound =
                    !config.centuryCakes.expiryAudio.sound;
            case CENTURY_CAKE_VOLUME -> config.centuryCakes.expiryAudio.volume = nextPercent(
                    config.centuryCakes.expiryAudio.volume, 0, 100);
            case DRAGON_COLOR -> openColor(config.combat.enderDragonHighlightColor,
                    color -> config.combat.enderDragonHighlightColor = color);
            case CHAT_PEEK_KEY -> listeningChord = QCloudyAdditionClient.ChordAction.PEEK_CHAT;
            case CHAT_SCROLL_TARGET -> config.chat.peekScrollTarget = next(
                    config.chat.peekScrollTarget, "CHAT", "HOTBAR");
            case OPACITY -> style.backgroundOpacity = style.backgroundOpacity >= 255
                    ? 0 : Math.min(255, (style.backgroundOpacity / 32 + 1) * 32);
            case BACKGROUND_COLOR -> openBackgroundColor(style);
            case BORDER -> style.border = !style.border;
            case BORDER_SIZE -> style.borderThickness = style.borderThickness % 4 + 1;
            case BORDER_COLOR -> openColor(style.borderColor, color -> style.borderColor = color);
            case TITLE_COLOR -> openColor(style.titleColor, color -> style.titleColor = color);
            case BOLD -> style.boldText = !style.boldText;
            case SHADOW -> style.textShadow = !style.textShadow;
            case SCALE -> {
                style.scale = Math.round((style.scale + 0.1f) * 10.0f) / 10.0f;
                if (style.scale > 2.0f) style.scale = 0.5f;
            }
            case COMMISSION_PROGRESS -> config.mining.commissionProgressMode = next(
                    config.mining.commissionProgressMode, "PERCENT", "NUMERIC");
            case HOTM_SLOT -> config.mining.showHotmSlot = !config.mining.showHotmSlot;
            case PET_ICON -> config.pets.showPetIcon = !config.pets.showPetIcon;
            case PET_LEVEL_XP -> config.pets.showLevelProgress = !config.pets.showLevelProgress;
            case PET_MAX_XP -> config.pets.showMaxProgress = !config.pets.showMaxProgress;
            case PET_OVERFLOW_LEVEL -> config.pets.showOverflowLevel = !config.pets.showOverflowLevel;
            case PET_SKIN_NAME -> config.pets.showSkinName = !config.pets.showSkinName;
            case PET_ACCESSORY -> config.pets.petAccessoryDisplay = next(config.pets.petAccessoryDisplay,
                    "ICON_AND_NAME", "ICON_ONLY", "NAME_ONLY");
            case EDIT_LAYOUT -> MinecraftClientCompat.setScreen(minecraft, new HudLayoutScreen(this));
        }
        ConfigManager.save();
    }

    private void openColor(int initial, IntConsumer setter) {
        MinecraftClientCompat.setScreen(minecraft, new ColorPickerScreen(this, initial, value -> {
            setter.accept(value);
            ConfigManager.save();
        }));
    }

    private void openBackgroundColor(ModConfig.PanelStyle style) {
        int restoredOpacity = style.backgroundOpacity > 0 ? style.backgroundOpacity : 120;
        MinecraftClientCompat.setScreen(minecraft, new ColorPickerScreen(this, style.backgroundColor, value -> {
            style.backgroundColor = value;
            ConfigManager.save();
        }, true, style.backgroundOpacity == 0, () -> {
            style.backgroundOpacity = 0;
            ConfigManager.save();
        }, () -> {
            style.backgroundOpacity = restoredOpacity;
            ConfigManager.save();
        }));
    }

    private static String next(String current, String... values) {
        for (int index = 0; index < values.length; index++) {
            if (values[index].equals(current)) return values[(index + 1) % values.length];
        }
        return values[0];
    }

    private static String nextTeleportSound(String current) {
        return next(current, "CHORUS", "ENDERMAN", "AMETHYST", "ORB", "PORTAL", "SHULKER");
    }

    private static int nextPercent(int current, int minimum, int maximum) {
        return current >= maximum ? minimum : Math.min(maximum, current + 10);
    }

    private ModConfig.PanelStyle panelStyle() {
        if (feature == null) return ConfigManager.get().hudStyle.map;
        ModConfig.HudType type = feature.hudType();
        return type == null ? ConfigManager.get().hudStyle.map : ConfigManager.get().hudStyle.style(type);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (AcaUiTheme.contains(mouseX, mouseY, contentX, contentY, contentWidth, viewportHeight())) {
            scroll = Math.clamp(scroll - (int) Math.round(vertical * 22), 0, maxScroll);
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

    private int viewportHeight() {
        return Math.max(1, windowHeight - (contentY - windowY) - 12);
    }

    private enum PartyCommandOption {
        WARP("config.setting.fast_party.warp", "config.setting.party_command.warp"),
        ALL_INVITE("config.setting.fast_party.all_invite", "config.setting.party_command.all_invite"),
        TRANSFER("config.setting.fast_party.transfer", "config.setting.party_command.transfer"),
        KICK("config.setting.fast_party.kick", "config.setting.party_command.kick"),
        COORDINATES("config.setting.fast_party.coordinates", "config.setting.party_command.coordinates"),
        PROMOTE("config.setting.fast_party.promote", "config.setting.party_command.promote"),
        STREAM("config.setting.fast_party.stream", "config.setting.party_command.stream"),
        DUNGEON("config.setting.fast_party.dungeon", "config.setting.party_command.dungeon"),
        KUUDRA("config.setting.fast_party.kuudra", "config.setting.party_command.kuudra");

        private final String fastLabelKey;
        private final String localLabelKey;

        PartyCommandOption(String fastLabelKey, String localLabelKey) {
            this.fastLabelKey = fastLabelKey;
            this.localLabelKey = localLabelKey;
        }

        boolean fastEnabled(ModConfig.Chat chat) {
            return switch (this) {
                case WARP -> chat.fastPartyWarp;
                case ALL_INVITE -> chat.fastPartyAllInvite;
                case TRANSFER -> chat.fastPartyTransfer;
                case KICK -> chat.fastPartyKick;
                case COORDINATES -> chat.fastPartyCoordinates;
                case PROMOTE -> chat.fastPartyPromote;
                case STREAM -> chat.fastPartyStream;
                case DUNGEON -> chat.fastPartyDungeon;
                case KUUDRA -> chat.fastPartyKuudra;
            };
        }

        void toggleFast(ModConfig.Chat chat) {
            switch (this) {
                case WARP -> chat.fastPartyWarp = !chat.fastPartyWarp;
                case ALL_INVITE -> chat.fastPartyAllInvite = !chat.fastPartyAllInvite;
                case TRANSFER -> chat.fastPartyTransfer = !chat.fastPartyTransfer;
                case KICK -> chat.fastPartyKick = !chat.fastPartyKick;
                case COORDINATES -> chat.fastPartyCoordinates = !chat.fastPartyCoordinates;
                case PROMOTE -> chat.fastPartyPromote = !chat.fastPartyPromote;
                case STREAM -> chat.fastPartyStream = !chat.fastPartyStream;
                case DUNGEON -> chat.fastPartyDungeon = !chat.fastPartyDungeon;
                case KUUDRA -> chat.fastPartyKuudra = !chat.fastPartyKuudra;
            }
        }

        ModConfig.PartyCommandTrigger fastTrigger(ModConfig.Chat chat) {
            return switch (this) {
                case WARP -> chat.fastPartyWarpTrigger;
                case ALL_INVITE -> chat.fastPartyAllInviteTrigger;
                case TRANSFER -> chat.fastPartyTransferTrigger;
                case KICK -> chat.fastPartyKickTrigger;
                case COORDINATES -> chat.fastPartyCoordinatesTrigger;
                case PROMOTE -> chat.fastPartyPromoteTrigger;
                case STREAM -> chat.fastPartyStreamTrigger;
                case DUNGEON -> chat.fastPartyDungeonTrigger;
                case KUUDRA -> chat.fastPartyKuudraTrigger;
            };
        }

        void cycleFastTrigger(ModConfig.Chat chat) {
            ModConfig.PartyCommandTrigger current = fastTrigger(chat);
            ModConfig.PartyCommandTrigger next = switch (current == null
                    ? ModConfig.PartyCommandTrigger.EVERYONE : current) {
                case EVERYONE -> ModConfig.PartyCommandTrigger.SELF_ONLY;
                case SELF_ONLY -> ModConfig.PartyCommandTrigger.OTHERS_ONLY;
                case OTHERS_ONLY -> ModConfig.PartyCommandTrigger.EVERYONE;
            };
            switch (this) {
                case WARP -> chat.fastPartyWarpTrigger = next;
                case ALL_INVITE -> chat.fastPartyAllInviteTrigger = next;
                case TRANSFER -> chat.fastPartyTransferTrigger = next;
                case KICK -> chat.fastPartyKickTrigger = next;
                case COORDINATES -> chat.fastPartyCoordinatesTrigger = next;
                case PROMOTE -> chat.fastPartyPromoteTrigger = next;
                case STREAM -> chat.fastPartyStreamTrigger = next;
                case DUNGEON -> chat.fastPartyDungeonTrigger = next;
                case KUUDRA -> chat.fastPartyKuudraTrigger = next;
            }
        }

        boolean localEnabled(ModConfig.Chat chat) {
            return switch (this) {
                case WARP -> chat.partyCommandWarp;
                case ALL_INVITE -> chat.partyCommandAllInvite;
                case TRANSFER -> chat.partyCommandTransfer;
                case KICK -> chat.partyCommandKick;
                case COORDINATES -> chat.partyCommandCoordinates;
                case PROMOTE -> chat.partyCommandPromote;
                case STREAM -> chat.partyCommandStream;
                case DUNGEON -> chat.partyCommandDungeon;
                case KUUDRA -> chat.partyCommandKuudra;
            };
        }

        void toggleLocal(ModConfig.Chat chat) {
            switch (this) {
                case WARP -> chat.partyCommandWarp = !chat.partyCommandWarp;
                case ALL_INVITE -> chat.partyCommandAllInvite = !chat.partyCommandAllInvite;
                case TRANSFER -> chat.partyCommandTransfer = !chat.partyCommandTransfer;
                case KICK -> chat.partyCommandKick = !chat.partyCommandKick;
                case COORDINATES -> chat.partyCommandCoordinates = !chat.partyCommandCoordinates;
                case PROMOTE -> chat.partyCommandPromote = !chat.partyCommandPromote;
                case STREAM -> chat.partyCommandStream = !chat.partyCommandStream;
                case DUNGEON -> chat.partyCommandDungeon = !chat.partyCommandDungeon;
                case KUUDRA -> chat.partyCommandKuudra = !chat.partyCommandKuudra;
            }
        }
    }

    private enum Kind {
        PROVIDER, EXTERNAL_STATUS,
        INTEGRATION_SCAN_PROGRESS, INTEGRATION_SCAN_CURRENT, INTEGRATION_SCAN_SUMMARY,
        INTEGRATION_SCAN_PROVIDERS, INTEGRATION_SCAN_EVENT_1, INTEGRATION_SCAN_EVENT_2,
        INTEGRATION_SCAN_EVENT_3, INTEGRATION_SCAN_REFRESH,
        DRAGON_COLOR, OPACITY, BACKGROUND_COLOR, BORDER, BORDER_SIZE, BORDER_COLOR,
        TITLE_COLOR, BOLD, SHADOW, SCALE, PET_ICON, PET_LEVEL_XP, PET_MAX_XP, PET_OVERFLOW_LEVEL,
        PET_SKIN_NAME, PET_ACCESSORY, COMMISSION_PROGRESS, HOTM_SLOT, EDIT_LAYOUT,
        OPEN_SHARD_GUIDE, OPEN_SHARD_PLANNER, SHARD_GUIDE_KEY,
        YIELD_FIRMAMENT, OPEN_CONFIG_KEY, SHOW_CREATION, SHOW_COUNTDOWNS,
        TIMESTAMP_FORMAT, CURSOR_TOLERANCE,
        INSTANT_SOUND_MODE, INSTANT_CUSTOM_SOUND, INSTANT_SOUND_VOLUME,
        ETHERWARP_SOUND_MODE, ETHERWARP_CUSTOM_SOUND, ETHERWARP_SOUND_VOLUME,
        FISHING_BITE_VOLUME, DEPLOYABLE_POWER_ORB_ALERTS, DEPLOYABLE_FLARE_ALERTS,
        DEPLOYABLE_EXPIRY_CENTER_TEXT, DEPLOYABLE_EXPIRY_SOUND, DEPLOYABLE_EXPIRY_VOLUME,
        OPEN_CENTURY_CAKES, CENTURY_CAKE_SOUND, CENTURY_CAKE_VOLUME,
        CHAT_PEEK_KEY, CHAT_SCROLL_TARGET,
        PARTY_FRIEND_MODE, OPEN_PARTY_WHITELIST
    }

    static boolean shardGuideEntryEnabled(ModConfig config) {
        return config.inventory.shardFusionHelper;
    }

    static boolean partyCommandChildSettingsAvailable(ModConfig config, boolean localPartyCommand) {
        return localPartyCommand ? config.chat.partyCommands : config.chat.fastPartyCommands;
    }

    private final class Setting {
        private final Kind kind;
        private final String labelKey;
        private final HuntingOption huntingOption;
        private final UnifiedModIntegration.NativeSetting externalSetting;
        private final PartyCommandOption partyCommandOption;
        private final boolean partyCommandTrigger;
        private final boolean localPartyCommand;

        private Setting(Kind kind, String labelKey) {
            this.kind = kind;
            this.labelKey = labelKey;
            this.huntingOption = null;
            this.externalSetting = null;
            this.partyCommandOption = null;
            this.partyCommandTrigger = false;
            this.localPartyCommand = false;
        }

        private Setting(HuntingOption huntingOption) {
            this.kind = null;
            this.labelKey = huntingOption.labelKey;
            this.huntingOption = huntingOption;
            this.externalSetting = null;
            this.partyCommandOption = null;
            this.partyCommandTrigger = false;
            this.localPartyCommand = false;
        }

        private Setting(UnifiedModIntegration.NativeSetting externalSetting) {
            this.kind = null;
            this.labelKey = "";
            this.huntingOption = null;
            this.externalSetting = externalSetting;
            this.partyCommandOption = null;
            this.partyCommandTrigger = false;
            this.localPartyCommand = false;
        }

        private Setting(PartyCommandOption partyCommandOption, boolean partyCommandTrigger,
                        boolean localPartyCommand) {
            this.kind = null;
            this.labelKey = "";
            this.huntingOption = null;
            this.externalSetting = null;
            this.partyCommandOption = partyCommandOption;
            this.partyCommandTrigger = partyCommandTrigger;
            this.localPartyCommand = localPartyCommand;
        }

        String label() {
            if (externalSetting != null) return externalSetting.label;
            if (partyCommandOption != null) {
                String option = ModText.get(localPartyCommand
                        ? partyCommandOption.localLabelKey : partyCommandOption.fastLabelKey);
                return partyCommandTrigger ? ModText.get("config.setting.party_trigger_scope", option) : option;
            }
            return ModText.get(labelKey);
        }

        String value() {
            ModConfig config = ConfigManager.get();
            if (externalSetting != null) return externalSetting.displayValue();
            if (huntingOption != null) {
                return switch (huntingOption.type) {
                    case BOOLEAN -> onOff(huntingOption.booleanValue(config.hunting));
                    case SLIDER -> huntingOption.intValue(config.hunting) + huntingOption.suffix;
                    case COLOR -> String.format("#%06X", huntingOption.intValue(config.hunting) & 0xFFFFFF);
                };
            }
            if (partyCommandOption != null) {
                if (partyCommandTrigger) {
                    ModConfig.PartyCommandTrigger trigger = partyCommandOption.fastTrigger(config.chat);
                    return ModText.get("config.value.party_trigger."
                            + (trigger == null ? ModConfig.PartyCommandTrigger.EVERYONE : trigger)
                            .name().toLowerCase(java.util.Locale.ROOT));
                }
                return onOff(localPartyCommand
                        ? partyCommandOption.localEnabled(config.chat)
                        : partyCommandOption.fastEnabled(config.chat));
            }
            ModConfig.PanelStyle style = panelStyle();
            return switch (kind) {
                case PROVIDER -> unifiedFeature.selectedProvider().displayName;
                case EXTERNAL_STATUS -> ModText.get("config.integration.unavailable");
                case INTEGRATION_SCAN_PROGRESS -> scanProgressValue();
                case INTEGRATION_SCAN_CURRENT -> scanCurrentValue();
                case INTEGRATION_SCAN_SUMMARY -> scanSummaryValue();
                case INTEGRATION_SCAN_PROVIDERS -> scanProvidersValue();
                case INTEGRATION_SCAN_EVENT_1 -> scanEventValue(0);
                case INTEGRATION_SCAN_EVENT_2 -> scanEventValue(1);
                case INTEGRATION_SCAN_EVENT_3 -> scanEventValue(2);
                case INTEGRATION_SCAN_REFRESH -> ModText.get("config.integration.scan.refresh_action");
                case PARTY_FRIEND_MODE -> ModText.get(config.chat.partyAutoAcceptFriendMode
                        == ModConfig.PartyAcceptFriendMode.SPECIAL_ONLY
                        ? "config.value.party_mode.special_only"
                        : "config.value.party_mode.normal_only");
                case OPEN_PARTY_WHITELIST -> ModText.get("config.party.whitelist.count",
                        config.chat.partyAutoAcceptWhitelist.size(),
                        ModConfig.Chat.PARTY_AUTO_ACCEPT_WHITELIST_LIMIT);
                case OPEN_SHARD_GUIDE, OPEN_SHARD_PLANNER ->
                        ModText.get(available() ? "config.open" : "config.disabled");
                case YIELD_FIRMAMENT -> onOff(config.inventory.yieldToFirmament);
                case SHARD_GUIDE_KEY, OPEN_CONFIG_KEY, CHAT_PEEK_KEY -> {
                    QCloudyAdditionClient.ChordAction action = chordAction();
                    yield action == listeningChord ? ModText.get("config.key.waiting")
                            : QCloudyAdditionClient.chordName(action);
                }
                case SHOW_CREATION -> onOff(config.inventory.showCreationTimestamp);
                case SHOW_COUNTDOWNS -> onOff(config.inventory.showCountdownCompletion);
                case TIMESTAMP_FORMAT -> config.inventory.timestampFormat.replace('_', ' ');
                case CURSOR_TOLERANCE -> config.inventory.cursorToleranceMs + " ms";
                case INSTANT_SOUND_MODE -> ModText.get("config.value."
                        + config.inventory.instantTransmissionSoundMode.toLowerCase());
                case INSTANT_CUSTOM_SOUND -> ModText.get("config.value.sound."
                        + config.inventory.instantTransmissionCustomSound.toLowerCase());
                case INSTANT_SOUND_VOLUME -> config.inventory.instantTransmissionSoundVolume + "%";
                case ETHERWARP_SOUND_MODE -> ModText.get("config.value."
                        + config.inventory.etherwarpSoundMode.toLowerCase());
                case ETHERWARP_CUSTOM_SOUND -> ModText.get("config.value.sound."
                        + config.inventory.etherwarpCustomSound.toLowerCase());
                case ETHERWARP_SOUND_VOLUME -> config.inventory.etherwarpSoundVolume + "%";
                case FISHING_BITE_VOLUME -> config.fishing.biteAlertVolume + "%";
                case DEPLOYABLE_POWER_ORB_ALERTS -> onOff(config.combat.deployablePowerOrbAlerts);
                case DEPLOYABLE_FLARE_ALERTS -> onOff(config.combat.deployableFlareAlerts);
                case DEPLOYABLE_EXPIRY_CENTER_TEXT -> onOff(config.combat.deployableExpiryCenterText);
                case DEPLOYABLE_EXPIRY_SOUND -> onOff(config.combat.deployableExpiryAudio.sound);
                case DEPLOYABLE_EXPIRY_VOLUME -> config.combat.deployableExpiryAudio.volume + "%";
                case OPEN_CENTURY_CAKES -> ModText.get("config.open");
                case CENTURY_CAKE_SOUND -> onOff(config.centuryCakes.expiryAudio.sound);
                case CENTURY_CAKE_VOLUME -> config.centuryCakes.expiryAudio.volume + "%";
                case CHAT_SCROLL_TARGET -> ModText.get("config.value."
                        + config.chat.peekScrollTarget.toLowerCase());
                case BACKGROUND_COLOR -> style.backgroundOpacity == 0
                        ? ModText.get("config.transparent") : String.format("#%06X", colorValue());
                case DRAGON_COLOR, BORDER_COLOR, TITLE_COLOR -> String.format("#%06X", colorValue());
                case OPACITY -> Math.round(style.backgroundOpacity / 255.0f * 100.0f) + "%";
                case BORDER -> onOff(style.border);
                case BORDER_SIZE -> style.borderThickness + " px";
                case BOLD -> onOff(style.boldText);
                case SHADOW -> onOff(style.textShadow);
                case SCALE -> Math.round(style.scale * 100) + "%";
                case COMMISSION_PROGRESS -> ModText.get("config.value."
                        + config.mining.commissionProgressMode.toLowerCase());
                case HOTM_SLOT -> onOff(config.mining.showHotmSlot);
                case PET_ICON -> onOff(config.pets.showPetIcon);
                case PET_LEVEL_XP -> onOff(config.pets.showLevelProgress);
                case PET_MAX_XP -> onOff(config.pets.showMaxProgress);
                case PET_OVERFLOW_LEVEL -> onOff(config.pets.showOverflowLevel);
                case PET_SKIN_NAME -> onOff(config.pets.showSkinName);
                case PET_ACCESSORY -> ModText.get("config.value." + config.pets.petAccessoryDisplay.toLowerCase());
                case EDIT_LAYOUT -> ModText.get("config.open");
            };
        }

        private UnifiedModIntegration.ScanStatus scanStatus() {
            return UnifiedModIntegration.scanStatus(integrationScanView());
        }

        private String scanProgressValue() {
            UnifiedModIntegration.ScanStatus status = scanStatus();
            String state = ModText.get("config.integration.scan.state."
                    + status.state().name().toLowerCase(java.util.Locale.ROOT));
            return state + " · " + status.percent() + "%";
        }

        private String scanCurrentValue() {
            UnifiedModIntegration.ScanStatus status = scanStatus();
            if (!status.currentProvider().isBlank() || !status.currentItem().isBlank()) {
                if (status.currentProvider().isBlank()) return status.currentItem();
                if (status.currentItem().isBlank()) return status.currentProvider();
                return status.currentProvider() + " · " + status.currentItem();
            }
            return ModText.get("config.integration.scan.phase."
                    + status.phase().name().toLowerCase(java.util.Locale.ROOT));
        }

        private String scanSummaryValue() {
            UnifiedModIntegration.ScanStatus status = scanStatus();
            String key = integrationScanView() == UnifiedModIntegration.ScanView.HUD
                    ? "config.integration.scan.hud_count" : "config.integration.scan.settings_count";
            return ModText.get(key, status.managedCount());
        }

        private String scanProvidersValue() {
            List<String> names = new ArrayList<>();
            for (UnifiedModIntegration.ProviderScan provider : scanStatus().providers()) {
                if (provider.discoveredFeatures() <= 0 && provider.hudManaged() <= 0) continue;
                names.add(provider.provider().displayName
                        + (provider.partial() ? " · " + ModText.get("config.integration.scan.partial") : " ✓"));
            }
            return names.isEmpty() ? ModText.get("config.integration.scan.none") : String.join("  |  ", names);
        }

        private String scanEventValue(int newestOffset) {
            List<String> events = scanStatus().recentItems();
            int index = events.size() - 1 - newestOffset;
            return index >= 0 && index < events.size() ? events.get(index)
                    : ModText.get("config.integration.scan.none");
        }

        boolean available() {
            if (externalSetting != null) return externalSetting.editable() && externalSetting.value() != null;
            if (partyCommandOption != null) {
                return partyCommandChildSettingsAvailable(ConfigManager.get(), localPartyCommand);
            }
            if (kind == Kind.EXTERNAL_STATUS) return false;
            if (kind == Kind.INTEGRATION_SCAN_REFRESH) {
                return feature != null && feature.enabled(ConfigManager.get())
                        && !UnifiedModIntegration.scanRunning();
            }
            return (kind != Kind.OPEN_SHARD_GUIDE && kind != Kind.OPEN_SHARD_PLANNER)
                    || shardGuideEntryEnabled(ConfigManager.get());
        }

        boolean color() {
            if (externalSetting != null) return false;
            if (huntingOption != null) return huntingOption.type == HuntingOption.Type.COLOR;
            if (partyCommandOption != null) return false;
            return kind == Kind.DRAGON_COLOR || kind == Kind.BACKGROUND_COLOR
                    || kind == Kind.BORDER_COLOR || kind == Kind.TITLE_COLOR;
        }

        QCloudyAdditionClient.ChordAction chordAction() {
            if (externalSetting != null) return null;
            if (huntingOption != null) return null;
            if (partyCommandOption != null) return null;
            return switch (kind) {
                case SHARD_GUIDE_KEY -> QCloudyAdditionClient.ChordAction.OPEN_SHARD_FUSION;
                case OPEN_CONFIG_KEY -> QCloudyAdditionClient.ChordAction.OPEN_CONFIG;
                case CHAT_PEEK_KEY -> QCloudyAdditionClient.ChordAction.PEEK_CHAT;
                default -> null;
            };
        }

        int colorValue() {
            if (externalSetting != null) return 0xFFFFFF;
            ModConfig config = ConfigManager.get();
            if (huntingOption != null) return huntingOption.intValue(config.hunting) & 0xFFFFFF;
            if (partyCommandOption != null) return 0xFFFFFF;
            ModConfig.PanelStyle style = panelStyle();
            return switch (kind) {
                case DRAGON_COLOR -> config.combat.enderDragonHighlightColor;
                case BACKGROUND_COLOR -> style.backgroundColor;
                case BORDER_COLOR -> style.borderColor;
                case TITLE_COLOR -> style.titleColor;
                default -> 0xFFFFFF;
            } & 0xFFFFFF;
        }

        boolean slider() {
            if (externalSetting != null) {
                return (externalSetting.kind == UnifiedModIntegration.ValueKind.INTEGER
                        || externalSetting.kind == UnifiedModIntegration.ValueKind.DECIMAL)
                        && externalSetting.minimum != null && externalSetting.maximum != null;
            }
            if (huntingOption != null) return huntingOption.type == HuntingOption.Type.SLIDER;
            if (partyCommandOption != null) return false;
            return switch (kind) {
                case OPACITY, SCALE, CURSOR_TOLERANCE, INSTANT_SOUND_VOLUME,
                        ETHERWARP_SOUND_VOLUME, FISHING_BITE_VOLUME, DEPLOYABLE_EXPIRY_VOLUME,
                        CENTURY_CAKE_VOLUME -> true;
                default -> false;
            };
        }

        double sliderFraction() {
            if (externalSetting != null) return externalSetting.sliderFraction();
            ModConfig config = ConfigManager.get();
            if (huntingOption != null) {
                return fraction(huntingOption.intValue(config.hunting), huntingOption.minimum, huntingOption.maximum);
            }
            if (partyCommandOption != null) return 0.0;
            ModConfig.PanelStyle style = panelStyle();
            return switch (kind) {
                case OPACITY -> style.backgroundOpacity / 255.0;
                case SCALE -> (style.scale - 0.5) / 1.5;
                case CURSOR_TOLERANCE -> fraction(config.inventory.cursorToleranceMs, 50, 5000);
                case INSTANT_SOUND_VOLUME -> fraction(config.inventory.instantTransmissionSoundVolume, 0, 100);
                case ETHERWARP_SOUND_VOLUME -> fraction(config.inventory.etherwarpSoundVolume, 0, 100);
                case FISHING_BITE_VOLUME -> fraction(config.fishing.biteAlertVolume, 0, 100);
                case DEPLOYABLE_EXPIRY_VOLUME -> fraction(config.combat.deployableExpiryAudio.volume, 0, 100);
                case CENTURY_CAKE_VOLUME -> fraction(config.centuryCakes.expiryAudio.volume, 0, 100);
                default -> 0.0;
            };
        }

        void setSliderFraction(double fraction) {
            if (externalSetting != null) {
                externalSetting.setSliderFraction(fraction);
                return;
            }
            ModConfig config = ConfigManager.get();
            if (huntingOption != null) {
                huntingOption.setInt(config.hunting,
                        ranged(Math.clamp(fraction, 0.0, 1.0), huntingOption.minimum, huntingOption.maximum));
                return;
            }
            if (partyCommandOption != null) return;
            ModConfig.PanelStyle style = panelStyle();
            double clamped = Math.clamp(fraction, 0.0, 1.0);
            switch (kind) {
                case OPACITY -> style.backgroundOpacity = ranged(clamped, 0, 255);
                case SCALE -> style.scale = Math.round((0.5f + (float) clamped * 1.5f) * 100.0f) / 100.0f;
                case CURSOR_TOLERANCE -> config.inventory.cursorToleranceMs =
                        Math.round(ranged(clamped, 50, 5000) / 10.0f) * 10;
                case INSTANT_SOUND_VOLUME -> config.inventory.instantTransmissionSoundVolume = ranged(clamped, 0, 100);
                case ETHERWARP_SOUND_VOLUME -> config.inventory.etherwarpSoundVolume = ranged(clamped, 0, 100);
                case FISHING_BITE_VOLUME -> config.fishing.biteAlertVolume = ranged(clamped, 0, 100);
                case DEPLOYABLE_EXPIRY_VOLUME -> config.combat.deployableExpiryAudio.volume = ranged(clamped, 0, 100);
                case CENTURY_CAKE_VOLUME -> config.centuryCakes.expiryAudio.volume = ranged(clamped, 0, 100);
                default -> { }
            }
        }

        private double fraction(int value, int minimum, int maximum) {
            return (value - minimum) / (double) (maximum - minimum);
        }

        private int ranged(double fraction, int minimum, int maximum) {
            return minimum + (int) Math.round(fraction * (maximum - minimum));
        }

        private String onOff(boolean enabled) {
            return ModText.get(enabled ? "config.enabled" : "config.disabled");
        }
    }

    private record Hit(Setting setting, int x, int y, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return AcaUiTheme.contains(mouseX, mouseY, x, y, width, height);
        }

        boolean sliderContains(double mouseX, double mouseY) {
            SliderLayout slider = sliderLayout(x, width);
            return AcaUiTheme.contains(mouseX, mouseY, slider.trackX() - 5, y + 4,
                    slider.trackWidth() + 10, height - 8);
        }
    }

    record SliderLayout(int trackX, int trackWidth) {
    }
}

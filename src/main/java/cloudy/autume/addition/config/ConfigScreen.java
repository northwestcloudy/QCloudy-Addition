package cloudy.autume.addition.config;

import cloudy.autume.addition.compat.MinecraftClientCompat;
import cloudy.autume.addition.i18n.ModText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ConfigScreen extends Screen {
    private static final Identifier ICON = Identifier.fromNamespaceAndPath(
            "qcloudy_addition", "icon.png");
    private static final int CARD_HEIGHT = 72;
    private static final int CARD_GAP = 8;
    private static final int GROUP_HEADER_HEIGHT = 22;
    private static final int TOP_CONTROL_Y_OFFSET = 37;
    private static final int TOP_CONTROL_HEIGHT = 18;
    private static final int SEARCH_HORIZONTAL_PADDING = 5;
    private static final int SEARCH_NAVIGATION_GAP = 8;
    static final int CONTENT_SCROLLBAR_GUTTER = VerticalScrollbar.WIDTH + 2;
    private static final int REPORT_ACCENT = 0xFFF2C14E;

    private final @Nullable Screen parent;
    private final long openedAt = System.nanoTime();
    private final List<Hit<UnifiedModIntegration.UnifiedFeature>> featureHits = new ArrayList<>();
    private final List<Hit<String>> groupHits = new ArrayList<>();
    private final VerticalScrollbar contentScrollbar = new VerticalScrollbar();
    private @Nullable Hit<Boolean> compatibilityReportHit;
    private final GroupExpansionState groupExpansionState = new GroupExpansionState();
    private Category category = Category.GENERAL;
    private EditBox searchBox;
    private String query = "";
    private int scroll;
    private int maximumScroll;
    private int sidebarScroll;
    private int sidebarMaximumScroll;
    private int windowX;
    private int windowY;
    private int windowWidth;
    private int windowHeight;
    private int sidebarWidth;
    private int contentX;
    private int contentY;
    private int contentWidth;
    private int contentHeight;
    private int navigationX;
    private int navigationY;
    private int navigationTabWidth;
    private int searchFrameX;
    private int searchFrameY;
    private int searchFrameWidth;

    public ConfigScreen(@Nullable Screen parent) {
        this(parent, null);
    }

    public ConfigScreen(@Nullable Screen parent, @Nullable HudFocus focus) {
        super(Component.literal("QCloudy_Addition"));
        this.parent = parent;
        if (focus != null) {
            category = switch (focus) {
                case MAP -> Category.MAPS;
                case MINING -> Category.MINING;
                case FORAGING -> Category.FORAGING;
                case HUNTING, SAFARI -> Category.HUNTING;
                case PET -> Category.ITEMS_AND_MENUS;
            };
        }
    }

    @Override
    protected void init() {
        contentScrollbar.cancelDrag();
        layoutWindow();
        int textY = searchFrameY + Math.max(0, (TOP_CONTROL_HEIGHT - font.lineHeight) / 2);
        searchBox = new EditBox(font, searchFrameX + SEARCH_HORIZONTAL_PADDING, textY,
                Math.max(1, searchFrameWidth - SEARCH_HORIZONTAL_PADDING * 2), font.lineHeight,
                ModText.component("config.search"));
        searchBox.setBordered(false);
        searchBox.setTextShadow(false);
        searchBox.setMaxLength(64);
        searchBox.setHint(ModText.component("config.search"));
        searchBox.setTextColor(AcaUiTheme.TEXT);
        searchBox.setValue(query);
        searchBox.setResponder(value -> {
            query = value;
            scroll = 0;
            contentScrollbar.cancelDrag();
        });
        addRenderableWidget(searchBox);
    }

    private void layoutWindow() {
        windowWidth = Math.max(1, Math.min(640, width - Math.min(20, Math.max(0, width - 1))));
        windowHeight = Math.max(1, Math.min(380, height - Math.min(20, Math.max(0, height - 1))));
        windowX = (width - windowWidth) / 2;
        windowY = (height - windowHeight) / 2;
        int contentGap = Math.min(10, Math.max(1, windowWidth / 20));
        int requestedSidebar = windowWidth >= 460 ? 112 : Math.max(54, windowWidth / 4);
        sidebarWidth = Math.max(1, Math.min(Math.max(1, windowWidth - contentGap - 1), requestedSidebar));
        contentX = windowX + sidebarWidth + contentGap;
        int availableContentWidth = Math.max(1, windowWidth - sidebarWidth - contentGap);
        int rightInset = Math.min(10, Math.max(0, availableContentWidth - 1));
        contentY = windowY + Math.min(68, Math.max(0, windowHeight - 1));
        contentWidth = Math.max(1, availableContentWidth - rightInset);
        contentHeight = Math.max(1, windowY + windowHeight - contentY
                - Math.min(12, Math.max(0, windowHeight - (contentY - windowY) - 1)));

        searchFrameWidth = Math.min(150, Math.max(1, contentWidth / 3));
        searchFrameX = contentX + contentWidth - searchFrameWidth;
        searchFrameY = windowY + TOP_CONTROL_Y_OFFSET;
        navigationX = contentX;
        navigationY = searchFrameY;
        int navigationWidth = Math.max(1, searchFrameX - SEARCH_NAVIGATION_GAP - navigationX);
        navigationTabWidth = Math.min(92, navigationWidth);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        layoutWindow();
        graphics.fill(0, 0, width, height, AcaUiTheme.SCRIM);
        UiAnimation.push(graphics, UiAnimation.scale(openedAt), width / 2.0f, height / 2.0f);
        graphics.fill(windowX + 4, windowY + 5, windowX + windowWidth + 5, windowY + windowHeight + 6, 0x66000000);
        AcaUiTheme.surface(graphics, windowX, windowY, windowWidth, windowHeight, AcaUiTheme.WINDOW);
        graphics.fill(windowX + 1, windowY + 1, windowX + windowWidth - 1, windowY + 31, AcaUiTheme.HEADER);
        graphics.fill(windowX + 1, windowY + 31, windowX + sidebarWidth, windowY + windowHeight - 1,
                AcaUiTheme.SIDEBAR);
        graphics.fill(windowX + sidebarWidth, windowY + 31, windowX + windowWidth - 1,
                windowY + windowHeight - 1, AcaUiTheme.CONTENT);

        drawBrand(graphics, mouseX, mouseY);
        drawTopNavigation(graphics, mouseX, mouseY);
        drawSidebar(graphics, mouseX, mouseY);
        drawSearchBackground(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        drawContent(graphics, mouseX, mouseY);
        UiAnimation.pop(graphics);
    }

    private void drawBrand(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, ICON, windowX + 9, windowY + 7, 0, 0,
                18, 18, 18, 18);
        graphics.text(font, Component.literal("QCLOUDY").withStyle(ChatFormatting.BOLD),
                windowX + 32, windowY + 7, AcaUiTheme.TEXT, false);
        graphics.text(font, "ADDITION", windowX + 32, windowY + 17, AcaUiTheme.TEXT_DIM, false);
        int closeX = windowX + windowWidth - 22;
        boolean hovered = AcaUiTheme.contains(mouseX, mouseY, closeX, windowY + 7, 14, 14);
        graphics.fill(closeX, windowY + 7, closeX + 14, windowY + 21, hovered ? AcaUiTheme.DANGER : AcaUiTheme.CONTROL);
        graphics.outline(closeX, windowY + 7, 14, 14, AcaUiTheme.BORDER);
        graphics.centeredText(font, "×", closeX + 7, windowY + 9, AcaUiTheme.TEXT);
    }

    private void drawTopNavigation(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        AcaUiTheme.button(graphics, font, ModText.get("config.tab.features"), navigationX, navigationY,
                navigationTabWidth, TOP_CONTROL_HEIGHT,
                AcaUiTheme.contains(mouseX, mouseY, navigationX, navigationY,
                        navigationTabWidth, TOP_CONTROL_HEIGHT), true);
    }

    private void drawSearchBackground(GuiGraphicsExtractor graphics) {
        if (searchBox == null) return;
        graphics.fill(searchFrameX, searchFrameY, searchFrameX + searchFrameWidth,
                searchFrameY + TOP_CONTROL_HEIGHT, AcaUiTheme.CONTROL);
        graphics.outline(searchFrameX, searchFrameY, searchFrameWidth, TOP_CONTROL_HEIGHT,
                searchBox.isFocused() ? AcaUiTheme.ACCENT : AcaUiTheme.BORDER);
    }

    private void drawSidebar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<Category> categories = availableCategories();
        int x = windowX + 8;
        int top = windowY + 43;
        int editY = windowY + windowHeight - 52;
        int viewportHeight = Math.max(20, editY - top - 5);
        int width = Math.max(1, sidebarWidth - 16);
        int categorySlotHeight = sidebarCategorySlotHeight(windowHeight, categories.size());
        int categoryButtonHeight = Math.min(22, categorySlotHeight - 2);
        sidebarMaximumScroll = Math.max(0, categories.size() * categorySlotHeight - viewportHeight);
        sidebarScroll = Math.clamp(sidebarScroll, 0, sidebarMaximumScroll);
        int y = top - sidebarScroll;
        graphics.enableScissor(windowX + 1, top, windowX + sidebarWidth, top + viewportHeight);
        for (Category value : categories) {
            boolean selected = category == value;
            boolean hovered = AcaUiTheme.contains(mouseX, mouseY, x, y, width, categoryButtonHeight);
            if (selected) graphics.fill(x, y, x + 3, y + categoryButtonHeight, AcaUiTheme.ACCENT);
            graphics.fill(x + 3, y, x + width, y + categoryButtonHeight,
                    selected ? 0xFF303A3F : hovered ? 0xFF2B3337 : AcaUiTheme.SIDEBAR);
            int textY = y + Math.max(2, (categoryButtonHeight - font.lineHeight) / 2);
            drawFittedText(graphics, Component.literal(ModText.get(value.key)), x + 10, textY,
                    Math.max(1, width - 13), selected ? AcaUiTheme.TEXT : AcaUiTheme.TEXT_MUTED);
            y += categorySlotHeight;
        }
        graphics.disableScissor();

        AcaUiTheme.button(graphics, font, ModText.get("config.layout"), x, editY, width, 20,
                AcaUiTheme.contains(mouseX, mouseY, x, editY, width, 20), false);
        int languageY = windowY + windowHeight - 27;
        AcaUiTheme.button(graphics, font, ModText.get("config.language.short"), x, languageY, width, 18,
                AcaUiTheme.contains(mouseX, mouseY, x, languageY, width, 18), false);
    }

    private void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        featureHits.clear();
        groupHits.clear();
        compatibilityReportHit = null;
        graphics.enableScissor(contentX, contentY, contentX + contentWidth, contentY + contentHeight);
        drawFeatureCards(graphics, mouseX, mouseY);
        graphics.disableScissor();
        contentScrollbar.update(contentX + contentWidth - VerticalScrollbar.WIDTH,
                contentY, contentHeight, maximumScroll, scroll);
        contentScrollbar.draw(graphics, mouseX, mouseY, AcaUiTheme.ACCENT);
    }

    private void drawFeatureCards(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<GroupBlock> blocks = groupBlocks();
        if (blocks.isEmpty()) {
            drawEmpty(graphics);
            maximumScroll = 0;
            return;
        }
        int featureWidth = Math.max(1, contentWidth - CONTENT_SCROLLBAR_GUTTER);
        int columns = contentWidth >= 360 ? 2 : 1;
        int cardWidth = Math.max(1, (featureWidth - CARD_GAP * (columns - 1)) / columns);
        int totalHeight = 0;
        for (GroupBlock block : blocks) {
            int itemCount = block.features().size() + (block.compatibilityReport() ? 1 : 0);
            totalHeight += featureGroupBlockHeight(block.expanded(), itemCount, columns);
        }
        maximumScroll = Math.max(0, totalHeight - CARD_GAP - contentHeight);
        scroll = Math.clamp(scroll, 0, maximumScroll);
        int y = contentY - scroll;
        for (GroupBlock block : blocks) {
            drawGroupHeader(graphics, block, contentX, y, featureWidth, mouseX, mouseY);
            if (intersectsContent(y, GROUP_HEADER_HEIGHT)) {
                groupHits.add(new Hit<>(block.group(), contentX, y,
                        featureWidth, GROUP_HEADER_HEIGHT));
            }
            y += GROUP_HEADER_HEIGHT + 5;
            if (block.expanded()) {
                int itemCount = block.features().size() + (block.compatibilityReport() ? 1 : 0);
                for (int index = 0; index < itemCount; index++) {
                    int column = index % columns;
                    int row = index / columns;
                    int x = contentX + column * (cardWidth + CARD_GAP);
                    int cardY = y + row * (CARD_HEIGHT + CARD_GAP);
                    if (block.compatibilityReport() && index == 0) {
                        drawCompatibilityReportCard(graphics, x, cardY, cardWidth, mouseX, mouseY);
                        if (intersectsContent(cardY, CARD_HEIGHT)) {
                            compatibilityReportHit = new Hit<>(Boolean.TRUE, x, cardY, cardWidth, CARD_HEIGHT);
                        }
                        continue;
                    }
                    int featureIndex = index - (block.compatibilityReport() ? 1 : 0);
                    UnifiedModIntegration.UnifiedFeature feature = block.features().get(featureIndex);
                    drawFeatureCard(graphics, feature, x, cardY, cardWidth, mouseX, mouseY);
                    if (intersectsContent(cardY, CARD_HEIGHT)) {
                        featureHits.add(new Hit<>(feature, x, cardY, cardWidth, CARD_HEIGHT));
                    }
                }
                int rows = (itemCount + columns - 1) / columns;
                y += rows * (CARD_HEIGHT + CARD_GAP) - CARD_GAP;
            }
            y += CARD_GAP;
        }
    }

    static int featureGroupBlockHeight(boolean expanded, int itemCount, int columns) {
        int height = GROUP_HEADER_HEIGHT + 5;
        if (expanded) {
            int rows = (itemCount + columns - 1) / columns;
            height += rows * (CARD_HEIGHT + CARD_GAP) - CARD_GAP;
        }
        return height + CARD_GAP;
    }

    private boolean intersectsContent(int y, int height) {
        return y + height > contentY && y < contentY + contentHeight;
    }

    private List<GroupBlock> groupBlocks() {
        List<GroupBlock> blocks = new ArrayList<>();
        boolean searching = !query.trim().isEmpty();
        String integrationGroup = ModText.get(FeatureGroup.INTEGRATIONS.key);
        boolean reportMatches = category == Category.GENERAL
                && matches(ModText.get("config.integration.report.title"),
                ModText.get("config.desc.integration.report"));
        java.util.Map<String, List<UnifiedModIntegration.UnifiedFeature>> byGroup = new java.util.LinkedHashMap<>();
        for (UnifiedModIntegration.UnifiedFeature feature : UnifiedModIntegration.features()) {
            if (feature.category != category || !matches(feature.title, feature.description)) continue;
            byGroup.computeIfAbsent(feature.group, ignored -> new ArrayList<>()).add(feature);
        }
        for (var entry : byGroup.entrySet()) {
            boolean compatibilityReport = reportMatches && entry.getKey().equals(integrationGroup);
            blocks.add(new GroupBlock(entry.getKey(), entry.getValue(),
                    searching || groupExpansionState.isExpanded(entry.getKey()), compatibilityReport));
            if (compatibilityReport) reportMatches = false;
        }
        if (reportMatches) {
            blocks.addFirst(new GroupBlock(integrationGroup, List.of(),
                    reportOnlyGroupExpanded(searching), true));
        }
        return blocks;
    }

    static boolean reportOnlyGroupExpanded(boolean searching) {
        return searching;
    }

    private void drawGroupHeader(GuiGraphicsExtractor graphics, GroupBlock block, int x, int y, int width,
                                 int mouseX, int mouseY) {
        boolean hovered = AcaUiTheme.contains(mouseX, mouseY, x, y, width, GROUP_HEADER_HEIGHT);
        graphics.fill(x, y, x + width, y + GROUP_HEADER_HEIGHT,
                hovered ? AcaUiTheme.CARD_HOVER : 0xFF20292D);
        graphics.outline(x, y, width, GROUP_HEADER_HEIGHT,
                hovered ? AcaUiTheme.ACCENT_DARK : AcaUiTheme.BORDER_SOFT);
        graphics.text(font, block.expanded() ? "▾" : "▸", x + 8, y + 7, AcaUiTheme.ACCENT, false);
        String count = Integer.toString(block.features().size() + (block.compatibilityReport() ? 1 : 0));
        drawFittedText(graphics, Component.literal(block.group()).withStyle(ChatFormatting.BOLD),
                x + 22, y + 7, Math.max(1, width - font.width(count) - 46), AcaUiTheme.TEXT);
        graphics.text(font, count, x + width - font.width(count) - 10, y + 7, AcaUiTheme.TEXT_DIM, false);
    }

    private void drawFeatureCard(GuiGraphicsExtractor graphics, UnifiedModIntegration.UnifiedFeature feature,
                                 int x, int y, int cardWidth,
                                 int mouseX, int mouseY) {
        boolean hovered = AcaUiTheme.contains(mouseX, mouseY, x, y, cardWidth, CARD_HEIGHT);
        boolean enabled = feature.enabled();
        graphics.fill(x, y, x + cardWidth, y + CARD_HEIGHT, hovered ? AcaUiTheme.CARD_HOVER : AcaUiTheme.CARD);
        graphics.outline(x, y, cardWidth, CARD_HEIGHT, hovered ? AcaUiTheme.ACCENT_DARK : AcaUiTheme.BORDER_SOFT);
        graphics.fill(x, y, x + 3, y + CARD_HEIGHT, enabled ? AcaUiTheme.ACCENT : AcaUiTheme.BORDER);
        Component title = Component.literal(feature.title).withStyle(ChatFormatting.BOLD);
        drawFittedText(graphics, title, x + 10, y + 8, Math.max(1, cardWidth - 20), AcaUiTheme.TEXT);
        List<FormattedCharSequence> lines = font.split(Component.literal(feature.description),
                Math.max(1, cardWidth - 20));
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            graphics.text(font, lines.get(i), x + 10, y + 24 + i * 10, AcaUiTheme.TEXT_MUTED, false);
        }
        String provider = feature.selectedProvider().displayName;
        String footer = feature.group + " · " + provider;
        drawFittedText(graphics, Component.literal(footer), x + 10, y + 57,
                Math.max(1, cardWidth - 20), AcaUiTheme.TEXT_DIM);
    }

    private void drawCompatibilityReportCard(GuiGraphicsExtractor graphics, int x, int y, int cardWidth,
                                             int mouseX, int mouseY) {
        boolean hovered = AcaUiTheme.contains(mouseX, mouseY, x, y, cardWidth, CARD_HEIGHT);
        graphics.fill(x, y, x + cardWidth, y + CARD_HEIGHT,
                hovered ? AcaUiTheme.CARD_HOVER : 0xFF2D2D29);
        graphics.outline(x, y, cardWidth, CARD_HEIGHT, hovered ? REPORT_ACCENT : AcaUiTheme.BORDER_SOFT);
        // Amber information strip deliberately differs from the blue enabled
        // strip used by real feature toggles. This card has no on/off state.
        graphics.fill(x, y, x + 3, y + CARD_HEIGHT, REPORT_ACCENT);
        graphics.fill(x + 10, y + 8, x + 23, y + 21, 0xFF554516);
        graphics.outline(x + 10, y + 8, 13, 13, REPORT_ACCENT);
        graphics.centeredText(font, "i", x + 16, y + 10, REPORT_ACCENT);
        drawFittedText(graphics,
                Component.literal(ModText.get("config.integration.report.title")).withStyle(ChatFormatting.BOLD),
                x + 29, y + 9, Math.max(1, cardWidth - 39), AcaUiTheme.TEXT);
        List<FormattedCharSequence> lines = font.split(
                Component.literal(ModText.get("config.desc.integration.report")), Math.max(1, cardWidth - 20));
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            graphics.text(font, lines.get(i), x + 10, y + 27 + i * 10, AcaUiTheme.TEXT_MUTED, false);
        }
        drawFittedText(graphics, Component.literal(ModText.get("config.integration.report.read_only")),
                x + 10, y + 57, Math.max(1, cardWidth - 20), REPORT_ACCENT);
    }

    private void drawEmpty(GuiGraphicsExtractor graphics) {
        graphics.centeredText(font, ModText.get("config.empty"), contentX + contentWidth / 2,
                contentY + contentHeight / 2 - 5, AcaUiTheme.TEXT_MUTED);
    }

    private boolean matches(String title, String description) {
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return true;
        return title.toLowerCase(Locale.ROOT).contains(normalized)
                || description.toLowerCase(Locale.ROOT).contains(normalized);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        VerticalScrollbar.Interaction scrollbarClick = contentScrollbar.mouseClicked(
                click.button(), click.x(), click.y(), scroll);
        if (scrollbarClick.consumed()) {
            scroll = scrollbarClick.scroll();
            return true;
        }
        if (opensCompatibilityReport(click.button()) && compatibilityReportHit != null
                && compatibilityReportHit.contains(click.x(), click.y())) {
            MinecraftClientCompat.setScreen(minecraft, new IntegrationCompatibilityScreen(this));
            return true;
        }
        if (click.button() == 1
                && AcaUiTheme.contains(click.x(), click.y(), contentX, contentY, contentWidth, contentHeight)) {
            for (Hit<UnifiedModIntegration.UnifiedFeature> hit : featureHits) {
                if (hit.contains(click.x(), click.y())) {
                    if (hasFeatureSettings(hit.value)) {
                        MinecraftClientCompat.setScreen(minecraft, new FeatureSettingsScreen(this, hit.value));
                    }
                    return true;
                }
            }
        }
        if (super.mouseClicked(click, doubled)) return true;
        if (click.button() != 0) return false;
        double mouseX = click.x();
        double mouseY = click.y();
        if (searchBox != null && AcaUiTheme.contains(mouseX, mouseY, searchFrameX, searchFrameY,
                searchFrameWidth, TOP_CONTROL_HEIGHT)) {
            searchBox.setFocused(true);
            return true;
        }
        if (AcaUiTheme.contains(mouseX, mouseY, windowX + windowWidth - 22, windowY + 7, 14, 14)) {
            onClose();
            return true;
        }
        if (AcaUiTheme.contains(mouseX, mouseY, navigationX, navigationY,
                navigationTabWidth, TOP_CONTROL_HEIGHT)) {
            scroll = 0;
            contentScrollbar.cancelDrag();
            return true;
        }
        int sidebarX = windowX + 8;
        int sidebarY = windowY + 43 - sidebarScroll;
        int sideWidth = sidebarWidth - 16;
        List<Category> categories = availableCategories();
        int categorySlotHeight = sidebarCategorySlotHeight(windowHeight, categories.size());
        int categoryButtonHeight = Math.min(22, categorySlotHeight - 2);
        int categoryViewportTop = windowY + 43;
        int categoryViewportBottom = windowY + windowHeight - 57;
        for (Category value : categories) {
            if (mouseY >= categoryViewportTop && mouseY < categoryViewportBottom
                    && AcaUiTheme.contains(mouseX, mouseY, sidebarX, sidebarY, sideWidth, categoryButtonHeight)) {
                category = value;
                scroll = 0;
                contentScrollbar.cancelDrag();
                return true;
            }
            sidebarY += categorySlotHeight;
        }
        int editY = windowY + windowHeight - 52;
        if (AcaUiTheme.contains(mouseX, mouseY, sidebarX, editY, sideWidth, 20)) {
            MinecraftClientCompat.setScreen(minecraft, new HudLayoutScreen(this));
            return true;
        }
        int languageY = windowY + windowHeight - 27;
        if (AcaUiTheme.contains(mouseX, mouseY, sidebarX, languageY, sideWidth, 18)) {
            ModConfig config = ConfigManager.get();
            config.language = "zh_cn".equals(config.language) ? "en_us" : "zh_cn";
            ConfigManager.save();
            UnifiedModIntegration.invalidate();
            // Group ids are localized display strings, so discard the stale
            // names. Every group remains collapsed after the language change.
            groupExpansionState.clear();
            contentScrollbar.cancelDrag();
            rebuildWidgets();
            return true;
        }
        if (!AcaUiTheme.contains(mouseX, mouseY, contentX, contentY, contentWidth, contentHeight)) return false;
        for (Hit<String> hit : groupHits) {
            if (!hit.contains(mouseX, mouseY)) continue;
            groupExpansionState.toggle(hit.value);
            contentScrollbar.cancelDrag();
            scroll = Math.clamp(scroll, 0, maximumScroll);
            return true;
        }
        for (Hit<UnifiedModIntegration.UnifiedFeature> hit : featureHits) {
            if (!hit.contains(mouseX, mouseY)) continue;
            if (isIntegrationScanMaster(hit.value.qcloudyFeature) && !hit.value.enabled()
                    && UnifiedModIntegration.requiresScanConfirmation()) {
                UnifiedModIntegration.ScanView view = hit.value.qcloudyFeature == Feature.UNIFIED_HUD_EDITOR
                        ? UnifiedModIntegration.ScanView.HUD : UnifiedModIntegration.ScanView.SETTINGS;
                MinecraftClientCompat.setScreen(minecraft, new IntegrationScanConfirmScreen(this, view, () -> {
                    boolean changed = hit.value.toggle();
                    if (changed && hit.value.enabled()) {
                        UnifiedModIntegration.requestConfirmedScan(false);
                        MinecraftClientCompat.setScreen(minecraft, new FeatureSettingsScreen(this, hit.value));
                    } else {
                        MinecraftClientCompat.setScreen(minecraft, this);
                    }
                }));
                return true;
            }
            boolean changed = hit.value.toggle();
            if (changed && hit.value.enabled()
                    && (hit.value.qcloudyFeature == Feature.UNIFIED_SETTINGS_EDITOR
                    || hit.value.qcloudyFeature == Feature.UNIFIED_HUD_EDITOR)) {
                MinecraftClientCompat.setScreen(minecraft, new FeatureSettingsScreen(this, hit.value));
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        VerticalScrollbar.Interaction scrollbarDrag = contentScrollbar.mouseDragged(
                click.button(), click.y(), scroll);
        if (scrollbarDrag.consumed()) {
            scroll = scrollbarDrag.scroll();
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        VerticalScrollbar.Interaction scrollbarRelease = contentScrollbar.mouseReleased(
                click.button(), click.y(), scroll);
        if (scrollbarRelease.consumed()) {
            scroll = scrollbarRelease.scroll();
            return true;
        }
        return super.mouseReleased(click);
    }

    static boolean isIntegrationScanMaster(Feature feature) {
        return feature == Feature.UNIFIED_SETTINGS_EDITOR
                || feature == Feature.UNIFIED_HUD_EDITOR;
    }

    static boolean hasFeatureSettings(UnifiedModIntegration.UnifiedFeature feature) {
        // External bindings may provide editable native options or a provider
        // selector even when the matching QCA toggle itself has no settings.
        return !feature.external.isEmpty()
                || feature.qcloudyFeature != null && feature.qcloudyFeature.hasSettings();
    }

    static List<Feature> integrationMasterSwitches() {
        return java.util.Arrays.stream(Feature.values())
                .filter(ConfigScreen::isIntegrationScanMaster)
                .toList();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (contentScrollbar.dragging()) {
            VerticalScrollbar.Interaction scrollbarWheel = contentScrollbar.mouseScrolled(vertical, 24, scroll);
            scroll = scrollbarWheel.scroll();
            return true;
        }
        int sidebarTop = windowY + 43;
        int sidebarBottom = windowY + windowHeight - 57;
        if (AcaUiTheme.contains(mouseX, mouseY, windowX + 1, sidebarTop,
                sidebarWidth - 1, Math.max(1, sidebarBottom - sidebarTop))) {
            sidebarScroll = Math.clamp(sidebarScroll - (int) Math.round(vertical * 20),
                    0, sidebarMaximumScroll);
            return true;
        }
        if (AcaUiTheme.contains(mouseX, mouseY, contentX, contentY, contentWidth, contentHeight)
                || contentScrollbar.contains(mouseX, mouseY)) {
            VerticalScrollbar.Interaction scrollbarWheel = contentScrollbar.mouseScrolled(vertical, 24, scroll);
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

    static int sidebarCategorySlotHeight(int windowHeight) {
        return sidebarCategorySlotHeight(windowHeight, Category.values().length);
    }

    static int sidebarCategorySlotHeight(int windowHeight, int categoryCount) {
        int availableHeight = windowHeight - 43 - 52 - 9;
        return Math.clamp(availableHeight / Math.max(1, categoryCount), 18, 24);
    }

    private List<Category> availableCategories() {
        List<Category> featureCategories = UnifiedModIntegration.features().stream()
                .map(feature -> feature.category)
                .toList();
        List<Category> categories = visibleCategories(featureCategories);
        if (!categories.contains(category)) {
            category = categories.getFirst();
            scroll = 0;
            contentScrollbar.cancelDrag();
        }
        return categories;
    }

    static List<Category> visibleCategories(Iterable<Category> featureCategories) {
        java.util.EnumSet<Category> present = java.util.EnumSet.noneOf(Category.class);
        for (Category featureCategory : featureCategories) present.add(featureCategory);
        if (present.isEmpty()) present.add(Category.GENERAL);
        return java.util.Arrays.stream(Category.values())
                .filter(present::contains)
                .toList();
    }

    static boolean opensCompatibilityReport(int mouseButton) {
        return mouseButton == 0 || mouseButton == 1;
    }

    private void drawFittedText(GuiGraphicsExtractor graphics, Component value, int x, int y,
                                int maximumWidth, int color) {
        int measured = font.width(value);
        if (measured <= maximumWidth) {
            graphics.text(font, value, x, y, color, false);
            return;
        }
        float scale = maximumWidth / (float) measured;
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y + Math.round((1.0f - scale) * 4.0f));
        graphics.pose().scale(scale, scale);
        graphics.text(font, value, 0, 0, color, false);
        graphics.pose().popMatrix();
    }

    public enum HudFocus { MAP, MINING, FORAGING, HUNTING, SAFARI, PET }

    enum Category {
        GENERAL("config.category.general"),
        MAPS("config.category.maps"),
        ITEMS_AND_MENUS("config.category.items_and_menus"),
        COMBAT("config.category.combat"),
        DUNGEONS("config.category.dungeons"),
        SLAYER("config.category.slayer"),
        MINING("config.category.mining"),
        FARMING("config.category.farming"),
        FORAGING("config.category.foraging"),
        FISHING("config.category.fishing"),
        HUNTING("config.category.hunting"),
        RIFT("config.category.rift"),
        EVENTS("config.category.events");

        final String key;

        Category(String key) {
            this.key = key;
        }
    }

    enum FeatureGroup {
        HUD(Category.GENERAL, "config.group.hud"),
        INTEGRATIONS(Category.GENERAL, "config.group.integrations"),
        CONNECTION(Category.GENERAL, "config.group.connection"),
        CHAT_UI(Category.GENERAL, "config.group.chat_ui"),
        COMMANDS(Category.GENERAL, "config.group.commands"),
        FISHING(Category.FISHING, "config.group.fishing"),
        MAPS(Category.MAPS, "config.group.maps"),
        WAYPOINTS(Category.MAPS, "config.group.waypoints"),
        MINING_OBJECTIVES(Category.MINING, "config.group.mining_objectives"),
        FORAGING_TORRHUS(Category.FORAGING, "config.group.torrhus"),
        FORAGING_GALATEA(Category.FORAGING, "config.group.galatea"),
        HUNTING_CORE(Category.HUNTING, "config.group.hunting_core"),
        HUNTING_SAFARI(Category.HUNTING, "config.group.safari"),
        CRIMSON_OBJECTIVES(Category.COMBAT, "config.group.crimson_objectives"),
        COMBAT_DEATH_SAVES(Category.COMBAT, "config.group.death_saves"),
        COMBAT_DEPLOYABLES(Category.COMBAT, "config.group.deployables"),
        COMBAT_VISIBILITY(Category.COMBAT, "config.group.combat_visibility"),
        DUNGEON_PARTY(Category.DUNGEONS, "config.group.dungeon_party"),
        PET_DISPLAY(Category.ITEMS_AND_MENUS, "config.group.pet_display"),
        CENTURY_CAKES(Category.ITEMS_AND_MENUS, "config.group.century_cakes"),
        SHARD_FUSION(Category.ITEMS_AND_MENUS, "config.group.shard_fusion"),
        INVENTORY_TOOLS(Category.ITEMS_AND_MENUS, "config.group.inventory_tools");

        final Category category;
        final String key;

        FeatureGroup(Category category, String key) {
            this.category = category;
            this.key = key;
        }
    }

    enum Feature {
        HUD_ANIMATIONS(FeatureGroup.HUD, "config.setting.animations", "config.desc.animations"),
        HUNTING_ALERT_SOUND(FeatureGroup.HUD, "config.hunting.alert_sound", "config.desc.hunting.alert_sound"),
        UNIFIED_SETTINGS_EDITOR(FeatureGroup.INTEGRATIONS, "config.integration.settings_editor",
                "config.desc.integration.settings_editor"),
        UNIFIED_HUD_EDITOR(FeatureGroup.INTEGRATIONS, "config.integration.hud_editor",
                "config.desc.integration.hud_editor"),
        MANUAL_RECONNECT(FeatureGroup.CONNECTION, "config.manual_reconnect", "config.desc.manual_reconnect"),
        CHAT_PEEK(FeatureGroup.CHAT_UI, "config.chat_peek", "config.desc.chat_peek"),
        PARTY_AUTO_ACCEPT(FeatureGroup.CHAT_UI, "config.party.auto_accept", "config.desc.party.auto_accept"),
        DIRECT_MESSAGE_PARTY_REQUEST(FeatureGroup.CHAT_UI, "config.chat.dm_party_request",
                "config.desc.chat.dm_party_request"),
        QUICK_PRIVATE_PARTY_REQUEST(FeatureGroup.CHAT_UI, "config.chat.quick_private_party_request",
                "config.desc.chat.quick_private_party_request"),
        FAST_PARTY_COMMANDS(FeatureGroup.CHAT_UI, "config.chat.fast_party_commands",
                "config.desc.chat.fast_party_commands"),
        PARTY_COMMANDS(FeatureGroup.COMMANDS, "config.commands.party_commands",
                "config.desc.commands.party_commands"),
        DUNGEON_QUICK_VIEW(FeatureGroup.DUNGEON_PARTY, "config.dungeon.quick_view",
                "config.desc.dungeon.quick_view"),
        FISHING_BITE_ALERT(FeatureGroup.FISHING, "config.fishing.bite_alert", "config.desc.fishing.bite_alert"),
        DWARVEN_MAP(FeatureGroup.MAPS, "config.dwarven_map", "config.desc.dwarven_map"),
        GLACITE_MAP(FeatureGroup.MAPS, "config.glacite_map", "config.desc.glacite_map"),
        FAIRY_SOUL_WAYPOINTS(FeatureGroup.WAYPOINTS, "config.hunting.fairy_souls", "config.desc.hunting.fairy_souls"),
        MINING_TRACKER(FeatureGroup.MINING_OBJECTIVES, "config.mining_tracker", "config.desc.mining_tracker"),
        TORRHUS_TRACKER(FeatureGroup.FORAGING_TORRHUS, "config.hunting.torrhus_tracker", "config.desc.hunting.torrhus_tracker"),
        GALATEA_TRACKER(FeatureGroup.FORAGING_GALATEA, "config.hunting.galatea_tracker", "config.desc.hunting.galatea_tracker"),
        TREE_CRITTER_TIMER(FeatureGroup.FORAGING_TORRHUS, "config.hunting.tree_critter_timer", "config.desc.hunting.tree_critter_timer"),
        MIRIA_CONTEST(FeatureGroup.FORAGING_TORRHUS, "config.hunting.miria_contest", "config.desc.hunting.miria_contest"),
        AGATHA_CONTEST(FeatureGroup.FORAGING_GALATEA, "config.hunting.agatha_contest", "config.desc.hunting.agatha_contest"),
        BENEFACTOR_HUD(FeatureGroup.FORAGING_TORRHUS, "config.hunting.benefactor", "config.desc.hunting.benefactor"),
        TREE_GIFT_ALERTS(FeatureGroup.FORAGING_TORRHUS, "config.hunting.tree_gift", "config.desc.hunting.tree_gift"),
        BEEHEEMOTH_HELPER(FeatureGroup.HUNTING_CORE, "config.hunting.beeheemoth", "config.desc.hunting.beeheemoth"),
        LASSO_REEL_SOUND(FeatureGroup.HUNTING_CORE, "config.hunting.lasso_reel_sound", "config.desc.hunting.lasso_reel_sound"),
        CRITTER_BEHAVIOR(FeatureGroup.HUNTING_CORE, "config.hunting.critter_behavior", "config.desc.hunting.critter_behavior"),
        COLD_SAFETY(FeatureGroup.HUNTING_SAFARI, "config.hunting.cold_safety", "config.desc.hunting.cold_safety"),
        DOOMSPIRAL_READY(FeatureGroup.HUNTING_SAFARI, "config.hunting.doomspiral_ready", "config.desc.hunting.doomspiral_ready"),
        WARDEN_READY_ALERT(FeatureGroup.HUNTING_SAFARI, "config.hunting.warden_ready", "config.desc.hunting.warden_ready"),
        SAFARI_CRITTER_HIGHLIGHT(FeatureGroup.HUNTING_SAFARI, "config.hunting.critter_highlight", "config.desc.hunting.critter_highlight"),
        SAFARI_DASHBOARD(FeatureGroup.HUNTING_SAFARI, "config.hunting.safari_dashboard", "config.desc.hunting.safari_dashboard"),
        SAFARI_SHARD_STATS(FeatureGroup.HUNTING_SAFARI, "config.hunting.safari_shard_stats", "config.desc.hunting.safari_shard_stats"),
        SAFARI_CRITTERDEX(FeatureGroup.HUNTING_SAFARI, "config.hunting.safari_critterdex", "config.desc.hunting.safari_critterdex"),
        SPARKLING_ALERT(FeatureGroup.HUNTING_SAFARI, "config.hunting.sparkling", "config.desc.hunting.sparkling"),
        FLOOR_QUEST_ASSISTANT(FeatureGroup.HUNTING_SAFARI, "config.hunting.floor_quest", "config.desc.hunting.floor_quest"),
        WUMPA_HUD(FeatureGroup.HUNTING_SAFARI, "config.hunting.wumpa", "config.desc.hunting.wumpa"),
        SNOOZLE_WALL_OVERLAY(FeatureGroup.HUNTING_SAFARI, "config.hunting.snoozle_wall", "config.desc.hunting.snoozle_wall"),
        SAFARI_BELT(FeatureGroup.HUNTING_SAFARI, "config.hunting.safari_belt", "config.desc.hunting.safari_belt"),
        CRIMSON_TASKS(FeatureGroup.CRIMSON_OBJECTIVES, "config.crimson_tasks", "config.desc.crimson_tasks"),
        DEATH_SAVE_ALERTS(FeatureGroup.COMBAT_DEATH_SAVES, "config.combat.death_save_alerts",
                "config.desc.combat.death_save_alerts"),
        SPIRIT_MASK_COOLDOWN_HUD(FeatureGroup.COMBAT_DEATH_SAVES, "config.combat.spirit_mask_cooldown_hud",
                "config.desc.combat.spirit_mask_cooldown_hud"),
        BONZO_MASK_COOLDOWN_HUD(FeatureGroup.COMBAT_DEATH_SAVES, "config.combat.bonzo_mask_cooldown_hud",
                "config.desc.combat.bonzo_mask_cooldown_hud"),
        PHOENIX_COOLDOWN_HUD(FeatureGroup.COMBAT_DEATH_SAVES, "config.combat.phoenix_cooldown_hud",
                "config.desc.combat.phoenix_cooldown_hud"),
        DEPLOYABLE_EXPIRY_ALERT(FeatureGroup.COMBAT_DEPLOYABLES, "config.combat.deployable_expiry",
                "config.desc.combat.deployable_expiry"),
        DRAGON_HIGHLIGHT(FeatureGroup.COMBAT_VISIBILITY, "config.dragon_highlight", "config.desc.dragon_highlight"),
        PET_HUD(FeatureGroup.PET_DISPLAY, "config.pet_hud", "config.desc.pet_hud"),
        CENTURY_CAKE_EFFECTS(FeatureGroup.CENTURY_CAKES, "config.century_cake",
                "config.desc.century_cake"),
        SHARD_FUSION_HELPER(FeatureGroup.SHARD_FUSION, "config.shard_fusion", "config.desc.shard_fusion"),
        ITEM_TIMESTAMPS(FeatureGroup.INVENTORY_TOOLS, "config.item_timestamps", "config.desc.item_timestamps"),
        CURSOR_MEMORY(FeatureGroup.INVENTORY_TOOLS, "config.cursor_memory", "config.desc.cursor_memory"),
        TELEPORT_SOUNDS(FeatureGroup.INVENTORY_TOOLS, "config.teleport_sounds", "config.desc.teleport_sounds");

        final FeatureGroup group;
        final Category category;
        final String titleKey;
        final String descriptionKey;

        Feature(FeatureGroup group, String titleKey, String descriptionKey) {
            this.group = group;
            this.category = group.category;
            this.titleKey = titleKey;
            this.descriptionKey = descriptionKey;
        }

        boolean enabled(ModConfig config) {
            return switch (this) {
                case HUD_ANIMATIONS -> config.hudStyle.animations;
                case MANUAL_RECONNECT -> config.manualReconnectButton;
                case PARTY_AUTO_ACCEPT -> config.chat.partyAutoAccept;
                case DIRECT_MESSAGE_PARTY_REQUEST -> config.chat.directMessagePartyRequest;
                case QUICK_PRIVATE_PARTY_REQUEST -> config.chat.quickPrivatePartyRequest;
                case FAST_PARTY_COMMANDS -> config.chat.fastPartyCommands;
                case PARTY_COMMANDS -> config.chat.partyCommands;
                case DUNGEON_QUICK_VIEW -> config.dungeons.playerQuickView;
                case FISHING_BITE_ALERT -> config.fishing.biteAlert;
                case DWARVEN_MAP -> config.maps.dwarvenMines;
                case GLACITE_MAP -> config.maps.glaciteTunnels;
                case MINING_TRACKER -> config.mining.taskAndPowderTracker;
                case HUNTING_ALERT_SOUND -> config.hunting.alertSound;
                case UNIFIED_SETTINGS_EDITOR -> config.integrations.unifiedSettingsEditor;
                case UNIFIED_HUD_EDITOR -> config.integrations.unifiedHudEditor;
                case COLD_SAFETY -> config.hunting.coldSafety;
                case DOOMSPIRAL_READY -> config.hunting.doomspiralReadyAlert;
                case WARDEN_READY_ALERT -> config.hunting.wardenReadyAlert;
                case FAIRY_SOUL_WAYPOINTS -> config.hunting.fairySoulWaypoints;
                case SAFARI_CRITTER_HIGHLIGHT -> config.hunting.safariCritterHighlight;
                case TORRHUS_TRACKER -> config.hunting.torrhusTracker;
                case GALATEA_TRACKER -> config.hunting.galateaTracker;
                case TREE_CRITTER_TIMER -> config.hunting.treeCritterTimer;
                case BEEHEEMOTH_HELPER -> config.hunting.beeheemothHelper;
                case LASSO_REEL_SOUND -> config.hunting.lassoReelAudio.sound;
                case MIRIA_CONTEST -> config.hunting.miriaContest;
                case AGATHA_CONTEST -> config.hunting.agathaContest;
                case CRITTER_BEHAVIOR -> config.hunting.critterBehavior;
                case BENEFACTOR_HUD -> config.hunting.benefactorHud;
                case TREE_GIFT_ALERTS -> config.hunting.treeGiftAlerts;
                case SAFARI_DASHBOARD -> config.hunting.safariDashboard;
                case SAFARI_SHARD_STATS -> config.hunting.safariShards;
                case SAFARI_CRITTERDEX -> config.hunting.safariCritterdex;
                case SPARKLING_ALERT -> config.hunting.sparklingAlert;
                case FLOOR_QUEST_ASSISTANT -> config.hunting.floorDropAssistant || config.hunting.questItemTracker;
                case WUMPA_HUD -> config.hunting.wumpaHud;
                case SNOOZLE_WALL_OVERLAY -> config.hunting.snoozleWallOverlay;
                case SAFARI_BELT -> config.hunting.safariBeltTooltip;
                case CRIMSON_TASKS -> config.crimsonIsle.taskTracker;
                case DEATH_SAVE_ALERTS -> config.combat.deathSaveAlerts;
                case SPIRIT_MASK_COOLDOWN_HUD -> config.combat.spiritMaskCooldownHud;
                case BONZO_MASK_COOLDOWN_HUD -> config.combat.bonzoMaskCooldownHud;
                case PHOENIX_COOLDOWN_HUD -> config.combat.phoenixCooldownHud;
                case DEPLOYABLE_EXPIRY_ALERT -> config.combat.deployableExpiryAlert;
                case DRAGON_HIGHLIGHT -> config.combat.enderDragonHighlight;
                case PET_HUD -> config.pets.equippedPetHud;
                case CENTURY_CAKE_EFFECTS -> config.centuryCakes.expiryAlerts;
                case SHARD_FUSION_HELPER -> config.inventory.shardFusionHelper;
                case CHAT_PEEK -> config.chat.chatPeek;
                case ITEM_TIMESTAMPS -> config.inventory.itemTimestamps;
                case CURSOR_MEMORY -> config.inventory.saveCursorPosition;
                case TELEPORT_SOUNDS -> config.inventory.teleportSoundCustomization;
            };
        }

        void toggle(ModConfig config) {
            switch (this) {
                case HUD_ANIMATIONS -> config.hudStyle.animations = !config.hudStyle.animations;
                case MANUAL_RECONNECT -> config.manualReconnectButton = !config.manualReconnectButton;
                case PARTY_AUTO_ACCEPT -> config.chat.partyAutoAccept = !config.chat.partyAutoAccept;
                case DIRECT_MESSAGE_PARTY_REQUEST -> config.chat.directMessagePartyRequest =
                        !config.chat.directMessagePartyRequest;
                case QUICK_PRIVATE_PARTY_REQUEST -> config.chat.quickPrivatePartyRequest =
                        !config.chat.quickPrivatePartyRequest;
                case FAST_PARTY_COMMANDS -> config.chat.fastPartyCommands = !config.chat.fastPartyCommands;
                case PARTY_COMMANDS -> config.chat.partyCommands = !config.chat.partyCommands;
                case DUNGEON_QUICK_VIEW -> config.dungeons.playerQuickView = !config.dungeons.playerQuickView;
                case FISHING_BITE_ALERT -> config.fishing.biteAlert = !config.fishing.biteAlert;
                case DWARVEN_MAP -> config.maps.dwarvenMines = !config.maps.dwarvenMines;
                case GLACITE_MAP -> config.maps.glaciteTunnels = !config.maps.glaciteTunnels;
                case MINING_TRACKER -> config.mining.taskAndPowderTracker = !config.mining.taskAndPowderTracker;
                case HUNTING_ALERT_SOUND -> config.hunting.alertSound = !config.hunting.alertSound;
                case UNIFIED_SETTINGS_EDITOR -> config.integrations.unifiedSettingsEditor =
                        !config.integrations.unifiedSettingsEditor;
                case UNIFIED_HUD_EDITOR -> config.integrations.unifiedHudEditor =
                        !config.integrations.unifiedHudEditor;
                case COLD_SAFETY -> config.hunting.coldSafety = !config.hunting.coldSafety;
                case DOOMSPIRAL_READY -> config.hunting.doomspiralReadyAlert = !config.hunting.doomspiralReadyAlert;
                case WARDEN_READY_ALERT -> config.hunting.wardenReadyAlert = !config.hunting.wardenReadyAlert;
                case FAIRY_SOUL_WAYPOINTS -> config.hunting.fairySoulWaypoints = !config.hunting.fairySoulWaypoints;
                case SAFARI_CRITTER_HIGHLIGHT -> config.hunting.safariCritterHighlight = !config.hunting.safariCritterHighlight;
                case TORRHUS_TRACKER -> config.hunting.torrhusTracker = !config.hunting.torrhusTracker;
                case GALATEA_TRACKER -> config.hunting.galateaTracker = !config.hunting.galateaTracker;
                case TREE_CRITTER_TIMER -> config.hunting.treeCritterTimer = !config.hunting.treeCritterTimer;
                case BEEHEEMOTH_HELPER -> config.hunting.beeheemothHelper = !config.hunting.beeheemothHelper;
                case LASSO_REEL_SOUND -> config.hunting.lassoReelAudio.sound = !config.hunting.lassoReelAudio.sound;
                case MIRIA_CONTEST -> config.hunting.miriaContest = !config.hunting.miriaContest;
                case AGATHA_CONTEST -> config.hunting.agathaContest = !config.hunting.agathaContest;
                case CRITTER_BEHAVIOR -> config.hunting.critterBehavior = !config.hunting.critterBehavior;
                case BENEFACTOR_HUD -> config.hunting.benefactorHud = !config.hunting.benefactorHud;
                case TREE_GIFT_ALERTS -> config.hunting.treeGiftAlerts = !config.hunting.treeGiftAlerts;
                case SAFARI_DASHBOARD -> config.hunting.safariDashboard = !config.hunting.safariDashboard;
                case SAFARI_SHARD_STATS -> config.hunting.safariShards = !config.hunting.safariShards;
                case SAFARI_CRITTERDEX -> config.hunting.safariCritterdex = !config.hunting.safariCritterdex;
                case SPARKLING_ALERT -> config.hunting.sparklingAlert = !config.hunting.sparklingAlert;
                case FLOOR_QUEST_ASSISTANT -> {
                    boolean enabled = config.hunting.floorDropAssistant || config.hunting.questItemTracker;
                    config.hunting.floorDropAssistant = !enabled;
                    config.hunting.questItemTracker = !enabled;
                }
                case WUMPA_HUD -> config.hunting.wumpaHud = !config.hunting.wumpaHud;
                case SNOOZLE_WALL_OVERLAY -> config.hunting.snoozleWallOverlay = !config.hunting.snoozleWallOverlay;
                case SAFARI_BELT -> config.hunting.safariBeltTooltip = !config.hunting.safariBeltTooltip;
                case CRIMSON_TASKS -> config.crimsonIsle.taskTracker = !config.crimsonIsle.taskTracker;
                case DEATH_SAVE_ALERTS -> config.combat.deathSaveAlerts = !config.combat.deathSaveAlerts;
                case SPIRIT_MASK_COOLDOWN_HUD -> config.combat.spiritMaskCooldownHud =
                        !config.combat.spiritMaskCooldownHud;
                case BONZO_MASK_COOLDOWN_HUD -> config.combat.bonzoMaskCooldownHud =
                        !config.combat.bonzoMaskCooldownHud;
                case PHOENIX_COOLDOWN_HUD -> config.combat.phoenixCooldownHud =
                        !config.combat.phoenixCooldownHud;
                case DEPLOYABLE_EXPIRY_ALERT -> config.combat.deployableExpiryAlert =
                        !config.combat.deployableExpiryAlert;
                case DRAGON_HIGHLIGHT -> config.combat.enderDragonHighlight = !config.combat.enderDragonHighlight;
                case PET_HUD -> config.pets.equippedPetHud = !config.pets.equippedPetHud;
                case CENTURY_CAKE_EFFECTS -> config.centuryCakes.expiryAlerts =
                        !config.centuryCakes.expiryAlerts;
                case SHARD_FUSION_HELPER -> config.inventory.shardFusionHelper = !config.inventory.shardFusionHelper;
                case CHAT_PEEK -> config.chat.chatPeek = !config.chat.chatPeek;
                case ITEM_TIMESTAMPS -> config.inventory.itemTimestamps = !config.inventory.itemTimestamps;
                case CURSOR_MEMORY -> config.inventory.saveCursorPosition = !config.inventory.saveCursorPosition;
                case TELEPORT_SOUNDS -> config.inventory.teleportSoundCustomization = !config.inventory.teleportSoundCustomization;
            }
        }

        ModConfig.HudType hudType() {
            return switch (this) {
                case DWARVEN_MAP, GLACITE_MAP -> ModConfig.HudType.MAP;
                case MINING_TRACKER, CRIMSON_TASKS -> ModConfig.HudType.MINING;
                case TORRHUS_TRACKER, GALATEA_TRACKER, TREE_CRITTER_TIMER, MIRIA_CONTEST, AGATHA_CONTEST,
                        CRITTER_BEHAVIOR, BENEFACTOR_HUD,
                        SAFARI_DASHBOARD, SAFARI_SHARD_STATS, SAFARI_CRITTERDEX,
                        FLOOR_QUEST_ASSISTANT, WUMPA_HUD -> ModConfig.HudType.HUNTING;
                case PET_HUD -> ModConfig.HudType.PET;
                case SPIRIT_MASK_COOLDOWN_HUD -> ModConfig.HudType.SPIRIT_MASK_COOLDOWN;
                case BONZO_MASK_COOLDOWN_HUD -> ModConfig.HudType.BONZO_MASK_COOLDOWN;
                case PHOENIX_COOLDOWN_HUD -> ModConfig.HudType.PHOENIX_COOLDOWN;
                case HUD_ANIMATIONS, HUNTING_ALERT_SOUND, UNIFIED_SETTINGS_EDITOR, UNIFIED_HUD_EDITOR,
                        MANUAL_RECONNECT, PARTY_AUTO_ACCEPT, DIRECT_MESSAGE_PARTY_REQUEST,
                        QUICK_PRIVATE_PARTY_REQUEST, FAST_PARTY_COMMANDS, PARTY_COMMANDS,
                        DUNGEON_QUICK_VIEW,
                        FISHING_BITE_ALERT,
                        COLD_SAFETY, DOOMSPIRAL_READY, WARDEN_READY_ALERT,
                        FAIRY_SOUL_WAYPOINTS, SAFARI_CRITTER_HIGHLIGHT, BEEHEEMOTH_HELPER,
                        LASSO_REEL_SOUND, TREE_GIFT_ALERTS,
                        SPARKLING_ALERT, SNOOZLE_WALL_OVERLAY, SAFARI_BELT,
                        DEATH_SAVE_ALERTS, DEPLOYABLE_EXPIRY_ALERT, DRAGON_HIGHLIGHT, CHAT_PEEK,
                        CENTURY_CAKE_EFFECTS -> null;
                case SHARD_FUSION_HELPER, ITEM_TIMESTAMPS, CURSOR_MEMORY, TELEPORT_SOUNDS -> null;
            };
        }

        boolean inventoryFeature() {
            // PET_HUD, Century Cakes and Shard Fusion share the sidebar
            // category but are not inventory-tool implementations. In
            // particular PET_HUD must continue into the HUD appearance rows.
            return group == FeatureGroup.INVENTORY_TOOLS;
        }

        boolean huntingFeature() {
            return group == FeatureGroup.FORAGING_TORRHUS || group == FeatureGroup.FORAGING_GALATEA
                    || group == FeatureGroup.HUNTING_CORE
                    || group == FeatureGroup.HUNTING_SAFARI;
        }

        boolean hasSettings() {
            if (this == HUD_ANIMATIONS || this == HUNTING_ALERT_SOUND
                    || this == MANUAL_RECONNECT || this == DEATH_SAVE_ALERTS
                    || this == DIRECT_MESSAGE_PARTY_REQUEST
                    || this == QUICK_PRIVATE_PARTY_REQUEST
                    || this == DUNGEON_QUICK_VIEW) return false;
            if (this == FAIRY_SOUL_WAYPOINTS) return false;
            if (huntingFeature()) return hudType() != null || !HuntingOption.forFeature(this).isEmpty();
            return true;
        }
    }

    private record GroupBlock(String group, List<UnifiedModIntegration.UnifiedFeature> features,
                              boolean expanded, boolean compatibilityReport) { }

    static final class GroupExpansionState {
        private final Set<String> expandedGroups = new HashSet<>();

        boolean isExpanded(String group) {
            return expandedGroups.contains(group);
        }

        void toggle(String group) {
            if (!expandedGroups.remove(group)) expandedGroups.add(group);
        }

        void clear() {
            expandedGroups.clear();
        }
    }

    private record Hit<T>(T value, int x, int y, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return AcaUiTheme.contains(mouseX, mouseY, x, y, width, height);
        }
    }
}

package cloudy.autume.addition.config;

import cloudy.autume.addition.compat.MinecraftClientCompat;
import cloudy.autume.addition.i18n.ModText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only view of recognised provider controls that QCA cannot safely manage. */
final class IntegrationCompatibilityScreen extends Screen {
    private static final int PROVIDER_HEADER_HEIGHT = 24;
    private static final int ROW_GAP = 5;
    private static final int REPORT_ACCENT = 0xFFF2C14E;

    private final Screen parent;
    private final long openedAt = System.nanoTime();
    private final VerticalScrollbar contentScrollbar = new VerticalScrollbar();
    private final List<UnifiedModIntegration.Provider> installedProviders;
    private final Map<UnifiedModIntegration.Provider,
            List<UnifiedModIntegration.CompatibilityGap>> gapsByProvider;
    private List<ProviderSection> reportSections = List.of();
    private int measuredContentWidth = -1;
    private int measuredReportHeight = 1;
    private int windowX;
    private int windowY;
    private int windowWidth;
    private int windowHeight;
    private int contentX;
    private int contentY;
    private int contentWidth;
    private int contentHeight;
    private int scroll;
    private int maximumScroll;

    IntegrationCompatibilityScreen(Screen parent) {
        super(ModText.component("config.integration.report.title"));
        this.parent = parent;
        this.installedProviders = UnifiedModIntegration.installedExternalProviders();
        this.gapsByProvider = groupGaps(UnifiedModIntegration.compatibilityGaps());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        layout();
        graphics.fill(0, 0, width, height, AcaUiTheme.SCRIM);
        UiAnimation.push(graphics, UiAnimation.scale(openedAt), width / 2.0f, height / 2.0f);
        graphics.fill(windowX + 4, windowY + 5, windowX + windowWidth + 5,
                windowY + windowHeight + 6, 0x66000000);
        AcaUiTheme.surface(graphics, windowX, windowY, windowWidth, windowHeight, AcaUiTheme.WINDOW);
        graphics.fill(windowX + 1, windowY + 1, windowX + windowWidth - 1, windowY + 34, AcaUiTheme.HEADER);
        AcaUiTheme.button(graphics, font, "‹", windowX + 10, windowY + 8, 24, 18,
                AcaUiTheme.contains(mouseX, mouseY, windowX + 10, windowY + 8, 24, 18), false);
        drawFittedText(graphics,
                Component.literal(ModText.get("config.integration.report.title")).withStyle(ChatFormatting.BOLD),
                windowX + 42, windowY + 10, Math.max(1, windowWidth - 54), AcaUiTheme.TEXT);
        drawFittedText(graphics, Component.literal(ModText.get("config.integration.report.detail")),
                contentX, windowY + 42, contentWidth, AcaUiTheme.TEXT_MUTED);

        ensureReportLayout();
        maximumScroll = Math.max(0, measuredReportHeight - contentHeight);
        scroll = Math.clamp(scroll, 0, maximumScroll);
        graphics.enableScissor(contentX, contentY, contentX + contentWidth, contentY + contentHeight);
        if (installedProviders.isEmpty()) {
            drawCenteredWrapped(graphics, ModText.get("config.integration.report.no_providers"));
        } else if (gapsByProvider.isEmpty()) {
            drawCenteredWrapped(graphics, ModText.get("config.integration.report.all_supported"));
        } else {
            int y = contentY - scroll;
            for (ProviderSection section : reportSections) {
                drawProviderHeader(graphics, section.provider(), section.rows().size(), y);
                y += PROVIDER_HEADER_HEIGHT + ROW_GAP;
                for (GapLayout row : section.rows()) {
                    drawGapRow(graphics, row, contentX, y, contentWidth - 5);
                    y += row.height() + ROW_GAP;
                }
                y += 4;
            }
        }
        graphics.disableScissor();
        contentScrollbar.update(contentX + contentWidth + 2,
                contentY, contentHeight, maximumScroll, scroll);
        contentScrollbar.draw(graphics, mouseX, mouseY, REPORT_ACCENT);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        UiAnimation.pop(graphics);
    }

    private void layout() {
        windowWidth = Math.max(1, Math.min(560, width - Math.min(24, Math.max(0, width - 1))));
        windowHeight = Math.max(1, Math.min(390, height - Math.min(24, Math.max(0, height - 1))));
        windowX = (width - windowWidth) / 2;
        windowY = (height - windowHeight) / 2;
        contentX = windowX + 12;
        contentY = windowY + 62;
        contentWidth = Math.max(1, windowWidth - 29);
        contentHeight = Math.max(1, windowY + windowHeight - contentY - 12);
    }

    private static Map<UnifiedModIntegration.Provider,
            List<UnifiedModIntegration.CompatibilityGap>> groupGaps(
            List<UnifiedModIntegration.CompatibilityGap> gaps) {
        Map<UnifiedModIntegration.Provider, List<UnifiedModIntegration.CompatibilityGap>> sections =
                new LinkedHashMap<>();
        for (UnifiedModIntegration.CompatibilityGap gap : gaps) {
            sections.computeIfAbsent(gap.provider(), ignored -> new ArrayList<>()).add(gap);
        }
        sections.replaceAll((provider, rows) -> List.copyOf(rows));
        return Collections.unmodifiableMap(sections);
    }

    private void ensureReportLayout() {
        if (measuredContentWidth == contentWidth) return;
        measuredContentWidth = contentWidth;
        int nameWidth = Math.max(1, contentWidth - 25);
        int totalHeight = 0;
        List<ProviderSection> sections = new ArrayList<>();
        for (Map.Entry<UnifiedModIntegration.Provider,
                List<UnifiedModIntegration.CompatibilityGap>> entry : gapsByProvider.entrySet()) {
            List<GapLayout> rows = new ArrayList<>();
            totalHeight += PROVIDER_HEADER_HEIGHT + ROW_GAP;
            for (UnifiedModIntegration.CompatibilityGap gap : entry.getValue()) {
                List<FormattedCharSequence> lines = List.copyOf(font.split(
                        Component.literal(gap.feature()), nameWidth));
                int rowHeight = 13 + Math.max(1, lines.size()) * 10 + 17;
                rows.add(new GapLayout(gap, lines, rowHeight));
                totalHeight += rowHeight + ROW_GAP;
            }
            totalHeight += 4;
            sections.add(new ProviderSection(entry.getKey(), List.copyOf(rows)));
        }
        reportSections = List.copyOf(sections);
        measuredReportHeight = gapsByProvider.isEmpty()
                ? contentHeight : Math.max(1, totalHeight - ROW_GAP);
    }

    private void drawProviderHeader(GuiGraphicsExtractor graphics, UnifiedModIntegration.Provider provider,
                                    int count, int y) {
        graphics.fill(contentX, y, contentX + contentWidth - 5, y + PROVIDER_HEADER_HEIGHT, 0xFF20292D);
        graphics.outline(contentX, y, contentWidth - 5, PROVIDER_HEADER_HEIGHT, AcaUiTheme.BORDER_SOFT);
        graphics.fill(contentX, y, contentX + 3, y + PROVIDER_HEADER_HEIGHT, REPORT_ACCENT);
        graphics.text(font, Component.literal(provider.displayName).withStyle(ChatFormatting.BOLD),
                contentX + 10, y + 8, AcaUiTheme.TEXT, false);
        String value = Integer.toString(count);
        graphics.text(font, value, contentX + contentWidth - font.width(value) - 15,
                y + 8, AcaUiTheme.TEXT_DIM, false);
    }

    private void drawGapRow(GuiGraphicsExtractor graphics, GapLayout row,
                            int x, int y, int rowWidth) {
        graphics.fill(x, y, x + rowWidth, y + row.height(), AcaUiTheme.CARD);
        graphics.outline(x, y, rowWidth, row.height(), AcaUiTheme.BORDER_SOFT);
        int lineY = y + 7;
        for (FormattedCharSequence line : row.lines()) {
            graphics.text(font, line, x + 10, lineY, AcaUiTheme.TEXT, false);
            lineY += 10;
        }
        int badgeY = y + row.height() - 16;
        int badgeX = x + 10;
        if (row.gap().settings()) {
            badgeX = drawBadge(graphics, ModText.get("config.integration.report.settings"),
                    badgeX, badgeY, AcaUiTheme.ACCENT_DARK, AcaUiTheme.ACCENT);
        }
        if (row.gap().hud()) {
            badgeX = drawBadge(graphics, ModText.get("config.integration.report.hud"),
                    badgeX, badgeY, 0xFF5B3A7A, 0xFFB77AF4);
        }
        if (row.gap().classification()) {
            drawBadge(graphics, ModText.get("config.integration.report.classification"),
                    badgeX, badgeY, 0xFF634E18, 0xFFF2C14E);
        }
    }

    private int drawBadge(GuiGraphicsExtractor graphics, String label, int x, int y, int fill, int border) {
        String text = "[" + label + "]";
        int width = font.width(text) + 8;
        graphics.fill(x, y, x + width, y + 13, fill);
        graphics.outline(x, y, width, 13, border);
        graphics.text(font, text, x + 4, y + 2, AcaUiTheme.TEXT, false);
        return x + width + 5;
    }

    private void drawCenteredWrapped(GuiGraphicsExtractor graphics, String text) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text), Math.max(1, contentWidth - 30));
        int y = contentY + Math.max(0, (contentHeight - lines.size() * 11) / 2);
        for (FormattedCharSequence line : lines) {
            graphics.centeredText(font, line, contentX + contentWidth / 2, y, AcaUiTheme.TEXT_MUTED);
            y += 11;
        }
    }

    private void drawFittedText(GuiGraphicsExtractor graphics, Component text, int x, int y,
                                int availableWidth, int color) {
        if (availableWidth <= 0) return;
        int measured = font.width(text);
        if (measured <= availableWidth) {
            graphics.text(font, text, x, y, color, false);
            return;
        }
        float scale = availableWidth / (float) Math.max(1, measured);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y + Math.round((1.0f - scale) * 4.0f));
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, 0, 0, color, false);
        graphics.pose().popMatrix();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if ((click.button() == 0 || click.button() == 1)
                && AcaUiTheme.contains(click.x(), click.y(), windowX + 10, windowY + 8, 24, 18)) {
            onClose();
            return true;
        }
        VerticalScrollbar.Interaction scrollbarClick = contentScrollbar.mouseClicked(
                click.button(), click.x(), click.y(), scroll);
        if (scrollbarClick.consumed()) {
            scroll = scrollbarClick.scroll();
            return true;
        }
        return super.mouseClicked(click, doubled);
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (contentScrollbar.dragging()) {
            VerticalScrollbar.Interaction scrollbarWheel = contentScrollbar.mouseScrolled(vertical, 24, scroll);
            scroll = scrollbarWheel.scroll();
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
        MinecraftClientCompat.setScreen(minecraft, parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record ProviderSection(UnifiedModIntegration.Provider provider, List<GapLayout> rows) { }

    private record GapLayout(UnifiedModIntegration.CompatibilityGap gap,
                             List<FormattedCharSequence> lines, int height) { }
}

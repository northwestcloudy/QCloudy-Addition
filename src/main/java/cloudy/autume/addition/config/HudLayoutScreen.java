package cloudy.autume.addition.config;

import cloudy.autume.addition.compat.MinecraftClientCompat;
import cloudy.autume.addition.hud.HudRenderer;
import cloudy.autume.addition.i18n.ModText;
import cloudy.autume.addition.tracker.IslandArea;
import cloudy.autume.addition.tracker.LocationTracker;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HudLayoutScreen extends Screen {
    private static final int TOOLBAR_WIDTH = 390;
    private static final int TOOLBAR_HEIGHT = 30;
    private static final int EXTERNAL_WIDTH = 150;
    private static final int EXTERNAL_HEIGHT = 38;
    private final Screen parent;
    private List<UnifiedModIntegration.ExternalHud> externalHuds = List.of();
    private final Map<String, ExternalPreview> externalPreviews = new HashMap<>();
    private Panel selected;
    private Panel dragging;
    private Panel resizing;
    private UnifiedModIntegration.ExternalHud selectedExternal;
    private UnifiedModIntegration.ExternalHud draggingExternal;
    private UnifiedModIntegration.ExternalHud resizingExternal;
    private ResizeEdge resizeEdge = ResizeEdge.NONE;
    private double dragOffsetX;
    private double dragOffsetY;
    private double resizeStartX;
    private double resizeStartY;
    private int resizePanelX;
    private int resizePanelY;
    private int resizePanelRight;
    private int resizePanelBottom;
    private float resizeStartScale;

    public HudLayoutScreen(Screen parent) {
        super(ModText.component("config.layout"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        externalHuds = UnifiedModIntegration.externalHuds();
        externalPreviews.clear();
        for (UnifiedModIntegration.ExternalHud hud : externalHuds) {
            externalPreviews.put(hud.id(), new ExternalPreview(
                    resolveExternalX(hud.x(), externalScaledWidth(hud)),
                    resolveExternalY(hud.y(), externalScaledHeight(hud)), hud.scale()));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0x6E05090B);
        drawGrid(graphics);
        for (Panel panel : Panel.values()) drawPreview(graphics, panel, mouseX, mouseY);
        for (UnifiedModIntegration.ExternalHud hud : externalHuds) drawExternalPreview(graphics, hud, mouseX, mouseY);
        drawToolbar(graphics, mouseX, mouseY);
        drawStatus(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void drawGrid(GuiGraphicsExtractor graphics) {
        for (int x = 0; x < width; x += 24) graphics.fill(x, 0, x + 1, height, 0x183E4B51);
        for (int y = 0; y < height; y += 24) graphics.fill(0, y, width, y + 1, 0x183E4B51);
        graphics.fill(width / 2, 0, width / 2 + 1, height, 0x5533B8E5);
        graphics.fill(0, height / 2, width, height / 2 + 1, 0x5533B8E5);
    }

    private void drawToolbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int toolbarWidth = Math.max(1, Math.min(TOOLBAR_WIDTH, width - Math.min(16, Math.max(0, width - 1))));
        int x = (width - toolbarWidth) / 2;
        int y = 8;
        graphics.fill(x + 3, y + 3, x + toolbarWidth + 3, y + TOOLBAR_HEIGHT + 3, 0x66000000);
        AcaUiTheme.surface(graphics, x, y, toolbarWidth, TOOLBAR_HEIGHT, AcaUiTheme.HEADER);
        int backWidth = Math.min(64, Math.max(1, toolbarWidth / 5));
        int resetWidth = Math.min(82, Math.max(1, toolbarWidth / 4));
        int backX = x + toolbarWidth - backWidth - 7;
        int resetX = backX - resetWidth - 5;
        drawFitted(graphics, Component.literal(ModText.get("config.layout")).withStyle(ChatFormatting.BOLD),
                x + 10, y + 6, Math.max(1, resetX - x - 16), AcaUiTheme.TEXT);
        drawFitted(graphics, Component.literal(ModText.get("config.layout.loaded", loadedCount())),
                x + 10, y + 17, Math.max(1, resetX - x - 16), AcaUiTheme.TEXT_DIM);
        AcaUiTheme.button(graphics, font, ModText.get("config.back"), backX, y + 6, backWidth, 18,
                AcaUiTheme.contains(mouseX, mouseY, backX, y + 6, backWidth, 18), false);
        AcaUiTheme.button(graphics, font, ModText.get("config.reset_selected"), resetX, y + 6, resetWidth, 18,
                AcaUiTheme.contains(mouseX, mouseY, resetX, y + 6, resetWidth, 18), false);
    }

    private void drawStatus(GuiGraphicsExtractor graphics) {
        boolean anyLoaded = loadedCount() > 0;
        String message = anyLoaded ? ModText.get("config.layout_help") : ModText.get("config.layout_none");
        int textWidth = Math.min(font.width(message), Math.max(1, width - 34));
        int x = Math.max(8, (width - textWidth - 18) / 2);
        int y = height - 24;
        graphics.fill(x, y, x + textWidth + 18, y + 17, AcaUiTheme.HEADER);
        graphics.outline(x, y, textWidth + 18, 17, AcaUiTheme.BORDER);
        drawFitted(graphics, Component.literal(message), x + 9, y + 4, textWidth,
                anyLoaded ? AcaUiTheme.TEXT_MUTED : 0xFFFFC766);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            if (toolbarButtonClicked(click.x(), click.y())) return true;
            UnifiedModIntegration.ExternalHud externalSettings = externalSettingsAt(click.x(), click.y());
            if (externalSettings != null) {
                MinecraftClientCompat.setScreen(minecraft, new FeatureSettingsScreen(this, externalSettings.feature));
                return true;
            }
            Panel settingsPanel = settingsAt(click.x(), click.y());
            if (settingsPanel != null) {
                if (settingsPanel == Panel.HUNTING) {
                    ConfigScreen.HudFocus focus = switch (LocationTracker.area()) {
                        case TORRHUS_CANYON -> ConfigScreen.HudFocus.FORAGING;
                        case CRITTER_SAFARI -> ConfigScreen.HudFocus.SAFARI;
                        default -> ConfigScreen.HudFocus.HUNTING;
                    };
                    MinecraftClientCompat.setScreen(minecraft, new ConfigScreen(this, focus));
                } else {
                    ConfigScreen.Feature feature = settingsPanel.feature();
                    if (feature != null) {
                        MinecraftClientCompat.setScreen(minecraft, new FeatureSettingsScreen(this, feature));
                    }
                }
                return true;
            }
            UnifiedModIntegration.ExternalHud external = externalAt(click.x(), click.y());
            if (external != null) {
                selected = null;
                selectedExternal = external;
                ExternalPreview preview = preview(external);
                if (externalResizeAt(external, click.x(), click.y())) {
                    resizingExternal = external;
                    resizeStartX = click.x();
                    resizeStartY = click.y();
                    resizeStartScale = preview.scale;
                } else {
                    draggingExternal = external;
                    dragOffsetX = click.x() - preview.x;
                    dragOffsetY = click.y() - preview.y;
                }
                return true;
            }
            Panel panel = panelAt(click.x(), click.y());
            selected = panel;
            selectedExternal = null;
            if (panel != null) {
                ResizeEdge edge = resizeAt(panel, click.x(), click.y());
                if (edge != ResizeEdge.NONE) {
                    resizing = panel;
                    resizeEdge = edge;
                    resizeStartX = click.x();
                    resizeStartY = click.y();
                    resizePanelX = panelX(panel);
                    resizePanelY = panelY(panel);
                    resizePanelRight = resizePanelX + scaledWidth(panel);
                    resizePanelBottom = resizePanelY + scaledHeight(panel);
                    resizeStartScale = previewScale(panel);
                    return true;
                }
                dragging = panel;
                dragOffsetX = click.x() - panelX(panel);
                dragOffsetY = click.y() - panelY(panel);
                return true;
            }
        }
        if (click.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            UnifiedModIntegration.ExternalHud external = externalAt(click.x(), click.y());
            if (external != null) {
                selectedExternal = external;
                selected = null;
                MinecraftClientCompat.setScreen(minecraft, new FeatureSettingsScreen(this, external.feature));
                return true;
            }
            Panel panel = panelAt(click.x(), click.y());
            if (panel != null) {
                selected = panel;
                resetPosition(panel);
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private boolean toolbarButtonClicked(double mouseX, double mouseY) {
        int toolbarWidth = Math.max(1, Math.min(TOOLBAR_WIDTH, width - Math.min(16, Math.max(0, width - 1))));
        int x = (width - toolbarWidth) / 2;
        int y = 8;
        int backWidth = Math.min(64, Math.max(1, toolbarWidth / 5));
        int resetWidth = Math.min(82, Math.max(1, toolbarWidth / 4));
        int backX = x + toolbarWidth - backWidth - 7;
        int resetX = backX - resetWidth - 5;
        if (AcaUiTheme.contains(mouseX, mouseY, backX, y + 6, backWidth, 18)) {
            onClose();
            return true;
        }
        if (AcaUiTheme.contains(mouseX, mouseY, resetX, y + 6, resetWidth, 18) && selected != null) {
            resetPosition(selected);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        if (resizingExternal != null && click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            ExternalPreview preview = preview(resizingExternal);
            double delta = Math.max(click.x() - resizeStartX, click.y() - resizeStartY);
            preview.scale = Math.round(Math.clamp(resizeStartScale + (float) delta / EXTERNAL_WIDTH,
                    0.25f, 4.0f) * 100.0f) / 100.0f;
            preview.x = Math.clamp(preview.x, 0, Math.max(0, width - externalScaledWidth(resizingExternal)));
            preview.y = Math.clamp(preview.y, 0, Math.max(0, height - externalScaledHeight(resizingExternal)));
            return true;
        }
        if (draggingExternal != null && click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            ExternalPreview preview = preview(draggingExternal);
            int panelWidth = externalScaledWidth(draggingExternal);
            int panelHeight = externalScaledHeight(draggingExternal);
            preview.x = snap((int) Math.clamp(click.x() - dragOffsetX, 0, Math.max(0, width - panelWidth)));
            preview.y = snap((int) Math.clamp(click.y() - dragOffsetY, 0, Math.max(0, height - panelHeight)));
            return true;
        }
        if (resizing != null && click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            resizePanel(click.x(), click.y());
            return true;
        }
        if (dragging == null || click.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseDragged(click, offsetX, offsetY);
        }
        int panelWidth = scaledWidth(dragging);
        int panelHeight = scaledHeight(dragging);
        int x = snap((int) Math.clamp(click.x() - dragOffsetX, 0, Math.max(0, width - panelWidth)));
        int y = snap((int) Math.clamp(click.y() - dragOffsetY, 0, Math.max(0, height - panelHeight)));
        setPosition(dragging, x, y);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (resizingExternal != null && click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            ExternalPreview preview = preview(resizingExternal);
            resizingExternal.setScale(preview.scale);
            resizingExternal.setPosition(preview.x, preview.y);
            resizingExternal = null;
            return true;
        }
        if (draggingExternal != null && click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            ExternalPreview preview = preview(draggingExternal);
            draggingExternal.setPosition(preview.x, preview.y);
            draggingExternal = null;
            return true;
        }
        if (resizing != null && click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            resizing = null;
            resizeEdge = ResizeEdge.NONE;
            ConfigManager.save();
            return true;
        }
        if (dragging != null && click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            dragging = null;
            ConfigManager.save();
            return true;
        }
        return super.mouseReleased(click);
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

    private void drawPreview(GuiGraphicsExtractor graphics, Panel panel, int mouseX, int mouseY) {
        if (!isLoaded(panel)) return;
        int x = panelX(panel);
        int y = panelY(panel);
        int panelWidth = scaledWidth(panel);
        int panelHeight = scaledHeight(panel);
        Matrix3x2fStack matrices = graphics.pose();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(previewScale(panel), previewScale(panel));
        HudRenderer.renderEditorPreview(graphics, panel.previewPanel);
        matrices.popMatrix();

        boolean hovered = AcaUiTheme.contains(mouseX, mouseY, x, y, panelWidth, panelHeight);
        int outline = panel == selected ? AcaUiTheme.ACCENT : hovered ? AcaUiTheme.ACCENT_DARK : AcaUiTheme.BORDER;
        graphics.outline(x - 1, y - 1, panelWidth + 2, panelHeight + 2, outline);
        if (panel == selected) graphics.outline(x - 2, y - 2, panelWidth + 4, panelHeight + 4, 0xAA28BCEB);
        if (panel == selected || hovered) drawResizeHandles(graphics, x, y, panelWidth, panelHeight, mouseX, mouseY, panel);

        String label = panel.label();
        int labelWidth = Math.min(panelWidth, font.width(label) + 12);
        int labelY = y >= 15 ? y - 15 : y;
        graphics.fill(x, labelY, x + labelWidth, labelY + 15,
                panel == selected ? AcaUiTheme.ACCENT_DARK : AcaUiTheme.HEADER);
        graphics.outline(x, labelY, labelWidth, 15, outline);
        drawFitted(graphics, Component.literal(label), x + 6, labelY + 3,
                Math.max(1, labelWidth - 12), AcaUiTheme.TEXT);

        if (panel.hasSettings()) {
            int gearX = x + panelWidth - 18;
            int gearY = y + panelHeight - 18;
            boolean gearHovered = AcaUiTheme.contains(mouseX, mouseY, gearX, gearY, 16, 16);
            graphics.fill(gearX, gearY, gearX + 16, gearY + 16,
                    gearHovered ? AcaUiTheme.ACCENT_DARK : AcaUiTheme.HEADER);
            graphics.outline(gearX, gearY, 16, 16, gearHovered ? AcaUiTheme.ACCENT : AcaUiTheme.BORDER);
            graphics.centeredText(font, "⚙", gearX + 8, gearY + 3, AcaUiTheme.TEXT);
        }
    }

    private int loadedCount() {
        int count = 0;
        for (Panel panel : Panel.values()) if (isLoaded(panel)) count++;
        return count + externalHuds.size();
    }

    private void drawFitted(GuiGraphicsExtractor graphics, Component text, int x, int y,
                            int availableWidth, int color) {
        if (availableWidth <= 0) return;
        int textWidth = Math.max(1, font.width(text));
        float scale = Math.min(1.0f, availableWidth / (float) textWidth);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y + Math.round((1.0f - scale) * 4.0f));
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, 0, 0, color, false);
        graphics.pose().popMatrix();
    }

    private void drawExternalPreview(GuiGraphicsExtractor graphics, UnifiedModIntegration.ExternalHud hud,
                                     int mouseX, int mouseY) {
        ExternalPreview preview = preview(hud);
        int panelWidth = externalScaledWidth(hud);
        int panelHeight = externalScaledHeight(hud);
        boolean hovered = AcaUiTheme.contains(mouseX, mouseY, preview.x, preview.y, panelWidth, panelHeight);
        boolean selected = hud == selectedExternal;
        int outline = selected ? AcaUiTheme.ACCENT : hovered ? AcaUiTheme.ACCENT_DARK : AcaUiTheme.BORDER;
        graphics.fill(preview.x + 3, preview.y + 3, preview.x + panelWidth + 3,
                preview.y + panelHeight + 3, 0x55000000);
        graphics.fill(preview.x, preview.y, preview.x + panelWidth, preview.y + panelHeight, AcaUiTheme.CARD);
        graphics.outline(preview.x, preview.y, panelWidth, panelHeight, outline);
        if (selected) graphics.outline(preview.x - 1, preview.y - 1, panelWidth + 2, panelHeight + 2, 0xAA28BCEB);
        String label = hud.label();
        float textScale = Math.min(1.0f, Math.max(0.45f,
                (panelWidth - 16.0f) / Math.max(1, font.width(label))));
        graphics.pose().pushMatrix();
        graphics.pose().translate(preview.x + 8, preview.y + 9);
        graphics.pose().scale(textScale, textScale);
        graphics.text(font, label, 0, 0, AcaUiTheme.TEXT, false);
        graphics.pose().popMatrix();
        drawFitted(graphics, Component.literal(ModText.get("config.integration.live_hud")), preview.x + 8,
                preview.y + panelHeight - 13, Math.max(1, panelWidth - 16), AcaUiTheme.TEXT_DIM);

        int gearX = preview.x + panelWidth - 17;
        int gearY = preview.y + panelHeight - 17;
        graphics.fill(gearX, gearY, gearX + 15, gearY + 15,
                AcaUiTheme.contains(mouseX, mouseY, gearX, gearY, 15, 15)
                        ? AcaUiTheme.ACCENT_DARK : AcaUiTheme.HEADER);
        graphics.outline(gearX, gearY, 15, 15, AcaUiTheme.BORDER);
        graphics.centeredText(font, "⚙", gearX + 7, gearY + 3, AcaUiTheme.TEXT);
        if (hud.scale != null && (selected || hovered)) {
            graphics.fill(preview.x + panelWidth - 6, preview.y + panelHeight - 6,
                    preview.x + panelWidth + 2, preview.y + panelHeight + 2, AcaUiTheme.ACCENT);
        }
    }

    private UnifiedModIntegration.@Nullable ExternalHud externalAt(double x, double y) {
        for (int index = externalHuds.size() - 1; index >= 0; index--) {
            UnifiedModIntegration.ExternalHud hud = externalHuds.get(index);
            ExternalPreview preview = preview(hud);
            if (AcaUiTheme.contains(x, y, preview.x, preview.y,
                    externalScaledWidth(hud), externalScaledHeight(hud))) return hud;
        }
        return null;
    }

    private UnifiedModIntegration.@Nullable ExternalHud externalSettingsAt(double x, double y) {
        for (UnifiedModIntegration.ExternalHud hud : externalHuds) {
            ExternalPreview preview = preview(hud);
            int gearX = preview.x + externalScaledWidth(hud) - 17;
            int gearY = preview.y + externalScaledHeight(hud) - 17;
            if (AcaUiTheme.contains(x, y, gearX, gearY, 15, 15)) return hud;
        }
        return null;
    }

    private boolean externalResizeAt(UnifiedModIntegration.ExternalHud hud, double x, double y) {
        if (hud.scale == null) return false;
        ExternalPreview preview = preview(hud);
        int right = preview.x + externalScaledWidth(hud);
        int bottom = preview.y + externalScaledHeight(hud);
        return Math.abs(x - right) <= 7 && Math.abs(y - bottom) <= 7;
    }

    private ExternalPreview preview(UnifiedModIntegration.ExternalHud hud) {
        return externalPreviews.computeIfAbsent(hud.id(), ignored -> new ExternalPreview(
                resolveExternalX(hud.x(), Math.max(1, Math.round(EXTERNAL_WIDTH * hud.scale()))),
                resolveExternalY(hud.y(), Math.max(1, Math.round(EXTERNAL_HEIGHT * hud.scale()))), hud.scale()));
    }

    private int externalScaledWidth(UnifiedModIntegration.ExternalHud hud) {
        return Math.max(1, Math.round(EXTERNAL_WIDTH * preview(hud).scale));
    }

    private int externalScaledHeight(UnifiedModIntegration.ExternalHud hud) {
        return Math.max(1, Math.round(EXTERNAL_HEIGHT * preview(hud).scale));
    }

    private int resolveExternalX(int configured, int panelWidth) {
        return Math.clamp(configured < 0 ? width + configured - panelWidth : configured,
                0, Math.max(0, width - panelWidth));
    }

    private int resolveExternalY(int configured, int panelHeight) {
        return Math.clamp(configured < 0 ? height + configured - panelHeight : configured,
                0, Math.max(0, height - panelHeight));
    }

    private Panel panelAt(double x, double y) {
        Panel[] values = Panel.values();
        for (int index = values.length - 1; index >= 0; index--) {
            Panel panel = values[index];
            if (isLoaded(panel)
                    && AcaUiTheme.contains(x, y, panelX(panel), panelY(panel), scaledWidth(panel), scaledHeight(panel))) {
                return panel;
            }
        }
        return null;
    }

    private Panel settingsAt(double x, double y) {
        Panel[] values = Panel.values();
        for (int index = values.length - 1; index >= 0; index--) {
            Panel panel = values[index];
            if (!isLoaded(panel) || !panel.hasSettings()) continue;
            int gearX = panelX(panel) + scaledWidth(panel) - 18;
            int gearY = panelY(panel) + scaledHeight(panel) - 18;
            if (AcaUiTheme.contains(x, y, gearX, gearY, 16, 16)) return panel;
        }
        return null;
    }

    private void drawResizeHandles(GuiGraphicsExtractor graphics, int x, int y, int panelWidth, int panelHeight,
                                   int mouseX, int mouseY, Panel panel) {
        int color = resizeAt(panel, mouseX, mouseY) == ResizeEdge.NONE ? AcaUiTheme.ACCENT_DARK : AcaUiTheme.ACCENT;
        int size = 5;
        graphics.fill(x - 2, y - 2, x + size, y + size, color);
        graphics.fill(x + panelWidth - size, y - 2, x + panelWidth + 2, y + size, color);
        graphics.fill(x - 2, y + panelHeight - size, x + size, y + panelHeight + 2, color);
        graphics.fill(x + panelWidth - size, y + panelHeight - size,
                x + panelWidth + 2, y + panelHeight + 2, color);
    }

    private ResizeEdge resizeAt(Panel panel, double mouseX, double mouseY) {
        if (!isLoaded(panel)) return ResizeEdge.NONE;
        int x = panelX(panel);
        int y = panelY(panel);
        int right = x + scaledWidth(panel);
        int bottom = y + scaledHeight(panel);
        int margin = 6;
        if (mouseX < x - margin || mouseX > right + margin || mouseY < y - margin || mouseY > bottom + margin) {
            return ResizeEdge.NONE;
        }
        boolean west = Math.abs(mouseX - x) <= margin;
        boolean east = Math.abs(mouseX - right) <= margin;
        boolean north = Math.abs(mouseY - y) <= margin;
        boolean south = Math.abs(mouseY - bottom) <= margin;
        if (north && west) return ResizeEdge.NORTH_WEST;
        if (north && east) return ResizeEdge.NORTH_EAST;
        if (south && west) return ResizeEdge.SOUTH_WEST;
        if (south && east) return ResizeEdge.SOUTH_EAST;
        if (north) return ResizeEdge.NORTH;
        if (south) return ResizeEdge.SOUTH;
        if (west) return ResizeEdge.WEST;
        if (east) return ResizeEdge.EAST;
        return ResizeEdge.NONE;
    }

    private void resizePanel(double mouseX, double mouseY) {
        int baseWidth = baseWidth(resizing);
        int baseHeight = baseHeight(resizing);
        double dx = mouseX - resizeStartX;
        double dy = mouseY - resizeStartY;
        float horizontal = resizeStartScale;
        float vertical = resizeStartScale;
        if (resizeEdge.west) horizontal = (float) ((resizePanelRight - resizePanelX - dx) / baseWidth);
        if (resizeEdge.east) horizontal = (float) ((resizePanelRight - resizePanelX + dx) / baseWidth);
        if (resizeEdge.north) vertical = (float) ((resizePanelBottom - resizePanelY - dy) / baseHeight);
        if (resizeEdge.south) vertical = (float) ((resizePanelBottom - resizePanelY + dy) / baseHeight);
        float candidate;
        if ((resizeEdge.west || resizeEdge.east) && (resizeEdge.north || resizeEdge.south)) {
            double horizontalChange = Math.abs(horizontal - resizeStartScale);
            double verticalChange = Math.abs(vertical - resizeStartScale);
            candidate = horizontalChange >= verticalChange ? horizontal : vertical;
        } else candidate = (resizeEdge.west || resizeEdge.east) ? horizontal : vertical;
        float scale = Math.round(Math.clamp(candidate, 0.5f, 2.0f) * 100.0f) / 100.0f;
        panelStyle(resizing).scale = scale;
        int newWidth = scaledWidth(resizing);
        int newHeight = scaledHeight(resizing);
        int x = resizeEdge.west ? resizePanelRight - newWidth : resizePanelX;
        int y = resizeEdge.north ? resizePanelBottom - newHeight : resizePanelY;
        setPosition(resizing, Math.clamp(x, 0, Math.max(0, width - newWidth)),
                Math.clamp(y, 0, Math.max(0, height - newHeight)));
    }

    private static boolean isLoaded(Panel panel) {
        ModConfig config = ConfigManager.get();
        return switch (panel) {
            case MAP -> HudRenderer.isMapLoaded();
            case MINING -> HudRenderer.isMiningLoaded();
            case HUNTING -> HudRenderer.isHuntingLoaded();
            case PET -> HudRenderer.isPetLoaded();
            case SPIRIT_MASK_COOLDOWN -> config.combat.spiritMaskCooldownHud;
            case BONZO_MASK_COOLDOWN -> config.combat.bonzoMaskCooldownHud;
            case PHOENIX_COOLDOWN -> config.combat.phoenixCooldownHud;
        };
    }

    private int panelX(Panel panel) {
        ModConfig.HudStyle style = ConfigManager.get().hudStyle;
        int configured = switch (panel) {
            case MAP -> style.mapX;
            case MINING -> style.miningX;
            case HUNTING -> style.huntingX;
            case PET -> style.petX;
            case SPIRIT_MASK_COOLDOWN -> style.spiritMaskCooldownX;
            case BONZO_MASK_COOLDOWN -> style.bonzoMaskCooldownX;
            case PHOENIX_COOLDOWN -> style.phoenixCooldownX;
        };
        return HudRenderer.resolveX(configured, baseWidth(panel), width, previewScale(panel));
    }

    private int panelY(Panel panel) {
        ModConfig.HudStyle style = ConfigManager.get().hudStyle;
        int configured = switch (panel) {
            case MAP -> style.mapY;
            case MINING -> style.miningY;
            case HUNTING -> style.huntingY;
            case PET -> style.petY;
            case SPIRIT_MASK_COOLDOWN -> style.spiritMaskCooldownY;
            case BONZO_MASK_COOLDOWN -> style.bonzoMaskCooldownY;
            case PHOENIX_COOLDOWN -> style.phoenixCooldownY;
        };
        return Math.clamp(configured, 0, Math.max(0, height - scaledHeight(panel)));
    }

    private static void setPosition(Panel panel, int x, int y) {
        ModConfig.HudStyle style = ConfigManager.get().hudStyle;
        switch (panel) {
            case MAP -> { style.mapX = x; style.mapY = y; }
            case MINING -> { style.miningX = x; style.miningY = y; }
            case HUNTING -> { style.huntingX = x; style.huntingY = y; }
            case PET -> { style.petX = x; style.petY = y; }
            case SPIRIT_MASK_COOLDOWN -> {
                style.spiritMaskCooldownX = x;
                style.spiritMaskCooldownY = y;
            }
            case BONZO_MASK_COOLDOWN -> {
                style.bonzoMaskCooldownX = x;
                style.bonzoMaskCooldownY = y;
            }
            case PHOENIX_COOLDOWN -> {
                style.phoenixCooldownX = x;
                style.phoenixCooldownY = y;
            }
        }
    }

    private static float previewScale(Panel panel) {
        return panelStyle(panel).scale;
    }

    private static int scaledWidth(Panel panel) {
        return Math.max(1, (int) Math.ceil(baseWidth(panel) * previewScale(panel)));
    }

    private static int scaledHeight(Panel panel) {
        return Math.max(1, (int) Math.ceil(baseHeight(panel) * previewScale(panel)));
    }

    private static int baseWidth(Panel panel) {
        return switch (panel) {
            case MAP -> HudRenderer.MAP_SIZE;
            case MINING -> HudRenderer.MINING_WIDTH;
            case HUNTING -> cloudy.autume.addition.hud.HuntingHudRenderer.WIDTH;
            case PET -> HudRenderer.currentPetWidth();
            case SPIRIT_MASK_COOLDOWN, BONZO_MASK_COOLDOWN, PHOENIX_COOLDOWN ->
                    HudRenderer.DEATH_SAVE_COOLDOWN_WIDTH;
        };
    }

    private static int baseHeight(Panel panel) {
        return switch (panel) {
            case MAP -> HudRenderer.MAP_PANEL_HEIGHT;
            case MINING -> HudRenderer.currentMiningHeight();
            case HUNTING -> cloudy.autume.addition.hud.HuntingHudRenderer.currentHeight();
            case PET -> HudRenderer.currentPetHeight();
            case SPIRIT_MASK_COOLDOWN, BONZO_MASK_COOLDOWN, PHOENIX_COOLDOWN ->
                    HudRenderer.DEATH_SAVE_COOLDOWN_HEIGHT;
        };
    }

    private static int snap(int value) {
        return Math.round(value / 4.0f) * 4;
    }

    private static void resetPosition(Panel panel) {
        ModConfig.HudStyle style = ConfigManager.get().hudStyle;
        switch (panel) {
            case MAP -> { style.mapX = 8; style.mapY = 8; }
            case MINING -> { style.miningX = -196; style.miningY = 8; }
            case HUNTING -> { style.huntingX = -304; style.huntingY = 8; }
            case PET -> { style.petX = 8; style.petY = 196; }
            case SPIRIT_MASK_COOLDOWN -> {
                style.spiritMaskCooldownX = -196;
                style.spiritMaskCooldownY = 196;
            }
            case BONZO_MASK_COOLDOWN -> {
                style.bonzoMaskCooldownX = -196;
                style.bonzoMaskCooldownY = 236;
            }
            case PHOENIX_COOLDOWN -> {
                style.phoenixCooldownX = -196;
                style.phoenixCooldownY = 276;
            }
        }
        panelStyle(panel).scale = 1.0f;
        ConfigManager.save();
    }

    private static ModConfig.PanelStyle panelStyle(Panel panel) {
        return ConfigManager.get().hudStyle.style(panel.hudType);
    }

    private enum Panel {
        MAP(ModConfig.HudType.MAP, HudRenderer.PreviewPanel.MAP, "hud.map"),
        MINING(ModConfig.HudType.MINING, HudRenderer.PreviewPanel.MINING, "hud.mining"),
        HUNTING(ModConfig.HudType.HUNTING, HudRenderer.PreviewPanel.HUNTING, "hud.hunting"),
        PET(ModConfig.HudType.PET, HudRenderer.PreviewPanel.PET, "hud.pet"),
        SPIRIT_MASK_COOLDOWN(ModConfig.HudType.SPIRIT_MASK_COOLDOWN,
                HudRenderer.PreviewPanel.SPIRIT_MASK_COOLDOWN, "hud.death_save.spirit_mask"),
        BONZO_MASK_COOLDOWN(ModConfig.HudType.BONZO_MASK_COOLDOWN,
                HudRenderer.PreviewPanel.BONZO_MASK_COOLDOWN, "hud.death_save.bonzo_mask"),
        PHOENIX_COOLDOWN(ModConfig.HudType.PHOENIX_COOLDOWN,
                HudRenderer.PreviewPanel.PHOENIX_COOLDOWN, "hud.death_save.phoenix");

        private final ModConfig.HudType hudType;
        private final HudRenderer.PreviewPanel previewPanel;
        private final String labelKey;

        Panel(ModConfig.HudType hudType, HudRenderer.PreviewPanel previewPanel, String labelKey) {
            this.hudType = hudType;
            this.previewPanel = previewPanel;
            this.labelKey = labelKey;
        }

        String label() {
            if (this == MINING && LocationTracker.area() == IslandArea.CRIMSON_ISLE) {
                return ModText.get("hud.crimson_tasks");
            }
            return ModText.get(labelKey);
        }

        ConfigScreen.@Nullable Feature feature() {
            return switch (this) {
                case MAP -> LocationTracker.area() == IslandArea.GLACITE_TUNNELS
                        ? ConfigScreen.Feature.GLACITE_MAP : ConfigScreen.Feature.DWARVEN_MAP;
                case MINING -> LocationTracker.area() == IslandArea.CRIMSON_ISLE
                        ? ConfigScreen.Feature.CRIMSON_TASKS : ConfigScreen.Feature.MINING_TRACKER;
                case HUNTING -> LocationTracker.area() == IslandArea.CRITTER_SAFARI
                        ? ConfigScreen.Feature.SAFARI_DASHBOARD
                        : LocationTracker.area() == IslandArea.GALATEA
                        ? ConfigScreen.Feature.GALATEA_TRACKER : ConfigScreen.Feature.TORRHUS_TRACKER;
                case PET -> ConfigScreen.Feature.PET_HUD;
                case SPIRIT_MASK_COOLDOWN -> ConfigScreen.Feature.SPIRIT_MASK_COOLDOWN_HUD;
                case BONZO_MASK_COOLDOWN -> ConfigScreen.Feature.BONZO_MASK_COOLDOWN_HUD;
                case PHOENIX_COOLDOWN -> ConfigScreen.Feature.PHOENIX_COOLDOWN_HUD;
            };
        }

        boolean hasSettings() {
            return true;
        }
    }

    private enum ResizeEdge {
        NONE(false, false, false, false),
        NORTH(false, false, true, false),
        SOUTH(false, false, false, true),
        WEST(true, false, false, false),
        EAST(false, true, false, false),
        NORTH_WEST(true, false, true, false),
        NORTH_EAST(false, true, true, false),
        SOUTH_WEST(true, false, false, true),
        SOUTH_EAST(false, true, false, true);

        final boolean west;
        final boolean east;
        final boolean north;
        final boolean south;

        ResizeEdge(boolean west, boolean east, boolean north, boolean south) {
            this.west = west;
            this.east = east;
            this.north = north;
            this.south = south;
        }
    }

    private static final class ExternalPreview {
        int x;
        int y;
        float scale;

        ExternalPreview(int x, int y, float scale) {
            this.x = x;
            this.y = y;
            this.scale = scale;
        }
    }
}

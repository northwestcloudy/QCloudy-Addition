package cloudy.autume.addition.hud;

import cloudy.autume.addition.combat.DeathSaveAlertManager;
import cloudy.autume.addition.compat.MinecraftClientCompat;
import cloudy.autume.addition.config.ConfigManager;
import cloudy.autume.addition.config.ModConfig;
import cloudy.autume.addition.i18n.ModText;
import cloudy.autume.addition.tracker.IslandArea;
import cloudy.autume.addition.tracker.HotmSlotTracker;
import cloudy.autume.addition.tracker.LocationTracker;
import cloudy.autume.addition.tracker.PetTracker;
import cloudy.autume.addition.tracker.PetLeveling;
import cloudy.autume.addition.tracker.PetSkinTracker;
import cloudy.autume.addition.tracker.TabListTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;

import java.util.List;
import java.util.Locale;

public final class HudRenderer {
    public static final int MAP_SIZE = 200;
    public static final int MAP_PANEL_HEIGHT = MAP_SIZE + 29;
    public static final int MINING_WIDTH = 228;
    public static final int PET_WIDTH = 188;
    public static final int PET_PANEL_HEIGHT = 55;
    public static final int DEATH_SAVE_COOLDOWN_WIDTH = 188;
    public static final int DEATH_SAVE_COOLDOWN_HEIGHT = 32;
    private static final int MAP_MARGIN = 12;
    private static final Identifier DWARVEN_MAP = id("textures/gui/dwarven_mines.png");
    private static final Identifier GLACITE_LOW = id("textures/gui/glacite_tunnels_low.png");
    private static final Identifier GLACITE_MIDDLE = id("textures/gui/glacite_tunnels_middle.png");
    private static final Identifier GLACITE_HIGH = id("textures/gui/glacite_tunnels_high.png");
    private static final Identifier PLAYER_ARROW = id("textures/gui/player_arrow.png");

    private HudRenderer() {
    }

    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || MinecraftClientCompat.isHudHidden(client)) return;
        var config = ConfigManager.get();
        renderDeathSaveCooldowns(graphics, config);

        if (!LocationTracker.isSkyBlock()) return;

        if (isMapLoaded()) {
            float scale = style(ModConfig.HudType.MAP).scale;
            int x = resolveX(config.hudStyle.mapX, MAP_SIZE, graphics.guiWidth(), scale);
            int y = resolveY(config.hudStyle.mapY, MAP_PANEL_HEIGHT, graphics.guiHeight(), scale);
            renderScaled(graphics, x, y, scale, () -> renderMap(graphics, client));
        }

        if (isMiningLoaded()) {
            float scale = style(ModConfig.HudType.MINING).scale;
            int height = currentMiningHeight();
            int x = resolveX(config.hudStyle.miningX, MINING_WIDTH, graphics.guiWidth(), scale);
            int y = resolveY(config.hudStyle.miningY, height, graphics.guiHeight(), scale);
            renderScaled(graphics, x, y, scale, () -> {
                if (LocationTracker.area() == IslandArea.CRIMSON_ISLE) renderCrimsonTasks(graphics, height);
                else renderMining(graphics, height);
            });
        }

        if (HuntingHudRenderer.loaded()) {
            float scale = style(ModConfig.HudType.HUNTING).scale;
            int height = HuntingHudRenderer.currentHeight();
            int x = resolveX(config.hudStyle.huntingX, HuntingHudRenderer.WIDTH, graphics.guiWidth(), scale);
            int y = resolveY(config.hudStyle.huntingY, height, graphics.guiHeight(), scale);
            renderScaled(graphics, x, y, scale, () -> HuntingHudRenderer.render(graphics));
        }

        if (config.pets.equippedPetHud && PetTracker.current() != null) {
            float scale = style(ModConfig.HudType.PET).scale;
            int petWidth = currentPetWidth();
            int petHeight = currentPetHeight();
            int x = resolveX(config.hudStyle.petX, petWidth, graphics.guiWidth(), scale);
            int y = resolveY(config.hudStyle.petY, petHeight, graphics.guiHeight(), scale);
            renderScaled(graphics, x, y, scale, () -> renderPet(graphics));
        }
    }

    public static void renderEditorPreview(GuiGraphicsExtractor graphics, PreviewPanel panel) {
        Minecraft client = Minecraft.getInstance();
        switch (panel) {
            case MAP -> {
                if (client.player != null && isMapLoaded()) renderMap(graphics, client);
            }
            case MINING -> {
                if (isMiningLoaded()) {
                    int height = currentMiningHeight();
                    if (LocationTracker.area() == IslandArea.CRIMSON_ISLE) renderCrimsonTasks(graphics, height);
                    else renderMining(graphics, height);
                }
            }
            case HUNTING -> {
                if (isHuntingLoaded()) HuntingHudRenderer.render(graphics);
            }
            case PET -> {
                if (isPetLoaded()) renderPet(graphics);
            }
            case SPIRIT_MASK_COOLDOWN -> renderDeathSaveCooldownPanel(graphics,
                    DeathSaveAlertManager.Ability.SPIRIT_MASK, 30_000L, true,
                    style(ModConfig.HudType.SPIRIT_MASK_COOLDOWN));
            case BONZO_MASK_COOLDOWN -> renderDeathSaveCooldownPanel(graphics,
                    DeathSaveAlertManager.Ability.BONZO_MASK, 360_000L, true,
                    style(ModConfig.HudType.BONZO_MASK_COOLDOWN));
            case PHOENIX_COOLDOWN -> renderDeathSaveCooldownPanel(graphics,
                    DeathSaveAlertManager.Ability.PHOENIX, 60_000L, false,
                    style(ModConfig.HudType.PHOENIX_COOLDOWN));
        }
    }

    private static void renderDeathSaveCooldowns(GuiGraphicsExtractor graphics, ModConfig config) {
        if (config.combat.spiritMaskCooldownHud) {
            renderDeathSaveCooldown(graphics, DeathSaveAlertManager.Ability.SPIRIT_MASK,
                    ModConfig.HudType.SPIRIT_MASK_COOLDOWN,
                    config.hudStyle.spiritMaskCooldownX, config.hudStyle.spiritMaskCooldownY, true);
        }
        if (config.combat.bonzoMaskCooldownHud) {
            renderDeathSaveCooldown(graphics, DeathSaveAlertManager.Ability.BONZO_MASK,
                    ModConfig.HudType.BONZO_MASK_COOLDOWN,
                    config.hudStyle.bonzoMaskCooldownX, config.hudStyle.bonzoMaskCooldownY, true);
        }
        if (config.combat.phoenixCooldownHud) {
            renderDeathSaveCooldown(graphics, DeathSaveAlertManager.Ability.PHOENIX,
                    ModConfig.HudType.PHOENIX_COOLDOWN,
                    config.hudStyle.phoenixCooldownX, config.hudStyle.phoenixCooldownY, false);
        }
    }

    private static void renderDeathSaveCooldown(GuiGraphicsExtractor graphics,
                                                DeathSaveAlertManager.Ability ability,
                                                ModConfig.HudType hudType,
                                                int configuredX, int configuredY,
                                                boolean showMaximum) {
        long remainingMillis = DeathSaveAlertManager.remainingMillis(ability);
        if (remainingMillis <= 0L) return;
        ModConfig.PanelStyle style = style(hudType);
        int x = resolveX(configuredX, DEATH_SAVE_COOLDOWN_WIDTH, graphics.guiWidth(), style.scale);
        int y = resolveY(configuredY, DEATH_SAVE_COOLDOWN_HEIGHT, graphics.guiHeight(), style.scale);
        renderScaled(graphics, x, y, style.scale,
                () -> renderDeathSaveCooldownPanel(graphics, ability, remainingMillis, showMaximum, style));
    }

    private static void renderDeathSaveCooldownPanel(GuiGraphicsExtractor graphics,
                                                     DeathSaveAlertManager.Ability ability,
                                                     long remainingMillis,
                                                     boolean showMaximum,
                                                     ModConfig.PanelStyle style) {
        HudPanel.background(graphics, 0, 0, DEATH_SAVE_COOLDOWN_WIDTH, DEATH_SAVE_COOLDOWN_HEIGHT, style);
        HudPanel.title(graphics, ModText.get(deathSaveTitleKey(ability)), 6, 5, style);
        String detail = formatCooldown(remainingMillis);
        if (showMaximum) {
            detail += " · " + ModText.get("hud.death_save.maximum") + " "
                    + formatCooldown(ability.baseCooldownSeconds() * 1_000L);
        }
        HudPanel.text(graphics, detail, 6, 18, 0xFFFFD45A, style);
    }

    private static String deathSaveTitleKey(DeathSaveAlertManager.Ability ability) {
        return switch (ability) {
            case SPIRIT_MASK -> "hud.death_save.spirit_mask";
            case BONZO_MASK -> "hud.death_save.bonzo_mask";
            case PHOENIX -> "hud.death_save.phoenix";
        };
    }

    private static String formatCooldown(long millis) {
        long totalSeconds = Math.max(0L, (millis + 999L) / 1_000L);
        return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60L, totalSeconds % 60L);
    }

    private static void renderMap(GuiGraphicsExtractor graphics, Minecraft client) {
        ModConfig.PanelStyle style = style(ModConfig.HudType.MAP);
        IslandArea area = LocationTracker.area();
        Identifier texture;
        String label;
        double minX;
        double maxX;
        double minZ;
        double maxZ;
        DwarvenMapProjection.Point dwarvenPoint = null;
        if (area == IslandArea.DWARVEN_MINES) {
            texture = DWARVEN_MAP;
            label = ModText.get("hud.map.dwarven");
            minX = -230;
            maxX = 210;
            minZ = -183;
            maxZ = 291;
            dwarvenPoint = DwarvenMapProjection.project(client.player.getX(), client.player.getZ());
        } else {
            double y = client.player.getY();
            if (y <= 126) {
                texture = GLACITE_LOW;
                label = ModText.get("hud.map.glacite") + " • " + ModText.get("hud.layer.low");
            } else if (y <= 143) {
                texture = GLACITE_MIDDLE;
                label = ModText.get("hud.map.glacite") + " • " + ModText.get("hud.layer.middle");
            } else {
                texture = GLACITE_HIGH;
                label = ModText.get("hud.map.glacite") + " • " + ModText.get("hud.layer.high");
            }
            minX = -131;
            maxX = 130;
            minZ = 181;
            maxZ = 485;
        }

        HudPanel.background(graphics, 0, 0, MAP_SIZE, MAP_PANEL_HEIGHT, style);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0, 0,
                MAP_SIZE, MAP_SIZE, MAP_SIZE, MAP_SIZE);
        HudPanel.title(graphics, label, 5, MAP_SIZE + 3, style);
        String coords = area == IslandArea.DWARVEN_MINES
                ? ModText.get("hud.coords_xz", client.player.getX(), client.player.getZ())
                : ModText.get("hud.coords", client.player.getX(), client.player.getY(), client.player.getZ());
        HudPanel.text(graphics, coords, 5, MAP_SIZE + 15, 0xFFD8E4EB, style);

        float mapX = dwarvenPoint == null
                ? mapCoordinate(client.player.getX(), minX, maxX) : dwarvenPoint.x();
        float mapY = dwarvenPoint == null
                ? mapCoordinate(client.player.getZ(), minZ, maxZ) : dwarvenPoint.y();
        Matrix3x2fStack matrices = graphics.pose();
        matrices.pushMatrix();
        matrices.translate(mapX - 6, mapY - 6);
        matrices.rotateAbout(Mth.DEG_TO_RAD * (client.player.getYRot() + 180.0f), 6.0f, 6.0f);
        graphics.blit(RenderPipelines.GUI_TEXTURED, PLAYER_ARROW, 0, 0, 0, 0, 12, 12, 12, 12);
        matrices.popMatrix();
    }

    private static void renderMining(GuiGraphicsExtractor graphics, int height) {
        ModConfig.PanelStyle style = style(ModConfig.HudType.MINING);
        HudPanel.background(graphics, 0, 0, MINING_WIDTH, height, style);
        int y = 5;
        HudPanel.title(graphics, ModText.get("hud.mining"), 6, y, style);
        y += 13;
        HudPanel.text(graphics, ModText.get("hud.commissions"), 6, y, 0xFFFFD45A, style);
        y += 11;
        List<TabListTracker.CommissionProgress> commissions = TabListTracker.commissionProgress();
        int commissionBarWidth = commissionBarWidth(commissions, style);
        if (commissions.isEmpty()) {
            HudPanel.text(graphics, ModText.get("hud.no_tasks"), 10, y, 0xFF98A5AC, style);
            y += 24;
        } else {
            for (TabListTracker.CommissionProgress commission : commissions) {
                HudPanel.text(graphics, commission.name(), 10, y, 0xFFFFFFFF, style);
                y += 10;
                renderCommissionBar(graphics, commission, y, commissionBarWidth, style);
                y += 14;
            }
        }
        y += 2;
        HudPanel.text(graphics, ModText.get("hud.powders"), 6, y, 0xFFFFD45A, style);
        y += 11;
        HudPanel.text(graphics, ModText.get("hud.mithril") + ": " + CompactNumbers.format(TabListTracker.mithrilPowder()), 10, y, 0xFF61C46E, style);
        y += 11;
        HudPanel.text(graphics, ModText.get("hud.gemstone") + ": " + CompactNumbers.format(TabListTracker.gemstonePowder()), 10, y, 0xFFFF72DB, style);
        y += 11;
        HudPanel.text(graphics, ModText.get("hud.glacite") + ": " + CompactNumbers.format(TabListTracker.glacitePowder()), 10, y, 0xFF64E6F2, style);
        if (ConfigManager.get().mining.showHotmSlot) {
            y += 11;
            String slotName = HotmSlotTracker.currentName();
            HudPanel.text(graphics, ModText.get("hud.hotm") + ": " + (slotName.isBlank() ? "—" : slotName),
                    10, y, 0xFF73F58C, style);
        }
    }

    private static void renderCrimsonTasks(GuiGraphicsExtractor graphics, int height) {
        ModConfig.PanelStyle style = style(ModConfig.HudType.MINING);
        HudPanel.background(graphics, 0, 0, MINING_WIDTH, height, style);
        int y = 5;
        HudPanel.title(graphics, ModText.get("hud.crimson_tasks"), 6, y, style);
        y += 13;
        HudPanel.text(graphics, ModText.get("hud.tasks"), 6, y, 0xFFFFD45A, style);
        y += 12;
        List<TabListTracker.CrimsonQuest> quests = TabListTracker.crimsonQuests();
        if (quests.isEmpty()) {
            HudPanel.text(graphics, ModText.get("hud.no_tasks"), 10, y, 0xFF98A5AC, style);
            return;
        }
        for (TabListTracker.CrimsonQuest quest : quests) {
            String marker = quest.readyToCollect() ? "✔ " : "✖ ";
            String amount = quest.amount() > 1 ? " x" + quest.amount() : "";
            HudPanel.text(graphics, marker + quest.name() + amount, 10, y,
                    quest.readyToCollect() ? 0xFF5CFA78 : 0xFFFF7770, style);
            y += 12;
        }
    }

    private static void renderCommissionBar(GuiGraphicsExtractor graphics,
                                            TabListTracker.CommissionProgress commission,
                                            int y, int width, ModConfig.PanelStyle style) {
        int x = 10;
        int height = 10;
        double fraction = Math.clamp(commission.percentage() / 100.0, 0.0, 1.0);
        graphics.fill(x, y, x + width, y + height, 0xD8070A0D);
        int fillWidth = (int) Math.round((width - 2) * fraction);
        if (fillWidth > 0) {
            int fillColor = fraction >= 0.66 ? 0xFF35DE4A : fraction >= 0.33 ? 0xFFFFB52E : 0xFFFF655C;
            graphics.fill(x + 1, y + 1, x + 1 + fillWidth, y + height - 1, fillColor);
        }
        graphics.outline(x, y, width, height, 0xFF35434B);
        HudPanel.text(graphics, commissionProgressText(commission, ConfigManager.get().mining.commissionProgressMode),
                x + 4, y + 1, 0xFFFFFFFF, style);
    }

    private static int commissionBarWidth(List<TabListTracker.CommissionProgress> commissions,
                                          ModConfig.PanelStyle style) {
        int widestName = 0;
        int widestProgress = 0;
        String mode = ConfigManager.get().mining.commissionProgressMode;
        for (TabListTracker.CommissionProgress commission : commissions) {
            widestName = Math.max(widestName, measuredWidth(commission.name(), style));
            widestProgress = Math.max(widestProgress,
                    measuredWidth(commissionProgressText(commission, mode), style));
        }
        return fittedCommissionBarWidth(widestName, widestProgress);
    }

    static int fittedCommissionBarWidth(int widestName, int widestProgress) {
        return Math.clamp(Math.max(80, Math.max(widestName, widestProgress + 8)), 80, MINING_WIDTH - 20);
    }

    static String commissionProgressText(TabListTracker.CommissionProgress commission, String mode) {
        if ("NUMERIC".equals(mode) && commission.hasNumericProgress()) {
            return commission.current() + "/" + commission.target();
        }
        return String.format(Locale.ROOT, "%.1f%%", Math.clamp(commission.percentage(), 0.0, 100.0));
    }

    private static void renderPet(GuiGraphicsExtractor graphics) {
        PetTracker.PetSnapshot pet = PetTracker.current();
        if (pet == null) return;
        var config = ConfigManager.get();
        ModConfig.PanelStyle style = style(ModConfig.HudType.PET);
        PetSkinTracker.PetDetails details = PetSkinTracker.currentDetails(pet.name());
        String skinKey = effectiveSkinKey(pet.name(), details);
        PetDisplayResources.Accessory accessory = currentAccessory(pet.name(), details);
        int width = currentPetWidth();
        int height = currentPetHeight();
        HudPanel.background(graphics, 0, 0, width, height, style);
        HudPanel.title(graphics, ModText.get("hud.pet"), 6, 5, style);
        ItemStack petHead = config.pets.showPetIcon
                ? PetHeadResources.stack(pet, skinKey, details.totalExperience()) : ItemStack.EMPTY;
        boolean hasIcon = !petHead.isEmpty();
        int textX = hasIcon ? 40 : 8;
        if (!petHead.isEmpty()) {
            renderPetHead(graphics, petHead);
        }
        String visibleLevel = displayedLevel(config.pets.showOverflowLevel, pet, skinKey, details.totalExperience());
        HudPanel.text(graphics, "[Lvl " + visibleLevel + "] " + pet.name(),
                textX, 17, 0xFF000000 | pet.rarityColor(), style);
        int y = 29;
        if (config.pets.showLevelProgress) {
            HudPanel.text(graphics, levelProgress(pet), textX, y, 0xFF9ED8FF, style);
            y += 12;
        }
        if (shouldShowMaxProgress(config.pets.showMaxProgress, pet)) {
            PetLeveling.Progress progress = PetLeveling.progress(pet);
            String value = ModText.get("hud.pet.max_progress", progress.maxLevel(),
                    CompactNumbers.format(progress.current()), CompactNumbers.format(progress.maximum()),
                    CompactNumbers.percent(progress.percentage()));
            HudPanel.text(graphics, value, textX, y, 0xFFFFD07D, style);
            y += 12;
        }
        if (config.pets.showSkinName && !skinKey.isBlank()) {
            HudPanel.text(graphics, ModText.get("hud.pet.skin", PetDisplayResources.skinName(skinKey)),
                    textX, y, 0xFFD7B5FF, style);
            y += 12;
        }
        if (accessory != null) {
            String mode = config.pets.petAccessoryDisplay;
            if (!"NAME_ONLY".equals(mode)) graphics.item(accessory.icon(), textX, y - 3);
            if (!"ICON_ONLY".equals(mode)) {
                int accessoryTextX = "ICON_AND_NAME".equals(mode) ? textX + 19 : textX;
                HudPanel.text(graphics, ModText.get("hud.pet.item", accessory.name()),
                        accessoryTextX, y, 0xFFB9E7C6, style);
            }
        }
    }

    private static String levelProgress(PetTracker.PetSnapshot pet) {
        if (pet.maxLevel()) return ModText.get("hud.max_level");
        if (!pet.currentXp().isEmpty() && !pet.nextXp().isEmpty()) {
            String percentage = pet.percentage().isEmpty() ? ""
                    : " (" + CompactNumbers.percent(CompactNumbers.parse(pet.percentage())) + ")";
            return CompactNumbers.format(pet.currentXp()) + "/" + CompactNumbers.format(pet.nextXp()) + " XP" + percentage;
        }
        if (!pet.currentXp().isEmpty()) return CompactNumbers.format(pet.currentXp()) + " XP";
        return "XP —";
    }

    public static int currentMiningHeight() {
        if (LocationTracker.area() == IslandArea.CRIMSON_ISLE) {
            return crimsonTaskHeight(TabListTracker.crimsonQuests().size());
        }
        return miningHeightForCommissionCount(TabListTracker.commissions().size(),
                ConfigManager.get().mining.showHotmSlot);
    }

    static int crimsonTaskHeight(int taskCount) {
        return 5 + 13 + 12 + Math.max(1, taskCount) * 12 + 5;
    }

    public static int currentPetHeight() {
        var pets = ConfigManager.get().pets;
        var pet = PetTracker.current();
        boolean showMaxProgress = pet == null || shouldShowMaxProgress(pets.showMaxProgress, pet);
        int lines = (pets.showLevelProgress ? 1 : 0) + (showMaxProgress ? 1 : 0);
        int accessoryHeight = 0;
        if (pet != null) {
            var details = PetSkinTracker.currentDetails(pet.name());
            if (pets.showSkinName && !effectiveSkinKey(pet.name(), details).isBlank()) lines++;
            if (currentAccessory(pet.name(), details) != null) {
                accessoryHeight = "NAME_ONLY".equals(pets.petAccessoryDisplay) ? 12 : 18;
            }
        }
        int contentHeight = 31 + lines * 12 + accessoryHeight;
        boolean hasIcon = pet != null && hasPetIcon(ConfigManager.get(), pet);
        return Math.max(hasIcon ? 49 : 31, contentHeight);
    }

    public static int currentPetWidth() {
        var pet = PetTracker.current();
        if (pet == null) return PET_WIDTH;
        var config = ConfigManager.get();
        ModConfig.PanelStyle style = style(ModConfig.HudType.PET);
        PetSkinTracker.PetDetails details = PetSkinTracker.currentDetails(pet.name());
        String skinKey = effectiveSkinKey(pet.name(), details);
        PetDisplayResources.Accessory accessory = currentAccessory(pet.name(), details);
        boolean hasIcon = hasPetIcon(config, pet);
        int textX = hasIcon ? 40 : 8;
        String visibleLevel = displayedLevel(config.pets.showOverflowLevel, pet, skinKey, details.totalExperience());
        int width = Math.max(PET_WIDTH, measuredWidth(ModText.get("hud.pet"), style) + 12);
        width = Math.max(width, textX + measuredWidth("[Lvl " + visibleLevel + "] " + pet.name(), style) + 8);
        if (config.pets.showLevelProgress) {
            width = Math.max(width, textX + measuredWidth(levelProgress(pet), style) + 8);
        }
        if (shouldShowMaxProgress(config.pets.showMaxProgress, pet)) {
            PetLeveling.Progress progress = PetLeveling.progress(pet);
            String value = ModText.get("hud.pet.max_progress", progress.maxLevel(),
                    CompactNumbers.format(progress.current()), CompactNumbers.format(progress.maximum()),
                    CompactNumbers.percent(progress.percentage()));
            width = Math.max(width, textX + measuredWidth(value, style) + 8);
        }
        if (config.pets.showSkinName && !skinKey.isBlank()) {
            String value = ModText.get("hud.pet.skin", PetDisplayResources.skinName(skinKey));
            width = Math.max(width, textX + measuredWidth(value, style) + 8);
        }
        if (accessory != null) {
            String mode = config.pets.petAccessoryDisplay;
            if ("ICON_ONLY".equals(mode)) {
                width = Math.max(width, textX + 24);
            } else {
                int accessoryTextX = "ICON_AND_NAME".equals(mode) ? textX + 19 : textX;
                String value = ModText.get("hud.pet.item", accessory.name());
                width = Math.max(width, accessoryTextX + measuredWidth(value, style) + 8);
            }
        }
        return width;
    }

    static int miningHeightForCommissionCount(int commissionCount) {
        return miningHeightForCommissionCount(commissionCount, true);
    }

    static int miningHeightForCommissionCount(int commissionCount, boolean showHotmSlot) {
        return 5 + 13 + 11 + Math.max(1, commissionCount) * 24 + 2 + 11 + 33
                + (showHotmSlot ? 11 : 0) + 5;
    }

    static boolean shouldShowMaxProgress(boolean enabled, PetTracker.PetSnapshot pet) {
        return enabled && !pet.maxLevel();
    }

    private static void renderPetHead(GuiGraphicsExtractor graphics, ItemStack head) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(4.0f, 15.0f);
        graphics.pose().scale(2.0f, 2.0f);
        graphics.item(head, 0, 0);
        graphics.pose().popMatrix();
    }

    static String displayedLevel(boolean enabled, PetTracker.PetSnapshot pet, String skinKey,
                                 double exactTotalExperience) {
        if (!enabled || !"golden_dragon_ancient".equals(skinKey)) return pet.level();
        int cosmetic = PetLeveling.cosmeticLevel(pet, exactTotalExperience);
        try {
            int received = Integer.parseInt(pet.level().replace(",", ""));
            return Integer.toString(Math.max(received, cosmetic));
        } catch (RuntimeException ignored) {
            return cosmetic > 0 ? Integer.toString(cosmetic) : pet.level();
        }
    }

    private static String effectiveSkinKey(String petName, PetSkinTracker.PetDetails details) {
        return !details.skinKey().isBlank() ? details.skinKey()
                : java.util.Objects.requireNonNullElse(PetSkinTracker.currentSkin(petName), "");
    }

    private static PetDisplayResources.Accessory currentAccessory(String petName,
                                                                    PetSkinTracker.PetDetails details) {
        PetDisplayResources.Accessory accessory = PetDisplayResources.accessory(details.heldItemId());
        if (accessory != null) return accessory;
        PetDisplayResources.Accessory received = PetDisplayResources.accessoryInLines(TabListTracker.petWidget());
        if (received != null) PetSkinTracker.rememberHeldItem(petName, received.name());
        return received;
    }

    private static boolean hasPetIcon(ModConfig config, PetTracker.PetSnapshot pet) {
        if (!config.pets.showPetIcon) return false;
        PetSkinTracker.PetDetails details = PetSkinTracker.currentDetails(pet.name());
        return !PetHeadResources.stack(pet, effectiveSkinKey(pet.name(), details), details.totalExperience()).isEmpty();
    }

    private static int measuredWidth(String value, ModConfig.PanelStyle style) {
        return Minecraft.getInstance().font.width(HudPanel.styledText(value, style));
    }

    public static boolean isMapLoaded() {
        var config = ConfigManager.get();
        return (LocationTracker.area() == IslandArea.DWARVEN_MINES && config.maps.dwarvenMines)
                || (LocationTracker.area() == IslandArea.GLACITE_TUNNELS && config.maps.glaciteTunnels);
    }

    public static boolean isMiningLoaded() {
        var config = ConfigManager.get();
        if (LocationTracker.area() == IslandArea.CRIMSON_ISLE) {
            return config.crimsonIsle.taskTracker && !TabListTracker.crimsonQuests().isEmpty();
        }
        return LocationTracker.area().isMiningIsland()
                && config.mining.taskAndPowderTracker
                && hasMiningHudContent(config);
    }

    static boolean hasMiningHudContent(ModConfig config) {
        return !TabListTracker.commissionProgress().isEmpty()
                || hasPowder(TabListTracker.mithrilPowder())
                || hasPowder(TabListTracker.gemstonePowder())
                || hasPowder(TabListTracker.glacitePowder())
                || config.mining.showHotmSlot && !HotmSlotTracker.currentName().isBlank();
    }

    private static boolean hasPowder(String value) {
        return value != null && !value.isBlank() && !"—".equals(value);
    }

    public static boolean isPetLoaded() {
        return LocationTracker.area() != IslandArea.CRITTER_SAFARI
                && ConfigManager.get().pets.equippedPetHud
                && PetTracker.current() != null;
    }

    public static boolean isHuntingLoaded() {
        return HuntingHudRenderer.loaded();
    }

    public static int resolveX(int configured, int panelWidth, int screenWidth, float scale) {
        int scaledWidth = Math.max(1, (int) Math.ceil(panelWidth * scale));
        int maximum = Math.max(0, screenWidth - scaledWidth);
        if (configured >= 0) return Math.clamp(configured, 0, maximum);
        int rightMargin = Math.max(0, -configured - panelWidth);
        return Math.max(0, maximum - rightMargin);
    }

    public static int resolveY(int configured, int panelHeight, int screenHeight, float scale) {
        int scaledHeight = Math.max(1, (int) Math.ceil(panelHeight * scale));
        int maximum = Math.max(0, screenHeight - scaledHeight);
        return Math.clamp(configured, 0, maximum);
    }

    static float mapCoordinate(double value, double minimum, double maximum) {
        double normalized = Math.clamp((value - minimum) / (maximum - minimum), 0.0, 1.0);
        return (float) (MAP_MARGIN + normalized * (MAP_SIZE - MAP_MARGIN * 2));
    }

    private static void renderScaled(GuiGraphicsExtractor graphics, int x, int y, float scale, Runnable renderer) {
        Matrix3x2fStack matrices = graphics.pose();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(scale, scale);
        renderer.run();
        matrices.popMatrix();
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("qcloudy_addition", path);
    }

    public static ModConfig.PanelStyle style(ModConfig.HudType type) {
        return ConfigManager.get().hudStyle.style(type);
    }

    public enum PreviewPanel {
        MAP,
        MINING,
        HUNTING,
        PET,
        SPIRIT_MASK_COOLDOWN,
        BONZO_MASK_COOLDOWN,
        PHOENIX_COOLDOWN
    }
}

package cloudy.autume.addition.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfigScreenFeatureTest {
    @Test
    void hudAnimationsIsTheFirstFeatureAndUsesTheSharedAnimationSetting() {
        ConfigScreen.Feature feature = ConfigScreen.Feature.values()[0];
        assertEquals(ConfigScreen.Feature.HUD_ANIMATIONS, feature);

        ModConfig config = new ModConfig();
        assertTrue(feature.enabled(config));
        feature.toggle(config);
        assertFalse(config.hudStyle.animations);
        assertFalse(feature.enabled(config));
        assertFalse(feature.hasSettings());
    }

    @Test
    void newHuntingFeaturesUseRequestedDefaultsAndSafariHasNoEssenceOption() {
        ModConfig config = new ModConfig();
        assertTrue(ConfigScreen.Feature.COLD_SAFETY.enabled(config));
        assertTrue(ConfigScreen.Feature.DOOMSPIRAL_READY.enabled(config));
        assertTrue(ConfigScreen.Feature.WARDEN_READY_ALERT.enabled(config));
        assertTrue(ConfigScreen.Feature.SAFARI_CRITTER_HIGHLIGHT.enabled(config));
        assertFalse(ConfigScreen.Feature.SAFARI_SHARD_STATS.enabled(config));
        assertTrue(ConfigScreen.Feature.SNOOZLE_WALL_OVERLAY.enabled(config));
        assertTrue(ConfigScreen.Feature.TREE_CRITTER_TIMER.enabled(config));
        assertTrue(ConfigScreen.Feature.GALATEA_TRACKER.enabled(config));
        assertTrue(ConfigScreen.Feature.AGATHA_CONTEST.enabled(config));
        assertTrue(ConfigScreen.Feature.BEEHEEMOTH_HELPER.enabled(config));
        assertTrue(ConfigScreen.Feature.LASSO_REEL_SOUND.enabled(config));
        assertTrue(HuntingOption.forFeature(ConfigScreen.Feature.BEEHEEMOTH_HELPER).stream()
                .anyMatch(option -> option == HuntingOption.BEEHEEMOTH_COLOR));
        assertTrue(HuntingOption.forFeature(ConfigScreen.Feature.BEEHEEMOTH_HELPER).stream()
                .anyMatch(option -> option == HuntingOption.BEEHEEMOTH_SOUND));
        assertTrue(HuntingOption.forFeature(ConfigScreen.Feature.BEEHEEMOTH_HELPER).stream()
                .anyMatch(option -> option == HuntingOption.BEEHEEMOTH_VOLUME));
        assertTrue(HuntingOption.forFeature(ConfigScreen.Feature.LASSO_REEL_SOUND).stream()
                .anyMatch(option -> option == HuntingOption.LASSO_REEL_VOLUME));
        assertTrue(HuntingOption.forFeature(ConfigScreen.Feature.WARDEN_READY_ALERT).stream()
                .anyMatch(option -> option == HuntingOption.WARDEN_READY_SOUND));
        assertTrue(HuntingOption.forFeature(ConfigScreen.Feature.WARDEN_READY_ALERT).stream()
                .anyMatch(option -> option == HuntingOption.WARDEN_READY_VOLUME));
        assertTrue(HuntingOption.forFeature(ConfigScreen.Feature.WUMPA_HUD).stream()
                .anyMatch(option -> option == HuntingOption.WUMPA_REQUIREMENTS));
        assertTrue(HuntingOption.forFeature(ConfigScreen.Feature.SNOOZLE_WALL_OVERLAY).stream()
                .anyMatch(option -> option == HuntingOption.SNOOZLE_WALL_COLOR));
        assertEquals(ModConfig.HudType.HUNTING, ConfigScreen.Feature.TREE_CRITTER_TIMER.hudType());
        assertFalse(ConfigScreen.Feature.FAIRY_SOUL_WAYPOINTS.enabled(config));
        assertTrue(HuntingOption.forFeature(ConfigScreen.Feature.SAFARI_DASHBOARD).stream()
                .noneMatch(option -> option.name().contains("ESSENCE")));
        assertTrue(HuntingOption.forFeature(ConfigScreen.Feature.SAFARI_CRITTERDEX).stream()
                .noneMatch(option -> option.name().contains("SHARDS")));
        ConfigScreen.Feature.SAFARI_SHARD_STATS.toggle(config);
        assertTrue(config.hunting.safariShards);
        assertEquals(ModConfig.HudType.HUNTING, ConfigScreen.Feature.SAFARI_SHARD_STATS.hudType());
        assertTrue(HuntingOption.forFeature(ConfigScreen.Feature.TORRHUS_TRACKER).stream()
                .anyMatch(option -> option == HuntingOption.SAFARI_ESSENCE_TORRHUS));
        assertTrue(HuntingOption.forFeature(ConfigScreen.Feature.TREE_GIFT_ALERTS).stream()
                .anyMatch(option -> option == HuntingOption.TREE_GIFT_VOLUME));
        assertTrue(HuntingOption.forFeature(ConfigScreen.Feature.MIRIA_CONTEST).stream()
                .noneMatch(option -> option.name().contains("SCOREBOARD")));
        assertTrue(HuntingOption.forFeature(ConfigScreen.Feature.GALATEA_TRACKER).stream()
                .anyMatch(option -> option == HuntingOption.GALATEA_SWEEP));
        assertTrue(HuntingOption.forFeature(ConfigScreen.Feature.AGATHA_CONTEST).stream()
                .anyMatch(option -> option == HuntingOption.AGATHA_NEXT_BRACKET));
    }

    @Test
    void manualReconnectIsASettingsFreeGeneralToggle() {
        ModConfig config = new ModConfig();
        assertEquals(ConfigScreen.Category.GENERAL, ConfigScreen.Feature.MANUAL_RECONNECT.category);
        assertTrue(ConfigScreen.Feature.MANUAL_RECONNECT.enabled(config));
        assertFalse(ConfigScreen.Feature.MANUAL_RECONNECT.hasSettings());
        ConfigScreen.Feature.MANUAL_RECONNECT.toggle(config);
        assertFalse(config.manualReconnectButton);
    }

    @Test
    void dungeonQuickViewKeepsItsOwnMasterAndOpensRequirementSettings() {
        ModConfig config = new ModConfig();
        ConfigScreen.Feature feature = ConfigScreen.Feature.DUNGEON_QUICK_VIEW;

        assertEquals(ConfigScreen.Category.DUNGEONS, feature.category);
        assertEquals(ConfigScreen.FeatureGroup.DUNGEON_PARTY, feature.group);
        assertTrue(feature.enabled(config));
        assertTrue(feature.hasSettings());
        assertFalse(config.dungeons.partyFinderAutoKick.enabled);

        feature.toggle(config);
        assertFalse(config.dungeons.playerQuickView);
        assertFalse(config.dungeons.partyFinderAutoKick.enabled);
    }

    @Test
    void secondaryBooleanRowsUseSwitchesAndOnlyRequirementPolicyHasAChildEditor() {
        assertTrue(FeatureSettingsScreen.isBooleanSettingKind(
                FeatureSettingsScreen.Kind.CHAT_CHANNEL_SHOW_OFFICER));
        assertTrue(FeatureSettingsScreen.isBooleanSettingKind(
                FeatureSettingsScreen.Kind.OPEN_DUNGEON_REQUIREMENTS));
        assertTrue(FeatureSettingsScreen.isBooleanSettingKind(FeatureSettingsScreen.Kind.BORDER));
        assertTrue(FeatureSettingsScreen.isBooleanSettingKind(FeatureSettingsScreen.Kind.PET_SKIN_NAME));
        assertFalse(FeatureSettingsScreen.isBooleanSettingKind(FeatureSettingsScreen.Kind.EDIT_LAYOUT));
        assertFalse(FeatureSettingsScreen.isBooleanSettingKind(FeatureSettingsScreen.Kind.OPEN_SHARD_GUIDE));
        assertTrue(FeatureSettingsScreen.hasSecondaryEditor(
                FeatureSettingsScreen.Kind.OPEN_DUNGEON_REQUIREMENTS));
        assertFalse(FeatureSettingsScreen.hasSecondaryEditor(
                FeatureSettingsScreen.Kind.CHAT_CHANNEL_SHOW_OFFICER));
        assertEquals(60, FeatureSettingsScreen.inlineEditorHintWidth(250, 100));
        assertEquals(1, FeatureSettingsScreen.inlineEditorHintWidth(70, 100));
    }

    @Test
    void dungeonRequirementEditorsAcceptOnlySafeExplicitValues() {
        assertEquals(50L, DungeonRequirementsScreen.parseWholeNumber("50", 0, 1_000_000));
        assertEquals(0L, DungeonRequirementsScreen.parseWholeNumber("0", 0, 1_000_000));
        assertEquals(null, DungeonRequirementsScreen.parseWholeNumber("-1", 0, 1_000_000));
        assertEquals(null, DungeonRequirementsScreen.parseWholeNumber("1.5", 0, 1_000_000));
        assertEquals(null, DungeonRequirementsScreen.parseWholeNumber("1000001", 0, 1_000_000));

        assertEquals(450_000L, DungeonRequirementsScreen.parseDurationMs("07:30"));
        assertEquals(3_599_000L, DungeonRequirementsScreen.parseDurationMs("59:59"));
        assertEquals(null, DungeonRequirementsScreen.parseDurationMs("7:3"));
        assertEquals(null, DungeonRequirementsScreen.parseDurationMs("07:60"));
        assertEquals(null, DungeonRequirementsScreen.parseDurationMs("00:00"));
        assertEquals("07:30", DungeonRequirementsScreen.formatDuration(450_999L));

        assertEquals(8.5, DungeonRequirementsScreen.parseDecimal("8.5", 0.0, 1_000.0));
        assertEquals(0.5, DungeonRequirementsScreen.parseDecimal(".5", 0.0, 1_000.0));
        assertEquals(8.0375, DungeonRequirementsScreen.parseDecimal("8.0375", 0.0, 1_000.0));
        assertEquals(0.000000000123456789,
                DungeonRequirementsScreen.parseDecimal("0.000000000123456789", 0.0, 1_000.0));
        assertEquals(null, DungeonRequirementsScreen.parseDecimal(
                "1000.00000000000000000001", 0.0, 1_000.0));
        assertEquals(null, DungeonRequirementsScreen.parseDecimal("NaN", 0.0, 1_000.0));
        assertEquals(null, DungeonRequirementsScreen.parseDecimal("1,5", 0.0, 1_000.0));
    }

    @Test
    void partyAutoAcceptIsAnOptInGeneralFeatureWithModeAndOrderedWhitelist() {
        ModConfig config = new ModConfig();
        ConfigScreen.Feature feature = ConfigScreen.Feature.PARTY_AUTO_ACCEPT;

        assertEquals(ConfigScreen.Category.GENERAL, feature.category);
        assertEquals(ConfigScreen.FeatureGroup.CHAT_UI, feature.group);
        assertFalse(feature.enabled(config));
        assertTrue(feature.hasSettings());
        assertEquals(null, feature.hudType());
        assertEquals(ModConfig.PartyAcceptFriendMode.NORMAL_ONLY,
                config.chat.partyAutoAcceptFriendMode);
        assertEquals(List.of(), config.chat.partyAutoAcceptWhitelist);

        feature.toggle(config);
        assertTrue(config.chat.partyAutoAccept);
        assertTrue(feature.enabled(config));
    }

    @Test
    void generalChatGroupHasTheRequiredFixedFeatureOrder() {
        List<ConfigScreen.Feature> chatFeatures = java.util.Arrays.stream(ConfigScreen.Feature.values())
                .filter(feature -> feature.group == ConfigScreen.FeatureGroup.CHAT_UI)
                .toList();

        assertEquals(List.of(
                ConfigScreen.Feature.CHAT_PEEK,
                ConfigScreen.Feature.CHAT_CHANNEL_SWITCHER,
                ConfigScreen.Feature.PARTY_AUTO_ACCEPT,
                ConfigScreen.Feature.DIRECT_MESSAGE_PARTY_REQUEST,
                ConfigScreen.Feature.QUICK_PRIVATE_PARTY_REQUEST,
                ConfigScreen.Feature.FAST_PARTY_COMMANDS), chatFeatures);
        assertTrue(chatFeatures.stream().allMatch(feature -> feature.category == ConfigScreen.Category.GENERAL));
        assertEquals(ConfigScreen.FeatureGroup.COMMANDS, ConfigScreen.Feature.PARTY_COMMANDS.group);
        assertEquals(ConfigScreen.Category.GENERAL, ConfigScreen.Feature.PARTY_COMMANDS.category);
    }

    @Test
    void chatChannelSwitcherIsOptInWithOfficerAsAnIndependentAdvancedChoice() {
        ModConfig config = new ModConfig();
        config.normalize();
        ConfigScreen.Feature feature = ConfigScreen.Feature.CHAT_CHANNEL_SWITCHER;

        assertEquals(ConfigScreen.FeatureGroup.CHAT_UI, feature.group);
        assertFalse(feature.enabled(config));
        assertFalse(config.chat.chatChannelShowOfficer);
        assertTrue(feature.hasSettings());
        assertEquals(null, feature.hudType());

        feature.toggle(config);
        assertTrue(feature.enabled(config));
        assertFalse(config.chat.chatChannelShowOfficer);
    }

    @Test
    void everyDeclaredFeatureGroupOwnsAtLeastOneFeature() {
        for (ConfigScreen.FeatureGroup group : ConfigScreen.FeatureGroup.values()) {
            assertTrue(java.util.Arrays.stream(ConfigScreen.Feature.values())
                            .anyMatch(feature -> feature.group == group),
                    () -> "Empty feature group: " + group);
        }
    }

    @Test
    void partyCommandFamiliesUseIndependentMastersAndParentGatedSettings() {
        ModConfig config = new ModConfig();
        ConfigScreen.Feature fast = ConfigScreen.Feature.FAST_PARTY_COMMANDS;
        ConfigScreen.Feature local = ConfigScreen.Feature.PARTY_COMMANDS;

        assertFalse(fast.enabled(config));
        assertTrue(local.enabled(config));
        assertTrue(fast.hasSettings());
        assertTrue(local.hasSettings());
        assertFalse(FeatureSettingsScreen.partyCommandChildSettingsAvailable(config, false));
        assertTrue(FeatureSettingsScreen.partyCommandChildSettingsAvailable(config, true));
        assertTrue(config.chat.fastPartyPromote);
        assertEquals(ModConfig.PartyCommandPermission.PARTY_MEMBERS,
                config.chat.fastPartyOtherPermission);
        assertTrue(config.chat.partyCommandPromote);

        fast.toggle(config);
        assertTrue(fast.enabled(config));
        assertTrue(FeatureSettingsScreen.partyCommandChildSettingsAvailable(config, false));
        assertTrue(local.enabled(config));

        local.toggle(config);
        assertFalse(local.enabled(config));
        assertFalse(FeatureSettingsScreen.partyCommandChildSettingsAvailable(config, true));
        assertTrue(config.chat.partyCommandPromote);
        assertTrue(config.chat.fastPartyPromote);
    }

    @Test
    void privateMessagePartyFeaturesAreIndependentSettingsFreeOptIns() {
        ModConfig config = new ModConfig();
        ConfigScreen.Feature request = ConfigScreen.Feature.DIRECT_MESSAGE_PARTY_REQUEST;
        ConfigScreen.Feature quick = ConfigScreen.Feature.QUICK_PRIVATE_PARTY_REQUEST;

        assertFalse(request.enabled(config));
        assertFalse(quick.enabled(config));
        assertFalse(request.hasSettings());
        assertFalse(quick.hasSettings());
        request.toggle(config);
        assertTrue(request.enabled(config));
        assertFalse(quick.enabled(config));
    }

    @Test
    void settingsFreeFeaturesCannotExposeSharedHudAppearanceControls() {
        assertFalse(FeatureSettingsScreen.usesHudAppearanceSettings(
                ConfigScreen.Feature.QUICK_PRIVATE_PARTY_REQUEST));
        assertFalse(FeatureSettingsScreen.usesHudAppearanceSettings(
                ConfigScreen.Feature.DIRECT_MESSAGE_PARTY_REQUEST));
        assertFalse(FeatureSettingsScreen.usesHudAppearanceSettings(
                ConfigScreen.Feature.CHAT_PEEK));
        assertFalse(FeatureSettingsScreen.usesHudAppearanceSettings(
                ConfigScreen.Feature.DRAGON_HIGHLIGHT));
        assertTrue(FeatureSettingsScreen.usesHudAppearanceSettings(
                ConfigScreen.Feature.MINING_TRACKER));
        assertTrue(FeatureSettingsScreen.usesHudAppearanceSettings(
                ConfigScreen.Feature.SPIRIT_MASK_COOLDOWN_HUD));
        assertTrue(FeatureSettingsScreen.usesHudAppearanceSettings(
                ConfigScreen.Feature.PET_HUD));
        assertFalse(ConfigScreen.Feature.PET_HUD.inventoryFeature());
        assertFalse(ConfigScreen.Feature.CENTURY_CAKE_EFFECTS.inventoryFeature());
        assertFalse(ConfigScreen.Feature.SHARD_FUSION_HELPER.inventoryFeature());
        assertTrue(ConfigScreen.Feature.ITEM_TIMESTAMPS.inventoryFeature());
        assertTrue(ConfigScreen.Feature.CURSOR_MEMORY.inventoryFeature());
        assertTrue(ConfigScreen.Feature.TELEPORT_SOUNDS.inventoryFeature());

        var settingsFree = new UnifiedModIntegration.UnifiedFeature(
                "chat:quick_private_party_request", "Quick private !p", "",
                ConfigScreen.Category.GENERAL, "Chat",
                ConfigScreen.Feature.QUICK_PRIVATE_PARTY_REQUEST, List.of());
        var realHud = new UnifiedModIntegration.UnifiedFeature(
                "mining:mining_tasks_powders", "Mining", "",
                ConfigScreen.Category.MINING, "Mining",
                ConfigScreen.Feature.MINING_TRACKER, List.of());
        assertFalse(ConfigScreen.hasFeatureSettings(settingsFree));
        assertTrue(ConfigScreen.hasFeatureSettings(realHud));
    }

    @Test
    void unifiedEditorsAreIndependentDefaultOffGeneralMasterSwitches() {
        ModConfig config = new ModConfig();
        ConfigScreen.Feature settings = ConfigScreen.Feature.UNIFIED_SETTINGS_EDITOR;
        ConfigScreen.Feature hud = ConfigScreen.Feature.UNIFIED_HUD_EDITOR;

        assertEquals(ConfigScreen.Category.GENERAL, settings.category);
        assertEquals(ConfigScreen.Category.GENERAL, hud.category);
        assertEquals(ConfigScreen.FeatureGroup.INTEGRATIONS, settings.group);
        assertEquals(ConfigScreen.FeatureGroup.INTEGRATIONS, hud.group);
        assertFalse(settings.enabled(config));
        assertFalse(hud.enabled(config));
        assertTrue(settings.hasSettings());
        assertTrue(hud.hasSettings());

        settings.toggle(config);
        assertTrue(config.integrations.unifiedSettingsEditor);
        assertFalse(config.integrations.unifiedHudEditor);

        hud.toggle(config);
        assertTrue(config.integrations.unifiedSettingsEditor);
        assertTrue(config.integrations.unifiedHudEditor);

        settings.toggle(config);
        assertFalse(config.integrations.unifiedSettingsEditor);
        assertTrue(config.integrations.unifiedHudEditor);
    }

    @Test
    void bothUnifiedEditorMasterSwitchesRemainRegistered() {
        assertEquals(List.of(
                        ConfigScreen.Feature.UNIFIED_SETTINGS_EDITOR,
                        ConfigScreen.Feature.UNIFIED_HUD_EDITOR),
                ConfigScreen.integrationMasterSwitches());
    }

    @Test
    void integrationGroupStartsCollapsedAndKeepsManualStateForTheOpenScreen() {
        ConfigScreen.GroupExpansionState state = new ConfigScreen.GroupExpansionState();
        String group = ConfigScreen.FeatureGroup.INTEGRATIONS.name();

        assertFalse(state.isExpanded(group));
        state.toggle(group);
        assertTrue(state.isExpanded(group));
        state.toggle(group);
        assertFalse(state.isExpanded(group));
    }

    @Test
    void reportOnlyIntegrationGroupExpandsOnlyWhileSearching() {
        assertFalse(ConfigScreen.reportOnlyGroupExpanded(false));
        assertTrue(ConfigScreen.reportOnlyGroupExpanded(true));
    }

    @Test
    void onlyUnifiedEditorMastersOpenTheInitialScanConfirmation() {
        assertTrue(ConfigScreen.isIntegrationScanMaster(ConfigScreen.Feature.UNIFIED_SETTINGS_EDITOR));
        assertTrue(ConfigScreen.isIntegrationScanMaster(ConfigScreen.Feature.UNIFIED_HUD_EDITOR));
        assertFalse(ConfigScreen.isIntegrationScanMaster(ConfigScreen.Feature.HUD_ANIMATIONS));
        assertFalse(ConfigScreen.isIntegrationScanMaster(ConfigScreen.Feature.MANUAL_RECONNECT));
    }

    @Test
    void compatibilityReportIsNotAFeatureToggleAndAcceptsEitherMainMouseButton() {
        assertTrue(ConfigScreen.opensCompatibilityReport(0));
        assertTrue(ConfigScreen.opensCompatibilityReport(1));
        assertFalse(ConfigScreen.opensCompatibilityReport(2));
        assertTrue(java.util.Arrays.stream(ConfigScreen.Feature.values())
                .noneMatch(feature -> feature.name().contains("COMPATIBILITY_REPORT")));
    }

    @Test
    void fishingBiteSoundIsAnOptInTopLevelFishingFeatureWithItsOwnSettings() {
        ModConfig config = new ModConfig();
        ConfigScreen.Feature feature = ConfigScreen.Feature.FISHING_BITE_ALERT;

        assertEquals(ConfigScreen.Category.FISHING, feature.category);
        assertEquals(ConfigScreen.FeatureGroup.FISHING, feature.group);
        assertFalse(feature.enabled(config));
        assertTrue(feature.hasSettings());
        assertEquals(64, config.fishing.biteAlertVolume);

        feature.toggle(config);
        assertTrue(feature.enabled(config));
    }

    @Test
    void deployableExpiryAlertIsAnEnabledCombatFeatureWithIndependentAudio() {
        ModConfig config = new ModConfig();
        ConfigScreen.Feature feature = ConfigScreen.Feature.DEPLOYABLE_EXPIRY_ALERT;

        assertEquals(ConfigScreen.Category.COMBAT, feature.category);
        assertEquals(ConfigScreen.FeatureGroup.COMBAT_DEPLOYABLES, feature.group);
        assertTrue(feature.enabled(config));
        assertTrue(feature.hasSettings());
        assertTrue(config.combat.deployablePowerOrbAlerts);
        assertTrue(config.combat.deployableFlareAlerts);
        assertTrue(config.combat.deployableExpiryCenterText);
        assertTrue(config.combat.deployableExpiryAudio.sound);
        assertEquals(64, config.combat.deployableExpiryAudio.volume);
        assertEquals(null, feature.hudType());

        feature.toggle(config);
        assertFalse(config.combat.deployableExpiryAlert);
    }

    @Test
    void centuryCakeEffectsUseOneEnabledMasterSwitchWithSharedAudio() {
        ModConfig config = new ModConfig();
        ConfigScreen.Feature feature = ConfigScreen.Feature.CENTURY_CAKE_EFFECTS;

        assertEquals(ConfigScreen.Category.ITEMS_AND_MENUS, feature.category);
        assertEquals(ConfigScreen.FeatureGroup.CENTURY_CAKES, feature.group);
        assertTrue(feature.enabled(config));
        assertTrue(feature.hasSettings());
        assertEquals(null, feature.hudType());
        assertTrue(config.centuryCakes.expiryAudio.sound);
        assertEquals(64, config.centuryCakes.expiryAudio.volume);
        assertArrayEquals(new String[]{"expiryAlerts", "expiryAudio"},
                java.util.Arrays.stream(ModConfig.CenturyCakes.class.getDeclaredFields())
                        .map(java.lang.reflect.Field::getName).sorted().toArray(String[]::new));

        feature.toggle(config);
        assertFalse(feature.enabled(config));
        assertFalse(config.centuryCakes.expiryAlerts);
    }

    @Test
    void requestedTopLevelCategoriesUseTheExactPublishedOrder() {
        assertArrayEquals(new ConfigScreen.Category[]{
                        ConfigScreen.Category.GENERAL,
                        ConfigScreen.Category.MAPS,
                        ConfigScreen.Category.ITEMS_AND_MENUS,
                        ConfigScreen.Category.COMBAT,
                        ConfigScreen.Category.DUNGEONS,
                        ConfigScreen.Category.SLAYER,
                        ConfigScreen.Category.MINING,
                        ConfigScreen.Category.FARMING,
                        ConfigScreen.Category.FORAGING,
                        ConfigScreen.Category.FISHING,
                        ConfigScreen.Category.HUNTING,
                        ConfigScreen.Category.RIFT,
                        ConfigScreen.Category.EVENTS
                },
                ConfigScreen.Category.values());
        int slotHeight = ConfigScreen.sidebarCategorySlotHeight(220);
        assertTrue(slotHeight >= 18);
    }

    @Test
    void sidebarHidesCategoriesWithoutAnyAvailableFeature() {
        var visible = ConfigScreen.visibleCategories(java.util.List.of(
                ConfigScreen.Category.GENERAL,
                ConfigScreen.Category.FISHING,
                ConfigScreen.Category.MINING,
                ConfigScreen.Category.FISHING));

        assertEquals(java.util.List.of(
                ConfigScreen.Category.GENERAL,
                ConfigScreen.Category.MINING,
                ConfigScreen.Category.FISHING), visible);
        assertFalse(visible.contains(ConfigScreen.Category.DUNGEONS));
        assertEquals(24, ConfigScreen.sidebarCategorySlotHeight(380, visible.size()));
    }

    @Test
    void shardFusionIsAnEnabledInventoryFeatureInItsOwnGroup() {
        ModConfig config = new ModConfig();
        ConfigScreen.Feature feature = ConfigScreen.Feature.SHARD_FUSION_HELPER;

        assertEquals(ConfigScreen.Category.ITEMS_AND_MENUS, feature.category);
        assertEquals(ConfigScreen.FeatureGroup.SHARD_FUSION, feature.group);
        assertTrue(feature.enabled(config));
        assertTrue(feature.hasSettings());
        assertEquals(null, feature.hudType());
        assertTrue(FeatureSettingsScreen.shardGuideEntryEnabled(config));

        feature.toggle(config);
        assertFalse(config.inventory.shardFusionHelper);
        assertFalse(feature.enabled(config));
        assertFalse(FeatureSettingsScreen.shardGuideEntryEnabled(config));
    }

    @Test
    void requestedSidebarCategoriesOwnEachFeatureOnce() {
        assertEquals(ConfigScreen.Category.FORAGING, ConfigScreen.Feature.TORRHUS_TRACKER.category);
        assertEquals(ConfigScreen.Category.FORAGING, ConfigScreen.Feature.GALATEA_TRACKER.category);
        assertEquals(ConfigScreen.Category.FORAGING, ConfigScreen.Feature.TREE_CRITTER_TIMER.category);
        assertEquals(ConfigScreen.Category.FORAGING, ConfigScreen.Feature.MIRIA_CONTEST.category);
        assertEquals(ConfigScreen.Category.FORAGING, ConfigScreen.Feature.AGATHA_CONTEST.category);
        assertEquals(ConfigScreen.Category.FORAGING, ConfigScreen.Feature.BENEFACTOR_HUD.category);
        assertEquals(ConfigScreen.Category.FORAGING, ConfigScreen.Feature.TREE_GIFT_ALERTS.category);

        assertEquals(ConfigScreen.Category.MAPS, ConfigScreen.Feature.FAIRY_SOUL_WAYPOINTS.category);
        assertEquals(ConfigScreen.Category.HUNTING, ConfigScreen.Feature.BEEHEEMOTH_HELPER.category);
        assertEquals(ConfigScreen.Category.HUNTING, ConfigScreen.Feature.LASSO_REEL_SOUND.category);
        assertEquals(ConfigScreen.Category.HUNTING, ConfigScreen.Feature.CRITTER_BEHAVIOR.category);

        assertEquals(ConfigScreen.Category.HUNTING, ConfigScreen.Feature.COLD_SAFETY.category);
        assertEquals(ConfigScreen.Category.HUNTING, ConfigScreen.Feature.DOOMSPIRAL_READY.category);
        assertEquals(ConfigScreen.Category.HUNTING, ConfigScreen.Feature.WARDEN_READY_ALERT.category);
        assertEquals(ConfigScreen.Category.HUNTING, ConfigScreen.Feature.SAFARI_CRITTER_HIGHLIGHT.category);
        assertEquals(ConfigScreen.Category.HUNTING, ConfigScreen.Feature.SAFARI_DASHBOARD.category);
        assertEquals(ConfigScreen.Category.HUNTING, ConfigScreen.Feature.SAFARI_SHARD_STATS.category);
        assertEquals(ConfigScreen.Category.HUNTING, ConfigScreen.Feature.SAFARI_CRITTERDEX.category);
        assertEquals(ConfigScreen.Category.HUNTING, ConfigScreen.Feature.SPARKLING_ALERT.category);
        assertEquals(ConfigScreen.Category.HUNTING, ConfigScreen.Feature.FLOOR_QUEST_ASSISTANT.category);
        assertEquals(ConfigScreen.Category.HUNTING, ConfigScreen.Feature.WUMPA_HUD.category);
        assertEquals(ConfigScreen.Category.HUNTING, ConfigScreen.Feature.SNOOZLE_WALL_OVERLAY.category);
        assertEquals(ConfigScreen.Category.HUNTING, ConfigScreen.Feature.SAFARI_BELT.category);
    }

    @Test
    void secondarySettingSlidersShrinkInsteadOfEscapingNarrowRows() {
        var narrow = FeatureSettingsScreen.sliderLayout(20, 96);
        var wide = FeatureSettingsScreen.sliderLayout(20, 480);

        assertTrue(narrow.trackX() >= 20);
        assertTrue(narrow.trackX() + narrow.trackWidth() <= 20 + 96);
        assertTrue(wide.trackX() >= 20);
        assertTrue(wide.trackX() + wide.trackWidth() <= 20 + 480);
        assertTrue(wide.trackWidth() > narrow.trackWidth());
    }

    @Test
    void collapsedFeatureGroupsIncludeTheCardStartGapInTheirScrollHeight() {
        assertEquals(35, ConfigScreen.featureGroupBlockHeight(false, 2, 2));
        assertEquals(107, ConfigScreen.featureGroupBlockHeight(true, 1, 2));
    }
}

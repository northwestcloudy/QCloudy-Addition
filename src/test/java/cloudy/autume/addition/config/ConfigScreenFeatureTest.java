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
                ConfigScreen.Feature.PARTY_AUTO_ACCEPT,
                ConfigScreen.Feature.DIRECT_MESSAGE_PARTY_REQUEST,
                ConfigScreen.Feature.QUICK_PRIVATE_PARTY_REQUEST,
                ConfigScreen.Feature.FAST_PARTY_COMMANDS), chatFeatures);
        assertTrue(chatFeatures.stream().allMatch(feature -> feature.category == ConfigScreen.Category.GENERAL));
        assertEquals(ConfigScreen.FeatureGroup.COMMANDS, ConfigScreen.Feature.PARTY_COMMANDS.group);
        assertEquals(ConfigScreen.Category.GENERAL, ConfigScreen.Feature.PARTY_COMMANDS.category);
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
        assertEquals(ModConfig.PartyCommandTrigger.EVERYONE, config.chat.fastPartyPromoteTrigger);
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

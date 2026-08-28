package cloudy.autume.addition.config;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UnifiedModIntegrationTest {
    @Test
    void localFeatureIdsStayStableAndCollisionFreeAcrossLanguages() {
        String originalLanguage = ConfigManager.get().language;
        try {
            ConfigManager.get().language = "en_us";
            Map<ConfigScreen.Feature, String> english = localFeatureIds(
                    UnifiedModIntegration.buildFeatures(List.of()));

            ConfigManager.get().language = "zh_cn";
            Map<ConfigScreen.Feature, String> chinese = localFeatureIds(
                    UnifiedModIntegration.buildFeatures(List.of()));

            assertEquals(ConfigScreen.Feature.values().length, english.size());
            assertEquals(english, chinese);
            assertEquals(english.size(), new HashSet<>(english.values()).size());

            // These are legacy English selectedProviders keys. Keeping them
            // stable prevents a language switch from losing provider choices.
            assertEquals("general:ui_animations", english.get(ConfigScreen.Feature.HUD_ANIMATIONS));
            assertEquals("general:manage_other_mod_settings",
                    english.get(ConfigScreen.Feature.UNIFIED_SETTINGS_EDITOR));
            assertEquals("maps:fairy_souls",
                    english.get(ConfigScreen.Feature.FAIRY_SOUL_WAYPOINTS));
            assertEquals("items_and_menus:cursor_memory",
                    english.get(ConfigScreen.Feature.CURSOR_MEMORY));
        } finally {
            ConfigManager.get().language = originalLanguage;
        }
    }

    @Test
    void englishProviderFeatureStillMergesWithLocalFeatureInChinese() {
        String originalLanguage = ConfigManager.get().language;
        try {
            UnifiedModIntegration.Classification classification = new UnifiedModIntegration.Classification(
                    ConfigScreen.Category.MAPS,
                    UnifiedModIntegration.ClassificationSource.VERIFIED_RULE, 1.0);
            UnifiedModIntegration.NativeFeature providerFeature = new UnifiedModIntegration.NativeFeature(
                    UnifiedModIntegration.Provider.SKYHANNI, "maps:fairy_souls",
                    "Fairy Soul Waypoints", "Provider description", classification,
                    "Waypoints", null, List.of());

            ConfigManager.get().language = "zh_cn";
            List<UnifiedModIntegration.UnifiedFeature> features =
                    UnifiedModIntegration.buildFeatures(List.of(providerFeature));
            UnifiedModIntegration.UnifiedFeature merged = features.stream()
                    .filter(feature -> feature.qcloudyFeature
                            == ConfigScreen.Feature.FAIRY_SOUL_WAYPOINTS)
                    .findFirst().orElse(null);

            assertNotNull(merged);
            assertEquals("maps:fairy_souls", merged.id);
            assertEquals(1, merged.external.size());
            assertEquals(UnifiedModIntegration.Provider.SKYHANNI,
                    merged.external.getFirst().provider);
        } finally {
            ConfigManager.get().language = originalLanguage;
        }
    }

    @Test
    void providerHudDiscoveryIsEmptyWhileItsMasterSwitchIsOff() {
        assertFalse(ConfigManager.get().integrations.unifiedHudEditor);
        assertTrue(UnifiedModIntegration.externalHuds().isEmpty());
    }

    @Test
    void prefixedFutureVersionTogglesUseReadableFeatureNamesAndStableStems() {
        assertEquals("Commissions", UnifiedModIntegration.featureTitle("enabledCommissions"));
        assertEquals("Price Display", UnifiedModIntegration.featureTitle("showPriceDisplay"));
        assertEquals("commissions", UnifiedModIntegration.semanticStem("enabledCommissions"));
        assertEquals("pricedisplay", UnifiedModIntegration.semanticStem("showPriceDisplay"));
    }

    @Test
    void capabilityDiscoveryAssociatesOnlySettingsFromTheSameFunctionStem() {
        assertTrue(UnifiedModIntegration.relatedToStem("commissionsX", "commissions"));
        assertTrue(UnifiedModIntegration.relatedToStem("commissionScale", "commission"));
        assertTrue(UnifiedModIntegration.relatedToStem("priceDisplayMode", "pricedisplay"));
        assertFalse(UnifiedModIntegration.relatedToStem("powderX", "commissions"));
    }

    @Test
    void prefixedHudCoordinatesAreRecognisedWithoutVersionSpecificFieldLists() {
        assertEquals("x", UnifiedModIntegration.coordinateRole("commissionsX"));
        assertEquals("y", UnifiedModIntegration.coordinateRole("powderY"));
        assertEquals("scale", UnifiedModIntegration.coordinateRole("priceDisplayScale"));
        assertNull(UnifiedModIntegration.coordinateRole("maximumPrice"));
    }

    @Test
    void compatibilityReportMergesOnlyUnavailableCapabilitiesPerProviderFeature() {
        List<UnifiedModIntegration.CompatibilityGap> result = UnifiedModIntegration.mergeCompatibilityGaps(List.of(
                new UnifiedModIntegration.CompatibilityGap(
                        UnifiedModIntegration.Provider.SKYHANNI, "Price Display", true, false),
                new UnifiedModIntegration.CompatibilityGap(
                        UnifiedModIntegration.Provider.SKYHANNI, "price display", false, true),
                new UnifiedModIntegration.CompatibilityGap(
                        UnifiedModIntegration.Provider.SKYBLOCKER, "Commissions", false, true),
                new UnifiedModIntegration.CompatibilityGap(
                        UnifiedModIntegration.Provider.FIRMAMENT, "Working Feature", false, false),
                new UnifiedModIntegration.CompatibilityGap(
                        UnifiedModIntegration.Provider.QCLOUDY, "Local Feature", true, true)
        ));

        assertEquals(2, result.size());
        assertEquals(UnifiedModIntegration.Provider.SKYHANNI, result.get(0).provider());
        assertEquals("Price Display", result.get(0).feature());
        assertTrue(result.get(0).settings());
        assertTrue(result.get(0).hud());
        assertEquals(UnifiedModIntegration.Provider.SKYBLOCKER, result.get(1).provider());
        assertFalse(result.get(1).settings());
        assertTrue(result.get(1).hud());
    }

    @Test
    void feeshDelegatedPropertiesGroupOnlyDeterministicallyRelatedSettings() {
        assertTrue(UnifiedModIntegration.feeshRelationScore(
                "alertOnRareDrops", "alertOnRareDropsSource") > 0);
        assertTrue(UnifiedModIntegration.feeshRelationScore(
                "alertOnRareDrops", "rareDropAlertShowPriceFor") > 0);
        assertTrue(UnifiedModIntegration.feeshRelationScore(
                "fishingProfitTrackerOverlay", "fishingProfitTrackerHideCheaperThan") > 0);
        assertEquals(0, UnifiedModIntegration.feeshRelationScore(
                "fishingProfitTrackerOverlay", "seaCreaturesTrackerShowTop"));
        assertTrue(UnifiedModIntegration.feeshRootPriority("fishingProfitTrackerOverlay")
                > UnifiedModIntegration.feeshRootPriority("fishingProfitTrackerCustomStyle"));
    }

    @Test
    void feeshOverlayCoordinatesRoundTripAllNativeAlignmentAnchors() {
        for (String alignment : List.of("LEFT", "CENTER", "RIGHT")) {
            int anchor = UnifiedModIntegration.feeshAnchorX(120, 80, alignment);
            assertEquals(120, UnifiedModIntegration.feeshLeftEdge(anchor, 80, alignment));
        }
    }

    @Test
    void feeshOverlayFeatureNamesDoNotExposeImplementationSuffixes() {
        assertEquals("Fishing Profit Tracker",
                UnifiedModIntegration.feeshFeatureTitle("fishingProfitTrackerOverlay"));
        assertEquals("Alert On Rare Drops",
                UnifiedModIntegration.feeshFeatureTitle("alertOnRareDrops"));
    }

    private static Map<ConfigScreen.Feature, String> localFeatureIds(
            List<UnifiedModIntegration.UnifiedFeature> features) {
        Map<ConfigScreen.Feature, String> result = new EnumMap<>(ConfigScreen.Feature.class);
        Set<String> ids = new HashSet<>();
        for (UnifiedModIntegration.UnifiedFeature feature : features) {
            if (feature.qcloudyFeature == null) continue;
            assertTrue(ids.add(feature.id), "Duplicate local feature ID: " + feature.id);
            result.put(feature.qcloudyFeature, feature.id);
        }
        return result;
    }
}

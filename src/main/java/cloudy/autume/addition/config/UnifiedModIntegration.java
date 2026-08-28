package cloudy.autume.addition.config;

import cloudy.autume.addition.i18n.ModText;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.joml.Vector2i;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/**
 * Optional, reflection-only adapters for supported SkyBlock mods.
 *
 * <p>No external mod is a compile or runtime dependency. An adapter is only
 * enabled only when its required live configuration and save capabilities are
 * present. Version strings are deliberately not used as a compatibility gate:
 * recognised fields from newer provider versions remain editable, while
 * unknown structures are skipped. Reads and writes go through the installed
 * mod's live configuration object and save hook; this deliberately avoids
 * editing another mod's JSON file behind its back.</p>
 */
final class UnifiedModIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger("QCloudy_Addition/UnifiedIntegration");
    private static final int MAX_SCAN_DEPTH = 5;
    private static final int MAX_SETTINGS_PER_FEATURE = 48;
    private static volatile List<UnifiedFeature> cached;
    private static volatile ScanSnapshot stableScan = ScanSnapshot.empty();
    private static volatile ScanProgress scanProgress = ScanProgress.idle();
    private static @Nullable ScanJob scanJob;

    /**
     * Stable identities for QCA features that existed before local feature IDs
     * stopped being derived from their translated display titles.
     *
     * <p>The values intentionally match the canonical IDs produced by the old
     * English UI. Keeping them here both makes IDs independent of the selected
     * language and preserves existing {@code selectedProviders} entries. New
     * features that are not listed fall back to their enum name, which is also
     * stable and language independent.</p>
     */
    private static final Map<String, String> LEGACY_LOCAL_FEATURE_IDS = Map.ofEntries(
            Map.entry("HUD_ANIMATIONS", "general:ui_animations"),
            Map.entry("HUNTING_ALERT_SOUND", "general:alert_sound_master"),
            Map.entry("UNIFIED_SETTINGS_EDITOR", "general:manage_other_mod_settings"),
            Map.entry("UNIFIED_HUD_EDITOR", "general:manage_other_mod_huds"),
            Map.entry("MANUAL_RECONNECT", "general:manual_reconnect_button"),
            Map.entry("PARTY_AUTO_ACCEPT", "general:friend_party_auto_accept"),
            Map.entry("FISHING_BITE_ALERT", "fishing:fishing_bite_sound"),
            Map.entry("DWARVEN_MAP", "maps:dwarven_mines_map"),
            Map.entry("GLACITE_MAP", "maps:glacite_tunnels_map"),
            Map.entry("FAIRY_SOUL_WAYPOINTS", "maps:fairy_soul_waypoints"),
            Map.entry("MINING_TRACKER", "mining:mining_tasks_powders"),
            Map.entry("TORRHUS_TRACKER", "foraging:torrhus_chapter_resources"),
            Map.entry("GALATEA_TRACKER", "foraging:galatea_chapter_resources"),
            Map.entry("TREE_CRITTER_TIMER", "foraging:tree_critter_timer"),
            Map.entry("MIRIA_CONTEST", "foraging:miria_contest_hud"),
            Map.entry("AGATHA_CONTEST", "foraging:agatha_contest_hud"),
            Map.entry("BENEFACTOR_HUD", "foraging:benefactor_status_hud"),
            Map.entry("TREE_GIFT_ALERTS", "foraging:rare_tree_gift_alerts"),
            Map.entry("BEEHEEMOTH_HELPER", "hunting:beeheemoth_helper"),
            Map.entry("LASSO_REEL_SOUND", "hunting:lasso_reel_sound"),
            Map.entry("CRITTER_BEHAVIOR", "hunting:critter_behavior_assistant"),
            Map.entry("COLD_SAFETY", "hunting:cold_safety_alert"),
            Map.entry("DOOMSPIRAL_READY", "hunting:doomspiral_ready_alert"),
            Map.entry("WARDEN_READY_ALERT", "hunting:warden_capture_ready_alert"),
            Map.entry("SAFARI_CRITTER_HIGHLIGHT", "hunting:safari_critter_highlight"),
            Map.entry("SAFARI_DASHBOARD", "hunting:safari_run_dashboard"),
            Map.entry("SAFARI_SHARD_STATS", "hunting:captured_shard_stats"),
            Map.entry("SAFARI_CRITTERDEX", "hunting:safari_run_critterdex"),
            Map.entry("SPARKLING_ALERT", "hunting:sparkling_critter_alert"),
            Map.entry("FLOOR_QUEST_ASSISTANT", "hunting:floor_drop_quest_items"),
            Map.entry("WUMPA_HUD", "hunting:wumpa_encounter_hud"),
            Map.entry("SNOOZLE_WALL_OVERLAY", "hunting:snoozle_wall_overlay"),
            Map.entry("SAFARI_BELT", "hunting:safari_belt_details"),
            Map.entry("CRIMSON_TASKS", "combat:crimson_isle_tasks"),
            Map.entry("DEATH_SAVE_ALERTS", "combat:death_save_center_alerts"),
            Map.entry("SPIRIT_MASK_COOLDOWN_HUD", "combat:spirit_mask_cooldown_hud"),
            Map.entry("BONZO_MASK_COOLDOWN_HUD", "combat:bonzo_s_mask_cooldown_hud"),
            Map.entry("PHOENIX_COOLDOWN_HUD", "combat:phoenix_rekindle_cooldown_hud"),
            Map.entry("DEPLOYABLE_EXPIRY_ALERT", "combat:power_orb_sos_despawn_alert"),
            Map.entry("DRAGON_HIGHLIGHT", "combat:ender_dragon_highlight"),
            Map.entry("PET_HUD", "items_and_menus:equipped_pet_hud"),
            Map.entry("CENTURY_CAKE_EFFECTS", "items_and_menus:century_cake_effect_expiry_alert"),
            Map.entry("SHARD_FUSION_HELPER", "items_and_menus:shard_fusion_helper"),
            Map.entry("CHAT_PEEK", "general:chat_peek"),
            Map.entry("ITEM_TIMESTAMPS", "items_and_menus:item_timestamps"),
            Map.entry("CURSOR_MEMORY", "items_and_menus:save_cursor_position"),
            Map.entry("TELEPORT_SOUNDS", "items_and_menus:aote_aotv_sound_settings")
    );

    private UnifiedModIntegration() { }

    enum Provider {
        QCLOUDY("qcloudy_addition", "QCloudy"),
        SKYHANNI("skyhanni", "SkyHanni"),
        SKYBLOCKER("skyblocker", "SkyBlocker"),
        FIRMAMENT("firmament", "Firmament"),
        BABYZOMBIE("babyzombieaddons", "BabyZombieAddons"),
        FEESH("feesh", "Feesh");

        final String modId;
        final String displayName;

        Provider(String modId, String displayName) {
            this.modId = modId;
            this.displayName = displayName;
        }
    }

    enum ValueKind { BOOLEAN, INTEGER, DECIMAL, ENUM, STRING, UNSUPPORTED }

    enum ClassificationSource { VERIFIED_RULE, LOCAL_CLASSIFIER, UNCLASSIFIED }

    record Classification(ConfigScreen.Category category, ClassificationSource source,
                          double confidence) { }

    enum ScanView { SETTINGS, HUD }

    enum ScanState { IDLE, SCANNING, READY, PARTIAL, FAILED }

    enum ScanPhase { IDLE, DETECTING, READING, CLASSIFYING, VALIDATING, COMPLETE }

    static final class NativeSetting {
        final String id;
        final String label;
        final ValueKind kind;
        final @Nullable Double minimum;
        final @Nullable Double maximum;
        private final ValueAccess access;

        NativeSetting(String id, String label, ValueKind kind, @Nullable Double minimum,
                      @Nullable Double maximum, ValueAccess access) {
            this.id = id;
            this.label = label;
            this.kind = kind;
            this.minimum = minimum;
            this.maximum = maximum;
            this.access = access;
        }

        @Nullable Object value() {
            try {
                return access.get();
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        boolean set(@Nullable Object value) {
            try {
                access.set(value);
                return true;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return false;
            }
        }

        String displayValue() {
            Object value = value();
            if (value == null) return ModText.get("config.integration.unavailable");
            if (value instanceof Boolean bool) {
                return ModText.get(bool ? "config.enabled" : "config.disabled");
            }
            if (value instanceof Number number) {
                if (kind == ValueKind.INTEGER) return Long.toString(number.longValue());
                return String.format(Locale.ROOT, "%.2f", number.doubleValue())
                        .replaceAll("0+$", "").replaceAll("\\.$", "");
            }
            if (value instanceof Enum<?> enumeration) return humanize(enumeration.name());
            return value.toString();
        }

        boolean toggleOrCycle() {
            Object current = value();
            if (current instanceof Boolean bool) return set(!bool);
            if (current instanceof Enum<?> enumeration) {
                Object[] constants = enumeration.getDeclaringClass().getEnumConstants();
                if (constants == null || constants.length == 0) return false;
                return set(constants[(enumeration.ordinal() + 1) % constants.length]);
            }
            return false;
        }

        double sliderFraction() {
            Object current = value();
            if (!(current instanceof Number number) || minimum == null || maximum == null
                    || maximum <= minimum) return 0.0;
            return Math.clamp((number.doubleValue() - minimum) / (maximum - minimum), 0.0, 1.0);
        }

        boolean setSliderFraction(double fraction) {
            if (minimum == null || maximum == null) return false;
            double number = minimum + Math.clamp(fraction, 0.0, 1.0) * (maximum - minimum);
            return set(kind == ValueKind.INTEGER ? (int) Math.round(number) : number);
        }

        boolean editable() {
            return kind == ValueKind.BOOLEAN || kind == ValueKind.ENUM
                    || ((kind == ValueKind.INTEGER || kind == ValueKind.DECIMAL)
                    && minimum != null && maximum != null && maximum > minimum);
        }
    }

    static final class NativeFeature {
        final Provider provider;
        final String id;
        final String title;
        final String description;
        final ConfigScreen.Category category;
        final Classification classification;
        final String group;
        final NativeSetting primary;
        final List<NativeSetting> settings;

        NativeFeature(Provider provider, String id, String title, String description,
                      Classification classification, String group, NativeSetting primary,
                      List<NativeSetting> settings) {
            this.provider = provider;
            this.id = id;
            this.title = title;
            this.description = description;
            this.category = classification.category;
            this.classification = classification;
            this.group = group;
            this.primary = primary;
            this.settings = List.copyOf(settings);
        }

        boolean enabled() {
            Object value = primary.value();
            if (value instanceof Boolean bool) return bool;
            if (value instanceof Enum<?> enumeration) {
                String name = enumeration.name().toUpperCase(Locale.ROOT);
                return !Set.of("OFF", "DISABLED", "NONE", "HIDDEN").contains(name);
            }
            return value != null;
        }

        boolean setEnabled(boolean enabled) {
            Object current = primary.value();
            if (current instanceof Boolean) return primary.set(enabled);
            if (current instanceof Enum<?> enumeration) {
                Object[] values = enumeration.getDeclaringClass().getEnumConstants();
                if (values == null || values.length == 0) return false;
                Object disabled = values[0];
                Object active = values[0];
                for (Object value : values) {
                    String name = ((Enum<?>) value).name().toUpperCase(Locale.ROOT);
                    if (Set.of("OFF", "DISABLED", "NONE", "HIDDEN").contains(name)) disabled = value;
                    else if (active == values[0]) active = value;
                }
                return primary.set(enabled ? active : disabled);
            }
            return false;
        }
    }

    /**
     * One installed-provider function that QCA can identify but cannot fully
     * expose through one or both unified editors.
     *
     * <p>This is deliberately a diagnostic result, not a feature toggle. The
     * report never guesses names for unknown future structures and never
     * writes a provider value while testing compatibility.</p>
     */
    record CompatibilityGap(Provider provider, String feature, boolean settings, boolean hud,
                            boolean classification) {
        CompatibilityGap(Provider provider, String feature, boolean settings, boolean hud) {
            this(provider, feature, settings, hud, false);
        }
    }

    static final class UnifiedFeature {
        final String id;
        final String title;
        final String description;
        final ConfigScreen.Category category;
        final String group;
        final ConfigScreen.@Nullable Feature qcloudyFeature;
        final List<NativeFeature> external;

        UnifiedFeature(String id, String title, String description, ConfigScreen.Category category,
                       String group, ConfigScreen.@Nullable Feature qcloudyFeature,
                       List<NativeFeature> external) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.category = category;
            this.group = group;
            this.qcloudyFeature = qcloudyFeature;
            this.external = List.copyOf(external);
        }

        List<Provider> providers() {
            List<Provider> providers = new ArrayList<>();
            if (qcloudyFeature != null) providers.add(Provider.QCLOUDY);
            for (NativeFeature feature : external) {
                if (!providers.contains(feature.provider)) providers.add(feature.provider);
            }
            return List.copyOf(providers);
        }

        Provider selectedProvider() {
            List<Provider> available = providers();
            if (available.isEmpty()) return Provider.QCLOUDY;
            String saved = ConfigManager.get().integrations.selectedProviders.get(id);
            if (saved != null) {
                try {
                    Provider provider = Provider.valueOf(saved);
                    if (available.contains(provider)) return provider;
                } catch (IllegalArgumentException ignored) { }
            }
            return available.getFirst();
        }

        void selectProvider(Provider provider) {
            if (!providers().contains(provider)) return;
            ConfigManager.get().integrations.selectedProviders.put(id, provider.name());
            ConfigManager.save();
        }

        Provider cycleProvider() {
            List<Provider> values = providers();
            if (values.isEmpty()) return Provider.QCLOUDY;
            int index = values.indexOf(selectedProvider());
            Provider result = values.get((index + 1) % values.size());
            selectProvider(result);
            return result;
        }

        boolean enabled() {
            Provider selected = selectedProvider();
            if (selected == Provider.QCLOUDY && qcloudyFeature != null) {
                return qcloudyFeature.enabled(ConfigManager.get());
            }
            NativeFeature binding = binding(selected);
            return binding != null && binding.enabled();
        }

        boolean toggle() {
            boolean next = !enabled();
            Provider selected = selectedProvider();
            if (next) {
                // Only providers attached to this exact logical feature are
                // mutually exclusive. Related price/profit features remain independent.
                if (qcloudyFeature != null && selected != Provider.QCLOUDY
                        && qcloudyFeature.enabled(ConfigManager.get())) qcloudyFeature.toggle(ConfigManager.get());
                for (NativeFeature binding : external) {
                    if (binding.provider != selected && binding.enabled()) binding.setEnabled(false);
                }
            }
            boolean changed;
            if (selected == Provider.QCLOUDY && qcloudyFeature != null) {
                if (qcloudyFeature.enabled(ConfigManager.get()) != next) qcloudyFeature.toggle(ConfigManager.get());
                changed = true;
            } else {
                NativeFeature binding = binding(selected);
                changed = binding != null && binding.setEnabled(next);
            }
            ConfigManager.save();
            if (qcloudyFeature == ConfigScreen.Feature.UNIFIED_SETTINGS_EDITOR
                    || qcloudyFeature == ConfigScreen.Feature.UNIFIED_HUD_EDITOR) {
                onMasterToggleChanged();
            }
            return changed;
        }

        @Nullable NativeFeature binding(Provider provider) {
            for (NativeFeature feature : external) if (feature.provider == provider) return feature;
            return null;
        }
    }

    /**
     * A movable HUD owned by the currently selected external provider.
     *
     * <p>The editor never invents a separate QCloudy position. It writes the
     * provider's own recognised live x/y/scale values, so reopening that mod's
     * native editor shows the same result.</p>
     */
    static final class ExternalHud {
        final UnifiedFeature feature;
        final NativeFeature binding;
        final NativeSetting x;
        final NativeSetting y;
        final @Nullable NativeSetting scale;
        private final BooleanSupplier visible;

        ExternalHud(UnifiedFeature feature, NativeFeature binding, NativeSetting x,
                    NativeSetting y, @Nullable NativeSetting scale) {
            this(feature, binding, x, y, scale, () -> true);
        }

        ExternalHud(UnifiedFeature feature, NativeFeature binding, NativeSetting x,
                    NativeSetting y, @Nullable NativeSetting scale, BooleanSupplier visible) {
            this.feature = feature;
            this.binding = binding;
            this.x = x;
            this.y = y;
            this.scale = scale;
            this.visible = visible;
        }

        String id() {
            // One native HUD can be surfaced by more than one Boolean option
            // in a provider config. The recognised position path is the stable
            // identity; including the feature binding would duplicate the
            // same live panel in QCA's editor.
            return binding.provider.name() + ":" + x.id;
        }

        String label() {
            return binding.provider.displayName + " · " + feature.title;
        }

        int x() {
            Object value = x.value();
            return value instanceof Number number ? number.intValue() : 0;
        }

        int y() {
            Object value = y.value();
            return value instanceof Number number ? number.intValue() : 0;
        }

        float scale() {
            Object value = scale == null ? null : scale.value();
            return value instanceof Number number ? Math.clamp(number.floatValue(), 0.25f, 4.0f) : 1.0f;
        }

        boolean setPosition(int newX, int newY) {
            return x.set(newX) && y.set(newY);
        }

        boolean setScale(float newScale) {
            return scale != null && scale.set(Math.clamp(newScale, 0.25f, 4.0f));
        }

        boolean visible() {
            try {
                return visible.getAsBoolean();
            } catch (RuntimeException | LinkageError ignored) {
                return false;
            }
        }
    }

    record ProviderScan(Provider provider, int discoveredFeatures, int settingsManaged,
                        int hudManaged, boolean partial) { }

    record ScanStatus(ScanState state, ScanPhase phase, int percent,
                      String currentProvider, String currentItem, List<String> recentItems,
                      List<ProviderScan> providers, int managedCount, int unresolvedCount) {
        boolean running() {
            return state == ScanState.SCANNING;
        }
    }

    private record ScanSnapshot(List<NativeFeature> nativeFeatures,
                                List<UnifiedFeature> allFeatures,
                                List<ExternalHud> providerHuds,
                                List<CompatibilityGap> gaps,
                                List<ProviderScan> providers,
                                int settingsManaged,
                                int hudManaged,
                                int unresolvedCount,
                                long revision) {
        static ScanSnapshot empty() {
            return new ScanSnapshot(List.of(), List.of(), List.of(), List.of(), List.of(),
                    0, 0, 0, 0L);
        }

        boolean valid() {
            return revision > 0L;
        }
    }

    private static final class ScanJob {
        final List<Adapter> adapters;
        final List<NativeFeature> nativeFeatures = new ArrayList<>();
        final List<CompatibilityGap> gaps = new ArrayList<>();
        final List<ProviderScan> providers = new ArrayList<>();
        final List<String> recentItems = new ArrayList<>();
        int adapterIndex;
        boolean announced;

        ScanJob(List<Adapter> adapters) {
            this.adapters = List.copyOf(adapters);
        }

        @Nullable Adapter current() {
            return adapterIndex < adapters.size() ? adapters.get(adapterIndex) : null;
        }

        void remember(String value) {
            if (value == null || value.isBlank()) return;
            recentItems.add(value.strip());
            while (recentItems.size() > 4) recentItems.removeFirst();
        }
    }

    private record ScanProgress(ScanState state, ScanPhase phase, int percent,
                                String currentProvider, String currentItem,
                                List<String> recentItems) {
        static ScanProgress idle() {
            return new ScanProgress(ScanState.IDLE, ScanPhase.IDLE, 0, "", "", List.of());
        }
    }

    static List<UnifiedFeature> features() {
        List<UnifiedFeature> result = cached;
        if (result != null) return result;
        synchronized (UnifiedModIntegration.class) {
            if (cached == null) {
                boolean includeExternal = ConfigManager.get().integrations.unifiedSettingsEditor
                        && stableScan.valid();
                cached = buildFeatures(includeExternal ? stableScan.nativeFeatures : List.of());
            }
            return cached;
        }
    }

    static void invalidate() {
        cached = null;
        ScanSnapshot snapshot = stableScan;
        if (snapshot.valid()) {
            stableScan = new ScanSnapshot(snapshot.nativeFeatures,
                    buildFeatures(snapshot.nativeFeatures), snapshot.providerHuds,
                    snapshot.gaps, snapshot.providers, snapshot.settingsManaged,
                    snapshot.hudManaged, snapshot.unresolvedCount, snapshot.revision);
        }
    }

    static void onMasterToggleChanged() {
        invalidate();
        ModConfig.Integrations integrations = ConfigManager.get().integrations;
        if (!integrations.unifiedSettingsEditor && !integrations.unifiedHudEditor) {
            scanJob = null;
            stableScan = ScanSnapshot.empty();
            scanProgress = ScanProgress.idle();
        }
    }

    static boolean requiresScanConfirmation() {
        return scanJob == null && !stableScan.valid();
    }

    static boolean scanRunning() {
        return scanJob != null;
    }

    static void requestConfirmedScan(boolean refresh) {
        if (!ConfigManager.get().integrations.unifiedSettingsEditor
                && !ConfigManager.get().integrations.unifiedHudEditor) return;
        requestScan(refresh);
    }

    private static void requestScan(boolean refresh) {
        if (scanJob != null) return;
        if (!refresh && stableScan.valid()) return;
        List<Adapter> installed = new ArrayList<>();
        for (Adapter adapter : adapters()) if (providerInstalled(adapter.provider())) installed.add(adapter);
        scanJob = new ScanJob(installed);
        scanProgress = new ScanProgress(ScanState.SCANNING, ScanPhase.DETECTING, 2,
                "", "", List.of());
    }

    static void tickScan() {
        ScanJob job = scanJob;
        if (job == null) return;
        Adapter adapter = job.current();
        if (adapter == null) {
            finishScan(job);
            return;
        }

        int total = Math.max(1, job.adapters.size());
        if (!job.announced) {
            job.announced = true;
            int percent = 5 + job.adapterIndex * 80 / total;
            scanProgress = new ScanProgress(ScanState.SCANNING, ScanPhase.READING, percent,
                    adapter.provider().displayName, ModText.get("config.integration.scan.configuration"),
                    List.copyOf(job.recentItems));
            return;
        }

        List<NativeFeature> discovered = List.of();
        boolean partial = false;
        try {
            if (!adapter.available()) {
                partial = true;
                job.gaps.add(new CompatibilityGap(adapter.provider(),
                        ModText.get("config.integration.report.configuration"), true, true));
            } else {
                discovered = adapter.discover();
                if (discovered.isEmpty()) {
                    partial = true;
                    job.gaps.add(new CompatibilityGap(adapter.provider(),
                            ModText.get("config.integration.report.configuration"), true, true));
                } else {
                    job.nativeFeatures.addAll(discovered);
                    auditFeatureGaps(discovered, job.gaps);
                    partial = job.gaps.stream().anyMatch(gap -> gap.provider == adapter.provider());
                }
                job.gaps.addAll(adapter.gaps());
                partial |= job.gaps.stream().anyMatch(gap -> gap.provider == adapter.provider());
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            partial = true;
            LOGGER.warn("Skipping unreadable {} configuration branches while preserving the previous scan snapshot",
                    adapter.provider().displayName, exception);
            job.gaps.add(new CompatibilityGap(adapter.provider(),
                    ModText.get("config.integration.report.configuration"), true, true));
        }

        int settingsManaged = 0;
        for (NativeFeature feature : discovered) {
            if (feature.primary.editable() && feature.primary.value() != null) settingsManaged++;
        }
        job.providers.add(new ProviderScan(adapter.provider(), discovered.size(), settingsManaged, 0, partial));
        if (!discovered.isEmpty()) {
            NativeFeature last = discovered.getLast();
            job.remember(adapter.provider().displayName + " · " + last.title);
        } else {
            job.remember(adapter.provider().displayName + " · "
                    + ModText.get("config.integration.scan.partial"));
        }
        job.adapterIndex++;
        job.announced = false;
        int percent = 5 + job.adapterIndex * 80 / total;
        scanProgress = new ScanProgress(ScanState.SCANNING, ScanPhase.CLASSIFYING, percent,
                adapter.provider().displayName,
                discovered.isEmpty() ? ModText.get("config.integration.scan.no_features")
                        : discovered.getLast().title,
                List.copyOf(job.recentItems));
    }

    private static void finishScan(ScanJob job) {
        scanProgress = new ScanProgress(ScanState.SCANNING, ScanPhase.VALIDATING, 92,
                "", ModText.get("config.integration.scan.snapshot"), List.copyOf(job.recentItems));
        List<UnifiedFeature> allFeatures = buildFeatures(job.nativeFeatures);
        List<ExternalHud> providerHuds = new ArrayList<>(babyZombieHuds());
        providerHuds.addAll(feeshHuds(job.gaps));
        collectBabyZombieHudGaps(job.gaps);

        Map<Provider, Set<String>> hudIds = new LinkedHashMap<>();
        for (UnifiedFeature feature : allFeatures) {
            for (NativeFeature binding : feature.external) {
                NativeSetting x = hudSetting(binding.settings, "x");
                NativeSetting y = hudSetting(binding.settings, "y");
                if (x == null || y == null || x.value() == null || y.value() == null) continue;
                hudIds.computeIfAbsent(binding.provider, ignored -> new java.util.LinkedHashSet<>())
                        .add(binding.provider.name() + ":" + x.id);
            }
        }
        for (ExternalHud hud : providerHuds) {
            hudIds.computeIfAbsent(hud.binding.provider, ignored -> new java.util.LinkedHashSet<>())
                    .add(hud.id());
        }

        List<CompatibilityGap> gaps = mergeCompatibilityGaps(job.gaps);
        List<ProviderScan> providers = new ArrayList<>();
        for (ProviderScan provider : job.providers) {
            boolean providerPartial = provider.partial || gaps.stream()
                    .anyMatch(gap -> gap.provider == provider.provider);
            providers.add(new ProviderScan(provider.provider, provider.discoveredFeatures,
                    provider.settingsManaged,
                    hudIds.getOrDefault(provider.provider, Set.of()).size(), providerPartial));
        }
        int settingsManaged = providers.stream().mapToInt(ProviderScan::settingsManaged).sum();
        int hudManaged = hudIds.values().stream().mapToInt(Set::size).sum();
        int unresolved = (int) job.nativeFeatures.stream()
                .filter(feature -> feature.classification.source == ClassificationSource.UNCLASSIFIED).count();
        boolean partial = providers.stream().anyMatch(ProviderScan::partial) || !gaps.isEmpty() || unresolved > 0;

        stableScan = new ScanSnapshot(List.copyOf(job.nativeFeatures), allFeatures,
                List.copyOf(providerHuds), gaps,
                List.copyOf(providers), settingsManaged, hudManaged, unresolved, System.nanoTime());
        cached = null;
        scanJob = null;
        scanProgress = new ScanProgress(partial ? ScanState.PARTIAL : ScanState.READY,
                ScanPhase.COMPLETE, 100, "", ModText.get("config.integration.scan.complete"),
                List.copyOf(job.recentItems));
    }

    static ScanStatus scanStatus(ScanView view) {
        ScanProgress progress = scanProgress;
        ScanSnapshot snapshot = stableScan;
        int managed = view == ScanView.SETTINGS ? snapshot.settingsManaged : snapshot.hudManaged;
        return new ScanStatus(progress.state, progress.phase, progress.percent,
                progress.currentProvider, progress.currentItem, progress.recentItems,
                snapshot.providers, managed, snapshot.unresolvedCount);
    }

    /**
     * Returns unresolved capabilities from the most recently completed,
     * immutable scan snapshot. Opening the report never starts another scan
     * and never writes provider state.
     */
    static List<CompatibilityGap> compatibilityGaps() {
        return stableScan.gaps;
    }

    static List<Provider> installedExternalProviders() {
        List<Provider> result = new ArrayList<>();
        for (ProviderScan provider : stableScan.providers) result.add(provider.provider);
        return List.copyOf(result);
    }

    private static void auditFeatureGaps(List<NativeFeature> features, List<CompatibilityGap> gaps) {
        for (NativeFeature feature : features) {
            boolean settingsGap = !feature.primary.editable() || feature.primary.value() == null;
            boolean hasX = false;
            boolean hasY = false;
            boolean xReady = false;
            boolean yReady = false;
            boolean hudGap = false;
            for (NativeSetting setting : feature.settings) {
                String segment = setting.id.substring(setting.id.lastIndexOf('.') + 1);
                String role = coordinateRole(segment);
                if ("x".equals(role)) {
                    hasX = true;
                    xReady |= setting.editable() && setting.value() != null;
                } else if ("y".equals(role)) {
                    hasY = true;
                    yReady |= setting.editable() && setting.value() != null;
                } else if (!setting.editable() || setting.value() == null) {
                    String normalized = setting.id.toLowerCase(Locale.ROOT);
                    if (normalized.contains("hud") || normalized.contains("position")) hudGap = true;
                    else settingsGap = true;
                }
            }
            hudGap |= (hasX || hasY) && !(hasX && hasY && xReady && yReady);
            boolean classificationGap = feature.classification.source == ClassificationSource.UNCLASSIFIED;
            if (settingsGap || hudGap || classificationGap) {
                gaps.add(new CompatibilityGap(feature.provider, feature.title,
                        settingsGap, hudGap, classificationGap));
            }
        }
    }

    static List<CompatibilityGap> mergeCompatibilityGaps(List<CompatibilityGap> gaps) {
        Map<String, CompatibilityGap> merged = new LinkedHashMap<>();
        for (CompatibilityGap gap : gaps) {
            if (gap == null || gap.provider == Provider.QCLOUDY || gap.feature == null
                    || gap.feature.isBlank() || (!gap.settings && !gap.hud && !gap.classification)) continue;
            String key = gap.provider.name() + ":" + gap.feature.strip().toLowerCase(Locale.ROOT);
            CompatibilityGap current = merged.get(key);
            if (current == null) {
                merged.put(key, new CompatibilityGap(gap.provider, gap.feature.strip(), gap.settings,
                        gap.hud, gap.classification));
            } else {
                merged.put(key, new CompatibilityGap(current.provider, current.feature,
                        current.settings || gap.settings, current.hud || gap.hud,
                        current.classification || gap.classification));
            }
        }
        List<CompatibilityGap> result = new ArrayList<>(merged.values());
        result.sort(Comparator.comparing((CompatibilityGap gap) -> gap.provider.ordinal())
                .thenComparing(CompatibilityGap::feature, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    static @Nullable UnifiedFeature forQCloudy(ConfigScreen.Feature feature) {
        for (UnifiedFeature unified : features()) if (unified.qcloudyFeature == feature) return unified;
        return null;
    }

    static List<ExternalHud> externalHuds() {
        ModConfig.Integrations integrations = ConfigManager.get().integrations;
        if (!integrations.unifiedHudEditor || !stableScan.valid()) return List.of();
        List<UnifiedFeature> available = stableScan.allFeatures;
        Map<String, ExternalHud> result = new LinkedHashMap<>();
        for (UnifiedFeature feature : available) {
            Provider selected = feature.selectedProvider();
            if (selected == Provider.QCLOUDY || !feature.enabled()) continue;
            NativeFeature binding = feature.binding(selected);
            if (binding == null) continue;
            NativeSetting x = hudSetting(binding.settings, "x");
            NativeSetting y = hudSetting(binding.settings, "y");
            if (x == null || y == null || x.value() == null || y.value() == null) continue;
            NativeSetting scale = hudSetting(binding.settings, "scale");
            ExternalHud hud = new ExternalHud(feature, binding, x, y, scale);
            if (hud.visible()) result.putIfAbsent(hud.id(), hud);
        }
        for (ExternalHud hud : stableScan.providerHuds) {
            if (hud.visible()) result.putIfAbsent(hud.id(), hud);
        }
        return List.copyOf(result.values());
    }

    /**
     * Reads Feesh's live overlay registry without taking a ResourcefulConfig
     * dependency. Positions are converted from Feesh's alignment anchor to
     * the top-left coordinates used by QCA's shared editor, then converted
     * back through Feesh's own setters when the player releases a drag.
     */
    private static List<ExternalHud> feeshHuds(List<CompatibilityGap> gaps) {
        if (!providerInstalled(Provider.FEESH)) return List.of();
        try {
            Class<?> guiType = Class.forName("com.github.sleepypanda.feesh.utils.gui.FeeshGui");
            Object companion = guiType.getField("Companion").get(null);
            Object rawGuis = companion.getClass().getMethod("getAllRegisteredGuis").invoke(companion);
            if (!(rawGuis instanceof Collection<?> guis)) {
                gaps.add(new CompatibilityGap(Provider.FEESH,
                        ModText.get("config.integration.report.hud_registry"), false, true));
                return List.of();
            }
            List<ExternalHud> result = new ArrayList<>();
            for (Object gui : guis) {
                String key = "";
                try {
                    key = String.valueOf(gui.getClass().getMethod("getCoordsDataKey").invoke(gui));
                    if (key.isBlank()) {
                        gaps.add(new CompatibilityGap(Provider.FEESH,
                                ModText.get("config.integration.report.hud_registry"), false, true));
                        continue;
                    }
                    String stableKey = key;
                    String title = humanize(stableKey);
                    BooleanSupplier visible = () -> feeshHudVisible(gui);
                    NativeSetting primary = new NativeSetting("hud." + stableKey + ".visible", title,
                            ValueKind.BOOLEAN, null, null, new ValueAccess() {
                        @Override public Object get() {
                            return feeshFunctionValue(invokeQuietly(gui, "getSettingsKey"), true);
                        }

                        @Override public void set(@Nullable Object value) {
                            throw new UnsupportedOperationException("Visibility is owned by Feesh settings");
                        }
                    });
                    NativeSetting x = feeshHudX(gui, stableKey);
                    NativeSetting y = feeshHudY(gui, stableKey);
                    NativeSetting scale = feeshHudScale(gui, stableKey);
                    NativeSetting alignment = feeshHudAlignment(gui, stableKey);
                    List<NativeSetting> settings = List.of(x, y, scale, alignment);
                    Classification classification = classifyFeesh("Overlays", stableKey, title);
                    NativeFeature binding = new NativeFeature(Provider.FEESH,
                            canonicalId(classification.category, title), title,
                            Provider.FEESH.displayName + " HUD", classification, "Overlays", primary, settings);
                    UnifiedFeature feature = new UnifiedFeature(binding.id, binding.title, binding.description,
                            binding.category, binding.group, null, List.of(binding));
                    result.add(new ExternalHud(feature, binding, x, y, scale, visible));
                } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                    gaps.add(new CompatibilityGap(Provider.FEESH,
                            key.isBlank() ? ModText.get("config.integration.report.hud_registry") : humanize(key),
                            false, true));
                    LOGGER.debug("Skipping one changed Feesh HUD while preserving sibling HUDs", exception);
                }
            }
            return List.copyOf(result);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            gaps.add(new CompatibilityGap(Provider.FEESH,
                    ModText.get("config.integration.report.hud_registry"), false, true));
            return List.of();
        }
    }

    private static boolean feeshHudVisible(Object gui) {
        Object setting = invokeQuietly(gui, "getSettingsKey");
        if (!feeshFunctionValue(setting, true)) return false;
        Object condition = invokeQuietly(gui, "getCondition");
        if (!feeshFunctionValue(condition, true)) return false;
        Object lines = invokeQuietly(gui, "getLines");
        // Feesh itself returns before drawing when its line collection is
        // empty. Mirroring that condition prevents an empty QCA edit panel.
        return lines instanceof Collection<?> collection && !collection.isEmpty();
    }

    private static boolean feeshFunctionValue(@Nullable Object function, boolean fallback) {
        if (function == null) return fallback;
        try {
            Object value = function.getClass().getMethod("invoke").invoke(function);
            return value instanceof Boolean bool ? bool : fallback;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static @Nullable Object invokeQuietly(Object owner, String method) {
        try {
            return owner.getClass().getMethod(method).invoke(owner);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static NativeSetting feeshHudX(Object gui, String key) {
        return new NativeSetting("hud." + key + ".x", "X", ValueKind.INTEGER,
                -4096.0, 4096.0, new ValueAccess() {
            @Override public Object get() throws ReflectiveOperationException {
                int anchor = ((Number) gui.getClass().getMethod("getX").invoke(gui)).intValue();
                return feeshLeftEdge(anchor, feeshHudWidth(gui), feeshAlignmentName(gui));
            }

            @Override public void set(@Nullable Object value) throws ReflectiveOperationException {
                int left = ((Number) value).intValue();
                int anchor = feeshAnchorX(left, feeshHudWidth(gui), feeshAlignmentName(gui));
                gui.getClass().getMethod("setX", int.class).invoke(gui, anchor);
            }
        });
    }

    private static NativeSetting feeshHudY(Object gui, String key) {
        return new NativeSetting("hud." + key + ".y", "Y", ValueKind.INTEGER,
                -4096.0, 4096.0, new ValueAccess() {
            @Override public Object get() throws ReflectiveOperationException {
                return gui.getClass().getMethod("getY").invoke(gui);
            }

            @Override public void set(@Nullable Object value) throws ReflectiveOperationException {
                gui.getClass().getMethod("setY", int.class).invoke(gui, ((Number) value).intValue());
                saveFeeshHud(gui, key);
            }
        });
    }

    private static NativeSetting feeshHudScale(Object gui, String key) {
        return new NativeSetting("hud." + key + ".scale", ModText.get("config.setting.scale"),
                ValueKind.DECIMAL, 0.2, 4.0, new ValueAccess() {
            @Override public Object get() throws ReflectiveOperationException {
                return gui.getClass().getMethod("getScale").invoke(gui);
            }

            @Override public void set(@Nullable Object value) throws ReflectiveOperationException {
                gui.getClass().getMethod("setScale", float.class)
                        .invoke(gui, ((Number) value).floatValue());
                saveFeeshHud(gui, key);
            }
        });
    }

    private static NativeSetting feeshHudAlignment(Object gui, String key) throws ReflectiveOperationException {
        Object current = gui.getClass().getMethod("getAlignment").invoke(gui);
        return new NativeSetting("hud." + key + ".alignment", humanize("alignment"),
                ValueKind.ENUM, null, null, new ValueAccess() {
            @Override public Object get() throws ReflectiveOperationException {
                return gui.getClass().getMethod("getAlignment").invoke(gui);
            }

            @Override public void set(@Nullable Object value) throws ReflectiveOperationException {
                Object old = gui.getClass().getMethod("getAlignment").invoke(gui);
                if (value == null || !old.getClass().isInstance(value)) return;
                try {
                    Method recalculate = Arrays.stream(gui.getClass().getMethods())
                            .filter(method -> method.getName().equals("recalculateXForAlignment"))
                            .filter(method -> method.getParameterCount() == 3)
                            .findFirst()
                            .orElseThrow(NoSuchMethodException::new);
                    recalculate.invoke(gui, Minecraft.getInstance().font, old, value);
                } catch (NoSuchMethodException ignored) {
                    int left = feeshLeftEdge(((Number) gui.getClass().getMethod("getX").invoke(gui)).intValue(),
                            feeshHudWidth(gui), ((Enum<?>) old).name());
                    gui.getClass().getMethod("setX", int.class)
                            .invoke(gui, feeshAnchorX(left, feeshHudWidth(gui), ((Enum<?>) value).name()));
                }
                gui.getClass().getMethod("setAlignment", old.getClass()).invoke(gui, value);
                saveFeeshHud(gui, key);
            }
        });
    }

    private static int feeshHudWidth(Object gui) throws ReflectiveOperationException {
        Object rawLines = gui.getClass().getMethod("getSampleLines").invoke(gui);
        int width = 0;
        if (rawLines instanceof Collection<?> lines) {
            for (Object line : lines) {
                width = Math.max(width, Minecraft.getInstance().font.width(Component.literal(String.valueOf(line))));
            }
        }
        float scale = ((Number) gui.getClass().getMethod("getScale").invoke(gui)).floatValue();
        return Math.max(0, (int) (width * scale));
    }

    private static String feeshAlignmentName(Object gui) throws ReflectiveOperationException {
        Object value = gui.getClass().getMethod("getAlignment").invoke(gui);
        return value instanceof Enum<?> enumeration ? enumeration.name() : "LEFT";
    }

    static int feeshLeftEdge(int anchor, int width, String alignment) {
        return switch (alignment.toUpperCase(Locale.ROOT)) {
            case "CENTER" -> anchor - width / 2;
            case "RIGHT" -> anchor - width;
            default -> anchor;
        };
    }

    static int feeshAnchorX(int left, int width, String alignment) {
        return switch (alignment.toUpperCase(Locale.ROOT)) {
            case "CENTER" -> left + width / 2;
            case "RIGHT" -> left + width;
            default -> left;
        };
    }

    private static void saveFeeshHud(Object gui, String key) throws ReflectiveOperationException {
        Class<?> manager = Class.forName("com.github.sleepypanda.feesh.utils.data.PersistentDataManager");
        Object instance = manager.getField("INSTANCE").get(null);
        Method update = null;
        for (Method method : manager.getMethods()) {
            if (method.getName().equals("updateOverlayCoordsData") && method.getParameterCount() == 5) {
                update = method;
                break;
            }
        }
        if (update == null) throw new NoSuchMethodException(manager.getName() + ".updateOverlayCoordsData");
        update.invoke(instance, key,
                ((Number) gui.getClass().getMethod("getX").invoke(gui)).intValue(),
                ((Number) gui.getClass().getMethod("getY").invoke(gui)).intValue(),
                ((Number) gui.getClass().getMethod("getScale").invoke(gui)).floatValue(),
                gui.getClass().getMethod("getAlignment").invoke(gui));
    }

    private static List<ExternalHud> babyZombieHuds() {
        if (!providerInstalled(Provider.BABYZOMBIE)) return List.of();
        try {
            Class<?> manager = Class.forName("top.babyzombie.addons.config.hud.HudManager");
            Field elementsField = manager.getDeclaredField("elements");
            elementsField.setAccessible(true);
            Map<?, ?> elements = (Map<?, ?>) elementsField.get(null);
            List<ExternalHud> result = new ArrayList<>();
            for (Map.Entry<?, ?> entry : elements.entrySet()) {
                try {
                    Object element = entry.getValue();
                    Field showField = findField(element.getClass(), "showCondition");
                    showField.setAccessible(true);
                    Object show = showField.get(element);
                    if (!(show instanceof BooleanSupplier supplier) || !supplier.getAsBoolean()) continue;
                    String name = String.valueOf(entry.getKey());
                    NativeSetting primary = new NativeSetting("hud." + name + ".visible", humanize(name),
                            ValueKind.BOOLEAN, null, null, new ValueAccess() {
                        @Override public Object get() {
                            return supplier.getAsBoolean();
                        }

                        @Override public void set(@Nullable Object value) {
                            throw new UnsupportedOperationException("Visibility is owned by the feature config");
                        }
                    });
                    List<NativeSetting> settings = List.of(
                            babyZombieHudField(manager, element, name, "x", ValueKind.INTEGER, -4096.0, 4096.0),
                            babyZombieHudField(manager, element, name, "y", ValueKind.INTEGER, -4096.0, 4096.0),
                            babyZombieHudField(manager, element, name, "scale", ValueKind.DECIMAL, 0.25, 4.0)
                    );
                    String title = humanize(name);
                    String description = Provider.BABYZOMBIE.displayName + " HUD";
                    Classification classification = classify(name, title, description);
                    ConfigScreen.Category category = classification.category;
                    NativeFeature binding = new NativeFeature(Provider.BABYZOMBIE,
                            canonicalId(category, title), title, description, classification, groupName(name, "HUD"),
                            primary, settings);
                    UnifiedFeature feature = new UnifiedFeature(binding.id, binding.title, binding.description,
                            category, binding.group, null, List.of(binding));
                    result.add(new ExternalHud(feature, binding, settings.get(0), settings.get(1), settings.get(2)));
                } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                    LOGGER.debug("Skipping one changed BabyZombieAddons HUD while preserving other HUDs", exception);
                }
            }
            return List.copyOf(result);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return List.of();
        }
    }

    private static void collectBabyZombieHudGaps(List<CompatibilityGap> gaps) {
        if (!providerInstalled(Provider.BABYZOMBIE)) return;
        try {
            Class<?> manager = Class.forName("top.babyzombie.addons.config.hud.HudManager");
            Field elementsField = manager.getDeclaredField("elements");
            elementsField.setAccessible(true);
            Object rawElements = elementsField.get(null);
            if (!(rawElements instanceof Map<?, ?> elements)) {
                gaps.add(new CompatibilityGap(Provider.BABYZOMBIE,
                        ModText.get("config.integration.report.hud_registry"), false, true));
                return;
            }
            for (Map.Entry<?, ?> entry : elements.entrySet()) {
                String name = humanize(String.valueOf(entry.getKey()));
                try {
                    Object element = entry.getValue();
                    Field showField = findField(element.getClass(), "showCondition");
                    showField.setAccessible(true);
                    Object show = showField.get(element);
                    if (!(show instanceof BooleanSupplier)) {
                        gaps.add(new CompatibilityGap(Provider.BABYZOMBIE, name, false, true));
                        continue;
                    }
                    Field x = findField(element.getClass(), "x");
                    Field y = findField(element.getClass(), "y");
                    x.setAccessible(true);
                    y.setAccessible(true);
                    if (!(x.get(element) instanceof Number) || !(y.get(element) instanceof Number)
                            || !hasZeroArgumentMethod(manager, "save")) {
                        gaps.add(new CompatibilityGap(Provider.BABYZOMBIE, name, false, true));
                    }
                } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                    gaps.add(new CompatibilityGap(Provider.BABYZOMBIE, name, false, true));
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            gaps.add(new CompatibilityGap(Provider.BABYZOMBIE,
                    ModText.get("config.integration.report.hud_registry"), false, true));
        }
    }

    private static NativeSetting babyZombieHudField(Class<?> manager, Object element, String name,
                                                     String fieldName, ValueKind kind,
                                                     double minimum, double maximum) {
        return new NativeSetting("hud." + name + "." + fieldName,
                fieldName.equals("scale") ? ModText.get("config.setting.scale")
                        : fieldName.toUpperCase(Locale.ROOT),
                kind, minimum, maximum, new ValueAccess() {
            @Override public Object get() throws ReflectiveOperationException {
                Field field = findField(element.getClass(), fieldName);
                field.setAccessible(true);
                return field.get(element);
            }

            @Override public void set(@Nullable Object value) throws ReflectiveOperationException {
                Field field = findField(element.getClass(), fieldName);
                field.setAccessible(true);
                field.set(element, coerce(value, field.getType()));
                Method save = manager.getDeclaredMethod("save");
                save.setAccessible(true);
                save.invoke(null);
            }
        });
    }

    private static @Nullable NativeSetting hudSetting(List<NativeSetting> settings, String suffix) {
        for (NativeSetting setting : settings) {
            String normalized = setting.id.toLowerCase(Locale.ROOT);
            if (normalized.endsWith("." + suffix)) return setting;
            String lastSegment = setting.id.substring(setting.id.lastIndexOf('.') + 1);
            if (suffix.equals(coordinateRole(lastSegment))) return setting;
        }
        return null;
    }

    static List<UnifiedFeature> buildFeatures(List<NativeFeature> nativeFeatures) {
        Map<String, MutableUnified> merged = new LinkedHashMap<>();
        for (ConfigScreen.Feature feature : ConfigScreen.Feature.values()) {
            String id = localFeatureId(feature);
            MutableUnified entry = merged.computeIfAbsent(id, ignored -> new MutableUnified(id));
            entry.local = feature;
            entry.title = ModText.get(feature.titleKey);
            entry.description = ModText.get(feature.descriptionKey);
            entry.category = feature.category;
            entry.group = ModText.get(feature.group.key);
        }
        for (NativeFeature feature : nativeFeatures) {
            String id = alias(canonicalId(feature.category, feature.title));
            MutableUnified entry = merged.computeIfAbsent(id, ignored -> new MutableUnified(id));
            if (entry.title == null) entry.title = feature.title;
            if (entry.description == null) entry.description = feature.description;
            if (entry.category == null) entry.category = feature.category;
            if (entry.group == null) entry.group = feature.group;
            entry.external.add(feature);
        }
        List<UnifiedFeature> features = new ArrayList<>();
        for (MutableUnified value : merged.values()) {
            if (value.title == null || value.category == null) continue;
            features.add(new UnifiedFeature(value.id, value.title,
                    value.description == null ? "" : value.description, value.category,
                    value.group == null ? ModText.get("config.group.integrations") : value.group,
                    value.local, value.external));
        }
        features.sort(Comparator.comparing((UnifiedFeature feature) -> feature.category.ordinal())
                .thenComparing(feature -> feature.group, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(feature -> feature.title, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(features);
    }

    private static String localFeatureId(ConfigScreen.Feature feature) {
        String legacyId = LEGACY_LOCAL_FEATURE_IDS.get(feature.name());
        if (legacyId != null) return alias(legacyId);
        return alias(feature.category.name().toLowerCase(Locale.ROOT) + ":"
                + feature.name().toLowerCase(Locale.ROOT));
    }

    private static List<Adapter> adapters() {
        return List.of(
                new ObjectGraphAdapter(Provider.SKYHANNI,
                        "at.hannibal2.skyhanni.SkyHanniMod", "feature", null, "saveNow"),
                new SkyBlockerAdapter(),
                new ObjectGraphAdapter(Provider.BABYZOMBIE,
                        "top.babyzombie.addons.config.ModConfigManager", null, "get", "save"),
                new FirmamentAdapter(),
                new FeeshAdapter()
        );
    }

    private static boolean providerInstalled(Provider provider) {
        return FabricLoader.getInstance().isModLoaded(provider.modId);
    }

    private interface Adapter {
        Provider provider();
        boolean available();
        List<NativeFeature> discover() throws ReflectiveOperationException;
        default List<CompatibilityGap> gaps() {
            return List.of();
        }
    }

    private abstract static class BaseAdapter implements Adapter {
        final Provider provider;

        BaseAdapter(Provider provider) {
            this.provider = provider;
        }

        @Override
        public Provider provider() {
            return provider;
        }

        @Override
        public boolean available() {
            if (!providerInstalled(provider)) return false;
            try {
                probeCapabilities();
                return true;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                LOGGER.warn("Skipping {} integration because its recognised configuration capabilities are unavailable",
                        provider.displayName, exception);
                return false;
            }
        }

        abstract void probeCapabilities() throws ReflectiveOperationException;
    }

    private static class ObjectGraphAdapter extends BaseAdapter {
        private final String rootClass;
        private final @Nullable String staticField;
        private final @Nullable String staticGetter;
        private final @Nullable String saveMethod;

        ObjectGraphAdapter(Provider provider, String rootClass, @Nullable String staticField,
                           @Nullable String staticGetter, @Nullable String saveMethod) {
            super(provider);
            this.rootClass = rootClass;
            this.staticField = staticField;
            this.staticGetter = staticGetter;
            this.saveMethod = saveMethod;
        }

        @Override
        void probeCapabilities() throws ReflectiveOperationException {
            Object root = root();
            if (root == null) throw new IllegalStateException("Configuration root is not initialised");
            if (saveMethod == null) return;
            if (hasZeroArgumentMethod(root.getClass(), saveMethod)) return;
            Class<?> type = Class.forName(rootClass);
            if (!hasZeroArgumentMethod(type, saveMethod)) {
                throw new NoSuchMethodException(type.getName() + "." + saveMethod + "()");
            }
        }

        @Override
        public List<NativeFeature> discover() throws ReflectiveOperationException {
            Object root = root();
            List<NativeFeature> result = new ArrayList<>();
            scanObject(this, root, List.of(), root.getClass().getSimpleName(), 0,
                    Collections.newSetFromMap(new IdentityHashMap<>()), result);
            return result;
        }

        Object root() throws ReflectiveOperationException {
            Class<?> type = Class.forName(rootClass);
            if (staticField != null) {
                Field field = type.getDeclaredField(staticField);
                field.setAccessible(true);
                return field.get(null);
            }
            Method method = type.getMethod(staticGetter);
            return method.invoke(null);
        }

        Object read(List<String> path) throws ReflectiveOperationException {
            return readPath(root(), path);
        }

        void write(List<String> path, @Nullable Object value) throws ReflectiveOperationException {
            Object root = root();
            writePath(root, path, value);
            save(root);
        }

        void save(Object root) throws ReflectiveOperationException {
            if (saveMethod == null) return;
            try {
                root.getClass().getMethod(saveMethod).invoke(root);
            } catch (NoSuchMethodException ignored) {
                Class<?> type = Class.forName(rootClass);
                type.getMethod(saveMethod).invoke(null);
            }
        }
    }

    private static final class SkyBlockerAdapter extends ObjectGraphAdapter {
        SkyBlockerAdapter() {
            super(Provider.SKYBLOCKER, "de.hysky.skyblocker.config.SkyblockerConfigManager",
                    null, "get", null);
        }

        @Override
        void probeCapabilities() throws ReflectiveOperationException {
            super.probeCapabilities();
            Class<?> manager = Class.forName("de.hysky.skyblocker.config.SkyblockerConfigManager");
            manager.getMethod("update", Consumer.class);
        }

        @Override
        void write(List<String> path, @Nullable Object value) throws ReflectiveOperationException {
            Class<?> manager = Class.forName("de.hysky.skyblocker.config.SkyblockerConfigManager");
            Method update = manager.getMethod("update", Consumer.class);
            Consumer<Object> action = root -> {
                try {
                    writePath(root, path, value);
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException(exception);
                }
            };
            update.invoke(null, action);
        }
    }

    private static final class FirmamentAdapter extends BaseAdapter {
        FirmamentAdapter() {
            super(Provider.FIRMAMENT);
        }

        @Override
        void probeCapabilities() throws ReflectiveOperationException {
            Class<?> managedConfig = Class.forName("moe.nea.firmament.util.data.ManagedConfig");
            Object companion = managedConfig.getField("Companion").get(null);
            Object instanceList = companion.getClass().getMethod("getAllManagedConfigs").invoke(companion);
            instanceList.getClass().getMethod("getAll");
        }

        @Override
        public List<NativeFeature> discover() throws ReflectiveOperationException {
            Class<?> managedConfig = Class.forName("moe.nea.firmament.util.data.ManagedConfig");
            Object companion = managedConfig.getField("Companion").get(null);
            Object instanceList = companion.getClass().getMethod("getAllManagedConfigs").invoke(companion);
            Collection<?> configs = (Collection<?>) instanceList.getClass().getMethod("getAll").invoke(instanceList);
            List<NativeFeature> result = new ArrayList<>();
            for (Object config : configs) {
                try {
                    discoverConfig(config, result);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                    LOGGER.debug("Skipping one changed Firmament config while preserving other configs", exception);
                }
            }
            return List.copyOf(result);
        }

        private void discoverConfig(Object config, List<NativeFeature> result)
                throws ReflectiveOperationException {
            String configName = String.valueOf(config.getClass().getMethod("getName").invoke(config));
            String categoryName = String.valueOf(config.getClass().getMethod("getCategory").invoke(config));
            Map<?, ?> options = (Map<?, ?>) config.getClass().getMethod("getAllOptions").invoke(config);
            for (Object option : options.values()) {
                try {
                    NativeFeature feature = discoverOption(config, configName, categoryName, options, option);
                    if (feature != null) result.add(feature);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                    LOGGER.debug("Skipping one changed Firmament option while preserving sibling options", exception);
                }
            }
        }

        private @Nullable NativeFeature discoverOption(Object config, String configName, String categoryName,
                                                        Map<?, ?> options, Object option)
                throws ReflectiveOperationException {
            Object value = option.getClass().getMethod("get").invoke(option);
            ValueKind kind = valueKind(value == null ? Object.class : value.getClass());
            if (kind != ValueKind.BOOLEAN && kind != ValueKind.ENUM) return null;
            String property = String.valueOf(option.getClass().getMethod("getPropertyName").invoke(option));
            String title = componentString(option, "getLabelText", humanize(property));
            String description = componentString(option, "getLabelDescription",
                    provider.displayName + " · " + configName);
            ValueAccess access = new ValueAccess() {
                @Override public Object get() throws ReflectiveOperationException {
                    return option.getClass().getMethod("get").invoke(option);
                }

                @Override public void set(@Nullable Object newValue) throws ReflectiveOperationException {
                    option.getClass().getMethod("set", Object.class).invoke(option, newValue);
                    markFirmamentDirty(config);
                }
            };
            NativeSetting primary = new NativeSetting(configName + "." + property, title, kind,
                    null, null, access);
            List<NativeSetting> settings = new ArrayList<>();
            for (Object sibling : options.values()) {
                if (sibling == option || settings.size() >= MAX_SETTINGS_PER_FEATURE) continue;
                List<NativeSetting> hudSettings = firmamentHudSettings(config, configName, sibling);
                if (!hudSettings.isEmpty()) {
                    for (NativeSetting setting : hudSettings) {
                        if (settings.size() >= MAX_SETTINGS_PER_FEATURE) break;
                        settings.add(setting);
                    }
                    continue;
                }
                NativeSetting setting = firmamentSetting(config, configName, sibling);
                if (setting != null) settings.add(setting);
            }
            String path = categoryName + "." + configName + "." + property;
            Classification classification = classify(path, title, description);
            ConfigScreen.Category category = classification.category;
            return new NativeFeature(provider, canonicalId(category, title), title, description,
                    classification, groupName(path, configName), primary, settings);
        }

        private @Nullable NativeSetting firmamentSetting(Object config, String configName, Object option) {
            try {
                Object value = option.getClass().getMethod("get").invoke(option);
                if (value != null && value.getClass().getName().equals("moe.nea.firmament.gui.config.HudMeta")) {
                    return null;
                }
                ValueKind kind = valueKind(value == null ? Object.class : value.getClass());
                if (kind == ValueKind.UNSUPPORTED) return null;
                String property = String.valueOf(option.getClass().getMethod("getPropertyName").invoke(option));
                String label = componentString(option, "getLabelText", humanize(property));
                ValueAccess access = new ValueAccess() {
                    @Override public Object get() throws ReflectiveOperationException {
                        return option.getClass().getMethod("get").invoke(option);
                    }

                    @Override public void set(@Nullable Object value) throws ReflectiveOperationException {
                        option.getClass().getMethod("set", Object.class).invoke(option, value);
                        markFirmamentDirty(config);
                    }
                };
                return new NativeSetting(configName + "." + property, label, kind, null, null, access);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        private List<NativeSetting> firmamentHudSettings(Object config, String configName, Object option) {
            try {
                Object value = option.getClass().getMethod("get").invoke(option);
                if (value == null || !value.getClass().getName().equals("moe.nea.firmament.gui.config.HudMeta")) {
                    return List.of();
                }
                String property = String.valueOf(option.getClass().getMethod("getPropertyName").invoke(option));
                return List.of(
                        firmamentHudAxis(config, configName, property, option, "x"),
                        firmamentHudAxis(config, configName, property, option, "y"),
                        firmamentHudScale(config, configName, property, option)
                );
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return List.of();
            }
        }

        private NativeSetting firmamentHudAxis(Object config, String configName, String property,
                                                Object option, String axis) {
            return new NativeSetting(configName + "." + property + "." + axis,
                    axis.toUpperCase(Locale.ROOT), ValueKind.INTEGER, -4096.0, 4096.0, new ValueAccess() {
                @Override public Object get() throws ReflectiveOperationException {
                    Object hud = option.getClass().getMethod("get").invoke(option);
                    Object position = hud.getClass().getMethod("getPosition").invoke(hud);
                    return position.getClass().getMethod(axis).invoke(position);
                }

                @Override public void set(@Nullable Object newValue) throws ReflectiveOperationException {
                    Object hud = option.getClass().getMethod("get").invoke(option);
                    Object position = hud.getClass().getMethod("getPosition").invoke(hud);
                    int x = ((Number) position.getClass().getMethod("x").invoke(position)).intValue();
                    int y = ((Number) position.getClass().getMethod("y").invoke(position)).intValue();
                    int changed = ((Number) newValue).intValue();
                    Method setter = hud.getClass().getMethod("setPosition", org.joml.Vector2ic.class);
                    setter.invoke(hud, axis.equals("x") ? new Vector2i(changed, y) : new Vector2i(x, changed));
                    markFirmamentDirty(config);
                }
            });
        }

        private NativeSetting firmamentHudScale(Object config, String configName, String property, Object option) {
            return new NativeSetting(configName + "." + property + ".scale", ModText.get("config.setting.scale"),
                    ValueKind.DECIMAL, 0.25, 4.0, new ValueAccess() {
                @Override public Object get() throws ReflectiveOperationException {
                    Object hud = option.getClass().getMethod("get").invoke(option);
                    return hud.getClass().getMethod("getScale").invoke(hud);
                }

                @Override public void set(@Nullable Object newValue) throws ReflectiveOperationException {
                    Object hud = option.getClass().getMethod("get").invoke(option);
                    hud.getClass().getMethod("setScale", float.class)
                            .invoke(hud, ((Number) newValue).floatValue());
                    markFirmamentDirty(config);
                }
            });
        }
    }

    /**
     * Feesh stores category values as Kotlin delegated properties. Public
     * getter/setter pairs are the stable native mutation boundary across its
     * ResourcefulConfig 4/5 builds, so this adapter deliberately avoids both
     * the generated delegate fields and ResourcefulConfig implementation
     * classes. Every successful write uses Feesh's own save method.
     */
    private static final class FeeshAdapter extends BaseAdapter {
        private static final String SETTINGS = "com.github.sleepypanda.feesh.settings.Settings";
        private static final List<String> CATEGORY_NAMES = List.of(
                "General", "Alerts", "Chat", "Overlays", "Items", "WorldRendering", "Commands");
        private List<CompatibilityGap> discoveredGaps = List.of();

        FeeshAdapter() {
            super(Provider.FEESH);
        }

        @Override
        void probeCapabilities() throws ReflectiveOperationException {
            Object settings = kotlinObject(SETTINGS);
            settings.getClass().getMethod("save");
            boolean foundCategory = false;
            for (String category : CATEGORY_NAMES) {
                try {
                    kotlinObject(feeshCategoryClass(category));
                    foundCategory = true;
                    break;
                } catch (ClassNotFoundException | NoSuchFieldException ignored) { }
            }
            if (!foundCategory) throw new ClassNotFoundException("No recognised Feesh setting category");
        }

        @Override
        public List<NativeFeature> discover() throws ReflectiveOperationException {
            Object settings = kotlinObject(SETTINGS);
            List<NativeFeature> result = new ArrayList<>();
            List<CompatibilityGap> gaps = new ArrayList<>();
            for (String categoryName : CATEGORY_NAMES) {
                try {
                    Object category = kotlinObject(feeshCategoryClass(categoryName));
                    discoverFeeshCategory(settings, category, categoryName, result, gaps);
                } catch (ClassNotFoundException | NoSuchFieldException ignored) {
                    // Future Feesh builds may remove or rename an independent
                    // category. Continue scanning all remaining categories.
                } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                    gaps.add(new CompatibilityGap(Provider.FEESH, humanize(categoryName), true, false));
                    LOGGER.debug("Skipping one changed Feesh category while preserving siblings", exception);
                }
            }
            discoveredGaps = mergeCompatibilityGaps(gaps);
            return List.copyOf(result);
        }

        @Override
        public List<CompatibilityGap> gaps() {
            return discoveredGaps;
        }

        private void discoverFeeshCategory(Object settingsRoot, Object category, String categoryName,
                                           List<NativeFeature> result, List<CompatibilityGap> gaps)
                throws ReflectiveOperationException {
            List<FeeshProperty> properties = feeshProperties(category);
            List<FeeshProperty> roots = new ArrayList<>();
            for (FeeshProperty property : properties) {
                if (property.kind != ValueKind.BOOLEAN) continue;
                boolean child = false;
                for (FeeshProperty possibleRoot : properties) {
                    if (possibleRoot == property || possibleRoot.kind != ValueKind.BOOLEAN) continue;
                    if (feeshRelationScore(possibleRoot.name, property.name) > 0
                            && feeshRootPriority(possibleRoot.name) > feeshRootPriority(property.name)) {
                        child = true;
                        break;
                    }
                }
                if (!child) roots.add(property);
            }

            Map<FeeshProperty, List<FeeshProperty>> children = new LinkedHashMap<>();
            for (FeeshProperty root : roots) children.put(root, new ArrayList<>());
            Set<FeeshProperty> claimed = Collections.newSetFromMap(new IdentityHashMap<>());
            claimed.addAll(roots);
            for (FeeshProperty property : properties) {
                if (roots.contains(property)) continue;
                FeeshProperty best = null;
                int bestScore = 0;
                for (FeeshProperty root : roots) {
                    int score = feeshRelationScore(root.name, property.name);
                    if (score > bestScore) {
                        bestScore = score;
                        best = root;
                    }
                }
                if (best != null && bestScore > 0) {
                    children.get(best).add(property);
                    claimed.add(property);
                }
            }

            for (FeeshProperty root : roots) {
                String title = feeshFeatureTitle(root.name);
                Classification classification = classifyFeesh(categoryName, root.name, title);
                NativeSetting primary = feeshSetting(settingsRoot, category, categoryName, root);
                List<NativeSetting> secondary = new ArrayList<>();
                for (FeeshProperty child : children.get(root)) {
                    if (secondary.size() >= MAX_SETTINGS_PER_FEATURE) break;
                    secondary.add(feeshSetting(settingsRoot, category, categoryName, child));
                }
                result.add(new NativeFeature(Provider.FEESH,
                        canonicalId(classification.category, title), title,
                        Provider.FEESH.displayName + " · " + humanize(categoryName),
                        classification, humanize(categoryName), primary, secondary));
            }

            for (FeeshProperty property : properties) {
                if (claimed.contains(property)) continue;
                // A non-toggle value without a deterministically related
                // feature cannot safely become a fake enable card. Report it
                // as a settings gap instead of guessing or silently dropping it.
                gaps.add(new CompatibilityGap(Provider.FEESH, humanize(property.name), true, false));
            }
        }

        private NativeSetting feeshSetting(Object settingsRoot, Object category,
                                           String categoryName, FeeshProperty property) {
            double[] range = feeshRange(property.name, property.kind, property.current);
            return new NativeSetting("feesh." + categoryName + "." + property.name,
                    humanize(property.name), property.kind,
                    range == null ? null : range[0], range == null ? null : range[1], new ValueAccess() {
                @Override public Object get() throws ReflectiveOperationException {
                    return property.getter.invoke(category);
                }

                @Override public void set(@Nullable Object value) throws ReflectiveOperationException {
                    Object converted = coerce(value, property.setter.getParameterTypes()[0]);
                    property.setter.invoke(category, converted);
                    settingsRoot.getClass().getMethod("save").invoke(settingsRoot);
                }
            });
        }
    }

    private record FeeshProperty(String name, Method getter, Method setter,
                                 ValueKind kind, @Nullable Object current) { }

    private static List<FeeshProperty> feeshProperties(Object category) {
        Map<String, Method> setters = new LinkedHashMap<>();
        for (Method method : category.getClass().getMethods()) {
            if (!method.getName().startsWith("set") || method.getName().length() <= 3
                    || method.getParameterCount() != 1 || Modifier.isStatic(method.getModifiers())) continue;
            setters.put(method.getName().substring(3), method);
        }
        List<FeeshProperty> result = new ArrayList<>();
        for (Method getter : category.getClass().getMethods()) {
            if (!getter.getName().startsWith("get") || getter.getName().length() <= 3
                    || getter.getParameterCount() != 0 || Modifier.isStatic(getter.getModifiers())) continue;
            String suffix = getter.getName().substring(3);
            Method setter = setters.get(suffix);
            if (setter == null) continue;
            ValueKind kind = valueKind(getter.getReturnType());
            if (kind == ValueKind.UNSUPPORTED) continue;
            try {
                Object current = getter.invoke(category);
                result.add(new FeeshProperty(decapitalize(suffix), getter, setter, kind, current));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // Continue with independent delegated properties.
            }
        }
        result.sort(Comparator.comparing(FeeshProperty::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    private static String feeshCategoryClass(String category) {
        return "com.github.sleepypanda.feesh.settings.categories." + category;
    }

    private static Object kotlinObject(String className) throws ReflectiveOperationException {
        Class<?> type = Class.forName(className);
        return type.getField("INSTANCE").get(null);
    }

    static String feeshFeatureTitle(String property) {
        String title = featureTitle(property);
        title = title.replaceFirst("(?i)\\s+Overlay$", "");
        return title.isBlank() ? humanize(property) : title;
    }

    private static Classification classifyFeesh(String categoryName, String property, String title) {
        String normalized = property.toLowerCase(Locale.ROOT);
        String path;
        if (containsAny(normalized, "spiritmask", "crimson", "combat", "archfiend")) {
            path = "combat.feesh." + categoryName + "." + property;
        } else if (containsAny(normalized, "item", "tooltip", "slot", "price", "gearcraft", "shop")) {
            path = "items.feesh." + categoryName + "." + property;
        } else if (containsAny(normalized, "festival", "jerryworkshop", "rain")) {
            path = "events.feesh." + categoryName + "." + property;
        } else {
            path = "fishing.feesh." + categoryName + "." + property;
        }
        return classify(path, title, Provider.FEESH.displayName + " · " + humanize(categoryName));
    }

    static int feeshRootPriority(String property) {
        String normalized = property.toLowerCase(Locale.ROOT);
        int score = normalized.endsWith("overlay") ? 120 : 40;
        if (normalized.startsWith("alerton") || normalized.startsWith("compact")
                || normalized.startsWith("share") || normalized.startsWith("messageon")
                || normalized.startsWith("automessageon") || normalized.startsWith("hide")
                || normalized.startsWith("mute")) score += 50;
        if (containsAny(normalized, "customstyle", "usegradient", "showprice", "include",
                "reset", "source", "mode", "template")) score -= 45;
        return score;
    }

    static int feeshRelationScore(String root, String candidate) {
        if (root.equals(candidate)) return 0;
        String normalizedRoot = root.toLowerCase(Locale.ROOT);
        String normalizedCandidate = candidate.toLowerCase(Locale.ROOT);
        if (normalizedCandidate.startsWith(normalizedRoot)) return 1000 + normalizedRoot.length();
        List<String> rootTokens = feeshCoreTokens(root);
        List<String> candidateTokens = feeshCoreTokens(candidate);
        if (rootTokens.isEmpty() || candidateTokens.isEmpty()) return 0;
        int common = 0;
        for (String token : rootTokens) if (candidateTokens.contains(token)) common++;
        if (common < Math.min(2, rootTokens.size())) return 0;
        boolean containsAll = candidateTokens.containsAll(rootTokens);
        return (containsAll ? 200 : 0) + common * 20 - Math.abs(candidateTokens.size() - rootTokens.size());
    }

    private static List<String> feeshCoreTokens(String property) {
        String words = property.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replace('_', ' ').toLowerCase(Locale.ROOT);
        Set<String> ignored = Set.of("alert", "on", "show", "display", "enable", "enabled", "overlay",
                "custom", "style", "use", "reset", "session", "game", "closed", "source", "mode",
                "include", "template", "should", "be", "when", "the", "a", "an", "for", "to");
        List<String> result = new ArrayList<>();
        for (String token : words.split("\\s+")) {
            if (token.isBlank() || ignored.contains(token)) continue;
            if (token.endsWith("s") && token.length() > 3) token = token.substring(0, token.length() - 1);
            result.add(token);
        }
        return List.copyOf(result);
    }

    private static @Nullable double[] feeshRange(String property, ValueKind kind, @Nullable Object current) {
        if (kind != ValueKind.INTEGER && kind != ValueKind.DECIMAL) return null;
        String normalized = property.toLowerCase(Locale.ROOT);
        if (normalized.contains("scale")) return new double[]{0.2, 4.0};
        if (normalized.contains("opacity") || normalized.contains("alpha")) return new double[]{0.0, 255.0};
        if (normalized.contains("volume")) return new double[]{0.0, 100.0};
        if (normalized.contains("distance")) return new double[]{0.0, 64.0};
        if (normalized.contains("seconds") || normalized.contains("duration")
                || normalized.contains("timer")) return new double[]{0.0, 3600.0};
        if (normalized.contains("price") || normalized.contains("cheaper")
                || normalized.contains("coins")) return new double[]{0.0, 2_000_000_000.0};
        if (normalized.contains("count") || normalized.contains("threshold")
                || normalized.contains("showtop") || normalized.contains("width")) {
            return new double[]{0.0, 1000.0};
        }
        if (current instanceof Number number) {
            double value = Math.abs(number.doubleValue());
            if (value == 0.0) return null;
            return new double[]{0.0, Math.max(10.0, Math.ceil(value * 4.0))};
        }
        return null;
    }

    private static String decapitalize(String value) {
        if (value.isEmpty()) return value;
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private static void scanObject(ObjectGraphAdapter adapter, Object object, List<String> path,
                                   String fallbackName, int depth, Set<Object> visited,
                                   List<NativeFeature> result) throws ReflectiveOperationException {
        if (object == null || depth > MAX_SCAN_DEPTH || visited.contains(object)) return;
        visited.add(object);
        List<Member> members = members(object);
        Member primary = findPrimary(object, members);
        if (primary != null && !path.isEmpty()) {
            String title = memberLabel(primary.ownerMember(), humanize(fallbackName));
            String description = memberDescription(primary.ownerMember(),
                    adapter.provider.displayName + " · " + String.join(" / ", path));
            String fullPath = String.join(".", path);
            Classification classification = classify(fullPath, title, description);
            ConfigScreen.Category category = classification.category;
            NativeSetting primarySetting = setting(adapter, append(path, primary.name()), primary, title);
            List<NativeSetting> settings = collectSettings(adapter, object, path, members, primary);
            result.add(new NativeFeature(adapter.provider, canonicalId(category, title), title, description,
                    classification, groupName(fullPath, fallbackName), primarySetting, settings));
            return;
        }
        for (Member member : members) {
            if (member.simple()) {
                if (isToggleCandidate(member) && !path.isEmpty()) {
                    String title = memberLabel(member.ownerMember(), featureTitle(member.name()));
                    String description = memberDescription(member.ownerMember(), adapter.provider.displayName
                            + " · " + String.join(" / ", path));
                    String fullPath = String.join(".", append(path, member.name()));
                    Classification classification = classify(fullPath, title, description);
                    ConfigScreen.Category category = classification.category;
                    NativeSetting primarySetting = setting(adapter, append(path, member.name()), member, title);
                    List<NativeSetting> settings = collectRelatedSettings(adapter, path, members, member);
                    result.add(new NativeFeature(adapter.provider, canonicalId(category, title), title, description,
                            classification, groupName(fullPath, path.getLast()), primarySetting, settings));
                }
                continue;
            }
            Object child;
            try {
                child = member.read(object);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                continue;
            }
            if (child == null || !belongsToProvider(adapter.provider, child.getClass())) continue;
            try {
                scanObject(adapter, child, append(path, member.name()), member.name(), depth + 1, visited, result);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                LOGGER.debug("Skipping one changed {} config branch while preserving siblings",
                        adapter.provider.displayName, exception);
            }
        }
    }

    private static List<NativeSetting> collectSettings(ObjectGraphAdapter adapter, Object object,
                                                       List<String> path, List<Member> members, Member primary) {
        List<NativeSetting> result = new ArrayList<>();
        for (Member member : members) {
            if (member == primary || result.size() >= MAX_SETTINGS_PER_FEATURE) continue;
            if (member.simple()) {
                result.add(setting(adapter, append(path, member.name()), member,
                        memberLabel(member.ownerMember(), humanize(member.name()))));
                continue;
            }
            if (adapter.provider == Provider.SKYHANNI
                    && member.ownerMember().getType().getName()
                    .equals("at.hannibal2.skyhanni.config.core.config.Position")) {
                result.addAll(skyHanniPositionSettings(adapter, append(path, member.name())));
            } else {
                // Keep a named complex value as a read-only diagnostic row.
                // The regular settings screen filters UNSUPPORTED values, but
                // the compatibility report can still tell the player which
                // recognised feature has settings QCA cannot safely expose.
                result.add(setting(adapter, append(path, member.name()), member,
                        memberLabel(member.ownerMember(), humanize(member.name()))));
            }
        }
        return result;
    }

    /**
     * Associates settings with prefixed future-version toggles without
     * guessing across unrelated functions in the same provider object. For
     * example, {@code enabledCommissions} owns {@code commissionsX} and
     * {@code commissionsY}, while {@code enabledPowder} remains a separate
     * feature.
     */
    private static List<NativeSetting> collectRelatedSettings(ObjectGraphAdapter adapter,
                                                               List<String> path,
                                                               List<Member> members,
                                                               Member primary) {
        String stem = semanticStem(primary.name());
        if (stem.isBlank()) return List.of();
        List<NativeSetting> result = new ArrayList<>();
        for (Member member : members) {
            if (member == primary || result.size() >= MAX_SETTINGS_PER_FEATURE || isToggleCandidate(member)) continue;
            if (member.simple() && relatedToStem(member.name(), stem)) {
                String role = coordinateRole(member.name());
                String label = role == null
                        ? memberLabel(member.ownerMember(), humanize(member.name()))
                        : role.equals("scale") ? ModText.get("config.setting.scale") : role.toUpperCase(Locale.ROOT);
                NativeSetting candidate = setting(adapter, append(path, member.name()), member, label);
                // Keep a recognised but unsupported setting in the binding so
                // the read-only compatibility report can explain the gap. The
                // normal secondary editor already filters non-editable rows.
                result.add(candidate);
                continue;
            }
            if (adapter.provider == Provider.SKYHANNI
                    && relatedToStem(member.name(), stem)
                    && member.ownerMember().getType().getName()
                    .equals("at.hannibal2.skyhanni.config.core.config.Position")) {
                result.addAll(skyHanniPositionSettings(adapter, append(path, member.name())));
            } else if (!member.simple() && relatedToStem(member.name(), stem)) {
                result.add(setting(adapter, append(path, member.name()), member,
                        memberLabel(member.ownerMember(), humanize(member.name()))));
            }
        }
        return List.copyOf(result);
    }

    private static List<NativeSetting> skyHanniPositionSettings(ObjectGraphAdapter adapter, List<String> path) {
        return List.of(
                skyHanniPositionAxis(adapter, path, "x"),
                skyHanniPositionAxis(adapter, path, "y"),
                new NativeSetting(String.join(".", append(path, "scale")), ModText.get("config.setting.scale"),
                        ValueKind.DECIMAL, 0.1, 10.0, new ValueAccess() {
                    @Override public Object get() throws ReflectiveOperationException {
                        Object position = readRawPath(adapter.root(), path);
                        return position.getClass().getMethod("getScale").invoke(position);
                    }

                    @Override public void set(@Nullable Object value) throws ReflectiveOperationException {
                        Object root = adapter.root();
                        Object position = readRawPath(root, path);
                        position.getClass().getMethod("setScale", float.class)
                                .invoke(position, ((Number) value).floatValue());
                        adapter.save(root);
                    }
                })
        );
    }

    private static NativeSetting skyHanniPositionAxis(ObjectGraphAdapter adapter, List<String> path, String axis) {
        return new NativeSetting(String.join(".", append(path, axis)), axis.toUpperCase(Locale.ROOT),
                ValueKind.INTEGER, -4096.0, 4096.0, new ValueAccess() {
            @Override public Object get() throws ReflectiveOperationException {
                Object position = readRawPath(adapter.root(), path);
                return position.getClass().getMethod("get" + axis.toUpperCase(Locale.ROOT)).invoke(position);
            }

            @Override public void set(@Nullable Object value) throws ReflectiveOperationException {
                Object root = adapter.root();
                Object position = readRawPath(root, path);
                int currentX = ((Number) position.getClass().getMethod("getX").invoke(position)).intValue();
                int currentY = ((Number) position.getClass().getMethod("getY").invoke(position)).intValue();
                int changed = ((Number) value).intValue();
                position.getClass().getMethod("moveTo", int.class, int.class)
                        .invoke(position, axis.equals("x") ? changed : currentX,
                                axis.equals("y") ? changed : currentY);
                adapter.save(root);
            }
        });
    }

    private static NativeSetting setting(ObjectGraphAdapter adapter, List<String> path,
                                         Member member, String label) {
        double[] range = sliderRange(member.ownerMember());
        String role = coordinateRole(member.name());
        if (range == null && member.kind() == ValueKind.INTEGER
                && ("x".equals(role) || "y".equals(role))) {
            range = new double[]{-4096.0, 4096.0};
        } else if (range == null && member.kind() == ValueKind.DECIMAL
                && "scale".equals(role)) {
            range = new double[]{0.25, 4.0};
        }
        return new NativeSetting(String.join(".", path), label, member.kind(),
                range == null ? null : range[0], range == null ? null : range[1], new ValueAccess() {
            @Override public Object get() throws ReflectiveOperationException {
                return unwrap(adapter.read(path));
            }

            @Override public void set(@Nullable Object value) throws ReflectiveOperationException {
                adapter.write(path, value);
            }
        });
    }

    private static @Nullable Member findPrimary(Object object, List<Member> members) {
        for (String preferred : List.of("enabled", "enable", "isEnabled", "visible", "active")) {
            for (Member member : members) {
                if (member.simple() && member.name().equalsIgnoreCase(preferred)
                        && (member.kind() == ValueKind.BOOLEAN
                        || member.kind() == ValueKind.ENUM)) return member;
            }
        }
        return null;
    }

    private static boolean isToggleCandidate(Member member) {
        return member.simple()
                && (member.kind() == ValueKind.BOOLEAN || member.kind() == ValueKind.ENUM);
    }

    static String featureTitle(String name) {
        String stripped = name.replaceFirst("^(?i:isEnabled|enabled|enable|display|show|visible|active|use)(?=[A-Z_])", "");
        return humanize(stripped.isBlank() ? name : stripped);
    }

    static String semanticStem(String name) {
        String stripped = name.replaceFirst("^(?i:isEnabled|enabled|enable|display|show|visible|active|use)(?=[A-Z_])", "");
        return stripped.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    static boolean relatedToStem(String candidate, String stem) {
        if (stem == null || stem.isBlank()) return false;
        String normalized = candidate.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        return normalized.startsWith(stem) || normalized.endsWith(stem);
    }

    static @Nullable String coordinateRole(String name) {
        if (name.equalsIgnoreCase("x") || name.endsWith("X") || name.endsWith("_x")) return "x";
        if (name.equalsIgnoreCase("y") || name.endsWith("Y") || name.endsWith("_y")) return "y";
        if (name.equalsIgnoreCase("scale") || name.endsWith("Scale") || name.endsWith("_scale")) return "scale";
        return null;
    }

    private static List<Member> members(Object owner) {
        Class<?> type = owner.getClass();
        Map<String, Field> fields = new LinkedHashMap<>();
        for (Class<?> cursor = type; cursor != null && cursor != Object.class; cursor = cursor.getSuperclass()) {
            for (Field field : cursor.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic() || field.getName().contains("$")) continue;
                fields.putIfAbsent(field.getName(), field);
            }
        }
        List<Member> result = new ArrayList<>();
        for (Field field : fields.values()) {
            try {
                field.setAccessible(true);
                Class<?> valueType = field.getType();
                ValueKind kind = valueKind(valueType);
                boolean property = isProperty(valueType);
                if (property) {
                    Object wrapper = field.get(owner);
                    Object actual = wrapper == null ? null : wrapper.getClass().getMethod("get").invoke(wrapper);
                    kind = actual == null ? ValueKind.UNSUPPORTED : valueKind(actual.getClass());
                    if (wrapper != null) findSingleArgumentMethod(wrapper.getClass(), "set");
                }
                boolean writableSimple = kind != ValueKind.UNSUPPORTED
                        && (!Modifier.isFinal(field.getModifiers()) || property);
                result.add(new Member(field.getName(), field, kind, writableSimple, property));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // A provider update may make one branch inaccessible. Keep
                // discovering other independent branches instead of hiding the
                // whole provider.
            }
        }
        return result;
    }

    private record Member(String name, Field ownerMember, ValueKind kind, boolean simple, boolean property) {
        Object read(Object owner) throws ReflectiveOperationException {
            Object value = ownerMember.get(owner);
            return property && value != null ? value.getClass().getMethod("get").invoke(value) : value;
        }
    }

    private interface ValueAccess {
        @Nullable Object get() throws ReflectiveOperationException;
        void set(@Nullable Object value) throws ReflectiveOperationException;
    }

    private static Object readPath(Object root, List<String> path) throws ReflectiveOperationException {
        Object current = readRawPath(root, path);
        return unwrap(current);
    }

    private static Object readRawPath(Object root, List<String> path) throws ReflectiveOperationException {
        Object current = root;
        for (String segment : path) {
            Field field = findField(current.getClass(), segment);
            field.setAccessible(true);
            current = field.get(current);
            if (current == null) return null;
        }
        return current;
    }

    private static void writePath(Object root, List<String> path, @Nullable Object value)
            throws ReflectiveOperationException {
        Object current = root;
        for (int index = 0; index < path.size() - 1; index++) {
            Field field = findField(current.getClass(), path.get(index));
            field.setAccessible(true);
            current = field.get(current);
            if (current == null) throw new IllegalStateException("Null config path");
        }
        Field field = findField(current.getClass(), path.getLast());
        field.setAccessible(true);
        Object existing = field.get(current);
        if (isProperty(field.getType()) && existing != null) {
            Method set = findSingleArgumentMethod(existing.getClass(), "set");
            set.invoke(existing, coerce(value, set.getParameterTypes()[0]));
        } else {
            field.set(current, coerce(value, field.getType()));
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            try {
                return cursor.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static Method findSingleArgumentMethod(Class<?> type, String name) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 1) return method;
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    private static boolean hasZeroArgumentMethod(Class<?> type, String name) {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            for (Method method : cursor.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == 0) return true;
            }
        }
        return false;
    }

    private static Object unwrap(Object value) throws ReflectiveOperationException {
        if (value != null && isProperty(value.getClass())) return value.getClass().getMethod("get").invoke(value);
        return value;
    }

    private static boolean isProperty(Class<?> type) {
        String name = type.getName();
        return name.endsWith(".Property") || name.contains("moulconfig.observer.Property");
    }

    private static @Nullable Object coerce(@Nullable Object value, Class<?> target) {
        if (value == null) return null;
        if (target.isInstance(value)) return value;
        if (value instanceof Number number) {
            if (target == int.class || target == Integer.class) return number.intValue();
            if (target == long.class || target == Long.class) return number.longValue();
            if (target == float.class || target == Float.class) return number.floatValue();
            if (target == double.class || target == Double.class) return number.doubleValue();
        }
        return value;
    }

    private static ValueKind valueKind(Class<?> type) {
        if (type == boolean.class || type == Boolean.class) return ValueKind.BOOLEAN;
        if (type == byte.class || type == short.class || type == int.class || type == long.class
                || type == Byte.class || type == Short.class || type == Integer.class || type == Long.class) {
            return ValueKind.INTEGER;
        }
        if (type == float.class || type == double.class || type == Float.class || type == Double.class) {
            return ValueKind.DECIMAL;
        }
        if (type.isEnum()) return ValueKind.ENUM;
        if (type == String.class) return ValueKind.STRING;
        return ValueKind.UNSUPPORTED;
    }

    private static boolean belongsToProvider(Provider provider, Class<?> type) {
        String name = type.getName();
        return switch (provider) {
            case SKYHANNI -> name.startsWith("at.hannibal2.skyhanni.config");
            case SKYBLOCKER -> name.startsWith("de.hysky.skyblocker.config");
            case BABYZOMBIE -> name.startsWith("top.babyzombie.addons.config");
            case FEESH -> name.startsWith("com.github.sleepypanda.feesh.settings");
            default -> false;
        };
    }

    static Classification classify(String path, String title, String description) {
        ConfigScreen.Category verified = classifyByVerifiedRule(path);
        if (verified != null) return new Classification(verified, ClassificationSource.VERIFIED_RULE, 1.0);
        LocalFeatureClassifier.Result local = LocalFeatureClassifier.classify(path, title, description);
        if (local != null) {
            return new Classification(local.category(), ClassificationSource.LOCAL_CLASSIFIER,
                    local.confidence());
        }
        return new Classification(ConfigScreen.Category.GENERAL, ClassificationSource.UNCLASSIFIED, 0.0);
    }

    private static ConfigScreen.@Nullable Category classifyByVerifiedRule(String path) {
        String normalized = path.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "dungeon", "catacomb", "terminal", "secret")) return ConfigScreen.Category.DUNGEONS;
        if (normalized.contains("slayer")) return ConfigScreen.Category.SLAYER;
        if (containsAny(normalized, "farming", "garden", "pest", "visitor", "crop")) return ConfigScreen.Category.FARMING;
        if (containsAny(normalized, "foraging", "galatea", "torrhus", "tree", "sweep")) return ConfigScreen.Category.FORAGING;
        if (containsAny(normalized, "fishing", "fish", "sea creature", "bobber", "lava fishing")) return ConfigScreen.Category.FISHING;
        if (containsAny(normalized, "hunting", "safari", "lasso", "critter", "shard")) return ConfigScreen.Category.HUNTING;
        if (containsAny(normalized, "mining", "dwarven", "crystal hollow", "glacite", "powder", "commission")) return ConfigScreen.Category.MINING;
        if (normalized.contains("rift")) return ConfigScreen.Category.RIFT;
        if (containsAny(normalized, "event", "carnival", "raffle", "anniversary", "spooky")) return ConfigScreen.Category.EVENTS;
        if (containsAny(normalized, "map", "waypoint", "fairy soul")) return ConfigScreen.Category.MAPS;
        if (containsAny(normalized, "inventory", "item", "storage", "tooltip", "pet", "menu", "wardrobe", "bazaar")) {
            return ConfigScreen.Category.ITEMS_AND_MENUS;
        }
        if (containsAny(normalized, "combat", "crimson", "kuudra", "dragon", "dojo", "mob")) {
            return ConfigScreen.Category.COMBAT;
        }
        if (containsAny(normalized, "chat", "notification", "performance", "interface", "general")) {
            return ConfigScreen.Category.GENERAL;
        }
        return null;
    }

    private static String groupName(String path, String fallback) {
        String normalized = path.toLowerCase(Locale.ROOT);
        if (normalized.contains("safari")) return "Safari";
        if (normalized.contains("crimson")) return "Crimson Isle";
        if (normalized.contains("kuudra")) return "Kuudra";
        if (normalized.contains("garden")) return "Garden";
        if (normalized.contains("rift")) return "Rift";
        if (normalized.contains("dungeon")) return "Dungeons";
        return humanize(fallback);
    }

    private static String canonicalId(ConfigScreen.Category category, String title) {
        String normalized = title.toLowerCase(Locale.ROOT).replaceAll("§[0-9a-fk-or]", "")
                .replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return alias(category.name().toLowerCase(Locale.ROOT) + ":" + normalized);
    }

    private static String alias(String id) {
        Map<String, String> aliases = Map.ofEntries(
                Map.entry("items_and_menus:pet_hud", "items_and_menus:pet_display"),
                Map.entry("items_and_menus:pet_overlay", "items_and_menus:pet_display"),
                Map.entry("items_and_menus:pet_display", "items_and_menus:pet_display"),
                Map.entry("maps:fairy_soul_waypoints", "maps:fairy_souls"),
                Map.entry("maps:fairy_souls", "maps:fairy_souls"),
                Map.entry("hunting:lasso_display", "hunting:lasso_hud"),
                Map.entry("hunting:lasso_hud", "hunting:lasso_hud"),
                Map.entry("general:save_cursor_position", "items_and_menus:cursor_memory"),
                Map.entry("items_and_menus:save_cursor_position", "items_and_menus:cursor_memory"),
                Map.entry("items_and_menus:cursor_memory", "items_and_menus:cursor_memory"),
                Map.entry("items_and_menus:lore_timers", "items_and_menus:item_timestamps"),
                Map.entry("items_and_menus:item_timestamps", "items_and_menus:item_timestamps")
        );
        return aliases.getOrDefault(id, id);
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    private static List<String> append(List<String> path, String value) {
        List<String> result = new ArrayList<>(path);
        result.add(value);
        return List.copyOf(result);
    }

    private static String humanize(String value) {
        if (value == null || value.isBlank()) return "Feature";
        String spaced = value.replace('_', ' ').replace('-', ' ')
                .replaceAll("([a-z0-9])([A-Z])", "$1 $2").replaceAll("\\s+", " ").trim();
        StringBuilder result = new StringBuilder();
        for (String word : spaced.split(" ")) {
            if (!result.isEmpty()) result.append(' ');
            if (word.length() <= 3 && word.equals(word.toUpperCase(Locale.ROOT))) result.append(word);
            else result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private static String memberLabel(Field field, String fallback) {
        return annotationText(field, "ConfigOption", "name", fallback);
    }

    private static String memberDescription(Field field, String fallback) {
        return annotationText(field, "ConfigOption", "desc", fallback);
    }

    private static String annotationText(Field field, String annotationName, String property, String fallback) {
        for (Annotation annotation : field.getAnnotations()) {
            if (!annotation.annotationType().getSimpleName().equals(annotationName)) continue;
            try {
                String value = String.valueOf(annotation.annotationType().getMethod(property).invoke(annotation));
                if (!value.isBlank()) {
                    String translated = Component.translatable(value).getString();
                    return translated.equals(value) ? humanize(value) : translated;
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { }
        }
        return fallback;
    }

    private static @Nullable double[] sliderRange(Field field) {
        for (Annotation annotation : field.getAnnotations()) {
            if (!annotation.annotationType().getSimpleName().contains("Slider")) continue;
            try {
                double minimum = ((Number) annotation.annotationType().getMethod("minValue").invoke(annotation)).doubleValue();
                double maximum = ((Number) annotation.annotationType().getMethod("maxValue").invoke(annotation)).doubleValue();
                if (maximum > minimum) return new double[]{minimum, maximum};
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { }
        }
        return null;
    }

    private static String componentString(Object owner, String getter, String fallback) {
        try {
            Object component = owner.getClass().getMethod(getter).invoke(owner);
            if (component instanceof Component value) {
                String text = value.getString();
                if (!text.isBlank() && !text.contains("firmament.config.")) return text;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { }
        return fallback;
    }

    private static void markFirmamentDirty(Object config) throws ReflectiveOperationException {
        Method method = config.getClass().getMethod("markDirty", java.util.concurrent.CompletableFuture.class);
        method.invoke(config, new Object[]{null});
    }

    private static final class MutableUnified {
        final String id;
        @Nullable String title;
        @Nullable String description;
        ConfigScreen.@Nullable Category category;
        @Nullable String group;
        ConfigScreen.@Nullable Feature local;
        final List<NativeFeature> external = new ArrayList<>();

        MutableUnified(String id) {
            this.id = id;
        }
    }
}

package cloudy.autume.addition.config;

import java.util.Arrays;
import java.util.List;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;

/** Data-driven secondary-menu options shared by the Foraging, Hunting, and Safari categories. */
enum HuntingOption {
    CHAPTER(ConfigScreen.Feature.TORRHUS_TRACKER, "config.hunting.chapter", h -> h.showChapter, (h, v) -> h.showChapter = v),
    CURRENT_TASK(ConfigScreen.Feature.TORRHUS_TRACKER, "config.hunting.current_task", h -> h.showCurrentTask, (h, v) -> h.showCurrentTask = v),
    TASK_PROGRESS(ConfigScreen.Feature.TORRHUS_TRACKER, "config.hunting.task_progress", h -> h.showTaskProgress, (h, v) -> h.showTaskProgress = v),
    COMPLETED_TASKS(ConfigScreen.Feature.TORRHUS_TRACKER, "config.hunting.completed_tasks", h -> h.showCompletedTasks, (h, v) -> h.showCompletedTasks = v),
    TOTAL_PROGRESS(ConfigScreen.Feature.TORRHUS_TRACKER, "config.hunting.total_progress", h -> h.showChapterTotalProgress, (h, v) -> h.showChapterTotalProgress = v),
    NEXT_UNLOCK(ConfigScreen.Feature.TORRHUS_TRACKER, "config.hunting.next_unlock", h -> h.showNextUnlock, (h, v) -> h.showNextUnlock = v),
    FOREST_WHISPERS(ConfigScreen.Feature.TORRHUS_TRACKER, "config.hunting.forest_whispers", h -> h.showForestWhispers, (h, v) -> h.showForestWhispers = v),
    DESERT_WHISPERS(ConfigScreen.Feature.TORRHUS_TRACKER, "config.hunting.desert_whispers", h -> h.showDesertWhispers, (h, v) -> h.showDesertWhispers = v),
    FOREST_ESSENCE(ConfigScreen.Feature.TORRHUS_TRACKER, "config.hunting.forest_essence", h -> h.showForestEssence, (h, v) -> h.showForestEssence = v),
    SAFARI_ESSENCE_TORRHUS(ConfigScreen.Feature.TORRHUS_TRACKER, "config.hunting.safari_essence_torrhus", h -> h.showSafariEssenceTorrhus, (h, v) -> h.showSafariEssenceTorrhus = v),
    SWEEP(ConfigScreen.Feature.TORRHUS_TRACKER, "config.hunting.sweep", h -> h.showSweep, (h, v) -> h.showSweep = v),
    FOREST_FORTUNE(ConfigScreen.Feature.TORRHUS_TRACKER, "config.hunting.forest_fortune", h -> h.showForestFortune, (h, v) -> h.showForestFortune = v),

    GALATEA_CHAPTER(ConfigScreen.Feature.GALATEA_TRACKER, "config.hunting.chapter", h -> h.showChapter, (h, v) -> h.showChapter = v),
    GALATEA_CURRENT_TASK(ConfigScreen.Feature.GALATEA_TRACKER, "config.hunting.current_task", h -> h.showCurrentTask, (h, v) -> h.showCurrentTask = v),
    GALATEA_TASK_PROGRESS(ConfigScreen.Feature.GALATEA_TRACKER, "config.hunting.task_progress", h -> h.showTaskProgress, (h, v) -> h.showTaskProgress = v),
    GALATEA_COMPLETED_TASKS(ConfigScreen.Feature.GALATEA_TRACKER, "config.hunting.completed_tasks", h -> h.showCompletedTasks, (h, v) -> h.showCompletedTasks = v),
    GALATEA_TOTAL_PROGRESS(ConfigScreen.Feature.GALATEA_TRACKER, "config.hunting.total_progress", h -> h.showChapterTotalProgress, (h, v) -> h.showChapterTotalProgress = v),
    GALATEA_NEXT_UNLOCK(ConfigScreen.Feature.GALATEA_TRACKER, "config.hunting.next_unlock", h -> h.showNextUnlock, (h, v) -> h.showNextUnlock = v),
    GALATEA_FOREST_WHISPERS(ConfigScreen.Feature.GALATEA_TRACKER, "config.hunting.forest_whispers", h -> h.showForestWhispers, (h, v) -> h.showForestWhispers = v),
    GALATEA_DESERT_WHISPERS(ConfigScreen.Feature.GALATEA_TRACKER, "config.hunting.desert_whispers", h -> h.showDesertWhispers, (h, v) -> h.showDesertWhispers = v),
    GALATEA_FOREST_ESSENCE(ConfigScreen.Feature.GALATEA_TRACKER, "config.hunting.forest_essence", h -> h.showForestEssence, (h, v) -> h.showForestEssence = v),
    GALATEA_SAFARI_ESSENCE(ConfigScreen.Feature.GALATEA_TRACKER, "config.hunting.safari_essence_torrhus", h -> h.showSafariEssenceTorrhus, (h, v) -> h.showSafariEssenceTorrhus = v),
    GALATEA_SWEEP(ConfigScreen.Feature.GALATEA_TRACKER, "config.hunting.sweep", h -> h.showSweep, (h, v) -> h.showSweep = v),
    GALATEA_FOREST_FORTUNE(ConfigScreen.Feature.GALATEA_TRACKER, "config.hunting.forest_fortune", h -> h.showForestFortune, (h, v) -> h.showForestFortune = v),

    NEXT_BRACKET(ConfigScreen.Feature.MIRIA_CONTEST, "config.hunting.next_bracket", h -> h.contestNextBracket, (h, v) -> h.contestNextBracket = v),
    EXPECTED_TICKET(ConfigScreen.Feature.MIRIA_CONTEST, "config.hunting.expected_ticket", h -> h.contestExpectedTicket, (h, v) -> h.contestExpectedTicket = v),
    REMAINING_SCORE(ConfigScreen.Feature.MIRIA_CONTEST, "config.hunting.remaining_score", h -> h.contestRemainingScore, (h, v) -> h.contestRemainingScore = v),

    AGATHA_NEXT_BRACKET(ConfigScreen.Feature.AGATHA_CONTEST, "config.hunting.next_bracket", h -> h.contestNextBracket, (h, v) -> h.contestNextBracket = v),
    AGATHA_EXPECTED_TICKET(ConfigScreen.Feature.AGATHA_CONTEST, "config.hunting.expected_ticket", h -> h.contestExpectedTicket, (h, v) -> h.contestExpectedTicket = v),
    AGATHA_REMAINING_SCORE(ConfigScreen.Feature.AGATHA_CONTEST, "config.hunting.remaining_score", h -> h.contestRemainingScore, (h, v) -> h.contestRemainingScore = v),

    BLUE_JAY(ConfigScreen.Feature.CRITTER_BEHAVIOR, "config.hunting.blue_jay", h -> h.blueJayAssistant, (h, v) -> h.blueJayAssistant = v),
    GOLDOLOT(ConfigScreen.Feature.CRITTER_BEHAVIOR, "config.hunting.goldolot", h -> h.goldolotAssistant, (h, v) -> h.goldolotAssistant = v),
    DUSTYBIT(ConfigScreen.Feature.CRITTER_BEHAVIOR, "config.hunting.dustybit", h -> h.dustybitAssistant, (h, v) -> h.dustybitAssistant = v),
    HIDEONSUN(ConfigScreen.Feature.CRITTER_BEHAVIOR, "config.hunting.hideonsun", h -> h.hideonsunAssistant, (h, v) -> h.hideonsunAssistant = v),
    CRITTER_SOUND(ConfigScreen.Feature.CRITTER_BEHAVIOR, "config.alert.sound", h -> h.critterBehaviorAudio.sound, (h, v) -> h.critterBehaviorAudio.sound = v),
    CRITTER_VOLUME(ConfigScreen.Feature.CRITTER_BEHAVIOR, "config.alert.volume", 0, 100, "%", h -> h.critterBehaviorAudio.volume, (h, v) -> h.critterBehaviorAudio.volume = v),

    BEEHEEMOTH_OUTLINE(ConfigScreen.Feature.BEEHEEMOTH_HELPER, "config.hunting.beeheemoth_outline", h -> h.beeheemothOutline, (h, v) -> h.beeheemothOutline = v),
    BEEHEEMOTH_BEACON(ConfigScreen.Feature.BEEHEEMOTH_HELPER, "config.hunting.beeheemoth_beacon", h -> h.beeheemothBeacon, (h, v) -> h.beeheemothBeacon = v),
    BEEHEEMOTH_COLOR(ConfigScreen.Feature.BEEHEEMOTH_HELPER, "config.hunting.beeheemoth_color", Type.COLOR,
            h -> h.beeheemothOutlineColor, (h, v) -> h.beeheemothOutlineColor = v),
    BEEHEEMOTH_SOUND(ConfigScreen.Feature.BEEHEEMOTH_HELPER, "config.hunting.beeheemoth_sound",
            h -> h.beeheemothSound, (h, v) -> h.beeheemothSound = v),
    BEEHEEMOTH_VOLUME(ConfigScreen.Feature.BEEHEEMOTH_HELPER, "config.hunting.beeheemoth_volume", 0, 100, "%",
            h -> h.beeheemothSoundVolume, (h, v) -> h.beeheemothSoundVolume = v),

    LASSO_REEL_VOLUME(ConfigScreen.Feature.LASSO_REEL_SOUND, "config.alert.volume", 0, 100, "%",
            h -> h.lassoReelAudio.volume, (h, v) -> h.lassoReelAudio.volume = v),

    BENEFACTOR_STATUS(ConfigScreen.Feature.BENEFACTOR_HUD, "config.hunting.benefactor_status", h -> h.benefactorStatus, (h, v) -> h.benefactorStatus = v),
    BENEFACTOR_TIMER(ConfigScreen.Feature.BENEFACTOR_HUD, "config.hunting.benefactor_timer", h -> h.benefactorTimer, (h, v) -> h.benefactorTimer = v),
    BENEFACTOR_EFFECTS(ConfigScreen.Feature.BENEFACTOR_HUD, "config.hunting.benefactor_effects", h -> h.benefactorEffects, (h, v) -> h.benefactorEffects = v),
    BENEFACTOR_DONATION(ConfigScreen.Feature.BENEFACTOR_HUD, "config.hunting.benefactor_donation", h -> h.benefactorDonation, (h, v) -> h.benefactorDonation = v),
    BENEFACTOR_SOUND(ConfigScreen.Feature.BENEFACTOR_HUD, "config.alert.sound", h -> h.benefactorAudio.sound, (h, v) -> h.benefactorAudio.sound = v),
    BENEFACTOR_VOLUME(ConfigScreen.Feature.BENEFACTOR_HUD, "config.alert.volume", 0, 100, "%", h -> h.benefactorAudio.volume, (h, v) -> h.benefactorAudio.volume = v),

    TREE_FIREFOX(ConfigScreen.Feature.TREE_GIFT_ALERTS, "config.hunting.tree_firefox", "Firefox"),
    TREE_GROUNDHOG(ConfigScreen.Feature.TREE_GIFT_ALERTS, "config.hunting.tree_groundhog", "Groundhog"),
    TREE_DRYBARK(ConfigScreen.Feature.TREE_GIFT_ALERTS, "config.hunting.tree_drybark", "Drybark"),
    TREE_PUCK(ConfigScreen.Feature.TREE_GIFT_ALERTS, "config.hunting.tree_puck", "Puck"),
    TREE_GRIZZLY(ConfigScreen.Feature.TREE_GIFT_ALERTS, "config.hunting.tree_grizzly", "Grizzly Bear"),
    TREE_SIGNAL(ConfigScreen.Feature.TREE_GIFT_ALERTS, "config.hunting.tree_signal", "Signal Enhancer"),
    TREE_CHAMELEON(ConfigScreen.Feature.TREE_GIFT_ALERTS, "config.hunting.tree_chameleon", "Chameleon Shard"),
    TREE_HUMMINGBIRD(ConfigScreen.Feature.TREE_GIFT_ALERTS, "config.hunting.tree_hummingbird", "Hummingbird Shard"),
    TREE_DREADWING(ConfigScreen.Feature.TREE_GIFT_ALERTS, "config.hunting.tree_dreadwing", "Dreadwing"),
    TREE_KARMA(ConfigScreen.Feature.TREE_GIFT_ALERTS, "config.hunting.tree_karma", "Enchanted Book (Karma I)"),
    TREE_MANGO_DYE(ConfigScreen.Feature.TREE_GIFT_ALERTS, "config.hunting.tree_mango_dye", "Mango Dye"),
    TREE_GIFT_SOUND(ConfigScreen.Feature.TREE_GIFT_ALERTS, "config.alert.sound", h -> h.treeGiftAudio.sound, (h, v) -> h.treeGiftAudio.sound = v),
    TREE_GIFT_VOLUME(ConfigScreen.Feature.TREE_GIFT_ALERTS, "config.alert.volume", 0, 100, "%", h -> h.treeGiftAudio.volume, (h, v) -> h.treeGiftAudio.volume = v),

    RUN_TIME(ConfigScreen.Feature.SAFARI_DASHBOARD, "config.hunting.run_time", h -> h.safariRunTime, (h, v) -> h.safariRunTime = v),
    TICKET_TIER(ConfigScreen.Feature.SAFARI_DASHBOARD, "config.hunting.ticket_tier", h -> h.safariTicketTier, (h, v) -> h.safariTicketTier = v),
    EAGLE_STATUS(ConfigScreen.Feature.SAFARI_DASHBOARD, "config.hunting.eagle_status",
            h -> h.safariEagleStatus, (h, v) -> h.safariEagleStatus = v),

    BIOME_PROGRESS(ConfigScreen.Feature.SAFARI_CRITTERDEX, "config.hunting.biome_progress", h -> h.critterdexBiomeProgress, (h, v) -> h.critterdexBiomeProgress = v),
    CAPTURED_NAMES(ConfigScreen.Feature.SAFARI_CRITTERDEX, "config.hunting.captured_names", h -> h.critterdexCapturedNames, (h, v) -> h.critterdexCapturedNames = v),
    MISSING_NAMES(ConfigScreen.Feature.SAFARI_CRITTERDEX, "config.hunting.missing_names", h -> h.critterdexMissingNames, (h, v) -> h.critterdexMissingNames = v),

    SPARKLING_BIOME(ConfigScreen.Feature.SPARKLING_ALERT, "config.hunting.sparkling_biome", h -> h.sparklingShowBiome, (h, v) -> h.sparklingShowBiome = v),
    SPARKLING_SOUND(ConfigScreen.Feature.SPARKLING_ALERT, "config.alert.sound", h -> h.sparklingAudio.sound, (h, v) -> h.sparklingAudio.sound = v),
    SPARKLING_VOLUME(ConfigScreen.Feature.SPARKLING_ALERT, "config.alert.volume", 0, 100, "%", h -> h.sparklingAudio.volume, (h, v) -> h.sparklingAudio.volume = v),
    SPARKLING_OUTLINE(ConfigScreen.Feature.SPARKLING_ALERT, "config.hunting.sparkling_outline", h -> h.sparklingOutline, (h, v) -> h.sparklingOutline = v),
    SPARKLING_COLOR(ConfigScreen.Feature.SPARKLING_ALERT, "config.hunting.sparkling_color", Type.COLOR,
            h -> h.sparklingOutlineColor, (h, v) -> h.sparklingOutlineColor = v),

    FLOOR_ALERT(ConfigScreen.Feature.FLOOR_QUEST_ASSISTANT, "config.hunting.floor_alert", h -> h.floorDropAlert, (h, v) -> h.floorDropAlert = v),
    FLOOR_DISTANCE(ConfigScreen.Feature.FLOOR_QUEST_ASSISTANT, "config.hunting.floor_distance", h -> h.floorDropDistance, (h, v) -> h.floorDropDistance = v),
    QUEST_ITEMS(ConfigScreen.Feature.FLOOR_QUEST_ASSISTANT, "config.hunting.quest_items", h -> h.questItemTracker, (h, v) -> h.questItemTracker = v),
    FLOOR_SOUND(ConfigScreen.Feature.FLOOR_QUEST_ASSISTANT, "config.alert.sound", h -> h.floorDropAudio.sound, (h, v) -> h.floorDropAudio.sound = v),
    FLOOR_VOLUME(ConfigScreen.Feature.FLOOR_QUEST_ASSISTANT, "config.alert.volume", 0, 100, "%", h -> h.floorDropAudio.volume, (h, v) -> h.floorDropAudio.volume = v),

    WUMPA_REQUIREMENTS(ConfigScreen.Feature.WUMPA_HUD, "config.hunting.wumpa_requirements", h -> h.wumpaRequirements, (h, v) -> h.wumpaRequirements = v),
    WUMPA_PHASE(ConfigScreen.Feature.WUMPA_HUD, "config.hunting.wumpa_phase", h -> h.wumpaPhase, (h, v) -> h.wumpaPhase = v),
    WUMPA_ALERTS(ConfigScreen.Feature.WUMPA_HUD, "config.hunting.wumpa_alerts", h -> h.wumpaAlerts, (h, v) -> h.wumpaAlerts = v),
    WUMPA_FAILURE(ConfigScreen.Feature.WUMPA_HUD, "config.hunting.wumpa_failure", h -> h.wumpaFailureWarning, (h, v) -> h.wumpaFailureWarning = v),
    WUMPA_ROUTE(ConfigScreen.Feature.WUMPA_HUD, "config.hunting.wumpa_route", h -> h.wumpaRoutePrediction, (h, v) -> h.wumpaRoutePrediction = v),
    WUMPA_SOUND(ConfigScreen.Feature.WUMPA_HUD, "config.alert.sound", h -> h.wumpaAudio.sound, (h, v) -> h.wumpaAudio.sound = v),
    WUMPA_VOLUME(ConfigScreen.Feature.WUMPA_HUD, "config.alert.volume", 0, 100, "%", h -> h.wumpaAudio.volume, (h, v) -> h.wumpaAudio.volume = v),

    SNOOZLE_WALL_COLOR(ConfigScreen.Feature.SNOOZLE_WALL_OVERLAY, "config.hunting.snoozle_wall_color", Type.COLOR,
            h -> h.snoozleWallOverlayColor, (h, v) -> h.snoozleWallOverlayColor = v),

    COLD_FIRST(ConfigScreen.Feature.COLD_SAFETY, "config.hunting.cold_first", 0, 98, "",
            h -> h.coldFirstThreshold, (h, v) -> h.coldFirstThreshold = Math.min(v, h.coldSecondThreshold - 1)),
    COLD_SECOND(ConfigScreen.Feature.COLD_SAFETY, "config.hunting.cold_second", 1, 99, "",
            h -> h.coldSecondThreshold, (h, v) -> h.coldSecondThreshold = Math.max(v, h.coldFirstThreshold + 1)),
    COLD_CAMPFIRE(ConfigScreen.Feature.COLD_SAFETY, "config.hunting.cold_campfire", h -> h.coldCampfireBeacon, (h, v) -> h.coldCampfireBeacon = v),
    COLD_SOUND(ConfigScreen.Feature.COLD_SAFETY, "config.alert.sound", h -> h.coldAudio.sound, (h, v) -> h.coldAudio.sound = v),
    COLD_VOLUME(ConfigScreen.Feature.COLD_SAFETY, "config.alert.volume", 0, 100, "%", h -> h.coldAudio.volume, (h, v) -> h.coldAudio.volume = v),

    DOOMSPIRAL_SOUND(ConfigScreen.Feature.DOOMSPIRAL_READY, "config.alert.sound", h -> h.doomspiralAudio.sound, (h, v) -> h.doomspiralAudio.sound = v),
    DOOMSPIRAL_VOLUME(ConfigScreen.Feature.DOOMSPIRAL_READY, "config.alert.volume", 0, 100, "%", h -> h.doomspiralAudio.volume, (h, v) -> h.doomspiralAudio.volume = v),

    WARDEN_READY_SOUND(ConfigScreen.Feature.WARDEN_READY_ALERT, "config.alert.sound", h -> h.wardenReadyAudio.sound, (h, v) -> h.wardenReadyAudio.sound = v),
    WARDEN_READY_VOLUME(ConfigScreen.Feature.WARDEN_READY_ALERT, "config.alert.volume", 0, 100, "%", h -> h.wardenReadyAudio.volume, (h, v) -> h.wardenReadyAudio.volume = v),

    BELT_MILESTONES(ConfigScreen.Feature.SAFARI_BELT, "config.hunting.belt_milestones", h -> h.safariBeltMilestones, (h, v) -> h.safariBeltMilestones = v),
    BELT_BONUSES(ConfigScreen.Feature.SAFARI_BELT, "config.hunting.belt_bonuses", h -> h.safariBeltBonuses, (h, v) -> h.safariBeltBonuses = v);

    enum Type { BOOLEAN, SLIDER, COLOR }

    final ConfigScreen.Feature owner;
    final String labelKey;
    final Type type;
    final BooleanGetter booleanGetter;
    final BooleanSetter booleanSetter;
    final ToIntFunction<ModConfig.Hunting> intGetter;
    final ObjIntConsumer<ModConfig.Hunting> intSetter;
    final int minimum;
    final int maximum;
    final String suffix;
    final String lootName;

    HuntingOption(ConfigScreen.Feature owner, String labelKey, BooleanGetter getter, BooleanSetter setter) {
        this.owner = owner; this.labelKey = labelKey; this.type = Type.BOOLEAN;
        this.booleanGetter = getter; this.booleanSetter = setter;
        this.intGetter = null; this.intSetter = null; this.minimum = 0; this.maximum = 0; this.suffix = ""; this.lootName = null;
    }

    HuntingOption(ConfigScreen.Feature owner, String labelKey, int minimum, int maximum, String suffix,
                  ToIntFunction<ModConfig.Hunting> getter, ObjIntConsumer<ModConfig.Hunting> setter) {
        this.owner = owner; this.labelKey = labelKey; this.type = Type.SLIDER;
        this.booleanGetter = null; this.booleanSetter = null;
        this.intGetter = getter; this.intSetter = setter; this.minimum = minimum; this.maximum = maximum;
        this.suffix = suffix; this.lootName = null;
    }

    HuntingOption(ConfigScreen.Feature owner, String labelKey, Type type,
                  ToIntFunction<ModConfig.Hunting> getter, ObjIntConsumer<ModConfig.Hunting> setter) {
        this.owner = owner; this.labelKey = labelKey; this.type = type;
        this.booleanGetter = null; this.booleanSetter = null;
        this.intGetter = getter; this.intSetter = setter; this.minimum = 0; this.maximum = 0xFFFFFF;
        this.suffix = ""; this.lootName = null;
    }

    HuntingOption(ConfigScreen.Feature owner, String labelKey, String lootName) {
        this.owner = owner; this.labelKey = labelKey; this.type = Type.BOOLEAN;
        this.booleanGetter = null; this.booleanSetter = null;
        this.intGetter = null; this.intSetter = null; this.minimum = 0; this.maximum = 0;
        this.suffix = ""; this.lootName = lootName;
    }

    static List<HuntingOption> forFeature(ConfigScreen.Feature feature) {
        return Arrays.stream(values()).filter(option -> option.owner == feature).toList();
    }

    boolean booleanValue(ModConfig.Hunting hunting) {
        return lootName == null ? booleanGetter.get(hunting)
                : Boolean.TRUE.equals(hunting.treeGiftLoot.get(lootName));
    }

    void toggle(ModConfig.Hunting hunting) {
        boolean value = !booleanValue(hunting);
        if (lootName == null) booleanSetter.set(hunting, value);
        else hunting.treeGiftLoot.put(lootName, value);
    }

    int intValue(ModConfig.Hunting hunting) { return intGetter.applyAsInt(hunting); }
    void setInt(ModConfig.Hunting hunting, int value) { intSetter.accept(hunting, value); }

    @FunctionalInterface interface BooleanGetter { boolean get(ModConfig.Hunting hunting); }
    @FunctionalInterface interface BooleanSetter { void set(ModConfig.Hunting hunting, boolean value); }
}

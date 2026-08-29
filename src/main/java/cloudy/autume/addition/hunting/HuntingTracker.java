package cloudy.autume.addition.hunting;

import cloudy.autume.addition.compat.MinecraftClientCompat;
import cloudy.autume.addition.config.ConfigManager;
import cloudy.autume.addition.config.ModConfig;
import cloudy.autume.addition.hud.CompactNumbers;
import cloudy.autume.addition.i18n.ModText;
import cloudy.autume.addition.inventory.ProfileContext;
import cloudy.autume.addition.tracker.IslandArea;
import cloudy.autume.addition.tracker.LocationTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HuntingTracker {
    private static final Pattern CAPTURED_MOBS = Pattern.compile("(?i)Captured Mobs\\s*:?\\s*([0-9,]+)");
    private static final long BEHAVIOR_CAPTURE_SUPPRESS_MS = 3_000L;
    private static final long BENEFATOR_WARNING_MS = 30_000L;
    private static final int FLOOR_SCAN_RADIUS = 10;
    private static final int FLOOR_SCAN_VERTICAL = 3;
    private static final int CAMPFIRE_CHUNK_RADIUS = 6;
    private static final double WUMPA_ROUTE_LENGTH = 32.0;
    private static final double WUMPA_CHASE_SPEED_SQR = 0.0025;
    private static final int SNOOZLE_WALL_SCAN_RADIUS = 18;
    private static final int SNOOZLE_WALL_SCAN_VERTICAL = 10;
    private static final int SNOOZLE_WALL_MIN_BLOCKS = 4;
    private static final int SNOOZLE_WALL_MAX_BLOCKS = 96;
    private static final float BEEHEEMOTH_SCALE = 9.0f;
    private static final float BEEHEEMOTH_SCALE_TOLERANCE = 0.01f;
    private static final double BEEHEEMOTH_BEACON_DISMISS_DISTANCE_SQR = 10.0 * 10.0;
    private static final double BEEHEEMOTH_SOUND_RADIUS_SQR = 12.0 * 12.0;
    private static final long BEEHEEMOTH_SOUND_GRACE_MS = 3_000L;
    private static final long CHAPTER_CHAT_WINDOW_MS = 4_000L;
    private static final int CHAPTER_CHAT_MAX_LINES = 12;
    private static final Set<String> BEHAVIOR_CRITTERS = Set.of("Blue Jay", "Goldolot", "Dustybit", "Hideonsun");

    private static final EnumMap<HuntingTextParser.Resource, Double> resources =
            new EnumMap<>(HuntingTextParser.Resource.class);
    private static final LinkedHashMap<String, Integer> shardDrops = new LinkedHashMap<>();
    private static final LinkedHashSet<String> capturedCritters = new LinkedHashSet<>();
    private static final LinkedHashSet<String> wumpaPrerequisiteCaptures = new LinkedHashSet<>();
    private static final LinkedHashMap<String, Integer> questItems = new LinkedHashMap<>();
    private static final Set<Long> knownFloorDrops = new HashSet<>();
    private static final Set<UUID> knownSparklingEntities = new HashSet<>();
    private static final Set<UUID> readyWardens = new HashSet<>();
    private static final List<String> recentChapterChat = new ArrayList<>();
    private static final TreeGiftAlertSession treeGiftAlerts = new TreeGiftAlertSession();

    private static HuntingTextParser.ChapterSnapshot chapter = HuntingTextParser.ChapterSnapshot.EMPTY;
    private static HuntingTextParser.ContestSnapshot contest = HuntingTextParser.ContestSnapshot.EMPTY;
    private static IslandArea lastArea = IslandArea.NONE;
    private static long safariStartedAt;
    private static int safariShardCount;
    private static int capturedMobs;
    private static String safariTicketTier = "";
    private static String safariBiome = "";
    private static double nearestFloorDrop = -1;
    private static String behaviorName = "";
    private static String behaviorStatus = "";
    private static String recentlyCapturedBehavior = "";
    private static long behaviorCaptureSuppressedUntil;
    private static UUID stillTarget;
    private static int stillTicks;
    private static boolean stillReadyAlerted;
    private static double previousPlayerX = Double.NaN;
    private static double previousPlayerZ = Double.NaN;
    private static WumpaPhase wumpaPhase = WumpaPhase.NONE;
    private static UUID trackedWumpa;
    private static Vec3 previousWumpaPosition;
    private static Vec3 wumpaRouteStart;
    private static Vec3 wumpaRouteEnd;
    private static boolean wumpaSpawnAlerted;
    private static int wumpaMovingTicks;
    private static int wumpaStillTicks;
    private static List<WallFace> snoozleWallFaces = List.of();
    private static int cold = -1;
    private static boolean coldFalling;
    private static boolean firstColdAlerted;
    private static boolean secondColdAlerted;
    private static BlockPos nearestCampfire;
    private static int lastCampfireScanTick = Integer.MIN_VALUE / 2;
    private static boolean doomspiralReadyAlerted;
    private static BenefactorState benefactor = BenefactorState.EMPTY;
    private static boolean benefactorEndingAlerted;
    private static long benefactorAuthoritativeUntil;
    private static HuntingTextParser.TreeCritterTimer treeCritterTimer;
    private static UUID trackedBeeheemoth;
    private static BlockPos beeheemothSpawn;
    private static boolean beeheemothBeaconDismissed;
    private static Vec3 recentBeeheemothSoundOrigin;
    private static long beeheemothSoundOriginExpiresAt;
    private static boolean lassoReelActive;
    private static String activeProgressKey = "";
    private static long lastChapterChatAt;
    private static int ticks;

    private HuntingTracker() {
    }

    public static void updateReceivedText(List<String> tabLines, List<String> scoreboardLines) {
        ensureProfileLoaded(Minecraft.getInstance());
        List<String> combined = new ArrayList<>(tabLines.size() + scoreboardLines.size());
        combined.addAll(tabLines);
        combined.addAll(scoreboardLines);
        if (isForagingChapterArea(LocationTracker.area())) {
            rememberReceivedChapter(tabLines);
            rememberReceivedChapter(scoreboardLines);
            HuntingTextParser.ContestSnapshot parsedContest = HuntingTextParser.contest(combined);
            if (parsedContest.active()) contest = parsedContest;
        }
        applyBenefactor(HuntingTextParser.benefactor(tabLines));
        applyBenefactor(HuntingTextParser.benefactor(scoreboardLines));
        applyResources(HuntingTextParser.resources(combined), false);
        if (LocationTracker.area() == IslandArea.CRITTER_SAFARI) {
            updateSafariText(combined);
            updateCold(HuntingTextParser.cold(combined));
        }
    }

    public static void onMessage(String raw, boolean overlay) {
        ensureProfileLoaded(Minecraft.getInstance());
        String line = HuntingTextParser.plain(raw);
        if (line.isBlank()) return;
        if (HuntingTextParser.fairySoulConfirmation(line)) markNearestFairySoulFound();
        if (LocationTracker.area() == IslandArea.TORRHUS_CANYON
                && HuntingTextParser.captureConfirmation(line)) suppressCapturedBehavior(line);
        if (LocationTracker.area() == IslandArea.TORRHUS_CANYON
                && HuntingTextParser.captureConfirmation(line)
                && line.toLowerCase(Locale.ROOT).contains("beeheemoth")) {
            beeheemothBeaconDismissed = true;
            beeheemothSpawn = null;
        }
        applyResources(HuntingTextParser.resources(List.of(line)), true);
        if (isForagingChapterArea(LocationTracker.area())) {
            rememberChapterMessage(line);
            HuntingTextParser.ContestSnapshot parsedContest = HuntingTextParser.contest(List.of(line));
            if (parsedContest.active()) contest = parsedContest;
        }

        if (LocationTracker.area() == IslandArea.CRITTER_SAFARI) {
            updateCold(HuntingTextParser.cold(List.of(line)));
            HuntingTextParser.Capture capture = HuntingTextParser.capture(line);
            if (capture != null) recordCapture(capture);
            String biome = HuntingTextParser.sparklingBiome(line);
            if (!biome.isBlank() && ConfigManager.get().hunting.sparklingAlert) {
                safariBiome = biome;
                String subtitle = ConfigManager.get().hunting.sparklingShowBiome ? biome : "SPARKLING Critter";
                HuntingAlertManager.show(HuntingAlertManager.Channel.SPARKLING,
                        "sparkling-chat:" + biome, "SPARKLING CRITTER", subtitle, 8_000L);
            }
            updateWumpaFromMessage(line);
        }

        HuntingTextParser.CritterProgress progress = HuntingTextParser.critterProgress(line);
        if (progress != null && ConfigManager.get().hunting.critterBehavior) {
            behaviorName = progress.name();
            behaviorStatus = progress.current() + "/" + progress.target();
            if (progress.current() >= progress.target()) {
                HuntingAlertManager.show(HuntingAlertManager.Channel.CRITTER_BEHAVIOR,
                        "critter-ready:" + progress.name(), "CRITTER READY", progress.name());
            }
        }
        applyBenefactor(HuntingTextParser.benefactor(List.of(line)));
    }

    /** Includes SHOW_TEXT hover data already carried by the received chat component. */
    public static void onMessage(Component message, boolean overlay) {
        if (message == null) return;
        onMessage(message.getString(), overlay);
        var config = ConfigManager.get().hunting;
        if (overlay) return;
        if (!LocationTracker.isSkyBlock() || !config.treeGiftAlerts) {
            treeGiftAlerts.reset();
            return;
        }
        for (String loot : treeGiftAlerts.accept(message, config.treeGiftLoot, System.currentTimeMillis())) {
            alertTreeGiftLoot(loot);
        }
    }

    public static void tick(Minecraft client) {
        ticks++;
        ensureProfileLoaded(client);
        IslandArea area = LocationTracker.area();
        if (area != lastArea) {
            onAreaChanged(lastArea, area);
            lastArea = area;
        }
        if (isForagingChapterArea(area) || area == IslandArea.CRITTER_SAFARI) {
            tickLassoReelSound(client);
        } else {
            lassoReelActive = false;
        }
        tickBenefactor();
        if (ticks % 10 == 0 && LocationTracker.isSkyBlock()) scanTorrhusMenu(client);
        if (area == IslandArea.TORRHUS_CANYON) {
            tickBeeheemoth(client);
            tickCritterBehavior(client);
            if (ticks % 10 == 0) {
                scanTreeCritterTimer(client);
            }
        }
        if (area == IslandArea.CRITTER_SAFARI) {
            tickWardenReadyAlert(client);
            if (ticks % 10 == 0) {
                scanQuestItems(client);
                scanSparklingEntities(client);
            }
            if (ConfigManager.get().hunting.wumpaRoutePrediction || ticks % 10 == 0) tickWumpa(client);
            else {
                wumpaRouteStart = null;
                wumpaRouteEnd = null;
            }
            if (campfireBeaconActive() && ticks - lastCampfireScanTick >= 40) {
                scanNearestCampfire(client);
                lastCampfireScanTick = ticks;
            } else if (!campfireBeaconActive()) {
                nearestCampfire = null;
                lastCampfireScanTick = Integer.MIN_VALUE / 2;
            }
            if (ticks % 20 == 0) scanFloorDrops(client);
            if (ticks % 20 == 0) scanSnoozleWalls(client);
        }
        if (ticks % 20 == 0) scanSafariMilestoneMenu(client);
    }

    public static HuntingTextParser.ChapterSnapshot chapter() { return chapter; }
    public static HuntingTextParser.ContestSnapshot contest() { return contest; }
    public static double resource(HuntingTextParser.Resource resource) { return resources.getOrDefault(resource, -1.0); }
    public static long safariRunMillis() { return safariStartedAt == 0 ? 0 : Math.max(0, System.currentTimeMillis() - safariStartedAt); }
    public static int safariShardCount() { return safariShardCount; }
    public static Map<String, Integer> shardDrops() { return Collections.unmodifiableMap(shardDrops); }
    public static Set<String> capturedCritters() { return Collections.unmodifiableSet(capturedCritters); }
    public static Set<String> wumpaPrerequisiteCaptures() {
        return Collections.unmodifiableSet(wumpaPrerequisiteCaptures);
    }
    public static int capturedMobs() { return Math.max(capturedMobs, capturedCritters.size()); }
    public static String safariTicketTier() { return safariTicketTier; }
    public static String safariBiome() { return safariBiome; }
    public static Map<String, Integer> questItems() { return Collections.unmodifiableMap(questItems); }
    public static double nearestFloorDrop() { return nearestFloorDrop; }
    public static String behaviorName() { return behaviorName; }
    public static String behaviorStatus() { return behaviorStatus; }
    public static WumpaPhase wumpaPhase() { return wumpaPhase; }
    public static boolean wumpaSpawned() { return wumpaPhase != WumpaPhase.NONE; }
    public static Route wumpaRoute() {
        return wumpaRouteStart == null || wumpaRouteEnd == null ? null : new Route(wumpaRouteStart, wumpaRouteEnd);
    }
    public static List<WallFace> snoozleWallFaces() { return snoozleWallFaces; }
    public static int cold() { return cold; }
    public static BlockPos nearestCampfire() { return nearestCampfire; }
    public static boolean campfireBeaconActive() {
        var config = ConfigManager.get().hunting;
        return LocationTracker.area() == IslandArea.CRITTER_SAFARI && config.coldSafety
                && config.coldCampfireBeacon
                && coldCampfireEligible(cold, config.coldFirstThreshold, coldFalling);
    }

    static boolean coldCampfireEligible(int value, int firstThreshold, boolean falling) {
        return value > firstThreshold && !falling;
    }
    public static BenefactorState benefactor() { return benefactor; }
    public static HuntingTextParser.TreeCritterTimer treeCritterTimer() { return treeCritterTimer; }
    public static ModConfig.HuntingProgressMemory currentProgress(Minecraft client) {
        return ensureProfileLoaded(client);
    }

    public static void updateSafariMilestones(Minecraft client, SafariMilestoneParser.Levels observed) {
        if (observed == null || observed.empty()) return;
        ModConfig.HuntingProgressMemory memory = ensureProfileLoaded(client);
        if (memory == null) return;
        boolean changed = false;
        if (observed.cavern() > memory.safariBeltCavernLevel) {
            memory.safariBeltCavernLevel = observed.cavern(); changed = true;
        }
        if (observed.forest() > memory.safariBeltForestLevel) {
            memory.safariBeltForestLevel = observed.forest(); changed = true;
        }
        if (observed.haunted() > memory.safariBeltHauntedLevel) {
            memory.safariBeltHauntedLevel = observed.haunted(); changed = true;
        }
        if (observed.icy() > memory.safariBeltIcyLevel) {
            memory.safariBeltIcyLevel = observed.icy(); changed = true;
        }
        if (changed) ConfigManager.save();
    }
    public static BlockPos beeheemothBeacon() {
        var config = ConfigManager.get().hunting;
        return config.beeheemothHelper && config.beeheemothBeacon && !beeheemothBeaconDismissed
                ? beeheemothSpawn : null;
    }

    public static boolean isBeeheemoth(Entity entity) {
        return entity instanceof Bee bee && beeheemothScale(bee.getScale());
    }

    static boolean beeheemothScale(float scale) {
        return Math.abs(scale - BEEHEEMOTH_SCALE) < BEEHEEMOTH_SCALE_TOLERANCE;
    }

    static boolean beeheemothSoundSourceNear(double x, double y, double z) {
        if (LocationTracker.area() != IslandArea.TORRHUS_CANYON) return false;
        Minecraft client = Minecraft.getInstance();
        Vec3 source = new Vec3(x, y, z);
        if (client.level != null) {
            for (Entity entity : client.level.entitiesForRendering()) {
                if (entity.isRemoved() || !isBeeheemoth(entity)) continue;
                rememberBeeheemothSoundOrigin(entity.position());
                if (entity.position().distanceToSqr(source) <= BEEHEEMOTH_SOUND_RADIUS_SQR) return true;
            }
        }
        long now = System.currentTimeMillis();
        if (recentBeeheemothSoundOrigin == null || now > beeheemothSoundOriginExpiresAt) {
            recentBeeheemothSoundOrigin = null;
            beeheemothSoundOriginExpiresAt = 0L;
            return false;
        }
        return recentBeeheemothSoundOrigin.distanceToSqr(source) <= BEEHEEMOTH_SOUND_RADIUS_SQR;
    }

    public static boolean fairySoulFound(IslandArea area, BlockPos pos) {
        Set<String> found = ConfigManager.get().hunting.foundFairySoulsByProfile.get(profileKey());
        return found != null && found.contains(fairySoulKey(area, pos));
    }

    static String fairySoulKey(IslandArea area, BlockPos pos) {
        return area.name() + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public static void reset() {
        resources.clear();
        chapter = HuntingTextParser.ChapterSnapshot.EMPTY;
        contest = HuntingTextParser.ContestSnapshot.EMPTY;
        lastArea = IslandArea.NONE;
        resetSafariSession();
        behaviorName = "";
        behaviorStatus = "";
        recentlyCapturedBehavior = "";
        behaviorCaptureSuppressedUntil = 0;
        stillTarget = null;
        stillTicks = 0;
        previousPlayerX = Double.NaN;
        previousPlayerZ = Double.NaN;
        benefactor = BenefactorState.EMPTY;
        benefactorEndingAlerted = false;
        benefactorAuthoritativeUntil = 0;
        recentChapterChat.clear();
        lastChapterChatAt = 0;
        treeCritterTimer = null;
        resetBeeheemoth();
        clearBeeheemothSoundOrigin();
        lassoReelActive = false;
        activeProgressKey = "";
        resetColdSafety();
        treeGiftAlerts.reset();
        HuntingAlertManager.reset();
    }

    private static void alertTreeGiftLoot(String loot) {
        if (loot == null || loot.isBlank()) return;
        HuntingAlertManager.show(HuntingAlertManager.Channel.TREE_GIFT,
                "tree-gift:" + loot, "RARE TREE GIFT", loot);
    }

    private static void markNearestFairySoulFound() {
        Minecraft client = Minecraft.getInstance();
        IslandArea area = LocationTracker.area();
        if (client.player == null || (area != IslandArea.TORRHUS_CANYON
                && area != IslandArea.CRITTER_SAFARI)) return;
        Vec3 playerPosition = client.player.position();
        BlockPos nearest = null;
        double nearestDistance = 100.0;
        for (BlockPos pos : HuntingWorldRenderer.fairySouls(area)) {
            double distance = playerPosition.distanceToSqr(Vec3.atCenterOf(pos));
            if (distance < nearestDistance) {
                nearest = pos;
                nearestDistance = distance;
            }
        }
        if (nearest == null) return;
        var foundByProfile = ConfigManager.get().hunting.foundFairySoulsByProfile;
        Set<String> found = foundByProfile.computeIfAbsent(profileKey(), ignored -> new LinkedHashSet<>());
        if (found.add(fairySoulKey(area, nearest))) ConfigManager.save();
    }

    private static String profileKey() {
        String profile = LocationTracker.profileName();
        return (profile == null || profile.isBlank() ? "default" : profile.trim()).toLowerCase(Locale.ROOT);
    }

    private static void onAreaChanged(IslandArea previous, IslandArea current) {
        behaviorName = "";
        behaviorStatus = "";
        recentlyCapturedBehavior = "";
        behaviorCaptureSuppressedUntil = 0;
        stillTarget = null;
        stillTicks = 0;
        previousPlayerX = Double.NaN;
        previousPlayerZ = Double.NaN;
        lassoReelActive = false;
        if (current == IslandArea.CRITTER_SAFARI) {
            resetSafariSession();
            safariStartedAt = System.currentTimeMillis();
        } else if (previous == IslandArea.CRITTER_SAFARI) {
            resetSafariSession();
        }
        if (!isForagingChapterArea(current)) {
            contest = HuntingTextParser.ContestSnapshot.EMPTY;
            treeCritterTimer = null;
            recentChapterChat.clear();
            lastChapterChatAt = 0;
            resetBeeheemoth();
            clearBeeheemothSoundOrigin();
        }
    }

    private static void resetSafariSession() {
        safariStartedAt = 0;
        safariShardCount = 0;
        capturedMobs = 0;
        safariTicketTier = "";
        safariBiome = "";
        nearestFloorDrop = -1;
        shardDrops.clear();
        capturedCritters.clear();
        wumpaPrerequisiteCaptures.clear();
        questItems.clear();
        knownFloorDrops.clear();
        knownSparklingEntities.clear();
        readyWardens.clear();
        wumpaPhase = WumpaPhase.NONE;
        trackedWumpa = null;
        previousWumpaPosition = null;
        wumpaRouteStart = null;
        wumpaRouteEnd = null;
        wumpaSpawnAlerted = false;
        wumpaMovingTicks = 0;
        wumpaStillTicks = 0;
        snoozleWallFaces = List.of();
        resetColdSafety();
        doomspiralReadyAlerted = false;
    }

    private static void updateSafariText(List<String> lines) {
        for (String raw : lines) {
            String line = HuntingTextParser.plain(raw);
            Matcher captured = CAPTURED_MOBS.matcher(line);
            if (captured.find()) capturedMobs = (int) Math.max(0, HuntingTextParser.whole(captured.group(1)));
            String ticket = ticketIn(line);
            if (!ticket.isBlank()) safariTicketTier = ticket;
            for (String biome : HuntingTextParser.SAFARI_CRITTERS.keySet()) {
                if (line.equalsIgnoreCase(biome) || line.equalsIgnoreCase(biome + " Biome")) safariBiome = biome;
            }
        }
    }

    private static void applyResources(List<HuntingTextParser.ResourceUpdate> updates, boolean allowAdditive) {
        ModConfig.HuntingProgressMemory memory = ensureProfileLoaded(Minecraft.getInstance());
        boolean changed = false;
        for (HuntingTextParser.ResourceUpdate update : updates) {
            if (update.additive() && !allowAdditive) continue;
            double value = CompactNumbers.parse(update.amount());
            if (!Double.isFinite(value) || value < 0.0) continue;
            double previous = resources.getOrDefault(update.resource(), 0.0);
            double next = update.additive() ? previous + value : value;
            if (Double.compare(resources.getOrDefault(update.resource(), -1.0), next) == 0) continue;
            resources.put(update.resource(), next);
            if (memory != null) memory.resources.put(update.resource().name(), next);
            changed = true;
        }
        if (changed && memory != null) ConfigManager.save();
    }

    private static ModConfig.HuntingProgressMemory ensureProfileLoaded(Minecraft client) {
        if (client == null || client.player == null || !LocationTracker.isSkyBlock()) return null;
        String key = ProfileContext.key(client).toLowerCase(Locale.ROOT);
        ModConfig.Hunting config = ConfigManager.get().hunting;
        ModConfig.HuntingProgressMemory memory = config.rememberedProgressByProfile.computeIfAbsent(
                key, ignored -> new ModConfig.HuntingProgressMemory());
        if (key.equals(activeProgressKey)) return memory;

        boolean migrated = false;
        if (config.legacySafariBeltMigrationPending) {
            memory.safariBeltCavernLevel = Math.max(memory.safariBeltCavernLevel,
                    config.safariBeltCavernLevel);
            memory.safariBeltForestLevel = Math.max(memory.safariBeltForestLevel,
                    config.safariBeltForestLevel);
            memory.safariBeltHauntedLevel = Math.max(memory.safariBeltHauntedLevel,
                    config.safariBeltHauntedLevel);
            memory.safariBeltIcyLevel = Math.max(memory.safariBeltIcyLevel,
                    config.safariBeltIcyLevel);
            config.legacySafariBeltMigrationPending = false;
            migrated = true;
        }

        activeProgressKey = key;
        resources.clear();
        for (HuntingTextParser.Resource resource : HuntingTextParser.Resource.values()) {
            Double value = memory.resources.get(resource.name());
            if (value != null && Double.isFinite(value) && value >= 0.0) resources.put(resource, value);
        }
        HuntingTextParser.ChapterSnapshot rememberedChapter = new HuntingTextParser.ChapterSnapshot(
                memory.chapter, memory.currentTask,
                memory.taskProgress, memory.completedTasks, memory.chapterTotalProgress, memory.nextUnlock);
        chapter = sanitizeRememberedChapter(rememberedChapter);
        if (!chapter.equals(rememberedChapter)) {
            memory.currentTask = chapter.task();
            memory.taskProgress = chapter.progress();
            migrated = true;
        }
        long now = System.currentTimeMillis();
        boolean benefactorActive = memory.benefactorExpiresAt > now;
        if (benefactorActive || !memory.benefactorTemple.isBlank() || !memory.benefactorEffect.isBlank()
                || !memory.benefactorDonation.isBlank()) {
            benefactor = new BenefactorState(benefactorActive,
                    benefactorActive ? memory.benefactorExpiresAt : 0,
                    memory.benefactorTemple, memory.benefactorEffect, memory.benefactorDonation);
        } else {
            benefactor = BenefactorState.EMPTY;
        }
        benefactorEndingAlerted = false;
        benefactorAuthoritativeUntil = 0;
        recentChapterChat.clear();
        lastChapterChatAt = 0;
        contest = HuntingTextParser.ContestSnapshot.EMPTY;
        if (migrated) ConfigManager.save();
        return memory;
    }

    private static void rememberChapter(HuntingTextParser.ChapterSnapshot update) {
        HuntingTextParser.ChapterSnapshot merged = mergeChapter(chapter, update);
        if (merged.equals(chapter)) return;
        chapter = merged;
        ModConfig.HuntingProgressMemory memory = ensureProfileLoaded(Minecraft.getInstance());
        if (memory == null) return;
        memory.chapter = merged.chapter();
        memory.currentTask = merged.task();
        memory.taskProgress = merged.progress();
        memory.completedTasks = merged.completed();
        memory.chapterTotalProgress = merged.totalProgress();
        memory.nextUnlock = merged.nextUnlock();
        ConfigManager.save();
    }

    static HuntingTextParser.ChapterSnapshot mergeChapter(HuntingTextParser.ChapterSnapshot remembered,
                                                            HuntingTextParser.ChapterSnapshot received) {
        remembered = sanitizeRememberedChapter(remembered);
        boolean changedChapter = received.chapter() != null && !received.chapter().isBlank()
                && !received.chapter().equals(remembered.chapter());
        HuntingTextParser.ChapterSnapshot base = changedChapter
                ? HuntingTextParser.ChapterSnapshot.EMPTY : remembered;
        return new HuntingTextParser.ChapterSnapshot(
                latest(received.chapter(), base.chapter()), latest(received.task(), base.task()),
                latest(received.progress(), base.progress()), latest(received.completed(), base.completed()),
                latest(received.totalProgress(), base.totalProgress()),
                latest(received.nextUnlock(), base.nextUnlock()));
    }

    private static HuntingTextParser.ChapterSnapshot sanitizeRememberedChapter(
            HuntingTextParser.ChapterSnapshot remembered) {
        if (remembered == null) return HuntingTextParser.ChapterSnapshot.EMPTY;
        if (remembered.task().isBlank() || HuntingTextParser.plausibleChapterTask(remembered.task())) {
            return remembered;
        }
        return new HuntingTextParser.ChapterSnapshot(remembered.chapter(), "", "",
                remembered.completed(), remembered.totalProgress(), remembered.nextUnlock());
    }

    private static void rememberReceivedChapter(List<String> lines) {
        HuntingTextParser.ChapterSnapshot parsed = HuntingTextParser.chapter(lines);
        if (!parsed.empty()) rememberChapter(parsed);
    }

    /** Keeps only one short received-chat block so split Helia status lines can be parsed together. */
    private static void rememberChapterMessage(String line) {
        long now = System.currentTimeMillis();
        String lower = line.toLowerCase(Locale.ROOT);
        boolean header = lower.contains("chapter")
                && (lower.contains("helia") || lower.contains("torrhus")
                || lower.matches("^chapter\\s*:?[ \\t]*(?:[ivxlcdm]+|[0-9]+).*$"));
        if (header || now - lastChapterChatAt > CHAPTER_CHAT_WINDOW_MS) recentChapterChat.clear();
        if (!header && recentChapterChat.isEmpty()) return;
        recentChapterChat.add(line);
        while (recentChapterChat.size() > CHAPTER_CHAT_MAX_LINES) recentChapterChat.removeFirst();
        lastChapterChatAt = now;
        rememberReceivedChapter(recentChapterChat);
    }

    private static String latest(String received, String remembered) {
        return received == null || received.isBlank() ? remembered : received;
    }

    private static void recordCapture(HuntingTextParser.Capture capture) {
        if (personalSafariCapture(capture)) {
            capturedCritters.add(capture.critter());
            capturedMobs++;
        }
        if (wumpaPrerequisiteCapture(capture)) {
            wumpaPrerequisiteCaptures.add(capture.critter());
            if (wumpaPrerequisitesComplete(wumpaPrerequisiteCaptures)) markWumpaSpawned(
                    WumpaPhase.AVAILABLE, ModText.get("alert.wumpa_spawned.requirements"));
        }
        safariShardCount += Math.max(1, capture.amount());
        shardDrops.merge(capture.shard(), Math.max(1, capture.amount()), Integer::sum);
        if (capture.critter().equals("Wumpa")) {
            wumpaPhase = WumpaPhase.CAUGHT;
            if (ConfigManager.get().hunting.wumpaHud && ConfigManager.get().hunting.wumpaAlerts) {
                HuntingAlertManager.show(HuntingAlertManager.Channel.WUMPA,
                        "wumpa-caught", "WUMPA CAUGHT", "Encounter complete");
            }
        }
    }

    static boolean personalSafariCapture(HuntingTextParser.Capture capture) {
        return capture != null && !capture.lootShare();
    }

    static boolean wumpaPrerequisiteCapture(HuntingTextParser.Capture capture) {
        return capture != null && HuntingTextParser.WUMPA_PREREQUISITES.contains(capture.critter());
    }

    private static void tickCritterBehavior(Minecraft client) {
        var config = ConfigManager.get().hunting;
        if (!config.critterBehavior || client.player == null || client.level == null) {
            clearStillBehavior(client);
            return;
        }
        Entity target = closestBehaviorCritter(client);
        if (target == null) {
            clearStillBehavior(client);
            return;
        }
        String name = critterName(target);
        if (System.currentTimeMillis() < behaviorCaptureSuppressedUntil
                && name.equals(recentlyCapturedBehavior)) {
            clearStillBehavior(client);
            return;
        }
        if (System.currentTimeMillis() >= behaviorCaptureSuppressedUntil) recentlyCapturedBehavior = "";
        if (name.equals("Dustybit") || name.equals("Hideonsun")) {
            boolean enabled = name.equals("Dustybit") ? config.dustybitAssistant : config.hideonsunAssistant;
            if (!enabled) {
                clearStillBehavior(client);
                return;
            }
            behaviorName = name;
            String receivedName = target.getCustomName() == null ? "" : target.getCustomName().getString();
            HuntingTextParser.CritterProgress progress = HuntingTextParser.critterProgress(receivedName);
            behaviorStatus = progress == null
                    ? name.equals("Dustybit") ? "Follow 4 jumps" : "Return projectile 3 times"
                    : progress.current() + "/" + progress.target();
            if (!target.getUUID().equals(stillTarget)) {
                stillTarget = target.getUUID();
                HuntingAlertManager.show(HuntingAlertManager.Channel.CRITTER_BEHAVIOR, "behavior:" + stillTarget,
                        name.equals("Dustybit") ? "FOLLOW THE JUMPS" : "RETURN PROJECTILE",
                        name.equals("Dustybit") ? "4 jumps until exhausted" : "Hit it back 3 times",
                        30_000L);
            }
            return;
        }
        if (!(name.equals("Blue Jay") && config.blueJayAssistant)
                && !(name.equals("Goldolot") && config.goldolotAssistant)) {
            clearStillBehavior(client);
            return;
        }
        if (!holdingCaptureTool(client)) {
            clearStillBehavior(client);
            return;
        }

        int requiredTicks = name.equals("Blue Jay") ? 160 : 100;
        boolean sameTarget = target.getUUID().equals(stillTarget);
        boolean stationary = playerStationary(client);
        if (!sameTarget) {
            stillTarget = target.getUUID();
            stillTicks = 0;
            stillReadyAlerted = false;
            HuntingAlertManager.show(HuntingAlertManager.Channel.CRITTER_BEHAVIOR,
                    "stand-still:" + stillTarget, "STAND STILL", name, 10_000L);
        }
        if (stationary) stillTicks = Math.min(requiredTicks, stillTicks + 1);
        else {
            stillTicks = 0;
            stillReadyAlerted = false;
        }
        behaviorName = name;
        int remaining = Math.max(0, requiredTicks - stillTicks);
        behaviorStatus = remaining == 0 ? "READY" : String.format(Locale.ROOT, "Stand still %.1fs", remaining / 20.0);
        if (remaining == 0 && !stillReadyAlerted) {
            stillReadyAlerted = true;
            HuntingAlertManager.show(HuntingAlertManager.Channel.CRITTER_BEHAVIOR,
                    "stand-ready:" + stillTarget, "CRITTER READY", name, 10_000L);
        }
    }

    private static void tickBeeheemoth(Minecraft client) {
        var config = ConfigManager.get().hunting;
        if (!config.beeheemothHelper || client.player == null || client.level == null) {
            resetBeeheemoth();
            return;
        }
        Bee found = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof Bee bee) || entity.isRemoved() || !isBeeheemoth(bee)) continue;
            if (entity.getUUID().equals(trackedBeeheemoth)) {
                found = bee;
                break;
            }
            double distance = entity.distanceToSqr(client.player);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                found = bee;
            }
        }
        if (found == null) {
            resetBeeheemoth();
            return;
        }
        if (!found.getUUID().equals(trackedBeeheemoth)) {
            trackedBeeheemoth = found.getUUID();
            beeheemothSpawn = found.blockPosition().immutable();
            beeheemothBeaconDismissed = false;
        }
        rememberBeeheemothSoundOrigin(found.position());
        if (!beeheemothBeaconDismissed && beeheemothSpawn != null
                && client.player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(beeheemothSpawn))
                <= BEEHEEMOTH_BEACON_DISMISS_DISTANCE_SQR) {
            beeheemothBeaconDismissed = true;
            beeheemothSpawn = null;
        }
    }

    private static void resetBeeheemoth() {
        trackedBeeheemoth = null;
        beeheemothSpawn = null;
        beeheemothBeaconDismissed = false;
    }

    private static void rememberBeeheemothSoundOrigin(Vec3 position) {
        recentBeeheemothSoundOrigin = position;
        beeheemothSoundOriginExpiresAt = System.currentTimeMillis() + BEEHEEMOTH_SOUND_GRACE_MS;
    }

    private static void clearBeeheemothSoundOrigin() {
        recentBeeheemothSoundOrigin = null;
        beeheemothSoundOriginExpiresAt = 0L;
    }

    /** Uses the same local leash/nearby-ArmorStand relation as SkyHanni's Lasso Display. */
    private static void tickLassoReelSound(Minecraft client) {
        var config = ConfigManager.get().hunting;
        if (!config.lassoReelAudio.sound || client.player == null || client.level == null
                || !holdingLasso(client)) {
            lassoReelActive = false;
            return;
        }
        List<Vec3> localLassoLabels = new ArrayList<>();
        List<ArmorStand> armorStands = new ArrayList<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity.isRemoved()) continue;
            if (entity instanceof Leashable leashable && leashable.getLeashHolder() == client.player) {
                localLassoLabels.add(entity.position().add(0.0, 2.0, 0.0));
            }
            if (entity instanceof ArmorStand armorStand) armorStands.add(armorStand);
        }
        boolean reel = false;
        if (!localLassoLabels.isEmpty() && !armorStands.isEmpty()) {
            for (ArmorStand armorStand : armorStands) {
                if (!HuntingTextParser.lassoReelLabel(armorStand.getDisplayName().getString())) continue;
                Vec3 labelPosition = armorStand.position();
                for (Vec3 position : localLassoLabels) {
                    if (position.distanceToSqr(labelPosition) > 4.0) continue;
                    reel = true;
                    break;
                }
                if (reel) break;
            }
        }
        if (reel && !lassoReelActive) {
            HuntingAlertManager.playSound(HuntingAlertManager.Channel.LASSO_REEL, 1.35f);
        }
        lassoReelActive = reel;
    }

    private static void tickWardenReadyAlert(Minecraft client) {
        var config = ConfigManager.get().hunting;
        if (!config.wardenReadyAlert || client.player == null || client.level == null) {
            readyWardens.clear();
            return;
        }

        int latency = 0;
        var connection = client.getConnection();
        if (connection != null) {
            var playerInfo = connection.getPlayerInfo(client.player.getUUID());
            if (playerInfo != null) latency = Math.max(0, playerInfo.getLatency());
        }

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof Warden warden) || warden.isRemoved()
                    || !WardenCooldownSupport.inArena(warden.blockPosition())) continue;
            UUID id = warden.getUUID();
            boolean ready = WardenCooldownSupport.captureReady(warden.tickCount, latency, warden.getPose());
            if (ready && readyWardens.add(id)) {
                HuntingAlertManager.show(HuntingAlertManager.Channel.WARDEN_READY,
                        "warden-ready:" + id,
                        ModText.get("alert.warden_ready.title"),
                        ModText.get("alert.warden_ready.subtitle"), 10_000L);
            } else if (!ready) {
                readyWardens.remove(id);
            }
        }
    }

    private static boolean holdingCaptureTool(Minecraft client) {
        ItemStack held = client.player.getMainHandItem();
        String name = HuntingTextParser.plain(held.getHoverName().getString()).toLowerCase(Locale.ROOT);
        return name.contains("lasso") || name.contains("fishing net");
    }

    private static boolean holdingLasso(Minecraft client) {
        String name = HuntingTextParser.plain(client.player.getMainHandItem().getHoverName().getString())
                .toLowerCase(Locale.ROOT);
        return name.contains("lasso");
    }

    private static Entity closestBehaviorCritter(Minecraft client) {
        Entity result = null;
        double nearest = 16.0 * 16.0;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity.isRemoved()) continue;
            String name = critterName(entity);
            if (!BEHAVIOR_CRITTERS.contains(name)) continue;
            double distance = entity.distanceToSqr(client.player);
            if (distance < nearest) {
                nearest = distance;
                result = entity;
            }
        }
        return result;
    }

    private static String critterName(Entity entity) {
        String visible = entity.hasCustomName() && entity.getCustomName() != null
                ? HuntingTextParser.plain(entity.getCustomName().getString()) : "";
        for (String name : BEHAVIOR_CRITTERS) if (visible.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT))) return name;
        return "";
    }

    private static void suppressCapturedBehavior(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        String captured = BEHAVIOR_CRITTERS.stream()
                .filter(name -> lower.contains(name.toLowerCase(Locale.ROOT)))
                .findFirst().orElse(behaviorName);
        if (captured.isBlank()) return;
        recentlyCapturedBehavior = captured;
        behaviorCaptureSuppressedUntil = System.currentTimeMillis() + BEHAVIOR_CAPTURE_SUPPRESS_MS;
        clearStillBehavior(Minecraft.getInstance());
    }

    private static boolean playerStationary(Minecraft client) {
        double x = client.player.getX();
        double z = client.player.getZ();
        boolean result = Double.isFinite(previousPlayerX)
                && Math.abs(x - previousPlayerX) < 0.002 && Math.abs(z - previousPlayerZ) < 0.002;
        previousPlayerX = x;
        previousPlayerZ = z;
        return result;
    }

    private static void clearStillBehavior(Minecraft client) {
        behaviorName = "";
        behaviorStatus = "";
        stillTarget = null;
        stillTicks = 0;
        stillReadyAlerted = false;
        if (client.player != null) {
            previousPlayerX = client.player.getX();
            previousPlayerZ = client.player.getZ();
        }
    }

    private static void scanQuestItems(Minecraft client) {
        if (client.player == null) return;
        questItems.clear();
        for (ItemStack stack : client.player.getInventory().getNonEquipmentItems()) {
            if (stack.isEmpty()) continue;
            String name = HuntingTextParser.plain(stack.getHoverName().getString());
            if (HuntingTextParser.QUEST_ITEMS.contains(name)) questItems.merge(name, stack.getCount(), Integer::sum);
        }
        updateDoomspiralReady();
    }

    private static void updateDoomspiralReady() {
        int incense = questItems.getOrDefault("Soothing Incense", 0);
        if (incense < 4) {
            doomspiralReadyAlerted = false;
            return;
        }
        if (!doomspiralReadyAlerted && ConfigManager.get().hunting.doomspiralReadyAlert) {
            doomspiralReadyAlerted = true;
            HuntingAlertManager.show(HuntingAlertManager.Channel.DOOMSPIRAL,
                    "doomspiral-ready", "DOOMSPIRAL READY",
                    incense + " Soothing Incense");
        }
    }

    private static void updateCold(int value) {
        if (value < 0) return;
        var config = ConfigManager.get().hunting;
        if (cold >= 0) {
            if (value < cold) coldFalling = true;
            else if (value > cold) coldFalling = false;
        }
        cold = value;
        if (value <= config.coldFirstThreshold) firstColdAlerted = false;
        if (value <= config.coldSecondThreshold) secondColdAlerted = false;
        if (!config.coldSafety) return;

        if (config.coldCampfireBeacon && coldCampfireEligible(value, config.coldFirstThreshold, coldFalling)
                && ticks - lastCampfireScanTick >= 40) {
            scanNearestCampfire(Minecraft.getInstance());
            lastCampfireScanTick = ticks;
        }

        if (value > config.coldSecondThreshold && !secondColdAlerted) {
            firstColdAlerted = true;
            secondColdAlerted = true;
            HuntingAlertManager.show(HuntingAlertManager.Channel.COLD,
                    "cold-second", "COLD CRITICAL", "Cold: " + value);
        } else if (value > config.coldFirstThreshold && !firstColdAlerted) {
            firstColdAlerted = true;
            HuntingAlertManager.show(HuntingAlertManager.Channel.COLD,
                    "cold-first", "COLD WARNING", "Cold: " + value);
        }
    }

    private static void scanNearestCampfire(Minecraft client) {
        if (client.player == null || client.level == null) {
            nearestCampfire = null;
            return;
        }
        BlockPos origin = client.player.blockPosition();
        int centerChunkX = origin.getX() >> 4;
        int centerChunkZ = origin.getZ() >> 4;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int chunkX = centerChunkX - CAMPFIRE_CHUNK_RADIUS;
             chunkX <= centerChunkX + CAMPFIRE_CHUNK_RADIUS; chunkX++) {
            for (int chunkZ = centerChunkZ - CAMPFIRE_CHUNK_RADIUS;
                 chunkZ <= centerChunkZ + CAMPFIRE_CHUNK_RADIUS; chunkZ++) {
                if (!client.level.hasChunk(chunkX, chunkZ)) continue;
                LevelChunk chunk = client.level.getChunk(chunkX, chunkZ);
                for (BlockPos pos : chunk.getBlockEntitiesPos()) {
                    var state = client.level.getBlockState(pos);
                    if (!state.is(Blocks.CAMPFIRE) && !state.is(Blocks.SOUL_CAMPFIRE)) continue;
                    double distance = client.player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos));
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos.immutable();
                    }
                }
            }
        }
        nearestCampfire = best;
    }

    private static void resetColdSafety() {
        cold = -1;
        coldFalling = false;
        firstColdAlerted = false;
        secondColdAlerted = false;
        nearestCampfire = null;
        lastCampfireScanTick = Integer.MIN_VALUE / 2;
    }

    private static void scanFloorDrops(Minecraft client) {
        var config = ConfigManager.get().hunting;
        if (!config.floorDropAssistant || client.player == null || client.level == null) {
            nearestFloorDrop = -1;
            return;
        }
        BlockPos origin = client.player.blockPosition();
        double nearest = Double.MAX_VALUE;
        for (int dx = -FLOOR_SCAN_RADIUS; dx <= FLOOR_SCAN_RADIUS; dx++) {
            for (int dy = -FLOOR_SCAN_VERTICAL; dy <= FLOOR_SCAN_VERTICAL; dy++) {
                for (int dz = -FLOOR_SCAN_RADIUS; dz <= FLOOR_SCAN_RADIUS; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (!client.level.getBlockState(pos).is(Blocks.TRIPWIRE)) continue;
                    double distance = Math.sqrt(client.player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)));
                    nearest = Math.min(nearest, distance);
                    if (knownFloorDrops.add(pos.asLong()) && config.floorDropAlert) {
                        HuntingAlertManager.show(HuntingAlertManager.Channel.FLOOR_DROP,
                                "floor-drop:" + pos.asLong(), "FLOOR DROP", String.format(Locale.ROOT,
                                        "%.1fm away", distance), 60_000L);
                    }
                }
            }
        }
        nearestFloorDrop = nearest == Double.MAX_VALUE ? -1 : nearest;
        if (knownFloorDrops.size() > 512) knownFloorDrops.clear();
    }

    private static void scanSparklingEntities(Minecraft client) {
        var config = ConfigManager.get().hunting;
        if (!config.sparklingAlert || client.level == null) return;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!entity.hasCustomName() || entity.getCustomName() == null) continue;
            String name = HuntingTextParser.plain(entity.getCustomName().getString());
            if (!name.toUpperCase(Locale.ROOT).contains("SPARKLING")) continue;
            if (knownSparklingEntities.add(entity.getUUID())) {
                String subtitle = config.sparklingShowBiome && !safariBiome.isBlank() ? safariBiome : name;
                HuntingAlertManager.show(HuntingAlertManager.Channel.SPARKLING,
                        "sparkling-entity:" + entity.getUUID(), "SPARKLING CRITTER", subtitle, 60_000L);
            }
        }
        if (knownSparklingEntities.size() > 128) knownSparklingEntities.clear();
    }

    private static void updateWumpaFromMessage(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        if (lower.contains("massive footsteps") || lower.equals("the wumpa has awoken.")) {
            markWumpaSpawned(lower.contains("awoken") ? WumpaPhase.AWOKEN : WumpaPhase.AVAILABLE,
                    lower.contains("awoken") ? "Awoken" : "Available");
        }
    }

    static boolean wumpaPrerequisitesComplete(Set<String> captured) {
        return captured != null && captured.containsAll(HuntingTextParser.WUMPA_PREREQUISITES);
    }

    private static void markWumpaSpawned(WumpaPhase phase, String subtitle) {
        if (wumpaPhase != WumpaPhase.CAUGHT && wumpaPhase != WumpaPhase.FAILED) wumpaPhase = phase;
        var config = ConfigManager.get().hunting;
        if (wumpaSpawnAlerted || !config.wumpaHud || !config.wumpaAlerts) return;
        wumpaSpawnAlerted = true;
        HuntingAlertManager.show(HuntingAlertManager.Channel.WUMPA,
                "wumpa-spawn", ModText.get("alert.wumpa_spawned.title"), subtitle, 30_000L);
    }

    private static void tickWumpa(Minecraft client) {
        var config = ConfigManager.get().hunting;
        if (!config.wumpaHud || client.player == null || client.level == null) {
            clearWumpaRoute();
            return;
        }
        if (client.player.isDeadOrDying() && wumpaPhase.active()) {
            wumpaPhase = WumpaPhase.FAILED;
            clearWumpaRoute();
            if (config.wumpaFailureWarning) HuntingAlertManager.show(HuntingAlertManager.Channel.WUMPA,
                    "wumpa-failed", "WUMPA FAILED", "No retry this run");
            return;
        }
        Entity namedWumpa = null;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!entity.hasCustomName() || entity.getCustomName() == null) continue;
            if (HuntingTextParser.plain(entity.getCustomName().getString()).toLowerCase(Locale.ROOT).contains("wumpa")) {
                namedWumpa = entity;
                break;
            }
        }
        if (namedWumpa != null && wumpaPhase == WumpaPhase.NONE) {
            markWumpaSpawned(WumpaPhase.AVAILABLE, "Visible in the Icy cavern");
        }
        Entity wumpa = resolveWumpaBody(client, namedWumpa);
        if (wumpa == null || wumpaPhase == WumpaPhase.CAUGHT || wumpaPhase == WumpaPhase.FAILED) {
            clearWumpaRoute();
            return;
        }
        Vec3 position = wumpa.position();
        Vec3 movement = wumpa.getDeltaMovement();
        if (wumpa.getUUID().equals(trackedWumpa) && previousWumpaPosition != null) {
            Vec3 observedMovement = position.subtract(previousWumpaPosition);
            if (observedMovement.lengthSqr() > movement.lengthSqr()) movement = observedMovement;
        }
        trackedWumpa = wumpa.getUUID();
        previousWumpaPosition = position;
        movement = new Vec3(movement.x, 0.0, movement.z);
        if (movement.lengthSqr() > WUMPA_CHASE_SPEED_SQR) {
            wumpaMovingTicks++;
            wumpaStillTicks = 0;
        } else {
            wumpaStillTicks++;
            wumpaMovingTicks = 0;
        }
        WumpaPhase next = wumpaMovingTicks >= 2 ? WumpaPhase.CHASING
                : wumpaPhase == WumpaPhase.CHASING && wumpaStillTicks >= 5 ? WumpaPhase.STUNNED
                : wumpaPhase == WumpaPhase.AVAILABLE ? WumpaPhase.SLEEPING : wumpaPhase;
        if (next != wumpaPhase) {
            wumpaPhase = next;
            if (config.wumpaAlerts && (next == WumpaPhase.CHASING || next == WumpaPhase.STUNNED)) {
                HuntingAlertManager.show(HuntingAlertManager.Channel.WUMPA,
                        "wumpa-phase:" + next, "WUMPA", next.display, 4_000L);
            }
        }
        updateWumpaRoute(client, wumpa, movement, next);
    }

    private static Entity resolveWumpaBody(Minecraft client, Entity namedWumpa) {
        Entity nearestRavager = null;
        Vec3 anchor = namedWumpa == null ? client.player.position() : namedWumpa.position();
        double nearestDistance = namedWumpa == null ? 48.0 * 48.0 : 12.0 * 12.0;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof Ravager) || entity.isRemoved()) continue;
            double distance = entity.position().distanceToSqr(anchor);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestRavager = entity;
            }
        }
        if (nearestRavager != null) return nearestRavager;
        return namedWumpa instanceof ArmorStand ? null : namedWumpa;
    }

    private static void updateWumpaRoute(Minecraft client, Entity wumpa, Vec3 movement, WumpaPhase phase) {
        if (!ConfigManager.get().hunting.wumpaRoutePrediction || phase != WumpaPhase.CHASING
                || movement.lengthSqr() < 1.0E-5) {
            wumpaRouteStart = null;
            wumpaRouteEnd = null;
            return;
        }
        Vec3 start = wumpa.position().add(0, wumpa.getBbHeight() * 0.5, 0);
        Vec3 projected = start.add(movement.normalize().scale(WUMPA_ROUTE_LENGTH));
        BlockHitResult hit = client.level.clip(new ClipContext(start, projected,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, wumpa));
        wumpaRouteStart = start;
        wumpaRouteEnd = hit.getType() == HitResult.Type.MISS ? projected : hit.getLocation();
    }

    private static void clearWumpaRoute() {
        trackedWumpa = null;
        previousWumpaPosition = null;
        wumpaRouteStart = null;
        wumpaRouteEnd = null;
        wumpaMovingTicks = 0;
        wumpaStillTicks = 0;
    }

    private static void scanSnoozleWalls(Minecraft client) {
        if (!ConfigManager.get().hunting.snoozleWallOverlay || client.player == null || client.level == null) {
            snoozleWallFaces = List.of();
            return;
        }
        BlockPos origin = client.player.blockPosition();
        Set<BlockPos> remaining = new HashSet<>();
        for (int dx = -SNOOZLE_WALL_SCAN_RADIUS; dx <= SNOOZLE_WALL_SCAN_RADIUS; dx++) {
            for (int dy = -SNOOZLE_WALL_SCAN_VERTICAL; dy <= SNOOZLE_WALL_SCAN_VERTICAL; dy++) {
                for (int dz = -SNOOZLE_WALL_SCAN_RADIUS; dz <= SNOOZLE_WALL_SCAN_RADIUS; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (client.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                            && snoozleWallMaterial(client.level.getBlockState(pos))) {
                        remaining.add(pos.immutable());
                    }
                }
            }
        }

        List<WallFace> found = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        while (!remaining.isEmpty()) {
            BlockPos seed = remaining.iterator().next();
            remaining.remove(seed);
            queue.add(seed);
            List<BlockPos> component = new ArrayList<>();
            boolean cobbledDeepslate = false;
            boolean tuff = false;
            while (!queue.isEmpty()) {
                BlockPos pos = queue.removeFirst();
                component.add(pos);
                cobbledDeepslate |= client.level.getBlockState(pos).is(Blocks.COBBLED_DEEPSLATE);
                tuff |= client.level.getBlockState(pos).is(Blocks.TUFF);
                for (Direction direction : Direction.values()) {
                    BlockPos neighbour = pos.relative(direction);
                    if (remaining.remove(neighbour)) queue.addLast(neighbour);
                }
            }
            if (!snoozleWallComponent(cobbledDeepslate, tuff, component.size())) continue;
            for (BlockPos pos : component) {
                for (Direction direction : Direction.values()) {
                    if (client.level.getBlockState(pos.relative(direction)).isAir()) {
                        found.add(new WallFace(pos, direction));
                    }
                }
            }
        }
        snoozleWallFaces = List.copyOf(found);
    }

    private static boolean snoozleWallMaterial(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(Blocks.COBBLED_DEEPSLATE) || state.is(Blocks.TUFF);
    }

    static boolean snoozleWallComponent(boolean cobbledDeepslate, boolean tuff, int blockCount) {
        return cobbledDeepslate && tuff && blockCount >= SNOOZLE_WALL_MIN_BLOCKS
                && blockCount <= SNOOZLE_WALL_MAX_BLOCKS;
    }

    private static void applyBenefactor(HuntingTextParser.BenefactorUpdate update) {
        if (update == null || !update.observed()) return;
        BenefactorState previous = benefactor;
        long now = System.currentTimeMillis();
        if (!update.active() && previous.active() && now < benefactorAuthoritativeUntil) return;
        if (!update.active() && previous.active() && !update.temple().isBlank()
                && !previous.temple().isBlank() && !update.temple().equals(previous.temple())) {
            return;
        }

        long expiresAt = previous.expiresAt();
        if (update.remainingSeconds() > 0) {
            boolean sameTemple = update.temple().isBlank() || previous.temple().isBlank()
                    || update.temple().equals(previous.temple());
            long candidate = update.additiveDuration()
                    ? (sameTemple ? Math.max(now, previous.expiresAt()) : now)
                    + update.remainingSeconds() * 1_000L
                    : now + update.remainingSeconds() * 1_000L;
            expiresAt = Math.abs(candidate - previous.expiresAt()) <= 2_000L
                    ? previous.expiresAt() : candidate;
        } else if (!update.active()) {
            expiresAt = 0;
        }
        String temple = latest(update.temple(), previous.temple());
        String effect = latest(update.effect(), previous.effect());
        String donation = latest(update.donation(), previous.donation());
        BenefactorState next = new BenefactorState(update.active(), expiresAt, temple, effect, donation);
        if (next.equals(previous)) return;
        benefactor = next;
        if (update.additiveDuration() && next.active()) benefactorAuthoritativeUntil = now + 5_000L;
        rememberBenefactor();

        boolean activated = !previous.active() && next.active();
        boolean ended = previous.active() && !next.active();
        if (activated || ended) benefactorEndingAlerted = false;
        if (!ConfigManager.get().hunting.benefactorHud) return;
        if (activated) HuntingAlertManager.show(HuntingAlertManager.Channel.BENEFACTOR,
                "benefactor-active", "BENEFACTOR ACTIVE",
                update.remainingSeconds() > 0 ? durationText(update.remainingSeconds()) : "Buff enabled");
        if (ended) HuntingAlertManager.show(HuntingAlertManager.Channel.BENEFACTOR,
                "benefactor-ended", "BENEFACTOR ENDED", "Buff expired");
    }

    private static void rememberBenefactor() {
        ModConfig.HuntingProgressMemory memory = ensureProfileLoaded(Minecraft.getInstance());
        if (memory == null) return;
        memory.benefactorExpiresAt = benefactor.expiresAt();
        memory.benefactorTemple = benefactor.temple();
        memory.benefactorEffect = benefactor.effect();
        memory.benefactorDonation = benefactor.donation();
        ConfigManager.save();
    }

    private static void tickBenefactor() {
        if (!benefactor.active() || benefactor.expiresAt() <= 0) return;
        long remaining = benefactor.expiresAt() - System.currentTimeMillis();
        if (remaining <= 0) {
            benefactor = new BenefactorState(false, 0, benefactor.temple(),
                    benefactor.effect(), benefactor.donation());
            rememberBenefactor();
            if (ConfigManager.get().hunting.benefactorHud) {
                HuntingAlertManager.show(HuntingAlertManager.Channel.BENEFACTOR,
                        "benefactor-expired", "BENEFACTOR ENDED", "Buff expired");
            }
        } else if (ConfigManager.get().hunting.benefactorHud
                && remaining <= BENEFATOR_WARNING_MS && !benefactorEndingAlerted) {
            benefactorEndingAlerted = true;
            HuntingAlertManager.show(HuntingAlertManager.Channel.BENEFACTOR,
                    "benefactor-ending", "BENEFACTOR", "30 seconds remaining");
        }
    }

    private static void scanSafariMilestoneMenu(Minecraft client) {
        var screen = MinecraftClientCompat.screen(client);
        if (client.player == null || screen == null) return;
        String title = HuntingTextParser.plain(screen.getTitle().getString()).toLowerCase(Locale.ROOT);
        if (!title.contains("safari") || !title.contains("milestone")) return;
        List<String> receivedItems = new ArrayList<>();
        for (var slot : client.player.containerMenu.slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            receivedItems.add(stackText(stack));
        }
        updateSafariMilestones(client, SafariMilestoneParser.parse(receivedItems));
    }

    /** Reads only text already present in the currently open Torrhus/HOTF menu. */
    private static void scanTorrhusMenu(Minecraft client) {
        var screen = MinecraftClientCompat.screen(client);
        if (client.player == null || screen == null) return;
        String title = HuntingTextParser.plain(screen.getTitle().getString());
        String lowerTitle = title.toLowerCase(Locale.ROOT);
        List<String> menuLines = new ArrayList<>();
        menuLines.add(title);
        List<String> itemTexts = new ArrayList<>();
        List<String> resourceLines = new ArrayList<>();
        boolean chapterMenu = lowerTitle.contains("chapter")
                && (lowerTitle.contains("helia") || lowerTitle.contains("hina")
                || lowerTitle.contains("torrhus") || lowerTitle.contains("galatea"));
        for (var slot : client.player.containerMenu.slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            String itemText = stackText(stack);
            itemTexts.add(itemText);
            List<String> boundedItemLines = new ArrayList<>();
            boundedItemLines.add(title);
            for (String rawLine : itemText.split("\\n")) {
                String line = HuntingTextParser.plain(rawLine);
                if (line.isBlank()) continue;
                menuLines.add(line);
                boundedItemLines.add(line);
                String lower = line.toLowerCase(Locale.ROOT);
                if ((lower.contains("helia") || lower.contains("hina") || lower.contains("galatea"))
                        && lower.contains("chapter")) chapterMenu = true;
                if (lower.matches("^(?:current )?(?:forest|desert) whispers?\\s*:.*")
                        || lower.matches("^(?:forest|safari) essence\\s*:.*")
                        || lower.matches("^(?:forest fortune|sweep)\\s*:.*")) {
                    resourceLines.add(line);
                }
            }
            applyBenefactor(HuntingTextParser.benefactor(boundedItemLines));
        }
        applyResources(HuntingTextParser.resources(resourceLines), false);
        if (chapterMenu) {
            HuntingTextParser.ChapterSnapshot parsed = HuntingTextParser.chapterMenu(title, itemTexts);
            if (!parsed.empty()) rememberChapter(parsed);
        }
    }

    /** Mirrors SkyHanni's tree-progress strategy: inspect only visible entity display text. */
    private static void scanTreeCritterTimer(Minecraft client) {
        if (!ConfigManager.get().hunting.treeCritterTimer || client.player == null || client.level == null) {
            treeCritterTimer = null;
            return;
        }
        HuntingTextParser.TreeCritterTimer nearestTimer = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity.isRemoved()) continue;
            HuntingTextParser.TreeCritterTimer parsed = HuntingTextParser.treeCritterTimer(
                    entity.getDisplayName().getString());
            if (parsed == null) continue;
            double distance = entity.distanceToSqr(client.player);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestTimer = parsed;
            }
        }
        treeCritterTimer = nearestTimer;
    }

    static String stackText(ItemStack stack) {
        StringBuilder result = new StringBuilder(HuntingTextParser.plain(stack.getHoverName().getString()));
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) {
            for (var line : lore.lines()) result.append('\n').append(HuntingTextParser.plain(line.getString()));
        }
        return result.toString();
    }

    public static int milestoneLevel(String raw) {
        try {
            return Math.clamp(Integer.parseInt(raw), 0, 10);
        } catch (NumberFormatException ignored) {
            return switch (raw.toUpperCase(Locale.ROOT)) {
                case "I" -> 1; case "II" -> 2; case "III" -> 3; case "IV" -> 4; case "V" -> 5;
                case "VI" -> 6; case "VII" -> 7; case "VIII" -> 8; case "IX" -> 9; case "X" -> 10;
                default -> 0;
            };
        }
    }

    private static String ticketIn(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        if (lower.contains("first-class safari")) return "First-Class";
        if (lower.contains("premium safari")) return "Premium";
        if (lower.contains("economy safari")) return "Economy";
        if (lower.contains("basic safari")) return "Basic";
        return "";
    }

    private static boolean isForagingChapterArea(IslandArea area) {
        return area == IslandArea.TORRHUS_CANYON || area == IslandArea.GALATEA;
    }

    public static String durationText(long seconds) {
        long hours = seconds / 3600;
        long minutes = seconds % 3600 / 60;
        long remainder = seconds % 60;
        if (hours > 0) return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, remainder);
        return String.format(Locale.ROOT, "%d:%02d", minutes, remainder);
    }

    public enum WumpaPhase {
        NONE("—"), AVAILABLE("Available"), SLEEPING("Sleeping"), AWOKEN("Awoken"),
        CHASING("Chasing"), STUNNED("Stunned"), CAUGHT("Caught"), FAILED("Failed");

        public final String display;
        WumpaPhase(String display) { this.display = display; }
        boolean active() { return this != NONE && this != CAUGHT && this != FAILED; }
    }

    public record BenefactorState(boolean active, long expiresAt, String temple,
                                  String effect, String donation) {
        public static final BenefactorState EMPTY = new BenefactorState(false, 0, "", "", "");
        public long remainingSeconds() {
            return expiresAt <= 0 ? -1 : Math.max(0, (expiresAt - System.currentTimeMillis() + 999) / 1_000);
        }
    }

    public record Route(Vec3 start, Vec3 end) { }
    public record WallFace(BlockPos pos, Direction face) { }
}

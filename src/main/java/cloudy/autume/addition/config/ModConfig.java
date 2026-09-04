package cloudy.autume.addition.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ModConfig {
    public int configVersion;
    public String language = "en_us";
    public boolean manualReconnectButton = true;

    public Maps maps = new Maps();
    public Mining mining = new Mining();
    public Fishing fishing = new Fishing();
    public Hunting hunting = new Hunting();
    public CrimsonIsle crimsonIsle = new CrimsonIsle();
    public Combat combat = new Combat();
    public CenturyCakes centuryCakes = new CenturyCakes();
    public Pets pets = new Pets();
    public Chat chat = new Chat();
    public Dungeons dungeons = new Dungeons();
    public Inventory inventory = new Inventory();
    public Integrations integrations = new Integrations();
    public Keybinds keybinds = new Keybinds();
    public HudStyle hudStyle = new HudStyle();

    public void normalize() {
        if (!"en_us".equals(language) && !"zh_cn".equals(language)) language = "en_us";
        if (maps == null) maps = new Maps();
        if (mining == null) mining = new Mining();
        if (fishing == null) fishing = new Fishing();
        if (hunting == null) hunting = new Hunting();
        if (crimsonIsle == null) crimsonIsle = new CrimsonIsle();
        if (combat == null) combat = new Combat();
        if (centuryCakes == null) centuryCakes = new CenturyCakes();
        if (pets == null) pets = new Pets();
        if (chat == null) chat = new Chat();
        if (dungeons == null) dungeons = new Dungeons();
        if (inventory == null) inventory = new Inventory();
        if (integrations == null) integrations = new Integrations();
        if (keybinds == null) keybinds = new Keybinds();
        if (hudStyle == null) hudStyle = new HudStyle();
        hudStyle.ensurePanels();
        if (configVersion < 2) {
            hudStyle.copyLegacyAppearanceToPanels();
            configVersion = 2;
        }
        if (configVersion < 3) {
            configVersion = 3;
        }
        if (configVersion < 4) {
            // Sound muting was replaced by non-destructive per-teleport sound
            // customization. Existing installs return to the original sounds.
            inventory.teleportSoundCustomization = true;
            inventory.instantTransmissionSoundMode = "VANILLA";
            inventory.etherwarpSoundMode = "VANILLA";
            configVersion = 4;
        }
        if (configVersion < 5) {
            // Pet menu/chat details are now retained locally so a max-level
            // pet does not lose its held-item row after reconnecting.
            configVersion = 5;
        }
        if (configVersion < 6) {
            // Rewriting version-5 configs drops fields retired from ACA.
            configVersion = 6;
        }
        if (configVersion < 7) {
            // Safari Essence now belongs only to the Torrhus resource HUD;
            // rewriting removes the retired Safari Dashboard toggle.
            configVersion = 7;
        }
        if (configVersion < 8) {
            // The alert system now stores sound/volume per alert source.
            configVersion = 8;
        }
        if (configVersion < 9) {
            // Persist Fairy Soul confirmations so collected waypoints stay hidden.
            configVersion = 9;
        }
        if (configVersion < 10) {
            // Move Hunting progression from one transient/global snapshot to
            // account + SkyBlock-profile memories. The first real profile seen
            // receives any legacy Safari Belt levels exactly once.
            hunting.legacySafariBeltMigrationPending = hunting.safariBeltCavernLevel > 0
                    || hunting.safariBeltForestLevel > 0 || hunting.safariBeltHauntedLevel > 0
                    || hunting.safariBeltIcyLevel > 0;
            configVersion = 10;
        }
        if (configVersion < 11) {
            // Keep reconnects explicitly player-triggered while making the
            // disconnect-screen button available to existing installations.
            manualReconnectButton = true;
            hunting.beeheemothSound = true;
            hunting.beeheemothSoundVolume = 64;
            configVersion = 11;
        }
        if (configVersion < 12) {
            // Fairy Soul waypoints are now one map-owned toggle. Legacy split
            // fields are intentionally discarded when the config is rewritten.
            configVersion = 12;
        }
        if (configVersion < 13) {
            // Slot Locking and Storage Overlay were removed from this mod.
            // Gson drops their old stored fields when the config is rewritten.
            configVersion = 13;
        }
        if (configVersion < 14) {
            // Middle-click menu conversion was removed. Teleport sound volumes
            // now use the same 0-100 scale and 64% default as alert volumes.
            inventory.instantTransmissionSoundVolume = 64;
            inventory.etherwarpSoundVolume = 64;
            configVersion = 14;
        }
        if (configVersion < 15) {
            // Galatea uses a separate feature group and toggles while sharing
            // the same client-received chapter/resource parsing model.
            hunting.galateaTracker = true;
            hunting.agathaContest = true;
            configVersion = 15;
        }
        if (configVersion < 16) {
            // Captured Safari Shards are now a separate HUD feature, not part
            // of the Critterdex toggle, and default to off.
            hunting.safariShards = false;
            configVersion = 16;
        }
        if (configVersion < 17) {
            // Shard Fusion is a local recipe guide and is enabled by default.
            inventory.shardFusionHelper = true;
            configVersion = 17;
        }
        if (configVersion < 18) {
            // The local fishing bite sound is opt-in. Existing users receive
            // the same disabled state and the project-wide 64% sound default.
            fishing.biteAlert = false;
            fishing.biteAlertVolume = 64;
            configVersion = 18;
        }
        if (configVersion < 19) {
            // The Shard Guide gains a fully local route planner, optional
            // client-mod Bazaar bridge, Hunting Box snapshots and graph layout.
            configVersion = 19;
        }
        if (configVersion < 20) {
            // First alpha of the optional unified SkyBlock mod settings layer.
            // Provider selections are local and do not make external mods required.
            configVersion = 20;
        }
        if (configVersion < 21) {
            // Cross-mod settings and HUD editing are powerful optional tools.
            // Keep both integrations opt-in and independent on every existing
            // installation instead of enabling provider writes during migration.
            integrations.unifiedSettingsEditor = false;
            integrations.unifiedHudEditor = false;
            configVersion = 21;
        }
        if (configVersion < 22) {
            // Power Orb and Flare expiry warnings are enabled by default and
            // keep an independent, locally played 64% alert sound.
            combat.deployableExpiryAlert = true;
            combat.deployableExpiryAudio = new AlertAudio();
            configVersion = 22;
        }
        if (configVersion < 23) {
            // Century Cake expiry alerts use one master switch for all twenty
            // effects. Sound remains local and follows the shared 64% default.
            centuryCakes.expiryAlerts = true;
            centuryCakes.expiryAudio = new AlertAudio();
            configVersion = 23;
        }
        if (configVersion < 24) {
            // Flare expiry is tracked from the local player's exact item use
            // and fixed lifecycle rather than an unreliable chat assumption.
            combat.deployablePowerOrbAlerts = true;
            combat.deployableFlareAlerts = true;
            combat.deployableExpiryCenterText = true;
            configVersion = 24;
        }
        if (configVersion < 25) {
            // Confirmed server messages now drive three independent
            // death-prevention alerts and cooldown HUDs. Their field defaults
            // remain opt-in; migration must not overwrite a saved choice.
            configVersion = 25;
        }
        if (configVersion < 26) {
            // Party auto-accept is opt-in. Missing fields already receive the
            // field default, so an upgrade only advances the schema version
            // and never overwrites an explicitly saved choice.
            configVersion = 26;
        }
        if (configVersion < 27) {
            // These party-command controls did not exist before schema 27, so
            // initializing them cannot overwrite a choice from an older build.
            chat.initializePartyCommandDefaults();
            configVersion = 27;
        }
        if (configVersion < 28) {
            // Dungeon Quick View is independent from the removed generic PV
            // commands and may be disabled without affecting party commands.
            dungeons.playerQuickView = true;
            configVersion = 28;
        }
        hudStyle.map.normalize();
        hudStyle.mining.normalize();
        hudStyle.hunting.normalize();
        hudStyle.pet.normalize();
        hudStyle.spiritMaskCooldown.normalize();
        hudStyle.bonzoMaskCooldown.normalize();
        hudStyle.phoenixCooldown.normalize();
        combat.normalize();
        centuryCakes.normalize();
        mining.normalize();
        fishing.normalize();
        hunting.normalize();
        pets.normalize();
        chat.normalize();
        inventory.normalize();
        integrations.normalize();
        keybinds.normalize();
    }

    public static final class Dungeons {
        public boolean playerQuickView = true;
    }

    public static final class Keybinds {
        public int openConfigModifiers;
        public int peekChatModifiers;
        public int openShardFusionModifiers;

        private void normalize() {
            openConfigModifiers &= 0x0F;
            peekChatModifiers &= 0x0F;
            openShardFusionModifiers &= 0x0F;
        }
    }

    public static final class Maps {
        public boolean dwarvenMines = true;
        public boolean glaciteTunnels = true;
    }

    public static final class Mining {
        public boolean taskAndPowderTracker = true;
        public boolean showHotmSlot = true;
        public String lastHotmSlotName = "";
        public String commissionProgressMode = "PERCENT";

        private void normalize() {
            if (lastHotmSlotName == null) lastHotmSlotName = "";
            if (!"PERCENT".equals(commissionProgressMode) && !"NUMERIC".equals(commissionProgressMode)) {
                commissionProgressMode = "PERCENT";
            }
        }
    }

    public static final class Fishing {
        public boolean biteAlert;
        public int biteAlertVolume = 64;

        private void normalize() {
            biteAlertVolume = Math.clamp(biteAlertVolume, 0, 100);
        }
    }

    public static final class CrimsonIsle {
        public boolean taskTracker = true;
    }

    public static final class Hunting {
        /** General master mute. Individual alert features own their volume. */
        public boolean alertSound = true;
        public AlertAudio critterBehaviorAudio = new AlertAudio();
        public AlertAudio benefactorAudio = new AlertAudio();
        public AlertAudio treeGiftAudio = new AlertAudio();
        public AlertAudio sparklingAudio = new AlertAudio();
        public AlertAudio floorDropAudio = new AlertAudio();
        public AlertAudio wumpaAudio = new AlertAudio();
        public AlertAudio coldAudio = new AlertAudio();
        public AlertAudio doomspiralAudio = new AlertAudio();
        public AlertAudio lassoReelAudio = new AlertAudio();
        public AlertAudio wardenReadyAudio = new AlertAudio();

        public boolean torrhusTracker = true;
        public boolean galateaTracker = true;
        public boolean treeCritterTimer = true;
        public boolean beeheemothHelper = true;
        public boolean beeheemothOutline = true;
        public boolean beeheemothBeacon = true;
        public int beeheemothOutlineColor = 0xFFD45A;
        public boolean beeheemothSound = true;
        public int beeheemothSoundVolume = 64;
        public boolean showChapter = true;
        public boolean showCurrentTask = true;
        public boolean showTaskProgress = true;
        public boolean showCompletedTasks;
        public boolean showChapterTotalProgress;
        public boolean showNextUnlock;
        public boolean showForestWhispers = true;
        public boolean showDesertWhispers = true;
        public boolean showForestEssence = true;
        public boolean showSafariEssenceTorrhus = true;
        public boolean showSweep = true;
        public boolean showForestFortune = true;

        public boolean miriaContest = true;
        public boolean agathaContest = true;
        public boolean contestNextBracket = true;
        public boolean contestExpectedTicket = true;
        public boolean contestRemainingScore = true;

        public boolean critterBehavior = true;
        public boolean blueJayAssistant = true;
        public boolean goldolotAssistant = true;
        public boolean dustybitAssistant = true;
        public boolean hideonsunAssistant = true;

        public boolean benefactorHud = true;
        public boolean benefactorStatus = true;
        public boolean benefactorTimer = true;
        public boolean benefactorEffects = true;
        public boolean benefactorDonation = true;

        public boolean treeGiftAlerts = true;
        public Map<String, Boolean> treeGiftLoot = defaultTreeGiftLoot();

        public boolean safariDashboard = true;
        public boolean safariShards;
        public boolean safariRunTime = true;
        public boolean safariTicketTier = true;

        public boolean safariCritterdex = true;
        public boolean critterdexBiomeProgress = true;
        public boolean critterdexCapturedNames = true;
        public boolean critterdexMissingNames = true;

        public boolean sparklingAlert = true;
        public boolean sparklingShowBiome = true;
        public boolean sparklingOutline = true;
        public int sparklingOutlineColor = 0xFF9B26;

        public boolean floorDropAssistant = true;
        public boolean floorDropAlert = true;
        public boolean floorDropDistance = true;
        public boolean questItemTracker = true;

        public boolean wumpaHud = true;
        public boolean wumpaRequirements = true;
        public boolean wumpaPhase = true;
        public boolean wumpaAlerts = true;
        public boolean wumpaFailureWarning = true;
        public boolean wumpaRoutePrediction;

        public boolean snoozleWallOverlay = true;
        public int snoozleWallOverlayColor = 0x55FF55;

        public boolean coldSafety = true;
        public int coldFirstThreshold = 80;
        public int coldSecondThreshold = 90;
        public boolean coldCampfireBeacon = true;

        public boolean doomspiralReadyAlert = true;
        public boolean wardenReadyAlert = true;

        public boolean fairySoulWaypoints;
        public Map<String, Set<String>> foundFairySoulsByProfile = new LinkedHashMap<>();

        public boolean safariCritterHighlight = true;

        public boolean safariBeltTooltip = true;
        public boolean safariBeltMilestones = true;
        public boolean safariBeltBonuses = true;
        public int safariBeltCavernLevel;
        public int safariBeltForestLevel;
        public int safariBeltHauntedLevel;
        public int safariBeltIcyLevel;
        public boolean legacySafariBeltMigrationPending;
        public Map<String, HuntingProgressMemory> rememberedProgressByProfile = new LinkedHashMap<>();

        private void normalize() {
            if (critterBehaviorAudio == null) critterBehaviorAudio = new AlertAudio();
            if (benefactorAudio == null) benefactorAudio = new AlertAudio();
            if (treeGiftAudio == null) treeGiftAudio = new AlertAudio();
            if (sparklingAudio == null) sparklingAudio = new AlertAudio();
            if (floorDropAudio == null) floorDropAudio = new AlertAudio();
            if (wumpaAudio == null) wumpaAudio = new AlertAudio();
            if (coldAudio == null) coldAudio = new AlertAudio();
            if (doomspiralAudio == null) doomspiralAudio = new AlertAudio();
            if (lassoReelAudio == null) lassoReelAudio = new AlertAudio();
            if (wardenReadyAudio == null) wardenReadyAudio = new AlertAudio();
            critterBehaviorAudio.normalize();
            benefactorAudio.normalize();
            treeGiftAudio.normalize();
            sparklingAudio.normalize();
            floorDropAudio.normalize();
            wumpaAudio.normalize();
            coldAudio.normalize();
            doomspiralAudio.normalize();
            lassoReelAudio.normalize();
            wardenReadyAudio.normalize();
            coldFirstThreshold = Math.clamp(coldFirstThreshold, 0, 98);
            coldSecondThreshold = Math.clamp(coldSecondThreshold, 1, 99);
            if (coldFirstThreshold >= coldSecondThreshold) {
                coldFirstThreshold = coldSecondThreshold - 1;
            }
            sparklingOutlineColor &= 0xFFFFFF;
            beeheemothOutlineColor &= 0xFFFFFF;
            beeheemothSoundVolume = Math.clamp(beeheemothSoundVolume, 0, 100);
            snoozleWallOverlayColor &= 0xFFFFFF;
            safariBeltCavernLevel = Math.clamp(safariBeltCavernLevel, 0, 10);
            safariBeltForestLevel = Math.clamp(safariBeltForestLevel, 0, 10);
            safariBeltHauntedLevel = Math.clamp(safariBeltHauntedLevel, 0, 10);
            safariBeltIcyLevel = Math.clamp(safariBeltIcyLevel, 0, 10);
            Map<String, HuntingProgressMemory> repairedProgress = new LinkedHashMap<>();
            if (rememberedProgressByProfile != null) {
                for (var entry : rememberedProgressByProfile.entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null || repairedProgress.size() >= 64) {
                        continue;
                    }
                    String key = entry.getKey().trim().toLowerCase(Locale.ROOT)
                            .replaceAll("[^a-z0-9_-]", "_");
                    if (key.isBlank()) continue;
                    HuntingProgressMemory memory = entry.getValue();
                    memory.normalize();
                    repairedProgress.put(key, memory);
                }
            }
            rememberedProgressByProfile = repairedProgress;
            Map<String, Boolean> repaired = defaultTreeGiftLoot();
            if (treeGiftLoot != null) {
                for (String name : repaired.keySet()) {
                    Boolean enabled = treeGiftLoot.get(name);
                    if (enabled != null) repaired.put(name, enabled);
                }
            }
            treeGiftLoot = repaired;
            Map<String, Set<String>> repairedFairySouls = new LinkedHashMap<>();
            if (foundFairySoulsByProfile != null) {
                foundFairySoulsByProfile.forEach((profile, positions) -> {
                    if (profile == null || profile.isBlank() || positions == null) return;
                    LinkedHashSet<String> cleaned = new LinkedHashSet<>();
                    positions.stream().filter(position -> position != null && !position.isBlank())
                            .limit(64).forEach(cleaned::add);
                    repairedFairySouls.put(profile.toLowerCase(Locale.ROOT), cleaned);
                });
            }
            foundFairySoulsByProfile = repairedFairySouls;
        }

        private static Map<String, Boolean> defaultTreeGiftLoot() {
            Map<String, Boolean> result = new LinkedHashMap<>();
            result.put("Firefox", true);
            result.put("Groundhog", true);
            result.put("Drybark", true);
            result.put("Puck", true);
            result.put("Grizzly Bear", true);
            result.put("Signal Enhancer", true);
            result.put("Chameleon Shard", true);
            result.put("Hummingbird Shard", true);
            result.put("Dreadwing", true);
            result.put("Enchanted Book (Karma I)", true);
            return result;
        }
    }

    public static final class AlertAudio {
        public boolean sound = true;
        public int volume = 64;

        private void normalize() {
            volume = Math.clamp(volume, 0, 100);
        }
    }

    /** Last confirmed Hunting values for one Minecraft account + SkyBlock profile. */
    public static final class HuntingProgressMemory {
        private static final Set<String> RESOURCE_KEYS = Set.of(
                "FOREST_WHISPERS", "DESERT_WHISPERS", "FOREST_ESSENCE", "SAFARI_ESSENCE",
                "FOREST_FORTUNE", "SWEEP");

        public Map<String, Double> resources = new LinkedHashMap<>();
        public int safariBeltCavernLevel;
        public int safariBeltForestLevel;
        public int safariBeltHauntedLevel;
        public int safariBeltIcyLevel;
        public String chapter = "";
        public String currentTask = "";
        public String taskProgress = "";
        public String completedTasks = "";
        public String chapterTotalProgress = "";
        public String nextUnlock = "";
        public long benefactorExpiresAt;
        public String benefactorTemple = "";
        public String benefactorEffect = "";
        public String benefactorDonation = "";

        private void normalize() {
            Map<String, Double> repairedResources = new LinkedHashMap<>();
            if (resources != null) {
                for (String key : RESOURCE_KEYS) {
                    Double value = resources.get(key);
                    if (value != null && Double.isFinite(value) && value >= 0.0) {
                        repairedResources.put(key, value);
                    }
                }
            }
            resources = repairedResources;
            safariBeltCavernLevel = Math.clamp(safariBeltCavernLevel, 0, 10);
            safariBeltForestLevel = Math.clamp(safariBeltForestLevel, 0, 10);
            safariBeltHauntedLevel = Math.clamp(safariBeltHauntedLevel, 0, 10);
            safariBeltIcyLevel = Math.clamp(safariBeltIcyLevel, 0, 10);
            if (chapter == null) chapter = "";
            if (currentTask == null) currentTask = "";
            if (taskProgress == null) taskProgress = "";
            if (completedTasks == null) completedTasks = "";
            if (chapterTotalProgress == null) chapterTotalProgress = "";
            if (nextUnlock == null) nextUnlock = "";
            if (benefactorExpiresAt < 0) benefactorExpiresAt = 0;
            if (benefactorTemple == null) benefactorTemple = "";
            if (benefactorEffect == null) benefactorEffect = "";
            if (benefactorDonation == null) benefactorDonation = "";
        }
    }

    public static final class Combat {
        public boolean enderDragonHighlight = true;
        public int enderDragonHighlightColor = 0xFF405C;
        public boolean deployableExpiryAlert = true;
        public boolean deployablePowerOrbAlerts = true;
        public boolean deployableFlareAlerts = true;
        public boolean deployableExpiryCenterText = true;
        public AlertAudio deployableExpiryAudio = new AlertAudio();
        public boolean deathSaveAlerts;
        public boolean spiritMaskCooldownHud;
        public boolean bonzoMaskCooldownHud;
        public boolean phoenixCooldownHud;

        private void normalize() {
            enderDragonHighlightColor &= 0xFFFFFF;
            if (deployableExpiryAudio == null) deployableExpiryAudio = new AlertAudio();
            deployableExpiryAudio.normalize();
        }
    }

    public static final class CenturyCakes {
        /** One master switch; individual cake effects never get separate toggles. */
        public boolean expiryAlerts = true;
        public AlertAudio expiryAudio = new AlertAudio();

        private void normalize() {
            if (expiryAudio == null) expiryAudio = new AlertAudio();
            expiryAudio.normalize();
        }
    }

    public static final class Pets {
        public boolean equippedPetHud = true;
        public boolean showPetIcon = true;
        public boolean showLevelProgress = true;
        public boolean showMaxProgress = true;
        public boolean showOverflowLevel = true;
        public boolean showSkinName = true;
        public String petAccessoryDisplay = "ICON_AND_NAME";
        public Map<String, PetMemory> rememberedDetails = new LinkedHashMap<>();

        private void normalize() {
            if (!"ICON_AND_NAME".equals(petAccessoryDisplay) && !"ICON_ONLY".equals(petAccessoryDisplay)
                    && !"NAME_ONLY".equals(petAccessoryDisplay)) petAccessoryDisplay = "ICON_AND_NAME";
            if (rememberedDetails == null) rememberedDetails = new LinkedHashMap<>();
            Map<String, PetMemory> repaired = new LinkedHashMap<>();
            for (var entry : rememberedDetails.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null || repaired.size() >= 128) continue;
                String key = entry.getKey().trim().toLowerCase(Locale.ROOT)
                        .replace('-', '_').replace(' ', '_');
                if (key.isBlank()) continue;
                PetMemory memory = entry.getValue();
                memory.normalize();
                if (!memory.isEmpty()) repaired.put(key, memory);
            }
            rememberedDetails = repaired;
        }
    }

    public static final class PetMemory {
        public String skinKey = "";
        public String heldItemId = "";
        public double totalExperience;

        public PetMemory() {
        }

        public PetMemory(String skinKey, String heldItemId, double totalExperience) {
            this.skinKey = skinKey;
            this.heldItemId = heldItemId;
            this.totalExperience = totalExperience;
            normalize();
        }

        private void normalize() {
            if (skinKey == null) skinKey = "";
            if (heldItemId == null) heldItemId = "";
            if (!Double.isFinite(totalExperience) || totalExperience < 0.0) totalExperience = 0.0;
        }

        public boolean isEmpty() {
            return skinKey.isBlank() && heldItemId.isBlank() && totalExperience <= 0.0;
        }
    }

    public enum PartyAcceptFriendMode {
        NORMAL_ONLY,
        SPECIAL_ONLY
    }

    public enum PartyCommandTrigger {
        SELF_ONLY,
        OTHERS_ONLY,
        EVERYONE
    }

    public static final class Chat {
        public static final int PARTY_AUTO_ACCEPT_WHITELIST_LIMIT = 16;

        public boolean chatPeek = true;
        public String peekScrollTarget = "CHAT";
        public boolean partyAutoAccept;
        public PartyAcceptFriendMode partyAutoAcceptFriendMode = PartyAcceptFriendMode.NORMAL_ONLY;
        public List<String> partyAutoAcceptWhitelist = new ArrayList<>();

        /** Invite the sender when a supported keyword arrives in a private message. */
        public boolean directMessagePartyRequest;
        /** Send a private !p request from the local double-slash helper. */
        public boolean quickPrivatePartyRequest;

        /** Party-chat keyword commands. The master is opt-in; child choices persist independently. */
        public boolean fastPartyCommands;
        public boolean fastPartyWarp = true;
        public boolean fastPartyAllInvite = true;
        public boolean fastPartyTransfer = true;
        public boolean fastPartyKick = true;
        public boolean fastPartyCoordinates = true;
        public boolean fastPartyPromote = true;
        public boolean fastPartyStream = true;
        public boolean fastPartyDungeon = true;
        public boolean fastPartyKuudra = true;
        public PartyCommandTrigger fastPartyWarpTrigger = PartyCommandTrigger.EVERYONE;
        public PartyCommandTrigger fastPartyAllInviteTrigger = PartyCommandTrigger.EVERYONE;
        public PartyCommandTrigger fastPartyTransferTrigger = PartyCommandTrigger.EVERYONE;
        public PartyCommandTrigger fastPartyKickTrigger = PartyCommandTrigger.EVERYONE;
        public PartyCommandTrigger fastPartyCoordinatesTrigger = PartyCommandTrigger.EVERYONE;
        public PartyCommandTrigger fastPartyPromoteTrigger = PartyCommandTrigger.EVERYONE;
        public PartyCommandTrigger fastPartyStreamTrigger = PartyCommandTrigger.EVERYONE;
        public PartyCommandTrigger fastPartyDungeonTrigger = PartyCommandTrigger.EVERYONE;
        public PartyCommandTrigger fastPartyKuudraTrigger = PartyCommandTrigger.EVERYONE;

        /** Local client-only command aliases. This family is independent from party-chat keywords. */
        public boolean partyCommands = true;
        public boolean partyCommandWarp = true;
        public boolean partyCommandAllInvite = true;
        public boolean partyCommandTransfer = true;
        public boolean partyCommandKick = true;
        public boolean partyCommandCoordinates = true;
        public boolean partyCommandPromote = true;
        public boolean partyCommandStream = true;
        public boolean partyCommandDungeon = true;
        public boolean partyCommandKuudra = true;

        private void normalize() {
            if (!"CHAT".equals(peekScrollTarget) && !"HOTBAR".equals(peekScrollTarget)) {
                peekScrollTarget = "CHAT";
            }
            if (partyAutoAcceptFriendMode == null) {
                partyAutoAcceptFriendMode = PartyAcceptFriendMode.NORMAL_ONLY;
            }
            repairPartyCommandTriggers();
            List<String> repairedWhitelist = new ArrayList<>();
            if (partyAutoAcceptWhitelist != null) {
                for (String name : partyAutoAcceptWhitelist) {
                    String normalized = normalizePartyAutoAcceptName(name);
                    if (!isValidMinecraftUsername(normalized)
                            || containsIgnoreCase(repairedWhitelist, normalized)) {
                        continue;
                    }
                    repairedWhitelist.add(normalized);
                    if (repairedWhitelist.size() >= PARTY_AUTO_ACCEPT_WHITELIST_LIMIT) break;
                }
            }
            partyAutoAcceptWhitelist = repairedWhitelist;
        }

        private void initializePartyCommandDefaults() {
            directMessagePartyRequest = false;
            quickPrivatePartyRequest = false;
            fastPartyCommands = false;
            fastPartyWarp = true;
            fastPartyAllInvite = true;
            fastPartyTransfer = true;
            fastPartyKick = true;
            fastPartyCoordinates = true;
            fastPartyPromote = true;
            fastPartyStream = true;
            fastPartyDungeon = true;
            fastPartyKuudra = true;
            fastPartyWarpTrigger = PartyCommandTrigger.EVERYONE;
            fastPartyAllInviteTrigger = PartyCommandTrigger.EVERYONE;
            fastPartyTransferTrigger = PartyCommandTrigger.EVERYONE;
            fastPartyKickTrigger = PartyCommandTrigger.EVERYONE;
            fastPartyCoordinatesTrigger = PartyCommandTrigger.EVERYONE;
            fastPartyPromoteTrigger = PartyCommandTrigger.EVERYONE;
            fastPartyStreamTrigger = PartyCommandTrigger.EVERYONE;
            fastPartyDungeonTrigger = PartyCommandTrigger.EVERYONE;
            fastPartyKuudraTrigger = PartyCommandTrigger.EVERYONE;
            partyCommands = true;
            partyCommandWarp = true;
            partyCommandAllInvite = true;
            partyCommandTransfer = true;
            partyCommandKick = true;
            partyCommandCoordinates = true;
            partyCommandPromote = true;
            partyCommandStream = true;
            partyCommandDungeon = true;
            partyCommandKuudra = true;
        }

        private void repairPartyCommandTriggers() {
            if (fastPartyWarpTrigger == null) fastPartyWarpTrigger = PartyCommandTrigger.EVERYONE;
            if (fastPartyAllInviteTrigger == null) fastPartyAllInviteTrigger = PartyCommandTrigger.EVERYONE;
            if (fastPartyTransferTrigger == null) fastPartyTransferTrigger = PartyCommandTrigger.EVERYONE;
            if (fastPartyKickTrigger == null) fastPartyKickTrigger = PartyCommandTrigger.EVERYONE;
            if (fastPartyCoordinatesTrigger == null) fastPartyCoordinatesTrigger = PartyCommandTrigger.EVERYONE;
            if (fastPartyPromoteTrigger == null) fastPartyPromoteTrigger = PartyCommandTrigger.EVERYONE;
            if (fastPartyStreamTrigger == null) fastPartyStreamTrigger = PartyCommandTrigger.EVERYONE;
            if (fastPartyDungeonTrigger == null) fastPartyDungeonTrigger = PartyCommandTrigger.EVERYONE;
            if (fastPartyKuudraTrigger == null) fastPartyKuudraTrigger = PartyCommandTrigger.EVERYONE;
        }

        public static String normalizePartyAutoAcceptName(String name) {
            return name == null ? "" : name.trim();
        }

        public static boolean isValidMinecraftUsername(String name) {
            String normalized = normalizePartyAutoAcceptName(name);
            return normalized.matches("[A-Za-z0-9_]{1,16}");
        }

        public boolean containsPartyAutoAcceptWhitelist(String name) {
            String normalized = normalizePartyAutoAcceptName(name);
            return containsIgnoreCase(partyAutoAcceptWhitelist, normalized);
        }

        public boolean addPartyAutoAcceptWhitelist(String name) {
            String normalized = normalizePartyAutoAcceptName(name);
            if (!isValidMinecraftUsername(normalized)
                    || partyAutoAcceptWhitelist.size() >= PARTY_AUTO_ACCEPT_WHITELIST_LIMIT
                    || containsIgnoreCase(partyAutoAcceptWhitelist, normalized)) {
                return false;
            }
            partyAutoAcceptWhitelist.add(normalized);
            return true;
        }

        public boolean replacePartyAutoAcceptWhitelist(String oldName, String newName) {
            String oldNormalized = normalizePartyAutoAcceptName(oldName);
            String newNormalized = normalizePartyAutoAcceptName(newName);
            if (!isValidMinecraftUsername(newNormalized)) return false;
            int index = indexOfIgnoreCase(partyAutoAcceptWhitelist, oldNormalized);
            if (index < 0) return false;
            int duplicate = indexOfIgnoreCase(partyAutoAcceptWhitelist, newNormalized);
            if (duplicate >= 0 && duplicate != index) return false;
            partyAutoAcceptWhitelist.set(index, newNormalized);
            return true;
        }

        public boolean removePartyAutoAcceptWhitelist(String name) {
            int index = indexOfIgnoreCase(partyAutoAcceptWhitelist,
                    normalizePartyAutoAcceptName(name));
            if (index < 0) return false;
            partyAutoAcceptWhitelist.remove(index);
            return true;
        }

        private static boolean containsIgnoreCase(List<String> names, String candidate) {
            return indexOfIgnoreCase(names, candidate) >= 0;
        }

        private static int indexOfIgnoreCase(List<String> names, String candidate) {
            if (names == null || candidate == null) return -1;
            for (int index = 0; index < names.size(); index++) {
                String name = names.get(index);
                if (name != null && name.equalsIgnoreCase(candidate)) return index;
            }
            return -1;
        }
    }

    public static final class Inventory {
        public boolean shardFusionHelper = true;
        public String shardPlannerMode = "IRONMAN";
        public String shardPlannerObjective = "FASTEST";
        public String shardPlannerTarget = "L4";
        public int shardPlannerQuantity = 1;
        public boolean shardPlannerMaterialsOnly;
        public boolean shardPlannerUseWarehouse = true;
        public boolean shardPlannerInstantBuy = true;
        public int shardPlannerHunterFortune;
        public int shardPlannerCrocodileLevel;
        public double shardPlannerCoinsPerHour;
        public int shardPlannerCraftSeconds = 1;
        public String shardPlannerKuudraTier = "NONE";
        public int shardPlannerKuudraSeconds = 60;
        public Map<String, Double> shardPlannerRates = new LinkedHashMap<>();
        public Map<String, String> shardFusionLinePositions = new LinkedHashMap<>();

        public boolean itemTimestamps = true;
        public boolean showCreationTimestamp = true;
        public boolean showCountdownCompletion = true;
        public String timestampFormat = "LOCAL_24H";

        public boolean saveCursorPosition = true;
        public int cursorToleranceMs = 500;

        public boolean teleportSoundCustomization = true;
        public String instantTransmissionSoundMode = "VANILLA";
        public String instantTransmissionCustomSound = "CHORUS";
        public int instantTransmissionSoundVolume = 64;
        public String etherwarpSoundMode = "VANILLA";
        public String etherwarpCustomSound = "AMETHYST";
        public int etherwarpSoundVolume = 64;

        private void normalize() {
            if (!"IRONMAN".equals(shardPlannerMode) && !"NORMAL".equals(shardPlannerMode)) {
                shardPlannerMode = "IRONMAN";
            }
            if (!"FASTEST".equals(shardPlannerObjective) && !"CHEAPEST".equals(shardPlannerObjective)) {
                shardPlannerObjective = "FASTEST";
            }
            if ("IRONMAN".equals(shardPlannerMode)) shardPlannerObjective = "FASTEST";
            if (shardPlannerTarget == null) shardPlannerTarget = "L4";
            shardPlannerTarget = shardPlannerTarget.trim().toUpperCase(Locale.ROOT);
            if (!shardPlannerTarget.matches("[CUREL]\\d+")) shardPlannerTarget = "L4";
            shardPlannerQuantity = Math.clamp(shardPlannerQuantity, 1, 1_000_000);
            shardPlannerHunterFortune = Math.clamp(shardPlannerHunterFortune, 0, 10_000);
            shardPlannerCrocodileLevel = Math.clamp(shardPlannerCrocodileLevel, 0, 10);
            if (!Double.isFinite(shardPlannerCoinsPerHour) || shardPlannerCoinsPerHour < 0.0) {
                shardPlannerCoinsPerHour = 0.0;
            }
            shardPlannerCraftSeconds = Math.clamp(shardPlannerCraftSeconds, 0, 600);
            if (shardPlannerKuudraTier == null || !Set.of("NONE", "T1", "T2", "T3", "T4", "T5")
                    .contains(shardPlannerKuudraTier)) shardPlannerKuudraTier = "NONE";
            shardPlannerKuudraSeconds = Math.clamp(shardPlannerKuudraSeconds, 10, 1800);
            Map<String, Double> repairedRates = new LinkedHashMap<>();
            if (shardPlannerRates != null) {
                for (var entry : shardPlannerRates.entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null || repairedRates.size() >= 320) continue;
                    String id = entry.getKey().trim().toUpperCase(Locale.ROOT);
                    double rate = entry.getValue();
                    if (id.matches("[CUREL]\\d+") && Double.isFinite(rate) && rate >= 0.0) {
                        repairedRates.put(id, rate);
                    }
                }
            }
            shardPlannerRates = repairedRates;
            Map<String, String> repairedPositions = new LinkedHashMap<>();
            if (shardFusionLinePositions != null) {
                for (var entry : shardFusionLinePositions.entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null || repairedPositions.size() >= 320) continue;
                    String id = entry.getKey().trim().toUpperCase(Locale.ROOT);
                    if (id.matches("[CUREL]\\d+") && entry.getValue().matches("-?\\d+,-?\\d+")) {
                        repairedPositions.put(id, entry.getValue());
                    }
                }
            }
            shardFusionLinePositions = repairedPositions;
            cursorToleranceMs = Math.clamp(cursorToleranceMs, 50, 5000);
            instantTransmissionSoundVolume = Math.clamp(instantTransmissionSoundVolume, 0, 100);
            etherwarpSoundVolume = Math.clamp(etherwarpSoundVolume, 0, 100);
            if (!"LOCAL_24H".equals(timestampFormat) && !"LOCAL_12H".equals(timestampFormat)
                    && !"ISO".equals(timestampFormat) && !"RFC".equals(timestampFormat)) {
                timestampFormat = "LOCAL_24H";
            }
            if (!"VANILLA".equals(instantTransmissionSoundMode)
                    && !"CUSTOM".equals(instantTransmissionSoundMode)) {
                instantTransmissionSoundMode = "VANILLA";
            }
            if (!"VANILLA".equals(etherwarpSoundMode) && !"CUSTOM".equals(etherwarpSoundMode)) {
                etherwarpSoundMode = "VANILLA";
            }
            if (!validTeleportSound(instantTransmissionCustomSound)) instantTransmissionCustomSound = "CHORUS";
            if (!validTeleportSound(etherwarpCustomSound)) etherwarpCustomSound = "AMETHYST";
        }

        private static boolean validTeleportSound(String value) {
            return "CHORUS".equals(value) || "ENDERMAN".equals(value) || "AMETHYST".equals(value)
                    || "ORB".equals(value) || "PORTAL".equals(value) || "SHULKER".equals(value);
        }
    }

    public static final class Integrations {
        /** Expose recognised live settings from installed provider mods. */
        public boolean unifiedSettingsEditor;
        /** Expose recognised live provider HUD positions in QCA's HUD editor. */
        public boolean unifiedHudEditor;
        /** Logical feature id -> selected live configuration provider. */
        public Map<String, String> selectedProviders = new LinkedHashMap<>();

        private void normalize() {
            Map<String, String> repaired = new LinkedHashMap<>();
            if (selectedProviders != null) {
                for (var entry : selectedProviders.entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null || repaired.size() >= 2048) continue;
                    String key = entry.getKey().trim().toLowerCase(Locale.ROOT)
                            .replaceAll("[^a-z0-9_:.-]", "_");
                    String provider = entry.getValue().trim().toUpperCase(Locale.ROOT);
                    if (!key.isBlank() && Set.of("QCLOUDY", "SKYHANNI", "SKYBLOCKER", "FIRMAMENT",
                            "BABYZOMBIE").contains(provider)) repaired.put(key, provider);
                }
            }
            selectedProviders = repaired;
        }
    }

    public enum HudType {
        MAP,
        MINING,
        HUNTING,
        PET,
        SPIRIT_MASK_COOLDOWN,
        BONZO_MASK_COOLDOWN,
        PHOENIX_COOLDOWN
    }

    public static final class HudStyle {
        public boolean animations = true;
        public PanelStyle map = new PanelStyle();
        public PanelStyle mining = new PanelStyle();
        public PanelStyle hunting = new PanelStyle();
        public PanelStyle pet = new PanelStyle();
        public PanelStyle spiritMaskCooldown = new PanelStyle();
        public PanelStyle bonzoMaskCooldown = new PanelStyle();
        public PanelStyle phoenixCooldown = new PanelStyle();

        public int mapX = 8;
        public int mapY = 8;
        public int miningX = -196;
        public int miningY = 8;
        public int huntingX = -304;
        public int huntingY = 8;
        public int petX = 8;
        public int petY = 196;
        public int spiritMaskCooldownX = -196;
        public int spiritMaskCooldownY = 196;
        public int bonzoMaskCooldownX = -196;
        public int bonzoMaskCooldownY = 236;
        public int phoenixCooldownX = -196;
        public int phoenixCooldownY = 276;

        // Version 1 fields are retained solely to migrate existing user configs.
        @Deprecated public int backgroundOpacity = 120;
        @Deprecated public boolean border = true;
        @Deprecated public int borderThickness = 1;
        @Deprecated public boolean boldText;
        @Deprecated public boolean textShadow = true;
        @Deprecated public float scale = 1.0f;

        public PanelStyle style(HudType type) {
            ensurePanels();
            return switch (type) {
                case MAP -> map;
                case MINING -> mining;
                case HUNTING -> hunting;
                case PET -> pet;
                case SPIRIT_MASK_COOLDOWN -> spiritMaskCooldown;
                case BONZO_MASK_COOLDOWN -> bonzoMaskCooldown;
                case PHOENIX_COOLDOWN -> phoenixCooldown;
            };
        }

        private void ensurePanels() {
            if (map == null) map = new PanelStyle();
            if (mining == null) mining = new PanelStyle();
            if (hunting == null) hunting = new PanelStyle();
            if (pet == null) pet = new PanelStyle();
            if (spiritMaskCooldown == null) spiritMaskCooldown = new PanelStyle();
            if (bonzoMaskCooldown == null) bonzoMaskCooldown = new PanelStyle();
            if (phoenixCooldown == null) phoenixCooldown = new PanelStyle();
        }

        private void copyLegacyAppearanceToPanels() {
            ensurePanels();
            for (PanelStyle panel : new PanelStyle[]{map, mining, hunting, pet,
                    spiritMaskCooldown, bonzoMaskCooldown, phoenixCooldown}) {
                panel.backgroundOpacity = backgroundOpacity;
                panel.border = border;
                panel.borderThickness = borderThickness;
                panel.boldText = boldText;
                panel.textShadow = textShadow;
                panel.scale = scale;
            }
        }
    }

    public static final class PanelStyle {
        public int backgroundOpacity = 120;
        public int backgroundColor = 0x08111C;
        public boolean border = true;
        public int borderThickness = 1;
        public int borderColor = 0x50C8FF;
        public boolean boldText;
        public boolean textShadow = true;
        public int titleColor = 0x7FDBFF;
        public float scale = 1.0f;

        public void normalize() {
            backgroundOpacity = Math.clamp(backgroundOpacity, 0, 255);
            backgroundColor &= 0xFFFFFF;
            borderThickness = Math.clamp(borderThickness, 1, 4);
            borderColor &= 0xFFFFFF;
            titleColor &= 0xFFFFFF;
            if (!Float.isFinite(scale)) scale = 1.0f;
            scale = Math.clamp(scale, 0.5f, 2.0f);
        }
    }
}

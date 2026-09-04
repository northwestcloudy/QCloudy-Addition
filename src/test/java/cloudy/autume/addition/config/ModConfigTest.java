package cloudy.autume.addition.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class ModConfigTest {
    @Test
    void repairsNullSectionsAndUnsafeStyleValues() {
        ModConfig config = new ModConfig();
        config.language = "invalid";
        config.maps = null;
        config.mining = null;
        config.fishing = null;
        config.hunting = null;
        config.crimsonIsle = null;
        config.combat = null;
        config.centuryCakes = null;
        config.pets = null;
        config.chat = null;
        config.dungeons = null;
        config.hudStyle = null;

        config.normalize();

        assertEquals("en_us", config.language);
        assertNotNull(config.maps);
        assertNotNull(config.mining);
        assertNotNull(config.fishing);
        assertNotNull(config.hunting);
        assertNotNull(config.crimsonIsle);
        assertNotNull(config.combat);
        assertNotNull(config.centuryCakes);
        assertNotNull(config.pets);
        assertNotNull(config.chat);
        assertNotNull(config.dungeons);
        assertNotNull(config.hudStyle);

        config.hudStyle.pet.backgroundOpacity = 999;
        config.hudStyle.pet.borderThickness = -5;
        config.hudStyle.pet.scale = Float.NaN;
        config.hudStyle.map.scale = 1.75f;
        config.mining.commissionProgressMode = "invalid";
        config.mining.lastHotmSlotName = null;
        config.keybinds.openShardFusionModifiers = 0x7F;
        config.normalize();
        assertEquals(255, config.hudStyle.pet.backgroundOpacity);
        assertEquals(1, config.hudStyle.pet.borderThickness);
        assertEquals(1.0f, config.hudStyle.pet.scale);
        assertEquals(1.75f, config.hudStyle.map.scale);
        assertEquals(28, config.configVersion);
        assertEquals(true, config.manualReconnectButton);
        assertEquals(true, config.pets.showMaxProgress);
        assertEquals(true, config.pets.showOverflowLevel);
        assertEquals(true, config.pets.showSkinName);
        assertEquals("ICON_AND_NAME", config.pets.petAccessoryDisplay);
        assertEquals("PERCENT", config.mining.commissionProgressMode);
        assertEquals("", config.mining.lastHotmSlotName);
        assertEquals(true, config.mining.showHotmSlot);
        assertEquals(false, config.integrations.unifiedSettingsEditor);
        assertEquals(false, config.integrations.unifiedHudEditor);
        assertEquals(true, config.inventory.shardFusionHelper);
        assertEquals("IRONMAN", config.inventory.shardPlannerMode);
        assertEquals("FASTEST", config.inventory.shardPlannerObjective);
        assertEquals("L4", config.inventory.shardPlannerTarget);
        assertEquals(1, config.inventory.shardPlannerQuantity);
        assertEquals(true, config.inventory.shardPlannerUseWarehouse);
        assertEquals(true, config.inventory.shardPlannerInstantBuy);
        assertEquals("NONE", config.inventory.shardPlannerKuudraTier);
        assertNotNull(config.inventory.shardPlannerRates);
        assertNotNull(config.inventory.shardFusionLinePositions);
        assertEquals(false, config.fishing.biteAlert);
        assertEquals(64, config.fishing.biteAlertVolume);
        assertEquals(0x0F, config.keybinds.openShardFusionModifiers);
        assertEquals(true, config.chat.chatPeek);
        assertEquals("CHAT", config.chat.peekScrollTarget);
        assertEquals(true, config.inventory.teleportSoundCustomization);
        assertEquals("VANILLA", config.inventory.instantTransmissionSoundMode);
        assertEquals("VANILLA", config.inventory.etherwarpSoundMode);
        assertEquals(64, config.inventory.instantTransmissionSoundVolume);
        assertEquals(64, config.inventory.etherwarpSoundVolume);
        assertEquals(true, config.hunting.alertSound);
        assertEquals(true, config.combat.deployableExpiryAlert);
        assertEquals(true, config.combat.deployablePowerOrbAlerts);
        assertEquals(true, config.combat.deployableFlareAlerts);
        assertEquals(true, config.combat.deployableExpiryCenterText);
        assertEquals(true, config.combat.deployableExpiryAudio.sound);
        assertEquals(64, config.combat.deployableExpiryAudio.volume);
        assertEquals(false, config.combat.deathSaveAlerts);
        assertEquals(false, config.combat.spiritMaskCooldownHud);
        assertEquals(false, config.combat.bonzoMaskCooldownHud);
        assertEquals(false, config.combat.phoenixCooldownHud);
        assertNotNull(config.hudStyle.spiritMaskCooldown);
        assertNotNull(config.hudStyle.bonzoMaskCooldown);
        assertNotNull(config.hudStyle.phoenixCooldown);
        assertEquals(true, config.centuryCakes.expiryAlerts);
        assertEquals(true, config.centuryCakes.expiryAudio.sound);
        assertEquals(64, config.centuryCakes.expiryAudio.volume);
        assertEquals(64, config.hunting.treeGiftAudio.volume);
        assertEquals(64, config.hunting.sparklingAudio.volume);
        assertEquals(true, config.hunting.coldAudio.sound);
        assertEquals(true, config.hunting.coldSafety);
        assertEquals(80, config.hunting.coldFirstThreshold);
        assertEquals(90, config.hunting.coldSecondThreshold);
        assertEquals(true, config.hunting.coldCampfireBeacon);
        assertEquals(true, config.hunting.doomspiralReadyAlert);
        assertEquals(false, config.hunting.fairySoulWaypoints);
        assertEquals(true, config.hunting.safariCritterHighlight);
        assertEquals(false, config.hunting.safariShards);
        assertEquals(false, config.hunting.wumpaRoutePrediction);
        assertEquals(true, config.hunting.wumpaRequirements);
        assertEquals(true, config.hunting.snoozleWallOverlay);
        assertEquals(0x55FF55, config.hunting.snoozleWallOverlayColor);
        assertEquals(true, config.hunting.showSafariEssenceTorrhus);
        assertEquals(true, config.hunting.treeCritterTimer);
        assertEquals(true, config.hunting.beeheemothHelper);
        assertEquals(true, config.hunting.beeheemothOutline);
        assertEquals(true, config.hunting.beeheemothBeacon);
        assertEquals(0xFFD45A, config.hunting.beeheemothOutlineColor);
        assertEquals(true, config.hunting.beeheemothSound);
        assertEquals(64, config.hunting.beeheemothSoundVolume);
        assertEquals(true, config.hunting.lassoReelAudio.sound);
        assertEquals(64, config.hunting.lassoReelAudio.volume);
        assertEquals(true, config.hunting.wardenReadyAlert);
        assertEquals(true, config.hunting.wardenReadyAudio.sound);
        assertEquals(64, config.hunting.wardenReadyAudio.volume);
        assertEquals(false, config.chat.partyAutoAccept);
        assertEquals(ModConfig.PartyAcceptFriendMode.NORMAL_ONLY,
                config.chat.partyAutoAcceptFriendMode);
        assertEquals(java.util.List.of(), config.chat.partyAutoAcceptWhitelist);
        assertEquals(false, config.chat.directMessagePartyRequest);
        assertEquals(false, config.chat.quickPrivatePartyRequest);
        assertEquals(false, config.chat.fastPartyCommands);
        assertEquals(true, config.chat.fastPartyWarp);
        assertEquals(true, config.chat.fastPartyAllInvite);
        assertEquals(true, config.chat.fastPartyTransfer);
        assertEquals(true, config.chat.fastPartyKick);
        assertEquals(true, config.chat.fastPartyCoordinates);
        assertEquals(true, config.chat.fastPartyPromote);
        assertEquals(true, config.chat.fastPartyStream);
        assertEquals(true, config.chat.fastPartyDungeon);
        assertEquals(true, config.chat.fastPartyKuudra);
        assertEquals(java.util.List.of(
                        ModConfig.PartyCommandTrigger.EVERYONE,
                        ModConfig.PartyCommandTrigger.EVERYONE,
                        ModConfig.PartyCommandTrigger.EVERYONE,
                        ModConfig.PartyCommandTrigger.EVERYONE,
                        ModConfig.PartyCommandTrigger.EVERYONE,
                        ModConfig.PartyCommandTrigger.EVERYONE,
                        ModConfig.PartyCommandTrigger.EVERYONE,
                        ModConfig.PartyCommandTrigger.EVERYONE,
                        ModConfig.PartyCommandTrigger.EVERYONE),
                java.util.List.of(
                        config.chat.fastPartyWarpTrigger,
                        config.chat.fastPartyAllInviteTrigger,
                        config.chat.fastPartyTransferTrigger,
                        config.chat.fastPartyKickTrigger,
                        config.chat.fastPartyCoordinatesTrigger,
                        config.chat.fastPartyPromoteTrigger,
                        config.chat.fastPartyStreamTrigger,
                        config.chat.fastPartyDungeonTrigger,
                        config.chat.fastPartyKuudraTrigger));
        assertEquals(true, config.chat.partyCommands);
        assertEquals(true, config.chat.partyCommandWarp);
        assertEquals(true, config.chat.partyCommandAllInvite);
        assertEquals(true, config.chat.partyCommandTransfer);
        assertEquals(true, config.chat.partyCommandKick);
        assertEquals(true, config.chat.partyCommandCoordinates);
        assertEquals(true, config.chat.partyCommandPromote);
        assertEquals(true, config.chat.partyCommandStream);
        assertEquals(true, config.chat.partyCommandDungeon);
        assertEquals(true, config.chat.partyCommandKuudra);
        assertEquals(true, config.hunting.showChapter);
        assertEquals(true, config.hunting.galateaTracker);
        assertEquals(true, config.hunting.agathaContest);
        assertEquals(false, config.hunting.showCompletedTasks);
        assertEquals(10, config.hunting.treeGiftLoot.size());
        assertEquals(true, config.hunting.treeGiftLoot.get("Dreadwing"));
        assertNotNull(config.hunting.foundFairySoulsByProfile);
        assertNotNull(config.hunting.rememberedProgressByProfile);

        config.hunting.snoozleWallOverlayColor = 0xFF123456;
        config.hunting.beeheemothSoundVolume = 900;
        config.fishing.biteAlertVolume = -50;
        config.normalize();
        assertEquals(0x123456, config.hunting.snoozleWallOverlayColor);
        assertEquals(100, config.hunting.beeheemothSoundVolume);
        assertEquals(0, config.fishing.biteAlertVolume);

        config.hunting.coldFirstThreshold = 100;
        config.hunting.coldSecondThreshold = -10;
        config.normalize();
        assertEquals(0, config.hunting.coldFirstThreshold);
        assertEquals(1, config.hunting.coldSecondThreshold);
    }

    @Test
    void migratesTeleportMutingToOriginalSoundsAndClampsCustomization() {
        ModConfig migrated = new ModConfig();
        migrated.configVersion = 3;
        migrated.inventory.instantTransmissionSoundMode = "CUSTOM";
        migrated.inventory.etherwarpSoundMode = "CUSTOM";

        migrated.normalize();

        assertEquals(28, migrated.configVersion);
        assertEquals("VANILLA", migrated.inventory.instantTransmissionSoundMode);
        assertEquals("VANILLA", migrated.inventory.etherwarpSoundMode);
        assertEquals(false, migrated.hunting.safariShards);
        assertEquals(true, migrated.inventory.shardFusionHelper);
        assertEquals(false, migrated.fishing.biteAlert);
        assertEquals(64, migrated.fishing.biteAlertVolume);
        assertEquals(false, migrated.integrations.unifiedSettingsEditor);
        assertEquals(false, migrated.integrations.unifiedHudEditor);
        assertEquals(true, migrated.combat.deployableExpiryAlert);
        assertEquals(true, migrated.combat.deployablePowerOrbAlerts);
        assertEquals(true, migrated.combat.deployableFlareAlerts);
        assertEquals(true, migrated.combat.deployableExpiryCenterText);
        assertEquals(64, migrated.combat.deployableExpiryAudio.volume);
        assertEquals(false, migrated.combat.deathSaveAlerts);
        assertEquals(false, migrated.combat.spiritMaskCooldownHud);
        assertEquals(false, migrated.combat.bonzoMaskCooldownHud);
        assertEquals(false, migrated.combat.phoenixCooldownHud);
        assertEquals(false, migrated.chat.partyAutoAccept);
        assertEquals(true, migrated.centuryCakes.expiryAlerts);
        assertEquals(64, migrated.centuryCakes.expiryAudio.volume);

        migrated.inventory.instantTransmissionSoundMode = "invalid";
        migrated.inventory.instantTransmissionCustomSound = "invalid";
        migrated.inventory.instantTransmissionSoundVolume = -20;
        migrated.inventory.etherwarpSoundVolume = 900;
        migrated.normalize();

        assertEquals("VANILLA", migrated.inventory.instantTransmissionSoundMode);
        assertEquals("CHORUS", migrated.inventory.instantTransmissionCustomSound);
        assertEquals(0, migrated.inventory.instantTransmissionSoundVolume);
        assertEquals(100, migrated.inventory.etherwarpSoundVolume);
    }

    @Test
    void migrationsPreserveExplicitDeathSaveAndPartyAutoAcceptChoices() {
        ModConfig migrated = new ModConfig();
        migrated.configVersion = 25;
        migrated.combat.deathSaveAlerts = true;
        migrated.combat.spiritMaskCooldownHud = true;
        migrated.combat.bonzoMaskCooldownHud = true;
        migrated.combat.phoenixCooldownHud = true;
        migrated.chat.partyAutoAccept = true;

        migrated.normalize();

        assertEquals(28, migrated.configVersion);
        assertEquals(true, migrated.combat.deathSaveAlerts);
        assertEquals(true, migrated.combat.spiritMaskCooldownHud);
        assertEquals(true, migrated.combat.bonzoMaskCooldownHud);
        assertEquals(true, migrated.combat.phoenixCooldownHud);
        assertEquals(true, migrated.chat.partyAutoAccept);
        assertEquals(ModConfig.HudType.SPIRIT_MASK_COOLDOWN,
                ModConfig.HudType.valueOf("SPIRIT_MASK_COOLDOWN"));
        assertEquals(false, migrated.hudStyle.spiritMaskCooldownY
                == migrated.hudStyle.bonzoMaskCooldownY);
        assertEquals(false, migrated.hudStyle.bonzoMaskCooldownY
                == migrated.hudStyle.phoenixCooldownY);
    }

    @Test
    void migration27InitializesOnlyNewPartyCommandFields() {
        ModConfig migrated = new ModConfig();
        migrated.configVersion = 26;
        migrated.combat.deathSaveAlerts = true;
        migrated.chat.partyAutoAccept = true;
        migrated.chat.directMessagePartyRequest = true;
        migrated.chat.fastPartyWarp = false;
        migrated.chat.fastPartyWarpTrigger = ModConfig.PartyCommandTrigger.SELF_ONLY;
        migrated.chat.partyCommands = false;
        migrated.chat.partyCommandKuudra = false;

        migrated.normalize();

        assertEquals(28, migrated.configVersion);
        assertEquals(true, migrated.combat.deathSaveAlerts);
        assertEquals(true, migrated.chat.partyAutoAccept);
        assertEquals(false, migrated.chat.directMessagePartyRequest);
        assertEquals(false, migrated.chat.quickPrivatePartyRequest);
        assertEquals(false, migrated.chat.fastPartyCommands);
        assertEquals(true, migrated.chat.fastPartyWarp);
        assertEquals(ModConfig.PartyCommandTrigger.EVERYONE, migrated.chat.fastPartyWarpTrigger);
        assertEquals(true, migrated.chat.partyCommands);
        assertEquals(true, migrated.chat.partyCommandKuudra);
    }

    @Test
    void currentSchemaPreservesCommandChoicesAndRepairsOnlyNullTriggerScopes() {
        ModConfig config = new ModConfig();
        config.configVersion = 28;
        config.chat.directMessagePartyRequest = true;
        config.chat.quickPrivatePartyRequest = true;
        config.chat.fastPartyCommands = true;
        config.chat.fastPartyWarp = false;
        config.chat.fastPartyWarpTrigger = ModConfig.PartyCommandTrigger.OTHERS_ONLY;
        config.chat.fastPartyPromoteTrigger = null;
        config.chat.partyCommands = false;
        config.chat.partyCommandPromote = false;

        config.normalize();

        assertEquals(true, config.chat.directMessagePartyRequest);
        assertEquals(true, config.chat.quickPrivatePartyRequest);
        assertEquals(true, config.chat.fastPartyCommands);
        assertEquals(false, config.chat.fastPartyWarp);
        assertEquals(ModConfig.PartyCommandTrigger.OTHERS_ONLY, config.chat.fastPartyWarpTrigger);
        assertEquals(ModConfig.PartyCommandTrigger.EVERYONE, config.chat.fastPartyPromoteTrigger);
        assertEquals(false, config.chat.partyCommands);
        assertEquals(false, config.chat.partyCommandPromote);
    }

    @Test
    void partyWhitelistIsOrderedCaseInsensitiveValidatedAndCappedAtSixteen() {
        ModConfig config = new ModConfig();
        config.configVersion = 28;
        config.chat.partyAutoAcceptFriendMode = null;
        config.chat.partyAutoAcceptWhitelist = new java.util.ArrayList<>();
        config.chat.partyAutoAcceptWhitelist.add("  Alice  ");
        config.chat.partyAutoAcceptWhitelist.add("alice");
        config.chat.partyAutoAcceptWhitelist.add("bad-name");
        config.chat.partyAutoAcceptWhitelist.add("");
        for (int index = 0; index < 20; index++) {
            config.chat.partyAutoAcceptWhitelist.add("Player_" + index);
        }

        config.normalize();

        assertEquals(ModConfig.PartyAcceptFriendMode.NORMAL_ONLY,
                config.chat.partyAutoAcceptFriendMode);
        assertEquals(16, config.chat.partyAutoAcceptWhitelist.size());
        assertEquals("Alice", config.chat.partyAutoAcceptWhitelist.getFirst());
        assertEquals(true, config.chat.containsPartyAutoAcceptWhitelist("ALICE"));
        assertEquals(false, config.chat.addPartyAutoAcceptWhitelist("overflow"));
        assertEquals(false, ModConfig.Chat.isValidMinecraftUsername("bad-name"));
        assertEquals(true, ModConfig.Chat.isValidMinecraftUsername("_"));

        assertEquals(true, config.chat.replacePartyAutoAcceptWhitelist("alice", "Cloudy"));
        assertEquals("Cloudy", config.chat.partyAutoAcceptWhitelist.getFirst());
        assertEquals(false, config.chat.replacePartyAutoAcceptWhitelist("Cloudy", "Player_0"));
        assertEquals(true, config.chat.removePartyAutoAcceptWhitelist("cLoUdY"));
        assertEquals(false, config.chat.containsPartyAutoAcceptWhitelist("Cloudy"));
    }

    @Test
    void repairsAndKeepsSeparateHuntingProgressForEachPlayerProfile() {
        ModConfig config = new ModConfig();
        ModConfig.HuntingProgressMemory apple = new ModConfig.HuntingProgressMemory();
        apple.resources.put("FOREST_WHISPERS", 25_271.0);
        apple.resources.put("INVALID", 999.0);
        apple.safariBeltCavernLevel = 4;
        apple.safariBeltForestLevel = 12;
        config.hunting.rememberedProgressByProfile.put("UUID_Apple", apple);

        ModConfig.HuntingProgressMemory banana = new ModConfig.HuntingProgressMemory();
        banana.resources.put("DESERT_WHISPERS", 8_500.0);
        banana.safariBeltIcyLevel = 7;
        config.hunting.rememberedProgressByProfile.put("UUID_Banana", banana);

        config.normalize();

        assertEquals(2, config.hunting.rememberedProgressByProfile.size());
        ModConfig.HuntingProgressMemory repairedApple =
                config.hunting.rememberedProgressByProfile.get("uuid_apple");
        ModConfig.HuntingProgressMemory repairedBanana =
                config.hunting.rememberedProgressByProfile.get("uuid_banana");
        assertEquals(25_271.0, repairedApple.resources.get("FOREST_WHISPERS"));
        assertEquals(false, repairedApple.resources.containsKey("INVALID"));
        assertEquals(4, repairedApple.safariBeltCavernLevel);
        assertEquals(10, repairedApple.safariBeltForestLevel);
        assertEquals(8_500.0, repairedBanana.resources.get("DESERT_WHISPERS"));
        assertEquals(7, repairedBanana.safariBeltIcyLevel);
    }
}

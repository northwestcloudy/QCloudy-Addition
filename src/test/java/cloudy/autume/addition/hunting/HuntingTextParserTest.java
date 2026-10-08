package cloudy.autume.addition.hunting;

import org.junit.jupiter.api.Test;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HuntingTextParserTest {
    @Test
    void preservesFullChapterTaskAndSeparatesProgress() {
        var result = HuntingTextParser.chapter(List.of(
                "Torrhus Chapter III",
                "Current Task:",
                "Catch 5 Unique Critters in the Critter Safari 2/5",
                "Completed: 4/9",
                "Chapter Progress: 44.4%",
                "Next Unlock: Chapter IV"));

        assertEquals("Chapter III", result.chapter());
        assertEquals("Catch 5 Unique Critters in the Critter Safari", result.task());
        assertEquals("2/5", result.progress());
        assertEquals("4/9", result.completed());
        assertEquals("44.4%", result.totalProgress());
        assertEquals("Chapter IV", result.nextUnlock());
        assertFalse(result.task().contains("..."));
    }

    @Test
    void parsesAbsoluteAndAdditiveResourcesWithoutTreatingStatPlusAsGain() {
        var updates = HuntingTextParser.resources(List.of(
                "Forest Whispers: 17.6k",
                "Forest Fortune: +125",
                "You gained +8 Safari Essence!"));

        assertEquals(3, updates.size());
        assertEquals(HuntingTextParser.Resource.FOREST_WHISPERS, updates.get(0).resource());
        assertFalse(updates.get(0).additive());
        assertEquals("125", updates.get(1).amount());
        assertFalse(updates.get(1).additive());
        assertEquals(HuntingTextParser.Resource.SAFARI_ESSENCE, updates.get(2).resource());
        assertTrue(updates.get(2).additive());
    }

    @Test
    void calculatesMiriaBracketFromReceivedCapturedMobs() {
        var result = HuntingTextParser.contest(List.of("Miria's Contest", "Captured Mobs: 820"));

        assertTrue(result.active());
        assertEquals(820, result.score());
        assertEquals(1_000, result.nextScore());
        assertEquals(180, result.remaining());
        assertEquals("Uncommon", result.bracket());
        assertEquals("Rare", result.nextBracket());
        assertEquals("Basic Safari Ticket", result.ticket());
    }

    @Test
    void parsesLiveMiriaWidgetTierAndRequiredDelta() {
        var result = HuntingTextParser.contest(List.of(
                "Miria's Contest 0m30s",
                "COMMON with 151",
                "Uncommon requires +99",
                "Basic Safari Ticket"));

        assertTrue(result.active());
        assertEquals(151, result.score());
        assertEquals("Common", result.bracket());
        assertEquals("Uncommon", result.nextBracket());
        assertEquals(250, result.nextScore());
        assertEquals(99, result.remaining());
    }

    @Test
    void parsesHotfAndForagingTrackerResourceFormats() {
        var updates = HuntingTextParser.resources(List.of(
                "Forest Whispers: 30,820",
                "Desert Whisper: 1.0m",
                "Forest Essence: 2,696x",
                "Forest Essence x4"));

        assertEquals(4, updates.size());
        assertFalse(updates.get(0).additive());
        assertFalse(updates.get(2).additive());
        assertTrue(updates.get(3).additive());
    }

    @Test
    void parsesSweepFromMainValueBeforeLogReductionText() {
        var updates = HuntingTextParser.resources(List.of(
                "Sweep: 952.84, 13.78 logs (-50%) (-50%) -> 5.46 logs"));

        assertEquals(1, updates.size());
        assertEquals(HuntingTextParser.Resource.SWEEP, updates.get(0).resource());
        assertEquals("952.84", updates.get(0).amount());
        assertFalse(updates.get(0).additive());
    }

    @Test
    void parsesSweepAdditiveOnlyWhenSweepStartsTheLine() {
        var updates = HuntingTextParser.resources(List.of("Sweep x4"));

        assertEquals(1, updates.size());
        assertEquals(HuntingTextParser.Resource.SWEEP, updates.get(0).resource());
        assertEquals("4", updates.get(0).amount());
        assertTrue(updates.get(0).additive());
    }

    @Test
    void doesNotTreatUnrelatedSkyblockProgressAsAChapterTask() {
        assertTrue(HuntingTextParser.chapter(List.of(
                "SB Level: [430] 78/100 XP",
                "Purse: 4,277,768")).empty());
        var contextual = HuntingTextParser.chapter(List.of(
                "Helia Chapter",
                "Task: SB Level: [430] 78/100 XP",
                "Progress: 78/100",
                "Purse: 4,277,768"));
        assertEquals("", contextual.task());
        assertEquals("", contextual.progress());
    }

    @Test
    void parsesReceivedHeliaChapterBlockWithoutCrossingIntoOtherTabSections() {
        var result = HuntingTextParser.chapter(List.of(
                "Helia's Chapter: III",
                "Catch 5 Unique Critters in the Critter Safari 2/5",
                "Tasks completed: 4/9"));

        assertEquals("Chapter III", result.chapter());
        assertEquals("Catch 5 Unique Critters in the Critter Safari", result.task());
        assertEquals("2/5", result.progress());
        assertEquals("4/9", result.completed());
        assertTrue(HuntingTextParser.chapter(List.of(
                "Helia Chapter",
                "SB Level: [430] 78/100 XP",
                "Purse: 4,277,768")).empty());
    }

    @Test
    void parsesRealHeliaChapterOverviewAndIgnoresLockedLaterChapters() {
        var result = HuntingTextParser.chapterMenu("Helia's Chapters", List.of(
                "Torrhus Canyon - Chapter 1\nTasks completed: 3/3\nClick to view tasks!",
                "Torrhus Canyon - Chapter 2\nTasks completed: 2/5\nClick to view tasks!",
                "Torrhus Canyon - Chapter 3\nTasks completed: 0/9\nYou haven't unlocked this chapter yet!"));

        assertEquals("Chapter 2", result.chapter());
        assertEquals("2/5", result.completed());
        assertEquals("", result.task());
    }

    @Test
    void parsesRealHeliaChapterDetailAndChoosesFirstIncompleteTask() {
        var result = HuntingTextParser.chapterMenu("Helia - Chapter 3", List.of(
                "Talk to Helia\nProgress: 1/1",
                "Catch 5 Unique Critters in the Critter Safari\nProgress: 2/5",
                "Loot 3 Tree Gifts\nProgress: 0/3",
                "Chapter 3\nTasks completed: 4/9"));

        assertEquals("Chapter 3", result.chapter());
        assertEquals("Catch 5 Unique Critters in the Critter Safari", result.task());
        assertEquals("2/5", result.progress());
        assertEquals("4/9", result.completed());
    }

    @Test
    void usesOfficialSparklingAndCaptureMessages() {
        assertEquals("Icy", HuntingTextParser.sparklingBiome(
                "A SPARKLING Critter has appeared in the Icy Biome!"));
        var capture = HuntingTextParser.capture(
                "CAPTURE! You caught a SPARKLING Wumpa and received a Rainbow Feather and 10x Wumpa Shard!");
        assertNotNull(capture);
        assertEquals("Wumpa", capture.critter());
        assertEquals("Wumpa Shard", capture.shard());
        assertEquals(10, capture.amount());
        assertTrue(capture.sparkling());
    }

    @Test
    void parsesTeammateLootShareCaptureForWumpaPrerequisites() {
        var capture = HuntingTextParser.capture(
                "LOOT SHARE! You received a Troodon Shard from Cloudy catching a Troodon!");

        assertNotNull(capture);
        assertEquals("Troodon", capture.critter());
        assertTrue(capture.lootShare());
        assertFalse(HuntingTracker.personalSafariCapture(capture));
        assertTrue(HuntingTracker.wumpaPrerequisiteCapture(capture));
    }

    @Test
    void distinguishesLassoCaptureConfirmationFromUnrelatedCatchText() {
        assertTrue(HuntingTextParser.captureConfirmation(
                "CAPTURE! You caught a Blue Jay and gained a Blue Jay Shard!"));
        assertFalse(HuntingTextParser.captureConfirmation("GREAT CATCH! You caught a Squid!"));
        assertFalse(HuntingTextParser.captureConfirmation("You caught a stray rabbit!"));
        assertTrue(HuntingTextParser.lassoReelLabel("§e§lREEL§r"));
        assertFalse(HuntingTextParser.lassoReelLabel("REEL IN"));
    }

    @Test
    void treeGiftSelectionRespectsOwnershipTogglesAndKarmaLine() {
        Map<String, Boolean> loot = new LinkedHashMap<>();
        loot.put("Firefox", false);
        loot.put("Grizzly Bear", true);
        loot.put("Enchanted Book (Karma I)", true);

        Component ownRewards = Component.literal("+3 rewards gained! ").append(
                Component.literal("(hover)").withStyle(style -> style.withHoverEvent(
                        new HoverEvent.ShowText(Component.literal(
                                "Firefox (0.2%)\nGrizzly Bear (0.4%)\nEnchanted Book (Karma I)")))));
        assertEquals(List.of("Grizzly Bear", "Enchanted Book (Karma I)"),
                HuntingTextParser.personalTreeGiftLoot(ownRewards, loot));

        Component nearbyPlayerDrop = Component.literal("A Grizzly Bear fell from the Tree!")
                .withStyle(style -> style.withHoverEvent(
                        new HoverEvent.ShowText(Component.literal("Grizzly Bear (0.4%)"))));
        assertEquals(List.of(), HuntingTextParser.personalTreeGiftLoot(nearbyPlayerDrop, loot));
    }

    @Test
    void treeGiftMangoDyeRespectsItsConfiguredToggle() {
        Map<String, Boolean> loot = new LinkedHashMap<>();
        loot.put("Mango Dye", true);

        Component ownRewards = Component.literal("+1 rewards gained! ").append(
                Component.literal("(hover)").withStyle(style -> style.withHoverEvent(
                        new HoverEvent.ShowText(Component.literal("Mango Dye (0.0001%)")))));
        assertEquals(List.of("Mango Dye"),
                HuntingTextParser.personalTreeGiftLoot(ownRewards, loot));
        assertEquals("Mango Dye",
                HuntingTextParser.treeGiftChatLoot("Mango Dye (0.0001%)", loot, true));

        loot.put("Mango Dye", false);
        assertEquals(List.of(), HuntingTextParser.personalTreeGiftLoot(ownRewards, loot));
        assertEquals("",
                HuntingTextParser.treeGiftChatLoot("Mango Dye (0.0001%)", loot, true));
    }

    @Test
    void readsTreeGiftLootFromReceivedHoverText() {
        Component message = Component.literal("+5 rewards gained! ").append(
                Component.literal("(hover)").withStyle(style -> style.withHoverEvent(
                        new HoverEvent.ShowText(Component.literal(
                                "Forest Essence x4\nSignal Enhancer (0.4%)\nDreadwing (1.2%)")))));

        assertTrue(HuntingTextParser.personalTreeGiftRewardSummary(message.getString()));
        assertEquals(List.of("Forest Essence x4", "Signal Enhancer (0.4%)", "Dreadwing (1.2%)"),
                HuntingTextParser.treeGiftHoverLines(message));
    }

    @Test
    void recognizesTreeGiftAndFairySoulServerFormats() {
        assertTrue(HuntingTextParser.personalTreeGiftRewardSummary("+5 rewards gained! (hover)"));
        assertFalse(HuntingTextParser.personalTreeGiftRewardSummary("TREE GIFT"));
        assertFalse(HuntingTextParser.personalTreeGiftRewardSummary("BONUS GIFT"));
        assertFalse(HuntingTextParser.personalTreeGiftRewardSummary(
                "You helped cut 33.0% of the Fig Tree."));
        assertFalse(HuntingTextParser.personalTreeGiftRewardSummary(
                "A Grizzly Bear fell from the Tree!"));
        assertFalse(HuntingTextParser.personalTreeGiftRewardSummary("▬".repeat(64)));
        assertTrue(HuntingTextParser.fairySoulConfirmation("SOUL! You found a Fairy Soul!"));
        assertTrue(HuntingTextParser.fairySoulConfirmation("You have already found that Fairy Soul!"));
        assertFalse(HuntingTextParser.fairySoulConfirmation("Fairy Souls: 3/12"));
    }

    @Test
    void containsEveryOfficialSafariCritterAndKeepsWikiOrder() {
        assertEquals(List.of("Cavern", "Forest", "Haunted", "Icy"),
                HuntingTextParser.SAFARI_CRITTERS.keySet().stream().toList());
        assertEquals(37, HuntingTextParser.SAFARI_CRITTERS.values().stream().mapToInt(List::size).sum());
        assertTrue(HuntingTextParser.SAFARI_CRITTERS.get("Icy").contains("Wumpa"));
        assertEquals(37, HuntingTextParser.SAFARI_CRITTER_RARITIES.size());
        assertEquals(HuntingTextParser.ShardRarity.EPIC,
                HuntingTextParser.critterRarity("[Lv 10] Gemzie"));
        assertEquals(HuntingTextParser.ShardRarity.LEGENDARY,
                HuntingTextParser.critterRarity("SPARKLING Wumpa"));
    }

    @Test
    void parsesReceivedMobLevelFormatsWithoutGuessingFromUntaggedNames() {
        assertEquals(1, HuntingTextParser.mobLevel("[Lv 1] Eagle 100/100❤"));
        assertEquals(120, HuntingTextParser.mobLevel(
                "[Lv 120] Mob Name 1.2M/1.2M❤"));
        assertEquals(42, HuntingTextParser.mobLevel(
                "§8[§7Lv 42§8] §cGemzie §a12k§c❤ ✯"));
        assertEquals(1_000, HuntingTextParser.mobLevel(
                "[Lv 1,000] Glyph Mob 2B/2B❤ ✦ ⚔"));

        assertEquals(null, HuntingTextParser.mobLevel("Eagle 100/100❤"));
        assertEquals(null, HuntingTextParser.mobLevel("Level 120 Mob Name 1.2M/1.2M❤"));
        assertEquals(null, HuntingTextParser.mobLevel("[Lv x] Mob Name 1.2M/1.2M❤"));
    }

    @Test
    void requiresExactlyTheEightNonWumpaIcyCrittersForWumpa() {
        assertEquals(List.of("Billygoat", "Mantis Shrimp", "Nozzlenose", "Polaris", "Shuddersquid",
                "Strongarm", "Tepid", "Troodon"), HuntingTextParser.WUMPA_PREREQUISITES);
        assertFalse(HuntingTextParser.WUMPA_PREREQUISITES.contains("Wumpa"));
        assertFalse(HuntingTracker.wumpaPrerequisitesComplete(Set.of("Billygoat", "Troodon")));
        assertTrue(HuntingTracker.wumpaPrerequisitesComplete(
                Set.copyOf(HuntingTextParser.WUMPA_PREREQUISITES)));
    }

    @Test
    void acceptsOnlySmallMixedSnoozleWallComponents() {
        assertTrue(HuntingTracker.snoozleWallComponent(true, true, 9));
        assertFalse(HuntingTracker.snoozleWallComponent(true, false, 9));
        assertFalse(HuntingTracker.snoozleWallComponent(false, true, 9));
        assertFalse(HuntingTracker.snoozleWallComponent(true, true, 3));
        assertFalse(HuntingTracker.snoozleWallComponent(true, true, 97));
    }

    @Test
    void parsesColdWithoutConfusingColdResistance() {
        assertEquals(91, HuntingTextParser.cold(List.of("Cold Resistance: 35", "❄ Cold: 91")));
        assertEquals(-1, HuntingTextParser.cold(List.of("Cold Resistance: 35")));
    }

    @Test
    void coldCampfireRequiresAValueStrictlyAboveThresholdThatIsNotFalling() {
        assertFalse(HuntingTracker.coldCampfireEligible(80, 80, false));
        assertTrue(HuntingTracker.coldCampfireEligible(81, 80, false));
        assertFalse(HuntingTracker.coldCampfireEligible(91, 80, true));
    }

    @Test
    void changingChapterDoesNotCarryThePreviousTaskIntoTheNewChapter() {
        var remembered = new HuntingTextParser.ChapterSnapshot(
                "Chapter II", "Catch 10 Critters", "7/10", "3/8", "37.5%", "Chapter III");
        var received = new HuntingTextParser.ChapterSnapshot(
                "Chapter III", "", "", "", "", "");

        var merged = HuntingTracker.mergeChapter(remembered, received);

        assertEquals("Chapter III", merged.chapter());
        assertEquals("", merged.task());
        assertEquals("", merged.progress());
        assertEquals("", merged.completed());
    }

    @Test
    void removesPreviouslyCachedNonChapterTaskWhenFreshChapterDataIsPartial() {
        var remembered = new HuntingTextParser.ChapterSnapshot(
                "Chapter III", "SB Level: [430] 78/100 XP", "78/100", "4/9", "", "");
        var received = new HuntingTextParser.ChapterSnapshot(
                "Chapter III", "", "", "5/9", "", "");

        var merged = HuntingTracker.mergeChapter(remembered, received);

        assertEquals("Chapter III", merged.chapter());
        assertEquals("", merged.task());
        assertEquals("", merged.progress());
        assertEquals("5/9", merged.completed());
    }

    @Test
    void parsesBenefactorStyleDurations() {
        assertEquals(5_430, HuntingTextParser.durationSeconds("1h 30m 30s"));
        assertEquals(754, HuntingTextParser.durationSeconds("12:34"));
        assertEquals(432_000, HuntingTextParser.durationSeconds("+5d"));
        assertEquals(500_295, HuntingTextParser.durationSeconds("5d 18:58:15"));
    }

    @Test
    void parsesOfficialBenefactorDonationChatAsAnAdditiveDuration() {
        var result = HuntingTextParser.benefactor(List.of(
                "BENEFACTOR: You donated to the Forest Temple and will receive its bonus for +5d!"));

        assertTrue(result.observed());
        assertTrue(result.active());
        assertTrue(result.additiveDuration());
        assertEquals(432_000, result.remainingSeconds());
        assertEquals("Forest Temple", result.temple());
        assertTrue(result.effect().contains("Foraging Fortune"));
    }

    @Test
    void parsesBenefactorFromBoundedTabAndTempleMenuBlocks() {
        var tab = HuntingTextParser.benefactor(List.of(
                "Benefactor",
                "Time Left: 18:58:15",
                "Forest Temple"));
        assertTrue(tab.observed());
        assertTrue(tab.active());
        assertEquals(68_295, tab.remainingSeconds());
        assertEquals("Forest Temple", tab.temple());

        var inactiveMenu = HuntingTextParser.benefactor(List.of(
                "Desert Temple",
                "Donate to Desert Starborn",
                "Become a Benefactor!",
                "BENEFACTOR BONUS",
                "You have no active donation for this temple!"));
        assertTrue(inactiveMenu.observed());
        assertFalse(inactiveMenu.active());
        assertEquals("Desert Temple", inactiveMenu.temple());

        assertFalse(HuntingTextParser.benefactor(List.of("Benefactors: 1,850")).observed());
    }

    @Test
    void parsesTreeProtectionOrderCritterCountdown() {
        var timer = HuntingTextParser.treeCritterTimer(" Critter in: 26m 47s");

        assertNotNull(timer);
        assertEquals("26m 47s", timer.display());
        assertEquals(1_607, timer.seconds());
        assertEquals(900, HuntingTextParser.treeCritterTimer("Critter in: 15m").seconds());
        assertEquals(0, HuntingTextParser.treeCritterTimer("Critter in: 0s").seconds());
    }

    @Test
    void rejectsUnrelatedCritterAndTreeProgressText() {
        assertEquals(null, HuntingTextParser.treeCritterTimer("Critter Safari: 26m 47s"));
        assertEquals(null, HuntingTextParser.treeCritterTimer("ACACIA TREE 82%"));
    }
}

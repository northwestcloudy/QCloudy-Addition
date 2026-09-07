package cloudy.autume.addition.tracker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TrackerParsingTest {
    @Test
    void classifiesTorrhusAndSafariWithoutConfusingTheEntrance() {
        assertEquals(IslandArea.TORRHUS_CANYON, LocationTracker.classifyEvidence("⏣ Torrhus Springs"));
        assertEquals(IslandArea.TORRHUS_CANYON, LocationTracker.classifyEvidence("⏣ Critter Safari Entrance"));
        assertEquals(IslandArea.GALATEA, LocationTracker.classifyEvidence("⏣ Galatea"));
        assertEquals(IslandArea.GALATEA, LocationTracker.classifyEvidence("Agatha's Contest"));
        assertEquals(IslandArea.CRITTER_SAFARI, LocationTracker.classifyEvidence("⏣ Critter Safari"));
        assertEquals(IslandArea.CRITTER_SAFARI, LocationTracker.classifyEvidence("⏣ Haunted Biome"));
    }
    @AfterEach
    void reset() {
        PetTracker.reset();
        PetSkinTracker.reset();
        TabListTracker.reset();
    }

    @Test
    void classifiesOfficialMiningAndEndSubLocations() {
        assertEquals(IslandArea.DWARVEN_MINES, LocationTracker.classifyEvidence("⏣ The Lift"));
        assertEquals(IslandArea.DWARVEN_MINES, LocationTracker.classifyEvidence("⏣ Palace Bridge"));
        assertEquals(IslandArea.DWARVEN_MINES, LocationTracker.classifyEvidence("⏣ C&C Minecarts Co."));
        assertEquals(IslandArea.GLACITE_TUNNELS, LocationTracker.classifyEvidence("⏣ Dwarven Base Camp"));
        assertEquals(IslandArea.GLACITE_TUNNELS, LocationTracker.classifyEvidence("⏣ Fossil Research Center"));
        assertEquals(IslandArea.GLACITE_TUNNELS, LocationTracker.classifyEvidence("⏣ Great Glacite Lake"));
        assertEquals(IslandArea.MINESHAFT, LocationTracker.classifyEvidence("⏣ Glacite Mineshafts"));
        assertEquals(IslandArea.CRYSTAL_HOLLOWS, LocationTracker.classifyEvidence("⏣ Goblin Queen's Den"));
        assertEquals(IslandArea.CRYSTAL_HOLLOWS, LocationTracker.classifyEvidence("⏣ Khazad-dûm"));
        assertEquals(IslandArea.CRYSTAL_HOLLOWS, LocationTracker.classifyEvidence("⏣ Jungle"));
        assertEquals(IslandArea.NONE, LocationTracker.classifyEvidence("⏣ Jungle Island"));
        assertEquals(IslandArea.THE_END, LocationTracker.classifyEvidence("⏣ Dragon's Nest"));
        assertEquals(IslandArea.CRIMSON_ISLE, LocationTracker.classifyEvidence("⏣ Crimson Isle"));
        assertEquals(IslandArea.CRIMSON_ISLE, LocationTracker.classifyEvidence("⏣ Ruins of Ashfang"));
        assertEquals(IslandArea.NONE, LocationTracker.classifyEvidence("⏣ Hub"));
    }

    @Test
    void extractsBoundedCommissionWidget() {
        List<String> result = TabListTracker.extractWidget(List.of(
                "Profile: Apple", "Commissions:", " Mithril Miner: 55%", " Goblin Slayer: 2/13",
                "", "Powders:", " Mithril: 35,448"), "Commissions:", 6);
        assertEquals(List.of("Mithril Miner: 55%", "Goblin Slayer: 2/13"), result);
    }

    @Test
    void extractsOnlyThePetWidgetIncludingItsHeldItem() {
        List<String> result = TabListTracker.extractWidget(List.of(
                "Profile: Apple", "Pet:", " [Lvl 200] Golden Dragon", " MAX LEVEL",
                " Dwarf Turtle Shelmet", "", "Powders:", " Mithril: 35,448"), "Pet:", 6);
        assertEquals(List.of("[Lvl 200] Golden Dragon", "MAX LEVEL", "Dwarf Turtle Shelmet"), result);
    }

    @Test
    void parsesPercentAndExactCommissionProgressFromClientText() {
        var percent = TabListTracker.parseCommission("Lava Springs Mithril: 80%", IslandArea.DWARVEN_MINES);
        assertEquals("Lava Springs Mithril", percent.name());
        assertEquals(80.0, percent.percentage());
        assertEquals(200, percent.current());
        assertEquals(250, percent.target());

        var exact = TabListTracker.parseCommission("Goblin Slayer: 2/13", IslandArea.CRYSTAL_HOLLOWS);
        assertEquals(2, exact.current());
        assertEquals(13, exact.target());
        assertEquals(2 * 100.0 / 13, exact.percentage());
    }

    @Test
    void resolvesWikiTargetsByMiningIslandWithoutGuessingUnknownTasks() {
        assertEquals(500, TabListTracker.targetFor("2x Mithril Powder Collector", IslandArea.DWARVEN_MINES));
        assertEquals(1_000, TabListTracker.targetFor("Ruby Gemstone Collector", IslandArea.CRYSTAL_HOLLOWS));
        assertEquals(1_500, TabListTracker.targetFor("Tungsten Collector", IslandArea.GLACITE_TUNNELS));
        assertEquals(-1, TabListTracker.targetFor("Future Unknown Task", IslandArea.DWARVEN_MINES));
    }

    @Test
    void findsOnlyTheSelectedNameInTheExactHotmSlotMenu() {
        var entries = List.of(
                new HotmSlotTracker.MenuEntry("Normal", List.of("SELECTED", "Right-click to rename!")),
                new HotmSlotTracker.MenuEntry("Powder", List.of("Click to select!")));
        assertEquals("Normal", HotmSlotTracker.selectedName("Heart of the Mountain Slot", entries));
        assertEquals("", HotmSlotTracker.selectedName("Heart of the Mountain", entries));
        var loadout = List.of(new HotmSlotTracker.MenuEntry("Heart of the Mountain",
                List.of("Current: Common")));
        assertEquals("Common", HotmSlotTracker.currentSelection(loadout));
    }

    @Test
    void parsesAllThreePowders() {
        TabListTracker.updatePowders(List.of("Powders:", " Mithril: 35,448",
                " Gemstone Powder: 1.4M", " Glacite: 29,537", "", "Pet:"));
        assertEquals("35,448", TabListTracker.mithrilPowder());
        assertEquals("1.4M", TabListTracker.gemstonePowder());
        assertEquals("29,537", TabListTracker.glacitePowder());
    }

    @Test
    void parsesCrimsonFactionQuestStatusAndKeepsOriginalName() {
        var pending = TabListTracker.parseCrimsonQuest(" ✖ Digested Mushrooms x20");
        assertEquals("Digested Mushrooms", pending.name());
        assertEquals(20, pending.amount());
        assertFalse(pending.readyToCollect());

        var ready = TabListTracker.parseCrimsonQuest(" ✔ Rescue Mission");
        assertEquals("Rescue Mission", ready.name());
        assertEquals(1, ready.amount());
        assertTrue(ready.readyToCollect());
    }

    @Test
    void detectsCompletedCrimsonFactionQuestsForHudFiltering() {
        assertTrue(TabListTracker.isCompletedCrimsonQuest("Heavy Pearls: COMPLETED"));
        assertTrue(TabListTracker.isCompletedCrimsonQuest(" Rescue Mission: DONE"));
        assertFalse(TabListTracker.isCompletedCrimsonQuest(" ✔ Rescue Mission"));
        assertFalse(TabListTracker.isCompletedCrimsonQuest(" ✖ Digested Mushrooms x20"));
    }

    @Test
    void parsesPetProgressAndNoPetState() {
        PetTracker.updateFromTab(List.of("Pet:", " [Lvl 200] [122✦] Golden Dragon", " 931,886.2/1.4M XP (67.2%)"));
        PetTracker.PetSnapshot pet = PetTracker.current();
        assertEquals("Golden Dragon", pet.name());
        assertEquals("200", pet.level());
        assertEquals("931,886.2", pet.currentXp());
        assertEquals("1.4M", pet.nextXp());
        assertEquals("67.2", pet.percentage());
        assertFalse(pet.maxLevel());

        PetTracker.updateFromTab(List.of("Pet:", " No pet selected"));
        assertNull(PetTracker.current());
    }

    @Test
    void parsesMaxLevelPet() {
        PetTracker.updateFromTab(List.of("Pet:", " [Lvl 100] Enderman", " MAX LEVEL", " +12.4M XP"));
        assertTrue(PetTracker.current().maxLevel());
        assertEquals("12.4M", PetTracker.current().overflowXp());
    }

    @Test
    void keepsPetRarityColorFromStyledTabName() {
        Component petLine = Component.literal("[Lvl 98] ")
                .append(Component.literal("Endermite").withStyle(ChatFormatting.GOLD));
        PetTracker.updateFromTab(
                List.of("Pet:", "[Lvl 98] Endermite", "1,200/3,400 XP (35.3%)"),
                List.of(Component.literal("Pet:"), petLine, Component.literal("1,200/3,400 XP (35.3%)")));
        assertEquals(0xFFAA00, PetTracker.current().rarityColor());
    }

    @Test
    void parsesTotalPetXpAndDoesNotReuseAnotherPetsLevel() {
        PetTracker.updateFromTab(List.of("Pet:", " [Lvl 200] Golden Dragon", " +163,119,730.2 XP"));
        assertEquals("163,119,730.2", PetTracker.current().currentXp());
        assertEquals("", PetTracker.current().nextXp());

        PetTracker.updateFromChat("You summoned your Enderman!");
        assertEquals("Enderman", PetTracker.current().name());
        assertEquals("?", PetTracker.current().level());
    }
}

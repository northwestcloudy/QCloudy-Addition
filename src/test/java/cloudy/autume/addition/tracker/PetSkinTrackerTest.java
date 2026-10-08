package cloudy.autume.addition.tracker;

import cloudy.autume.addition.config.ConfigManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PetSkinTrackerTest {
    @BeforeEach
    void suppressDiskWrites() {
        ConfigManager.get().pets.rememberedDetails.clear();
        PetSkinTracker.setMemorySavesEnabledForTests(false);
    }

    @AfterEach
    void reset() {
        PetTracker.reset();
        PetSkinTracker.reset();
        ConfigManager.get().pets.rememberedDetails.clear();
        PetSkinTracker.setMemorySavesEnabledForTests(true);
    }

    @Test
    void extractsTextureHashFromReceivedProfileProperty() {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/abc123\"}}}";
        String encoded = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        assertEquals("abc123", PetSkinTracker.textureHash(encoded));
        assertEquals("", PetSkinTracker.textureHash("not base64"));
    }

    @Test
    void updatesAndRemovesTheCurrentPetsHeldItemFromReceivedChat() {
        PetTracker.updateFromChat("You summoned your Golden Dragon!");
        PetSkinTracker.onChat("Your pet is now holding Dwarf Turtle Shelmet.");
        assertEquals("Dwarf Turtle Shelmet",
                PetSkinTracker.currentDetails("Golden Dragon").heldItemId());

        PetSkinTracker.onChat("You removed Dwarf Turtle Shelmet from your pet!");
        assertEquals("", PetSkinTracker.currentDetails("Golden Dragon").heldItemId());
    }

    @Test
    void retainsAReceivedHeldItemAcrossSessionResetEvenAtMaxLevel() {
        PetTracker.updateFromTab(java.util.List.of(
                "Pet:", " [Lvl 100] Spinosaurus", " MAX LEVEL"));
        PetSkinTracker.onChat("Your pet is now holding Dwarf Turtle Shelmet.");

        PetSkinTracker.reset();

        assertEquals("Dwarf Turtle Shelmet",
                PetSkinTracker.currentDetails("Spinosaurus").heldItemId());
    }

    @Test
    void rejectsNearbyHeadsThatBelongToAnotherPetType() {
        assertEquals(true, PetSkinTracker.skinBelongsToPet("golden_dragon_ancient", "Golden Dragon"));
        assertEquals(false, PetSkinTracker.skinBelongsToPet("slime_spring", "Golden Dragon"));
        assertEquals(false, PetSkinTracker.skinBelongsToPet("jade_dragon_default", "Golden Dragon"));
    }

    @Test
    void parsesAuthoritativeSpecialTierAndInstanceFromPetInfo() {
        var info = PetSkinTracker.parsePetInfoJson("""
                {"type":"PHOENIX","active":true,"tier":"VERY SPECIAL",\
                 "uuid":"A1-B2-C3","heldItem":"PET_ITEM_LUCKY_CLOVER","exp":123.5}
                """);

        assertNotNull(info);
        assertEquals(PetTier.VERY_SPECIAL, info.tier());
        assertEquals("a1b2c3", info.instanceId());

        PetTracker.updateFromTab(java.util.List.of("Pet:", " [Lvl 100] Phoenix", " MAX LEVEL"));
        PetSkinTracker.rememberReceivedPetInfo(info, "");
        assertEquals(PetTier.VERY_SPECIAL, PetTracker.current().tier());
        assertEquals("a1b2c3", PetTracker.current().instanceId());
        assertEquals("PET_ITEM_LUCKY_CLOVER",
                PetSkinTracker.currentDetails("Phoenix").heldItemId());
    }

    @Test
    void isolatesReceivedDroneModsByConcretePetUuid() {
        PetTracker.updateFromTab(java.util.List.of(
                "Pet:", " [Lvl 1] Precursor Drone", " 0/100 XP (0%)"));
        var first = PetSkinTracker.parsePetInfoJson("""
                {"type":"PRECURSOR_DRONE","active":true,"tier":"LEGENDARY",\
                 "uuid":"drone-one","heldItem":"GRUNGLE"}
                """);
        var second = PetSkinTracker.parsePetInfoJson("""
                {"type":"PRECURSOR_DRONE","active":true,"tier":"LEGENDARY",\
                 "uuid":"drone-two","heldItem":"CONTRABAND"}
                """);
        PetSkinTracker.rememberReceivedPetInfo(first, "");
        assertEquals("GRUNGLE", PetSkinTracker.currentDetails("Precursor Drone").heldItemId());
        PetSkinTracker.rememberReceivedPetInfo(second, "");
        assertEquals("CONTRABAND", PetSkinTracker.currentDetails("Precursor Drone").heldItemId());

        PetTracker.noteMetadata("PRECURSOR_DRONE", PetTier.LEGENDARY, "drone-one");
        assertEquals("GRUNGLE", PetSkinTracker.currentDetails("Precursor Drone").heldItemId());
        assertTrue(PetSkinTracker.isKnownDroneMod("MINING_OFF_CAMERA"));
        assertEquals(false, PetSkinTracker.isKnownDroneMod("PET_ITEM_LUCKY_CLOVER"));
    }

    @Test
    void rejectsMalformedPetInfoInsteadOfInventingMetadata() {
        assertNull(PetSkinTracker.parsePetInfoJson("not-json"));
        assertNull(PetSkinTracker.parsePetInfoJson("{\"tier\":\"SPECIAL\"}"));
    }
}

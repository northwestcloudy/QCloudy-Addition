package cloudy.autume.addition.hud;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PetDisplayResourcesTest {
    @Test
    void bundlesAllCurrentPetAccessoryMetadata() throws Exception {
        try (var stream = getClass().getResourceAsStream(
                "/assets/qcloudy_addition/data/pet_accessories.json")) {
            assertNotNull(stream);
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(json.size() >= 87);
            for (String id : new String[]{"DWARF_TURTLE_SHELMET", "MINOS_RELIC",
                    "ANTIQUE_REMEDIES", "POIGNANT_LUCKY_CLOVER", "BARREL_OF_RICHES",
                    "GRUNGLE", "CONTRABAND", "MINING_OFF_CAMERA"}) {
                assertTrue(json.has(id), id);
                assertFalse(json.getAsJsonObject(id).get("name").getAsString().isBlank(), id);
            }
        }
    }

    @Test
    void keepsPetAndSkinProfilesDistinctByIdentity() {
        assertFalse(PetHeadResources.baseTexture("Jade Dragon").isBlank());
        assertFalse(PetHeadResources.baseTexture("Golden Dragon").isBlank());
        assertFalse(PetHeadResources.skinTexture("jade_dragon_baby").isBlank());
        assertFalse(PetHeadResources.skinTexture("golden_dragon_ancient").isBlank());
        assertNotEquals(PetHeadResources.baseTexture("Jade Dragon"),
                PetHeadResources.baseTexture("Golden Dragon"));
        assertNotEquals(PetHeadResources.skinTexture("jade_dragon_baby"),
                PetHeadResources.skinTexture("golden_dragon_ancient"));
    }

    @Test
    void mapsEveryPublishedBabySpinosaurusAnimationFrameToItsSkin() throws Exception {
        try (var stream = getClass().getResourceAsStream(
                "/assets/qcloudy_addition/data/pet_skin_texture_index.json")) {
            assertNotNull(stream);
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            long frames = json.entrySet().stream()
                    .filter(entry -> "spinosaurus_baby".equals(entry.getValue().getAsString()))
                    .count();
            assertTrue(frames >= 60, "Baby Spinosaurus frames: " + frames);
        }
    }

    @Test
    void metadataHasNoFirmamentRuntimeDependency() throws Exception {
        try (var stream = getClass().getResourceAsStream("/fabric.mod.json")) {
            assertNotNull(stream);
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertFalse(json.getAsJsonObject("depends").has("firmament"));
        }
    }
}

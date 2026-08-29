package cloudy.autume.addition.inventory;

import cloudy.autume.addition.config.ModConfig;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InventoryFeatureAvailabilityTest {
    @Test
    void cursorMemoryDependsOnlyOnItsOwnFeatureSwitch() {
        ModConfig.Inventory config = new ModConfig.Inventory();

        assertTrue(CursorPositionSaver.enabled(config));
        config.saveCursorPosition = false;
        assertFalse(CursorPositionSaver.enabled(config));
    }

    @Test
    void itemTimestampsDependOnlyOnTheirOwnSwitchAndSkyBlockContext() {
        ModConfig.Inventory config = new ModConfig.Inventory();

        assertTrue(ItemTimestampTooltip.enabled(config, true));
        assertFalse(ItemTimestampTooltip.enabled(config, false));
        config.itemTimestamps = false;
        assertFalse(ItemTimestampTooltip.enabled(config, true));
    }

    @Test
    void retiredFirmamentHandoffIsNotPartOfTheSavedInventorySchema() {
        assertFalse(Arrays.stream(ModConfig.Inventory.class.getFields())
                .anyMatch(field -> field.getName().equals("yieldToFirmament")));

        Gson gson = new Gson();
        ModConfig legacy = gson.fromJson("""
                {"inventory":{"yieldToFirmament":true,"itemTimestamps":false}}
                """, ModConfig.class);
        legacy.normalize();

        assertFalse(legacy.inventory.itemTimestamps);
        assertFalse(gson.toJson(legacy).contains("yieldToFirmament"));
    }
}

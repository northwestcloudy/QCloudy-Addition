package cloudy.autume.addition.weather;

import cloudy.autume.addition.compat.MinecraftClientCompat;
import cloudy.autume.addition.tracker.IslandArea;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Reads only bounded text from a weather-related menu already open locally. */
public final class IslandWeatherMenuObserver {
    private IslandWeatherMenuObserver() {
    }

    public static void observe(Minecraft client, IslandArea island) {
        if (client == null || client.player == null || island == null || !island.isWeatherIsland()) return;
        var screen = MinecraftClientCompat.screen(client);
        if (screen == null) return;
        String title = plain(screen.getTitle().getString());
        String lowerTitle = title.toLowerCase(Locale.ROOT);
        if (!lowerTitle.contains("weather") && !lowerTitle.contains("professor wynd")
                && !lowerTitle.contains("forecast")) return;

        for (var slot : client.player.containerMenu.slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            List<String> boundedItem = new ArrayList<>();
            boundedItem.add(title);
            boundedItem.add(plain(stack.getHoverName().getString()));
            var lore = stack.get(DataComponents.LORE);
            if (lore != null) {
                lore.lines().forEach(line -> boundedItem.add(plain(line.getString())));
            }
            if (IslandWeatherTracker.observe(island, WeatherSnapshot.EvidenceSource.MENU,
                    boundedItem)) return;
        }
    }

    private static String plain(String value) {
        String stripped = ChatFormatting.stripFormatting(value == null ? "" : value);
        return stripped == null ? "" : stripped.replace('\u00a0', ' ').trim();
    }
}

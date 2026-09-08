package cloudy.autume.addition.dungeon;

import cloudy.autume.addition.compat.MinecraftClientCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tracks only the local player's own advertised Dungeon Party Finder floor. */
final class DungeonPartyFinderFloorTracker {
    private static final Pattern FLOOR = Pattern.compile(
            "(?i)^Floor:?\\s*(?:Floor\\s*)?(Entrance|VII|VI|IV|V|III|II|I|[1-7])$");
    private static DungeonFloor currentFloor;

    private DungeonPartyFinderFloorTracker() { }

    static void update(Minecraft client) {
        if (client == null || client.player == null
                || !(MinecraftClientCompat.screen(client) instanceof AbstractContainerScreen<?> screen)) return;
        Item.TooltipContext context = client.level == null
                ? Item.TooltipContext.EMPTY : Item.TooltipContext.of(client.level);
        List<MenuEntry> entries = new ArrayList<>();
        for (var slot : screen.getMenu().slots) {
            if (slot.container instanceof Inventory || slot.getItem().isEmpty()) continue;
            List<String> tooltip = new ArrayList<>();
            try {
                slot.getItem().getTooltipLines(context, client.player, TooltipFlag.NORMAL)
                        .forEach(line -> tooltip.add(line.getString()));
            } catch (RuntimeException ignored) {
                continue;
            }
            entries.add(new MenuEntry(slot.index, slot.getItem().getHoverName().getString(), tooltip,
                    slot.getItem().is(Items.PLAYER_HEAD), slot.getItem().is(Items.BOOKSHELF)));
        }
        floorFromPartyFinder(screen.getTitle().getString(), entries, client.getUser().getName())
                .ifPresent(floor -> currentFloor = floor);
    }

    static DungeonFloor currentFloor() {
        return currentFloor;
    }

    static void observeSystemMessage(String raw) {
        String text = clean(raw).toLowerCase(Locale.ROOT);
        if (text.equals("you left the party.")
                || text.contains("the party was disbanded")
                || text.contains("you have been kicked from the party")
                || text.contains("you removed your group from the party finder")
                || text.contains("you are not currently in a party")) {
            currentFloor = null;
        }
    }

    static void reset() {
        currentFloor = null;
    }

    static Optional<DungeonFloor> floorFromPartyFinder(
            String title, List<MenuEntry> entries, String localPlayer) {
        String cleanTitle = clean(title).toLowerCase(Locale.ROOT);
        if (!cleanTitle.contains("party finder") || entries == null || entries.isEmpty()
                || !FriendName.valid(localPlayer)) return Optional.empty();

        int maximumSlot = entries.stream().mapToInt(MenuEntry::slot).max().orElse(-1);
        int bottomStart = Math.max(0, maximumSlot - 8);
        boolean hasDelist = entries.stream().anyMatch(entry -> entry.slot() >= bottomStart
                && isDelistControl(entry));
        if (!hasDelist) return Optional.empty();

        for (MenuEntry entry : entries) {
            if (entry.playerHead() && entry.slot() >= bottomStart) {
                Optional<DungeonFloor> floor = floorFromOwnPartyEntry(entry);
                if (floor.isPresent()) return floor;
            }
        }
        for (MenuEntry entry : entries) {
            if (!entry.playerHead() || !belongsToLocalPlayer(entry, localPlayer)) continue;
            Optional<DungeonFloor> floor = floorFromOwnPartyEntry(entry);
            if (floor.isPresent()) return floor;
        }
        return Optional.empty();
    }

    private static boolean belongsToLocalPlayer(MenuEntry entry, String localPlayer) {
        String local = localPlayer.toLowerCase(Locale.ROOT);
        String name = clean(entry.name()).toLowerCase(Locale.ROOT);
        if (name.equals(local + "'s party") || name.equals(local + "'s Party".toLowerCase(Locale.ROOT))) {
            return true;
        }
        for (String raw : entry.tooltip()) {
            String line = clean(raw).toLowerCase(Locale.ROOT);
            if (line.contains("you are in this party") || line.contains(local)) return true;
        }
        return false;
    }

    private static boolean isDelistControl(MenuEntry entry) {
        if (entry.bookshelf()) return true;
        String name = clean(entry.name()).toLowerCase(Locale.ROOT);
        return name.contains("delist")
                || name.contains("remove group")
                || name.contains("remove party");
    }

    private static Optional<DungeonFloor> floorFromOwnPartyEntry(MenuEntry entry) {
        boolean catacombs = false;
        boolean master = false;
        String floorValue = null;
        for (String raw : entry.tooltip()) {
            String line = clean(raw);
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.startsWith("dungeon:") && lower.contains("catacombs")) {
                catacombs = true;
                master = lower.contains("master mode") || lower.contains("mm ");
            }
            Matcher floor = FLOOR.matcher(line);
            if (floor.matches()) floorValue = floor.group(1);
        }
        if (!catacombs || floorValue == null) return Optional.empty();
        if (floorValue.equalsIgnoreCase("Entrance")) return Optional.of(new DungeonFloor("E"));
        int number = roman(floorValue.toUpperCase(Locale.ROOT));
        return number < 1 ? Optional.empty()
                : Optional.of(new DungeonFloor((master ? "M" : "F") + number));
    }

    private static int roman(String value) {
        return switch (value) {
            case "I", "1" -> 1;
            case "II", "2" -> 2;
            case "III", "3" -> 3;
            case "IV", "4" -> 4;
            case "V", "5" -> 5;
            case "VI", "6" -> 6;
            case "VII", "7" -> 7;
            default -> -1;
        };
    }

    private static String clean(String raw) {
        String stripped = net.minecraft.ChatFormatting.stripFormatting(raw == null ? "" : raw);
        return stripped == null ? "" : stripped.trim();
    }

    record MenuEntry(int slot, String name, List<String> tooltip,
                     boolean playerHead, boolean bookshelf) {
        MenuEntry {
            tooltip = tooltip == null ? List.of() : List.copyOf(tooltip);
        }
    }

    private static final class FriendName {
        private static boolean valid(String name) {
            return name != null && name.matches("[A-Za-z0-9_]{1,16}");
        }
    }
}

package cloudy.autume.addition.profile.ui;

import cloudy.autume.addition.profile.ProfileSectionId;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Safe vanilla fallbacks until the API exposes a stable item appearance descriptor. */
final class ProfileItemVisuals {
    private static final Map<String, ItemStack> CACHE = new ConcurrentHashMap<>();

    private ProfileItemVisuals() {
    }

    static ItemStack icon(ProfilePresentationMapper.ItemView item) {
        String key = item.itemId().toUpperCase(Locale.ROOT) + '|' + item.rarity();
        return CACHE.computeIfAbsent(key, ignored -> create(item.itemId())).copy();
    }

    static ItemStack navigationIcon(ProfileSectionId section) {
        return switch (section) {
            case OVERVIEW -> new ItemStack(Items.COMPASS);
            case GEAR -> dyedLeatherChestplate();
            case ACCESSORIES -> new ItemStack(Items.NETHER_STAR);
            case PETS -> new ItemStack(Items.PLAYER_HEAD);
            case INVENTORY -> new ItemStack(Items.CHEST);
            case SKILLS -> new ItemStack(Items.EXPERIENCE_BOTTLE);
            case SLAYER -> new ItemStack(Items.IRON_SWORD);
            case MINIONS -> new ItemStack(Items.DISPENSER);
            case BESTIARY -> new ItemStack(Items.WRITABLE_BOOK);
            case COLLECTIONS -> new ItemStack(Items.BOOKSHELF);
            case MINING -> new ItemStack(Items.DIAMOND_PICKAXE);
            case CRIMSON_ISLE -> new ItemStack(Items.BLAZE_POWDER);
            case RIFT -> new ItemStack(Items.ENDER_EYE);
            case MISC -> new ItemStack(Items.GOLDEN_HOE);
            case MUSEUM -> new ItemStack(Items.GOLD_BLOCK);
            case GARDEN -> new ItemStack(Items.WHEAT);
            case MARKET -> new ItemStack(Items.EMERALD);
        };
    }

    private static ItemStack dyedLeatherChestplate() {
        ItemStack stack = new ItemStack(Items.LEATHER_CHESTPLATE);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(0xD74444));
        return stack;
    }

    private static ItemStack create(String rawId) {
        String id = rawId == null ? "" : rawId.toUpperCase(Locale.ROOT);
        if (contains(id, "HELMET")) return new ItemStack(Items.DIAMOND_HELMET);
        if (contains(id, "CHESTPLATE")) return new ItemStack(Items.DIAMOND_CHESTPLATE);
        if (contains(id, "LEGGINGS")) return new ItemStack(Items.DIAMOND_LEGGINGS);
        if (contains(id, "BOOTS")) return new ItemStack(Items.DIAMOND_BOOTS);
        if (contains(id, "BOW", "TERMINATOR", "SHORTBOW")) return new ItemStack(Items.BOW);
        if (contains(id, "SWORD", "BLADE", "HYPERION", "SCYLLA", "ASTRAEA", "VALKYRIE")) {
            return new ItemStack(Items.DIAMOND_SWORD);
        }
        if (contains(id, "DRILL", "PICKAXE", "GAUNTLET")) return new ItemStack(Items.DIAMOND_PICKAXE);
        if (contains(id, "AXE")) return new ItemStack(Items.DIAMOND_AXE);
        if (contains(id, "HOE")) return new ItemStack(Items.DIAMOND_HOE);
        if (contains(id, "ROD")) return new ItemStack(Items.FISHING_ROD);
        if (contains(id, "POTION")) return new ItemStack(Items.POTION);
        if (contains(id, "BOOK", "ENCHANT")) return new ItemStack(Items.ENCHANTED_BOOK);
        if (contains(id, "PET", "HEAD", "MASK")) return new ItemStack(Items.PLAYER_HEAD);
        if (contains(id, "TALISMAN", "RING", "ARTIFACT", "ACCESSORY")) return new ItemStack(Items.NETHER_STAR);
        if (contains(id, "SHARD", "GEM", "CRYSTAL")) return new ItemStack(Items.AMETHYST_SHARD);
        if (contains(id, "SACK", "BACKPACK", "BAG")) return new ItemStack(Items.BUNDLE);
        if (contains(id, "COOKIE")) return new ItemStack(Items.COOKIE);
        if (contains(id, "DIAMOND")) return new ItemStack(Items.DIAMOND);
        if (contains(id, "EMERALD")) return new ItemStack(Items.EMERALD);
        if (contains(id, "GOLD")) return new ItemStack(Items.GOLD_INGOT);
        return new ItemStack(Items.CHEST);
    }

    private static boolean contains(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }
}

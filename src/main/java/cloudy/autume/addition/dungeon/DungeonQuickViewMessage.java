package cloudy.autume.addition.dungeon;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;

/** Builds the colored, hover-first QCA Player Quick View chat component. */
public final class DungeonQuickViewMessage {
    static final int TARGET_LINE_WIDTH = 330;
    private static final String TITLE = " QCA Player Quick View ";
    private static final String LINE_GLYPH = "─";

    private DungeonQuickViewMessage() { }

    public static Component build(DungeonQuickViewSnapshot snapshot, Font font) {
        Component styledTitle = Component.literal(TITLE)
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
        return build(snapshot, font::width, font.width(styledTitle),
                DungeonQuickViewMessage::itemHover);
    }

    static Component build(DungeonQuickViewSnapshot snapshot, ToIntFunction<String> width) {
        return build(snapshot, width, DungeonQuickViewMessage::itemHover);
    }

    static Component build(DungeonQuickViewSnapshot snapshot, ToIntFunction<String> width,
                           ItemHoverFactory hoverFactory) {
        return build(snapshot, width, width.applyAsInt(TITLE), hoverFactory);
    }

    private static Component build(DungeonQuickViewSnapshot snapshot, ToIntFunction<String> width,
                                   int styledTitleWidth, ItemHoverFactory hoverFactory) {
        Lines separators = separators(width, styledTitleWidth);
        MutableComponent output = Component.empty();
        output.append(Component.literal(separators.left()).withStyle(ChatFormatting.DARK_AQUA));
        output.append(Component.literal(TITLE).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        output.append(Component.literal(separators.right()).withStyle(ChatFormatting.DARK_AQUA));

        output.append("\n").append(label("Catacombs: "));
        output.append(stat(snapshot.catacombs(), true));

        output.append("\n").append(label("Secrets: "));
        output.append(value(snapshot.totalSecrets() == null ? "Missing"
                : String.format(Locale.ROOT, "%,d", snapshot.totalSecrets())));
        output.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));
        output.append(value(snapshot.averageSecrets() == null ? "Missing"
                : String.format(Locale.ROOT, "%.1f", snapshot.averageSecrets())));

        output.append("\n").append(label("Class: "));
        int classIndex = 0;
        for (DungeonQuickViewSnapshot.DungeonClass dungeonClass
                : DungeonQuickViewSnapshot.DungeonClass.values()) {
            if (classIndex++ > 0) output.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));
            DungeonQuickViewSnapshot.Stat stat = snapshot.classes().get(dungeonClass);
            MutableComponent classText = Component.literal(dungeonClass.label() + " " + level(stat))
                    .withStyle(style -> style.withColor(ChatFormatting.AQUA).withUnderlined(true));
            classText.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(xpHover(stat))));
            output.append(classText);
        }

        output.append("\n").append(label("Floor: "));
        String floor = snapshot.floor().id().isBlank() ? "Missing" : snapshot.floor().id();
        output.append(value(floor));
        output.append(Component.literal(" | Runs: ").withStyle(ChatFormatting.DARK_GRAY));
        output.append(value(snapshot.floor().runs() == null ? "Missing"
                : String.format(Locale.ROOT, "%,d", snapshot.floor().runs())));
        output.append(Component.literal(" | Fastest: ").withStyle(ChatFormatting.DARK_GRAY));
        output.append(value(formatTime(snapshot.floor().fastestMs())));

        output.append("\n").append(label("Armor:"));
        String[] fallbackNames = {"Helmet", "Chestplate", "Leggings", "Boots"};
        for (int index = 0; index < 4; index++) {
            output.append("\n");
            DungeonQuickViewSnapshot.Item item = index < snapshot.armor().size()
                    ? snapshot.armor().get(index) : null;
            if (item == null) {
                output.append(Component.literal(fallbackNames[index] + ": Missing")
                        .withStyle(ChatFormatting.RED));
            } else {
                output.append(itemComponent(item, ItemKind.armor(index), hoverFactory));
            }
        }

        output.append("\n").append(label("Weapons: "));
        output.append(presence("Withered Blade", snapshot.witherBlade(), ItemKind.SWORD, hoverFactory));
        output.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));
        output.append(presence("Terminator", snapshot.terminator(), ItemKind.BOW, hoverFactory));

        output.append("\n").append(label("Pets: "));
        output.append(presence("GDragon", snapshot.goldenDragon(), ItemKind.PET, hoverFactory));
        output.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));
        output.append(presence("EDragon", snapshot.enderDragon(), ItemKind.PET, hoverFactory));

        output.append("\n").append(label("Magical Power: "));
        output.append(value(snapshot.magicalPower() == null ? "Missing"
                : String.format(Locale.ROOT, "%,d", snapshot.magicalPower())));

        if (snapshot.stale() || !snapshot.failure().isBlank()) {
            Component detail = Component.literal(snapshot.failure().isBlank()
                    ? "Cached data is stale." : snapshot.failure()).withStyle(ChatFormatting.RED);
            output.append(Component.literal(" ⚠").withStyle(style -> style
                    .withColor(ChatFormatting.YELLOW)
                    .withHoverEvent(new HoverEvent.ShowText(detail))));
        }

        output.append("\n").append(Component.literal(separators.bottom()).withStyle(ChatFormatting.DARK_AQUA));
        output.append("\n").append(Component.literal("CLICK HERE TO KICK THE PLAYER OUT")
                .withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true).withUnderlined(true)
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(
                                "Click to run /party kick " + snapshot.playerName()).withStyle(ChatFormatting.RED)))
                        .withClickEvent(new ClickEvent.RunCommand("/party kick " + snapshot.playerName()))));
        return output;
    }

    static Lines separators(ToIntFunction<String> width) {
        return separators(width, width.applyAsInt(TITLE));
    }

    static Lines separators(ToIntFunction<String> width, int styledTitleWidth) {
        int glyph = Math.max(1, width.applyAsInt(LINE_GLYPH));
        int title = Math.max(0, styledTitleWidth);
        int available = Math.max(glyph * 2, TARGET_LINE_WIDTH - title);
        int leftCount = Math.max(1, Math.round(available / (2.0f * glyph)));
        int rightCount = Math.max(1, Math.round((TARGET_LINE_WIDTH - title - leftCount * glyph)
                / (float) glyph));
        String left = LINE_GLYPH.repeat(leftCount);
        String right = LINE_GLYPH.repeat(rightCount);
        int topWidth = width.applyAsInt(left) + title + width.applyAsInt(right);
        int bottomCount = Math.max(2, Math.round(topWidth / (float) glyph));
        return new Lines(left, right, LINE_GLYPH.repeat(bottomCount), topWidth,
                width.applyAsInt(LINE_GLYPH.repeat(bottomCount)));
    }

    private static Component label(String text) {
        return Component.literal(text).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
    }

    private static Component value(String text) {
        return Component.literal(text).withStyle("Missing".equals(text)
                ? ChatFormatting.RED : ChatFormatting.GRAY);
    }

    private static Component stat(DungeonQuickViewSnapshot.Stat stat, boolean oneDecimal) {
        String shown = stat == null || stat.level() == null ? "Missing"
                : String.format(Locale.ROOT, oneDecimal ? "%.1f" : "%.0f", stat.level());
        return value(shown).copy().withStyle(style -> style.withHoverEvent(
                new HoverEvent.ShowText(xpHover(stat))));
    }

    private static String level(DungeonQuickViewSnapshot.Stat stat) {
        return stat == null || stat.level() == null ? "Missing"
                : String.format(Locale.ROOT, "%.1f", stat.level());
    }

    private static Component xpHover(DungeonQuickViewSnapshot.Stat stat) {
        return Component.literal("XP: " + (stat == null || stat.xp() == null ? "Missing"
                : String.format(Locale.ROOT, "%,.0f", stat.xp())))
                .withStyle(stat == null || stat.xp() == null ? ChatFormatting.RED : ChatFormatting.AQUA);
    }

    private static Component presence(String label, DungeonQuickViewSnapshot.Presence presence,
                                      ItemKind kind, ItemHoverFactory hoverFactory) {
        String suffix = switch (presence.state()) {
            case PRESENT -> " ✔";
            case ABSENT -> " ✖";
            case MISSING -> " Missing";
        };
        ChatFormatting color = switch (presence.state()) {
            case PRESENT -> ChatFormatting.GREEN;
            case ABSENT, MISSING -> ChatFormatting.RED;
        };
        MutableComponent text = Component.literal(label + suffix).withStyle(color);
        if (presence.item() != null) {
            text.withStyle(style -> style.withHoverEvent(hoverFactory.create(presence.item(), kind)));
        }
        return text;
    }

    private static Component itemComponent(DungeonQuickViewSnapshot.Item item, ItemKind kind,
                                           ItemHoverFactory hoverFactory) {
        return legacy(item.name()).withStyle(style -> style.withHoverEvent(hoverFactory.create(item, kind)));
    }

    private static HoverEvent itemHover(DungeonQuickViewSnapshot.Item item,
                                        ItemKind kind) {
        ItemStack stack = new ItemStack(kind.fallback());
        stack.set(DataComponents.CUSTOM_NAME, legacy(item.name()));
        List<Component> lore = new ArrayList<>();
        for (String line : item.lore()) lore.add(legacy(line));
        if (!lore.isEmpty()) stack.set(DataComponents.LORE, new ItemLore(lore));
        return new HoverEvent.ShowItem(ItemStackTemplate.fromNonEmptyStack(stack));
    }

    static MutableComponent legacy(String raw) {
        String text = raw == null ? "" : raw;
        MutableComponent result = Component.empty();
        Style style = Style.EMPTY;
        int segmentStart = 0;
        for (int index = 0; index + 1 < text.length(); index++) {
            if (text.charAt(index) != ChatFormatting.PREFIX_CODE) continue;
            if (index > segmentStart) result.append(Component.literal(text.substring(segmentStart, index)).setStyle(style));
            ChatFormatting formatting = ChatFormatting.getByCode(text.charAt(index + 1));
            if (formatting != null) style = style.applyLegacyFormat(formatting);
            index++;
            segmentStart = index + 1;
        }
        if (segmentStart < text.length()) result.append(Component.literal(text.substring(segmentStart)).setStyle(style));
        return result;
    }

    private static String formatTime(Long milliseconds) {
        if (milliseconds == null) return "Missing";
        long minutes = milliseconds / 60_000;
        long seconds = (milliseconds / 1_000) % 60;
        long millis = milliseconds % 1_000;
        return String.format(Locale.ROOT, "%d:%02d.%03d", minutes, seconds, millis);
    }

    record Lines(String left, String right, String bottom, int topWidth, int bottomWidth) { }

    @FunctionalInterface
    interface ItemHoverFactory {
        HoverEvent create(DungeonQuickViewSnapshot.Item item, ItemKind kind);
    }

    enum ItemKind {
        HELMET, CHESTPLATE, LEGGINGS, BOOTS, SWORD, BOW, PET;

        static ItemKind armor(int index) {
            return switch (index) {
                case 0 -> HELMET;
                case 1 -> CHESTPLATE;
                case 2 -> LEGGINGS;
                default -> BOOTS;
            };
        }

        net.minecraft.world.item.Item fallback() {
            return switch (this) {
                case HELMET -> Items.DIAMOND_HELMET;
                case CHESTPLATE -> Items.DIAMOND_CHESTPLATE;
                case LEGGINGS -> Items.DIAMOND_LEGGINGS;
                case BOOTS -> Items.DIAMOND_BOOTS;
                case SWORD -> Items.DIAMOND_SWORD;
                case BOW -> Items.BOW;
                case PET -> Items.PLAYER_HEAD;
            };
        }
    }
}

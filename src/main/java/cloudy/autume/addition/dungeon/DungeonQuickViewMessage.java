package cloudy.autume.addition.dungeon;

import cloudy.autume.addition.dungeon.requirements.DungeonRequirement;
import cloudy.autume.addition.dungeon.requirements.DungeonRequirementCriterion;
import cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvaluation;
import cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidenceValue;
import cloudy.autume.addition.dungeon.requirements.RequirementFinding;
import cloudy.autume.addition.i18n.ModText;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;

/** Builds the colored, hover-first QCA Player Quick View chat component. */
public final class DungeonQuickViewMessage {
    static final int TARGET_LINE_WIDTH = 330;
    private static final String TITLE = " QCA Player Quick View ";
    private static final String LINE_GLYPH = "─";
    private static final DungeonQuickViewSnapshot.DungeonClass[] ODIN_CLASS_ORDER = {
            DungeonQuickViewSnapshot.DungeonClass.ARCHER,
            DungeonQuickViewSnapshot.DungeonClass.BERSERK,
            DungeonQuickViewSnapshot.DungeonClass.HEALER,
            DungeonQuickViewSnapshot.DungeonClass.MAGE,
            DungeonQuickViewSnapshot.DungeonClass.TANK
    };

    private DungeonQuickViewMessage() { }

    public static Component build(DungeonQuickViewSnapshot snapshot, Font font) {
        Component styledTitle = Component.literal(TITLE)
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
        int boldLineGlyphWidth = font.width(Component.literal(LINE_GLYPH)
                .withStyle(ChatFormatting.BOLD));
        return build(snapshot, font::width, font.width(styledTitle), boldLineGlyphWidth,
                DungeonQuickViewMessage::itemHover, null);
    }

    public static Component buildWithUnknowns(DungeonQuickViewSnapshot snapshot,
                                              DungeonRequirementEvaluation evaluation,
                                              Font font) {
        Component styledTitle = Component.literal(TITLE)
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
        int boldLineGlyphWidth = font.width(Component.literal(LINE_GLYPH)
                .withStyle(ChatFormatting.BOLD));
        return build(snapshot, font::width, font.width(styledTitle), boldLineGlyphWidth,
                DungeonQuickViewMessage::itemHover, evaluation);
    }

    /** A request failure is not player data, so it must never be rendered as an all-Missing profile card. */
    public static Component unavailable(String playerName, String reason) {
        Component detail = Component.literal(reason == null || reason.isBlank()
                ? "Dungeon profile data is unavailable." : reason).withStyle(ChatFormatting.RED);
        MutableComponent output = Component.literal("[QCA] ")
                .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD);
        output.append(Component.literal("Dungeon Quick View unavailable for ")
                .withStyle(ChatFormatting.GRAY));
        output.append(Component.literal(playerName).withStyle(ChatFormatting.AQUA));
        output.append(Component.literal(" ⚠").withStyle(style -> style
                .withColor(ChatFormatting.YELLOW)
                .withHoverEvent(new HoverEvent.ShowText(detail))));
        return output;
    }

    static Component build(DungeonQuickViewSnapshot snapshot, ToIntFunction<String> width) {
        return build(snapshot, width, DungeonQuickViewMessage::itemHover);
    }

    static Component buildWithUnknowns(DungeonQuickViewSnapshot snapshot,
                                       DungeonRequirementEvaluation evaluation,
                                       ToIntFunction<String> width) {
        return build(snapshot, width, DungeonQuickViewMessage::itemHover, evaluation);
    }

    static Component buildWithUnknowns(DungeonQuickViewSnapshot snapshot,
                                       DungeonRequirementEvaluation evaluation,
                                       ToIntFunction<String> width,
                                       ItemHoverFactory hoverFactory) {
        return build(snapshot, width, hoverFactory, evaluation);
    }

    static Component failures(String playerName, String floor,
                              DungeonRequirementEvaluation evaluation) {
        return failures(playerName, floor, evaluation, "");
    }

    static Component failures(String playerName, String floor,
                              DungeonRequirementEvaluation evaluation,
                              String actionError) {
        MutableComponent output = Component.literal("[QCA] ")
                .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD);
        output.append(Component.literal(ModText.get(
                "dungeon.requirements.failed_header", playerName, floor))
                .withStyle(ChatFormatting.RED));
        if (evaluation != null) {
            for (RequirementFinding finding : evaluation.failures()) {
                output.append("\n").append(Component.literal("- ")
                        .withStyle(ChatFormatting.DARK_GRAY));
                output.append(findingText(finding, floor).withStyle(ChatFormatting.RED));
            }
            appendUnknowns(output, evaluation, floor, actionError);
        } else if (actionError != null && !actionError.isBlank()) {
            appendUnknowns(output, null, floor, actionError);
        }
        return output;
    }

    static Component build(DungeonQuickViewSnapshot snapshot, ToIntFunction<String> width,
                           ItemHoverFactory hoverFactory) {
        return build(snapshot, width, hoverFactory, null);
    }

    private static Component build(DungeonQuickViewSnapshot snapshot, ToIntFunction<String> width,
                                   ItemHoverFactory hoverFactory,
                                   DungeonRequirementEvaluation evaluation) {
        int lineGlyphWidth = Math.max(1, width.applyAsInt(LINE_GLYPH));
        return build(snapshot, width, width.applyAsInt(TITLE), lineGlyphWidth + 1,
                hoverFactory, evaluation);
    }

    private static Component build(DungeonQuickViewSnapshot snapshot, ToIntFunction<String> width,
                                   int styledTitleWidth, int boldLineGlyphWidth,
                                   ItemHoverFactory hoverFactory,
                                   DungeonRequirementEvaluation evaluation) {
        Lines separators = separators(width, styledTitleWidth, boldLineGlyphWidth);
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
                : formatAverageSecrets(snapshot.averageSecrets())));

        output.append("\n").append(label("Classes: "));
        int classIndex = 0;
        for (DungeonQuickViewSnapshot.DungeonClass dungeonClass : ODIN_CLASS_ORDER) {
            if (classIndex++ > 0) output.append(Component.literal("/").withStyle(ChatFormatting.DARK_GRAY));
            DungeonQuickViewSnapshot.Stat stat = snapshot.classes().get(dungeonClass);
            ChatFormatting color = stat == null || stat.level() == null
                    ? ChatFormatting.RED : classColor(dungeonClass);
            MutableComponent classText = Component.literal(level(stat)).withStyle(style -> style
                    .withColor(color)
                    .withHoverEvent(new HoverEvent.ShowText(classHover(dungeonClass, stat))));
            output.append(classText);
        }
        output.append(Component.literal(" | Class Average: ").withStyle(ChatFormatting.DARK_GRAY));
        output.append(value(classAverage(snapshot)));

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

        output.append("\n").append(label("Highest Magical Power: "));
        output.append(value(snapshot.magicalPower() == null ? "Missing"
                : String.format(Locale.ROOT, "%,d", snapshot.magicalPower())));

        if (snapshot.stale() || !snapshot.failure().isBlank()) {
            Component detail = Component.literal(snapshot.failure().isBlank()
                    ? "Cached data is stale." : snapshot.failure()).withStyle(ChatFormatting.RED);
            output.append(Component.literal(" ⚠").withStyle(style -> style
                    .withColor(ChatFormatting.YELLOW)
                    .withHoverEvent(new HoverEvent.ShowText(detail))));
        }

        appendUnknowns(output, evaluation, snapshot.floor().id(), "");

        output.append("\n").append(Component.literal(separators.bottomNormal())
                .withStyle(ChatFormatting.DARK_AQUA));
        output.append(Component.literal(separators.bottomBold())
                .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD));
        output.append("\n").append(Component.literal("CLICK HERE TO KICK THE PLAYER OUT")
                .withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true).withUnderlined(true)
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(
                                "Click to run /party kick " + snapshot.playerName()).withStyle(ChatFormatting.RED)))
                        .withClickEvent(new ClickEvent.RunCommand("/party kick " + snapshot.playerName()))));
        return output;
    }

    private static MutableComponent findingText(RequirementFinding finding, String floor) {
        String label = requirementLabel(finding.requirement(), floor);
        DungeonRequirementEvidenceValue evidence = finding.evidence();
        DungeonRequirementCriterion criterion = finding.criterion();
        String detail;
        if (evidence instanceof DungeonRequirementEvidenceValue.LongValue actual
                && criterion instanceof DungeonRequirementCriterion.LongThreshold required) {
            String actualText = finding.requirement() == DungeonRequirement.MAXIMUM_FASTEST_COMPLETION
                    ? formatRequirementActualTime(actual.value())
                    : String.format(Locale.ROOT, "%,d", actual.value());
            String requiredText = finding.requirement() == DungeonRequirement.MAXIMUM_FASTEST_COMPLETION
                    ? formatRequirementTime(required.value()) : String.format(Locale.ROOT, "%,d", required.value());
            String key = required.comparison() == DungeonRequirementCriterion.Comparison.AT_MOST
                    ? "dungeon.requirements.required_at_most"
                    : "dungeon.requirements.required_at_least";
            detail = actualText + " / " + ModText.get(key, requiredText);
        } else if (evidence instanceof DungeonRequirementEvidenceValue.DecimalValue actual
                && criterion instanceof DungeonRequirementCriterion.DecimalThreshold required) {
            detail = formatDecimal(actual.value()) + " / "
                    + ModText.get("dungeon.requirements.required_at_least",
                    formatDecimal(required.value()));
        } else if (evidence instanceof DungeonRequirementEvidenceValue.DuplicateClass duplicate) {
            detail = ModText.get("dungeon.requirements.duplicate_with",
                    duplicate.newcomerClass().displayName(),
                    String.join(", ", duplicate.conflictingPlayers()));
        } else {
            detail = ModText.get("dungeon.requirements.not_owned") + " / "
                    + ModText.get("dungeon.requirements.required_present");
        }
        return Component.literal(label + ": " + detail);
    }

    private static String requirementLabel(DungeonRequirement requirement, String floor) {
        String key = switch (requirement) {
            case MINIMUM_FLOOR_COMPLETIONS -> "dungeon.requirements.rule.floor_completions";
            case DISALLOW_DUPLICATE_CLASS -> "dungeon.requirements.rule.duplicate_class";
            case MAXIMUM_FASTEST_COMPLETION -> "dungeon.requirements.rule.fastest_completion";
            case MINIMUM_AVERAGE_SECRETS -> "dungeon.requirements.rule.average_secrets";
            case MINIMUM_MAGICAL_POWER -> "dungeon.requirements.rule.magical_power";
            case REQUIRE_WITHER_BLADE -> "dungeon.requirements.rule.wither_blade";
            case REQUIRE_TERMINATOR -> "dungeon.requirements.rule.terminator";
            case REQUIRE_GOLDEN_DRAGON -> "dungeon.requirements.rule.golden_dragon";
            case REQUIRE_ENDER_DRAGON -> "dungeon.requirements.rule.ender_dragon";
        };
        return requirement == DungeonRequirement.MINIMUM_FLOOR_COMPLETIONS
                ? ModText.get(key, floor) : ModText.get(key);
    }

    private static void appendUnknowns(MutableComponent output,
                                       DungeonRequirementEvaluation evaluation,
                                       String floor,
                                       String actionError) {
        List<RequirementFinding> unknowns = evaluation == null ? List.of() : evaluation.unknowns();
        boolean hasActionError = actionError != null && !actionError.isBlank();
        if (unknowns.isEmpty() && !hasActionError) return;
        output.append("\n").append(Component.literal(
                ModText.get("dungeon.requirements.unknown_header"))
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        for (RequirementFinding finding : unknowns) {
            output.append("\n").append(Component.literal("- ")
                    .withStyle(ChatFormatting.DARK_GRAY));
            output.append(Component.literal(requirementLabel(finding.requirement(), floor) + ": ")
                    .withStyle(ChatFormatting.YELLOW));
            output.append(Component.literal(unknownReason(finding.unavailableReason()))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (hasActionError) {
            output.append("\n").append(Component.literal("- ")
                    .withStyle(ChatFormatting.DARK_GRAY));
            output.append(Component.literal(ModText.get(
                            "dungeon.requirements.automatic_action") + ": ")
                    .withStyle(ChatFormatting.YELLOW));
            output.append(Component.literal(unknownReason(actionError))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private static String unknownReason(String reason) {
        String raw = reason == null ? "" : reason.trim();
        String[] details = raw.split("\\|", -1);
        if (details.length >= 3 && details[0].equals("PARTY_ROSTER_COUNT_MISMATCH")) {
            return ModText.get("dungeon.requirements.unknown.party_roster_count",
                    details[1], details[2]);
        }
        if (details.length >= 3 && details[0].equals("PARTY_ROSTER_IDENTITY_MISMATCH")) {
            return ModText.get("dungeon.requirements.unknown.party_roster_identity",
                    displayNames(details[1]), displayNames(details[2]));
        }
        if (details.length >= 2 && details[0].equals("PARTY_CLASS_IDENTITIES_UNAVAILABLE")) {
            return ModText.get("dungeon.requirements.unknown.party_class_identities",
                    displayNames(details[1]));
        }
        if (details.length >= 2 && details[0].equals("PARTY_CLASSES_MISSING")) {
            return ModText.get("dungeon.requirements.unknown.party_classes_missing",
                    displayNames(details[1]));
        }
        String normalized = raw.toUpperCase(Locale.ROOT)
                .replace(' ', '_');
        String key = switch (normalized) {
            case "SOURCE_STALE", "PROFILE_EVIDENCE_IS_STALE", "DATA_IS_STALE" ->
                    "dungeon.requirements.unknown.stale";
            case "INVENTORY_INCOMPLETE" -> "dungeon.requirements.unknown.inventory_incomplete";
            case "PETS_UNAVAILABLE" -> "dungeon.requirements.unknown.pets_unavailable";
            case "PETS_INCOMPLETE" -> "dungeon.requirements.unknown.pets_incomplete";
            case "PARTY_CLASSES_UNAVAILABLE", "PARTY_CLASSES_NOT_EVALUATED_YET",
                    "PARTY_CLASSES_INCOMPLETE", "PARTY_CLASS_DATA_IS_INCOMPLETE" ->
                    "dungeon.requirements.unknown.party_classes_incomplete";
            case "SCOPE_MISMATCH", "CALCULATION_UNAVAILABLE" ->
                    "dungeon.requirements.unknown.calculation_unavailable";
            case "NO_VALID_COMPLETION_TIME", "NO_COMPLETION_TIME" ->
                    "dungeon.requirements.unknown.no_completion_time";
            case "PARTY_AUTHORITY_UNAVAILABLE" ->
                    "dungeon.requirements.unknown.party_authority";
            case "PARTY_NOT_CONFIRMED" ->
                    "dungeon.requirements.unknown.party_not_confirmed";
            case "LOCAL_PLAYER_NOT_PARTY_LEADER" ->
                    "dungeon.requirements.unknown.not_party_leader";
            case "TARGET_NOT_IN_PARTY" ->
                    "dungeon.requirements.unknown.target_not_in_party";
            case "NEWCOMER_CLASS_MISSING" ->
                    "dungeon.requirements.unknown.newcomer_class_missing";
            case "NO_COMPLETED_DUNGEON_RUNS" ->
                    "dungeon.requirements.unknown.no_completed_runs";
            case "AVERAGE_SECRETS_UNAVAILABLE" ->
                    "dungeon.requirements.unknown.average_secrets_unavailable";
            case "PLAYER_IDENTITY_EVIDENCE_DOES_NOT_MATCH", "IDENTITY_MISMATCH" ->
                    "dungeon.requirements.unknown.identity_mismatch";
            case "DUNGEON_FLOOR_EVIDENCE_DOES_NOT_MATCH", "FLOOR_MISMATCH",
                    "FLOOR_NOT_REQUESTED" -> "dungeon.requirements.unknown.floor_mismatch";
            case "BACKEND_REQUIREMENTS_EVIDENCE_VERSION_IS_UNSUPPORTED",
                    "BACKEND_REQUIREMENTS_EVIDENCE_IS_UNAVAILABLE",
                    "REQUIREMENTS_EVIDENCE_IS_UNAVAILABLE",
                    "REQUIREMENTS_EVIDENCE_IS_INVALID", "UNSUPPORTED_EVIDENCE" ->
                    "dungeon.requirements.unknown.unsupported_evidence";
            case "PROFILE_SELECTION_UNCERTAIN", "PROFILE_ID_MISSING" ->
                    "dungeon.requirements.unknown.profile_uncertain";
            default -> "dungeon.requirements.unknown.missing_value";
        };
        return ModText.get(key);
    }

    private static String displayNames(String encoded) {
        return encoded == null || encoded.isBlank()
                ? ModText.get("dungeon.requirements.unknown.none")
                : encoded.replace(",", ", ");
    }

    static Lines separators(ToIntFunction<String> width) {
        return separators(width, width.applyAsInt(TITLE));
    }

    static Lines separators(ToIntFunction<String> width, int styledTitleWidth) {
        int glyph = Math.max(1, width.applyAsInt(LINE_GLYPH));
        return separators(width, styledTitleWidth, glyph + 1);
    }

    static Lines separators(ToIntFunction<String> width, int styledTitleWidth,
                            int boldLineGlyphWidth) {
        int glyph = Math.max(1, width.applyAsInt(LINE_GLYPH));
        int boldGlyph = Math.max(1, boldLineGlyphWidth);
        int title = Math.max(0, styledTitleWidth);
        int available = Math.max(glyph * 2, TARGET_LINE_WIDTH - title);
        int leftCount = Math.max(1, Math.round(available / (2.0f * glyph)));
        int rightCount = Math.max(1, Math.round((TARGET_LINE_WIDTH - title - leftCount * glyph)
                / (float) glyph));
        String left = LINE_GLYPH.repeat(leftCount);
        String right = LINE_GLYPH.repeat(rightCount);
        int topWidth = width.applyAsInt(left) + title + width.applyAsInt(right);
        BottomLine bottom = closestBottomLine(topWidth, glyph, boldGlyph);
        return new Lines(left, right,
                LINE_GLYPH.repeat(bottom.normalCount()),
                LINE_GLYPH.repeat(bottom.boldCount()),
                topWidth, bottom.width());
    }

    private static BottomLine closestBottomLine(int targetWidth, int glyphWidth,
                                                int boldGlyphWidth) {
        BottomLine best = new BottomLine(2, 0, glyphWidth * 2);
        int bestDifference = Math.abs(best.width() - targetWidth);
        int narrowestGlyph = Math.max(1, Math.min(glyphWidth, boldGlyphWidth));
        int maximumCount = Math.max(2, targetWidth / narrowestGlyph + 2);

        for (int totalCount = 2; totalCount <= maximumCount; totalCount++) {
            for (int boldCount = 0; boldCount <= totalCount; boldCount++) {
                int normalCount = totalCount - boldCount;
                int candidateWidth = normalCount * glyphWidth + boldCount * boldGlyphWidth;
                int difference = Math.abs(candidateWidth - targetWidth);
                if (difference < bestDifference
                        || difference == bestDifference && boldCount < best.boldCount()) {
                    best = new BottomLine(normalCount, boldCount, candidateWidth);
                    bestDifference = difference;
                }
                if (bestDifference == 0 && best.boldCount() == 0) return best;
            }
        }
        return best;
    }

    private static Component label(String text) {
        return Component.literal(text).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
    }

    private static Component value(String text) {
        return Component.literal(text).withStyle("Missing".equals(text)
                ? ChatFormatting.RED : ChatFormatting.GRAY);
    }

    /** Round-trip-safe human-facing form for validated decimal requirement values. */
    static String formatDecimal(double number) {
        if (!Double.isFinite(number)) throw new IllegalArgumentException("Decimal must be finite");
        String plain = BigDecimal.valueOf(number).stripTrailingZeros().toPlainString();
        return plain.indexOf('.') >= 0 ? plain : plain + ".0";
    }

    /** Compact profile-card form; admission comparisons retain the original value. */
    static String formatAverageSecrets(double number) {
        if (!Double.isFinite(number)) throw new IllegalArgumentException("Decimal must be finite");
        return String.format(Locale.ROOT, "%.1f", number);
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

    private static String classAverage(DungeonQuickViewSnapshot snapshot) {
        double total = 0.0;
        for (DungeonQuickViewSnapshot.DungeonClass dungeonClass : ODIN_CLASS_ORDER) {
            DungeonQuickViewSnapshot.Stat stat = snapshot.classes().get(dungeonClass);
            if (stat == null || stat.level() == null || !Double.isFinite(stat.level())) return "Missing";
            total += stat.level();
        }
        return String.format(Locale.ROOT, "%.1f", total / ODIN_CLASS_ORDER.length);
    }

    private static ChatFormatting classColor(DungeonQuickViewSnapshot.DungeonClass dungeonClass) {
        return switch (dungeonClass) {
            case ARCHER -> ChatFormatting.GOLD;
            case BERSERK -> ChatFormatting.DARK_RED;
            case HEALER -> ChatFormatting.LIGHT_PURPLE;
            case MAGE -> ChatFormatting.AQUA;
            case TANK -> ChatFormatting.DARK_GREEN;
        };
    }

    private static Component classHover(DungeonQuickViewSnapshot.DungeonClass dungeonClass,
                                        DungeonQuickViewSnapshot.Stat stat) {
        MutableComponent hover = Component.literal(className(dungeonClass) + " Level")
                .withStyle(classColor(dungeonClass));
        hover.append("\n").append(xpHover(stat));
        return hover;
    }

    private static String className(DungeonQuickViewSnapshot.DungeonClass dungeonClass) {
        return switch (dungeonClass) {
            case ARCHER -> "Archer";
            case BERSERK -> "Berserk";
            case HEALER -> "Healer";
            case MAGE -> "Mage";
            case TANK -> "Tank";
        };
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

    private static String formatRequirementTime(long milliseconds) {
        long minutes = milliseconds / 60_000;
        long seconds = (milliseconds / 1_000) % 60;
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    private static String formatRequirementActualTime(long milliseconds) {
        long minutes = milliseconds / 60_000;
        long seconds = (milliseconds / 1_000) % 60;
        long millis = milliseconds % 1_000;
        return String.format(Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, millis);
    }

    record Lines(String left, String right, String bottomNormal, String bottomBold,
                 int topWidth, int bottomWidth) {
        String bottom() { return bottomNormal + bottomBold; }
    }

    private record BottomLine(int normalCount, int boldCount, int width) { }

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

package cloudy.autume.addition.hud;

import cloudy.autume.addition.config.ConfigManager;
import cloudy.autume.addition.config.ModConfig;
import cloudy.autume.addition.hunting.HuntingTextParser;
import cloudy.autume.addition.hunting.HuntingTracker;
import cloudy.autume.addition.hunting.SafariEagleState;
import cloudy.autume.addition.hunting.SafariEagleTracker;
import cloudy.autume.addition.i18n.ModText;
import cloudy.autume.addition.tracker.IslandArea;
import cloudy.autume.addition.tracker.LocationTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/** One responsive panel shared by Torrhus Canyon and Critter Safari features. */
public final class HuntingHudRenderer {
    public static final int WIDTH = 296;
    private static final int PADDING = 7;
    private static final int LINE_HEIGHT = 10;

    private HuntingHudRenderer() {
    }

    public static void render(GuiGraphicsExtractor graphics) {
        ModConfig.PanelStyle style = ConfigManager.get().hudStyle.hunting;
        List<Line> lines = lines();
        if (lines.isEmpty()) return;
        int height = height(lines, style);
        HudPanel.background(graphics, 0, 0, WIDTH, height, style);
        int y = 5;
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            Line line = lines.get(lineIndex);
            List<FormattedCharSequence> wrapped = wrap(line, style);
            for (FormattedCharSequence part : wrapped) {
                graphics.text(Minecraft.getInstance().font, part, PADDING + line.indent, y,
                        lineIndex == 0 ? 0xFF000000 | style.titleColor : line.color, style.textShadow);
                y += LINE_HEIGHT;
            }
            if (line.section) y += 2;
        }
    }

    public static int currentHeight() {
        return height(lines(), ConfigManager.get().hudStyle.hunting);
    }

    public static boolean loaded() {
        return (LocationTracker.area() == IslandArea.TORRHUS_CANYON
                || LocationTracker.area() == IslandArea.GALATEA
                || LocationTracker.area() == IslandArea.CRITTER_SAFARI)
                && !lines().isEmpty();
    }

    private static List<Line> lines() {
        return switch (LocationTracker.area()) {
            case CRITTER_SAFARI -> safariLines();
            case GALATEA -> galateaLines();
            case TORRHUS_CANYON -> torrhusLines();
            default -> List.of();
        };
    }

    private static List<Line> torrhusLines() {
        var config = ConfigManager.get().hunting;
        List<Line> content = new ArrayList<>();
        if (config.torrhusTracker) {
            List<Line> chapterLines = new ArrayList<>();
            var chapter = HuntingTracker.chapter();
            if (config.showChapter) addValueIfPresent(chapterLines, ModText.get("hud.hunting.chapter_value"), chapter.chapter());
            if (config.showCurrentTask) addValueIfPresent(chapterLines, ModText.get("hud.hunting.task"), chapter.task());
            if (config.showTaskProgress) addValueIfPresent(chapterLines, ModText.get("hud.hunting.progress"), chapter.progress());
            if (config.showCompletedTasks) addValueIfPresent(chapterLines, ModText.get("hud.hunting.completed"), chapter.completed());
            if (config.showChapterTotalProgress) addValueIfPresent(chapterLines, ModText.get("hud.hunting.total_progress"), chapter.totalProgress());
            if (config.showNextUnlock) addValueIfPresent(chapterLines, ModText.get("hud.hunting.next_unlock"), chapter.nextUnlock());
            appendSectionIfPresent(content, ModText.get("hud.hunting.chapter"), 0xFFFFD45A, chapterLines);
            appendTorrhusResources(content, config);
        }
        if (config.treeCritterTimer && HuntingTracker.treeCritterTimer() != null) {
            appendSectionIfPresent(content, "Tree Protection Order", 0xFFFF6B6B,
                    List.of(Line.value("Critter in", HuntingTracker.treeCritterTimer().display())));
        }
        if (config.miriaContest) appendContest(content, config, "Miria's Contest");
        if (config.critterBehavior && !HuntingTracker.behaviorName().isBlank()) {
            appendSectionIfPresent(content, ModText.get("hud.hunting.behavior"), 0xFFFFB866,
                    List.of(Line.value(HuntingTracker.behaviorName(), value(HuntingTracker.behaviorStatus()))));
        }
        if (config.benefactorHud) appendBenefactor(content, config);
        return withTitle("Torrhus Canyon", content);
    }

    private static List<Line> galateaLines() {
        var config = ConfigManager.get().hunting;
        List<Line> content = new ArrayList<>();
        if (config.galateaTracker) {
            List<Line> chapterLines = new ArrayList<>();
            var chapter = HuntingTracker.chapter();
            if (config.showChapter) addValueIfPresent(chapterLines, "Hina Chapter", chapter.chapter());
            if (config.showCurrentTask) addValueIfPresent(chapterLines, ModText.get("hud.hunting.task"), chapter.task());
            if (config.showTaskProgress) addValueIfPresent(chapterLines, ModText.get("hud.hunting.progress"), chapter.progress());
            if (config.showCompletedTasks) addValueIfPresent(chapterLines, ModText.get("hud.hunting.completed"), chapter.completed());
            if (config.showChapterTotalProgress) addValueIfPresent(chapterLines, ModText.get("hud.hunting.total_progress"), chapter.totalProgress());
            if (config.showNextUnlock) addValueIfPresent(chapterLines, ModText.get("hud.hunting.next_unlock"), chapter.nextUnlock());
            appendSectionIfPresent(content, "Hina Chapter", 0xFFFFD45A, chapterLines);
            appendTorrhusResources(content, config);
        }
        if (config.agathaContest) appendContest(content, config, "Agatha's Contest");
        return withTitle("Galatea", content);
    }

    private static void appendTorrhusResources(List<Line> lines, ModConfig.Hunting config) {
        List<Line> resources = new ArrayList<>();
        if (config.showForestWhispers) appendResource(resources, "Forest Whispers", HuntingTextParser.Resource.FOREST_WHISPERS, 0xFF55E3C0);
        if (config.showDesertWhispers) appendResource(resources, "Desert Whispers", HuntingTextParser.Resource.DESERT_WHISPERS, 0xFFFFC55C);
        if (config.showForestEssence) appendResource(resources, "Forest Essence", HuntingTextParser.Resource.FOREST_ESSENCE, 0xFF67E87A);
        if (config.showSafariEssenceTorrhus) appendResource(resources, "Safari Essence", HuntingTextParser.Resource.SAFARI_ESSENCE, 0xFFFFB35C);
        if (config.showSweep) appendResource(resources, "Sweep", HuntingTextParser.Resource.SWEEP, 0xFFFFAA00);
        if (config.showForestFortune) appendResource(resources, "Forest Fortune", HuntingTextParser.Resource.FOREST_FORTUNE, 0xFFB9F78A);
        appendSectionIfPresent(lines, ModText.get("hud.hunting.resources"), 0xFFFFD45A, resources);
    }

    private static void appendResource(List<Line> lines, String name, HuntingTextParser.Resource resource, int color) {
        double amount = HuntingTracker.resource(resource);
        if (amount < 0) return;
        lines.add(new Line(name + ": " + CompactNumbers.format(amount), color, 3, false));
    }

    private static void appendContest(List<Line> lines, ModConfig.Hunting config, String title) {
        var contest = HuntingTracker.contest();
        if (!contest.active() && contest.nextScore() < 0 && contest.remaining() < 0 && contest.ticket().isBlank()) return;
        List<Line> contestLines = new ArrayList<>();
        if (config.contestNextBracket) {
            if (contest.nextScore() >= 0) {
                String value = (contest.nextBracket().isBlank() ? "" : contest.nextBracket() + " · ")
                        + CompactNumbers.format(contest.nextScore());
                contestLines.add(Line.value(ModText.get("hud.hunting.next_bracket"), value));
            }
        }
        if (config.contestExpectedTicket) addValueIfPresent(contestLines, ModText.get("hud.hunting.expected_ticket"), contest.ticket());
        if (config.contestRemainingScore) {
            if (contest.remaining() >= 0) {
                contestLines.add(Line.value(ModText.get("hud.hunting.score_remaining"), CompactNumbers.format(contest.remaining())));
            }
        }
        appendSectionIfPresent(lines, title, 0xFFFF72DB, contestLines);
    }

    private static void appendBenefactor(List<Line> lines, ModConfig.Hunting config) {
        var benefactor = HuntingTracker.benefactor();
        if (benefactor == HuntingTracker.BenefactorState.EMPTY) return;
        List<Line> benefactorLines = new ArrayList<>();
        if (config.benefactorStatus) benefactorLines.add(Line.value(ModText.get("hud.hunting.status"),
                benefactor.active() ? ModText.get("hud.hunting.active") : ModText.get("hud.hunting.inactive")));
        if (config.benefactorTimer) {
            long seconds = benefactor.remainingSeconds();
            benefactorLines.add(Line.value(ModText.get("hud.hunting.time_left"), seconds < 0 ? "—" : HuntingTracker.durationText(seconds)));
        }
        if (config.benefactorEffects && !benefactor.effect().isBlank()) benefactorLines.add(Line.value(ModText.get("hud.hunting.effect"), benefactor.effect()));
        if (config.benefactorDonation && !benefactor.donation().isBlank()) benefactorLines.add(Line.value(ModText.get("hud.hunting.donation"), benefactor.donation()));
        appendSectionIfPresent(lines, "Benefactor", 0xFFA98CFF, benefactorLines);
    }

    private static List<Line> safariLines() {
        var config = ConfigManager.get().hunting;
        List<Line> content = new ArrayList<>();
        if (config.safariDashboard) {
            List<Line> dashboard = new ArrayList<>();
            if (config.safariRunTime && HuntingTracker.safariRunMillis() > 0) {
                dashboard.add(Line.value(ModText.get("hud.hunting.run_time"),
                        HuntingTracker.durationText(HuntingTracker.safariRunMillis() / 1_000)));
            }
            if (config.safariTicketTier) addValueIfPresent(dashboard, ModText.get("hud.hunting.ticket_tier"), HuntingTracker.safariTicketTier());
            appendSectionIfPresent(content, ModText.get("hud.hunting.run_dashboard"), 0xFFFFD45A, dashboard);
        }
        if (config.safariEagleStatus) appendEagle(content);
        if (config.safariShards) appendShardStats(content);
        if (config.safariCritterdex) appendCritterdex(content, config);
        if (config.floorDropAssistant || config.questItemTracker) appendSafariItems(content, config);
        if (config.wumpaHud) {
            List<Line> wumpaLines = new ArrayList<>();
            if (HuntingTracker.wumpaSpawned()) {
                wumpaLines.add(Line.value("Wumpa", ModText.get("hud.hunting.wumpa_spawned")));
            } else if (config.wumpaRequirements) {
                appendWumpaRequirements(wumpaLines);
            }
            if (config.wumpaPhase) wumpaLines.add(Line.value(ModText.get("hud.hunting.phase"), HuntingTracker.wumpaPhase().display));
            appendSectionIfPresent(content, "Wumpa Encounter", 0xFF8FD8FF, wumpaLines);
        }
        return withTitle("Critter Safari", content);
    }

    private static void appendEagle(List<Line> lines) {
        SafariEagleState eagle = SafariEagleTracker.current();
        if (!eagle.observed()) return;
        List<Line> values = new ArrayList<>();
        if (eagle.unlockStatus() != SafariEagleState.UnlockStatus.UNKNOWN) {
            values.add(Line.value(ModText.get("hud.hunting.eagle_unlock"),
                    ModText.get(eagle.unlockStatus() == SafariEagleState.UnlockStatus.UNLOCKED
                            ? "hud.hunting.unlocked" : "hud.hunting.locked")));
        }
        if (eagle.presence() != SafariEagleState.Presence.UNKNOWN) {
            values.add(Line.value(ModText.get("hud.hunting.eagle_presence"),
                    ModText.get(eagle.presence() == SafariEagleState.Presence.PRESENT
                            ? "hud.hunting.present" : "hud.hunting.absent")));
        }
        if (eagle.tier() != cloudy.autume.addition.tracker.PetTier.UNKNOWN) {
            values.add(Line.value(ModText.get("hud.hunting.eagle_tier"),
                    eagle.tier().name().replace('_', ' ')));
        }
        appendSectionIfPresent(lines, ModText.get("hud.hunting.eagle"), 0xFFFFD45A, values);
    }

    private static void appendWumpaRequirements(List<Line> lines) {
        Set<String> captured = HuntingTracker.wumpaPrerequisiteCaptures();
        long count = HuntingTextParser.WUMPA_PREREQUISITES.stream().filter(captured::contains).count();
        lines.add(Line.value(ModText.get("hud.hunting.icy_requirements"),
                count + "/" + HuntingTextParser.WUMPA_PREREQUISITES.size()));
        for (String critter : HuntingTextParser.WUMPA_PREREQUISITES) {
            boolean complete = captured.contains(critter);
            lines.add(new Line((complete ? "✔ " : "✘ ") + critter,
                    complete ? 0xFF55E875 : 0xFFFF6B6B, 6, false));
        }
    }

    private static void appendCritterdex(List<Line> lines, ModConfig.Hunting config) {
        Set<String> captured = HuntingTracker.capturedCritters();
        List<Line> critterdexLines = new ArrayList<>();
        if (config.critterdexBiomeProgress) {
            for (Map.Entry<String, List<String>> entry : HuntingTextParser.SAFARI_CRITTERS.entrySet()) {
                long count = entry.getValue().stream().filter(captured::contains).count();
                critterdexLines.add(new Line(entry.getKey() + ": " + count + "/" + entry.getValue().size(), biomeColor(entry.getKey()), 3, false));
            }
        }
        String biome = HuntingTracker.safariBiome();
        List<String> critters = HuntingTextParser.SAFARI_CRITTERS.get(biome);
        if (critters != null && config.critterdexCapturedNames) {
            StringJoiner names = new StringJoiner(", ");
            critters.stream().filter(captured::contains).forEach(names::add);
            critterdexLines.add(Line.value(ModText.get("hud.hunting.captured"), names.length() == 0 ? "—" : names.toString()));
        }
        if (critters != null && config.critterdexMissingNames) {
            StringJoiner names = new StringJoiner(", ");
            critters.stream().filter(name -> !captured.contains(name)).forEach(names::add);
            critterdexLines.add(Line.value(ModText.get("hud.hunting.missing"), names.length() == 0 ? ModText.get("hud.hunting.none") : names.toString()));
        }
        appendSectionIfPresent(lines, "Safari Run Critterdex", 0xFF9FF5B2, critterdexLines);
    }

    private static void appendShardStats(List<Line> lines) {
        List<Line> shardLines = new ArrayList<>();
        appendGroupedShardDrops(shardLines);
        appendSectionIfPresent(lines, ModText.get("hud.hunting.captured_shards"), 0xFF9FF5B2, shardLines);
    }

    private static void appendGroupedShardDrops(List<Line> lines) {
        Map<String, Integer> drops = HuntingTracker.shardDrops();
        if (drops.isEmpty()) return;
        lines.add(Line.value(ModText.get("hud.hunting.shards"), Integer.toString(HuntingTracker.safariShardCount())));
        for (Map.Entry<String, List<String>> biome : HuntingTextParser.SAFARI_CRITTERS.entrySet()) {
            List<Line> biomeDrops = new ArrayList<>();
            for (String critter : biome.getValue()) {
                String shard = critter + " Shard";
                int count = drops.getOrDefault(shard, 0);
                if (count <= 0) continue;
                HuntingTextParser.ShardRarity rarity = HuntingTextParser.critterRarity(critter);
                int color = rarity == null ? 0xFFD8E4EB : 0xFF000000 | rarity.color;
                biomeDrops.add(new Line(shard + (count > 1 ? " x" + count : ""), color, 9, false));
            }
            if (biomeDrops.isEmpty()) continue;
            lines.add(new Line(biome.getKey(), biomeColor(biome.getKey()), 6, false));
            lines.addAll(biomeDrops);
        }
    }

    private static int biomeColor(String biome) {
        return switch (biome) {
            case "Cavern" -> 0xFFB8B8B8;
            case "Forest" -> 0xFF55FF55;
            case "Haunted" -> 0xFFAA55FF;
            case "Icy" -> 0xFF55FFFF;
            default -> 0xFFD8E4EB;
        };
    }

    private static void appendSafariItems(List<Line> lines, ModConfig.Hunting config) {
        List<Line> itemLines = new ArrayList<>();
        if (config.floorDropAssistant && config.floorDropDistance) {
            double distance = HuntingTracker.nearestFloorDrop();
            if (distance >= 0) {
                itemLines.add(Line.value(ModText.get("hud.hunting.nearest_floor_drop"),
                        String.format(Locale.ROOT, "%.1fm", distance)));
            }
        }
        if (config.questItemTracker) {
            String values = joinedCounts(HuntingTracker.questItems());
            if (!values.isBlank()) itemLines.add(Line.value(ModText.get("hud.hunting.quest_items"), values));
        }
        appendSectionIfPresent(lines, ModText.get("hud.hunting.safari_items"), 0xFFFFC96B, itemLines);
    }

    private static void appendSectionIfPresent(List<Line> lines, String title, int color, List<Line> content) {
        if (content.isEmpty()) return;
        lines.add(Line.section(title, color));
        lines.addAll(content);
    }

    private static void addValueIfPresent(List<Line> lines, String label, String value) {
        if (value == null || value.isBlank()) return;
        lines.add(Line.value(label, value));
    }

    private static List<Line> withTitle(String title, List<Line> content) {
        if (content.isEmpty()) return List.of();
        List<Line> result = new ArrayList<>(content.size() + 1);
        result.add(Line.title(title));
        result.addAll(content);
        return result;
    }

    private static String joinedCounts(Map<String, Integer> values) {
        StringJoiner result = new StringJoiner(", ");
        values.forEach((name, count) -> result.add(name + (count > 1 ? " x" + count : "")));
        return result.toString();
    }

    private static String value(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static int height(List<Line> lines, ModConfig.PanelStyle style) {
        int height = 10;
        for (Line line : lines) height += wrap(line, style).size() * LINE_HEIGHT + (line.section ? 2 : 0);
        return Math.max(28, height);
    }

    private static List<FormattedCharSequence> wrap(Line line, ModConfig.PanelStyle style) {
        int available = WIDTH - PADDING * 2 - line.indent;
        List<FormattedCharSequence> wrapped = Minecraft.getInstance().font.split(
                HudPanel.styledText(line.text, style), available);
        return wrapped.isEmpty() ? List.of(Component.literal(" ").getVisualOrderText()) : wrapped;
    }

    private record Line(String text, int color, int indent, boolean section) {
        static Line title(String value) { return new Line(value, 0xFF7FDBFF, 0, true); }
        static Line section(String value, int color) { return new Line(value, color, 0, true); }
        static Line value(String label, String value) { return new Line(label + ": " + value, 0xFFD8E4EB, 3, false); }
    }
}

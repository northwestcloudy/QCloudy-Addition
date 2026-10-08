package cloudy.autume.addition.hunting;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure parsers for text that the client has already received from Hypixel. */
public final class HuntingTextParser {
    private static final String NUMBER = "[0-9][0-9,.]*(?:\\.[0-9]+)?[kmbt]?";
    private static final String RESOURCE_NAME = "Forest Whispers?|Desert Whispers?|Forest Essence|"
            + "Safari Essence|Forest Fortune|Sweep";
    private static final Pattern RESOURCE_AFTER = Pattern.compile(
            "(?i)(" + RESOURCE_NAME + ")\\s*(:|x)?\\s*([+]?" + NUMBER + ")(?:x)?\\b");
    private static final Pattern RESOURCE_BEFORE = Pattern.compile(
            "(?i)([+]?" + NUMBER + ")\\s+(" + RESOURCE_NAME + ")");
    private static final Pattern SWEEP_RESOURCE = Pattern.compile(
            "(?i)^\\s*Sweep\\s*(:|x)?\\s*([+]?" + NUMBER + ")(?:x)?\\b");
    private static final Pattern CHAPTER = Pattern.compile(
            "(?i)\\b(?:(?:Helia|Hina)'?s?\\s+)?Chapter\\s*:?[ \\t]*([IVXLCDM]+|[0-9]+)(?:\\s*[:\\-]\\s*(.*))?");
    private static final Pattern FRACTION = Pattern.compile("([0-9][0-9,]*)\\s*/\\s*([0-9][0-9,]*)");
    private static final Pattern PERCENT = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)%");
    private static final Pattern SCORE = Pattern.compile(
            "(?i)(?:Score|Points|Captured Mobs)\\s*:?\\s*(" + NUMBER + ")");
    private static final Pattern EXPLICIT_NEXT = Pattern.compile("(?i)(?:Next (?:Bracket|Tier)|Next Reward)\\s*:?\\s*(" + NUMBER + ")");
    private static final Pattern TIER_WITH = Pattern.compile(
            "(?i)\\b(Common|Uncommon|Rare|Epic|Legendary|Mythic|Divine|Special)\\s+with\\s+(" + NUMBER + ")");
    private static final Pattern TIER_REQUIRES = Pattern.compile(
            "(?i)\\b(Common|Uncommon|Rare|Epic|Legendary|Mythic|Divine|Special)\\s+requires\\s+\\+?(" + NUMBER + ")");
    private static final Pattern CAPTURE_AMOUNT = Pattern.compile("(?i)\\b([0-9]+)x\\b");
    private static final Pattern SPARKLING = Pattern.compile(
            "(?i)SPARKLING.*?Critter.*?appeared in the\\s+(Cavern|Forest|Haunted|Icy)(?:\\s+Biome)?[!.]?$" );
    private static final Pattern CRITTER_PROGRESS = Pattern.compile(
            "(?i)(Blue Jay|Goldolot|Dustybit|Hideonsun).*?([0-9]+)\\s*/\\s*([0-9]+)");
    private static final Pattern MOB_LEVEL = Pattern.compile(
            "(?i)^\\[\\s*Lv\\s*([0-9][0-9,]*)\\s*]\\s+.+$");
    private static final Pattern COLD = Pattern.compile(
            "(?i)(?:❄\\s*)?\\bCold\\s*(?:[:：]|➜)?\\s*([0-9]{1,3})\\b");
    private static final Pattern CLOCK = Pattern.compile("\\b([0-9]{1,2}):([0-9]{2})(?::([0-9]{2}))?\\b");
    private static final Pattern DURATION_PART = Pattern.compile(
            "(?i)([0-9]+)\\s*(days?|d|hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)\\b");
    private static final Pattern TREE_CRITTER_TIMER = Pattern.compile(
            "(?i)\\bCritter\\s+in\\s*:\\s*((?:[0-9]+\\s*(?:h|m|s)\\s*){1,3}|[0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?)\\b");
    private static final Pattern PERSONAL_TREE_GIFT_REWARDS = Pattern.compile(
            "(?i)^\\+[0-9,]+ rewards gained!(?:\\s*\\(hover\\))?$");
    private static final Pattern TREE_GIFT_BORDER = Pattern.compile("^▬{64}$");
    private static final Pattern TREE_GIFT_BONUS_HEADER = Pattern.compile("(?i)^BONUS GIFT$");
    private static final Pattern TREE_GIFT_BONUS_REWARD = Pattern.compile(
            "(?i)^(.+?)\\s*\\([0-9]+(?:\\.[0-9]+)?%\\)$");
    private static final Pattern TREE_GIFT_PHANTOM = Pattern.compile(
            "(?i)^A (.+?) fell from the Tree!$");
    private static final Pattern TREE_GIFT_WILD_CREATURE = Pattern.compile(
            "(?i)^-?\\s*A wild (.+?) appeared!$");
    private static final Pattern BENEFACTOR_WORD = Pattern.compile("(?i)\\bBenefactor\\b");
    private static final Pattern BENEFACTOR_DONATION = Pattern.compile(
            "(?i)^BENEFACTOR\\s*:\\s*You donated to the (Forest|Desert) Temple "
                    + "and will receive its bonus for \\+?(.+?)!$");
    private static final Pattern TEMPLE = Pattern.compile("(?i)\\b(Forest|Desert)(?:\\s+Starborn)?\\s+Temple\\b");
    private static final Pattern STARBORN = Pattern.compile("(?i)\\b(Forest|Desert)\\s+Starborn\\b");

    public static final Map<String, List<String>> SAFARI_CRITTERS = safariCritters();
    /** The eight Icy Critters that must be captured before Wumpa can spawn. */
    public static final List<String> WUMPA_PREREQUISITES = List.copyOf(
            SAFARI_CRITTERS.get("Icy").stream().filter(name -> !name.equals("Wumpa")).toList());
    public static final Map<String, ShardRarity> SAFARI_CRITTER_RARITIES = safariCritterRarities();
    public static final Set<String> QUEST_ITEMS = Set.of(
            "Bag of Seeds", "Flavor-packed Fish", "Icebreaker", "Lime Gem", "Lush Lily Pad",
            "Shining Coin", "Soothing Incense", "Orange Gem", "Purple Gem", "Wholesale Wheat",
            "Wriggleworm", "Yogi Berry");

    private static final int[] TORRHUS_BRACKETS = {25, 250, 1_000, 2_500, 5_000, 10_000, 15_000, 20_000};
    private static final String[] BRACKET_NAMES = {
            "Common", "Uncommon", "Rare", "Epic", "Legendary", "Mythic", "Divine", "Special"
    };

    private HuntingTextParser() {
    }

    public static String plain(String value) {
        String stripped = ChatFormatting.stripFormatting(value == null ? "" : value);
        return stripped == null ? "" : stripped.replace('\u00a0', ' ').trim();
    }

    public static List<ResourceUpdate> resources(Iterable<String> lines) {
        List<ResourceUpdate> result = new ArrayList<>();
        for (String raw : lines) {
            String line = plain(raw);
            Matcher sweep = SWEEP_RESOURCE.matcher(line);
            if (sweep.find()) {
                boolean additive = "x".equalsIgnoreCase(sweep.group(1)) || gainLanguage(line);
                result.add(new ResourceUpdate(Resource.SWEEP, sweep.group(2).replace("+", ""), additive));
                continue;
            }
            Matcher matcher = RESOURCE_AFTER.matcher(line);
            while (matcher.find()) {
                Resource resource = Resource.from(matcher.group(1));
                if (resource == Resource.SWEEP) continue;
                String amount = matcher.group(3);
                boolean additive = "x".equalsIgnoreCase(matcher.group(2)) || gainLanguage(line);
                result.add(new ResourceUpdate(resource, amount.replace("+", ""), additive));
            }
            Matcher before = RESOURCE_BEFORE.matcher(line);
            while (before.find()) {
                // Do not emit a second update for the same "Resource: amount" fragment.
                if (matcher.reset().find()) break;
                String amount = before.group(1);
                result.add(new ResourceUpdate(Resource.from(before.group(2)), amount.replace("+", ""),
                        amount.startsWith("+") || gainLanguage(line)));
            }
        }
        return List.copyOf(result);
    }

    public static ChapterSnapshot chapter(Iterable<String> lines) {
        String chapter = "";
        String task = "";
        String progress = "";
        String completed = "";
        String totalProgress = "";
        String nextUnlock = "";
        boolean chapterContext = false;
        int taskLineIndex = -100;
        int contextUntil = -100;
        int lineIndex = 0;
        for (String raw : lines) {
            lineIndex++;
            String line = plain(raw);
            if (line.isBlank()) continue;
            String lower = line.toLowerCase(Locale.ROOT);
            boolean explicitContext = lower.contains("helia") && lower.contains("chapter")
                    || lower.contains("hina") && lower.contains("chapter")
                    || lower.contains("torrhus") && lower.contains("chapter")
                    || lower.contains("galatea") && lower.contains("chapter");
            if (explicitContext) {
                chapterContext = true;
                contextUntil = lineIndex + 10;
            } else if (chapterContext && lineIndex > contextUntil) {
                chapterContext = false;
            }
            if (lower.startsWith("completed:") || lower.startsWith("tasks completed:")) {
                completed = afterColon(line);
                continue;
            }
            if (lower.startsWith("chapter progress:") || lower.startsWith("total progress:")) {
                totalProgress = afterColon(line);
                continue;
            }
            if (lower.startsWith("next unlock:")) {
                nextUnlock = afterColon(line);
                continue;
            }
            Matcher chapterMatcher = CHAPTER.matcher(line);
            if (chapterMatcher.find() && (chapterContext || lower.startsWith("chapter")
                    || lower.contains("torrhus canyon") || lower.contains("galatea"))) {
                chapterContext = true;
                contextUntil = lineIndex + 10;
                chapter = "Chapter " + chapterMatcher.group(1);
                String suffix = chapterMatcher.group(2);
                if (suffix != null && !suffix.isBlank() && !looksLikeStatusLabel(suffix)
                        && plausibleChapterTask(suffix)) {
                    task = suffix.trim();
                    taskLineIndex = lineIndex;
                }
                continue;
            }
            if (!chapterContext) continue;
            if (lower.startsWith("current task:") || lower.startsWith("task:")
                    || lower.startsWith("chapter task:")) {
                String receivedTask = afterColon(line);
                if (plausibleChapterTask(receivedTask)) {
                    task = receivedTask;
                    taskLineIndex = lineIndex;
                }
                continue;
            }
            if ((lower.startsWith("progress:") || lower.startsWith("task progress:")) && !task.isBlank()) {
                progress = afterColon(line);
                continue;
            }
            if (!task.isBlank() && progress.isBlank() && lineIndex - taskLineIndex <= 2
                    && (FRACTION.matcher(line).find() || PERCENT.matcher(line).find())) {
                progress = progressFrom(line);
            } else if (task.isBlank() && looksLikeTask(line)) {
                task = taskName(line);
                progress = progressFrom(line);
                taskLineIndex = lineIndex;
            }
        }
        if (!task.isBlank() && progress.isBlank()) progress = progressFrom(task);
        task = taskName(task);
        return new ChapterSnapshot(chapter, task, progress, completed, totalProgress, nextUnlock);
    }

    /** Parses the real Chapters overview/detail inventory layout already open on the client. */
    public static ChapterSnapshot chapterMenu(String rawTitle, Iterable<String> itemTexts) {
        String title = plain(rawTitle);
        String lowerTitle = title.toLowerCase(Locale.ROOT);
        if (!(lowerTitle.contains("chapter") && (lowerTitle.contains("helia")
                || lowerTitle.contains("hina") || lowerTitle.contains("torrhus")
                || lowerTitle.contains("galatea")))) return ChapterSnapshot.EMPTY;

        List<String> items = new ArrayList<>();
        for (String item : itemTexts) if (item != null && !item.isBlank()) items.add(item);
        if (lowerTitle.contains("chapters")) return chapterOverview(items);

        ChapterSnapshot titleSnapshot = chapter(List.of(title));
        String chapter = titleSnapshot.chapter();
        String completed = "";
        String task = "";
        String progress = "";
        for (String item : items) {
            List<String> itemLines = cleanLines(item);
            if (itemLines.isEmpty()) continue;
            ChapterSnapshot itemSnapshot = chapter(itemLines);
            if (chapter.isBlank() && !itemSnapshot.chapter().isBlank()) chapter = itemSnapshot.chapter();
            if (!itemSnapshot.completed().isBlank()) completed = itemSnapshot.completed();

            String name = itemLines.getFirst();
            if (CHAPTER.matcher(name).find() || !plausibleChapterTask(name)) continue;
            String itemProgress = itemLines.stream()
                    .filter(line -> line.toLowerCase(Locale.ROOT).startsWith("progress:"))
                    .map(HuntingTextParser::afterColon).filter(value -> !value.isBlank())
                    .findFirst().orElse("");
            if (itemProgress.isBlank() || progressComplete(itemProgress)) continue;
            if (task.isBlank()) {
                task = taskName(name);
                progress = progressFrom(itemProgress);
            }
        }
        return new ChapterSnapshot(chapter, task, progress, completed, "", "");
    }

    private static ChapterSnapshot chapterOverview(List<String> items) {
        ChapterSnapshot highestUnlocked = ChapterSnapshot.EMPTY;
        ChapterSnapshot highestIncomplete = ChapterSnapshot.EMPTY;
        int unlockedNumber = -1;
        int incompleteNumber = -1;
        for (String item : items) {
            List<String> lines = cleanLines(item);
            String joined = String.join(" ", lines).toLowerCase(Locale.ROOT);
            if (joined.contains("haven't unlocked") || joined.contains("not unlocked")
                    || joined.contains("chapter locked")) continue;
            ChapterSnapshot candidate = chapter(lines);
            int number = chapterNumber(candidate.chapter());
            if (number < 0) continue;
            if (number > unlockedNumber) {
                unlockedNumber = number;
                highestUnlocked = candidate;
            }
            if (!progressComplete(candidate.completed()) && number > incompleteNumber) {
                incompleteNumber = number;
                highestIncomplete = candidate;
            }
        }
        return highestIncomplete.empty() ? highestUnlocked : highestIncomplete;
    }

    /** Parses the player's own Benefactor state from one bounded Tab/scoreboard/chat/menu block. */
    public static BenefactorUpdate benefactor(Iterable<String> rawLines) {
        List<String> lines = new ArrayList<>();
        for (String raw : rawLines) {
            String line = plain(raw);
            if (!line.isBlank()) lines.add(line);
        }
        if (lines.isEmpty()) return BenefactorUpdate.EMPTY;
        String joined = String.join(" ", lines);
        if (!BENEFACTOR_WORD.matcher(joined).find()) return BenefactorUpdate.EMPTY;

        for (String line : lines) {
            Matcher donation = BENEFACTOR_DONATION.matcher(line);
            if (!donation.matches()) continue;
            String temple = titleCase(donation.group(1)) + " Temple";
            long seconds = durationSeconds(donation.group(2));
            return new BenefactorUpdate(true, true, seconds, true, temple,
                    benefactorEffect(temple), line);
        }

        String lower = joined.toLowerCase(Locale.ROOT);
        String temple = benefactorTemple(joined);
        boolean inactive = lower.contains("no active donation") || lower.contains("not a benefactor")
                || lower.contains("benefactor inactive") || lower.contains("benefactor expired")
                || lower.contains("benefactor ended");
        boolean explicitActive = lower.contains("benefactor active") || lower.contains("active benefactor")
                || lower.contains("active donation") || lower.contains("benefactor:");
        long remaining = 0;
        for (String line : lines) {
            String lineLower = line.toLowerCase(Locale.ROOT);
            if (!(BENEFACTOR_WORD.matcher(line).find() || lineLower.contains("remaining")
                    || lineLower.contains("time left") || lineLower.contains("expires")
                    || lineLower.contains("active donation"))) continue;
            remaining = Math.max(remaining, durationSeconds(line));
        }
        boolean observed = inactive || explicitActive || remaining > 0;
        if (!observed) return BenefactorUpdate.EMPTY;
        boolean active = !inactive && (explicitActive || remaining > 0);
        String effect = benefactorEffect(temple);
        String donation = lines.stream().filter(line -> {
            String value = line.toLowerCase(Locale.ROOT);
            return value.contains("donated") || value.contains("active donation");
        }).findFirst().orElse("");
        return new BenefactorUpdate(true, active, remaining, false, temple, effect, donation);
    }

    public static ContestSnapshot contest(Iterable<String> lines) {
        boolean active = false;
        long score = -1;
        long explicitNext = -1;
        long explicitRemaining = -1;
        String currentBracket = "";
        String nextBracket = "";
        String receivedTicket = "";
        for (String raw : lines) {
            String line = plain(raw);
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("miria's contest") || lower.contains("mirias contest")
                    || lower.contains("agatha's contest") || lower.contains("agathas contest")
                    || lower.contains("starlyn contest")) active = true;
            Matcher scoreMatcher = SCORE.matcher(line);
            if (scoreMatcher.find()) score = whole(scoreMatcher.group(1));
            Matcher nextMatcher = EXPLICIT_NEXT.matcher(line);
            if (nextMatcher.find()) explicitNext = whole(nextMatcher.group(1));
            Matcher tierWith = TIER_WITH.matcher(line);
            if (tierWith.find()) {
                currentBracket = titleCase(tierWith.group(1));
                score = whole(tierWith.group(2));
            }
            Matcher tierRequires = TIER_REQUIRES.matcher(line);
            if (tierRequires.find()) {
                nextBracket = titleCase(tierRequires.group(1));
                explicitRemaining = whole(tierRequires.group(2));
            }
            String ticket = ticketIn(line);
            if (!ticket.isBlank()) receivedTicket = ticket;
        }
        if (!active) return ContestSnapshot.EMPTY;
        int currentIndex = bracketIndex(score);
        long next = explicitNext >= 0 ? explicitNext
                : explicitRemaining >= 0 && score >= 0 ? score + explicitRemaining : nextThreshold(score);
        long remaining = explicitRemaining >= 0 ? explicitRemaining
                : score < 0 || next < 0 ? -1 : Math.max(0, next - score);
        String bracket = currentBracket.isBlank() && currentIndex >= 0 ? BRACKET_NAMES[currentIndex] : currentBracket;
        if (nextBracket.isBlank() && currentIndex + 1 >= 0 && currentIndex + 1 < BRACKET_NAMES.length) {
            nextBracket = BRACKET_NAMES[currentIndex + 1];
        }
        String ticket = receivedTicket.isBlank() ? expectedTicket(score) : receivedTicket;
        return new ContestSnapshot(true, score, next, remaining, bracket, nextBracket, ticket);
    }

    public static Capture capture(String raw) {
        String line = plain(raw);
        String lower = line.toLowerCase(Locale.ROOT);
        boolean lootShare = lower.contains("loot share");
        if (!lower.contains("capture!") && !lower.contains("you caught") && !lootShare) return null;
        String critter = findCritter(line);
        if (critter.isBlank()) return null;
        int amount = 1;
        Matcher amountMatcher = CAPTURE_AMOUNT.matcher(line);
        while (amountMatcher.find()) amount = Math.max(amount, Integer.parseInt(amountMatcher.group(1)));
        String shard = critter + " Shard";
        return new Capture(critter, shard, amount, lootShare, lower.contains("sparkling"));
    }

    /** Exact capture prefix used to end local behavior guidance after a successful Lasso catch. */
    public static boolean captureConfirmation(String raw) {
        return plain(raw).toLowerCase(Locale.ROOT).startsWith("capture! you caught ");
    }

    public static boolean lassoReelLabel(String raw) {
        return plain(raw).equals("REEL");
    }

    public static String sparklingBiome(String raw) {
        Matcher matcher = SPARKLING.matcher(plain(raw));
        if (!matcher.find()) return "";
        return matcher.group(1).trim();
    }

    public static CritterProgress critterProgress(String raw) {
        Matcher matcher = CRITTER_PROGRESS.matcher(plain(raw));
        if (!matcher.find()) return null;
        return new CritterProgress(matcher.group(1), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
    }

    public static int cold(Iterable<String> lines) {
        int result = -1;
        for (String raw : lines) {
            Matcher matcher = COLD.matcher(plain(raw));
            if (matcher.find()) result = Math.clamp(Integer.parseInt(matcher.group(1)), 0, 100);
        }
        return result;
    }

    public static ShardRarity critterRarity(String rawEntityName) {
        String lower = plain(rawEntityName).toLowerCase(Locale.ROOT);
        if (lower.isBlank()) return null;
        for (Map.Entry<String, ShardRarity> entry : SAFARI_CRITTER_RARITIES.entrySet()) {
            if (lower.contains(entry.getKey().toLowerCase(Locale.ROOT))) return entry.getValue();
        }
        return null;
    }

    /** Reads the leading level tag from a mob display name already received by the client. */
    public static Integer mobLevel(String rawEntityName) {
        Matcher matcher = MOB_LEVEL.matcher(plain(rawEntityName));
        if (!matcher.matches()) return null;
        try {
            return Integer.valueOf(matcher.group(1).replace(",", ""));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static long durationSeconds(String raw) {
        String line = plain(raw);
        long seconds = 0;
        Matcher part = DURATION_PART.matcher(line);
        while (part.find()) {
            long value = Long.parseLong(part.group(1));
            String unit = part.group(2).toLowerCase(Locale.ROOT);
            if (unit.startsWith("d")) seconds += value * 86_400;
            else if (unit.startsWith("h")) seconds += value * 3600;
            else if (unit.startsWith("m")) seconds += value * 60;
            else seconds += value;
        }
        Matcher clock = CLOCK.matcher(line);
        if (clock.find()) {
            long first = Long.parseLong(clock.group(1));
            long second = Long.parseLong(clock.group(2));
            String third = clock.group(3);
            seconds += third == null ? first * 60 + second
                    : first * 3600 + second * 60 + Long.parseLong(third);
        }
        return seconds;
    }

    /** Parses the floating countdown already rendered by a Tree Protection Order. */
    public static TreeCritterTimer treeCritterTimer(String raw) {
        Matcher matcher = TREE_CRITTER_TIMER.matcher(plain(raw));
        if (!matcher.find()) return null;
        String display = matcher.group(1).trim().replaceAll("\\s+", " ");
        return new TreeCritterTimer(display, durationSeconds(display));
    }

    private static String trackedTreeGiftLoot(String raw, Map<String, Boolean> enabled) {
        String line = plain(raw);
        String lower = line.toLowerCase(Locale.ROOT);
        if (Boolean.TRUE.equals(enabled.get("Enchanted Book (Karma I)")) && lower.contains("karma i")) {
            return "Enchanted Book (Karma I)";
        }
        return enabled.entrySet().stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .sorted((left, right) -> Integer.compare(right.length(), left.length()))
                .filter(name -> matchesTreeGiftName(lower, name))
                .findFirst().orElse("");
    }

    private static boolean matchesTreeGiftName(String lower, String configuredName) {
        String name = configuredName.toLowerCase(Locale.ROOT);
        if (lower.contains(name)) return true;
        // Hypixel's bonus line uses the underlying Shard name without the
        // display suffix for these two entries.
        return (name.equals("chameleon shard") && lower.contains("chameleon"))
                || (name.equals("hummingbird shard") && lower.contains("hummingbird"));
    }

    /**
     * The reward summary is sent to the player who earned the Tree Gift and carries that
     * player's rewards in SHOW_TEXT. Gift headers and phantom-drop lines are public and
     * therefore must not be used to infer ownership.
     */
    public static boolean personalTreeGiftRewardSummary(String raw) {
        return PERSONAL_TREE_GIFT_REWARDS.matcher(plain(raw)).matches();
    }

    /** Returns enabled rare rewards only from this player's own reward-summary component. */
    public static List<String> personalTreeGiftLoot(Component message, Map<String, Boolean> enabled) {
        if (message == null || !personalTreeGiftRewardSummary(message.getString())) return List.of();
        return treeGiftHoverLoot(message, enabled);
    }

    /**
     * Reads tracked loot from SHOW_TEXT after the caller has already proved
     * that this component contains the player's personal reward summary.
     */
    static List<String> treeGiftHoverLoot(Component message, Map<String, Boolean> enabled) {
        if (message == null || enabled == null) return List.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String hoverLine : treeGiftHoverLines(message)) {
            String loot = trackedTreeGiftLoot(hoverLine, enabled);
            if (!loot.isBlank()) result.add(loot);
        }
        return List.copyOf(result);
    }

    public static boolean treeGiftBorder(String raw) {
        return TREE_GIFT_BORDER.matcher(plain(raw)).matches();
    }

    public static boolean treeGiftBonusHeader(String raw) {
        return TREE_GIFT_BONUS_HEADER.matcher(plain(raw)).matches();
    }

    /** Parses only exact Tree Gift bonus rows from an already-open personal gift block. */
    public static String treeGiftChatLoot(String raw, Map<String, Boolean> enabled, boolean bonusSection) {
        String line = plain(raw);
        Matcher wild = TREE_GIFT_WILD_CREATURE.matcher(line);
        if (wild.matches()) return trackedTreeGiftLoot(wild.group(1), enabled);
        if (!bonusSection) return "";
        Matcher phantom = TREE_GIFT_PHANTOM.matcher(line);
        if (phantom.matches()) return trackedTreeGiftLoot(phantom.group(1), enabled);
        Matcher reward = TREE_GIFT_BONUS_REWARD.matcher(line);
        return reward.matches() ? trackedTreeGiftLoot(reward.group(1), enabled) : "";
    }

    /** Parses only the exact public creature-spawn sentence. */
    static String treeGiftWildCreatureLoot(String raw, Map<String, Boolean> enabled) {
        Matcher wild = TREE_GIFT_WILD_CREATURE.matcher(plain(raw));
        return wild.matches() ? trackedTreeGiftLoot(wild.group(1), enabled) : "";
    }

    /** Returns only SHOW_TEXT data already attached to the received chat component. */
    public static List<String> treeGiftHoverLines(Component message) {
        if (message == null) return List.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Component part : message.toFlatList()) {
            if (!(part.getStyle().getHoverEvent() instanceof HoverEvent.ShowText showText)) continue;
            for (String rawLine : showText.value().getString().split("\\R")) {
                String line = plain(rawLine);
                if (!line.isBlank()) result.add(line);
            }
        }
        return List.copyOf(result);
    }

    public static boolean fairySoulConfirmation(String raw) {
        String line = plain(raw);
        return line.equals("SOUL! You found a Fairy Soul!")
                || line.equals("You have already found that Fairy Soul!");
    }

    static long whole(String raw) {
        if (raw == null) return -1;
        String value = raw.replace(",", "").trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return -1;
        double multiplier = 1;
        char suffix = value.charAt(value.length() - 1);
        if (suffix == 'k' || suffix == 'm' || suffix == 'b' || suffix == 't') {
            value = value.substring(0, value.length() - 1);
            multiplier = switch (suffix) {
                case 'k' -> 1_000;
                case 'm' -> 1_000_000;
                case 'b' -> 1_000_000_000;
                case 't' -> 1_000_000_000_000L;
                default -> 1;
            };
        }
        try {
            return Math.round(Double.parseDouble(value) * multiplier);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static boolean looksLikeStatusLabel(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("completed") || lower.startsWith("progress") || lower.startsWith("next");
    }

    private static boolean looksLikeTask(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return !line.endsWith(":") && plausibleChapterTask(line)
                && (lower.startsWith("talk ") || lower.startsWith("open ") || lower.startsWith("loot ")
                || lower.startsWith("obtain ") || lower.startsWith("participate ")
                || lower.startsWith("reach ") || lower.startsWith("hunt ") || lower.startsWith("pick up ")
                || lower.startsWith("enter ") || lower.startsWith("catch ") || lower.startsWith("craft ")
                || lower.startsWith("purchase ") || lower.startsWith("collect ")
                || lower.startsWith("capture ") || lower.startsWith("visit ")
                || lower.startsWith("speak ") || lower.startsWith("chop ") || lower.startsWith("use ")
                || lower.startsWith("find ") || lower.startsWith("complete ") || lower.startsWith("kill "));
    }

    static boolean plausibleChapterTask(String raw) {
        String lower = plain(raw).toLowerCase(Locale.ROOT);
        return !lower.isBlank() && !lower.startsWith("sb level") && !lower.startsWith("purse")
                && !lower.startsWith("bits") && !lower.startsWith("profile")
                && !lower.startsWith("bank") && !lower.startsWith("pet:")
                && !lower.startsWith("benefactor") && !lower.startsWith("forest whispers")
                && !lower.startsWith("desert whispers") && !lower.startsWith("forest essence")
                && !lower.startsWith("safari essence") && !lower.startsWith("forest fortune")
                && !lower.startsWith("sweep:") && !lower.startsWith("miria's contest");
    }

    private static String afterColon(String value) {
        int index = value.indexOf(':');
        return index < 0 ? "" : value.substring(index + 1).trim();
    }

    private static String progressFrom(String value) {
        Matcher fraction = FRACTION.matcher(value);
        if (fraction.find()) return fraction.group();
        Matcher percent = PERCENT.matcher(value);
        return percent.find() ? percent.group() : "";
    }

    private static String taskName(String value) {
        if (value == null || value.isBlank()) return "";
        return value.replaceFirst("\\s*[-:–]?\\s*[0-9][0-9,]*\\s*/\\s*[0-9][0-9,]*\\s*$", "")
                .replaceFirst("\\s*[-:–]?\\s*[0-9]+(?:\\.[0-9]+)?%\\s*$", "").trim();
    }

    private static List<String> cleanLines(String value) {
        List<String> result = new ArrayList<>();
        for (String raw : value.split("\\R")) {
            String line = plain(raw);
            if (!line.isBlank()) result.add(line);
        }
        return result;
    }

    private static boolean progressComplete(String value) {
        Matcher matcher = FRACTION.matcher(value == null ? "" : value);
        if (!matcher.find()) return false;
        long current = whole(matcher.group(1));
        long target = whole(matcher.group(2));
        return target > 0 && current >= target;
    }

    private static int chapterNumber(String value) {
        Matcher matcher = CHAPTER.matcher(value == null ? "" : value);
        if (!matcher.find()) return -1;
        String number = matcher.group(1).toUpperCase(Locale.ROOT);
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException ignored) {
            return switch (number) {
                case "I" -> 1; case "II" -> 2; case "III" -> 3; case "IV" -> 4;
                case "V" -> 5; case "VI" -> 6; case "VII" -> 7;
                default -> -1;
            };
        }
    }

    private static String benefactorTemple(String value) {
        Matcher temple = TEMPLE.matcher(value);
        if (temple.find()) return titleCase(temple.group(1)) + " Temple";
        Matcher starborn = STARBORN.matcher(value);
        return starborn.find() ? titleCase(starborn.group(1)) + " Temple" : "";
    }

    private static String benefactorEffect(String temple) {
        return switch (temple) {
            case "Forest Temple" -> "Axes +25% larger/faster; 2x Will Foraging Fortune";
            case "Desert Temple" -> "Black Holes/Huntraps +25% faster; 2x Will Hunting Fortune";
            default -> "";
        };
    }

    private static int bracketIndex(long score) {
        if (score < TORRHUS_BRACKETS[0]) return -1;
        int result = 0;
        for (int index = 1; index < TORRHUS_BRACKETS.length; index++) {
            if (score < TORRHUS_BRACKETS[index]) break;
            result = index;
        }
        return result;
    }

    private static long nextThreshold(long score) {
        if (score < 0) return -1;
        for (int threshold : TORRHUS_BRACKETS) if (score < threshold) return threshold;
        return -1;
    }

    private static String expectedTicket(long score) {
        if (score < 250) return "No Safari Ticket";
        if (score < 1_000) return "Basic Safari Ticket";
        if (score < 2_500) return "Economy Safari Ticket";
        if (score < 5_000) return "Premium Safari Ticket";
        return "First-Class Safari Ticket";
    }

    private static String ticketIn(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("first-class safari ticket")) return "First-Class Safari Ticket";
        if (lower.contains("premium safari ticket")) return "Premium Safari Ticket";
        if (lower.contains("economy safari ticket")) return "Economy Safari Ticket";
        if (lower.contains("basic safari ticket")) return "Basic Safari Ticket";
        return "";
    }

    private static String titleCase(String value) {
        if (value == null || value.isBlank()) return "";
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String findCritter(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        for (List<String> critters : SAFARI_CRITTERS.values()) {
            for (String critter : critters) {
                if (lower.contains(critter.toLowerCase(Locale.ROOT))) return critter;
            }
        }
        return "";
    }

    private static boolean gainLanguage(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.contains("gained ") || lower.contains("received ") || lower.contains("earned ")
                || lower.contains("obtained ");
    }

    private static Map<String, List<String>> safariCritters() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put("Cavern", List.of("Cavernfish", "Chuckwalla", "Driftling", "Gemzie", "Rockmite",
                "Scrappy", "Shyworm", "Snoozle", "Flitter"));
        result.put("Forest", List.of("Bluebird", "Fluffling", "Foxtrot", "Hideonfloor", "Honeybug",
                "Macaw", "Parakeet", "Treefrog", "Woodchucker"));
        result.put("Haunted", List.of("Areita", "Bloodbat", "Doomspiral", "Duplico", "Gazer",
                "Gimmiegold", "Hideonwall", "Hideyho", "Litterbug", "Solsnatcher"));
        result.put("Icy", List.of("Billygoat", "Mantis Shrimp", "Nozzlenose", "Polaris", "Shuddersquid",
                "Strongarm", "Tepid", "Troodon", "Wumpa"));
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, ShardRarity> safariCritterRarities() {
        Map<String, ShardRarity> result = new LinkedHashMap<>();
        putRarity(result, ShardRarity.COMMON,
                "Cavernfish", "Flitter", "Shyworm", "Foxtrot", "Strongarm", "Tepid");
        putRarity(result, ShardRarity.UNCOMMON,
                "Driftling", "Bluebird", "Honeybug", "Treefrog", "Woodchucker", "Areita",
                "Bloodbat", "Duplico", "Gazer", "Litterbug", "Solsnatcher", "Polaris", "Shuddersquid");
        putRarity(result, ShardRarity.RARE,
                "Chuckwalla", "Rockmite", "Scrappy", "Snoozle", "Fluffling", "Hideonfloor",
                "Parakeet", "Gimmiegold", "Hideonwall", "Hideyho", "Billygoat", "Mantis Shrimp",
                "Nozzlenose", "Troodon");
        putRarity(result, ShardRarity.EPIC, "Gemzie");
        putRarity(result, ShardRarity.LEGENDARY, "Macaw", "Doomspiral", "Wumpa");
        return Collections.unmodifiableMap(result);
    }

    private static void putRarity(Map<String, ShardRarity> target, ShardRarity rarity, String... names) {
        for (String name : names) target.put(name, rarity);
    }

    public enum ShardRarity {
        COMMON(0xFFFFFF),
        UNCOMMON(0x55FF55),
        RARE(0x5555FF),
        EPIC(0xAA00AA),
        LEGENDARY(0xFFAA00);

        public final int color;

        ShardRarity(int color) {
            this.color = color;
        }
    }

    public enum Resource {
        FOREST_WHISPERS, DESERT_WHISPERS, FOREST_ESSENCE, SAFARI_ESSENCE, FOREST_FORTUNE, SWEEP;

        static Resource from(String value) {
            String normalized = value.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
            if (normalized.equals("FOREST_WHISPER")) normalized = "FOREST_WHISPERS";
            if (normalized.equals("DESERT_WHISPER")) normalized = "DESERT_WHISPERS";
            return Resource.valueOf(normalized);
        }
    }

    public record ResourceUpdate(Resource resource, String amount, boolean additive) { }

    public record ChapterSnapshot(String chapter, String task, String progress, String completed,
                                  String totalProgress, String nextUnlock) {
        public static final ChapterSnapshot EMPTY = new ChapterSnapshot("", "", "", "", "", "");
        public boolean empty() { return chapter.isBlank() && task.isBlank(); }
    }

    public record BenefactorUpdate(boolean observed, boolean active, long remainingSeconds,
                                   boolean additiveDuration, String temple, String effect, String donation) {
        public static final BenefactorUpdate EMPTY = new BenefactorUpdate(false, false, 0,
                false, "", "", "");
    }

    public record ContestSnapshot(boolean active, long score, long nextScore, long remaining,
                                  String bracket, String nextBracket, String ticket) {
        public static final ContestSnapshot EMPTY = new ContestSnapshot(false, -1, -1, -1, "", "", "");
    }

    public record Capture(String critter, String shard, int amount, boolean lootShare, boolean sparkling) { }

    public record CritterProgress(String name, int current, int target) { }

    public record TreeCritterTimer(String display, long seconds) { }
}

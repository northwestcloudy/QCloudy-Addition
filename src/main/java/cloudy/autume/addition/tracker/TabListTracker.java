package cloudy.autume.addition.tracker;

import cloudy.autume.addition.compat.MinecraftClientCompat;
import cloudy.autume.addition.mixin.PlayerTabOverlayAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TabListTracker {
    private static final Pattern POWDER = Pattern.compile("(?i)^(Mithril|Gemstone|Glacite)(?: Powder)?\\s*:?\\s*([0-9,.kmb]+)$");
    private static final Pattern COMMISSION_PERCENT = Pattern.compile("^(.+?):\\s*([0-9]+(?:\\.[0-9]+)?)%$");
    private static final Pattern COMMISSION_NUMERIC = Pattern.compile("^(.+?):\\s*([0-9,]+)\\s*/\\s*([0-9,]+)$");
    private static final Pattern COMMISSION_DONE = Pattern.compile("(?i)^(.+?):\\s*(?:DONE|COMPLETE|COMPLETED)$");
    private static final Pattern CRIMSON_QUEST = Pattern.compile("^\\s*([✖✔])\\s+(.+?)(?:\\s+x([0-9]+))?$");
    private static final Pattern CRIMSON_QUEST_DONE = Pattern.compile("(?i).*\\b(?:DONE|COMPLETE|COMPLETED)\\b.*");
    private static List<String> lines = List.of();
    private static List<String> commissions = List.of();
    private static List<CommissionProgress> commissionProgress = List.of();
    private static List<CrimsonQuest> crimsonQuests = List.of();
    private static List<String> petWidget = List.of();
    private static String mithrilPowder = "—";
    private static String gemstonePowder = "—";
    private static String glacitePowder = "—";

    private TabListTracker() {
    }

    public static void update(Minecraft client) {
        if (client.getConnection() == null || !LocationTracker.isSkyBlock()) {
            clearReceivedState();
            return;
        }

        List<String> current = new ArrayList<>();
        List<Component> currentComponents = new ArrayList<>();
        var overlay = MinecraftClientCompat.tabList(client);
        List<PlayerInfo> playerInfos = ((PlayerTabOverlayAccessor) overlay).autumeCloudyAddition$getPlayerInfos();
        for (PlayerInfo playerInfo : playerInfos) {
            Component display = playerInfo.getTabListDisplayName();
            if (display == null) continue;
            String line = strip(display.getString());
            current.add(line);
            currentComponents.add(display);
        }
        lines = Collections.unmodifiableList(current);
        LocationTracker.observeProfile(lines);
        commissions = extractWidget(current, "Commissions:", 6);
        commissionProgress = commissions.stream()
                .map(raw -> parseCommission(raw, LocationTracker.area()))
                .toList();
        crimsonQuests = extractWidget(current, "Faction Quests:", 8).stream()
                .filter(raw -> !isCompletedCrimsonQuest(raw))
                .map(TabListTracker::parseCrimsonQuest)
                .toList();
        petWidget = extractWidget(current, "Pet:", 6);
        updatePowders(current);
        PetTracker.updateFromTab(current, currentComponents);
    }

    public static List<String> lines() {
        return lines;
    }

    public static List<String> commissions() {
        return commissions;
    }

    public static List<CommissionProgress> commissionProgress() {
        return commissionProgress;
    }

    public static List<CrimsonQuest> crimsonQuests() {
        return crimsonQuests;
    }

    public static List<String> petWidget() {
        return petWidget;
    }

    public static String mithrilPowder() {
        return mithrilPowder;
    }

    public static String gemstonePowder() {
        return gemstonePowder;
    }

    public static String glacitePowder() {
        return glacitePowder;
    }

    /**
     * Clears every value derived from a received Tab list. This is narrower
     * than persisted profile memory and is safe to call at any world boundary.
     */
    public static void clearReceivedState() {
        lines = List.of();
        commissions = List.of();
        commissionProgress = List.of();
        crimsonQuests = List.of();
        petWidget = List.of();
        mithrilPowder = "—";
        gemstonePowder = "—";
        glacitePowder = "—";
    }

    public static void reset() {
        clearReceivedState();
    }

    static void updatePowders(List<String> current) {
        List<String> widget = extractWidget(current, "Powders:", 5);
        List<String> source = widget.isEmpty() ? current : widget;
        for (String raw : source) {
            String candidate = raw.trim().replace("᠅", "").trim();
            Matcher matcher = POWDER.matcher(candidate);
            if (!matcher.matches()) continue;
            String amount = matcher.group(2);
            switch (matcher.group(1).toLowerCase(Locale.ROOT)) {
                case "mithril" -> mithrilPowder = amount;
                case "gemstone" -> gemstonePowder = amount;
                case "glacite" -> glacitePowder = amount;
                default -> { }
            }
        }
    }

    static List<String> extractWidget(List<String> source, String title, int limit) {
        for (int index = 0; index < source.size(); index++) {
            if (!source.get(index).trim().equalsIgnoreCase(title)) continue;
            List<String> result = new ArrayList<>();
            for (int cursor = index + 1; cursor < source.size() && result.size() < limit; cursor++) {
                String raw = source.get(cursor);
                String value = raw.trim();
                if (value.isEmpty()) break;
                if (looksLikeWidgetTitle(value)) break;
                result.add(value);
            }
            return List.copyOf(result);
        }
        return List.of();
    }

    static CommissionProgress parseCommission(String raw, IslandArea area) {
        Matcher numeric = COMMISSION_NUMERIC.matcher(raw.trim());
        if (numeric.matches()) {
            long current = parseWhole(numeric.group(2));
            long target = parseWhole(numeric.group(3));
            double percentage = target <= 0 ? 0.0 : current * 100.0 / target;
            return new CommissionProgress(numeric.group(1).trim(), percentage, current, target);
        }

        Matcher percent = COMMISSION_PERCENT.matcher(raw.trim());
        if (percent.matches()) {
            String name = percent.group(1).trim();
            double percentage = Double.parseDouble(percent.group(2));
            long target = targetFor(name, area);
            long current = target > 0 ? Math.round(target * Math.clamp(percentage, 0.0, 100.0) / 100.0) : -1;
            return new CommissionProgress(name, percentage, current, target);
        }

        Matcher done = COMMISSION_DONE.matcher(raw.trim());
        if (done.matches()) {
            String name = done.group(1).trim();
            long target = targetFor(name, area);
            return new CommissionProgress(name, 100.0, target, target);
        }

        int separator = raw.lastIndexOf(':');
        String name = separator > 0 ? raw.substring(0, separator).trim() : raw.trim();
        return new CommissionProgress(name, 0.0, -1, -1);
    }

    static long targetFor(String name, IslandArea area) {
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        if (area == IslandArea.GLACITE_TUNNELS || area == IslandArea.MINESHAFT) {
            if (normalized.endsWith(" collector")) return 1_500;
            return switch (normalized) {
                case "mineshaft explorer", "scrap collector" -> 1;
                case "corpse looter" -> 2;
                case "maniac slayer" -> 10;
                default -> -1;
            };
        }
        if (area == IslandArea.CRYSTAL_HOLLOWS) {
            if (normalized.endsWith(" gemstone collector")) return 1_000;
            if (normalized.endsWith(" crystal hunter")) return 1;
            return switch (normalized) {
                case "automaton slayer", "team treasurite member slayer", "goblin slayer", "yog slayer" -> 13;
                case "sludge slayer" -> 25;
                case "chest looter" -> 3;
                case "thyst slayer" -> 5;
                case "hard stone miner" -> 1_000;
                case "boss corleone slayer" -> 1;
                default -> -1;
            };
        }

        if (normalized.endsWith(" mithril") && !normalized.equals("mithril miner")) return 250;
        if (normalized.endsWith(" titanium") && !normalized.equals("titanium miner")) return 10;
        return switch (normalized) {
            case "glacite walker slayer" -> 50;
            case "goblin slayer" -> 100;
            // Starter commissions reuse these names with smaller goals. The normal target
            // is used after the tutorial; exact x/y text from the server always wins above.
            case "mithril miner" -> 350;
            case "titanium miner" -> 15;
            case "treasure hoarder puncher", "star sentry puncher" -> 10;
            case "goblin raid", "raffle", "elusive goblin slayer", "golden goblin slayer" -> 1;
            case "goblin raid slayer", "lucky raffle" -> 20;
            case "2x mithril powder collector" -> 500;
            default -> -1;
        };
    }

    private static long parseWhole(String value) {
        try {
            return Long.parseLong(value.replace(",", ""));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    static CrimsonQuest parseCrimsonQuest(String raw) {
        Matcher matcher = CRIMSON_QUEST.matcher(raw);
        if (!matcher.matches()) return new CrimsonQuest(raw.trim(), 1, false);
        int amount = matcher.group(3) == null ? 1 : Integer.parseInt(matcher.group(3));
        return new CrimsonQuest(matcher.group(2).trim(), amount, "✔".equals(matcher.group(1)));
    }

    static boolean isCompletedCrimsonQuest(String raw) {
        return CRIMSON_QUEST_DONE.matcher(strip(raw).trim()).matches();
    }

    public record CommissionProgress(String name, double percentage, long current, long target) {
        public boolean hasNumericProgress() {
            return current >= 0 && target > 0;
        }
    }

    public record CrimsonQuest(String name, int amount, boolean readyToCollect) {
    }

    private static boolean looksLikeWidgetTitle(String value) {
        return value.endsWith(":") && !value.matches(".*\\d+%?:$");
    }

    private static String strip(String text) {
        String stripped = ChatFormatting.stripFormatting(text);
        return stripped == null ? "" : stripped;
    }
}

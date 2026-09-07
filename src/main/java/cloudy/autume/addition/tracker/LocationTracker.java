package cloudy.autume.addition.tracker;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class LocationTracker {
    private static final Pattern LOCATION_MARKER = Pattern.compile("[\uE067⏣\uE020ф]");
    private static final Pattern PROFILE_LINE = Pattern.compile("(?i)^Profile:\\s*(.+?)\\s*$");
    private static IslandArea area = IslandArea.NONE;
    private static boolean skyBlock;
    private static String visibleLocation = "";
    private static String profileName = "default";
    private static boolean dungeon;
    private static boolean rift;
    private static List<String> receivedScoreboardLines = List.of();

    private LocationTracker() {
    }

    public static void update(Minecraft client) {
        if (client.level == null || client.player == null) {
            clearWorldContext();
            return;
        }

        List<String> lines = scoreboardLines(client);
        receivedScoreboardLines = List.copyOf(lines);
        String serverBrand = client.player.connection == null
                ? "" : client.player.connection.serverBrand();
        String joined = String.join("\n", lines).toLowerCase(Locale.ROOT);
        skyBlock = HypixelSessionTracker.allowsPassiveSkyBlock(serverBrand, lines);
        if (!skyBlock) {
            area = IslandArea.NONE;
            visibleLocation = "";
            dungeon = false;
            rift = false;
            if (HypixelSessionTracker.hasAuthoritativeLocation()) profileName = "default";
            return;
        }

        // Profile labels are not guaranteed to be present in every scoreboard
        // refresh. Keep the last explicit received label until SkyBlock is
        // exited instead of bouncing the persistence key back to "default".
        observeProfile(lines);
        String apiLocation = (HypixelSessionTracker.mode() + "\n" + HypixelSessionTracker.map())
                .toLowerCase(Locale.ROOT);
        dungeon = containsAny(apiLocation, "dungeon", "catacombs")
                || containsAny(joined, "the catacombs", "dungeon cleared", "cleared:");
        rift = containsAny(apiLocation, "the rift", "rift")
                || containsAny(joined, "the rift", "rift time", "wizard tower");

        visibleLocation = lines.stream()
                .filter(line -> LOCATION_MARKER.matcher(line).find())
                .findFirst()
                .orElse("");
        String evidence = visibleLocation.isEmpty() ? joined : visibleLocation.toLowerCase(Locale.ROOT);
        area = classifyEvidence(evidence);
        if (area == IslandArea.NONE && !apiLocation.isBlank()) area = classifyEvidence(apiLocation);
        if (area == IslandArea.NONE && !visibleLocation.isEmpty()) area = classifyEvidence(joined);
    }

    static IslandArea classifyEvidence(String evidence) {
        evidence = evidence.toLowerCase(Locale.ROOT);
        if (evidence.contains("critter safari entrance")) {
            return IslandArea.TORRHUS_CANYON;
        } else if (containsAny(evidence, "critter safari", "safari zone", "cavern biome", "forest biome",
                "haunted biome", "icy biome")) {
            return IslandArea.CRITTER_SAFARI;
        } else if (containsAny(evidence, "galatea", "agatha's contest", "agathas contest",
                "hina chapter", "hina's chapter", "hinas chapter")) {
            return IslandArea.GALATEA;
        } else if (containsAny(evidence, "torrhus canyon", "torrhus heights", "miria's hut",
                "pangolin hideaway", "spring path", "torrhus springs", "spring shallows",
                "spring depths", "ant's cave", "hotspot haven", "desert temple",
                "critter safari entrance", "helia", "miria's contest", "mirias contest",
                "the benefactor")) {
            return IslandArea.TORRHUS_CANYON;
        } else if (containsAny(evidence, "glacite tunnels", "dwarven base camp", "great glacite lake",
                "fossil research center", "grandpa wolf's cave")) {
            return IslandArea.GLACITE_TUNNELS;
        } else if (containsAny(evidence, "glacite mineshaft", "mineshaft")) {
            return IslandArea.MINESHAFT;
        } else if (containsAny(evidence, "crystal hollows", "crystal nucleus", "mithril deposits",
                "mines of divan", "goblin holdout", "goblin queen's den", "precursor remnants",
                "lost precursor city", "magma fields", "khazad-dûm", "khazad-dum", "fairy grotto",
                "jungle temple", "dragon's lair") || isExactLocation(evidence, "jungle")) {
            return IslandArea.CRYSTAL_HOLLOWS;
        } else if (containsAny(evidence, "dwarven mines", "dwarven village", "dwarven base camp",
                "the forge", "forge basin", "palace bridge", "royal palace", "aristocrat passage",
                "hanging court", "divan's gateway", "far reserve", "goblin burrows", "miner's guild",
                "great ice wall", "the mist", "abandoned quarry", "grand library", "barracks of heroes",
                "the lift", "royal quarters", "lava springs", "cliffside veins", "rampart's quarry",
                "upper mines", "royal mines", "dwarven tavern", "c&c minecarts co.",
                "gates to the mines", "ironman's guild")) {
            return IslandArea.DWARVEN_MINES;
        } else if (containsAny(evidence, "crimson isle", "stronghold", "crimson fields", "blazing volcano",
                "odger's hut", "plhlegblast pool", "magma chamber", "aura's lab", "matriarch's lair",
                "belly of the beast", "dojo", "burning desert", "mystic marsh", "barbarian outpost",
                "mage outpost", "dragontail", "chief's hut", "the dukedom", "the bastion", "scarleton",
                "throne room", "mage council", "igrupan's house", "igrupan's chicken coop", "cathedral",
                "courtyard", "the wasteland", "ruins of ashfang", "forgotten skull", "smoldering tomb")) {
            return IslandArea.CRIMSON_ISLE;
        } else if (containsAny(evidence, "the end", "dragon's nest", "void sepulture", "zealot bruiser hideout")) {
            return IslandArea.THE_END;
        }
        return IslandArea.NONE;
    }

    public static IslandArea area() {
        return area;
    }

    public static boolean isSkyBlock() {
        return skyBlock || HypixelSessionTracker.isSkyBlockConfirmed();
    }

    public static String visibleLocation() {
        return visibleLocation;
    }

    public static String profileName() {
        return profileName;
    }

    /** Updates the profile only from an explicit received Profile line. */
    public static void observeProfile(Iterable<String> receivedLines) {
        if (!isSkyBlock()) return;
        for (String raw : receivedLines) {
            var matcher = PROFILE_LINE.matcher(strip(raw).trim());
            if (!matcher.matches()) continue;
            String observed = matcher.group(1).trim();
            if (!observed.isEmpty()) profileName = observed;
            return;
        }
    }

    public static boolean isDungeon() {
        return dungeon;
    }

    public static boolean isRift() {
        return rift;
    }

    /** Scoreboard text already received by the vanilla client, stripped of formatting. */
    public static List<String> scoreboardLines() {
        return receivedScoreboardLines;
    }

    public static void reset() {
        area = IslandArea.NONE;
        skyBlock = false;
        visibleLocation = "";
        profileName = "default";
        dungeon = false;
        rift = false;
        receivedScoreboardLines = List.of();
    }

    public static void clearWorldContext() {
        area = IslandArea.NONE;
        skyBlock = false;
        visibleLocation = "";
        dungeon = false;
        rift = false;
        receivedScoreboardLines = List.of();
    }

    private static boolean containsAny(String evidence, String... needles) {
        for (String needle : needles) if (evidence.contains(needle)) return true;
        return false;
    }

    /**
     * Some SkyBlock subareas share a word with a different island. In particular,
     * Crystal Hollows has an exact "Jungle" subarea while The Park reports
     * "Jungle Island". Substring matching would incorrectly enable mining HUDs in
     * The Park, so ambiguous short names must match the complete received location.
     */
    private static boolean isExactLocation(String evidence, String expected) {
        String location = LOCATION_MARKER.matcher(evidence).replaceAll("").trim();
        return location.equals(expected);
    }

    private static List<String> scoreboardLines(Minecraft client) {
        List<String> result = new ArrayList<>();
        Scoreboard scoreboard = client.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.BY_ID.apply(1));
        if (objective == null) return result;

        Component title = objective.getDisplayName();
        result.add(strip(title.getString()));
        for (ScoreHolder holder : scoreboard.getTrackedPlayers()) {
            if (!scoreboard.listPlayerScores(holder).containsKey(objective)) continue;
            PlayerTeam team = scoreboard.getPlayersTeam(holder.getScoreboardName());
            if (team == null) continue;
            String line = team.getPlayerPrefix().getString() + team.getPlayerSuffix().getString();
            line = strip(line).trim();
            if (!line.isEmpty()) result.add(line);
        }
        return result;
    }

    private static String strip(String text) {
        String stripped = ChatFormatting.stripFormatting(text);
        return stripped == null ? "" : stripped;
    }
}

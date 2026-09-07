package cloudy.autume.addition.tracker;

import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.packet.impl.clientbound.ClientboundHelloPacket;
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Single source of truth for the current Hypixel session.
 *
 * <p>Official Mod API packets are authoritative. The server-list address is
 * deliberately not used because accelerators and local proxies preserve the
 * Hypixel connection while exposing a different address to Minecraft.</p>
 */
public final class HypixelSessionTracker {
    private static final Pattern LOCATION_MARKER = Pattern.compile("[\uE067⏣\uE020ф]");
    private static final Pattern PROFILE_LINE = Pattern.compile("(?i)^Profile:\\s*\\S.*$");
    private static final Pattern PURSE_LINE = Pattern.compile("(?i)^Purse:\\s*\\S.*$");
    private static final Pattern RIFT_TIME_LINE = Pattern.compile("(?i)^Rift Time:\\s*\\S.*$");
    private static volatile Snapshot snapshot = Snapshot.empty();
    private static boolean initialized;

    private HypixelSessionTracker() {
    }

    /** Registers the official API handlers once for the complete client lifecycle. */
    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        HypixelModAPI api = HypixelModAPI.getInstance();
        api.subscribeToEventPacket(ClientboundLocationPacket.class);
        api.createHandler(ClientboundHelloPacket.class, packet -> onClientThread(() -> {
            markHello();
            LocationTracker.update(Minecraft.getInstance());
        }));
        api.createHandler(ClientboundLocationPacket.class, packet -> onClientThread(() -> {
            markLocation(packet.getServerName(),
                    packet.getServerType().map(type -> type.getName()).orElse(""),
                    packet.getLobbyName().orElse(""),
                    packet.getMode().orElse(""),
                    packet.getMap().orElse(""));
            LocationTracker.update(Minecraft.getInstance());
        }));
    }

    private static void onClientThread(Runnable action) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        client.execute(action);
    }

    /** Starts a fresh physical connection without trusting its typed address. */
    public static void beginConnection() {
        snapshot = Snapshot.empty();
    }

    static void markHello() {
        Snapshot current = snapshot;
        snapshot = new Snapshot(true, current.locationReceived(), current.serverName(),
                current.serverType(), current.lobbyName(), current.mode(), current.map());
    }

    static void markLocation(String serverName, String serverType, String lobbyName,
                             String mode, String map) {
        snapshot = new Snapshot(true, true, safe(serverName), safe(serverType),
                safe(lobbyName), safe(mode), safe(map));
    }

    /** Clears instance-specific data immediately so it cannot leak across world changes. */
    public static void onWorldChange() {
        Snapshot current = snapshot;
        snapshot = current.hypixelConfirmed()
                ? new Snapshot(true, false, "", "", "", "", "")
                : Snapshot.empty();
    }

    public static void reset() {
        snapshot = Snapshot.empty();
    }

    public static boolean isHypixelConfirmed() {
        return snapshot.hypixelConfirmed();
    }

    public static boolean canSendHypixelCommand() {
        return snapshot.hypixelConfirmed();
    }

    public static boolean canUseDungeonQuickView() {
        return isSkyBlockConfirmed();
    }

    public static boolean isSkyBlockConfirmed() {
        Snapshot current = snapshot;
        return current.locationReceived() && "SKYBLOCK".equalsIgnoreCase(current.serverType());
    }

    public static boolean hasAuthoritativeLocation() {
        return snapshot.locationReceived();
    }

    public static String serverName() {
        return snapshot.serverName();
    }

    public static String serverType() {
        return snapshot.serverType();
    }

    public static String lobbyName() {
        return snapshot.lobbyName();
    }

    public static String mode() {
        return snapshot.mode();
    }

    public static String map() {
        return snapshot.map();
    }

    /**
     * Allows passive SkyBlock rendering while an official Location packet is pending.
     * A known Hypixel hello or the exact Hypixel proxy brand must be accompanied by a
     * strict SkyBlock sidebar signature. It never grants command-sending authority.
     */
    public static boolean allowsPassiveSkyBlock(String serverBrand, List<String> scoreboardLines) {
        if (isSkyBlockConfirmed()) return true;
        if (hasAuthoritativeLocation()) return false;
        return (isHypixelConfirmed() || isHypixelBrand(serverBrand))
                && hasStrictSkyBlockScoreboard(scoreboardLines);
    }

    static boolean isHypixelBrand(String rawBrand) {
        String brand = safe(rawBrand).trim().toLowerCase(Locale.ROOT);
        if (!brand.startsWith("hypixel bungeecord")) return false;
        if (brand.length() == "hypixel bungeecord".length()) return true;
        char suffix = brand.charAt("hypixel bungeecord".length());
        return Character.isWhitespace(suffix) || suffix == '/' || suffix == '(';
    }

    static boolean hasStrictSkyBlockScoreboard(List<String> lines) {
        if (lines == null || lines.size() < 2) return false;
        String title = lettersOnly(lines.getFirst());
        if (!title.equals("skyblock") && !title.equals("skyblockcoop")) return false;
        for (int index = 1; index < lines.size(); index++) {
            String line = safe(lines.get(index)).trim();
            if (LOCATION_MARKER.matcher(line).find()
                    || PROFILE_LINE.matcher(line).matches()
                    || PURSE_LINE.matcher(line).matches()
                    || RIFT_TIME_LINE.matcher(line).matches()
                    || line.toLowerCase(Locale.ROOT).contains("the catacombs")) {
                return true;
            }
        }
        return false;
    }

    private static String lettersOnly(String value) {
        String input = safe(value);
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < input.length(); index++) {
            char character = Character.toLowerCase(input.charAt(index));
            if (character >= 'a' && character <= 'z') result.append(character);
        }
        return result.toString();
    }

    private static String safe(String value) {
        return Objects.requireNonNullElse(value, "");
    }

    private record Snapshot(boolean hypixelConfirmed, boolean locationReceived,
                            String serverName, String serverType, String lobbyName,
                            String mode, String map) {
        private static Snapshot empty() {
            return new Snapshot(false, false, "", "", "", "", "");
        }
    }
}

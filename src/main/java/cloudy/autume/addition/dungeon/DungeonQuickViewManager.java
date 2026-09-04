package cloudy.autume.addition.dungeon;

import cloudy.autume.addition.QCloudyAdditionClient;
import cloudy.autume.addition.config.ConfigManager;
import cloudy.autume.addition.network.QcaApiClient;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Runtime boundary for the automatic, Dungeon-only quick view. */
public final class DungeonQuickViewManager {
    private static final DungeonQuickViewService SERVICE = new DungeonQuickViewService(
            QcaApiClient.createDefault(userAgent()), Clock.systemUTC());
    private static final Map<String, Long> RECENT_JOINS = new HashMap<>();
    private static DungeonFloor currentFloor;
    private static long session;

    private DungeonQuickViewManager() { }

    public static void updateScoreboard(List<String> lines) {
        currentFloor = DungeonFloor.fromScoreboard(lines).orElse(null);
    }

    public static void onMessage(Minecraft client, Component message) {
        if (!ConfigManager.get().dungeons.playerQuickView || client.player == null || message == null) return;
        DungeonJoinParser.newcomer(message.getString()).ifPresent(player -> request(client, player));
    }

    private static void request(Minecraft client, String player) {
        if (player.equalsIgnoreCase(client.getUser().getName())) return;
        long now = System.nanoTime();
        String key = player.toLowerCase(Locale.ROOT);
        Long last = RECENT_JOINS.put(key, now);
        if (last != null && now - last < 2_000_000_000L) return;
        RECENT_JOINS.entrySet().removeIf(entry -> now - entry.getValue() > 30_000_000_000L);

        long requestSession = session;
        String floor = currentFloor == null ? "" : currentFloor.id();
        SERVICE.load(player, floor).whenComplete((snapshot, failure) -> client.execute(() -> {
            if (requestSession != session || client.player == null) return;
            DungeonQuickViewSnapshot shown = snapshot;
            if (failure != null) {
                Throwable cause = failure;
                while (cause instanceof java.util.concurrent.CompletionException
                        && cause.getCause() != null) cause = cause.getCause();
                String reason = cause.getMessage() == null
                        ? "Dungeon profile data is unavailable." : cause.getMessage();
                shown = DungeonQuickViewSnapshot.missing(player, floor, reason);
                QCloudyAdditionClient.LOGGER.warn("Could not load Dungeon Quick View for {}", player, cause);
            }
            client.player.sendSystemMessage(DungeonQuickViewMessage.build(shown, client.font));
        }));
    }

    public static void reset() {
        session++;
        currentFloor = null;
        RECENT_JOINS.clear();
        SERVICE.reset();
    }

    private static String userAgent() {
        String version = FabricLoader.getInstance()
                .getModContainer(QCloudyAdditionClient.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        return ("QCloudy_Addition/" + version).replaceAll("[^A-Za-z0-9._+/-]", "_");
    }
}

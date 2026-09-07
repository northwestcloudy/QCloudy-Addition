package cloudy.autume.addition.dungeon;

import cloudy.autume.addition.QCloudyAdditionClient;
import cloudy.autume.addition.config.ConfigManager;
import cloudy.autume.addition.network.QcaApiClient;
import cloudy.autume.addition.tracker.LocationTracker;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Runtime boundary for the automatic, Dungeon-only quick view. */
public final class DungeonQuickViewManager {
    private static final DungeonQuickViewService SERVICE = new DungeonQuickViewService(
            QcaApiClient.createDefault(userAgent()), Clock.systemUTC());
    private static final Map<String, Long> RECENT_JOINS = new HashMap<>();
    private static final DungeonQuickViewFailureGate FAILURE_GATE =
            new DungeonQuickViewFailureGate(Duration.ofSeconds(30));
    private static DungeonFloor currentFloor;
    private static long session;

    private DungeonQuickViewManager() { }

    public static void updateScoreboard(List<String> lines) {
        currentFloor = DungeonFloor.retainWhileQueued(currentFloor, lines).orElse(null);
    }

    public static void onMessage(Minecraft client, Component message) {
        if (!ConfigManager.get().dungeons.playerQuickView || client.player == null || message == null) return;
        DungeonJoinParser.newcomer(message.getString()).ifPresent(player -> {
            updateScoreboard(LocationTracker.liveScoreboardLines(client));
            request(client, player);
        });
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
        DungeonQuickViewSnapshot cached = SERVICE.cached(player, floor);
        if (cached != null) {
            client.player.sendSystemMessage(DungeonQuickViewMessage.build(cached, client.font));
            return;
        }
        if (!FAILURE_GATE.allowRequest(now)) return;
        SERVICE.load(player, floor).whenComplete((snapshot, failure) -> client.execute(() -> {
            if (requestSession != session || client.player == null) return;
            if (failure != null) {
                DungeonQuickViewException problem = failure(failure);
                boolean notify = FAILURE_GATE.recordFailure(problem.isServiceFailure(), System.nanoTime());
                if (notify) {
                    QCloudyAdditionClient.LOGGER.warn(
                            "Could not load Dungeon Quick View for {}", player, problem);
                    client.player.sendSystemMessage(
                            DungeonQuickViewMessage.unavailable(player, problem.getMessage()));
                }
                return;
            }
            FAILURE_GATE.recordSuccess();
            client.player.sendSystemMessage(DungeonQuickViewMessage.build(snapshot, client.font));
        }));
    }

    public static void reset() {
        session++;
        currentFloor = null;
        RECENT_JOINS.clear();
        FAILURE_GATE.reset();
        SERVICE.reset();
    }

    private static DungeonQuickViewException failure(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof java.util.concurrent.CompletionException
                && cause.getCause() != null) cause = cause.getCause();
        return cause instanceof DungeonQuickViewException exception ? exception
                : new DungeonQuickViewException("Dungeon profile request failed.", cause);
    }

    private static String userAgent() {
        String version = FabricLoader.getInstance()
                .getModContainer(QCloudyAdditionClient.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        return ("QCloudy_Addition/" + version).replaceAll("[^A-Za-z0-9._+/-]", "_");
    }
}

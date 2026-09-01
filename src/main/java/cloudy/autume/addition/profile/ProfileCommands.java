package cloudy.autume.addition.profile;

import cloudy.autume.addition.QCloudyAdditionClient;
import cloudy.autume.addition.compat.MinecraftClientCompat;
import cloudy.autume.addition.network.QcaApiClient;
import cloudy.autume.addition.profile.market.MarketTooltipPriceService;
import cloudy.autume.addition.profile.ui.ProfileViewerScreen;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.time.Clock;
import java.util.regex.Pattern;

/** Registers QCA's conflict-free, client-only profile viewer commands. */
public final class ProfileCommands {
    static final String DOUBLE_SLASH_ROOT = "/pv";
    static final String SINGLE_SLASH_ROOT = "qpv";
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern COMPACT_UUID = Pattern.compile("[0-9a-fA-F]{32}");
    private static final Pattern DASHED_UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private ProfileCommands() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        register(dispatcher, ServiceHolder.PROFILES, ServiceHolder.MARKET_PRICES);
    }

    static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, ProfileService service) {
        register(dispatcher, service, ServiceHolder.MARKET_PRICES);
    }

    static void register(CommandDispatcher<FabricClientCommandSource> dispatcher,
                         ProfileService service,
                         MarketTooltipPriceService priceService) {
        // Minecraft strips the first slash before Brigadier parses a command.
        // A literal beginning with '/' is therefore reached through //pv only.
        registerRoot(dispatcher, DOUBLE_SLASH_ROOT, "//pv", service, priceService);
        registerRoot(dispatcher, SINGLE_SLASH_ROOT, "/qpv", service, priceService);
    }

    private static void registerRoot(CommandDispatcher<FabricClientCommandSource> dispatcher,
                                     String root,
                                     String displayName,
                                     ProfileService service,
                                     MarketTooltipPriceService priceService) {
        if (dispatcher.getRoot().getChild(root) != null) {
            QCloudyAdditionClient.LOGGER.warn(
                    "Skipping client command {} because another mod already registered its root", displayName);
            return;
        }
        dispatcher.register(ClientCommands.literal(root)
                .executes(context -> open(context.getSource(), null, service, priceService))
                .then(ClientCommands.argument("player", StringArgumentType.word())
                        .executes(context -> open(context.getSource(),
                                StringArgumentType.getString(context, "player"), service, priceService))));
    }

    private static int open(FabricClientCommandSource source,
                            String requestedTarget,
                            ProfileService service,
                            MarketTooltipPriceService priceService) {
        Minecraft client = source.getClient();
        String target = normalizeTarget(requestedTarget, client.getUser().getName());
        client.execute(() -> MinecraftClientCompat.setScreen(client,
                new ProfileViewerScreen(MinecraftClientCompat.screen(client), target,
                        service, priceService)));
        return 1;
    }

    static String normalizeTarget(String requestedTarget, String localPlayerName) {
        String requested = requestedTarget == null ? "" : requestedTarget.trim();
        if (!requested.isEmpty()) return requested;
        return localPlayerName == null ? "" : localPlayerName.trim();
    }

    static boolean isSupportedTarget(String target) {
        if (target == null) return false;
        String value = target.trim();
        return PLAYER_NAME.matcher(value).matches()
                || COMPACT_UUID.matcher(value).matches()
                || DASHED_UUID.matcher(value).matches();
    }

    private static String userAgent() {
        String version = FabricLoader.getInstance().getModContainer(QCloudyAdditionClient.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        return ("QCloudy_Addition/" + version).replaceAll("[^A-Za-z0-9._+/-]", "_");
    }

    private static final class ServiceHolder {
        private static final QcaApiClient API = QcaApiClient.createDefault(userAgent());
        private static final Clock CLOCK = Clock.systemUTC();
        private static final ProfileService PROFILES = new ProfileService(API, CLOCK);
        private static final MarketTooltipPriceService MARKET_PRICES =
                new MarketTooltipPriceService(API, CLOCK);
    }
}

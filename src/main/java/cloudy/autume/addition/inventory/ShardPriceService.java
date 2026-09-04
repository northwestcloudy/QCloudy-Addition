package cloudy.autume.addition.inventory;

import cloudy.autume.addition.QCloudyAdditionClient;
import cloudy.autume.addition.network.QcaApiClient;
import cloudy.autume.addition.market.shard.ShardBazaarLoadResult;
import cloudy.autume.addition.market.shard.ShardBazaarService;
import cloudy.autume.addition.market.shard.ShardBazaarSide;
import cloudy.autume.addition.market.shard.ShardBazaarSnapshot;
import net.fabricmc.loader.api.FabricLoader;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded bridge from the Shard Planner to QCloudy's transformed Bazaar
 * snapshot. Network work is initiated by the planner's existing background
 * future; this class never contacts Hypixel directly and never reads another
 * mod's private price structures.
 */
public final class ShardPriceService {
    private static final ShardPriceService INSTANCE = new ShardPriceService(
            new ShardBazaarService(QcaApiClient.createDefault(userAgent()), Clock.systemUTC()));

    private final ShardBazaarService bazaarService;
    private volatile Availability availability = Availability.NOT_LOADED;
    private volatile String sourceName = "QCloudy market snapshot (not loaded)";

    ShardPriceService(ShardBazaarService bazaarService) {
        this.bazaarService = java.util.Objects.requireNonNull(bazaarService, "bazaarService");
    }

    public static ShardPriceService instance() {
        return INSTANCE;
    }

    public Availability availability() {
        return availability;
    }

    public String sourceName() {
        return sourceName;
    }

    /**
     * Returns catalog Shard IDs mapped to positive Bazaar values. The boolean
     * keeps its historical UI meaning: true is instant-buy/acquisition cost;
     * false is instant-sell/liquidation value.
     */
    public Map<String, Double> snapshot(boolean instantBuy) {
        ShardBazaarSide side = instantBuy
                ? ShardBazaarSide.INSTANT_BUY : ShardBazaarSide.INSTANT_SELL;
        try {
            ShardBazaarLoadResult result = bazaarService.load(side).join();
            ShardBazaarSnapshot snapshot = result.snapshot();
            Map<String, Double> prices = new LinkedHashMap<>();
            for (ShardFusionCatalog.Shard shard : ShardFusionCatalog.instance().shards()) {
                snapshot.price(shard.bazaarId()).ifPresent(price -> prices.put(shard.id(), price));
            }
            availability = prices.isEmpty() ? Availability.NO_DATA : Availability.AVAILABLE;
            String cacheLabel = result.fromSessionCache() ? " · client cache" : "";
            String staleLabel = snapshot.stale() ? " · stale" : "";
            sourceName = "QCloudy " + snapshot.source() + cacheLabel + staleLabel;
            return Map.copyOf(prices);
        } catch (RuntimeException exception) {
            availability = Availability.UNAVAILABLE;
            sourceName = "QCloudy market snapshot (unavailable)";
            QCloudyAdditionClient.LOGGER.warn("Could not load the QCloudy Shard price snapshot", exception);
            return Map.of();
        }
    }

    /** Clears only the bounded in-process price cache. */
    public void reset() {
        bazaarService.clearSessionCache();
        availability = Availability.NOT_LOADED;
        sourceName = "QCloudy market snapshot (not loaded)";
    }

    private static String userAgent() {
        String version = FabricLoader.getInstance()
                .getModContainer(QCloudyAdditionClient.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        return ("QCloudy_Addition/" + version).replaceAll("[^A-Za-z0-9._+/-]", "_");
    }

    public enum Availability {
        AVAILABLE(true), NOT_LOADED(false), NO_DATA(false), UNAVAILABLE(false);

        private final boolean available;

        Availability(boolean available) {
            this.available = available;
        }

        public boolean available() {
            return available;
        }
    }
}

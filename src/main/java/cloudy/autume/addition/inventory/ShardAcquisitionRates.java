package cloudy.autume.addition.inventory;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Offline baseline acquisition rates used by the Ironman route planner. */
public final class ShardAcquisitionRates {
    public static final String RESOURCE = "/assets/qcloudy_addition/data/shard_rates.json";

    private final String source;
    private final String sourceCommit;
    private final Map<String, Double> rates;

    private ShardAcquisitionRates(RawRates raw) {
        if (raw == null || raw.schemaVersion != 1 || raw.rates == null) {
            throw new IllegalStateException("Invalid Shard acquisition-rate resource");
        }
        source = text(raw.source);
        sourceCommit = text(raw.sourceCommit);
        Set<String> expectedIds = ShardFusionCatalog.instance().shards().stream()
                .map(ShardFusionCatalog.Shard::id)
                .collect(Collectors.toUnmodifiableSet());
        if (raw.rates.size() != ShardFusionCatalog.EXPECTED_SHARD_COUNT
                || !raw.rates.keySet().equals(expectedIds)) {
            throw new IllegalStateException("Shard acquisition-rate IDs do not match the "
                    + ShardFusionCatalog.EXPECTED_SHARD_COUNT + "-Shard catalog");
        }
        Map<String, Double> loaded = new LinkedHashMap<>();
        for (ShardFusionCatalog.Shard shard : ShardFusionCatalog.instance().shards()) {
            Double value = raw.rates.get(shard.id());
            if (value == null || !Double.isFinite(value) || value < 0.0) {
                throw new IllegalStateException("Invalid Shard rate for " + shard.id());
            }
            double rate = value;
            loaded.put(shard.id(), rate);
        }
        if (loaded.size() != ShardFusionCatalog.EXPECTED_SHARD_COUNT) {
            throw new IllegalStateException("Expected " + ShardFusionCatalog.EXPECTED_SHARD_COUNT
                    + " Shard rates, found " + loaded.size());
        }
        rates = Map.copyOf(loaded);
    }

    public static ShardAcquisitionRates instance() {
        return Holder.INSTANCE;
    }

    public static ShardAcquisitionRates load() {
        try (var stream = ShardAcquisitionRates.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing " + RESOURCE);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return new ShardAcquisitionRates(new Gson().fromJson(reader, RawRates.class));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read Shard acquisition rates", exception);
        }
    }

    public double rate(String shardId) {
        return rates.getOrDefault(shardId, 0.0);
    }

    public Map<String, Double> rates() {
        return rates;
    }

    public String source() {
        return source;
    }

    public String sourceCommit() {
        return sourceCommit;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class Holder {
        private static final ShardAcquisitionRates INSTANCE = load();
    }

    @SuppressWarnings("unused")
    private static final class RawRates {
        int schemaVersion;
        String source;
        String sourceCommit;
        Map<String, Double> rates;
    }
}

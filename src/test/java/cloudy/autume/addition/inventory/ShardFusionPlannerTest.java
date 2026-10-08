package cloudy.autume.addition.inventory;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShardFusionPlannerTest {
    private final ShardFusionCatalog catalog = ShardFusionCatalog.load();
    private final ShardFusionPlanner planner = new ShardFusionPlanner(catalog);

    @Test
    void acquisitionRateSnapshotMatchesTheCurrent324Catalog() {
        var rates = ShardAcquisitionRates.load();
        assertEquals(324, rates.rates().size());
        assertEquals(catalog.shards().stream().map(ShardFusionCatalog.Shard::id).sorted().toList(),
                rates.rates().keySet().stream().sorted().toList());
        assertFalse(rates.source().isBlank());
        assertFalse(rates.sourceCommit().isBlank());
    }

    @Test
    void usesOfficial0272ShopStocksAndEvidenceBoundedNewShardRates() {
        var rates = ShardAcquisitionRates.load();
        assertEquals(24.0 / 124.0, rates.rate("C22"));
        assertEquals(18.0 / 124.0, rates.rate("U106"));
        assertEquals(12.0 / 124.0, rates.rate("R94"));
        assertEquals(6.0 / 124.0, rates.rate("E46"));
        assertEquals(64.0 / 124.0, rates.rate("U73"));
        assertEquals(0.0, rates.rate("C48"));
        assertEquals(0.0, rates.rate("R28"));
        assertEquals(0.0, rates.rate("L52"));
    }

    @Test
    void buildsAMultiStepIronmanTreeForAFusionOnlyShard() {
        var target = catalog.byName("Pandarai").orElseThrow();
        Map<String, Double> rates = new LinkedHashMap<>();
        for (var shard : catalog.shards()) rates.put(shard.id(), 1_000.0);
        rates.put(target.id(), 0.0);

        var plan = planner.plan(target.id(), 3, parameters(
                ShardFusionPlanner.Mode.IRONMAN, ShardFusionPlanner.Objective.FASTEST,
                rates, Map.of(), Map.of(), false));

        assertTrue(plan.possible(), plan.problem());
        assertEquals(ShardFusionPlanner.Acquisition.FUSION, plan.root().method());
        assertFalse(plan.root().inputs().isEmpty());
        assertTrue(plan.crafts() > 0);
        assertTrue(plan.huntMaterials().values().stream().mapToInt(Integer::intValue).sum() > 0);
    }

    @Test
    void cheapestNormalRouteUsesTheOptionalPriceSnapshot() {
        var target = catalog.byName("Pandarai").orElseThrow();
        Map<String, Double> prices = new LinkedHashMap<>();
        for (var shard : catalog.shards()) prices.put(shard.id(), 1.0);
        prices.put(target.id(), 1_000_000.0);

        var plan = planner.plan(target.id(), 1, parameters(
                ShardFusionPlanner.Mode.NORMAL, ShardFusionPlanner.Objective.CHEAPEST,
                Map.of(), prices, Map.of(), false));

        assertTrue(plan.possible(), plan.problem());
        assertEquals(ShardFusionPlanner.Acquisition.FUSION, plan.root().method());
        assertTrue(plan.estimatedCost() < 1_000_000.0);
    }

    @Test
    void cheapestModeIsExplicitlyUnavailableWithoutACompatiblePriceProvider() {
        var plan = planner.plan("L4", 1, parameters(
                ShardFusionPlanner.Mode.NORMAL, ShardFusionPlanner.Objective.CHEAPEST,
                Map.of(), Map.of(), Map.of(), false));

        assertFalse(plan.possible());
        assertTrue(plan.problem().contains("price provider"));
    }

    @Test
    void ironmanNeverRequiresOrUsesBazaarEvenIfCheapestWasPersisted() {
        var parameters = parameters(ShardFusionPlanner.Mode.IRONMAN,
                ShardFusionPlanner.Objective.CHEAPEST,
                Map.of("L4", 2.0), Map.of("L4", 1.0), Map.of(), false);

        var plan = planner.plan("L4", 1, parameters);

        assertEquals(ShardFusionPlanner.Objective.FASTEST, parameters.objective());
        assertTrue(plan.possible(), plan.problem());
        assertEquals(ShardFusionPlanner.Acquisition.HUNT, plan.root().method());
    }

    @Test
    void warehouseQuantityOffsetsTheRequestedTarget() {
        var plan = planner.plan("L4", 4, parameters(
                ShardFusionPlanner.Mode.IRONMAN, ShardFusionPlanner.Objective.FASTEST,
                Map.of("L4", 1.0), Map.of(), Map.of("L4", 4), true));

        assertTrue(plan.possible(), plan.problem());
        assertEquals(ShardFusionPlanner.Acquisition.INVENTORY, plan.root().method());
        assertEquals(4, plan.inventoryUsed().get("L4"));
        assertEquals(0.0, plan.estimatedCost());
    }

    @Test
    void krakenKuudraModelUsesTierTimeAndCoinOpportunityCost() {
        double slow = ShardFusionPlanner.kuudraRate(ShardFusionPlanner.KuudraTier.T5,
                1_000_000.0, 120);
        double fast = ShardFusionPlanner.kuudraRate(ShardFusionPlanner.KuudraTier.T5,
                20_000_000.0, 80);
        assertTrue(slow > 0.0);
        assertTrue(fast > slow);
        assertEquals(0.0, ShardFusionPlanner.kuudraRate(
                ShardFusionPlanner.KuudraTier.NONE, 20_000_000.0, 80));
    }

    private static ShardFusionPlanner.Parameters parameters(
            ShardFusionPlanner.Mode mode, ShardFusionPlanner.Objective objective,
            Map<String, Double> rates, Map<String, Double> prices,
            Map<String, Integer> warehouse, boolean useWarehouse) {
        return new ShardFusionPlanner.Parameters(mode, objective, rates, prices, warehouse,
                useWarehouse, 0, 0, 10_000_000.0, 0,
                ShardFusionPlanner.KuudraTier.NONE, 60);
    }
}

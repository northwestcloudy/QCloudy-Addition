package cloudy.autume.addition.inventory;

import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShardFusionCatalogTest {
    private final ShardFusionCatalog catalog = ShardFusionCatalog.load();

    @AfterEach
    void resetShardItemSessionCache() {
        ShardItemResolver.resetSessionCache();
    }

    @Test
    void loadsTheOfficial324ShardSetWithoutLegacyRainbug() {
        assertEquals(324, catalog.shards().size());
        assertEquals(324, catalog.shards().stream().map(ShardFusionCatalog.Shard::id).distinct().count());
        assertEquals(324, catalog.shards().stream().map(ShardFusionCatalog.Shard::name).distinct().count());
        assertEquals(324, catalog.shards().stream().map(ShardFusionCatalog.Shard::bazaarId).distinct().count());
        assertEquals(323, catalog.shards().stream().map(ShardFusionCatalog.Shard::internalId)
                .filter(value -> !value.isBlank()).distinct().count());
        assertEquals(293, catalog.shards().stream().filter(shard -> !shard.idResult().isBlank()).count());
        assertEquals(100, catalog.shards().stream().filter(shard ->
                !shard.specialLeft().isBlank() || !shard.specialRight().isBlank()).count());

        assertEquals("Anteater", catalog.byId("R70").orElseThrow().name());
        assertEquals("Zombuddy", catalog.byId("R84").orElseThrow().name());
        assertEquals("Troodon", catalog.byId("R86").orElseThrow().name());
        assertEquals("Goldolot", catalog.byId("R92").orElseThrow().name());
        assertEquals("Ghost Crab", catalog.byId("L38").orElseThrow().name());
        assertEquals("Chocobun", catalog.byId("C48").orElseThrow().name());
        assertEquals("Folf", catalog.byId("R28").orElseThrow().name());
        assertEquals("Flora", catalog.byId("L52").orElseThrow().name());
        var packrat = catalog.byId("U73").orElseThrow();
        assertEquals("Packrat", packrat.name());
        assertEquals("SHARD_PACKRAT_SKULL", packrat.bazaarId());
        assertTrue(packrat.internalId().isBlank());
        assertSame(packrat, catalog.byItemId("SHARD_PACKRAT_SKULL").orElseThrow());
        assertEquals(List.of("SHARD_PACKRAT_SKULL"), catalog.sources().unresolvedInternalIds());
        assertTrue(catalog.byName("Rainbug").isEmpty());
    }

    @Test
    void usesExactSkyBlockRarityAndSemanticColors() {
        assertEquals(0xFFAA00AA, ShardFusionCatalog.Rarity.EPIC.color());
        assertEquals(0xFF55FF55, ShardFusionCatalog.TextTone.GREEN.color());
        assertEquals(0xFFFFFF55, ShardFusionCatalog.TextTone.YELLOW.color());

        var gemzie = catalog.byName("Gemzie").orElseThrow();
        assertEquals(ShardFusionCatalog.Rarity.EPIC, gemzie.rarity());
        assertTrue(gemzie.effect().stream().anyMatch(span -> span.text().contains("Gemstone Spread")
                && span.tone() == ShardFusionCatalog.TextTone.YELLOW));

        var howlingSpirit = catalog.byName("Howling Spirit").orElseThrow();
        assertTrue(howlingSpirit.effect().stream().anyMatch(span -> span.text().contains("Defense")
                && span.tone() == ShardFusionCatalog.TextTone.GREEN));
        assertTrue(howlingSpirit.effect().stream().anyMatch(span -> span.text().equals("Animal")
                && span.tone() == ShardFusionCatalog.TextTone.GREEN));
    }

    @Test
    void everyShardHasEffectAndAcquisitionDetails() {
        for (var shard : catalog.shards()) {
            assertFalse(shard.effect().isEmpty(), shard.id());
            assertTrue(shard.effect().stream().allMatch(span -> !span.text().isBlank()), shard.id());
            assertFalse(shard.acquisition().isEmpty(), shard.id());
            assertTrue(shard.acquisition().stream().allMatch(method -> !method.text().isBlank()), shard.id());
        }

        var gemzie = catalog.byName("Gemzie").orElseThrow();
        assertEquals("Grants +0.25–2.5 Gemstone Spread",
                gemzie.effect().stream().map(ShardFusionCatalog.TextSpan::text)
                        .collect(java.util.stream.Collectors.joining()));
        assertTrue(gemzie.acquisition().stream().anyMatch(method ->
                method.text().contains("Critter Capsule")
                        && method.text().contains("Cavern Biome")
                        && method.kind() == ShardFusionCatalog.AcquisitionKind.CAPTURE));
    }

    @Test
    void fusionSourcesIncludeNaturalAndFusionOnlyShards() {
        var pandarai = catalog.byName("Pandarai").orElseThrow();
        assertTrue(pandarai.fusionOnly());
        assertFalse(catalog.recipesForOutput(pandarai.id()).isEmpty());

        var queenBee = catalog.byName("Queen Bee").orElseThrow();
        assertFalse(queenBee.fusionOnly());
        assertFalse(queenBee.acquisition().isEmpty());
        assertFalse(catalog.recipesForOutput(queenBee.id()).isEmpty());
    }

    @Test
    void searchesCanonicalNamesIdsAttributesAndMetadata() {
        assertEquals("Goldolot", catalog.search("Goldolot").getFirst().name());
        assertEquals("Troodon", catalog.search("R86").getFirst().name());
        assertFalse(catalog.search("Elemental").isEmpty());
        assertFalse(catalog.search("Forest").isEmpty());
        assertTrue(catalog.search("Gemstone Spread").stream()
                .anyMatch(shard -> shard.name().equals("Gemzie")));
        assertTrue(catalog.search("Cavern Biome").stream()
                .anyMatch(shard -> shard.name().equals("Gemzie")));
        assertFalse(catalog.search("Aquatic").isEmpty());
        assertTrue(catalog.search("definitely-not-a-shard").isEmpty());
    }

    @Test
    void everyCurrentShardHasACompleteOfflineIconResourceSet() throws Exception {
        Set<String> resourceIds = new HashSet<>();
        for (var shard : catalog.shards()) {
            String resourceId = shard.id().toLowerCase(Locale.ROOT);
            assertTrue(resourceIds.add(resourceId), "duplicate icon resource " + resourceId);

            String itemPath = "/assets/qcloudy_addition/items/shards/" + resourceId + ".json";
            try (InputStream stream = ShardFusionCatalogTest.class.getResourceAsStream(itemPath)) {
                assertNotNull(stream, itemPath);
                var root = JsonParser.parseReader(new java.io.InputStreamReader(stream)).getAsJsonObject();
                assertEquals("minecraft:model", root.getAsJsonObject("model").get("type").getAsString());
                assertEquals("qcloudy_addition:item/shards/" + resourceId,
                        root.getAsJsonObject("model").get("model").getAsString());
            }

            String modelPath = "/assets/qcloudy_addition/models/item/shards/" + resourceId + ".json";
            try (InputStream stream = ShardFusionCatalogTest.class.getResourceAsStream(modelPath)) {
                assertNotNull(stream, modelPath);
                var root = JsonParser.parseReader(new java.io.InputStreamReader(stream)).getAsJsonObject();
                assertEquals("minecraft:item/generated", root.get("parent").getAsString());
                assertEquals("qcloudy_addition:item/shards/" + resourceId,
                        root.getAsJsonObject("textures").get("layer0").getAsString());
            }

            String texturePath = "/assets/qcloudy_addition/textures/item/shards/" + resourceId + ".png";
            try (InputStream stream = ShardFusionCatalogTest.class.getResourceAsStream(texturePath)) {
                assertNotNull(stream, texturePath);
                var image = ImageIO.read(stream);
                assertNotNull(image, "invalid PNG " + texturePath);
                assertTrue(image.getWidth() >= 16 && image.getWidth() <= 64, texturePath);
                assertTrue(image.getHeight() >= 16 && image.getHeight() <= 64, texturePath);
                assertTrue(image.getColorModel().hasAlpha(), texturePath);
                boolean hasVisiblePixel = false;
                for (int y = 0; y < image.getHeight() && !hasVisiblePixel; y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        if ((image.getRGB(x, y) >>> 24) != 0) {
                            hasVisiblePixel = true;
                            break;
                        }
                    }
                }
                assertTrue(hasVisiblePixel, "fully transparent PNG " + texturePath);
            }
        }

        assertEquals(324, resourceIds.size());
        assertNull(ShardFusionCatalogTest.class.getResource(
                "/assets/qcloudy_addition/textures/item/shards/l49.png"));
        assertNull(ShardFusionCatalogTest.class.getResource(
                "/assets/qcloudy_addition/models/item/shards/l49.json"));
        assertNull(ShardFusionCatalogTest.class.getResource(
                "/assets/qcloudy_addition/items/shards/l49.json"));
    }

    @Test
    void bundledIconUsesItsOverrideableLocalItemModel() {
        var shard = catalog.byName("Chameleon").orElseThrow();
        assertEquals(Identifier.parse("qcloudy_addition:shards/l4"),
                ShardItemResolver.bundledModel(shard));
    }

    @Test
    void normalizesOnlyCanonicalShardNamesForObservedOverrides() {
        assertEquals("Chameleon", ShardItemResolver.canonicalName(" Chameleon Shard "));
        assertEquals("", ShardItemResolver.canonicalName("Chameleon"));
        assertEquals("", ShardItemResolver.canonicalName(null));
    }

    @Test
    void firstInputControlsHowManyOfBothInputsAreConsumed() {
        var chameleon = catalog.byName("Chameleon").orElseThrow();
        var grove = catalog.byName("Grove").orElseThrow();
        var phanpyre = catalog.byName("Phanpyre").orElseThrow();

        assertEquals(1, chameleon.inputCount());
        assertEquals(2, grove.inputCount());
        assertEquals(5, phanpyre.inputCount());
        assertEquals(1, catalog.fuse(chameleon.id(), phanpyre.id()).orElseThrow().inputCount());
        assertEquals(5, catalog.fuse(phanpyre.id(), chameleon.id()).orElseThrow().inputCount());
    }

    @Test
    void specialFusionReturnsTwoAnteaterShards() {
        var queenAnt = catalog.byName("Queen Ant").orElseThrow();
        var kingCobra = catalog.byName("King Cobra").orElseThrow();
        var recipe = catalog.fuse(queenAnt.id(), kingCobra.id()).orElseThrow();
        var anteater = recipe.output("R70").orElseThrow();

        assertEquals(2, anteater.count());
        assertEquals(ShardFusionCatalog.FusionKind.SPECIAL, anteater.kind());
    }

    @Test
    void supportsBothChocobunRoutesAndTheFolfRoute() {
        var bunbun = catalog.byName("Bunbun").orElseThrow();
        var cocoaleech = catalog.byName("Cocoaleech").orElseThrow();
        var rabbitMafioso = catalog.byName("Rabbit Mafioso").orElseThrow();
        var soulOfTheAlpha = catalog.byName("Soul of the Alpha").orElseThrow();
        var kingMinos = catalog.byName("King Minos").orElseThrow();

        assertTrue(catalog.fuse(bunbun.id(), cocoaleech.id()).orElseThrow()
                .output("C48").isPresent());
        assertTrue(catalog.fuse(bunbun.id(), rabbitMafioso.id()).orElseThrow()
                .output("C48").isPresent());
        assertTrue(catalog.fuse(soulOfTheAlpha.id(), kingMinos.id()).orElseThrow()
                .output("R28").isPresent());
    }

    @Test
    void preservesSeparateIdAndSpecialSlotsWhenTheyYieldTheSameShard() {
        var recipe = catalog.fuse("C4", "R31").orElseThrow();
        var dreadwing = recipe.outputs().stream()
                .filter(output -> output.shard().id().equals("R49"))
                .toList();

        assertEquals(2, dreadwing.size());
        assertTrue(dreadwing.stream().anyMatch(output -> output.count() == 1
                && output.kind() == ShardFusionCatalog.FusionKind.ID));
        assertTrue(dreadwing.stream().anyMatch(output -> output.count() == 2
                && output.kind() == ShardFusionCatalog.FusionKind.SPECIAL));
    }

    @Test
    void idFusionPreservesLeftRightOrder() {
        var grove = catalog.byName("Grove").orElseThrow();
        var phanflare = catalog.byName("Phanflare").orElseThrow();
        Set<String> forward = outputIds(catalog.fuse(grove.id(), phanflare.id()).orElseThrow());
        Set<String> reverse = outputIds(catalog.fuse(phanflare.id(), grove.id()).orElseThrow());

        assertNotEquals(forward, reverse);
    }

    @Test
    void chameleonUsesNumericIdStepsAndRollsMissingIdsIntoNextRarity() {
        var chameleon = catalog.byName("Chameleon").orElseThrow();
        var input = catalog.byId("C47").orElseThrow();
        var outputs = catalog.fuse(chameleon.id(), input.id()).orElseThrow().outputs();

        assertEquals(List.of("C48", "C49", "U1"),
                outputs.stream().map(output -> output.shard().id()).toList());
        assertTrue(outputs.stream().allMatch(output -> output.count() == 1
                && output.kind() == ShardFusionCatalog.FusionKind.CHAMELEON));
    }

    @Test
    void everyOrderedPairRespectsOutputInvariants() {
        for (var left : catalog.shards()) {
            for (var right : catalog.shards()) {
                var recipe = catalog.fuse(left.id(), right.id()).orElse(null);
                if (recipe == null) continue;
                assertEquals(left.inputCount(), recipe.inputCount());
                assertTrue(recipe.outputs().size() <= 3);
                for (var output : recipe.outputs()) {
                    assertNotEquals(left.id(), output.shard().id());
                    assertNotEquals(right.id(), output.shard().id());
                    assertEquals(output.kind() == ShardFusionCatalog.FusionKind.SPECIAL ? 2 : 1,
                            output.count());
                }
            }
        }
    }

    @Test
    void recipeAndUsesIndexesRoundTrip() {
        List<ShardFusionCatalog.Recipe> recipes = catalog.recipesForOutput("R70");
        assertFalse(recipes.isEmpty());
        assertTrue(recipes.stream().allMatch(recipe -> recipe.output("R70").isPresent()));
        ShardFusionCatalog.Recipe first = recipes.getFirst();
        ShardFusionCatalog.Recipe use = catalog.usesForInput(first.left().id()).stream()
                .filter(recipe -> recipe.left().id().equals(first.left().id())
                        && recipe.right().id().equals(first.right().id())
                        && recipe.output("R70").isPresent())
                .findFirst()
                .orElseThrow();

        assertSame(recipes, catalog.recipesForOutput("R70"));
        assertSame(catalog.usesForInput(first.left().id()), catalog.usesForInput(first.left().id()));
        assertSame(first, use);
        assertSame(first, catalog.fuse(first.left().id(), first.right().id()).orElseThrow());
    }

    @Test
    void indexesContainOnlyValidRecipesAndShareEveryRecipeInstance() {
        Set<ShardFusionCatalog.Recipe> outputRecipes = java.util.Collections
                .newSetFromMap(new java.util.IdentityHashMap<>());
        for (var shard : catalog.shards()) {
            for (var recipe : catalog.recipesForOutput(shard.id())) {
                assertFalse(recipe.outputs().isEmpty());
                assertTrue(recipe.outputs().stream().anyMatch(output -> output.shard() == shard));
                outputRecipes.add(recipe);
            }
        }

        Set<ShardFusionCatalog.Recipe> useRecipes = java.util.Collections
                .newSetFromMap(new java.util.IdentityHashMap<>());
        for (var shard : catalog.shards()) {
            for (var recipe : catalog.usesForInput(shard.id())) {
                assertTrue(recipe.left() == shard || recipe.right() == shard);
                assertTrue(outputRecipes.contains(recipe));
                useRecipes.add(recipe);
            }
        }

        assertEquals(outputRecipes.size(), useRecipes.size());
    }

    @Test
    void prepareIndexIsIdempotentAndThreadSafe() throws Exception {
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<List<ShardFusionCatalog.Recipe>>> futures = new java.util.ArrayList<>();
            for (int worker = 0; worker < 8; worker++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    catalog.prepareIndex();
                    return catalog.recipesForOutput("R70");
                }));
            }
            start.countDown();

            List<ShardFusionCatalog.Recipe> expected = futures.getFirst().get(10, TimeUnit.SECONDS);
            assertFalse(expected.isEmpty());
            for (Future<List<ShardFusionCatalog.Recipe>> future : futures) {
                assertSame(expected, future.get(10, TimeUnit.SECONDS));
            }

            catalog.prepareIndex();
            catalog.prepareIndex();
            assertSame(expected, catalog.recipesForOutput("R70"));
            ShardFusionCatalog.Recipe recipe = expected.getFirst();
            assertSame(recipe, catalog.fuse(recipe.left().id(), recipe.right().id()).orElseThrow());
            assertTrue(catalog.usesForInput(recipe.left().id()).stream().anyMatch(use -> use == recipe));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private static Set<String> outputIds(ShardFusionCatalog.Recipe recipe) {
        Set<String> ids = new HashSet<>();
        recipe.outputs().forEach(output -> ids.add(output.shard().id()));
        return ids;
    }
}

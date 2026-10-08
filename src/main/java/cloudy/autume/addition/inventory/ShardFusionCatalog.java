package cloudy.autume.addition.inventory;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable, offline Attribute Shard catalog and fusion evaluator.
 *
 * <p>The evaluator follows the current Attribute Fusion rules documented by
 * the Hypixel SkyBlock Wiki. It never opens a connection, reads server state,
 * clicks a slot, or sends a command.</p>
 */
public final class ShardFusionCatalog {
    public static final String RESOURCE = "/assets/qcloudy_addition/data/shard_fusions.json";
    public static final int EXPECTED_SHARD_COUNT = 324;
    private static final int MAX_OUTPUTS = 3;
    private static final Set<String> CHAMELEON_EXCLUSIONS = Set.of(
            "Chameleon", "Molthorn", "Galaxy Fish", "Bitbug");
    private static final Set<String> MINING_SHARDS = Set.of(
            "C10", "C36", "U6", "U7", "R21", "R29", "R31", "R33", "R52", "E15", "E36", "L12");

    private final String dataVersion;
    private final String verifiedAt;
    private final SourceInfo sources;
    private final List<Shard> shards;
    private final Map<String, Shard> byId;
    private final Map<String, Shard> byName;
    private final Map<String, Shard> byItemId;
    private final List<SpecialRule> specialRules;
    private final Shard chameleon;
    private volatile RecipeIndex recipeIndex;

    private ShardFusionCatalog(RawCatalog raw) {
        dataVersion = requireText(raw.dataVersion, "dataVersion");
        verifiedAt = requireText(raw.verifiedAt, "verifiedAt");
        sources = SourceInfo.from(raw.sources);
        if (raw.schemaVersion != 1) {
            throw new IllegalStateException("Unsupported Shard catalog schema: " + raw.schemaVersion);
        }
        if (raw.shards == null || raw.shards.isEmpty()) {
            throw new IllegalStateException("Shard catalog has no entries");
        }

        List<Shard> loaded = new ArrayList<>(raw.shards.size());
        Map<String, Shard> ids = new LinkedHashMap<>();
        Map<String, Shard> names = new HashMap<>();
        Map<String, Shard> itemIds = new HashMap<>();
        for (int index = 0; index < raw.shards.size(); index++) {
            RawShard source = raw.shards.get(index);
            Shard shard = Shard.from(index, source);
            if (ids.putIfAbsent(shard.id(), shard) != null) {
                throw new IllegalStateException("Duplicate Shard ID: " + shard.id());
            }
            if (names.putIfAbsent(normalize(shard.name()), shard) != null) {
                throw new IllegalStateException("Duplicate Shard name: " + shard.name());
            }
            addItemId(itemIds, shard.bazaarId(), shard);
            addItemId(itemIds, shard.internalId(), shard);
            loaded.add(shard);
        }
        if (loaded.size() != EXPECTED_SHARD_COUNT) {
            throw new IllegalStateException("Expected " + EXPECTED_SHARD_COUNT
                    + " Shards, found " + loaded.size());
        }
        shards = List.copyOf(loaded);
        byId = Map.copyOf(ids);
        byName = Map.copyOf(names);
        byItemId = Map.copyOf(itemIds);
        chameleon = byName.get(normalize("Chameleon"));
        if (chameleon == null) throw new IllegalStateException("Catalog is missing Chameleon");

        List<SpecialRule> rules = new ArrayList<>();
        for (Shard output : shards) {
            String left = output.specialLeft();
            String right = output.specialRight();
            if (left.isBlank() && right.isBlank()) continue;
            if (left.isBlank()) left = "Any";
            if (right.isBlank()) right = "Any";
            rules.add(new SpecialRule(output, membership(left), membership(right)));
        }
        specialRules = List.copyOf(rules);
        validateReferences();
    }

    public static ShardFusionCatalog instance() {
        return Holder.INSTANCE;
    }

    public static ShardFusionCatalog load() {
        try (var stream = ShardFusionCatalog.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing Shard catalog resource " + RESOURCE);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                RawCatalog raw = new Gson().fromJson(reader, RawCatalog.class);
                if (raw == null) throw new IllegalStateException("Empty Shard catalog resource");
                return new ShardFusionCatalog(raw);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read Shard catalog", exception);
        }
    }

    public String dataVersion() {
        return dataVersion;
    }

    public String verifiedAt() {
        return verifiedAt;
    }

    public SourceInfo sources() {
        return sources;
    }

    public List<Shard> shards() {
        return shards;
    }

    public Optional<Shard> byId(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(byId.get(id.trim().toUpperCase(Locale.ROOT)));
    }

    public Optional<Shard> byName(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(byName.get(normalize(name)));
    }

    public Optional<Shard> byItemId(String itemId) {
        if (itemId == null) return Optional.empty();
        String normalized = itemId.trim().toUpperCase(Locale.ROOT);
        if (normalized.endsWith(";1")) normalized = normalized.substring(0, normalized.length() - 2);
        return Optional.ofNullable(byItemId.get(normalized));
    }

    public List<Shard> search(String query) {
        String value = normalize(query);
        if (value.isBlank()) return shards;
        List<ScoredShard> matches = new ArrayList<>();
        for (Shard shard : shards) {
            int score = score(shard, value);
            if (score >= 0) matches.add(new ScoredShard(shard, score));
        }
        matches.sort(Comparator.comparingInt(ScoredShard::score)
                .thenComparing(entry -> entry.shard().rarity().ordinal())
                .thenComparing(entry -> entry.shard().id(), ShardFusionCatalog::compareIds));
        return matches.stream().map(ScoredShard::shard).toList();
    }

    /**
     * Builds the shared recipe indexes if needed. Safe to call repeatedly or
     * from a background task before the first recipe lookup.
     */
    public void prepareIndex() {
        recipeIndex();
    }

    /** Returns the exact outcome for an ordered pair. */
    public Optional<Recipe> fuse(String leftId, String rightId) {
        Shard left = shardById(leftId);
        Shard right = shardById(rightId);
        if (left == null || right == null) return Optional.empty();
        return Optional.ofNullable(recipeIndex().recipe(left, right));
    }

    /**
     * Returns every ordered input pair which exposes {@code outputId} as one
     * of the selectable Fusion Machine results.
     */
    public List<Recipe> recipesForOutput(String outputId) {
        Shard target = shardById(outputId);
        if (target == null) return List.of();
        return recipeIndex().recipesForOutput(target.id());
    }

    /** Returns all ordered fusions in which {@code inputId} is consumed. */
    public List<Recipe> usesForInput(String inputId) {
        Shard input = shardById(inputId);
        if (input == null) return List.of();
        return recipeIndex().usesForInput(input.id());
    }

    private RecipeIndex recipeIndex() {
        RecipeIndex result = recipeIndex;
        if (result != null) return result;
        synchronized (this) {
            result = recipeIndex;
            if (result == null) {
                result = buildRecipeIndex();
                recipeIndex = result;
            }
            return result;
        }
    }

    private RecipeIndex buildRecipeIndex() {
        int pairCount = shards.size() * shards.size();
        Recipe[] byPair = new Recipe[pairCount];
        Map<String, List<Recipe>> byOutput = new HashMap<>();
        Map<String, List<Recipe>> byInput = new HashMap<>();

        for (Shard left : shards) {
            for (Shard right : shards) {
                List<Output> result = outputs(left, right);
                if (result.isEmpty()) continue;

                Recipe recipe = new Recipe(left, right, left.inputCount(), result, pureReptile(left, right));
                byPair[pairIndex(left, right, shards.size())] = recipe;
                byInput.computeIfAbsent(left.id(), ignored -> new ArrayList<>()).add(recipe);
                if (left != right) {
                    byInput.computeIfAbsent(right.id(), ignored -> new ArrayList<>()).add(recipe);
                }

                // A recipe may expose the same Shard in separate ID and
                // Special slots. It is still one recipe in that Shard's
                // reverse index, while both selectable slots remain intact.
                for (int outputIndex = 0; outputIndex < result.size(); outputIndex++) {
                    Output output = result.get(outputIndex);
                    boolean alreadyIndexed = false;
                    for (int previous = 0; previous < outputIndex; previous++) {
                        if (result.get(previous).shard() == output.shard()) {
                            alreadyIndexed = true;
                            break;
                        }
                    }
                    if (alreadyIndexed) continue;
                    byOutput.computeIfAbsent(output.shard().id(), ignored -> new ArrayList<>()).add(recipe);
                }
            }
        }

        Comparator<Recipe> usesOrder = Comparator
                .comparing((Recipe recipe) -> recipe.left().id(), ShardFusionCatalog::compareIds)
                .thenComparing(recipe -> recipe.right().id(), ShardFusionCatalog::compareIds);
        Map<String, List<Recipe>> immutableOutputs = new HashMap<>();
        for (Map.Entry<String, List<Recipe>> entry : byOutput.entrySet()) {
            entry.getValue().sort(recipeComparator(entry.getKey()));
            immutableOutputs.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        Map<String, List<Recipe>> immutableInputs = new HashMap<>();
        for (Map.Entry<String, List<Recipe>> entry : byInput.entrySet()) {
            entry.getValue().sort(usesOrder);
            immutableInputs.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return new RecipeIndex(shards.size(), byPair,
                Map.copyOf(immutableOutputs), Map.copyOf(immutableInputs));
    }

    private static int pairIndex(Shard left, Shard right, int shardCount) {
        return left.ordinal() * shardCount + right.ordinal();
    }

    private List<Output> outputs(Shard left, Shard right) {
        if (left == chameleon || right == chameleon) {
            Shard other = left == chameleon ? right : left;
            return chameleonOutputs(other);
        }

        List<Shard> special = specialOutputs(left, right);
        List<Shard> idResults = idOutputs(left, right);
        int idSlots = Math.max(0, MAX_OUTPUTS - special.size());
        int start = Math.max(0, idResults.size() - idSlots);
        List<Output> result = new ArrayList<>(MAX_OUTPUTS);
        for (int index = start; index < idResults.size() && result.size() < MAX_OUTPUTS; index++) {
            addOutput(result, idResults.get(index), 1, FusionKind.ID, left, right);
        }
        for (Shard shard : special) {
            addOutput(result, shard, 2, FusionKind.SPECIAL, left, right);
        }
        return List.copyOf(result);
    }

    private List<Output> chameleonOutputs(Shard input) {
        List<Output> result = new ArrayList<>(MAX_OUTPUTS);
        for (int offset = 1; offset <= MAX_OUTPUTS; offset++) {
            Shard candidate = chameleonResult(input, offset, result);
            if (candidate != null) result.add(new Output(candidate, 1, FusionKind.CHAMELEON));
        }
        return List.copyOf(result);
    }

    /** Mirrors the Fusion Machine's ID stepping: a missing ID rolls into the next rarity. */
    private Shard chameleonResult(Shard input, int offset, List<Output> existing) {
        String directId = input.id().substring(0, 1) + (numericId(input) + offset);
        Shard direct = byId.get(directId);
        if (direct != null) {
            return CHAMELEON_EXCLUSIONS.contains(direct.name()) ? null : direct;
        }

        int nextRarity = input.rarity().ordinal() + 1;
        if (nextRarity >= Rarity.values().length) return null;
        String prefix = String.valueOf("CUREL".charAt(nextRarity));
        Set<String> used = new HashSet<>();
        existing.forEach(output -> used.add(output.shard().id()));
        for (int number = 1; number <= 999; number++) {
            Shard candidate = byId.get(prefix + number);
            if (candidate == null || used.contains(candidate.id())
                    || CHAMELEON_EXCLUSIONS.contains(candidate.name())) continue;
            return candidate;
        }
        return null;
    }

    private List<Shard> idOutputs(Shard left, Shard right) {
        List<Shard> result = new ArrayList<>(2);
        if (left.category() != right.category()) {
            addIdTarget(result, left);
            addIdTarget(result, right);
            return result;
        }
        if (left.rarity() == right.rarity()) {
            Shard rightTarget = idTarget(right);
            if (rightTarget != null) {
                if (rightTarget != left) result.add(rightTarget);
                else addIdTarget(result, left);
            }
            return result;
        }
        addIdTarget(result, left.rarity().ordinal() > right.rarity().ordinal() ? left : right);
        return result;
    }

    private void addIdTarget(List<Shard> result, Shard source) {
        Shard target = idTarget(source);
        if (target != null) result.add(target);
    }

    private Shard idTarget(Shard source) {
        return source.idResult().isBlank() ? null : byId.get(source.idResult());
    }

    private List<Shard> specialOutputs(Shard left, Shard right) {
        List<Shard> result = new ArrayList<>();
        for (SpecialRule rule : specialRules) {
            if (rule.output() == left || rule.output() == right) continue;
            boolean direct = rule.left().get(left.ordinal()) && rule.right().get(right.ordinal());
            boolean reverse = rule.left().get(right.ordinal()) && rule.right().get(left.ordinal());
            if (direct || reverse) result.add(rule.output());
        }
        result.sort(Comparator.comparingInt((Shard shard) -> 5 - shard.rarity().ordinal())
                .thenComparing(ShardFusionCatalog::numericId)
                .thenComparing(ShardFusionCatalog::suffixId));
        if (result.size() <= MAX_OUTPUTS) return result;
        return new ArrayList<>(result.subList(result.size() - MAX_OUTPUTS, result.size()));
    }

    private BitSet membership(String expression) {
        BitSet result = new BitSet(shards.size());
        for (Shard shard : shards) {
            if (matches(shard, expression)) result.set(shard.ordinal());
        }
        return result;
    }

    private boolean matches(Shard shard, String expression) {
        if (expression.contains("&")) {
            for (String member : expression.split("&")) {
                if (!matchesMember(shard, member.trim())) return false;
            }
            return true;
        }
        if (expression.contains("|")) {
            for (String member : expression.split("\\|")) {
                if (matchesMember(shard, member.trim())) return true;
            }
            return false;
        }
        return matchesMember(shard, expression.trim());
    }

    private boolean matchesMember(Shard shard, String member) {
        if (member.equals("Any")) return true;
        String rarityName = member.endsWith("+") ? member.substring(0, member.length() - 1) : member;
        Optional<Rarity> rarity = Rarity.parse(rarityName);
        if (rarity.isPresent()) {
            return member.endsWith("+")
                    ? shard.rarity().ordinal() >= rarity.get().ordinal()
                    : shard.rarity() == rarity.get();
        }
        Optional<Category> category = Category.parse(member);
        if (category.isPresent()) return shard.category() == category.get();
        if (member.equals("Mining Shards")) return MINING_SHARDS.contains(shard.id());
        if (shard.families().stream().anyMatch(member::equals)) return true;
        String name = member.endsWith(" Shard") ? member.substring(0, member.length() - 6) : member;
        return shard.name().equals(name);
    }

    private static void addOutput(List<Output> outputs, Shard shard, int count,
                                  FusionKind kind, Shard left, Shard right) {
        // The Fusion Machine may expose the same Shard in two distinct slots
        // when ID Fusion (x1) and Special Fusion (x2) converge. Keep both
        // choices because their yields are different.
        if (shard == left || shard == right || outputs.size() >= MAX_OUTPUTS) return;
        outputs.add(new Output(shard, count, kind));
    }

    private static boolean pureReptile(Shard left, Shard right) {
        return reptile(left) || reptile(right);
    }

    private static boolean reptile(Shard shard) {
        return shard.name().equals("Chameleon") || shard.families().contains("Reptile");
    }

    private Comparator<Recipe> recipeComparator(String targetId) {
        return Comparator.<Recipe>comparingInt(recipe -> outputPriority(recipe, targetId))
                .thenComparingInt(Recipe::inputCount)
                .thenComparing(recipe -> recipe.left().id(), ShardFusionCatalog::compareIds)
                .thenComparing(recipe -> recipe.right().id(), ShardFusionCatalog::compareIds);
    }

    private static int outputPriority(Recipe recipe, String targetId) {
        int priority = 3;
        for (Output output : recipe.outputs()) {
            if (!output.shard().id().equals(targetId)) continue;
            int candidate = output.kind() == FusionKind.SPECIAL ? 0
                    : output.kind() == FusionKind.CHAMELEON ? 1 : 2;
            priority = Math.min(priority, candidate);
        }
        return priority;
    }

    private static int score(Shard shard, String query) {
        List<String> exact = List.of(normalize(shard.id()), normalize(shard.name()), normalize(shard.attributeName()));
        if (exact.contains(query)) return 0;
        if (exact.stream().anyMatch(value -> value.startsWith(query))) return 1;
        if (exact.stream().anyMatch(value -> value.contains(query))) return 2;
        List<String> metadata = new ArrayList<>();
        metadata.add(normalize(shard.rarity().displayName));
        metadata.add(normalize(shard.category().displayName));
        metadata.add(normalize(shard.skill()));
        shard.families().forEach(value -> metadata.add(normalize(value)));
        shard.mobTypes().forEach(value -> metadata.add(normalize(value)));
        shard.effect().forEach(value -> metadata.add(normalize(value.text())));
        shard.acquisition().forEach(value -> metadata.add(normalize(value.text())));
        return metadata.stream().anyMatch(value -> value.contains(query)) ? 3 : -1;
    }

    static String normalize(String value) {
        if (value == null) return "";
        String result = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .toLowerCase(Locale.ROOT)
                .replace("attribute shard", "")
                .replace("shard", "");
        return result.replaceAll("[^a-z0-9]+", "");
    }

    private void validateReferences() {
        for (Shard shard : shards) {
            if (!shard.idResult().isBlank() && !byId.containsKey(shard.idResult())) {
                throw new IllegalStateException("Unknown ID Fusion result: " + shard.id() + " -> " + shard.idResult());
            }
        }
        requireShard("R70", "Anteater");
        requireShard("R84", "Zombuddy");
        requireShard("R86", "Troodon");
        requireShard("R92", "Goldolot");
        requireShard("L38", "Ghost Crab");
        if (byName.containsKey(normalize("Rainbug")) || byId.containsKey("L49")) {
            throw new IllegalStateException("Rainbug is not an Attribute Shard item");
        }
    }

    private void requireShard(String id, String name) {
        Shard shard = byId.get(id);
        if (shard == null || !shard.name().equals(name)) {
            throw new IllegalStateException("Expected reviewed Shard " + id + " to be " + name);
        }
    }

    private Shard shardById(String id) {
        if (id == null) return null;
        return byId.get(id.trim().toUpperCase(Locale.ROOT));
    }

    private static void addItemId(Map<String, Shard> values, String id, Shard shard) {
        if (id == null || id.isBlank()) return;
        values.put(id.trim().toUpperCase(Locale.ROOT), shard);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing " + field);
        return value;
    }

    private static int compareIds(String left, String right) {
        int rarity = Integer.compare(rarityIndex(left), rarityIndex(right));
        if (rarity != 0) return rarity;
        int number = Integer.compare(numericId(left), numericId(right));
        if (number != 0) return number;
        return Integer.compare(suffixId(left), suffixId(right));
    }

    private static int rarityIndex(String id) {
        return "CUREL".indexOf(id.charAt(0));
    }

    private static int numericId(Shard shard) {
        return numericId(shard.id());
    }

    private static int numericId(String id) {
        int dash = id.indexOf('-');
        String number = dash < 0 ? id.substring(1) : id.substring(1, dash);
        return Integer.parseInt(number);
    }

    private static int suffixId(Shard shard) {
        return suffixId(shard.id());
    }

    private static int suffixId(String id) {
        int dash = id.indexOf('-');
        return dash < 0 ? 0 : Integer.parseInt(id.substring(dash + 1));
    }

    public enum Rarity {
        COMMON("Common", 0xFFFFFFFF),
        UNCOMMON("Uncommon", 0xFF55FF55),
        RARE("Rare", 0xFF5555FF),
        // SkyBlock EPIC uses Minecraft DARK_PURPLE (§5), not LIGHT_PURPLE (§d).
        EPIC("Epic", 0xFFAA00AA),
        LEGENDARY("Legendary", 0xFFFFAA00);

        private final String displayName;
        private final int color;

        Rarity(String displayName, int color) {
            this.displayName = displayName;
            this.color = color;
        }

        public String displayName() {
            return displayName;
        }

        public int color() {
            return color;
        }

        static Optional<Rarity> parse(String value) {
            for (Rarity rarity : values()) {
                if (rarity.displayName.equalsIgnoreCase(value)) return Optional.of(rarity);
            }
            return Optional.empty();
        }
    }

    public enum Category {
        FOREST("Forest", 0xFF55FF55),
        WATER("Water", 0xFF55FFFF),
        COMBAT("Combat", 0xFFFF5555);

        private final String displayName;
        private final int color;

        Category(String displayName, int color) {
            this.displayName = displayName;
            this.color = color;
        }

        public String displayName() {
            return displayName;
        }

        public int color() {
            return color;
        }

        static Optional<Category> parse(String value) {
            for (Category category : values()) {
                if (category.displayName.equalsIgnoreCase(value)) return Optional.of(category);
            }
            return Optional.empty();
        }
    }

    public enum FusionKind {
        ID,
        SPECIAL,
        CHAMELEON
    }

    /** Minecraft/SkyBlock formatting colors used by the Wiki Stat and Mob Type templates. */
    public enum TextTone {
        TEXT(0xFFAAAAAA), BLACK(0xFF000000), DARK_BLUE(0xFF0000AA), DARK_GREEN(0xFF00AA00),
        DARK_AQUA(0xFF00AAAA), DARK_RED(0xFFAA0000), DARK_PURPLE(0xFFAA00AA),
        GOLD(0xFFFFAA00), GRAY(0xFFAAAAAA), DARK_GRAY(0xFF555555), BLUE(0xFF5555FF),
        GREEN(0xFF55FF55), AQUA(0xFF55FFFF), RED(0xFFFF5555),
        LIGHT_PURPLE(0xFFFF55FF), YELLOW(0xFFFFFF55), WHITE(0xFFFFFFFF);

        private final int color;

        TextTone(int color) {
            this.color = color;
        }

        public int color() {
            return color;
        }
    }

    public enum AcquisitionKind {
        FUSION(TextTone.DARK_PURPLE), CAPTURE(TextTone.AQUA), KILL(TextTone.RED),
        TRAP(TextTone.DARK_GREEN), FISHING(TextTone.BLUE), TREE_GIFT(TextTone.GREEN),
        SHOP(TextTone.GOLD), CHEST(TextTone.GOLD), FLOOR_DROP(TextTone.YELLOW),
        HUNTING(TextTone.GREEN), OTHER(TextTone.GRAY), UNKNOWN(TextTone.DARK_GRAY);

        private final TextTone tone;

        AcquisitionKind(TextTone tone) {
            this.tone = tone;
        }

        public int color() {
            return tone.color();
        }
    }

    public record Shard(int ordinal, String id, String name, String attributeName,
                        List<TextSpan> effect, List<Acquisition> acquisition, List<String> mobTypes,
                        Rarity rarity, Category category, List<String> families, String skill, String bazaarId,
                        String internalId, int inputCount, String idResult, String specialLeft,
                        String specialRight, boolean wikiListed) {
        private static Shard from(int ordinal, RawShard raw) {
            if (raw == null) throw new IllegalStateException("Null Shard entry");
            Rarity rarity;
            Category category;
            try {
                rarity = Rarity.valueOf(requireText(raw.rarity, "rarity"));
                category = Category.valueOf(requireText(raw.category, "category"));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Invalid rarity/category for " + raw.id, exception);
            }
            if (raw.inputCount < 1) throw new IllegalStateException("Invalid input count for " + raw.id);
            return new Shard(ordinal, requireText(raw.id, "id"), requireText(raw.name, "name"),
                    text(raw.attributeName), TextSpan.from(raw.effect), Acquisition.from(raw.acquisition),
                    raw.mobTypes == null ? List.of() : List.copyOf(raw.mobTypes), rarity, category,
                    raw.families == null ? List.of() : List.copyOf(raw.families), text(raw.skill),
                    text(raw.bazaarId), text(raw.internalId), raw.inputCount, text(raw.idResult),
                    text(raw.specialLeft), text(raw.specialRight), raw.wikiListed);
        }

        public String displayName() {
            return name + " Shard";
        }

        public String sourceLabel() {
            return wikiListed ? "Wiki" : "Client supplement";
        }

        public boolean fusionOnly() {
            return !acquisition.isEmpty()
                    && acquisition.stream().allMatch(value -> value.kind() == AcquisitionKind.FUSION);
        }
    }

    public record TextSpan(String text, TextTone tone) {
        private static List<TextSpan> from(List<RawTextSpan> raw) {
            if (raw == null || raw.isEmpty()) return List.of();
            List<TextSpan> result = new ArrayList<>(raw.size());
            for (RawTextSpan value : raw) {
                if (value == null || value.text == null || value.text.isBlank()) continue;
                try {
                    // Rich-text spans deliberately preserve boundary whitespace. Trimming each
                    // span would join differently coloured fragments (for example "Grants " and
                    // "+0.25 Gemstone Spread") into a single malformed word.
                    result.add(new TextSpan(value.text,
                            TextTone.valueOf(requireText(value.tone, "tone"))));
                } catch (IllegalArgumentException exception) {
                    throw new IllegalStateException("Invalid Shard text tone " + value.tone, exception);
                }
            }
            return List.copyOf(result);
        }
    }

    public record Acquisition(String text, AcquisitionKind kind) {
        private static List<Acquisition> from(List<RawAcquisition> raw) {
            if (raw == null || raw.isEmpty()) return List.of();
            List<Acquisition> result = new ArrayList<>(raw.size());
            for (RawAcquisition value : raw) {
                if (value == null || ShardFusionCatalog.text(value.text).isBlank()) continue;
                try {
                    result.add(new Acquisition(ShardFusionCatalog.text(value.text),
                            AcquisitionKind.valueOf(requireText(value.kind, "acquisition kind"))));
                } catch (IllegalArgumentException exception) {
                    throw new IllegalStateException("Invalid acquisition kind " + value.kind, exception);
                }
            }
            return List.copyOf(result);
        }
    }

    public record Output(Shard shard, int count, FusionKind kind) {
    }

    public record Recipe(Shard left, Shard right, int inputCount, List<Output> outputs,
                         boolean pureReptilePossible) {
        public Recipe {
            outputs = List.copyOf(outputs);
        }

        public Optional<Output> output(String shardId) {
            return outputs.stream().filter(value -> value.shard().id().equals(shardId)).findFirst();
        }
    }

    public record SourceInfo(String wikiAttributeFusion, String wikiAttributes, String skyShardsCommit,
                             List<String> clientSupplements, List<String> unresolvedInternalIds,
                             String note) {
        private static SourceInfo from(RawSources raw) {
            if (raw == null) throw new IllegalStateException("Missing Shard source metadata");
            return new SourceInfo(text(raw.wikiAttributeFusion), text(raw.wikiAttributes),
                    text(raw.skyShardsCommit), raw.clientSupplements == null ? List.of()
                    : List.copyOf(raw.clientSupplements), raw.unresolvedInternalIds == null
                    ? List.of() : List.copyOf(raw.unresolvedInternalIds), text(raw.note));
        }
    }

    private record SpecialRule(Shard output, BitSet left, BitSet right) {
    }

    /** One immutable snapshot shared by exact, reverse, and uses lookups. */
    private static final class RecipeIndex {
        private final Recipe[] byPair;
        private final Map<String, List<Recipe>> byOutput;
        private final Map<String, List<Recipe>> byInput;
        private final int shardCount;

        private RecipeIndex(int shardCount, Recipe[] byPair,
                            Map<String, List<Recipe>> byOutput, Map<String, List<Recipe>> byInput) {
            this.shardCount = shardCount;
            this.byPair = byPair;
            this.byOutput = byOutput;
            this.byInput = byInput;
        }

        private Recipe recipe(Shard left, Shard right) {
            return byPair[pairIndex(left, right, shardCount)];
        }

        private List<Recipe> recipesForOutput(String outputId) {
            return byOutput.getOrDefault(outputId, List.of());
        }

        private List<Recipe> usesForInput(String inputId) {
            return byInput.getOrDefault(inputId, List.of());
        }
    }

    private record ScoredShard(Shard shard, int score) {
    }

    private static final class Holder {
        private static final ShardFusionCatalog INSTANCE = load();
    }

    @SuppressWarnings("unused")
    private static final class RawCatalog {
        int schemaVersion;
        String dataVersion;
        String verifiedAt;
        RawSources sources;
        List<RawShard> shards = Collections.emptyList();
    }

    @SuppressWarnings("unused")
    private static final class RawSources {
        String wikiAttributeFusion;
        String wikiAttributes;
        String skyShardsCommit;
        List<String> clientSupplements = Collections.emptyList();
        List<String> unresolvedInternalIds = Collections.emptyList();
        String note;
    }

    @SuppressWarnings("unused")
    private static final class RawShard {
        String id;
        String name;
        String attributeName;
        List<RawTextSpan> effect = Collections.emptyList();
        List<RawAcquisition> acquisition = Collections.emptyList();
        List<String> mobTypes = Collections.emptyList();
        String rarity;
        String category;
        List<String> families = Collections.emptyList();
        String skill;
        String bazaarId;
        String internalId;
        int inputCount;
        String idResult;
        String specialLeft;
        String specialRight;
        boolean wikiListed;
    }

    @SuppressWarnings("unused")
    private static final class RawTextSpan {
        String text;
        String tone;
    }

    @SuppressWarnings("unused")
    private static final class RawAcquisition {
        String text;
        String kind;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}

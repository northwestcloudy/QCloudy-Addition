package cloudy.autume.addition.dungeon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Strict bounded model for one new Dungeon party member. */
public record DungeonQuickViewSnapshot(
        String playerName,
        Stat catacombs,
        Map<DungeonClass, Stat> classes,
        FloorStats floor,
        Long totalSecrets,
        Double averageSecrets,
        Long magicalPower,
        List<Item> armor,
        Presence witherBlade,
        Presence terminator,
        Presence goldenDragon,
        Presence enderDragon,
        boolean stale,
        String failure) {

    public DungeonQuickViewSnapshot {
        playerName = safe(playerName, 16);
        if (!playerName.matches("[A-Za-z0-9_]{3,16}")) {
            throw new DungeonQuickViewException("The Dungeon quick-view player name was invalid.");
        }
        catacombs = catacombs == null ? Stat.missing() : catacombs;
        classes = classes == null ? Map.of() : Map.copyOf(classes);
        floor = floor == null ? new FloorStats("", null, null) : floor;
        armor = armor == null ? List.of()
                : java.util.Collections.unmodifiableList(new ArrayList<>(armor));
        failure = safe(failure, 192);
    }

    public static DungeonQuickViewSnapshot parse(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (integer(root, "schemaVersion", true) != 1) {
                throw new DungeonQuickViewException("Unsupported Dungeon quick-view schema.");
            }
            JsonObject identity = object(root, "identity");
            String playerName = string(identity, "name", true, 16);
            Stat catacombs = stat(object(root, "catacombs"));
            JsonObject rawClasses = object(root, "classes");
            EnumMap<DungeonClass, Stat> classes = new EnumMap<>(DungeonClass.class);
            for (DungeonClass value : DungeonClass.values()) {
                JsonObject raw = rawClasses.getAsJsonObject(value.wireName);
                classes.put(value, raw == null ? Stat.missing() : stat(raw));
            }
            JsonObject rawFloor = object(root, "floor");
            FloorStats floor = new FloorStats(string(rawFloor, "id", false, 2),
                    longNumber(rawFloor, "runs"), longNumber(rawFloor, "fastestMs"));
            JsonObject secrets = object(root, "secrets");
            Long total = longNumber(secrets, "total");
            Double average = decimal(secrets, "averagePerRun");
            Long magicalPower = longNumber(root, "magicalPower");

            JsonArray rawArmor = root.getAsJsonArray("armor");
            List<Item> armor = new ArrayList<>(4);
            for (int index = 0; index < 4; index++) {
                JsonElement raw = rawArmor != null && index < rawArmor.size() ? rawArmor.get(index) : null;
                armor.add(raw == null || raw.isJsonNull() ? null : item(raw.getAsJsonObject()));
            }
            JsonObject weapons = object(root, "weapons");
            boolean weaponComplete = bool(weapons, "complete");
            JsonObject pets = object(root, "pets");
            boolean petComplete = bool(pets, "complete");
            JsonObject metadata = object(root, "metadata");
            return new DungeonQuickViewSnapshot(playerName, catacombs, classes, floor,
                    total, average, magicalPower, armor,
                    presence(weapons.getAsJsonObject("witherBlade"), weaponComplete),
                    presence(weapons.getAsJsonObject("terminator"), weaponComplete),
                    presence(pets.getAsJsonObject("goldenDragon"), petComplete),
                    presence(pets.getAsJsonObject("enderDragon"), petComplete),
                    "stale".equals(string(metadata, "status", false, 16)), "");
        } catch (DungeonQuickViewException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DungeonQuickViewException("The Dungeon quick-view response was invalid.", exception);
        }
    }

    public static DungeonQuickViewSnapshot missing(String playerName, String floor, String failure) {
        EnumMap<DungeonClass, Stat> classes = new EnumMap<>(DungeonClass.class);
        for (DungeonClass value : DungeonClass.values()) classes.put(value, Stat.missing());
        return new DungeonQuickViewSnapshot(playerName, Stat.missing(), classes,
                new FloorStats(floor, null, null), null, null, null,
                java.util.Arrays.asList(null, null, null, null),
                Presence.missing(), Presence.missing(), Presence.missing(), Presence.missing(),
                false, failure);
    }

    private static Stat stat(JsonObject object) {
        return new Stat(decimal(object, "level"), decimal(object, "xp"));
    }

    private static Presence presence(JsonObject object, boolean complete) {
        if (object == null) return Presence.missing();
        boolean present = bool(object, "present");
        Item item = object.has("item") && object.get("item").isJsonObject()
                ? item(object.getAsJsonObject("item")) : null;
        return new Presence(present ? PresenceState.PRESENT
                : complete ? PresenceState.ABSENT : PresenceState.MISSING, item);
    }

    private static Item item(JsonObject object) {
        String id = string(object, "itemId", true, 128);
        String name = string(object, "name", true, 256);
        String rarity = string(object, "rarity", false, 32);
        List<String> lore = new ArrayList<>();
        JsonArray rawLore = object.getAsJsonArray("lore");
        if (rawLore != null) {
            for (JsonElement line : rawLore) {
                if (lore.size() >= 80 || !line.isJsonPrimitive() || !line.getAsJsonPrimitive().isString()) break;
                lore.add(safe(line.getAsString(), 512));
            }
        }
        return new Item(id, name, lore, rarity);
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonObject result = parent.getAsJsonObject(key);
        if (result == null) throw new IllegalArgumentException("Missing object: " + key);
        return result;
    }

    private static String string(JsonObject object, String key, boolean required, int maximum) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            if (required) throw new IllegalArgumentException("Missing string: " + key);
            return "";
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Invalid string: " + key);
        }
        String value = safe(element.getAsString(), maximum);
        if (required && value.isEmpty()) throw new IllegalArgumentException("Blank string: " + key);
        return value;
    }

    private static int integer(JsonObject object, String key, boolean required) {
        Long value = longNumber(object, key);
        if (value == null) {
            if (required) throw new IllegalArgumentException("Missing integer: " + key);
            return 0;
        }
        return Math.toIntExact(value);
    }

    private static Long longNumber(JsonObject object, String key) {
        Double value = decimal(object, key);
        if (value == null) return null;
        if (value < 0 || value > Long.MAX_VALUE || value != Math.rint(value)) {
            throw new IllegalArgumentException("Invalid integer: " + key);
        }
        return value.longValue();
    }

    private static Double decimal(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) return null;
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Invalid number: " + key);
        }
        double value = new BigDecimal(element.getAsString()).doubleValue();
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid number: " + key);
        return value;
    }

    private static boolean bool(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("Invalid boolean: " + key);
        }
        return element.getAsBoolean();
    }

    private static String safe(String value, int maximum) {
        if (value == null) return "";
        String normalized = value.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
        return normalized.length() <= maximum ? normalized : normalized.substring(0, maximum);
    }

    public enum DungeonClass {
        HEALER("healer", "Heal."), MAGE("mage", "Mage"), BERSERK("berserk", "Bers."),
        ARCHER("archer", "Arch."), TANK("tank", "Tank");

        private final String wireName;
        private final String label;
        DungeonClass(String wireName, String label) { this.wireName = wireName; this.label = label; }
        public String label() { return label; }
    }

    public enum PresenceState { PRESENT, ABSENT, MISSING }
    public record Stat(Double level, Double xp) { public static Stat missing() { return new Stat(null, null); } }
    public record FloorStats(String id, Long runs, Long fastestMs) {
        public FloorStats { id = safe(id, 2).toUpperCase(Locale.ROOT); }
    }
    public record Item(String itemId, String name, List<String> lore, String rarity) {
        public Item { lore = lore == null ? List.of() : List.copyOf(lore); }
    }
    public record Presence(PresenceState state, Item item) {
        public Presence { state = state == null ? PresenceState.MISSING : state; }
        public static Presence missing() { return new Presence(PresenceState.MISSING, null); }
    }
}

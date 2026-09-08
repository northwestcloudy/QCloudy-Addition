package cloudy.autume.addition.dungeon;

import cloudy.autume.addition.dungeon.requirements.DungeonFloorKey;
import cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidence;
import cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidenceValue.DecimalValue;
import cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidenceValue.DuplicateClass;
import cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidenceValue.LongValue;
import cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidenceValue.PresenceEvidence;
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
import java.util.UUID;

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
        String failure,
        String queryName,
        UUID playerUuid,
        long evidenceFetchedAt,
        DungeonRequirementEvidence requirementsEvidence) {

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
        queryName = safe(queryName, 16);
    }

    /**
     * True only when the backend protocol bound this evidence to one fresh,
     * certain player/Profile/floor response. Local party-class evidence must
     * never turn one of these global blockers back into an actionable FAIL.
     */
    public boolean requirementsEvidenceTrusted() {
        return requirementsEvidenceBlocker().isEmpty();
    }

    /** Returns the precise global blocker, or an empty string when evidence is trusted. */
    public String requirementsEvidenceBlocker() {
        if (requirementsEvidence == null) return "UNSUPPORTED_EVIDENCE";
        String blocker = requirementsEvidence.floorCompletions().unavailableReason();
        String normalized = blocker == null ? "" : blocker.trim().toUpperCase(Locale.ROOT);
        if (switch (normalized) {
            case "SOURCE_STALE", "PROFILE_SELECTION_UNCERTAIN", "PROFILE_ID_MISSING",
                    "FLOOR_MISMATCH", "IDENTITY_MISMATCH", "UNSUPPORTED_EVIDENCE" -> true;
            default -> false;
        }) return normalized;
        if (playerUuid == null || queryName.isBlank()
                || !queryName.equalsIgnoreCase(playerName)) return "IDENTITY_MISMATCH";
        DungeonFloorKey displayed = DungeonFloorKey.parse(floor.id()).orElse(null);
        if (displayed == null || requirementsEvidence.floor() != displayed) {
            return "FLOOR_MISMATCH";
        }
        if (evidenceFetchedAt <= 0) return "UNSUPPORTED_EVIDENCE";
        return "";
    }

    public static DungeonQuickViewSnapshot parse(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (integer(root, "schemaVersion", true) != 1) {
                throw new DungeonQuickViewException("Unsupported Dungeon quick-view schema.");
            }
            JsonObject identity = object(root, "identity");
            String playerName = string(identity, "name", true, 16);
            String queryName = string(identity, "queryName", false, 16);
            UUID playerUuid = uuid(string(identity, "uuid", false, 36));
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
            boolean stale = "stale".equals(string(metadata, "status", false, 16));
            JsonObject rawRequirementsEvidence = root.getAsJsonObject("requirementsEvidence");
            DungeonRequirementEvidence requirementsEvidence = parseRequirementsEvidence(
                    rawRequirementsEvidence, floor, playerName,
                    queryName, playerUuid, stale);
            return new DungeonQuickViewSnapshot(playerName, catacombs, classes, floor,
                    total, average, magicalPower, armor,
                    presence(weapons.getAsJsonObject("witherBlade"), weaponComplete),
                    presence(weapons.getAsJsonObject("terminator"), weaponComplete),
                    presence(pets.getAsJsonObject("goldenDragon"), petComplete),
                    presence(pets.getAsJsonObject("enderDragon"), petComplete),
                    stale, "", queryName, playerUuid,
                    evidenceFetchedAt(rawRequirementsEvidence), requirementsEvidence);
        } catch (DungeonQuickViewException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DungeonQuickViewException("The Dungeon quick-view response was invalid.", exception);
        }
    }

    private static DungeonRequirementEvidence parseRequirementsEvidence(
            JsonObject raw, FloorStats displayFloor, String playerName,
            String queryName, UUID playerUuid, boolean displayStale) {
        DungeonFloorKey floor = DungeonFloorKey.parse(displayFloor.id()).orElse(null);
        if (floor == null) return null;
        if (raw == null) {
            return DungeonRequirementEvidence.unavailable(
                    floor, "UNSUPPORTED_EVIDENCE");
        }
        String unavailable = "UNSUPPORTED_EVIDENCE";
        try {
            if (integer(raw, "version", true) != 1) {
                return DungeonRequirementEvidence.unavailable(
                        floor, "UNSUPPORTED_EVIDENCE");
            }
            JsonObject evidenceIdentity = object(raw, "identity");
            String evidenceName = string(evidenceIdentity, "name", true, 16);
            String evidenceQuery = string(evidenceIdentity, "queryName", true, 16);
            UUID evidenceUuid = uuid(string(evidenceIdentity, "uuid", true, 36));
            JsonObject request = object(raw, "request");
            String requestedFloor = string(request, "floor", true, 2).toUpperCase(Locale.ROOT);
            String responseFloor = string(request, "responseFloor", true, 2).toUpperCase(Locale.ROOT);
            boolean floorMatches = bool(request, "floorMatches");
            JsonObject profile = object(raw, "profile");
            boolean selectionCertain = bool(profile, "selectionCertain");
            UUID profileId = uuid(string(profile, "id", false, 36));
            boolean fresh = bool(raw, "fresh");

            if (displayStale || !fresh) unavailable = "SOURCE_STALE";
            else if (!selectionCertain) unavailable = "PROFILE_SELECTION_UNCERTAIN";
            else if (profileId == null) unavailable = "PROFILE_SELECTION_UNCERTAIN";
            else if (!floorMatches || !floor.name().equals(requestedFloor)
                    || !floor.name().equals(responseFloor)) unavailable = "FLOOR_MISMATCH";
            else if (!evidenceName.equalsIgnoreCase(playerName)
                    || !evidenceQuery.equalsIgnoreCase(queryName.isBlank() ? evidenceQuery : queryName)
                    || evidenceUuid == null || playerUuid == null || !evidenceUuid.equals(playerUuid)) {
                unavailable = "IDENTITY_MISMATCH";
            } else {
                return new DungeonRequirementEvidence(floor,
                        longEvidence(object(raw, "floorCompletions"), "value"),
                        DuplicateClass.unknown(null, null, "PARTY_CLASSES_INCOMPLETE"),
                        longEvidence(object(raw, "fastestCompletion"), "valueMs"),
                        decimalEvidence(object(raw, "averageSecrets"), "value"),
                        longEvidence(object(raw, "magicalPower"), "value"),
                        presenceEvidence(object(object(raw, "weapons"), "witherBlade")),
                        presenceEvidence(object(object(raw, "weapons"), "terminator")),
                        presenceEvidence(object(object(raw, "pets"), "goldenDragon")),
                        presenceEvidence(object(object(raw, "pets"), "enderDragon")));
            }
        } catch (RuntimeException ignored) {
            unavailable = "UNSUPPORTED_EVIDENCE";
        }
        return DungeonRequirementEvidence.unavailable(floor, unavailable);
    }

    private static long evidenceFetchedAt(JsonObject raw) {
        if (raw == null) return 0L;
        try {
            return java.util.Objects.requireNonNullElse(longNumber(raw, "fetchedAt"), 0L);
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private static LongValue longEvidence(JsonObject object, String key) {
        if ("KNOWN".equals(string(object, "state", false, 16))) {
            Long value = longNumber(object, key);
            return value == null ? LongValue.unknown(reason(object)) : LongValue.known(value);
        }
        return LongValue.unknown(reason(object));
    }

    private static DecimalValue decimalEvidence(JsonObject object, String key) {
        if ("KNOWN".equals(string(object, "state", false, 16))) {
            Double value = decimal(object, key);
            return value == null ? DecimalValue.unknown(reason(object)) : DecimalValue.known(value);
        }
        return DecimalValue.unknown(reason(object));
    }

    private static PresenceEvidence presenceEvidence(JsonObject object) {
        return switch (string(object, "state", false, 24)) {
            case "PRESENT" -> PresenceEvidence.present();
            case "ABSENT" -> PresenceEvidence.confirmedAbsent();
            default -> PresenceEvidence.unknown(reason(object));
        };
    }

    private static String reason(JsonObject object) {
        String reason = string(object, "reason", false, 64);
        return reason.isBlank() ? "MISSING_VALUE" : reason.toUpperCase(Locale.ROOT);
    }

    private static UUID uuid(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.matches("[0-9a-fA-F]{32}")) {
            normalized = normalized.substring(0, 8) + '-'
                    + normalized.substring(8, 12) + '-'
                    + normalized.substring(12, 16) + '-'
                    + normalized.substring(16, 20) + '-'
                    + normalized.substring(20);
        }
        try {
            return UUID.fromString(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
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
        HEALER("healer"), MAGE("mage"), BERSERK("berserk"),
        ARCHER("archer"), TANK("tank");

        private final String wireName;
        DungeonClass(String wireName) { this.wireName = wireName; }
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

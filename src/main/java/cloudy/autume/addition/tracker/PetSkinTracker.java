package cloudy.autume.addition.tracker;

import cloudy.autume.addition.compat.MinecraftClientCompat;
import cloudy.autume.addition.config.ConfigManager;
import cloudy.autume.addition.config.ModConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PetSkinTracker {
    private static final String TEXTURE_INDEX =
            "/assets/qcloudy_addition/data/pet_skin_texture_index.json";
    private static final Map<String, String> SKIN_BY_TEXTURE = loadTextureIndex();
    private static final Map<String, String> LAST_SKIN_BY_PET = new HashMap<>();
    private static final Map<String, ResolvableProfile> LAST_PROFILE_BY_PET = new HashMap<>();
    private static final Map<String, PetDetails> DETAILS_BY_PET = new HashMap<>();
    private static final Set<String> DRONE_MOD_IDS = Set.of(
            "GRUNGLE", "CONTRABAND", "MINING_OFF_CAMERA");
    private static boolean memorySavesEnabled = true;
    private static final Pattern HELD_ITEM_MESSAGE = Pattern.compile("^Your pet is now holding (.+?)\\.$");
    private static final Pattern REMOVED_ITEM_MESSAGE = Pattern.compile("^You removed .+? from your pet!$");
    private static final Pattern HELD_ITEM_LORE = Pattern.compile("^Held Item:\\s*(.+)$");

    private PetSkinTracker() {
    }

    public static void update(Minecraft client) {
        PetTracker.PetSnapshot pet = PetTracker.current();
        if (pet == null) return;
        updateFromOpenMenu(client);
        updateFromRenderedEntities(client, pet);
    }

    public static void onChat(String raw) {
        PetTracker.PetSnapshot pet = PetTracker.current();
        if (pet == null) return;
        Matcher held = HELD_ITEM_MESSAGE.matcher(raw.trim());
        if (held.matches()) {
            updateHeldItem(pet.name(), held.group(1).trim());
        } else if (REMOVED_ITEM_MESSAGE.matcher(raw.trim()).matches()) {
            updateHeldItem(pet.name(), "");
        }
    }

    public static void noteSkinMarker(String petName, boolean skinPresent) {
        if (skinPresent) return;
        String key = normalize(petName);
        LAST_SKIN_BY_PET.remove(key);
        LAST_PROFILE_BY_PET.remove(key);
        PetDetails details = currentDetails(key);
        if (details != null && !details.skinKey().isBlank()) {
            remember(key, new PetDetails("", details.heldItemId(), details.totalExperience(), details.tier()));
        }
    }

    public static String currentSkin(String petName) {
        return LAST_SKIN_BY_PET.get(normalize(petName));
    }

    /** Returns only a profile that was validated as belonging to this pet. */
    public static ResolvableProfile currentProfile(String petName) {
        return LAST_PROFILE_BY_PET.get(normalize(petName));
    }

    public static PetDetails currentDetails(String petName) {
        String petKey = normalize(petName);
        String instanceId = activeInstanceId(petName);
        String key = detailsKey(petKey, instanceId);
        PetDetails live = DETAILS_BY_PET.get(key);
        if (live != null) return live;
        // A Drone without an observed instance UUID is session-only. Loading a
        // legacy type-wide memory here could apply one Drone's mods to another.
        if (isPrecursorDrone(petKey) && instanceId.isBlank()) return PetDetails.EMPTY;
        ModConfig.PetMemory memory = ConfigManager.get().pets.rememberedDetails.get(key);
        return memory == null ? PetDetails.EMPTY
                : new PetDetails(memory.skinKey, memory.heldItemId, memory.totalExperience,
                PetTier.fromWire(memory.tier));
    }

    /** Retains an accessory name that was explicitly present in the received Tab widget. */
    public static void rememberHeldItem(String petName, String heldItem) {
        if (petName == null || petName.isBlank() || heldItem == null || heldItem.isBlank()) return;
        updateHeldItem(petName, heldItem);
    }

    public static void reset() {
        LAST_SKIN_BY_PET.clear();
        LAST_PROFILE_BY_PET.clear();
        DETAILS_BY_PET.clear();
    }

    private static void updateFromOpenMenu(Minecraft client) {
        if (!(MinecraftClientCompat.screen(client) instanceof AbstractContainerScreen<?> screen)) return;
        if (!screen.getTitle().getString().matches("(?:\\(\\d+/\\d+\\) )?Pets(?:.*)?")) return;
        for (var slot : screen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            PetInfo info = petInfo(stack);
            if (info == null || !info.active && !isActivePet(stack)) continue;
            String pet = normalize(info.type);
            if (info.skin == null || info.skin.isBlank()) LAST_SKIN_BY_PET.remove(pet);
            else LAST_SKIN_BY_PET.put(pet, normalizeSkin(info.skin));
            ResolvableProfile profile = stack.get(DataComponents.PROFILE);
            if (profile == null) LAST_PROFILE_BY_PET.remove(pet);
            else LAST_PROFILE_BY_PET.put(pet, profile);
            String heldItem = info.heldItem == null || info.heldItem.isBlank() ? heldItemFromLore(stack) : info.heldItem;
            rememberReceivedPetInfo(info, heldItem);
            return;
        }
    }

    static void rememberReceivedPetInfo(PetInfo info, String fallbackHeldItem) {
        if (info == null) return;
        PetTracker.noteMetadata(info.type, info.tier, info.instanceId);
        String heldItem = info.heldItem == null || info.heldItem.isBlank()
                ? java.util.Objects.requireNonNullElse(fallbackHeldItem, "") : info.heldItem;
        remember(info.type, info.instanceId,
                new PetDetails(normalizeSkin(info.skin), heldItem, info.experience, info.tier));
    }

    private static boolean isActivePet(ItemStack stack) {
        ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        return lore.lines().stream().anyMatch(line -> line.getString().contains("Click to despawn!"));
    }

    private static String heldItemFromLore(ItemStack stack) {
        ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        for (var line : lore.lines()) {
            Matcher matcher = HELD_ITEM_LORE.matcher(line.getString().trim());
            if (matcher.matches()) return matcher.group(1).trim();
        }
        return "";
    }

    private static void updateHeldItem(String petName, String heldItem) {
        String key = normalize(petName);
        PetDetails details = currentDetails(key);
        remember(key, new PetDetails(details.skinKey(), heldItem, details.totalExperience(), details.tier()));
    }

    private static void updateFromRenderedEntities(Minecraft client, PetTracker.PetSnapshot pet) {
        if (client.level == null || client.player == null || SKIN_BY_TEXTURE.isEmpty()) return;
        var nearby = new ArrayList<LivingEntity>();
        var named = new ArrayList<LivingEntity>();
        for (var entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || entity == client.player
                    || entity.distanceToSqr(client.player) > 18.0 * 18.0) continue;
            nearby.add(living);
            if (living.getCustomName() != null && living.getCustomName().getString().contains(pet.name())) {
                named.add(living);
            }
        }
        String bestSkin = null;
        ResolvableProfile bestProfile = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity anchor : named) {
            for (LivingEntity candidate : nearby) {
                if (candidate.distanceToSqr(anchor) > 4.0) continue;
                ItemStack candidateHead = candidate.getItemBySlot(EquipmentSlot.HEAD);
                if (!hasProfileHead(candidateHead)) continue;
                String skin = skinFromHead(candidateHead);
                if (skin == null || !skinBelongsToPet(skin, pet.name())) continue;
                double distance = candidate.distanceToSqr(anchor);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestSkin = skin;
                    bestProfile = candidateHead.get(DataComponents.PROFILE);
                }
            }
        }
        if (bestSkin != null) {
            String key = normalize(pet.name());
            LAST_SKIN_BY_PET.put(key, bestSkin);
            if (bestProfile != null) LAST_PROFILE_BY_PET.put(key, bestProfile);
            PetDetails details = currentDetails(key);
            if (!bestSkin.equals(details.skinKey())) {
                remember(key, new PetDetails(bestSkin, details.heldItemId(), details.totalExperience(), details.tier()));
            }
        }
    }

    private static void remember(String petName, PetDetails details) {
        remember(petName, activeInstanceId(petName), details);
    }

    private static void remember(String petName, String instanceId, PetDetails details) {
        String petKey = normalize(petName);
        String key = detailsKey(petKey, instanceId);
        PetDetails clean = new PetDetails(normalizeSkin(details.skinKey()),
                details.heldItemId() == null ? "" : details.heldItemId().trim(),
                Double.isFinite(details.totalExperience()) && details.totalExperience() > 0.0
                        ? details.totalExperience() : 0.0,
                details.tier() == null ? PetTier.UNKNOWN : details.tier());
        DETAILS_BY_PET.put(key, clean);

        // The official API identifies Drone Mods as pet items, but it does not
        // document their per-instance petInfo representation. Until an actual
        // UUID is received, keep any observed Drone state in this session only.
        if (isPrecursorDrone(petKey) && normalizeInstanceId(instanceId).isBlank()) return;

        var memories = ConfigManager.get().pets.rememberedDetails;
        ModConfig.PetMemory previous = memories.get(key);
        boolean empty = clean.skinKey().isBlank() && clean.heldItemId().isBlank()
                && clean.totalExperience() <= 0.0 && clean.tier() == PetTier.UNKNOWN;
        boolean unchanged = previous != null
                && java.util.Objects.equals(previous.skinKey, clean.skinKey())
                && java.util.Objects.equals(previous.heldItemId, clean.heldItemId())
                && Double.compare(previous.totalExperience, clean.totalExperience()) == 0
                && java.util.Objects.equals(previous.tier, clean.tier().wireValue());
        if (unchanged || empty && previous == null) return;
        if (empty) memories.remove(key);
        else memories.put(key, new ModConfig.PetMemory(
                clean.skinKey(), clean.heldItemId(), clean.tier().wireValue(), clean.totalExperience()));
        while (memories.size() > 128) {
            String oldest = memories.keySet().iterator().next();
            memories.remove(oldest);
        }
        if (memorySavesEnabled) ConfigManager.save();
    }

    static void setMemorySavesEnabledForTests(boolean enabled) {
        memorySavesEnabled = enabled;
    }

    static boolean skinBelongsToPet(String skinKey, String petName) {
        String pet = normalize(petName);
        String skin = normalizeSkin(skinKey);
        return skin.equals(pet) || skin.startsWith(pet + "_");
    }

    public static boolean isKnownDroneMod(String itemId) {
        return itemId != null && DRONE_MOD_IDS.contains(itemId.trim().toUpperCase(Locale.ROOT));
    }

    private static PetInfo petInfo(ItemStack stack) {
        if (stack.isEmpty()) return null;
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        var root = data.copyTag();
        var attributes = root.getCompoundOrEmpty("ExtraAttributes");
        String raw = attributes.getStringOr("petInfo", root.getStringOr("petInfo", ""));
        return parsePetInfoJson(raw);
    }

    static PetInfo parsePetInfoJson(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
            if (!json.has("type")) return null;
            String type = json.get("type").getAsString();
            boolean active = json.has("active") && json.get("active").getAsBoolean();
            String skin = json.has("skin") && !json.get("skin").isJsonNull() ? json.get("skin").getAsString() : null;
            String heldItem = json.has("heldItem") && !json.get("heldItem").isJsonNull()
                    ? json.get("heldItem").getAsString() : null;
            double experience = json.has("exp") ? json.get("exp").getAsDouble() : 0.0;
            PetTier tier = json.has("tier") && !json.get("tier").isJsonNull()
                    ? PetTier.fromWire(json.get("tier").getAsString()) : PetTier.UNKNOWN;
            String instanceId = json.has("uuid") && !json.get("uuid").isJsonNull()
                    ? normalizeInstanceId(json.get("uuid").getAsString()) : "";
            return new PetInfo(type, active, skin, heldItem, experience, tier, instanceId);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String skinFromHead(ItemStack head) {
        if (!hasProfileHead(head)) return null;
        var profile = head.get(DataComponents.PROFILE);
        for (var property : profile.partialProfile().properties().get("textures")) {
            String textureHash = textureHash(property.value());
            String skin = SKIN_BY_TEXTURE.get(textureHash);
            if (skin != null) return skin;
        }
        return null;
    }

    private static boolean hasProfileHead(ItemStack head) {
        return !head.isEmpty() && head.get(DataComponents.PROFILE) != null;
    }

    static String textureHash(String encodedProperty) {
        try {
            String decoded = new String(Base64.getDecoder().decode(encodedProperty), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(decoded).getAsJsonObject();
            String url = root.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
            return url.substring(url.lastIndexOf('/') + 1);
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static Map<String, String> loadTextureIndex() {
        try (var stream = PetSkinTracker.class.getResourceAsStream(TEXTURE_INDEX)) {
            if (stream == null) return Map.of();
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                Map<String, String> result = new HashMap<>();
                json.entrySet().forEach(entry -> result.put(entry.getKey(), entry.getValue().getAsString()));
                return Map.copyOf(result);
            }
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static String normalizeSkin(String value) {
        if (value == null) return "";
        return normalize(value).replace("pet_skin_", "");
    }

    private static String activeInstanceId(String petName) {
        PetTracker.PetSnapshot pet = PetTracker.current();
        return pet != null && normalize(pet.name()).equals(normalize(petName))
                ? normalizeInstanceId(pet.instanceId()) : "";
    }

    private static String detailsKey(String normalizedPetName, String instanceId) {
        String normalizedInstance = normalizeInstanceId(instanceId);
        return normalizedInstance.isBlank() ? normalizedPetName : normalizedPetName + "#" + normalizedInstance;
    }

    private static String normalizeInstanceId(String value) {
        if (value == null || value.isBlank()) return "";
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static boolean isPrecursorDrone(String normalizedPetName) {
        return "precursor_drone".equals(normalizedPetName);
    }

    public record PetDetails(String skinKey, String heldItemId, double totalExperience, PetTier tier) {
        private static final PetDetails EMPTY = new PetDetails("", "", 0.0, PetTier.UNKNOWN);

        public PetDetails(String skinKey, String heldItemId, double totalExperience) {
            this(skinKey, heldItemId, totalExperience, PetTier.UNKNOWN);
        }

        public PetDetails {
            tier = tier == null ? PetTier.UNKNOWN : tier;
        }
    }

    record PetInfo(String type, boolean active, String skin, String heldItem, double experience,
                   PetTier tier, String instanceId) {
    }
}

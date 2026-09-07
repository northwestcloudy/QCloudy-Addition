package cloudy.autume.addition.fishing;

import cloudy.autume.addition.QCloudyAdditionClient;
import cloudy.autume.addition.config.ConfigManager;
import cloudy.autume.addition.tracker.LocationTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Plays a local sound once when the local player's hook receives Hypixel's bite marker. */
public final class FishingBiteAlert {
    private static final SoundEvent BITE_SOUND = SoundEvent.createVariableRangeEvent(
            Identifier.fromNamespaceAndPath(QCloudyAdditionClient.MOD_ID, "fishing_bite"));
    private static final FishingBiteSession SESSION = new FishingBiteSession();
    private static final FishingHookResolver HOOK_RESOLVER = new FishingHookResolver(40);
    private static final double MARKER_SEARCH_RADIUS = 4.0;
    private static final double HOOK_ASSOCIATION_RADIUS = 96.0;

    private FishingBiteAlert() {
    }

    public static void tick(Minecraft client) {
        var config = ConfigManager.get().fishing;
        if (!config.biteAlert || !LocationTracker.isSkyBlock()
                || client.player == null || client.level == null) {
            reset();
            return;
        }

        FishingHook hook = resolveHook(client, true);
        if (hook == null) {
            SESSION.reset();
            return;
        }

        boolean biteMarkerVisible = !client.level.getEntities(hook,
                hook.getBoundingBox().inflate(MARKER_SEARCH_RADIUS), FishingBiteAlert::isBiteMarker).isEmpty();
        playOnce(client, hook, biteMarkerVisible);
    }

    /**
     * Handles the received marker metadata immediately. Hypixel's final !!!
     * state can be shorter than the gap to the next client tick, so the normal
     * tick scan remains a fallback rather than the only observation path.
     */
    public static void onArmorStandMetadata(Minecraft client, ArmorStand marker) {
        var config = ConfigManager.get().fishing;
        if (!config.biteAlert || !LocationTracker.isSkyBlock()
                || client.player == null || client.level == null || !isBiteMarker(marker)) return;
        FishingHook hook = resolveHook(client, false);
        if (hook == null || !hook.getBoundingBox().inflate(MARKER_SEARCH_RADIUS)
                .contains(marker.position())) return;
        playOnce(client, hook, true);
    }

    private static FishingHook resolveHook(Minecraft client, boolean advanceAssociationWindow) {
        FishingHook directHook = client.player.fishing;
        List<FishingHook> loadedHooks = directHook == null && HOOK_RESOLVER.needsCandidates()
                ? loadedHooks(client) : List.of();
        List<FishingHookResolver.Candidate> candidates = loadedHooks.stream()
                .map(hook -> candidate(client, hook)).toList();
        int directHookId = directHook == null ? FishingHookResolver.NO_HOOK : directHook.getId();
        int hookId = advanceAssociationWindow
                ? HOOK_RESOLVER.resolve(directHookId, candidates)
                : HOOK_RESOLVER.observe(directHookId, candidates);
        return directHook != null ? directHook : hookById(loadedHooks, hookId);
    }

    private static void playOnce(Minecraft client, FishingHook hook, boolean biteMarkerVisible) {
        if (!SESSION.shouldPlay(hook.getId(), biteMarkerVisible)) return;

        float volume = ConfigManager.get().fishing.biteAlertVolume / 100.0F;
        if (volume <= 0.0F) return;
        client.getSoundManager().play(SimpleSoundInstance.forUI(BITE_SOUND, 1.0F, volume));
    }

    static boolean isBiteMarker(Entity entity) {
        return entity instanceof ArmorStand
                && entity.isInvisible()
                && entity.hasCustomName()
                && entity.isCustomNameVisible()
                && "!!!".equals(entity.getCustomName().getString());
    }

    /** Observes a physical rod use so ownerless Hypixel lava hooks can be associated locally. */
    public static void onRodUse(Minecraft client, ItemStack stack) {
        if (!ConfigManager.get().fishing.biteAlert || !LocationTracker.isSkyBlock()
                || client.player == null || client.level == null || !stack.is(Items.FISHING_ROD)) return;
        Set<Integer> visibleHookIds = loadedHooks(client).stream()
                .map(FishingHook::getId)
                .collect(Collectors.toSet());
        boolean startsNewCast = HOOK_RESOLVER.onRodUse(visibleHookIds, client.player.fishing != null);
        SESSION.onRodUse(startsNewCast);
    }

    private static List<FishingHook> loadedHooks(Minecraft client) {
        return client.level.getEntitiesOfClass(FishingHook.class,
                client.player.getBoundingBox().inflate(HOOK_ASSOCIATION_RADIUS), hook -> true);
    }

    private static FishingHookResolver.Candidate candidate(Minecraft client, FishingHook hook) {
        var owner = hook.getPlayerOwner();
        FishingHookResolver.Ownership ownership = owner == client.player
                ? FishingHookResolver.Ownership.LOCAL_PLAYER
                : owner == null
                ? FishingHookResolver.Ownership.UNKNOWN
                : FishingHookResolver.Ownership.OTHER_PLAYER;
        return new FishingHookResolver.Candidate(hook.getId(), ownership,
                hook.distanceToSqr(client.player));
    }

    private static FishingHook hookById(List<FishingHook> hooks, int hookId) {
        if (hookId == FishingHookResolver.NO_HOOK) return null;
        for (FishingHook hook : hooks) {
            if (hook.getId() == hookId) return hook;
        }
        return null;
    }

    public static void reset() {
        SESSION.reset();
        HOOK_RESOLVER.reset();
    }
}

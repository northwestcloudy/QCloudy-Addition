package cloudy.autume.addition.mixin;

import cloudy.autume.addition.fishing.FishingBiteAlert;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes short-lived received entity metadata after vanilla has applied it. */
@Mixin(value = ClientPacketListener.class, priority = 950)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleSetEntityData", at = @At("TAIL"))
    private void qca$observeFishingBiteMarker(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        Entity entity = client.level.getEntity(packet.id());
        if (entity instanceof ArmorStand armorStand) {
            FishingBiteAlert.onArmorStandMetadata(client, armorStand);
        }
    }
}

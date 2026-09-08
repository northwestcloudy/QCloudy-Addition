package cloudy.autume.addition.mixin;

import cloudy.autume.addition.dungeon.DungeonPartyAuthorityTracker;
import net.hypixel.modapi.packet.impl.clientbound.ClientboundPartyInfoPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Carries the network-decode time through the later main-thread API dispatch. */
@Mixin(value = ClientboundPartyInfoPacket.class, remap = false)
public abstract class ClientboundPartyInfoPacketMixin
        implements DungeonPartyAuthorityTracker.IngressTimestampCarrier {
    @Unique
    private long qca$ingressAtNanos;

    @Inject(
            method = {
                    "<init>(IZLjava/util/Map;)V",
                    "<init>(Lnet/hypixel/modapi/serializer/PacketSerializer;)V"
            },
            at = @At("RETURN"),
            remap = false
    )
    private void qca$captureDecodeTime(CallbackInfo ci) {
        qca$ingressAtNanos = System.nanoTime();
    }

    @Override
    public long qca$ingressAtNanos() {
        return qca$ingressAtNanos;
    }
}

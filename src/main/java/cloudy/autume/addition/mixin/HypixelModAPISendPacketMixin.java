package cloudy.autume.addition.mixin;

import cloudy.autume.addition.dungeon.DungeonPartyAuthorityTracker;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.error.ErrorReason;
import net.hypixel.modapi.packet.ClientboundHypixelPacket;
import net.hypixel.modapi.packet.HypixelPacket;
import net.hypixel.modapi.packet.impl.clientbound.ClientboundPartyInfoPacket;
import net.hypixel.modapi.packet.impl.serverbound.ServerboundPartyInfoPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records all successful PartyInfo sends made through the shared Hypixel API. */
@Mixin(value = HypixelModAPI.class, remap = false)
public abstract class HypixelModAPISendPacketMixin {
    private static final String PARTY_INFO_IDENTIFIER = "hypixel:party_info";

    @WrapMethod(
            method = "sendPacket(Lnet/hypixel/modapi/packet/HypixelPacket;)Z",
            remap = false
    )
    private boolean qca$recordPartyInfoSend(HypixelPacket packet,
                                            Operation<Boolean> original) {
        if (!(packet instanceof ServerboundPartyInfoPacket partyInfo)) {
            return original.call(packet);
        }
        // PartyInfo has no request id. Serializing the complete shared-API
        // call makes ledger order identical to actual send order even if a
        // third-party mod calls sendPacket from another thread.
        synchronized (DungeonPartyAuthorityTracker.class) {
            boolean sent = original.call(packet);
            if (sent) DungeonPartyAuthorityTracker.onSuccessfulPartyInfoSend(partyInfo);
            return sent;
        }
    }

    @Inject(
            method = "handle(Lnet/hypixel/modapi/packet/ClientboundHypixelPacket;)V",
            at = @At("HEAD"),
            remap = false
    )
    private void qca$consumePartyInfoBeforeRegisteredHandlers(
            ClientboundHypixelPacket packet, CallbackInfo ci) {
        long handlerIngressAtNanos = System.nanoTime();
        if (!(packet instanceof ClientboundPartyInfoPacket partyInfo)) return;
        long decodedAtNanos = ((DungeonPartyAuthorityTracker.IngressTimestampCarrier) partyInfo)
                .qca$ingressAtNanos();
        long ingressAtNanos = decodedAtNanos > 0L
                && decodedAtNanos <= handlerIngressAtNanos ? decodedAtNanos : 0L;
        DungeonPartyAuthorityTracker.onPartyInfoResponseIngress(partyInfo, ingressAtNanos);
    }

    @Inject(
            method = "handleError(Ljava/lang/String;Lnet/hypixel/modapi/error/ErrorReason;)V",
            at = @At("HEAD"),
            remap = false
    )
    private void qca$consumePartyInfoErrorBeforeRegisteredHandlers(
            String identifier, ErrorReason reason, CallbackInfo ci) {
        long handlerIngressAtNanos = System.nanoTime();
        if (PARTY_INFO_IDENTIFIER.equals(identifier)) {
            DungeonPartyAuthorityTracker.onPartyInfoErrorIngress(handlerIngressAtNanos);
        }
    }
}

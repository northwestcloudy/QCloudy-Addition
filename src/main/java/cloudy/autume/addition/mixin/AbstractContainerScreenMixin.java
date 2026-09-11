package cloudy.autume.addition.mixin;

import cloudy.autume.addition.dungeon.DungeonQuickViewManager;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reads Group Builder selections at the click boundary, before Hypixel closes the menu. */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Inject(method = "slotClicked", at = @At("HEAD"))
    private void qca$captureGroupBuilderFloor(Slot slot, int slotId, int buttonNum,
                                               ContainerInput input, CallbackInfo callback) {
        DungeonQuickViewManager.onContainerSlotClick(
                (AbstractContainerScreen<?>) (Object) this, slot, buttonNum, input);
    }
}

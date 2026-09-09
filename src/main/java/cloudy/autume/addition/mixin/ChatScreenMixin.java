package cloudy.autume.addition.mixin;

import cloudy.autume.addition.chat.ChatChannelSwitcher;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gives QCA's channel row priority over chat text and command suggestions. */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Shadow protected EditBox input;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void qca$channelButtonClick(MouseButtonEvent click, boolean doubled,
                                        CallbackInfoReturnable<Boolean> callback) {
        if (ChatChannelSwitcher.handleClick((ChatScreen) (Object) this, click, doubled)) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void qca$protectPendingChannelDraft(KeyEvent event,
                                                 CallbackInfoReturnable<Boolean> callback) {
        if (event.isConfirmation() && ChatChannelSwitcher.shouldBlockPlainMessage(input.getValue())) {
            callback.setReturnValue(true);
        }
    }
}

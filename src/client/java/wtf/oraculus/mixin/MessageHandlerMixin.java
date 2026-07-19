package wtf.oraculus.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.message.MessageHandler;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wtf.oraculus.event.EventDispatcher;
import wtf.oraculus.event.impl.game.chat.ChatReceivedEvent;

import java.time.Instant;

@Mixin(MessageHandler.class)
public final class MessageHandlerMixin {

    @Inject(method = "processChatMessageInternal", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;getChatHud()Lnet/minecraft/client/gui/hud/ChatHud;", ordinal = 0), cancellable = true)
    private void hookOnSignedChatMessage(MessageType.Parameters params, SignedMessage message, Text decorated, GameProfile sender, boolean onlyShowSecureChat, Instant receptionTimestamp, CallbackInfoReturnable<Boolean> cir) {
        oraculus$onChatMessage(decorated, cir);
    }

    @Inject(method = "processChatMessageInternal", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;getChatHud()Lnet/minecraft/client/gui/hud/ChatHud;", ordinal = 1), cancellable = true)
    private void hookOnFilteredSignedChatMessage(MessageType.Parameters params, SignedMessage message, Text decorated, GameProfile sender, boolean onlyShowSecureChat, Instant receptionTimestamp, CallbackInfoReturnable<Boolean> cir) {
        final Text filtered = message.filterMask().getFilteredText(message.getSignedContent());
        if (filtered == null) {
            return;
        }
        oraculus$onChatMessage(params.applyChatDecoration(filtered), cir);
    }

    @Inject(method = "method_45745", at = @At("HEAD"), cancellable = true)
    private void hookOnProfilelessChatMessage(MessageType.Parameters params, Text content, Instant receptionTimestamp, CallbackInfoReturnable<Boolean> cir) {
        oraculus$onChatMessage(params.applyChatDecoration(content), cir);
    }

    @Unique
    private void oraculus$onChatMessage(Text message, CallbackInfoReturnable<Boolean> cir) {
        final ChatReceivedEvent event = new ChatReceivedEvent(message, false);
        EventDispatcher.dispatch(event);
        if (event.isCancelled()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "onGameMessage", at = @At("HEAD"), cancellable = true)
    private void hookOnGameMessage(Text message, boolean overlay, CallbackInfo ci) {
        final ChatReceivedEvent event = new ChatReceivedEvent(message, overlay);
        EventDispatcher.dispatch(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

}

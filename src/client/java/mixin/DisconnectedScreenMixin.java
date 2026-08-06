package mixin;

import net.minecraft.client.gui.screen.DisconnectedScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.oraculus.event.EventDispatcher;
import wtf.oraculus.event.impl.game.server.ServerDisconnectEvent;

@Mixin(DisconnectedScreen.class)
public final class DisconnectedScreenMixin {

    private DisconnectedScreenMixin() {
    }

    @Inject(
            method = "init",
            at = @At("HEAD")
    )
    private void injectDisconnectEvent(CallbackInfo ci) {
        EventDispatcher.dispatch(new ServerDisconnectEvent());
    }

}

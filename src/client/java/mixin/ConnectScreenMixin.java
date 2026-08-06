package mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.CookieStorage;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.oraculus.event.EventDispatcher;
import wtf.oraculus.client.edition.EditionHooks;
import wtf.oraculus.event.impl.game.server.ServerConnectEvent;

@Mixin(ConnectScreen.class)
public final class ConnectScreenMixin {

    private ConnectScreenMixin() {
    }

    @Inject(
            method = "connect(Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/network/ServerAddress;Lnet/minecraft/client/network/ServerInfo;Lnet/minecraft/client/network/CookieStorage;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onConnect(MinecraftClient client, ServerAddress address, ServerInfo info, CookieStorage cookieStorage, CallbackInfo ci) {
        final ServerConnectEvent serverConnectEvent = new ServerConnectEvent(address);
        EventDispatcher.dispatch(serverConnectEvent);
        if (serverConnectEvent.isCancelled()) {
            ci.cancel();
            return;
        }
        if (EditionHooks.shouldDeferServerConnection(address,
                () -> ((ConnectScreenInvoker) (Object) this).invokeConnect(client, address, info, cookieStorage))) {
            ci.cancel();
        }
    }

}

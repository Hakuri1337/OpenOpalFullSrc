package mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.CookieStorage;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ConnectScreen.class)
public interface ConnectScreenInvoker {
    @Invoker("connect")
    void invokeConnect(MinecraftClient client, ServerAddress address, ServerInfo info,
                       CookieStorage cookieStorage);
}

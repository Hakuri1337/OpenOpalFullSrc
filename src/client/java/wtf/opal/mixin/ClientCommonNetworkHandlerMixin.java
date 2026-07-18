package wtf.opal.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.module.impl.utility.ServerPackSpoofModule;

@Mixin(ClientCommonNetworkHandler.class)
public abstract class ClientCommonNetworkHandlerMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    @Final
    private ClientConnection connection;

    @Inject(
            method = "onResourcePackSend",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V",
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void opal$interceptServerResourcePack(final ResourcePackSendS2CPacket packet, final CallbackInfo ci) {
        final OpalClient opal = OpalClient.getInstance();
        if (!opal.isPostInitialization() || opal.getModuleRepository() == null) {
            return;
        }

        final ServerPackSpoofModule module = opal.getModuleRepository().getModule(ServerPackSpoofModule.class);
        if (module == null || !module.isEnabled()) {
            return;
        }

        module.handleRequest(this.client, this.connection, packet);
        ci.cancel();
    }
}

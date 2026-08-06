package wtf.oraculus.mixin;

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
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.module.impl.utility.ServerPackSpoofModule;

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
    private void oraculus$interceptServerResourcePack(final ResourcePackSendS2CPacket packet, final CallbackInfo ci) {
        final OraculusClient oraculus = OraculusClient.getInstance();
        if (!oraculus.isPostInitialization() || oraculus.getModuleRepository() == null) {
            return;
        }

        final ServerPackSpoofModule module = oraculus.getModuleRepository().getModule(ServerPackSpoofModule.class);
        if (module == null || !module.isEnabled()) {
            return;
        }

        module.handleRequest(this.client, this.connection, packet);
        ci.cancel();
    }
}

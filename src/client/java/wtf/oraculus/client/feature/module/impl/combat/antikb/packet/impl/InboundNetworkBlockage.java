package wtf.oraculus.client.feature.module.impl.combat.antikb.packet.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.*;
import wtf.oraculus.client.feature.module.impl.combat.antikb.packet.DirectionalNetworkBlockage;
import wtf.oraculus.client.feature.module.impl.combat.antikb.packet.block.PacketValidator;
import wtf.oraculus.duck.ClientConnectionAccess;

public final class InboundNetworkBlockage extends DirectionalNetworkBlockage<ClientPlayNetworkHandler> {
    public static InboundNetworkBlockage get() {
        return instance;
    }

    private static final InboundNetworkBlockage instance;

    static {
        instance = new InboundNetworkBlockage();
    }

    @Override
    protected void flushPacket(ClientConnection connection, Packet<?> packet) {
        final ClientConnectionAccess access = (ClientConnectionAccess) connection;
        // TODO: check if this fixes flushing
        MinecraftClient.getInstance().send(() -> access.oraculus$channelReadSilent(packet));
    }

    public static final PacketValidator VISUAL_VALIDATOR = p -> {
        if (p instanceof EntityStatusS2CPacket status) {
            return status.getStatus() != 2 && status.getStatus() != 3;
        } else if (p instanceof EntityTrackerUpdateS2CPacket tracker) {
            final ClientPlayerEntity clientPlayer = MinecraftClient.getInstance().player;
            return clientPlayer == null || tracker.id() == clientPlayer.getId();
        }
        return !(p instanceof EntityAnimationS2CPacket || p instanceof TitleS2CPacket || p instanceof TitleFadeS2CPacket || p instanceof ClearTitleS2CPacket ||
                p instanceof PlaySoundS2CPacket || p instanceof StopSoundS2CPacket || p instanceof ChatMessageS2CPacket || p instanceof ChatSuggestionsS2CPacket ||
                p instanceof EntityEquipmentUpdateS2CPacket || p instanceof SubtitleS2CPacket);
    };
}
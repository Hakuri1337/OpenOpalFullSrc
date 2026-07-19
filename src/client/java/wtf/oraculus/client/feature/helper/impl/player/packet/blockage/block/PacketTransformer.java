package wtf.oraculus.client.feature.helper.impl.player.packet.blockage.block;

import net.minecraft.network.packet.Packet;

public interface
PacketTransformer {
    Packet<?> transform(Packet<?> packet);
}

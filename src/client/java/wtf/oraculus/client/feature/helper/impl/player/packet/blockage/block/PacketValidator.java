package wtf.oraculus.client.feature.helper.impl.player.packet.blockage.block;

import net.minecraft.network.packet.Packet;

public interface PacketValidator {
    boolean isValid(Packet<?> packet);
}

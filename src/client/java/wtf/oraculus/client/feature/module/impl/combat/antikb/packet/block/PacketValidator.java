package wtf.oraculus.client.feature.module.impl.combat.antikb.packet.block;

import net.minecraft.network.packet.Packet;

public interface PacketValidator {
    boolean isValid(Packet<?> packet);
}

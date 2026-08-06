package wtf.oraculus.duck;

import net.minecraft.network.packet.Packet;

public interface ClientConnectionAccess {
    void oraculus$channelReadSilent(Packet<?> packet);
    void oraculus$sendPacketSilent(Packet<?> packet);
}

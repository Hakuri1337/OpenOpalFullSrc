package wtf.oraculus.event.impl.game.packet;

import net.minecraft.network.packet.Packet;
import wtf.oraculus.event.EventCancellable;

public final class ReceivePacketEvent extends EventCancellable {

    private Packet<?> packet;

    public ReceivePacketEvent(final Packet<?> packet) {
        this.packet = packet;
    }

    public Packet<?> getPacket() {
        return packet;
    }

    public void setPacket(final Packet<?> packet) {
        this.packet = packet;
    }

}

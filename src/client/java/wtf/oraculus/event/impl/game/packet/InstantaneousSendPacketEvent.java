package wtf.oraculus.event.impl.game.packet;

import net.minecraft.network.packet.Packet;
import wtf.oraculus.event.EventCancellable;

public final class InstantaneousSendPacketEvent extends EventCancellable {

    private final Packet<?> packet;

    public InstantaneousSendPacketEvent(final Packet<?> packet) {
        this.packet = packet;
    }

    public Packet<?> getPacket() {
        return packet;
    }

}

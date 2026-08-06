package wtf.oraculus.utility.network;

import net.minecraft.network.packet.Packet;

import java.util.ArrayList;
import java.util.List;

import static wtf.oraculus.client.Constants.mc;

public final class PacketUtility {

    private static final List<Packet<?>> QUEUED_PACKETS = new ArrayList<>();

    private PacketUtility() {
    }

    public static boolean shouldBypass(final Packet<?> packet) {
        synchronized (QUEUED_PACKETS) {
            return removeByIdentity(packet);
        }
    }

    public static void sendQueued(final Packet<?> packet) {
        if (mc.player == null || mc.getNetworkHandler() == null) {
            return;
        }

        synchronized (QUEUED_PACKETS) {
            QUEUED_PACKETS.add(packet);
        }

        try {
            mc.getNetworkHandler().sendPacket(packet);
        } finally {
            synchronized (QUEUED_PACKETS) {
                removeByIdentity(packet);
            }
        }
    }

    private static boolean removeByIdentity(final Packet<?> packet) {
        for (int i = 0; i < QUEUED_PACKETS.size(); i++) {
            if (QUEUED_PACKETS.get(i) == packet) {
                QUEUED_PACKETS.remove(i);
                return true;
            }
        }
        return false;
    }
}

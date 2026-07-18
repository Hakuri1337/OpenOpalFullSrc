package wtf.opal.client.feature.module.impl.utility.disabler.impl;

import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import wtf.opal.client.feature.helper.impl.player.packet.blockage.impl.OutboundNetworkBlockage;
import wtf.opal.client.feature.module.impl.utility.disabler.DisablerModule;
import wtf.opal.client.feature.module.property.impl.mode.ModuleMode;
import wtf.opal.event.impl.game.JoinWorldEvent;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.impl.game.packet.SendPacketEvent;
import wtf.opal.event.impl.game.server.ServerDisconnectEvent;
import wtf.opal.event.subscriber.Subscribe;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static wtf.opal.client.Constants.mc;

public final class CubecraftDisabler extends ModuleMode<DisablerModule> {
    private static final long PACKET_DELAY_MS = 2_000L;

    private record PacketEntry(Packet<?> packet, long queuedAt) {
    }

    private final Queue<PacketEntry> packetQueue = new ConcurrentLinkedQueue<>();
    private long disabledStartedAt;

    public CubecraftDisabler(DisablerModule module) {
        super(module);
    }

    @Subscribe
    public void onSendPacket(SendPacketEvent event) {
        if (mc.player == null || this.isWaiting()) {
            return;
        }

        final Packet<?> packet = event.getPacket();
        if (packet instanceof ClientCommandC2SPacket command
                && command.getMode() == ClientCommandC2SPacket.Mode.START_SPRINTING) {
            event.setCancelled();
            return;
        }

        if (packet instanceof KeepAliveC2SPacket || packet instanceof CommonPongC2SPacket) {
            this.packetQueue.add(new PacketEntry(packet, System.currentTimeMillis()));
            event.setCancelled();
        }
    }

    @Subscribe
    public void onPreGameTick(PreGameTickEvent event) {
        final long now = System.currentTimeMillis();
        PacketEntry entry;
        while ((entry = this.packetQueue.peek()) != null && now - entry.queuedAt() >= PACKET_DELAY_MS) {
            this.packetQueue.poll();
            sendDirect(entry.packet());
        }
    }

    @Subscribe
    public void onJoinWorld(JoinWorldEvent event) {
        this.clearState();
    }

    @Subscribe
    public void onServerDisconnect(ServerDisconnectEvent event) {
        this.clearState();
    }

    @Override
    public void onEnable() {
        this.clearState();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        this.flushQueue();
        super.onDisable();
    }

    @Override
    public Enum<?> getEnumValue() {
        return DisablerModule.Mode.CUBECRAFT;
    }

    public boolean isWaiting() {
        return false;
    }

    public double getDisabledDuration() {
        return (System.currentTimeMillis() - this.disabledStartedAt) / 1_000.0D;
    }

    public int getQueuedPacketCount() {
        return this.packetQueue.size();
    }

    public String getStatusSuffix() {
        return "Sentinel";
    }

    private void flushQueue() {
        PacketEntry entry;
        while ((entry = this.packetQueue.poll()) != null) {
            sendDirect(entry.packet());
        }
    }

    private void clearState() {
        this.packetQueue.clear();
        this.disabledStartedAt = System.currentTimeMillis();
    }

    private static void sendDirect(Packet<?> packet) {
        if (mc.getNetworkHandler() != null) {
            OutboundNetworkBlockage.sendPacketDirect(packet);
        }
    }
}

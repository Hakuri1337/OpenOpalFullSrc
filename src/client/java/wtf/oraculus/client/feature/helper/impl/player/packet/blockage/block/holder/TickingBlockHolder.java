package wtf.oraculus.client.feature.helper.impl.player.packet.blockage.block.holder;

import org.jetbrains.annotations.Nullable;
import wtf.oraculus.client.feature.helper.impl.player.packet.blockage.DirectionalNetworkBlockage;
import wtf.oraculus.client.feature.helper.impl.player.packet.blockage.block.NetworkBlock;
import wtf.oraculus.client.feature.helper.impl.player.packet.blockage.block.PacketValidator;

import java.util.ArrayList;
import java.util.List;

public final class TickingBlockHolder {
    private final DirectionalNetworkBlockage<?> networkBlockage;
    private final @Nullable PacketValidator packetValidator;

    public TickingBlockHolder(DirectionalNetworkBlockage<?> networkBlockage, @Nullable PacketValidator packetValidator) {
        this.networkBlockage = networkBlockage;
        this.packetValidator = packetValidator;
    }

    public TickingBlockHolder(DirectionalNetworkBlockage<?> networkBlockage) {
        this(networkBlockage, null);
    }

    private final List<NetworkBlock> networkBlockList = new ArrayList<>();

    public void tick() {
        synchronized (this.networkBlockList) {
            this.networkBlockList.add(this.networkBlockage.newBlockage(null, this.packetValidator));
        }
    }

    public void release(int count) {
        synchronized (this.networkBlockList) {
            while (!this.networkBlockList.isEmpty() && count > 0) {
                final NetworkBlock block = this.networkBlockList.removeFirst();
                this.networkBlockage.releaseBlockage(block);
                count--;
            }
        }
    }

    public void release() {
        synchronized (this.networkBlockList) {
            while (!this.networkBlockList.isEmpty()) {
                final NetworkBlock block = this.networkBlockList.removeFirst();
                this.networkBlockage.releaseBlockage(block);
            }
        }
    }

    public boolean isBlocking() {
        synchronized (this.networkBlockList) {
            return !this.networkBlockList.isEmpty();
        }
    }

    public int getTickCount() {
        synchronized (this.networkBlockList) {
            return this.networkBlockList.size();
        }
    }

    public List<NetworkBlock> getNetworkBlockList() {
        return networkBlockList;
    }
}

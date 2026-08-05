package wtf.oraculus.client.feature.module.impl.combat.antikb.packet.block.holder;

import wtf.oraculus.client.feature.module.impl.combat.antikb.packet.DirectionalNetworkBlockage;
import wtf.oraculus.client.feature.module.impl.combat.antikb.packet.block.NetworkBlock;
import wtf.oraculus.client.feature.module.impl.combat.antikb.packet.block.PacketTransformer;
import wtf.oraculus.client.feature.module.impl.combat.antikb.packet.block.PacketValidator;

public final class BlockHolder {
    private final DirectionalNetworkBlockage<?> networkBlockage;
    private final boolean priority;

    public BlockHolder(DirectionalNetworkBlockage<?> networkBlockage, boolean priority) {
        this.networkBlockage = networkBlockage;
        this.priority = priority;
    }

    public BlockHolder(DirectionalNetworkBlockage<?> networkBlockage) {
        this(networkBlockage, false);
    }

    private NetworkBlock networkBlock;

    /**
     * Blocks the connection in the utilized direction
     */
    public void block(PacketTransformer packetTransformer, PacketValidator packetValidator) {
        if (this.networkBlock == null) {
            this.networkBlock = this.networkBlockage.newBlockage(packetTransformer, packetValidator, this.priority);
        }
    }

    public void block(PacketTransformer packetTransformer) {
        this.block(packetTransformer, null);
    }

    public void block() {
        this.block(null);
    }

    public void setPacketTransformer(PacketTransformer packetTransformer) {
        if (this.networkBlock != null) {
            this.networkBlock.setPacketTransformer(packetTransformer);
        }
    }

    /**
     * Entirely releases the block, it must be blocked again for packets to be queued
     */
    public void release() {
        if (this.networkBlock != null) {
            this.networkBlockage.releaseBlockage(this.networkBlock);
            this.networkBlock = null;
        }
    }

    /**
     * Flushes queued packets and maintains the network block
     */
    public void flush() {
        this.release();
        this.block();
    }

    public boolean isBlocking() {
        return this.networkBlock != null;
    }
}

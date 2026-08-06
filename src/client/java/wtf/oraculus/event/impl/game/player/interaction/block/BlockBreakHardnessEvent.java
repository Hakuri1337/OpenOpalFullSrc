package wtf.oraculus.event.impl.game.player.interaction.block;

import net.minecraft.block.BlockState;

public final class BlockBreakHardnessEvent {
    private final BlockState blockState;
    private float hardness;

    public BlockBreakHardnessEvent(BlockState blockState, float hardness) {
        this.blockState = blockState;
        this.hardness = hardness;
    }

    public BlockState getBlockState() {
        return blockState;
    }

    public float getHardness() {
        return hardness;
    }

    public void setHardness(float hardness) {
        this.hardness = hardness;
    }
}
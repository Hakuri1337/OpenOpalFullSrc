package wtf.oraculus.event.impl.game.player.interaction.block;

import net.minecraft.block.BlockState;

public final class BlockBreakCanHarvestEvent {
    private final BlockState blockState;
    private boolean canHarvest;

    public BlockBreakCanHarvestEvent(BlockState blockState, boolean canHarvest) {
        this.blockState = blockState;
        this.canHarvest = canHarvest;
    }

    public BlockState getBlockState() {
        return blockState;
    }

    public boolean isCanHarvest() {
        return canHarvest;
    }

    public void setCanHarvest(boolean canHarvest) {
        this.canHarvest = canHarvest;
    }
}

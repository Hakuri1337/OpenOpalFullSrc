package wtf.oraculus.event.impl.game.player.interaction.block;

import net.minecraft.util.hit.BlockHitResult;

public final class BlockPlacedEvent {

    private final BlockHitResult blockHitResult;

    public BlockPlacedEvent(final BlockHitResult blockHitResult) {
        this.blockHitResult = blockHitResult;
    }

    public BlockHitResult getBlockHitResult() {
        return blockHitResult;
    }
}

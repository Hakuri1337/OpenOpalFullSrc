package wtf.oraculus.client.feature.module.impl.world.blockfly.state;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public record BlockFlyPlacementTarget(BlockPos supportPos, Direction clickedFace) {
    public BlockPos placePos() {
        return this.supportPos.offset(this.clickedFace);
    }
}

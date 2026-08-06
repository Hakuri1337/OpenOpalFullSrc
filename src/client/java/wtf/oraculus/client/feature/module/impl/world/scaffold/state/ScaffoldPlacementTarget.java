package wtf.oraculus.client.feature.module.impl.world.scaffold.state;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public record ScaffoldPlacementTarget(BlockPos supportPos, Direction clickedFace) {
    public BlockPos placePos() {
        return this.supportPos.offset(this.clickedFace);
    }
}

package wtf.oraculus.client.feature.module.impl.world.ssngscaffold;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** SSNG semantics: pos is the support block, facing is the clicked face. */
public record SsngBlockData(BlockPos pos, Direction facing) {
    public BlockPos placePos() {
        return this.pos.offset(this.facing);
    }
}

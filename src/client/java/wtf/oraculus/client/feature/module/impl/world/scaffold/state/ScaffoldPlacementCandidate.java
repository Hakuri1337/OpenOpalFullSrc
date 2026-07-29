package wtf.oraculus.client.feature.module.impl.world.scaffold.state;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public record ScaffoldPlacementCandidate(BlockPos pos, Direction direction, int depth) {
}

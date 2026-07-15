package wtf.opal.client.feature.module.impl.world.blockfly.state;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public record BlockFlyPlacementCandidate(BlockPos pos, Direction direction, int depth) {
}

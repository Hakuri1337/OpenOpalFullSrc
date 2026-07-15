package wtf.opal.client.feature.module.impl.world.blockfly.block;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import wtf.opal.client.feature.module.impl.world.blockfly.state.BlockFlyPlacementCandidate;
import wtf.opal.client.feature.module.impl.world.blockfly.state.BlockFlyPlacementTarget;

import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;

import static wtf.opal.client.Constants.mc;

public final class BlockFlyPlacementSearch {
    private BlockFlyPlacementSearch() {
    }

    public static BlockFlyPlacementTarget findShellTarget(final Vec3d eye, final int targetYLevel) {
        if (mc.world == null || mc.player == null) {
            return null;
        }

        final BlockPos belowFeet = BlockPos.ofFloored(eye.x, targetYLevel + 0.1D, eye.z);
        if (BlockFlyBlockUtil.hasSolidTop(belowFeet)) {
            return null;
        }

        BlockFlyPlacementTarget result = targetFromAirPosition(eye, belowFeet);
        if (result != null) {
            return result;
        }

        final int feetX = belowFeet.getX();
        final int feetZ = belowFeet.getZ();
        for (int radius = 1; radius <= 6; radius++) {
            result = targetFromAirPosition(eye, new BlockPos(feetX, targetYLevel - radius, feetZ));
            if (result != null) {
                return result;
            }

            for (int x = 1; x <= radius; x++) {
                for (int z = 0; z <= radius - x; z++) {
                    final int yOffset = radius - x - z;
                    for (int signX = 0; signX <= 1; signX++) {
                        for (int signZ = 0; signZ <= 1; signZ++) {
                            final BlockPos candidate = new BlockPos(
                                    feetX + (signX == 0 ? x : -x),
                                    targetYLevel - yOffset,
                                    feetZ + (signZ == 0 ? z : -z)
                            );
                            result = targetFromAirPosition(eye, candidate);
                            if (result != null) {
                                return result;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    public static BlockFlyPlacementTarget findLegacyBfsTarget(
            final BlockPos origin,
            final int targetYLevel
    ) {
        if (mc.world == null || mc.player == null) {
            return null;
        }
        final Direction[] directions = {
                Direction.DOWN,
                Direction.EAST,
                Direction.WEST,
                Direction.NORTH,
                Direction.SOUTH,
                Direction.UP
        };
        final PriorityQueue<BlockFlyPlacementCandidate> queue = new PriorityQueue<>(
                Comparator.comparingDouble(candidate -> manhattan(candidate.pos(), origin))
        );
        final Set<BlockPos> visited = new HashSet<>();
        queue.offer(new BlockFlyPlacementCandidate(origin, null, 0));
        visited.add(origin);

        while (!queue.isEmpty()) {
            final BlockFlyPlacementCandidate candidate = queue.poll();
            for (final Direction direction : directions) {
                final BlockPos neighbor = candidate.pos().offset(direction);
                if (visited.contains(neighbor) || manhattan(neighbor, origin) > 4.5D) {
                    continue;
                }
                visited.add(neighbor);
                if (isValidLegacyBlock(neighbor, targetYLevel)) {
                    final Direction face = direction == Direction.DOWN ? Direction.UP : direction.getOpposite();
                    if (mc.world.getBlockState(neighbor).isSolidSurface(mc.world, neighbor, mc.player, face)) {
                        return new BlockFlyPlacementTarget(neighbor, face);
                    }
                } else if (candidate.depth() < 3) {
                    queue.offer(new BlockFlyPlacementCandidate(neighbor, direction, candidate.depth() + 1));
                }
            }
        }
        return null;
    }

    private static BlockFlyPlacementTarget targetFromAirPosition(final Vec3d eye, final BlockPos placePos) {
        if (!BlockFlyBlockUtil.isAir(placePos)) {
            return null;
        }
        final Vec3d center = Vec3d.ofCenter(placePos);
        for (final Direction sourceDirection : Direction.values()) {
            final Vec3d normal = Vec3d.of(sourceDirection.getVector());
            final Vec3d facePoint = center.add(normal.multiply(0.5D));
            final BlockPos supportPos = placePos.offset(sourceDirection);
            if (!BlockFlyBlockUtil.isSupportFace(supportPos, sourceDirection)) {
                continue;
            }
            final Vec3d delta = facePoint.subtract(eye);
            if (delta.lengthSquared() <= 20.25D
                    && delta.normalize().dotProduct(normal.normalize()) >= 0.0D) {
                return new BlockFlyPlacementTarget(supportPos.toImmutable(), sourceDirection.getOpposite());
            }
        }
        return null;
    }

    private static boolean isValidLegacyBlock(final BlockPos pos, final int targetYLevel) {
        if (mc.world == null || mc.world.isOutOfHeightLimit(pos.getY())) {
            return false;
        }
        final BlockState state = mc.world.getBlockState(pos);
        return BlockFlyBlockUtil.isSearchSolid(state, pos)
                && pos.getY() <= targetYLevel + 1
                && !state.getCollisionShape(mc.world, pos).isEmpty();
    }

    private static double manhattan(final BlockPos first, final BlockPos second) {
        return Math.abs(first.getX() - second.getX())
                + Math.abs(first.getY() - second.getY())
                + Math.abs(first.getZ() - second.getZ());
    }
}

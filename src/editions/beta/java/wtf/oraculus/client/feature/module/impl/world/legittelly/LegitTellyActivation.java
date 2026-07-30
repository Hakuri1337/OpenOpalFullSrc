package wtf.oraculus.client.feature.module.impl.world.legittelly;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import wtf.oraculus.utility.player.RotationUtility;

import static wtf.oraculus.client.Constants.mc;

public final class LegitTellyActivation {
    private static final float YAW_TOLERANCE = 2.0F;
    private static final float MIN_PITCH = 75.0F;
    private static final double EDGE_READY_DISTANCE = 0.65D;
    public static final double SIDE_MIN_ACROSS = 0.38D;
    public static final double SIDE_MAX_ACROSS = 0.65D;
    public static final double SIDE_MIN_HEIGHT = 0.25D;
    public static final double SIDE_MAX_HEIGHT = 0.75D;

    public ActivationInspection inspect() {
        if (mc.player == null || mc.world == null) {
            return ActivationInspection.failed(ActivationIssue.WORLD_UNAVAILABLE);
        }

        final float baseYaw = nearestDiagonalYaw(mc.player.getYaw());
        final float yawDifference = MathHelper.angleBetween(mc.player.getYaw(), baseYaw);
        if (yawDifference > YAW_TOLERANCE) {
            return ActivationInspection.failed(
                    ActivationIssue.ALIGN_DIAGONAL, yawDifference
            );
        }
        if (mc.player.getPitch() < MIN_PITCH) {
            return ActivationInspection.failed(
                    ActivationIssue.LOOK_DOWN, mc.player.getPitch()
            );
        }

        final Direction travel = travelDirection(baseYaw);
        final BlockPos belowPlayer = supportBlockForPlayer(travel);
        final double edgeGap = edgeGap(belowPlayer, travel);
        if (edgeGap > 0.0D) {
            return ActivationInspection.failed(
                    ActivationIssue.MOVE_TO_EDGE, edgeGap
            );
        }
        final BlockPos aheadFeet = belowPlayer.offset(travel).up();
        if (!LegitTellyBlockPolicy.isReplaceable(aheadFeet)) {
            return ActivationInspection.failed(ActivationIssue.FRONT_BLOCKED);
        }

        final Vec3d eye = mc.player.getEyePos();
        final Vec3d end = eye.add(RotationUtility.getRotationVector(
                mc.player.getPitch(), mc.player.getYaw()
        ).multiply(mc.player.getBlockInteractionRange()));
        final BlockHitResult hit = mc.world.raycast(new RaycastContext(
                eye, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, mc.player
        ));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return ActivationInspection.failed(ActivationIssue.AIM_AT_BLOCK);
        }
        if (hit.getSide().getAxis().isVertical() || hit.getSide() != travel) {
            return ActivationInspection.failed(ActivationIssue.AIM_AT_FORWARD_SIDE);
        }

        final BlockPos activationBlock = hit.getBlockPos();
        if (!belowPlayer.equals(activationBlock)) {
            return ActivationInspection.failed(ActivationIssue.AIM_AT_OWN_BLOCK);
        }
        if (!isNarrowHit(hit, activationBlock, travel)) {
            return ActivationInspection.failed(ActivationIssue.AIM_AT_SIDE_CENTER);
        }

        final boolean alongX = travel.getAxis() == Direction.Axis.X;
        final double lane = alongX ? mc.player.getZ() : mc.player.getX();
        final int bridgeLaneBlock = MathHelper.floor(lane);
        final double progress = progress(activationBlock, travel);
        return ActivationInspection.ready(new ActivationSnapshot(
                activationBlock.toImmutable(),
                travel,
                mc.player.getYaw(),
                lane,
                bridgeLaneBlock,
                progress
        ));
    }

    public enum ActivationIssue {
        READY,
        WORLD_UNAVAILABLE,
        ALIGN_DIAGONAL,
        LOOK_DOWN,
        MOVE_TO_EDGE,
        FRONT_BLOCKED,
        AIM_AT_BLOCK,
        AIM_AT_FORWARD_SIDE,
        AIM_AT_OWN_BLOCK,
        AIM_AT_SIDE_CENTER
    }

    public record ActivationInspection(
            ActivationSnapshot snapshot,
            ActivationIssue issue,
            double measurement
    ) {
        static ActivationInspection ready(final ActivationSnapshot snapshot) {
            return new ActivationInspection(snapshot, ActivationIssue.READY, 0.0D);
        }

        static ActivationInspection failed(final ActivationIssue issue) {
            return failed(issue, 0.0D);
        }

        static ActivationInspection failed(
                final ActivationIssue issue,
                final double measurement
        ) {
            return new ActivationInspection(null, issue, measurement);
        }
    }

    public static float requiredPitch() {
        return MIN_PITCH;
    }

    public static float nearestDiagonalYaw(final float yaw) {
        return Math.round((yaw - 45.0F) / 90.0F) * 90.0F + 45.0F;
    }

    public static Direction travelDirectionForYaw(final float yaw) {
        return travelDirection(nearestDiagonalYaw(yaw));
    }

    public static BlockPos blockBelowPlayer() {
        if (mc.player == null) {
            return BlockPos.ORIGIN;
        }
        return new BlockPos(
                MathHelper.floor(mc.player.getX()),
                MathHelper.floor(mc.player.getY()) - 1,
                MathHelper.floor(mc.player.getZ())
        );
    }

    public static BlockPos supportBlockForPlayer(final Direction travel) {
        final BlockPos centered = blockBelowPlayer();
        if (mc.world == null || travel == null || !travel.getAxis().isHorizontal()
                || LegitTellyBlockPolicy.isSafeSupport(centered)) {
            return centered;
        }
        final BlockPos behind = centered.offset(travel.getOpposite());
        return LegitTellyBlockPolicy.isSafeSupport(behind) ? behind : centered;
    }

    public static Vec3d sidePoint(
            final BlockPos block,
            final Direction travel,
            final double across,
            final double height,
            final double outwardOffset
    ) {
        final double physicalAcross = travel == Direction.SOUTH || travel == Direction.WEST
                ? 1.0D - across
                : across;
        return switch (travel) {
            case NORTH -> new Vec3d(
                    block.getX() + physicalAcross,
                    block.getY() + height,
                    block.getZ() - outwardOffset
            );
            case SOUTH -> new Vec3d(
                    block.getX() + physicalAcross,
                    block.getY() + height,
                    block.getZ() + 1.0D + outwardOffset
            );
            case WEST -> new Vec3d(
                    block.getX() - outwardOffset,
                    block.getY() + height,
                    block.getZ() + physicalAcross
            );
            case EAST -> new Vec3d(
                    block.getX() + 1.0D + outwardOffset,
                    block.getY() + height,
                    block.getZ() + physicalAcross
            );
            default -> Vec3d.ofCenter(block);
        };
    }

    public static Vec3d sideAimPoint(final BlockPos block, final Direction travel) {
        return sidePoint(
                block,
                travel,
                (SIDE_MIN_ACROSS + SIDE_MAX_ACROSS) * 0.5D,
                (SIDE_MIN_HEIGHT + SIDE_MAX_HEIGHT) * 0.5D,
                0.002D
        );
    }

    public static String directionName(final Direction direction) {
        return switch (direction) {
            case NORTH -> "北";
            case SOUTH -> "南";
            case EAST -> "东";
            case WEST -> "西";
            default -> "-";
        };
    }

    public static double progress(final BlockPos pos, final Direction direction) {
        return pos.getX() * direction.getOffsetX() + pos.getZ() * direction.getOffsetZ();
    }

    private static Direction travelDirection(final float baseYaw) {
        final double radians = Math.toRadians(baseYaw + 135.0F);
        final double x = -Math.sin(radians);
        final double z = Math.cos(radians);
        if (Math.abs(x) > Math.abs(z)) {
            return x > 0.0D ? Direction.EAST : Direction.WEST;
        }
        return z > 0.0D ? Direction.SOUTH : Direction.NORTH;
    }

    public static boolean isNarrowHit(
            final BlockHitResult hit,
            final BlockPos block,
            final Direction travel
    ) {
        final Vec3d point = hit.getPos();
        double across = travel.getAxis() == Direction.Axis.Z
                ? point.x - block.getX()
                : point.z - block.getZ();
        if (travel == Direction.SOUTH || travel == Direction.WEST) {
            across = 1.0D - across;
        }
        final double height = point.y - block.getY();
        return across >= SIDE_MIN_ACROSS && across <= SIDE_MAX_ACROSS
                && height >= SIDE_MIN_HEIGHT && height <= SIDE_MAX_HEIGHT;
    }

    public static double edgeGap(final BlockPos block, final Direction travel) {
        if (mc.player == null) {
            return Double.POSITIVE_INFINITY;
        }
        return lipDistance(block, travel) - desiredEdgeDistance();
    }

    public static double desiredEdgeDistance() {
        return EDGE_READY_DISTANCE;
    }

    public static boolean isAimingAtSide(final BlockPos block, final Direction travel) {
        if (mc.player == null || mc.world == null) {
            return false;
        }
        final Vec3d eye = mc.player.getEyePos();
        final Vec3d end = eye.add(RotationUtility.getRotationVector(
                mc.player.getPitch(), mc.player.getYaw()
        ).multiply(mc.player.getBlockInteractionRange()));
        final BlockHitResult hit = mc.world.raycast(new RaycastContext(
                eye, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, mc.player
        ));
        return hit.getType() == HitResult.Type.BLOCK
                && block.equals(hit.getBlockPos())
                && hit.getSide() == travel
                && isNarrowHit(hit, block, travel);
    }

    public static Vec2f findActivationAim(
            final BlockPos block,
            final Direction travel,
            final float baseYaw
    ) {
        if (mc.player == null || mc.world == null || block == null || travel == null) {
            return null;
        }
        Vec2f best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        final float currentPitch = MathHelper.clamp(
                mc.player.getPitch(), MIN_PITCH, 89.0F
        );
        for (float yawOffset = -YAW_TOLERANCE;
             yawOffset <= YAW_TOLERANCE + 0.001F;
             yawOffset += 0.5F) {
            final float yaw = baseYaw + yawOffset;
            for (float pitch = MIN_PITCH; pitch <= 89.0F; pitch += 0.5F) {
                final BlockHitResult hit = raycast(yaw, pitch);
                if (hit.getType() != HitResult.Type.BLOCK
                        || !block.equals(hit.getBlockPos())
                        || hit.getSide() != travel
                        || !isNarrowHit(hit, block, travel)) {
                    continue;
                }
                final double score = Math.abs(yawOffset) * 2.0D
                        + Math.abs(pitch - currentPitch);
                if (score < bestScore) {
                    bestScore = score;
                    best = new Vec2f(yaw, pitch);
                }
            }
        }
        return best;
    }

    private static BlockHitResult raycast(final float yaw, final float pitch) {
        final Vec3d eye = mc.player.getEyePos();
        final Vec3d end = eye.add(RotationUtility.getRotationVector(
                pitch, yaw
        ).multiply(mc.player.getBlockInteractionRange()));
        return mc.world.raycast(new RaycastContext(
                eye, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, mc.player
        ));
    }

    private static double lipDistance(final BlockPos block, final Direction travel) {
        return switch (travel) {
            case NORTH -> mc.player.getZ() - block.getZ();
            case SOUTH -> block.getZ() + 1.0D - mc.player.getZ();
            case WEST -> mc.player.getX() - block.getX();
            case EAST -> block.getX() + 1.0D - mc.player.getX();
            default -> Double.POSITIVE_INFINITY;
        };
    }

    public record ActivationSnapshot(
            BlockPos block,
            Direction travel,
            float baseYaw,
            double lane,
            int bridgeLaneBlock,
            double startProgress
    ) {
        public ActivationSnapshot withBaseYaw(final float yaw) {
            return new ActivationSnapshot(
                    this.block, this.travel, yaw, this.lane,
                    this.bridgeLaneBlock, this.startProgress
            );
        }
    }
}

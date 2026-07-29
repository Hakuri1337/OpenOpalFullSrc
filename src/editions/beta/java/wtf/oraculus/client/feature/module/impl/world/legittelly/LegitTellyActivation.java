package wtf.oraculus.client.feature.module.impl.world.legittelly;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import wtf.oraculus.utility.player.RotationUtility;

import static wtf.oraculus.client.Constants.mc;

final class LegitTellyActivation {
    private static final float YAW_TOLERANCE = 2.0F;
    private static final float MIN_PITCH = 75.0F;
    private static final double MAX_LIP_DISTANCE = 0.65D;

    ActivationInspection inspect() {
        if (mc.player == null || mc.world == null) {
            return ActivationInspection.failed(ActivationIssue.WORLD_UNAVAILABLE);
        }

        final float baseYaw = nearestDiagonal(mc.player.getYaw());
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
        final BlockPos belowPlayer = BlockPos.ofFloored(
                mc.player.getX(), mc.player.getY() - 0.5D, mc.player.getZ()
        );
        final double lipDistance = lipDistance(belowPlayer, travel);
        if (lipDistance > MAX_LIP_DISTANCE) {
            return ActivationInspection.failed(
                    ActivationIssue.MOVE_TO_EDGE, lipDistance - MAX_LIP_DISTANCE
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

    enum ActivationIssue {
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

    record ActivationInspection(
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

    static float requiredPitch() {
        return MIN_PITCH;
    }

    static String directionName(final Direction direction) {
        return switch (direction) {
            case NORTH -> "北";
            case SOUTH -> "南";
            case EAST -> "东";
            case WEST -> "西";
            default -> "-";
        };
    }

    static double progress(final BlockPos pos, final Direction direction) {
        return pos.getX() * direction.getOffsetX() + pos.getZ() * direction.getOffsetZ();
    }

    private static float nearestDiagonal(final float yaw) {
        return Math.round((yaw - 45.0F) / 90.0F) * 90.0F + 45.0F;
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

    private static boolean isNarrowHit(
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
        return across >= 0.38D && across <= 0.65D
                && height >= 0.25D && height <= 0.75D;
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

    record ActivationSnapshot(
            BlockPos block,
            Direction travel,
            float baseYaw,
            double lane,
            int bridgeLaneBlock,
            double startProgress
    ) {
        ActivationSnapshot withBaseYaw(final float yaw) {
            return new ActivationSnapshot(
                    this.block, this.travel, yaw, this.lane,
                    this.bridgeLaneBlock, this.startProgress
            );
        }
    }
}

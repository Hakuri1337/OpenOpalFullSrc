package wtf.opal.client.feature.module.impl.world.blockfly.rotation;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import wtf.opal.client.feature.module.impl.world.blockfly.math.BlockFlyMathUtil;

import static wtf.opal.client.Constants.mc;

public final class BlockFlyRotationUtil {
    private BlockFlyRotationUtil() {
    }

    public static BlockFlyRotation smoothRotation(
            final BlockFlyRotation current,
            final BlockFlyRotation target,
            final double speed
    ) {
        float targetYaw = target.yaw();
        float targetPitch = target.pitch();
        final float currentYaw = current.yaw();
        final float currentPitch = current.pitch();

        if (speed != 0.0D) {
            final double yawDifference = MathHelper.wrapDegrees(targetYaw - currentYaw);
            final double pitchDifference = targetPitch - currentPitch;
            final double distance = Math.sqrt(yawDifference * yawDifference + pitchDifference * pitchDifference);
            if (distance > 1.0E-8D) {
                final double yawRatio = Math.abs(yawDifference / distance);
                final double pitchRatio = Math.abs(pitchDifference / distance);
                final double maximumYawStep = speed * yawRatio;
                final double maximumPitchStep = speed * pitchRatio;
                final float yawStep = (float) Math.max(Math.min(yawDifference, maximumYawStep), -maximumYawStep);
                final float pitchStep = (float) Math.max(Math.min(pitchDifference, maximumPitchStep), -maximumPitchStep);
                targetYaw = currentYaw + yawStep;
                targetPitch = currentPitch + pitchStep;
            }
        }

        final boolean addJitter = Math.random() > 0.8D;
        final int passes = (int) (2.0D + Math.random() * 2.0D);
        for (int pass = 1; pass <= passes; pass++) {
            if (addJitter) {
                targetYaw += (float) ((Math.random() - 0.5D) / 1.0E8D);
                targetPitch -= (float) (Math.random() / 2.0E8D);
            }
            final BlockFlyRotation snapped = new BlockFlyRotation(targetYaw, targetPitch)
                    .snapToSensitivity(mc.options.getMouseSensitivity().getValue().floatValue());
            targetYaw = snapped.yaw();
            targetPitch = MathHelper.clamp(snapped.pitch(), -90.0F, 90.0F);
        }
        return new BlockFlyRotation(targetYaw, targetPitch);
    }

    public static float clampAngle(final float angle, final float maximum) {
        if (Math.abs(angle) < maximum) {
            return angle;
        }
        if (angle > 0.0F) {
            return maximum;
        }
        if (angle < 0.0F) {
            return -maximum;
        }
        return 0.0F;
    }

    public static BlockFlyRotation rotationToBlock(final BlockPos blockPos, final float partialTicks) {
        if (mc.player == null) {
            return new BlockFlyRotation();
        }
        final Vec3d velocity = mc.player.getVelocity();
        final Vec3d predictedEye = new Vec3d(
                mc.player.getX() + velocity.x * partialTicks,
                mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()) + velocity.y * partialTicks,
                mc.player.getZ() + velocity.z * partialTicks
        );
        final double deltaX = blockPos.getX() - predictedEye.x + 0.5D;
        final double deltaY = blockPos.getY() - predictedEye.y + 0.5D;
        final double deltaZ = blockPos.getZ() - predictedEye.z + 0.5D;
        return rotationFromDeltas(addNoise(deltaX), addNoise(deltaY), addNoise(deltaZ));
    }

    public static BlockFlyRotation rotationFromVec(final Vec3d target) {
        if (mc.player == null) {
            return new BlockFlyRotation();
        }
        final Vec3d eye = mc.player.getEyePos();
        return rotationFromPoints(target.x, target.y, target.z, eye.x, eye.y, eye.z);
    }

    public static BlockFlyRotation rotationFromPoints(
            final double targetX,
            final double targetY,
            final double targetZ,
            final double sourceX,
            final double sourceY,
            final double sourceZ
    ) {
        final double deltaX = addNoise(targetX - sourceX);
        final double deltaY = addNoise(targetY - sourceY);
        final double deltaZ = addNoise(targetZ - sourceZ);
        final double horizontalDistance = MathHelper.sqrt((float) (deltaX * deltaX + deltaZ * deltaZ));
        final float yaw = (float) (Math.atan2(deltaZ, deltaX) * 180.0D / Math.PI) - 90.0F;
        final float pitch = (float) -(Math.atan2(deltaY, horizontalDistance) * 180.0D / Math.PI);
        return new BlockFlyRotation(yaw, pitch);
    }

    public static BlockFlyRotation rotationFromDeltas(
            final double deltaX,
            final double deltaY,
            final double deltaZ
    ) {
        final double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        final float yaw = (float) Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0F;
        final float pitch = (float) -Math.toDegrees(Math.atan2(deltaY, horizontalDistance));
        return new BlockFlyRotation(MathHelper.wrapDegrees(yaw), MathHelper.wrapDegrees(pitch));
    }

    public static float moveTowards(final float maximumStep, final float current, final float target) {
        float difference = MathHelper.wrapDegrees(target - current);
        if (difference > maximumStep) {
            difference = maximumStep;
        }
        if (difference < -maximumStep) {
            difference = -maximumStep;
        }
        return current + difference;
    }

    public static double angleDifference(final float first, final float second) {
        return normalizeAngle(first - second);
    }

    public static Vec3d directionFromRotation(final BlockFlyRotation rotation) {
        return Vec3d.fromPolar(rotation.pitch(), rotation.yaw());
    }

    private static double addNoise(final double value) {
        return value + BlockFlyMathUtil.randomDouble(0.05D, 0.08D)
                * (BlockFlyMathUtil.randomDouble(0.0D, 1.0D) * 2.0D - 1.0D);
    }

    private static double normalizeAngle(final double angle) {
        return ((angle + 180.0D) % 360.0D + 360.0D) % 360.0D - 180.0D;
    }
}

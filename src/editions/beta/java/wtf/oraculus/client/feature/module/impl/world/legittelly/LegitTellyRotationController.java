package wtf.oraculus.client.feature.module.impl.world.legittelly;

import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import wtf.oraculus.utility.player.RotationUtility;

import static wtf.oraculus.client.Constants.mc;

final class LegitTellyRotationController {
    private static final int[] NUDGE_PATTERN = {0, 1, -1, 2, -2};

    private Vec2f applied;
    private long lastUpdateNanos;
    private float takeoverAccumulation;
    private double laneCoordinate;
    private float antiSwayOffset;
    private boolean antiSwayTapUsed;
    private int nudgeIndex;

    void begin(final double laneCoordinate) {
        this.applied = new Vec2f(mc.player.getYaw(), mc.player.getPitch());
        this.lastUpdateNanos = System.nanoTime();
        this.takeoverAccumulation = 0.0F;
        this.laneCoordinate = laneCoordinate;
        this.antiSwayOffset = 0.0F;
        this.antiSwayTapUsed = false;
        this.nudgeIndex = 0;
    }

    boolean detectManualCamera() {
        if (this.applied == null || mc.player == null) {
            return false;
        }
        final float difference = MathHelper.angleBetween(mc.player.getYaw(), this.applied.x)
                + Math.abs(mc.player.getPitch() - this.applied.y);
        if (difference > 0.7F) {
            this.takeoverAccumulation += difference;
        } else {
            this.takeoverAccumulation = Math.max(0.0F, this.takeoverAccumulation - 1.5F);
        }
        return this.takeoverAccumulation >= 25.0F;
    }

    Vec2f correctForLane(final Vec2f target, final Direction travelDirection, final boolean antiSway) {
        return antiSway
                ? new Vec2f(target.x + this.antiSwayOffset, target.y)
                : target;
    }

    float correctStrafe(
            final float forward,
            final float recordedStrafe,
            final Direction travelDirection,
            final boolean enabled
    ) {
        if (!enabled || mc.player == null) {
            this.antiSwayOffset = 0.0F;
            this.antiSwayTapUsed = false;
            return recordedStrafe;
        }

        final boolean alongX = travelDirection.getAxis() == Direction.Axis.X;
        final double lanePosition = alongX ? mc.player.getZ() : mc.player.getX();
        final double laneVelocity = alongX
                ? mc.player.getVelocity().z
                : mc.player.getVelocity().x;
        final double error = this.laneCoordinate - lanePosition;

        if (Math.abs(error) < 0.015D && Math.abs(laneVelocity) < 0.008D) {
            this.antiSwayTapUsed = false;
            this.antiSwayOffset *= 0.65F;
            if (Math.abs(this.antiSwayOffset) < 0.03F) {
                this.antiSwayOffset = 0.0F;
            }
            return recordedStrafe;
        }

        final double desiredLaneVelocity = MathHelper.clamp(
                error * 0.42D - laneVelocity * 0.78D, -0.16D, 0.16D
        );
        final double velocityCorrection = desiredLaneVelocity - laneVelocity;
        final double radians = Math.toRadians(mc.player.getYaw());
        final double sin = Math.sin(radians);
        final double cos = Math.cos(radians);
        final double yawLaneDerivative = alongX
                ? -forward * sin + recordedStrafe * cos
                : -forward * cos - recordedStrafe * sin;

        double desiredYawOffset = 0.0D;
        if (Math.abs(yawLaneDerivative) >= 0.12D) {
            desiredYawOffset = Math.toDegrees(
                    velocityCorrection * 0.55D / yawLaneDerivative
            );
        }
        desiredYawOffset = MathHelper.clamp(desiredYawOffset, -2.25D, 2.25D);
        this.antiSwayOffset = this.antiSwayOffset * 0.60F
                + (float) desiredYawOffset * 0.40F;

        final double strafeLaneAxis = alongX ? sin : cos;
        final boolean tapHelps = Math.abs(strafeLaneAxis) >= 0.20D
                && velocityCorrection * strafeLaneAxis > 0.0D;
        if (tapHelps && !this.antiSwayTapUsed
                && Math.abs(velocityCorrection) >= 0.03D
                && recordedStrafe < 0.5F) {
            this.antiSwayTapUsed = true;
            return recordedStrafe + 1.0F;
        }
        return recordedStrafe;
    }

    Vec2f apply(final Vec2f target) {
        if (mc.player == null) {
            return target;
        }
        final long now = System.nanoTime();
        final float alpha = MathHelper.clamp((now - this.lastUpdateNanos) / 50_000_000.0F, 0.35F, 1.0F);
        this.lastUpdateNanos = now;

        final Vec2f from = this.applied == null
                ? new Vec2f(mc.player.getYaw(), mc.player.getPitch())
                : this.applied;
        final float yaw = from.x + MathHelper.wrapDegrees(target.x - from.x) * alpha;
        final float pitch = MathHelper.lerp(alpha, from.y, target.y);
        Vec2f quantized = RotationUtility.patchConstantRotation(
                new Vec2f(yaw, MathHelper.clamp(pitch, -89.5F, 89.5F)),
                from
        );

        final double sensitivity = mc.options.getMouseSensitivity().getValue() * 0.6D + 0.2D;
        final float quantum = (float) (sensitivity * sensitivity * sensitivity * 8.0D * 0.15D);
        quantized = new Vec2f(
                quantized.x + quantum * NUDGE_PATTERN[this.nudgeIndex++ % NUDGE_PATTERN.length],
                quantized.y
        );

        this.applied = quantized;
        mc.player.setYaw(quantized.x);
        mc.player.setPitch(quantized.y);
        return quantized;
    }

    Vec2f applied() {
        return this.applied;
    }

    void clear() {
        this.applied = null;
        this.takeoverAccumulation = 0.0F;
        this.antiSwayOffset = 0.0F;
        this.antiSwayTapUsed = false;
    }
}

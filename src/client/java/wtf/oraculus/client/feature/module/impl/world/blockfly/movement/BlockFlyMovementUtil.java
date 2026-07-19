package wtf.oraculus.client.feature.module.impl.world.blockfly.movement;

import net.minecraft.util.math.MathHelper;
import wtf.oraculus.event.impl.game.input.MoveInputEvent;

import static wtf.oraculus.client.Constants.mc;

public final class BlockFlyMovementUtil {
    private BlockFlyMovementUtil() {
    }

    public static boolean isMoving() {
        return mc.options.forwardKey.isPressed()
                || mc.options.backKey.isPressed()
                || mc.options.leftKey.isPressed()
                || mc.options.rightKey.isPressed();
    }

    public static boolean isInputActive() {
        if (mc.player == null || mc.world == null) {
            return false;
        }
        return mc.player.input.getMovementInput().x != 0.0F
                || mc.player.input.getMovementInput().y != 0.0F;
    }

    public static void correctInput(final MoveInputEvent event, final float rotationYaw) {
        if (mc.player == null) {
            return;
        }

        final float forward = event.getForward();
        final float sideways = event.getSideways();
        if (forward == 0.0F && sideways == 0.0F) {
            return;
        }

        final double targetDirection = MathHelper.wrapDegrees(
                Math.toDegrees(directionYaw(mc.player.getYaw(), forward, sideways))
        );
        int bestForward = 0;
        int bestSideways = 0;
        float bestDifference = Float.MAX_VALUE;

        for (int candidateForward = -1; candidateForward <= 1; candidateForward++) {
            for (int candidateSideways = -1; candidateSideways <= 1; candidateSideways++) {
                if (candidateForward == 0 && candidateSideways == 0) {
                    continue;
                }
                final double candidateDirection = MathHelper.wrapDegrees(
                        Math.toDegrees(directionYaw(rotationYaw, candidateForward, candidateSideways))
                );
                final float difference = (float) Math.abs(MathHelper.wrapDegrees(targetDirection - candidateDirection));
                if (difference < bestDifference) {
                    bestDifference = difference;
                    bestForward = candidateForward;
                    bestSideways = candidateSideways;
                }
            }
        }

        event.setForward(bestForward);
        event.setSideways(bestSideways);
    }

    public static double directionYaw(float yaw, final double forward, final double sideways) {
        if (forward < 0.0D) {
            yaw += 180.0F;
        }

        float sidewaysFactor = 1.0F;
        if (forward < 0.0D) {
            sidewaysFactor = -0.5F;
        } else if (forward > 0.0D) {
            sidewaysFactor = 0.5F;
        }

        if (sideways > 0.0D) {
            yaw -= 90.0F * sidewaysFactor;
        }
        if (sideways < 0.0D) {
            yaw += 90.0F * sidewaysFactor;
        }
        return Math.toRadians(yaw);
    }
}

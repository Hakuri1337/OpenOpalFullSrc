package wtf.oraculus.client.feature.module.impl.world.ssngscaffold;

import net.minecraft.util.math.BlockPos;
import wtf.oraculus.event.impl.game.input.MoveInputEvent;

import static wtf.oraculus.client.Constants.mc;

public final class SsngMovementUtil {
    private static int onGroundTicks, offGroundTicks;
    private static boolean cancelMove;

    private SsngMovementUtil() { }

    public static void tick() {
        if (mc.player == null) { reset(); return; }
        if (mc.player.isOnGround()) { onGroundTicks++; offGroundTicks = 0; }
        else { offGroundTicks++; onGroundTicks = 0; }
    }

    public static void apply(final MoveInputEvent event) {
        if (cancelMove) {
            event.setForward(0.0F);
            event.setSideways(0.0F);
            cancelMove = false;
        }
    }

    public static boolean isMoving() {
        return mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed()
                || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed();
    }

    public static boolean isAirBelow(final int down) {
        if (mc.player == null || mc.world == null) return false;
        return SsngClientRayTraceUtil.isIgnoredBlock(mc.world.getBlockState(BlockPos.ofFloored(mc.player.getX(), mc.player.getY() - down, mc.player.getZ())));
    }

    public static void cancelMove() { cancelMove = true; }
    public static void resetMove() { cancelMove = false; }
    public static boolean isCancelMove() { return cancelMove; }
    public static int onGroundTicks() { return onGroundTicks; }
    public static int offGroundTicks() { return offGroundTicks; }
    public static void reset() { onGroundTicks = 0; offGroundTicks = 0; cancelMove = false; }
}

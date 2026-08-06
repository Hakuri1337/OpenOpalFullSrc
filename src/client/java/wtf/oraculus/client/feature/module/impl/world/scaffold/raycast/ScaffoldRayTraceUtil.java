package wtf.oraculus.client.feature.module.impl.world.scaffold.raycast;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import wtf.oraculus.client.feature.module.impl.world.scaffold.rotation.ScaffoldRotation;
import wtf.oraculus.client.feature.module.impl.world.scaffold.rotation.ScaffoldRotationUtil;

import static wtf.oraculus.client.Constants.mc;

public final class ScaffoldRayTraceUtil {
    private ScaffoldRayTraceUtil() {
    }

    public static boolean canRayTrace(
            final ScaffoldRotation rotation,
            final Direction direction,
            final BlockPos supportPos,
            final boolean checkFace
    ) {
        if (rotation == null || mc.player == null || mc.world == null) {
            return false;
        }
        final Vec3d eye = mc.player.getEyePos();
        final Vec3d end = eye.add(ScaffoldRotationUtil.directionFromRotation(rotation).multiply(5.0D));
        final BlockHitResult result = mc.world.raycast(new RaycastContext(
                eye,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));
        return result.getType() == HitResult.Type.BLOCK
                && result.getBlockPos().equals(supportPos)
                && (!checkFace || result.getSide() == direction);
    }

    public static BlockHitResult rayTrace(final double range, final ScaffoldRotation rotation) {
        if (rotation == null || mc.player == null || mc.world == null) {
            return null;
        }
        final Vec3d eye = mc.player.getEyePos();
        final Vec3d end = eye.add(ScaffoldRotationUtil.directionFromRotation(rotation).multiply(range));
        return mc.world.raycast(new RaycastContext(
                eye,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.ANY,
                mc.player
        ));
    }
}

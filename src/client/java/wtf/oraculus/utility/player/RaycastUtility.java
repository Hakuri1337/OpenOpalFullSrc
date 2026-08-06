package wtf.oraculus.utility.player;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.function.Predicate;

import static wtf.oraculus.client.Constants.mc;

public final class RaycastUtility {

    private RaycastUtility() {
    }

    public static HitResult raycastBlock(final double maxDistance, final float tickDelta, final boolean includeFluids, final float yaw, final float pitch) {
        final Vec3d start = RaycastUtility.getCameraPosVec(tickDelta, mc.player);
        return raycastBlock(maxDistance, includeFluids, yaw, pitch, start);
    }

    public static HitResult raycastBlock(final double maxDistance, final boolean includeFluids, final float yaw, final float pitch, final Vec3d start) {
        final Vec3d rotationVector = RotationUtility.getRotationVector(pitch, yaw);

        final Vec3d end = start.add(rotationVector.x * maxDistance, rotationVector.y * maxDistance, rotationVector.z * maxDistance);

        return mc.world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.OUTLINE, includeFluids ? RaycastContext.FluidHandling.ANY : RaycastContext.FluidHandling.NONE, mc.player));
    }

    public static EntityHitResult raycastEntity(final double maxDistance, final float tickDelta, final float yaw, final float pitch, final Predicate<Entity> predicate) {
        return raycastEntity(maxDistance, RaycastUtility.getCameraPosVec(tickDelta, mc.player), yaw, pitch, predicate);
    }

    public static EntityHitResult raycastEntity(final double maxDistance, final Vec3d start, final float yaw, final float pitch, final Predicate<Entity> predicate) {
        final Vec3d rotationVector = RotationUtility.getRotationVector(pitch, yaw);

        final Vec3d end = start.add(rotationVector.x * maxDistance, rotationVector.y * maxDistance, rotationVector.z * maxDistance);

        final Box box = mc.player.getBoundingBox().stretch(rotationVector.multiply(maxDistance)).expand(1, 1, 1);

        return ProjectileUtil.raycast(mc.player, start, end, box, predicate, MathHelper.square(maxDistance));
    }

    public static Vec3d getCameraPosVec(final float tickDelta, final Entity entity) {
        final double x = MathHelper.lerp(tickDelta, entity.lastX, entity.getX());
        final double y = MathHelper.lerp(tickDelta, entity.lastY, entity.getY()) + (double) entity.getStandingEyeHeight();
        final double z = MathHelper.lerp(tickDelta, entity.lastZ, entity.getZ());
        return new Vec3d(x, y, z);
    }
}

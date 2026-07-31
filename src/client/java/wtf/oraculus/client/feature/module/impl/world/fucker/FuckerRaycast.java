package wtf.oraculus.client.feature.module.impl.world.fucker;

import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import java.util.ArrayList;
import java.util.List;
import static wtf.oraculus.client.Constants.mc;
import wtf.oraculus.utility.player.RotationUtility;

final class FuckerRaycast {
    private FuckerRaycast() { }
    static BlockHitResult trace(final Vec3d from, final Vec3d to) {
        final HitResult hit = mc.world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
        return hit instanceof BlockHitResult blockHit ? blockHit : null;
    }
    static BlockHitResult raycast(final float yaw, final float pitch, final double range) {
        final Vec3d eyes = mc.player.getEyePos();
        final HitResult hit = mc.world.raycast(new RaycastContext(eyes, eyes.add(RotationUtility.getRotationVector(pitch, yaw).multiply(range)), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
        return hit instanceof BlockHitResult blockHit ? blockHit : null;
    }
    static List<Vec3d> sampleOutline(final BlockPos pos, final BlockState state, final boolean fullBlock) {
        final List<Vec3d> samples = new ArrayList<>();
        final List<Box> boxes = fullBlock ? List.of(new Box(0, 0, 0, 1, 1, 1)) : state.getOutlineShape(mc.world, pos).getBoundingBoxes();
        for (final Box local : boxes) {
            final Box box = local.offset(pos);
            for (double a : new double[]{0.1D, 0.3D, 0.5D, 0.7D, 0.9D}) for (double b : new double[]{0.1D, 0.3D, 0.5D, 0.7D, 0.9D}) {
                samples.add(new Vec3d(box.minX + (box.maxX - box.minX) * a, box.minY + (box.maxY - box.minY) * b, box.minZ));
                samples.add(new Vec3d(box.minX + (box.maxX - box.minX) * a, box.minY + (box.maxY - box.minY) * b, box.maxZ));
                samples.add(new Vec3d(box.minX, box.minY + (box.maxY - box.minY) * a, box.minZ + (box.maxZ - box.minZ) * b));
                samples.add(new Vec3d(box.maxX, box.minY + (box.maxY - box.minY) * a, box.minZ + (box.maxZ - box.minZ) * b));
                samples.add(new Vec3d(box.minX + (box.maxX - box.minX) * a, box.minY, box.minZ + (box.maxZ - box.minZ) * b));
                samples.add(new Vec3d(box.minX + (box.maxX - box.minX) * a, box.maxY, box.minZ + (box.maxZ - box.minZ) * b));
            }
        }
        return samples;
    }
}

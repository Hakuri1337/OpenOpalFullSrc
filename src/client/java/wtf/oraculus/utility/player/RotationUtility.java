package wtf.oraculus.utility.player;

import com.google.common.base.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import org.jetbrains.annotations.Nullable;
import wtf.oraculus.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.oraculus.utility.misc.math.RandomUtility;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static wtf.oraculus.client.Constants.mc;

public final class RotationUtility {

    private RotationUtility() {
    }

    public static float getRotationDifference(Vec2f a, Vec2f b) {
        return MathHelper.angleBetween(a.x, b.x) + Math.abs(a.y - b.y);
    }

    public static double getCursorDelta(double rotationDelta, double sensitivityMultiplier) {
        return (float) (rotationDelta / sensitivityMultiplier) / 0.15F;
    }

    public static Vec2f patchConstantRotation(final Vec2f rotation, final Vec2f prevRotation) {
        final double sensitivity = mc.options.getMouseSensitivity().getValue() * 0.6F + 0.2F;
        final double multiplier = (sensitivity * sensitivity * sensitivity) * 8.0D;
        final double divisor = multiplier * 0.15F;

        final float yawDelta = rotation.x - prevRotation.x;
        final float pitchDelta = rotation.y - prevRotation.y;
        final float yaw = prevRotation.x + (float) (Math.round(yawDelta / divisor) * divisor);
        final float pitch = prevRotation.y + (float) (Math.round(pitchDelta / divisor) * divisor);
        return new Vec2f(yaw, pitch);
    }

    public static float getSensitivityModifiedRotation(double original) {
        final double sensitivity = mc.options.getMouseSensitivity().getValue() * 0.6F + 0.2F;
        final double multiplier = (sensitivity * sensitivity * sensitivity) * 8.0D;
        return (float) (getCursorDelta(original, multiplier) * multiplier) * 0.15F;
    }

    public static Vec2f getSentRotation(final Vec2f original) {
        return getSensitivityModifiedRotation(patchConstantRotation(original, getRotation()));
    }

    public static Vec2f getSensitivityModifiedRotation(Vec2f original) {
        return new Vec2f(getSensitivityModifiedRotation(original.x), getSensitivityModifiedRotation(original.y));
    }

    public static Vec2f getVanillaRotation(Vec2f original) {
        final Vec2f sentRotation = getSentRotation(original);
        final float wrappedYaw = getDuplicateWrapped(sentRotation.x, mc.player.getYaw());
        return new Vec2f(wrappedYaw, sentRotation.y);
    }

    public static float getDuplicateWrapped(float value, float target) { // makes value in the same 360 range as target, e.g. value = 740 target = 0 it will return 20
        return target + MathHelper.wrapDegrees(value - target);
    }
    
    public static Vec2f getRotation() {
        return new Vec2f(mc.player.getYaw(), mc.player.getPitch());
    }

    public static Vec2f getPriorityAngle(final Vec2f currentRotation, final float steps, final boolean snap, final boolean diagonal) {
        // making the player walk towards the center of the block in the closest yaw rounding
        final float targetYaw;
        if (snap) {
            final float rounding = 45.0F / steps;
            final float roundedMoveDir = Math.round(MoveUtility.getDirectionDegrees() / rounding) * rounding;

            final float yawRad = (float) Math.toRadians(roundedMoveDir);
            final float offset = 10.0F;
            final float dirX = -MathHelper.sin(yawRad) * offset;
            final float dirZ = MathHelper.cos(yawRad) * offset;

            final Vec3d playerPos = mc.player.getEntityPos();
            final double targetBlockCenterX = Math.floor(playerPos.x) + dirX + 0.5D;
            final double targetBlockCenterZ = Math.floor(playerPos.z) + dirZ + 0.5D;

            final double deltaX = targetBlockCenterX - playerPos.x;
            final double deltaZ = targetBlockCenterZ - playerPos.z;

            targetYaw = (float) Math.toDegrees(Math.atan2(-deltaX, deltaZ));
        } else {
            targetYaw = MoveUtility.getDirectionDegrees();
        }
        final float endYaw = targetYaw + RandomUtility.getRandomFloat(-0.01F, 0.01F);

        final List<Float> yaws = new ArrayList<>(4);
        if (!diagonal) {
            yaws.add(endYaw);
            yaws.add(endYaw + 180);
        }
        for (int f = 45; f < 180; f += 90) {
            yaws.add(endYaw + f);
            yaws.add(endYaw - f);
        }
        yaws.sort(Comparator.comparingDouble((y) -> MathHelper.angleBetween(y, currentRotation.x)));
        return new Vec2f(yaws.getFirst(), currentRotation.y);
    }

    @Nullable
    public static RaytracedRotation getRotationFromRaycastedBlock(final BlockPos blockPos, final Direction side, final Vec2f priorityRotations, final Vec3d playerPos) {
//        {
//            final Vec2f currentRotations = getRotation();
//            final HitResult hitResult = RaycastUtility.raycastBlock(mc.player.getBlockInteractionRange(), false, currentRotations.x, currentRotations.y, playerPos);
//            if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
//                final BlockHitResult blockHitResult = (BlockHitResult) hitResult;
//                final BlockPos hitResultPos = blockHitResult.getBlockPos();
//                final Direction hitResultSide = blockHitResult.getSide();
//
//                if (hitResultPos.equals(blockPos) && hitResultSide == side) {
//                    return new RaytracedRotation(currentRotations, hitResult);
//                }
//            }
//        }

        final Box box = new Box(blockPos);

        final Vec3d facedVector = box.getCenter();

        final double widthX = box.getLengthX();
        final double height = box.getLengthY();
        final double widthZ = box.getLengthZ();

        final List<RaytracedRotation> rotations = new ArrayList<>();

        final float step = 12.F;
        for (double vx = widthX, x = -vx; x < vx; x += vx / step) {
            for (double vy = height, y = -vy; y < vy; y += vy / step) {
                for (double vz = widthZ, z = -vz; z < vz; z += vz / step) {
                    final Vec3d offsetVector = new Vec3d(x, y, z);
                    final Vec3d raytraceVector = facedVector.add(offsetVector);

                    final Vec2f raytraceRotation = getVanillaRotation(getRotationFromPosition(raytraceVector));

                    final HitResult hitResult = RaycastUtility.raycastBlock(mc.player.getBlockInteractionRange(), false, raytraceRotation.x, raytraceRotation.y, playerPos);

                    if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
                        final BlockHitResult blockHitResult = (BlockHitResult) hitResult;
                        final BlockPos hitResultPos = blockHitResult.getBlockPos();
                        final Direction hitResultSide = blockHitResult.getSide();

                        if (hitResultPos.equals(blockPos) && hitResultSide == side) {
                            rotations.add(new RaytracedRotation(raytraceRotation, hitResult));
                        }
                    }
                }
            }
        }

        if (rotations.isEmpty()) {
            return null;
        }

        rotations.sort(Comparator.comparingDouble(r -> getRotationDifference(r.rotation(), priorityRotations)));

        return rotations.getFirst();
    }

    @Nullable
    public static RaytracedRotation getRotationFromRaycastedEntity(final LivingEntity entity, final Vec3d closestVector, final double entityInteractionRange) {
        final Predicate<Entity> targetPredicate = e -> e == entity; // to ignore other entities in the raytrace

//        {
//            final Vec2f currentRotations = getRotation();
//            final HitResult hitResult = RaycastUtility.raycastEntity(entityInteractionRange, 1F, currentRotations.x, currentRotations.y, targetPredicate);
//            if (hitResult != null) {
//                return new RaytracedRotation(currentRotations, hitResult);
//            }
//        }

        final Box box = entity.getBoundingBox().expand(entity.getTargetingMargin());
        final Vec3d facedVector = box.getCenter();
        double widthX = box.getLengthX();
        double height = box.getLengthY();
        double widthZ = box.getLengthZ();

        final List<RaytracedRotation> rotations = new ArrayList<>();

        final Vec2f rotationFromPosition = RotationUtility.getRotationFromPosition(closestVector);
        final float range = (float) RandomUtility.getJoinRandomDouble(0.01D, 0.05D);
        final Vec2f randomAddition = new Vec2f(
                RandomUtility.getRandomFloat(-range, range),
                RandomUtility.getRandomFloat(-range, range)
        );
        final Vec2f randomClosestRotation = rotationFromPosition.add(randomAddition);
        final Vec2f closestVectorRotation = getVanillaRotation(randomClosestRotation);
        final HitResult closestHitResult = RaycastUtility.raycastEntity(entityInteractionRange, 1F, closestVectorRotation.x, closestVectorRotation.y, targetPredicate);
        if (closestHitResult != null) {
            return new RaytracedRotation(closestVectorRotation, closestHitResult);
        }

        final float step = 8.F - (RandomUtility.RANDOM.nextFloat() * 0.25F);
        for (double vx = widthX, x = -vx; x < vx; x += vx / step) {
            for (double vy = height, y = -vy; y < vy; y += vy / step) {
                for (double vz = widthZ, z = -vz; z < vz; z += vz / step) {
                    final Vec3d offsetVector = new Vec3d(x, y, z);
                    final Vec3d raytraceVector = facedVector.add(offsetVector);

                    final Vec2f raytraceRotation = getVanillaRotation(RotationUtility.getRotationFromPosition(raytraceVector));

                    final HitResult hitResult = RaycastUtility.raycastEntity(entityInteractionRange, 1F, raytraceRotation.x, raytraceRotation.y, targetPredicate);

                    if (hitResult != null) {
                        rotations.add(new RaytracedRotation(raytraceRotation, hitResult));
                    }
                }
            }
        }

        if (rotations.isEmpty()) {
            return null;
        }

        rotations.sort(Comparator.comparingDouble(r -> RotationUtility.getRotationDifference(r.rotation(), closestVectorRotation)));

        return rotations.getFirst();
    }

    public static Vec2f getRotationFromBlock(final BlockPos blockPos, final Direction direction) {
        final float xDiff = (float) (blockPos.getX() + 0.5 - mc.player.getX() + direction.getOffsetX() * 0.5);
        final float yDiff = (float) (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()) - blockPos.getY() - direction.getOffsetY() * 0.5);
        final float zDiff = (float) (blockPos.getZ() + 0.5 - mc.player.getZ() + direction.getOffsetZ() * 0.5);

        final double distance = MathHelper.sqrt(xDiff * xDiff + zDiff * zDiff);

        final float yaw = (float) Math.toDegrees(-Math.atan2(xDiff, zDiff));
        final float pitch = (float) Math.toDegrees(Math.atan(yDiff / distance));

        return new Vec2f(yaw, pitch);
    }

    public static Vec2f getRotationFromPosition(final Vec3d pos) {
        return getRotationFromPosition(mc.player.getEyePos(), pos);
    }

    public static Vec2f getRotationFromPosition(final Vec3d from, final Vec3d to) {
        final double xDiff = to.getX() - from.getX();
        final double yDiff = to.getY() - from.getY();
        final double zDiff = to.getZ() - from.getZ();

        final double distance = Math.sqrt(xDiff * xDiff + zDiff * zDiff);

        final float yaw = (float) Math.toDegrees(-Math.atan2(xDiff, zDiff));
        final float pitch = (float) -Math.toDegrees(Math.atan2(yDiff, distance));

        return new Vec2f(yaw, pitch);
    }

    public static Vec3d getRotationVector(float pitch, float yaw) {
        float f = pitch * (float) (Math.PI / 180.0);
        float g = -yaw * (float) (Math.PI / 180.0);
        float h = MathHelper.cos(g);
        float i = MathHelper.sin(g);
        float j = MathHelper.cos(f);
        float k = MathHelper.sin(f);
        return new Vec3d(i * j, -k, h * j);
    }

    public static double getEntityFOV(final Entity entity) {
        final double yawDiff = (RotationHelper.getClientHandler().getYawOr(mc.player.getYaw()) - getRotationFromPosition(entity.getEntityPos()).x) % 360.0 + 540.0;
        return yawDiff % 360.0 - 180.0;
    }

    public static boolean isEntityInFOV(final Entity entity, final float fov) {
        if (fov >= 180.F) {
            return true;
        }
        final double angle = getEntityFOV(entity);
        return Math.abs(angle) < fov;
    }
}

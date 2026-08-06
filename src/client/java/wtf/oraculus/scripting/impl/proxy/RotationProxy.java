package wtf.oraculus.scripting.impl.proxy;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import wtf.oraculus.utility.player.RaytracedRotation;
import wtf.oraculus.utility.player.RotationUtility;

public class
RotationProxy {

    public float getRotationDifference(Vec2f a, Vec2f b) {
        return RotationUtility.getRotationDifference(a, b);
    }

    public double getCursorDelta(double rotationDelta, double sensitivityMultiplier) {
        return RotationUtility.getCursorDelta(rotationDelta, sensitivityMultiplier);
    }

    public Vec2f patchConstantRotation(Vec2f rotation, Vec2f prevRotation) {
        return RotationUtility.patchConstantRotation(rotation, prevRotation);
    }

    public float getSensitivityModifiedRotation(double original) {
        return RotationUtility.getSensitivityModifiedRotation(original);
    }

    public Vec2f getSentRotation(Vec2f original) {
        return RotationUtility.getSentRotation(original);
    }

    public Vec2f getSensitivityModifiedRotation(Vec2f original) {
        return RotationUtility.getSensitivityModifiedRotation(original);
    }

    public Vec2f getVanillaRotation(Vec2f original) {
        return RotationUtility.getVanillaRotation(original);
    }

    public float getDuplicateWrapped(float value, float target) {
        return RotationUtility.getDuplicateWrapped(value, target);
    }

    public Vec2f getRotation() {
        return RotationUtility.getRotation();
    }

    public RaytracedRotation getRotationFromRaycastedBlock(BlockPos blockPos, Direction side, Vec2f priorityRotations, Vec3d playerPos) {
        return RotationUtility.getRotationFromRaycastedBlock(blockPos, side, priorityRotations, playerPos);
    }

    public RaytracedRotation getRotationFromRaycastedEntity(net.minecraft.entity.LivingEntity entity, Vec3d closestVector, double entityInteractionRange) {
        return RotationUtility.getRotationFromRaycastedEntity(entity, closestVector, entityInteractionRange);
    }

    public Vec2f getRotationFromBlock(BlockPos blockPos, Direction direction) {
        return RotationUtility.getRotationFromBlock(blockPos, direction);
    }

    public Vec2f getRotationFromPosition(Vec3d pos) {
        return RotationUtility.getRotationFromPosition(pos);
    }

    public Vec3d getRotationVector(float pitch, float yaw) {
        return RotationUtility.getRotationVector(pitch, yaw);
    }

    public double getEntityFOV(net.minecraft.entity.Entity entity) {
        return RotationUtility.getEntityFOV(entity);
    }

    public boolean isEntityInFOV(net.minecraft.entity.Entity entity, float fov) {
        return RotationUtility.isEntityInFOV(entity, fov);
    }

}

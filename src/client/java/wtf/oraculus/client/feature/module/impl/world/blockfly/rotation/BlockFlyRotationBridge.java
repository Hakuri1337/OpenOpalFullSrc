package wtf.oraculus.client.feature.module.impl.world.blockfly.rotation;

public final class BlockFlyRotationBridge {
    private BlockFlyRotationBridge() {
    }

    public static boolean ownsRotation() {
        return BlockFlyRotationHandler.isOwningRotation();
    }

    public static float logicalYawOr(final float fallback) {
        final BlockFlyRotation target = BlockFlyRotationHandler.targetRotation();
        return target == null ? fallback : target.yaw();
    }

    public static float logicalPitchOr(final float fallback) {
        final BlockFlyRotation target = BlockFlyRotationHandler.targetRotation();
        return target == null ? fallback : target.pitch();
    }
}

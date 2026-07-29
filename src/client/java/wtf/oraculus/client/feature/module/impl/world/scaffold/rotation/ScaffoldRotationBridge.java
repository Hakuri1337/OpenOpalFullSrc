package wtf.oraculus.client.feature.module.impl.world.scaffold.rotation;

public final class ScaffoldRotationBridge {
    private ScaffoldRotationBridge() {
    }

    public static boolean ownsRotation() {
        return ScaffoldRotationHandler.isOwningRotation();
    }

    public static float logicalYawOr(final float fallback) {
        final ScaffoldRotation target = ScaffoldRotationHandler.targetRotation();
        return target == null ? fallback : target.yaw();
    }

    public static float logicalPitchOr(final float fallback) {
        final ScaffoldRotation target = ScaffoldRotationHandler.targetRotation();
        return target == null ? fallback : target.pitch();
    }
}

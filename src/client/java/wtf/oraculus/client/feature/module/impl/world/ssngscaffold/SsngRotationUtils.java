package wtf.oraculus.client.feature.module.impl.world.ssngscaffold;

import net.minecraft.util.math.*;
import wtf.oraculus.event.impl.game.input.MoveInputEvent;
import wtf.oraculus.event.impl.game.player.movement.PreMovementPacketEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static wtf.oraculus.client.Constants.mc;

/** SSNG-local rotation coordinator and closest-face solver. */
public final class SsngRotationUtils {
    private static SsngRotation rotation;
    private static SsngRotation serverRotation;
    private static SsngRotation lastRotation;
    private static float lastPitchDifference;

    private SsngRotationUtils() { }

    public static void reset() {
        if (mc.player != null) {
            serverRotation = new SsngRotation(mc.player.getYaw(), mc.player.getPitch());
            lastRotation = serverRotation.copy();
        } else {
            serverRotation = null;
            lastRotation = null;
        }
        rotation = null;
        lastPitchDifference = 0.0F;
    }

    public static void setRotation(final SsngRotation value) {
        if (value == null) {
            rotation = null;
            return;
        }

        final SsngRotation adjusted = value.copy();
        final float serverYaw = getServerRotation().yaw();
        // Keep the packet yaw continuous around the -180/180 boundary. Sending
        // the normalized equivalent directly would appear as a near-360 degree
        // turn to the server.
        adjusted.setYaw(serverYaw + MathHelper.wrapDegrees(adjusted.yaw() - serverYaw));
        rotation = adjusted;
    }

    public static SsngRotation getRotation() { return rotation == null ? null : rotation.copy(); }
    public static boolean hasRotation() { return rotation != null; }
    public static float getMovementYawOr(final float fallback) { return rotation == null ? fallback : rotation.yaw(); }
    public static SsngRotation getServerRotation() {
        if (serverRotation != null) return serverRotation.copy();
        return mc.player == null ? new SsngRotation() : new SsngRotation(mc.player.getYaw(), mc.player.getPitch());
    }
    public static SsngRotation getLastRotation() { return lastRotation == null ? getServerRotation() : lastRotation.copy(); }
    public static float getLastPitchDifference() { return lastPitchDifference; }

    public static void apply(final PreMovementPacketEvent event) {
        if (rotation == null) return;
        event.setYaw(rotation.yaw());
        event.setPitch(rotation.pitch());
        lastPitchDifference = serverRotation == null ? 0.0F : Math.abs(rotation.pitch() - serverRotation.pitch());
        lastRotation = serverRotation == null ? rotation.copy() : serverRotation.copy();
        serverRotation = rotation.copy();
    }

    public static void correctInput(final MoveInputEvent event) {
        if (rotation == null || mc.player == null) return;
        final float forward = event.getForward(), sideways = event.getSideways();
        if (forward == 0.0F && sideways == 0.0F) return;
        final double target = direction(mc.player.getYaw(), forward, sideways);
        int bestForward = 0, bestSideways = 0;
        double bestDifference = Double.MAX_VALUE;
        for (int f = -1; f <= 1; f++) {
            for (int s = -1; s <= 1; s++) {
                if (f == 0 && s == 0) continue;
                final double difference = Math.abs(MathHelper.wrapDegrees((float) Math.toDegrees(target - direction(rotation.yaw(), f, s))));
                if (difference < bestDifference) { bestDifference = difference; bestForward = f; bestSideways = s; }
            }
        }
        event.setForward(bestForward); event.setSideways(bestSideways);
    }

    private static double direction(float yaw, final double forward, final double sideways) {
        if (forward < 0.0D) yaw += 180.0F;
        float factor = 1.0F;
        if (forward < 0.0D) factor = -0.5F; else if (forward > 0.0D) factor = 0.5F;
        if (sideways > 0.0D) yaw -= 90.0F * factor;
        if (sideways < 0.0D) yaw += 90.0F * factor;
        return Math.toRadians(yaw);
    }

    public static SsngRotation getClosestToBlockFace(final BlockPos pos, final Direction face,
                                                     final float referenceYaw, final float referencePitch) {
        if (mc.player == null || pos == null || face == null) return null;
        SsngClientRayTraceUtil.updateEyePos();
        final Vec3d center = pos.toCenterPos().add(Vec3d.of(face.getVector()).multiply(0.5D));
        final List<SsngRotation> candidates = new ArrayList<>();
        for (double a = -0.4D; a <= 0.4001D; a += 0.1D) {
            for (double b = -0.4D; b <= 0.4001D; b += 0.1D) {
                final Vec3d point = switch (face.getAxis()) {
                    case X -> center.add(0.0D, a, b);
                    case Y -> center.add(a, 0.0D, b);
                    case Z -> center.add(a, b, 0.0D);
                };
                final SsngRotation candidate = rotationTo(point);
                if (SsngClientRayTraceUtil.didHitBlockFace(candidate, pos, face, true)) candidates.add(candidate);
            }
        }
        if (candidates.isEmpty()) return rotationTo(center);
        candidates.sort(Comparator.comparingDouble(r -> rotationDifference(r, referenceYaw, referencePitch)));
        return candidates.getFirst();
    }

    public static SsngRotation rotationTo(final Vec3d target) {
        final Vec3d eye = mc.player.getEyePos();
        final double x = target.x - eye.x, y = target.y - eye.y, z = target.z - eye.z;
        final double horizontal = Math.sqrt(x * x + z * z);
        return new SsngRotation((float) Math.toDegrees(Math.atan2(z, x)) - 90.0F,
                (float) -Math.toDegrees(Math.atan2(y, horizontal)));
    }

    private static double rotationDifference(final SsngRotation r, final float yaw, final float pitch) {
        final double dy = MathHelper.wrapDegrees(r.yaw() - yaw), dp = r.pitch() - pitch;
        return dy * dy + dp * dp;
    }

    public static float yawDiff(final float target, final float current) { return MathHelper.wrapDegrees(target - current); }
    public static float smooth(final float difference, final float maximum) { return MathHelper.clamp(difference, -maximum, maximum); }
}

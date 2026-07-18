package wtf.opal.utility.player;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import wtf.opal.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.opal.client.feature.helper.impl.player.rotation.model.IRotationModel;

/**
 * Provides Amadeus-compatible logical rotations. ON_TICK leaves the camera
 * alone and changes only the player's queried look vector for the current tick.
 */
public final class RotationInjector {

    private static final long RETURN_DURATION_MS = 200L;

    private static Vec2f rotation;
    private static boolean returning;
    private static Vec2f returnFrom;
    private static Vec2f returnTo;
    private static long returnStartTime;

    private RotationInjector() {
    }

    public static void startReturn(final Vec2f currentPlayerRotation) {
        if ((rotation == null && !returning) || currentPlayerRotation == null) {
            return;
        }

        final Vec2f from = rotation != null ? rotation : returnTo;
        if (from == null) {
            clear();
            return;
        }

        returnFrom = new Vec2f(from.x, from.y);
        returnTo = new Vec2f(currentPlayerRotation.x, currentPlayerRotation.y);
        returnStartTime = System.currentTimeMillis();
        rotation = null;
        returning = true;
    }

    public static void applyRotation(final Vec2f target, final RotationMode mode, final IRotationModel model) {
        if (target == null) {
            return;
        }

        if (mode == RotationMode.ON_TICK) {
            setRotation(target);
            return;
        }

        RotationHelper.getHandler().rotate(target, model);
    }

    public static void setRotation(final Vec2f rotation) {
        RotationInjector.rotation = new Vec2f(rotation.x, MathHelper.clamp(rotation.y, -90.0F, 90.0F));
        returning = false;
        returnFrom = null;
        returnTo = null;
    }

    public static Vec2f getRotation() {
        if (!returning) {
            return rotation;
        }

        final float progress = Math.min(1.0F, (System.currentTimeMillis() - returnStartTime) / (float) RETURN_DURATION_MS);
        final Vec2f current = new Vec2f(
                returnFrom.x + (returnTo.x - returnFrom.x) * progress,
                returnFrom.y + (returnTo.y - returnFrom.y) * progress
        );

        if (progress >= 1.0F) {
            clear();
            return null;
        }

        return current;
    }

    public static void clear() {
        rotation = null;
        returning = false;
        returnFrom = null;
        returnTo = null;
        returnStartTime = 0L;
    }

    public static boolean isActive() {
        return rotation != null || returning;
    }

    public enum RotationMode {
        NORMAL("Normal"),
        ON_TICK("OnTick");

        private final String name;

        RotationMode(final String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }
}

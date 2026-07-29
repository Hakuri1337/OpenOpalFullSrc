package wtf.oraculus.client.feature.module.impl.world.legittelly;

/**
 * Tick-exact movement and camera recording extracted from the reference macro.
 * Values are deliberately immutable: changing their length or phase alignment
 * changes the jump arc and is therefore treated as a profile change.
 */
final class LegitTellyProfile {
    static final int SETUP_TICKS = 12;
    static final int FIRST_RUNNING_PHASE = 19;

    private static final float[] YAW = {
            91.68F, 98.88F, 78.94F, 37.45F, 1.61F, -21.69F, -33.98F,
            -35.80F, -34.64F, -33.85F, -33.06F, -31.55F, -29.26F,
            -26.65F, -24.19F, -21.07F, -18.84F, -17.06F, -8.87F,
            2.61F, 41.94F
    };

    private static final float[] PITCH = {
            64.31F, 59.95F, 60.57F, 61.46F, 60.64F, 58.89F, 56.91F,
            56.63F, 58.65F, 61.63F, 64.20F, 66.74F, 68.69F, 70.64F,
            73.01F, 75.37F, 77.46F, 78.56F, 78.90F, 77.22F, 72.25F
    };

    private LegitTellyProfile() {
    }

    static int length() {
        return YAW.length;
    }

    static float yaw(final int phase) {
        return YAW[clampPhase(phase)];
    }

    static float pitch(final int phase) {
        return PITCH[clampPhase(phase)];
    }

    static float forward(final int phase) {
        if (phase <= 1) {
            return 1.0F;
        }
        if (phase <= 3) {
            return 0.0F;
        }
        if (phase <= 19) {
            return -1.0F;
        }
        return 1.0F;
    }

    static float sideways(final int phase) {
        return phase <= 3 || phase >= 17 ? -1.0F : 0.0F;
    }

    static boolean jump(final int phase) {
        return phase >= 1 && phase <= 19;
    }

    static boolean sprint(final int phase) {
        return phase <= 1;
    }

    static boolean useWindow(final int phase) {
        return phase >= 7;
    }

    private static int clampPhase(final int phase) {
        return Math.max(0, Math.min(YAW.length - 1, phase));
    }
}

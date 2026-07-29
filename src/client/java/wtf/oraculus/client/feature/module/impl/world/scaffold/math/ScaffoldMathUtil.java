package wtf.oraculus.client.feature.module.impl.world.scaffold.math;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public final class ScaffoldMathUtil {
    private static final Random RANDOM = new Random();

    private ScaffoldMathUtil() {
    }

    public static double randomDouble(final double min, final double max) {
        return min >= max ? min : RANDOM.nextDouble() * (max - min) + min;
    }

    // OpenZen names these arguments in the opposite order used by its call sites.
    public static float randomFloat(final float max, final float min) {
        return ThreadLocalRandom.current().nextFloat() * (max - min) + min;
    }
}

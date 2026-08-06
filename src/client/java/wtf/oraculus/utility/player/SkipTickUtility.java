package wtf.oraculus.utility.player;

public final class SkipTickUtility {

    private static int skipTicks;

    private SkipTickUtility() {
    }

    public static void addSkipTicks(final int ticks) {
        if (ticks <= 0) {
            return;
        }
        skipTicks += ticks;
    }

    public static boolean consumeSkipTick() {
        if (skipTicks > 0) {
            skipTicks--;
            return true;
        }
        return false;
    }

    public static void reset() {
        skipTicks = 0;
    }
}

package wtf.oraculus.client.feature.helper.impl.player.swing;

import wtf.oraculus.client.feature.helper.IHelper;
import wtf.oraculus.event.EventDispatcher;
import wtf.oraculus.utility.misc.time.Stopwatch;

import static wtf.oraculus.client.Constants.mc;

public final class SwingDelay implements IHelper {

    private final Stopwatch swingStopwatch = new Stopwatch();

    public static void reset() {
        instance.swingStopwatch.reset();
    }

    private static SwingDelay instance;

    public static void setInstance() {
        instance = new SwingDelay();
        EventDispatcher.subscribe(instance);
    }

    public static boolean isSwingAvailable(final CPSProperty cpsProperty, final boolean reset) {
        if (cpsProperty.isModernDelay() && mc.player != null) {
            return mc.player.getAttackCooldownProgress(0.5F) >= 1.0F;
        }
        if (instance.swingStopwatch.hasTimeElapsed(cpsProperty.getNextClick())) {
            if (reset) {
                cpsProperty.resetClick();
                reset();
            }
            return true;
        }
        return false;
    }

    public static boolean isSwingAvailable(final CPSProperty cpsProperty) {
        return isSwingAvailable(cpsProperty, true);
    }
}

package wtf.oraculus.client.feature.helper.impl.player.timer;

public final class TimerHelper {

    public float timer = 1F;

    private TimerHelper() {
    }

    private static TimerHelper instance;

    public static TimerHelper getInstance() {
        return instance;
    }

    public static void setInstance() {
        instance = new TimerHelper();
    }

}

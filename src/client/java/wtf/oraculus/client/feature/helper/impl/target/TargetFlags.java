package wtf.oraculus.client.feature.helper.impl.target;

public final class TargetFlags {

    public static final int PLAYERS = 1;
    public static final int HOSTILE = 1 << 1;
    public static final int PASSIVE = 1 << 2;
    public static final int FRIENDLY = 1 << 3;
    public static final int LOCAL = 1 << 4;

    public static int get(final boolean players, final boolean hostile, final boolean passive, final boolean friendly) {
        int flags = 0;
        if (players) flags |= PLAYERS;
        if (hostile) flags |= HOSTILE;
        if (passive) flags |= PASSIVE;
        if (friendly) flags |= FRIENDLY;
        return flags;
    }

}

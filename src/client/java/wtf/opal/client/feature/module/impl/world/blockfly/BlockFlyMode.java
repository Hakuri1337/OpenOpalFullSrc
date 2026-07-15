package wtf.opal.client.feature.module.impl.world.blockfly;

public enum BlockFlyMode {
    NORMAL("Normal"),
    TELLY_BRIDGE("Telly Bridge"),
    OLD_TELLY("Old Telly"),
    KEEP_Y("Keep Y");

    private final String displayName;

    BlockFlyMode(final String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return this.displayName;
    }
}

package wtf.oraculus.client.feature.module.impl.world.scaffold;

public enum ScaffoldMode {
    NORMAL("Normal"),
    TELLY_BRIDGE("Telly Bridge"),
    OLD_TELLY("Old Telly"),
    KEEP_Y("Keep Y");

    private final String displayName;

    ScaffoldMode(final String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return this.displayName;
    }
}

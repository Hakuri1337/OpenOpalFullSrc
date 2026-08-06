package wtf.oraculus.client.feature.module.impl.utility.disabler;

import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.impl.utility.disabler.impl.CubecraftDisabler;
import wtf.oraculus.client.feature.module.impl.utility.disabler.impl.MinibloxDisabler;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.mode.ModeProperty;

/** Free edition: Heypixel and HypixelInventory modes are intentionally absent. */
public final class DisablerModule extends Module {
    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", this, Mode.CUBECRAFT)
            .alias("MINIBLOX_C0C", Mode.MINIBLOX)
            .alias("MINIBLOX_INPUT", Mode.MINIBLOX)
            .alias("MINIBLOX_MOVEPAYLOAD", Mode.MINIBLOX);
    private final BooleanProperty debug = new BooleanProperty("Debug", false)
            .hideIf(() -> !this.isMinibloxMode());

    public DisablerModule() {
        super("Disabler", "Lessens anti-cheat strength.", ModuleCategory.UTILITY);
        addProperties(mode, debug);
        addModuleModes(mode, new CubecraftDisabler(this), new MinibloxDisabler(this));
    }

    @Override
    public String getSuffix() {
        if (this.getActiveMode() instanceof CubecraftDisabler cubecraft) {
            return cubecraft.getStatusSuffix();
        }
        return mode.getValue().toString();
    }

    public boolean isDebugEnabled() {
        return debug.getValue();
    }

    private boolean isMinibloxMode() {
        return mode.getValue() == Mode.MINIBLOX;
    }

    public enum Mode {
        CUBECRAFT("CubeCraft"),
        MINIBLOX("MiniBlox");

        private final String name;

        Mode(final String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}

package wtf.oraculus.client.feature.module.impl.movement.longjump;

import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.impl.movement.longjump.impl.AntiGamingChairFireballLongJump;
import wtf.oraculus.client.feature.module.property.impl.mode.ModeProperty;

public final class LongJumpModule extends Module {

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", this, Mode.ANTI_GAMING_CHAIR_FIREBALL);

    public LongJumpModule() {
        super("Long Jump", "Allows you to jump further.", ModuleCategory.MOVEMENT);
        addProperties(mode);
        addModuleModes(mode,new AntiGamingChairFireballLongJump(this));
    }

    @Override
    protected void onEnable() {
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        super.onDisable();
    }

    @Override
    public String getSuffix() {
        return mode.getValue().toString();
    }

    public enum Mode {
        ANTI_GAMING_CHAIR_FIREBALL("Anti Gaming Chair Fireball");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

}

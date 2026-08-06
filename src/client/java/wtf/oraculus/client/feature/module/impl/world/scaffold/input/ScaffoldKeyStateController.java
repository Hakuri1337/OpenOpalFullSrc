package wtf.oraculus.client.feature.module.impl.world.scaffold.input;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import static wtf.oraculus.client.Constants.mc;

public final class ScaffoldKeyStateController {
    public boolean isPhysicalJumpDown() {
        return this.isPhysicalKeyDown(mc.options.jumpKey);
    }

    public void setJump(final boolean pressed) {
        mc.options.jumpKey.setPressed(pressed);
    }

    public void setSneak(final boolean pressed) {
        mc.options.sneakKey.setPressed(pressed);
    }

    public void setSprint(final boolean pressed) {
        mc.options.sprintKey.setPressed(pressed);
    }

    public void restorePhysicalStates() {
        if (mc.getWindow() == null) {
            return;
        }
        mc.options.jumpKey.setPressed(this.isPhysicalKeyDown(mc.options.jumpKey));
        mc.options.sneakKey.setPressed(this.isPhysicalKeyDown(mc.options.sneakKey));
        mc.options.sprintKey.setPressed(this.isPhysicalKeyDown(mc.options.sprintKey));
        mc.options.useKey.setPressed(false);
    }

    private boolean isPhysicalKeyDown(final KeyBinding keyBinding) {
        return mc.getWindow() != null
                && InputUtil.isKeyPressed(mc.getWindow(), keyBinding.getDefaultKey().getCode());
    }
}

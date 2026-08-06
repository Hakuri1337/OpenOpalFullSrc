package wtf.oraculus.client.feature.helper.impl.player.mouse;

import net.minecraft.client.option.KeyBinding;
import mixin.KeyBindingAccessor;

public final class MouseButton {

    private final KeyBinding keyBinding;

    public MouseButton(KeyBinding keyBinding) {
        this.keyBinding = keyBinding;
    }

    private boolean pressed;
    private boolean disabled;
    private int holdTicks;

    public void setDisabled() {
        this.pressed = false;
        this.holdTicks = 0;
        this.disabled = true;
    }

    public void setPressed() {
        this.setPressed(true, 0);
    }

    public void setPressed(boolean pressed, int holdTicks) {
        this.pressed = pressed;
        this.holdTicks = holdTicks;
    }

    public boolean wasPressed() {
        if (this.disabled) {
            return false;
        }
        boolean pressed = false;
        if (this.pressed) {
            pressed = true;
            this.pressed = false;
        }
        return this.keyBinding.wasPressed() || pressed;
    }

    public boolean isPressed() {
        if (this.disabled) {
            return false;
        }
        return this.keyBinding.isPressed() || this.pressed || this.holdTicks > 0;
    }

    public boolean isForcePressed() {
        return this.pressed;
    }

    public void tick() {
        if (this.holdTicks > 0) {
            this.holdTicks--;
        }
        if (this.keyBinding.isPressed() || this.holdTicks == 0) {
            this.showSwings = true;
        }
        this.pressed = false;
        this.disabled = false;
    }

    public int getHoldTicks() {
        return holdTicks;
    }

    public boolean isDisabled() {
        return disabled;
    }

    private boolean showSwings = true;

    public boolean isShowSwings() {
        return showSwings || ((KeyBindingAccessor) this.keyBinding).getTimesPressed() > 0;
    }

    public void setShowSwings(boolean showSwings) {
        this.showSwings = showSwings;
    }

    public KeyBinding getKeyBinding() {
        return keyBinding;
    }
}

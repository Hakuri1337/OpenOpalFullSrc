package wtf.oraculus.client.feature.helper.impl.player.mouse;

import net.minecraft.client.option.KeyBinding;
import wtf.oraculus.mixin.KeyBindingAccessor;

public final class MouseButton {

    private final KeyBinding keyBinding;

    public MouseButton(KeyBinding keyBinding) {
        this.keyBinding = keyBinding;
    }

    private boolean pressed;
    private boolean syntheticPress;
    private boolean disabled;
    private int holdTicks;

    public void setDisabled() {
        this.pressed = false;
        this.syntheticPress = false;
        this.holdTicks = 0;
        this.disabled = true;
    }

    public void setPressed() {
        this.setPressed(true, 0);
    }

    public void setPressed(boolean pressed, int holdTicks) {
        this.pressed = pressed;
        this.syntheticPress = pressed;
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

    /**
     * True for the current input pass when a module, rather than a physical
     * mouse press, requested this button.
     */
    public boolean isSyntheticPress() {
        return this.syntheticPress;
    }

    public boolean isPhysicalPressed() {
        return this.keyBinding.isPressed();
    }

    public void tick() {
        if (this.holdTicks > 0) {
            this.holdTicks--;
        }
        if (this.keyBinding.isPressed() || this.holdTicks == 0) {
            this.showSwings = true;
        }
        this.pressed = false;
        this.syntheticPress = false;
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

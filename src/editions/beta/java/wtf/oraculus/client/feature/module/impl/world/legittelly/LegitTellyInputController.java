package wtf.oraculus.client.feature.module.impl.world.legittelly;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import wtf.oraculus.mixin.KeyBindingAccessor;
import wtf.oraculus.utility.player.PlayerUtility;

import java.util.IdentityHashMap;
import java.util.Map;

import static wtf.oraculus.client.Constants.mc;

final class LegitTellyInputController {
    private final Map<KeyBinding, Boolean> activationHeld = new IdentityHashMap<>();
    private final Map<KeyBinding, Boolean> releasedSinceActivation = new IdentityHashMap<>();

    private float forward;
    private float sideways;
    private boolean jump;
    private boolean sneak;
    private boolean sprint;

    void captureActivationInputs() {
        this.activationHeld.clear();
        this.releasedSinceActivation.clear();
        for (final KeyBinding key : movementKeys()) {
            this.activationHeld.put(key, this.isPhysicalDown(key));
            this.releasedSinceActivation.put(key, false);
        }
    }

    boolean hasManualTakeover() {
        for (final KeyBinding key : movementKeys()) {
            final boolean down = this.isPhysicalDown(key);
            final boolean heldAtActivation = this.activationHeld.getOrDefault(key, false);
            final boolean released = this.releasedSinceActivation.getOrDefault(key, false);
            if (heldAtActivation && !down) {
                this.releasedSinceActivation.put(key, true);
            } else if (down && (!heldAtActivation || released)) {
                return true;
            }
        }
        return false;
    }

    void set(final float forward, final float sideways, final boolean jump,
             final boolean sneak, final boolean sprint) {
        this.forward = forward;
        this.sideways = sideways;
        this.jump = jump;
        this.sneak = sneak;
        this.sprint = sprint;

        mc.options.jumpKey.setPressed(jump);
        mc.options.sneakKey.setPressed(sneak);
        mc.options.sprintKey.setPressed(sprint);
        mc.options.useKey.setPressed(false);
        mc.options.attackKey.setPressed(false);
    }

    void apply(final wtf.oraculus.event.impl.game.input.MoveInputEvent event) {
        event.setForward(this.forward);
        event.setSideways(this.sideways);
        event.setJump(this.jump);
        event.setSneak(this.sneak);
    }

    boolean sprinting() {
        return this.sprint;
    }

    boolean isPhysicalSneakDown() {
        return this.isPhysicalDown(mc.options.sneakKey);
    }

    boolean isPhysicalUseDown() {
        return this.isPhysicalDown(mc.options.useKey);
    }

    void restore() {
        if (mc.options == null || mc.getWindow() == null) {
            return;
        }
        for (final KeyBinding key : restoredKeys()) {
            key.setPressed(this.isPhysicalDown(key));
        }
        this.activationHeld.clear();
        this.releasedSinceActivation.clear();
        this.forward = 0.0F;
        this.sideways = 0.0F;
        this.jump = false;
        this.sneak = false;
        this.sprint = false;
    }

    private KeyBinding[] movementKeys() {
        return new KeyBinding[]{
                mc.options.forwardKey, mc.options.backKey,
                mc.options.leftKey, mc.options.rightKey,
                mc.options.jumpKey, mc.options.sneakKey,
                mc.options.sprintKey
        };
    }

    private KeyBinding[] restoredKeys() {
        return new KeyBinding[]{
                mc.options.forwardKey, mc.options.backKey,
                mc.options.leftKey, mc.options.rightKey,
                mc.options.jumpKey, mc.options.sneakKey,
                mc.options.sprintKey, mc.options.useKey,
                mc.options.attackKey
        };
    }

    private boolean isPhysicalDown(final KeyBinding binding) {
        if (binding == null || mc.getWindow() == null) {
            return false;
        }
        final InputUtil.Key key = ((KeyBindingAccessor) binding).getBoundKey();
        if (key.getCategory() == InputUtil.Type.MOUSE) {
            return PlayerUtility.isMouseButtonPressed(key.getCode());
        }
        return InputUtil.isKeyPressed(mc.getWindow(), key.getCode());
    }
}

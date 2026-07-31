package wtf.oraculus.client.feature.module.impl.movement.inventorymove;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import wtf.oraculus.mixin.KeyBindingAccessor;

import static wtf.oraculus.client.Constants.mc;

/** Reads the active binding, including remapped mouse bindings. */
public final class InventoryMoveInputTracker {
    public boolean isPressed(final KeyBinding binding) {
        final InputUtil.Key key = ((KeyBindingAccessor) binding).getBoundKey();
        if (key.getCategory() == InputUtil.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), key.getCode()) == GLFW.GLFW_PRESS;
        }
        return InputUtil.isKeyPressed(mc.getWindow(), key.getCode());
    }

    public void clear() {
        // The tracker reads GLFW state directly, so no stale key state exists to reset.
    }
}

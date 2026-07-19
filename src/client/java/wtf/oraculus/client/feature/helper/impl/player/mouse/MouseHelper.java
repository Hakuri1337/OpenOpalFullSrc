package wtf.oraculus.client.feature.helper.impl.player.mouse;

import net.minecraft.client.option.KeyBinding;

import java.util.HashMap;
import java.util.Map;

import static wtf.oraculus.client.Constants.mc;

public final class MouseHelper {

    private final Map<KeyBinding, MouseButton> mouseButtonMap = new HashMap<>();
    private final MouseButton leftButton, rightButton;

    private MouseHelper() {
        this.leftButton = this.register(new MouseButton(mc.options.attackKey));
        this.rightButton = this.register(new MouseButton(mc.options.useKey));
    }

    private <T extends MouseButton> T register(T button) {
        this.mouseButtonMap.put(button.getKeyBinding(), button);
        return button;
    }

    public void tick() {
        this.leftButton.tick();
        this.rightButton.tick();
    }

    public static MouseButton getButtonFromBinding(KeyBinding binding) {
        return instance.mouseButtonMap.get(binding);
    }

    public static MouseButton getLeftButton() {
        return instance.leftButton;
    }

    public static MouseButton getRightButton() {
        return instance.rightButton;
    }

    private static MouseHelper instance;

    public static void setInstance() {
        instance = new MouseHelper();
    }

    public static MouseHelper getInstance() {
        return instance;
    }
}

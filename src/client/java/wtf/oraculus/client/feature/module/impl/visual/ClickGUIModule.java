package wtf.oraculus.client.feature.module.impl.visual;

import org.lwjgl.glfw.GLFW;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.binding.type.InputType;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.screen.click.dropdown.DropdownClickGUI;
import wtf.oraculus.event.impl.render.RenderBloomEvent;
import wtf.oraculus.event.subscriber.Subscribe;

import static wtf.oraculus.client.Constants.mc;

public final class ClickGUIModule extends Module {

    private final DropdownClickGUI dropdownClickGUI = new DropdownClickGUI();
    private final BooleanProperty allowDrag = new BooleanProperty("Allow Drag", false);
    private final BooleanProperty enhancedMainMenu = new BooleanProperty("Enhanced Main Menu", true);

    public ClickGUIModule() {
        super("Click GUI", "A display for interacting with client features.", ModuleCategory.VISUAL);
        OraculusClient.getInstance().getBindRepository().getBindingService().register(GLFW.GLFW_KEY_RIGHT_SHIFT, this, InputType.KEYBOARD);
        this.addProperties(allowDrag, enhancedMainMenu);
    }

    @Override
    protected void onEnable() {
        mc.setScreen(dropdownClickGUI);
    }

    @Override
    protected void onDisable() {
        if (mc.currentScreen == dropdownClickGUI) {
            dropdownClickGUI.close();
        }
    }

    @Subscribe
    public void onBloomRender(final RenderBloomEvent event) {
        if (mc.currentScreen == dropdownClickGUI) {
            dropdownClickGUI.render(event.drawContext(), -1, -1, event.tickDelta());
        }
    }

    public boolean isAllowDrag() {
        return allowDrag.getValue();
    }

    public boolean isEnhancedMainMenu() {
        return enhancedMainMenu.getValue();
    }

}

package wtf.oraculus.client.feature.module.impl.visual;

import org.lwjgl.glfw.GLFW;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.binding.type.InputType;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.impl.visual.overlay.LiquidGlassV2Settings;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.screen.click.dropdown.DropdownClickGUI;
import wtf.oraculus.event.impl.render.RenderBloomEvent;
import wtf.oraculus.event.subscriber.Subscribe;

import static wtf.oraculus.client.Constants.mc;

public final class ClickGUIModule extends Module {

    private final DropdownClickGUI dropdownClickGUI = new DropdownClickGUI();
    private final BooleanProperty allowDrag = new BooleanProperty("Allow Drag", false);
    private final BooleanProperty enhancedMainMenu = new BooleanProperty("Enhanced Main Menu", true);
    private final LiquidGlassV2Settings liquidGlassV2 = new LiquidGlassV2Settings(
            "click-gui", "click-gui-liquid-glass-v2", () -> true
    );

    public ClickGUIModule() {
        super("Click GUI", "A display for interacting with client features.", ModuleCategory.VISUAL);
        OraculusClient.getInstance().getBindRepository().getBindingService().register(GLFW.GLFW_KEY_RIGHT_SHIFT, this, InputType.KEYBOARD);
        this.addProperties(this.liquidGlassV2.after(allowDrag, enhancedMainMenu));
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
            dropdownClickGUI.renderBloom(event.drawContext(), event.tickDelta());
        }
    }

    public boolean isAllowDrag() {
        return allowDrag.getValue();
    }

    public boolean isEnhancedMainMenu() {
        return enhancedMainMenu.getValue();
    }

    public boolean isLiquidGlassV2() {
        return this.isEnabled()
                && mc.currentScreen == this.dropdownClickGUI
                && this.liquidGlassV2.isEnabled();
    }

    public LiquidGlassV2Settings getLiquidGlassV2Settings() {
        return this.liquidGlassV2;
    }

}

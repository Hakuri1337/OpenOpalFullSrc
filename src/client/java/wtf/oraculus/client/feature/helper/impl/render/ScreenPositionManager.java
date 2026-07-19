package wtf.oraculus.client.feature.helper.impl.render;

import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import wtf.oraculus.client.feature.helper.IHelper;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.property.impl.ScreenPositionProperty;
import wtf.oraculus.client.renderer.NVGRenderer;
import wtf.oraculus.event.EventDispatcher;
import wtf.oraculus.event.impl.render.RenderScreenEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.misc.HoverUtility;
import wtf.oraculus.utility.player.PlayerUtility;
import wtf.oraculus.utility.render.ColorUtility;

import java.util.HashMap;
import java.util.Map;

import static wtf.oraculus.client.Constants.mc;

public final class
ScreenPositionManager implements IHelper {

    private final Map<ScreenPositionProperty, Module> properties = new HashMap<>();
    private boolean dragging;

    private ScreenPositionManager() {
    }

    public void register(final Module module, final ScreenPositionProperty property) {
        this.properties.put(property, module);
    }

    @Subscribe(priority = -50)
    public void onRenderScreen(final RenderScreenEvent event) {
        if (!(mc.currentScreen instanceof ChatScreen)) {
            if (this.dragging) {
                this.releaseDraggedProperties();
            }
            return;
        }

        final double mouseX = event.mouseX();
        final double mouseY = event.mouseY();

        this.properties.forEach((property, module) -> {
            if (!module.isEnabled()) {
                return;
            }

            if (property.isDragging()) {
                property.setRelativeX((float) (mouseX - property.getStartX()));
                property.setRelativeY((float) (mouseY - property.getStartY()));

                if (!PlayerUtility.isKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT)) {
                    property.snapToGrid();
                }
            }

            final float scaledX = property.getScaledX();
            final float scaledY = property.getScaledY();

            final float width = property.getWidth();
            final float height = property.getHeight();

            if (HoverUtility.isHovering(scaledX, scaledY, width, height, mouseX, mouseY)) {
                NVGRenderer.roundedRect(
                        scaledX - 2, scaledY - 2,
                        width + 4, height + 4,
                        6, ColorUtility.applyOpacity(0xFF000000, 0.2F)
                );
            }
        });

        final Window window = mc.getWindow();
        final int scaledWidth = window.getScaledWidth();
        final int scaledHeight = window.getScaledHeight();

        if (this.dragging) {
            final int gridLineColor = ColorUtility.applyOpacity(-1, 0.5F);
            NVGRenderer.rectOutline(0, 0, scaledWidth, scaledHeight, 1, gridLineColor);
            NVGRenderer.rect(0, scaledHeight / 2.F - 0.5F, scaledWidth, 1, gridLineColor);
            NVGRenderer.rect(scaledWidth / 2.F - 1, 0, 1, scaledHeight, gridLineColor);
        }
    }

    public void onMouseClick(final double mouseX, final double mouseY, final int button) {
        if (button != 0) {
            return;
        }

        this.properties.forEach((property, module) -> {
            if (!module.isEnabled()) {
                return;
            }

            final float scaledX = property.getScaledX();
            final float scaledY = property.getScaledY();

            if (HoverUtility.isHovering(scaledX, scaledY, property.getWidth(), property.getHeight(), mouseX, mouseY)) {
                property.setStartX((float) (mouseX - scaledX));
                property.setStartY((float) (mouseY - scaledY));
                property.setDragging(true);
                this.dragging = true;
            }
        });
    }

    public void releaseDraggedProperties() {
        this.properties.forEach((property, _module) -> property.setDragging(false));
        this.dragging = false;
    }

    private static ScreenPositionManager instance;

    public static ScreenPositionManager getInstance() {
        return instance;
    }

    public static void setInstance() {
        instance = new ScreenPositionManager();
        EventDispatcher.subscribe(instance);
    }

}

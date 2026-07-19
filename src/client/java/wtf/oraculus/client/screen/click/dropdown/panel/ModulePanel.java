package wtf.oraculus.client.screen.click.dropdown.panel;

import com.ibm.icu.impl.Pair;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.binding.repository.BindRepository;
import wtf.oraculus.client.binding.type.InputType;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.renderer.NVGRenderer;
import wtf.oraculus.client.renderer.repository.FontRepository;
import wtf.oraculus.client.renderer.text.NVGTextRenderer;
import wtf.oraculus.client.screen.click.OraculusPanelComponent;
import wtf.oraculus.client.screen.click.dropdown.DropdownClickGUI;
import wtf.oraculus.client.screen.click.dropdown.panel.property.PropertyProvider;
import wtf.oraculus.utility.misc.HoverUtility;
import wtf.oraculus.utility.render.ColorUtility;
import wtf.oraculus.utility.render.animation.Animation;
import wtf.oraculus.utility.render.animation.Easing;

import java.util.Optional;

import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER;
import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE;

public final class ModulePanel extends OraculusPanelComponent {

    private final Module module;

    private Animation hoverAnimation, toggleAnimation;
    private final Animation expandAnimation = new Animation(Easing.DECELERATE, 125);

    private boolean lastModule, expanded, selectingBind;

    private final PropertyProvider propertyProvider;

    private final BindRepository bindRepository = OraculusClient.getInstance().getBindRepository();

    public ModulePanel(final Module module) {
        this.module = module;

        this.propertyProvider = new PropertyProvider(module, this::isExpandedAnimation, this::isLastModule);
    }

    private boolean isExpandedAnimation() {
        return expanded || expandAnimation.getValue() > 0F;
    }

    public boolean isExpanded() {
        return expanded;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        handleAnimations(mouseX, mouseY);

        final int baseColor = 0xff1e1e2d;

        final String font = module.isEnabled() ? "productsans-bold" : "productsans-medium";

        final Pair<Integer, Integer> colors = ColorUtility.getClientTheme();
        if (!lastModule) {
            NVGRenderer.rect(x, y, width, height, NVGRenderer.BLUR_PAINT);
            NVGRenderer.rect(x, y, width, height, ColorUtility.applyOpacity(baseColor, 0.7F));

            NVGRenderer.rectGradient(x, y, width, height, ColorUtility.applyOpacity(colors.first, toggleAnimation.getValue()), ColorUtility.applyOpacity(colors.second, toggleAnimation.getValue()), 0);
        } else {
            NVGRenderer.roundedRectVarying(x, y, width, height, 0, 0, 5, 5, NVGRenderer.BLUR_PAINT);
            NVGRenderer.roundedRectVarying(x, y, width, height, 0, 0, 5, 5, ColorUtility.applyOpacity(baseColor, 0.7F));

            NVGRenderer.roundedRectVaryingGradient(x, y, width, height, 0, 0, 5, 5, ColorUtility.applyOpacity(colors.first, toggleAnimation.getValue()), ColorUtility.applyOpacity(colors.second, toggleAnimation.getValue()), 0);
        }

        NVGRenderer.scissor(x, y, width, height, () -> {
            final int color = module.isEnabled() ? -1 : ColorUtility.darker(-1, 0.2F);

            FontRepository.getFont(font).drawString(module.getName(), x + 6, y + 12.5F, 8, color);

            if (propertyProvider.isHasProperties() && !selectingBind && !DropdownClickGUI.displayingBinds) {
                final String expandIcon = "\ue5cf";
                final NVGTextRenderer iconFont = FontRepository.getFont("materialicons-regular");
                final float iconSize = 12;
                final float iconWidth = iconFont.getStringWidth(expandIcon, iconSize);
                NVGRenderer.rotate(
                        expandAnimation.getValue() * 180,
                        x + width - 17,
                        y + 4,
                        iconWidth,
                        iconSize,
                        () -> iconFont.drawString("\ue5cf", 0, 0, iconSize, color, false, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE)
                );
            }

            final Optional<Pair<Integer, InputType>> bind = bindRepository.getBindingService().getKeyFromBindable(module);

            String keyString = null;
            if (selectingBind) {
                keyString = Formatting.GRAY + "[" + Formatting.WHITE + "..." + Formatting.GRAY + "]";
            } else if (DropdownClickGUI.displayingBinds && bind.isPresent()) {
                final String key = bindRepository.getNameFromInteger(bind.get().first);
                keyString = Formatting.GRAY + "[" + Formatting.WHITE + key + Formatting.GRAY + "]";
            }

            if (keyString != null) {
                FontRepository.getFont("productsans-medium").drawString(keyString, x + width - FontRepository.getFont(font).getStringWidth(keyString, 7) - 5, y + 12, 7, -1);
            }

            if (expandAnimation.isFinished() && !isExpanded()) return;

            propertyProvider.setX(x);
            propertyProvider.setY(y + 20);
            propertyProvider.setWidth(width);
            propertyProvider.render(context, mouseX, mouseY, delta);
        });
    }

    private void handleAnimations(final float mouseX, final float mouseY) {
        final float hoverFactor = HoverUtility.isHovering(x, y, width, height - (isExpanded() ? propertyProvider.getExtraHeight() : 0), mouseX, mouseY) ? 0.7F : 0;
        if (this.hoverAnimation == null) {
            this.hoverAnimation = new Animation(Easing.DECELERATE, 150);
            this.hoverAnimation.setValue(hoverFactor);
        } else {
            this.hoverAnimation.run(hoverFactor);
        }

        final float toggledFactor = module.isEnabled() ? 0.4F : 0;
        if (this.toggleAnimation == null) {
            this.toggleAnimation = new Animation(Easing.DECELERATE, 150);
            this.toggleAnimation.setValue(toggledFactor);
        } else {
            this.toggleAnimation.run(toggledFactor);
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (selectingBind) {
            bindRepository.getBindingService().clearBindings(module);
            bindRepository.getBindingService().register(button, module, InputType.MOUSE);
            selectingBind = DropdownClickGUI.selectingBind = false;
            return;
        }

        if (HoverUtility.isHovering(x, y, width, height - (isExpanded() ? propertyProvider.getExtraHeight() : 0), mouseX, mouseY)) {
            switch (button) {
                case 0 -> module.toggle();
                case 1 -> {
                    if (!module.getPropertyList().isEmpty()) {
                        expanded = !expanded;
                    }
                }
                case 2 -> {
                    selectingBind = DropdownClickGUI.selectingBind = true;
                }
            }
        }

        propertyProvider.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void keyPressed(KeyInput keyInput) {
        if (selectingBind) {
            bindRepository.getBindingService().clearBindings(module);
            if (keyInput.key() != GLFW.GLFW_KEY_ESCAPE) {
                bindRepository.getBindingService().register(keyInput.key(), module, InputType.KEYBOARD);
            }
            selectingBind = DropdownClickGUI.selectingBind = false;
            return;
        }

        propertyProvider.keyPressed(keyInput);
    }

    @Override
    public void charTyped(char chr, int modifiers) {
        propertyProvider.charTyped(chr, modifiers);
    }

    public float getAddedHeight() {
        return this.propertyProvider.getExtraHeight();
    }

    public Animation getExpandAnimation() {
        return expandAnimation;
    }

    @Override
    public void mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        propertyProvider.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        propertyProvider.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void init() {
        propertyProvider.init();
    }

    @Override
    public void close() {
        propertyProvider.close();
    }

    public void setLastModule(final boolean lastModule) {
        this.lastModule = lastModule;
    }

    public boolean isLastModule() {
        return lastModule;
    }

    public Module getModule() {
        return module;
    }

}

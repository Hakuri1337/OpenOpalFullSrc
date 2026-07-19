package wtf.oraculus.client.screen.click.dropdown.panel.property.impl;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import wtf.oraculus.client.feature.module.property.impl.GroupProperty;
import wtf.oraculus.client.renderer.NVGRenderer;
import wtf.oraculus.client.renderer.repository.FontRepository;
import wtf.oraculus.client.renderer.text.NVGTextRenderer;
import wtf.oraculus.client.screen.click.dropdown.panel.property.PropertyPanel;
import wtf.oraculus.client.screen.click.dropdown.panel.property.PropertyProvider;
import wtf.oraculus.utility.misc.HoverUtility;
import wtf.oraculus.utility.render.ColorUtility;
import wtf.oraculus.utility.render.animation.Animation;
import wtf.oraculus.utility.render.animation.Easing;

import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER;
import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE;

public final class GroupPropertyComponent extends PropertyPanel<GroupProperty> {

    private final PropertyProvider propertyProvider;

    private final Animation expandAnimation = new Animation(Easing.DECELERATE, 125);
    private boolean expanded;

    public GroupPropertyComponent(final GroupProperty property) {
        super(property);

        this.propertyProvider = new PropertyProvider(property, this::isExpandedAnimation, null);
    }

    private boolean isExpandedAnimation() {
        return expanded || expandAnimation.getValue() > 0F;
    }

    @Override
    public void close() {
        propertyProvider.close();
    }

    @Override
    public void init() {
        propertyProvider.init();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        expandAnimation.run(expanded ? 1 : 0);

        final float padding = 3;
        final float animatedPadding = padding * (1 - expandAnimation.getValue());

        final float rectWidth = this.width;

        final float adjustedX = x + animatedPadding;
        final float adjustedY = y + animatedPadding;
        final float adjustedWidth = rectWidth - (animatedPadding * 2);
        final float adjustedHeight = 22 - (animatedPadding * 2);

        final float cornerRadius = 4 * (1 - expandAnimation.getValue());
        final NVGTextRenderer font = FontRepository.getFont("productsans-bold");

        NVGRenderer.roundedRect(adjustedX, adjustedY, adjustedWidth, adjustedHeight, cornerRadius, ColorUtility.applyOpacity(0xff000000, 0.2F));
        font.drawString(
                getProperty().getName(),
                adjustedX + (adjustedWidth - font.getStringWidth(getProperty().getName(), 7)) / 2,
                y + 13.5F, 7, -1
        );

        final String expandIcon = "\ue5cf";
        final NVGTextRenderer iconFont = FontRepository.getFont("materialicons-regular");
        final float iconSize = 12;
        final float iconWidth = iconFont.getStringWidth(expandIcon, iconSize);
        NVGRenderer.rotate(
                expandAnimation.getValue() * 180,
                x + padding + rectWidth - 20,
                y + 5,
                iconWidth,
                iconSize,
                () -> iconFont.drawString("\ue5cf", 0, 0, iconSize, -1, false, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE)
        );

        NVGRenderer.scissor(x, y, width, height, () -> {
            propertyProvider.setX(x);
            propertyProvider.setY(y + 22);
            propertyProvider.setWidth(width);
            propertyProvider.render(context, mouseX, mouseY, delta);
        });

        final float height = propertyProvider.getExtraHeight();
        setHeight((height * expandAnimation.getValue()) + 22);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (HoverUtility.isHovering(x, y, width, 17, mouseX, mouseY)) {
            expanded = !expanded;
        }

        propertyProvider.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void keyPressed(KeyInput keyInput) {
        propertyProvider.keyPressed(keyInput);
    }

    @Override
    public void charTyped(char chr, int modifiers) {
        propertyProvider.charTyped(chr, modifiers);
    }

    @Override
    public void mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        propertyProvider.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        propertyProvider.mouseReleased(mouseX, mouseY, button);
    }
}

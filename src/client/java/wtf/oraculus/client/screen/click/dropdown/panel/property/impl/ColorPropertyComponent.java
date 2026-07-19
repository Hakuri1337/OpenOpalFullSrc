package wtf.oraculus.client.screen.click.dropdown.panel.property.impl;

import net.minecraft.client.gui.DrawContext;
import wtf.oraculus.client.feature.module.property.impl.ColorProperty;
import wtf.oraculus.client.renderer.NVGRenderer;
import wtf.oraculus.client.renderer.repository.FontRepository;
import wtf.oraculus.client.screen.click.dropdown.panel.property.PropertyPanel;
import wtf.oraculus.utility.misc.HoverUtility;
import wtf.oraculus.utility.render.ColorUtility;
import wtf.oraculus.utility.render.animation.Animation;
import wtf.oraculus.utility.render.animation.Easing;

import java.awt.*;

public final class ColorPropertyComponent extends PropertyPanel<ColorProperty> {

    private boolean expanded;

    private ColorDragType colorDragType;

    private final Animation expandAnimation = new Animation(Easing.DECELERATE, 125);

    public ColorPropertyComponent(final ColorProperty property) {
        super(property);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        expandAnimation.run(expanded ? 1 : 0);

        FontRepository.getFont("productsans-medium").drawString(getProperty().getName(), x + 5, y + 10.5F, 7, -1);
        NVGRenderer.roundedRect(x + width - 22, y + 3.5F, 18, 10, 3, getProperty().getValue());

        final float xPos = x + 5;
        final float yPos = y + DEFAULT_HEIGHT;
        final float w = 65;
        final float h = 50;

        NVGRenderer.scissor(x, y, width, height, () -> {
            if (expandAnimation.getValue() > 0) {
                switch (colorDragType) {
                    case HUE -> {
                        getProperty().setHue(Math.min(1, Math.max(0, ((mouseY - yPos) / h))));
                    }
                    case PICKER -> {
                        getProperty().setSaturation(Math.min(1, Math.max(0, (mouseX - xPos) / w)));
                        getProperty().setBrightness(Math.min(1, Math.max(0, 1 - ((mouseY - yPos) / h))));
                    }
                }

                final float[] hsb = getProperty().getHSB();

                getProperty().updateValue();

                // Picker
                NVGRenderer.rect(xPos, yPos, w, h, Color.getHSBColor(hsb[0], 1, 1).getRGB());
                NVGRenderer.rectGradient(xPos, yPos, w, h, Color.getHSBColor(hsb[0], 0, 1).getRGB(), ColorUtility.applyOpacity(Color.getHSBColor(hsb[0], 0, 1).getRGB(), 0), 0);
                NVGRenderer.rectGradient(xPos, yPos, w, h, ColorUtility.applyOpacity(Color.getHSBColor(hsb[0], 1, 0).getRGB(), 0), Color.getHSBColor(hsb[0], 1, 0).getRGB(), 90);

                // Hue
                NVGRenderer.rainbowRect(xPos + w + 5, yPos, 8, h);
            }

            setHeight(DEFAULT_HEIGHT + (h * expandAnimation.getValue()));
        });
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (HoverUtility.isHovering(x, y, width, DEFAULT_HEIGHT, mouseX, mouseY) && button == 1) {
            expanded = !expanded;
            return;
        }

        if (button == 0) {
            final float xPos = x + 5;
            final float yPos = y + DEFAULT_HEIGHT;
            final float w = 65;
            final float h = 50;

            if (HoverUtility.isHovering(xPos, yPos, w, h, mouseX, mouseY)) {
                colorDragType = ColorDragType.PICKER;
            } else if (HoverUtility.isHovering(xPos + w + 5, yPos, 8, h, mouseX, mouseY)) {
                colorDragType = ColorDragType.HUE;
            }
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        colorDragType = ColorDragType.NONE;
    }

    private enum ColorDragType {
        PICKER,
        HUE,
        OPACITY,
        NONE
    }
}

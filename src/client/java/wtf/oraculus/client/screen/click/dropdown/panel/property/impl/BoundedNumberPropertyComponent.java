package wtf.oraculus.client.screen.click.dropdown.panel.property.impl;

import com.ibm.icu.impl.Pair;
import net.minecraft.client.gui.DrawContext;
import wtf.oraculus.client.feature.module.property.impl.number.BoundedNumberProperty;
import wtf.oraculus.client.renderer.NVGRenderer;
import wtf.oraculus.client.renderer.repository.FontRepository;
import wtf.oraculus.client.renderer.text.NVGTextRenderer;
import wtf.oraculus.client.screen.click.dropdown.panel.property.PropertyPanel;
import wtf.oraculus.utility.misc.HoverUtility;
import wtf.oraculus.utility.misc.math.MathUtility;
import wtf.oraculus.utility.render.ColorUtility;
import wtf.oraculus.utility.render.animation.Animation;
import wtf.oraculus.utility.render.animation.Easing;

public final class BoundedNumberPropertyComponent extends PropertyPanel<BoundedNumberProperty> {

    private boolean draggingLow, draggingHigh;
    private Animation draggingLowAnimation;
    private Animation draggingHighAnimation;

    public BoundedNumberPropertyComponent(final BoundedNumberProperty property) {
        super(property);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        setHeight(26);

        super.render(context, mouseX, mouseY, delta);

        final BoundedNumberProperty property = getProperty();
        final NVGTextRenderer font = FontRepository.getFont("productsans-medium");

        font.drawString(property.getName(), x + 5, y + 8.5F, 7, -1);

        final Pair<Double, Double> values = property.getValue();
        final Double lowValue = values.first;
        final Double highValue = values.second;

        final float sliderWidth = width - 12;
        final float sliderHeight = 2.5F;

        final float sliderX = x + 6;
        final float sliderY = y + 13F;

        final float percent = Math.min(1, Math.max(0, (mouseX - sliderX) / sliderWidth));

        if (mouseX != -1) {
            final double newValue = MathUtility.interpolate(property.getMinValue(), property.getMaxValue(), percent);
            if (draggingLow) {
                if (newValue <= highValue) {
                    property.setValue(Pair.of(newValue, highValue));
                }
            }
            if (draggingHigh) {
                if (newValue >= lowValue) {
                    property.setValue(Pair.of(lowValue, newValue));
                }
            }
        }

        final double lowPercent = (lowValue - property.getMinValue()) / (property.getMaxValue() - property.getMinValue());
        final double highPercent = (highValue - property.getMinValue()) / (property.getMaxValue() - property.getMinValue());

        final double lowDestination = sliderWidth * lowPercent;
        if (this.draggingLowAnimation == null) {
            this.draggingLowAnimation = new Animation(Easing.LINEAR, 50);
            this.draggingLowAnimation.setValue((float) lowDestination);
        } else {
            this.draggingLowAnimation.run((float) lowDestination);
        }

        final double highDestination = sliderWidth * highPercent;
        if (this.draggingHighAnimation == null) {
            this.draggingHighAnimation = new Animation(Easing.LINEAR, 50);
            this.draggingHighAnimation.setValue((float) highDestination);
        } else {
            this.draggingHighAnimation.run((float) highDestination);
        }

        NVGRenderer.roundedRect(sliderX, sliderY, sliderWidth, sliderHeight, sliderHeight / 2f, 0xff373737);

        final float lowAnim = draggingLowAnimation.getValue();
        final float highAnim = draggingHighAnimation.getValue();
        if (highAnim > lowAnim) {
            final int color = ColorUtility.getClientTheme().first;
            NVGRenderer.roundedRectGradient(sliderX + lowAnim, sliderY, highAnim - lowAnim, sliderHeight, sliderHeight / 2f, color, ColorUtility.darker(color, 0.5F), 90);
        }

        NVGRenderer.roundedRectGradient(sliderX + lowAnim - 1, sliderY - 1.3f, 2, 5, 1, -1, ColorUtility.darker(-1, 0.1F), 90);
        NVGRenderer.roundedRectGradient(sliderX + highAnim - 1, sliderY - 1.3f, 2, 5, 1, -1, ColorUtility.darker(-1, 0.1F), 90);

        String lowValueString;
        if (lowValue == lowValue.intValue()) {
            lowValueString = String.valueOf(lowValue.intValue());
        } else {
            lowValueString = String.format("%.3f", lowValue).replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        String highValueString;
        if (highValue == highValue.intValue()) {
            highValueString = String.valueOf(highValue.intValue());
        } else {
            highValueString = String.format("%.3f", highValue).replaceAll("0+$", "").replaceAll("\\.$", "");
        }

        if (getProperty().getSuffix() != null) {
            lowValueString += getProperty().getSuffix();
            highValueString += getProperty().getSuffix();
        }

        final float lowValueWidth = font.getStringWidth(lowValueString, 5.5F);
        final float highValueWidth = font.getStringWidth(highValueString, 5.5F);
        final float lowValueX = Math.max(x + 4, Math.min(x + width - lowValueWidth - 4, sliderX + lowAnim - (lowValueWidth / 2)));
        final float highValueX = Math.max(x + 4, Math.min(x + width - highValueWidth - 4, sliderX + highAnim - (highValueWidth / 2)));
        font.drawString(lowValueString, lowValueX, y + 22f, 5.5F, ColorUtility.applyOpacity(-1, 0.8F));
        font.drawString(highValueString, highValueX, y + 22f, 5.5F, ColorUtility.applyOpacity(-1, 0.8F));
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (HoverUtility.isHovering(x, y, width, height, mouseX, mouseY) && button == 0) {
            final float sliderWidth = width - 12;
            final float sliderX = x + 6;

            final BoundedNumberProperty property = getProperty();

            final Pair<Double, Double> values = property.getValue();
            final double lowPercent = (values.first - property.getMinValue()) /
                    (property.getMaxValue() - property.getMinValue());
            final double highPercent = (values.second - property.getMinValue()) /
                    (property.getMaxValue() - property.getMinValue());

            final float lowSliderX = sliderX + (float) (sliderWidth * lowPercent);
            final float highSliderX = sliderX + (float) (sliderWidth * highPercent);

            final double lowSliderDiff = Math.abs(mouseX - lowSliderX);
            final double highSliderDiff = Math.abs(mouseX - highSliderX);
            if (lowSliderDiff == highSliderDiff) {
                if (mouseX < lowSliderX) {
                    draggingLow = true;
                } else {
                    draggingHigh = true;
                }
            } else if (lowSliderDiff < highSliderDiff) {
                draggingLow = true;
            } else {
                draggingHigh = true;
            }
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0)
            draggingHigh = draggingLow = false;
    }
}

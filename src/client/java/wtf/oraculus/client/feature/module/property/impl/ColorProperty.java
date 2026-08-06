package wtf.oraculus.client.feature.module.property.impl;

import wtf.oraculus.client.feature.module.property.Property;
import wtf.oraculus.client.feature.module.property.impl.mode.ModuleMode;
import wtf.oraculus.client.screen.click.dropdown.panel.property.PropertyPanel;
import wtf.oraculus.client.screen.click.dropdown.panel.property.impl.ColorPropertyComponent;
import wtf.oraculus.utility.render.ColorUtility;

import java.awt.*;

public final class ColorProperty extends Property<Integer> {

    private final float[] hsb = new float[3];

    public ColorProperty(final String name, final int value) {
        super(name);
        setValue(value);
    }

    public ColorProperty(final String name, final ModuleMode<?> parent, final int value) {
        super(name, parent);
        setValue(value);
    }

    public float[] getHSB() {
        return hsb;
    }

    public void setHue(final float hue) {
        this.hsb[0] = hue;
    }

    public void setSaturation(final float saturation) {
        this.hsb[1] = saturation;
    }

    public void setBrightness(final float brightness) {
        this.hsb[2] = brightness;
    }

    public void updateValue() {
        setValue(Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]));
    }

    @Override
    public PropertyPanel<?> createClickGUIComponent() {
        return new ColorPropertyComponent(this);
    }

    @Override
    public void applyValue(final Object propertyValue) {
        final double colorHex = Double.parseDouble(String.valueOf(propertyValue));
        final int[] rgba = ColorUtility.hexToRGB((int) colorHex);

        final float[] hsb = Color.RGBtoHSB(rgba[0], rgba[1], rgba[2], null);
        setHue(hsb[0]);
        setSaturation(hsb[1]);
        setBrightness(hsb[2]);

        updateValue();
    }

}

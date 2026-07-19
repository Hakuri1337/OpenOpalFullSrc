package wtf.oraculus.client.feature.module.property.impl.number;

import org.jetbrains.annotations.NotNull;
import wtf.oraculus.client.feature.module.property.Property;
import wtf.oraculus.client.feature.module.property.impl.mode.ModuleMode;
import wtf.oraculus.client.screen.click.dropdown.panel.property.PropertyPanel;
import wtf.oraculus.client.screen.click.dropdown.panel.property.impl.NumberPropertyComponent;
import wtf.oraculus.utility.misc.math.MathUtility;

public final class NumberProperty extends Property<Double> {

    private final Double minValue, maxValue, increment;
    private String suffix;

    public NumberProperty(final String name, final double defaultValue, final double minValue, final double maxValue, final double increment) {
        super(name);

        this.minValue = minValue;
        this.maxValue = maxValue;
        this.increment = increment;

        setValue(defaultValue);
    }

    public NumberProperty(final String name, final ModuleMode<?> parent, final double defaultValue, final double minValue, final double maxValue, final double increment) {
        super(name, parent);

        this.minValue = minValue;
        this.maxValue = maxValue;
        this.increment = increment;

        setValue(defaultValue);
    }

    public NumberProperty(final String name, final String suffix, final double defaultValue, final double minValue, final double maxValue, final double increment) {
        this(name, defaultValue, minValue, maxValue, increment);
        this.suffix = suffix;
    }

    public NumberProperty(final String name, final String suffix, final ModuleMode<?> parent, final double defaultValue, final double minValue, final double maxValue, final double increment) {
        this(name, parent, defaultValue, minValue, maxValue, increment);
        this.suffix = suffix;
    }

    public double getIncrement() {
        return increment;
    }

    public double getMaxValue() {
        return maxValue;
    }

    public double getMinValue() {
        return minValue;
    }

    public String getSuffix() {
        return suffix;
    }

    @Override
    public void setValue(@NotNull Double value) {
        super.setValue(MathUtility.roundAndClamp(value, this.minValue, this.maxValue, this.increment).doubleValue());
    }

    @Override
    public PropertyPanel<?> createClickGUIComponent() {
        return new NumberPropertyComponent(this);
    }

    @Override
    public void applyValue(Object propertyValue) {
        setValue(Double.parseDouble(String.valueOf(propertyValue)));
    }
}

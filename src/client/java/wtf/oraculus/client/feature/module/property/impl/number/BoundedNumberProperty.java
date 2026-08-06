package wtf.oraculus.client.feature.module.property.impl.number;

import com.google.gson.internal.LinkedTreeMap;
import com.ibm.icu.impl.Pair;
import wtf.oraculus.client.feature.module.property.Property;
import wtf.oraculus.client.feature.module.property.impl.mode.ModuleMode;
import wtf.oraculus.client.screen.click.dropdown.panel.property.PropertyPanel;
import wtf.oraculus.client.screen.click.dropdown.panel.property.impl.BoundedNumberPropertyComponent;
import wtf.oraculus.utility.misc.math.MathUtility;
import wtf.oraculus.utility.misc.math.RandomUtility;

public final class BoundedNumberProperty extends Property<Pair<Double, Double>> {

    private final double minValue, maxValue, increment;
    private String suffix;

    public BoundedNumberProperty(final String name, final double defaultValue, final double defaultValue2, final double minValue, final double maxValue, final double increment) {
        super(name);

        this.minValue = minValue;
        this.maxValue = maxValue;
        this.increment = increment;

        setValue(Pair.of(defaultValue, defaultValue2));
    }

    public BoundedNumberProperty(final String name, final ModuleMode<?> parent, final double defaultValue, final double defaultValue2, final double minValue, final double maxValue, final double increment) {
        super(name, parent);

        this.minValue = minValue;
        this.maxValue = maxValue;
        this.increment = increment;

        setValue(Pair.of(defaultValue, defaultValue2));
    }

    public BoundedNumberProperty(final String name, final String suffix, final double defaultValue, final double defaultValue2, final double minValue, final double maxValue, final double increment) {
        this(name, defaultValue, defaultValue2, minValue, maxValue, increment);
        this.suffix = suffix;
    }

    public BoundedNumberProperty(final String name, final ModuleMode<?> parent, final String suffix, final double defaultValue, final double defaultValue2, final double minValue, final double maxValue, final double increment) {
        this(name, parent, defaultValue, defaultValue2, minValue, maxValue, increment);
        this.suffix = suffix;
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

    public Double getMidpoint() {
        return (getValue().first + getValue().second) / 2;
    }

    public Double getRandomValue() {
        return RandomUtility.getRandomDouble(getValue().first, getValue().second);
    }

    @Override
    public void setValue(Pair<Double, Double> value) {
        super.setValue(Pair.of(
                MathUtility.roundAndClamp(value.first, this.minValue, this.maxValue, this.increment).doubleValue(),
                MathUtility.roundAndClamp(value.second, this.minValue, this.maxValue, this.increment).doubleValue()
        ));
    }

    @Override
    public void applyValue(Object propertyValue) {
        if (propertyValue instanceof LinkedTreeMap<?,?> jsonProperty) {
            if (jsonProperty.isEmpty()) {
                return;
            }

            final Object value1 = jsonProperty.get("x");
            final Object value2 = jsonProperty.get("y");

            if (value1 instanceof Double val1 && value2 instanceof Double val2) {
                final double boundedValue1 = MathUtility.roundAndClamp(val1, minValue, maxValue, increment).doubleValue();
                final double boundedValue2 = MathUtility.roundAndClamp(val2, minValue, maxValue, increment).doubleValue();
                setValue(Pair.of(boundedValue1, boundedValue2));
            }
        }
    }

    @Override
    public PropertyPanel<?> createClickGUIComponent() {
        return new BoundedNumberPropertyComponent(this);
    }
}

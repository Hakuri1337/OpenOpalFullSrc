package wtf.oraculus.client.feature.module.property.impl.mode;

import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.property.Property;
import wtf.oraculus.client.screen.click.dropdown.panel.property.PropertyPanel;
import wtf.oraculus.client.screen.click.dropdown.panel.property.impl.ModePropertyComponent;

import java.util.HashMap;
import java.util.Map;

public final class ModeProperty<T extends Enum<T>> extends Property<T> {

    private final T[] values;
    private final Map<String, T> aliases = new HashMap<>();
    private Module module;

    private boolean theme;

    public ModeProperty(final String name, final T value) {
        super(name);
        setValue(value);
        this.values = getEnumConstants();
    }

    public ModeProperty(final String name, final T value, final T[] values) {
        super(name);
        setValue(value);
        this.values = values;
    }

    public ModeProperty(final String name, final T value, final boolean theme) {
        super(name);
        setValue(value);
        this.values = getEnumConstants();

        this.theme = theme;
    }

    public ModeProperty(final String name, final T value, final T[] values, final boolean theme) {
        super(name);
        setValue(value);
        this.values = values;
        this.theme = theme;
    }

    public ModeProperty(final String name, final ModuleMode<?> parent, final T value) {
        super(name, parent);
        setValue(value);
        this.values = getEnumConstants();
    }

    public ModeProperty(final String name, final Module module, final T value) {
        super(name);
        setValue(value);
        this.values = getEnumConstants();
        this.module = module;
        module.setModeProperty(this);
    }

    @SuppressWarnings("unchecked")
    private T[] getEnumConstants() {
        return (T[]) getValue().getClass().getEnumConstants();
    }

    public T[] getValues() {
        return values;
    }

    public ModeProperty<T> alias(final String legacyValue, final T value) {
        aliases.put(normalize(legacyValue), value);
        return this;
    }

    public void setValueOrdinal(final int ordinal) {
        T selected = null;
        for (final T possibleValue : values) {
            if (possibleValue != null && possibleValue.ordinal() == ordinal) {
                selected = possibleValue;
                break;
            }
        }
        if (selected == null) {
            return;
        }
        if (module != null && module.isEnabled()) {
            module.getModuleModes().forEach(ModuleMode::onDisable);
        }
        setValue(selected);
        if (module != null) {
            for (final ModuleMode<?> mode : module.getModuleModes()) {
                if (mode.getEnumValue().ordinal() == ordinal && module.isEnabled()) {
                    mode.onEnable();
                    break;
                }
            }
        }
    }

    public void cycle(final boolean forwards) {
        if (values.length == 0) {
            return;
        }
        int currentIndex = 0;
        for (int index = 0; index < values.length; index++) {
            if (values[index] == getValue()) {
                currentIndex = index;
                break;
            }
        }
        for (int offset = 1; offset <= values.length; offset++) {
            final int delta = forwards ? offset : -offset;
            final int nextIndex = Math.floorMod(currentIndex + delta, values.length);
            if (values[nextIndex] != null) {
                setValueOrdinal(values[nextIndex].ordinal());
                return;
            }
        }
    }

    public boolean isTheme() {
        return theme;
    }

    public boolean is(final T value) {
        return getValue() == value;
    }

    @Override
    public PropertyPanel<?> createClickGUIComponent() {
        return new ModePropertyComponent(this);
    }

    @Override
    public void applyValue(Object propertyValue) {
        if (propertyValue instanceof Number numberValue) {
            setValueOrdinal(numberValue.intValue());
            return;
        }

        if (propertyValue instanceof String valueString) {
            final String normalizedValue = normalize(valueString);
            for (T possibleValue : values) {
                if (normalize(possibleValue.name()).equals(normalizedValue)
                        || normalize(possibleValue.toString()).equals(normalizedValue)) {
                    setValueOrdinal(possibleValue.ordinal());
                    return;
                }
            }

            final T aliasedValue = aliases.get(normalizedValue);
            if (aliasedValue != null) {
                setValueOrdinal(aliasedValue.ordinal());
            }
        }
    }

    private static String normalize(final String value) {
        if (value == null) {
            return "";
        }

        final StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            final char character = value.charAt(i);
            if (Character.isLetterOrDigit(character)) {
                builder.append(Character.toLowerCase(character));
            }
        }
        return builder.toString();
    }
}

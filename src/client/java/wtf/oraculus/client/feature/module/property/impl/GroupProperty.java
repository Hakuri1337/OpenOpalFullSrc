package wtf.oraculus.client.feature.module.property.impl;

import com.google.gson.internal.LinkedTreeMap;
import wtf.oraculus.client.feature.module.property.IPropertyListProvider;
import wtf.oraculus.client.feature.module.property.Property;
import wtf.oraculus.client.screen.click.dropdown.panel.property.PropertyPanel;
import wtf.oraculus.client.screen.click.dropdown.panel.property.impl.GroupPropertyComponent;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class GroupProperty extends Property<List<Property<?>>> implements IPropertyListProvider {
    private final BooleanSupplier enabledSupplier;

    public GroupProperty(final String name, final BooleanSupplier enabledSupplier, final Property<?>... children) {
        super(name);
        this.enabledSupplier = enabledSupplier;
        setValue(Arrays.stream(children).filter(Objects::nonNull).toList());
    }

    public GroupProperty(final String name, final Property<?>... children) {
        this(name, null, children);
    }

    @Override
    public List<Property<?>> getPropertyList() {
        return getValue();
    }

    @Override
    public PropertyPanel<?> createClickGUIComponent() {
        return new GroupPropertyComponent(this);
    }

    public boolean isEnabled() {
        return this.enabledSupplier != null && this.enabledSupplier.getAsBoolean();
    }

    @Override
    public void applyValue(Object propertyValue) {
        if (propertyValue instanceof List<?> groupValues) {
            for (Object jsonGroupPropObj : groupValues) {
                final LinkedTreeMap<?, ?> jsonGroupProp = (LinkedTreeMap<?, ?>) jsonGroupPropObj;
                final String groupName = (String) jsonGroupProp.get("name");
                final Object groupValue = jsonGroupProp.get("value");

                for (Property<?> clientGroupProp : getPropertyList()) {
                    if (groupName.equals(clientGroupProp.getName())) {
                        clientGroupProp.applyValue(groupValue);
                    }
                }
            }
        }
    }
}

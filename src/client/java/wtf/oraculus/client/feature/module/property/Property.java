package wtf.oraculus.client.feature.module.property;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.mojang.logging.LogUtils;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.property.impl.mode.ModeProperty;
import wtf.oraculus.client.feature.module.property.impl.mode.ModuleMode;
import wtf.oraculus.client.screen.click.dropdown.panel.property.PropertyPanel;
import wtf.oraculus.event.EventDispatcher;
import wtf.oraculus.event.impl.client.PropertyUpdateEvent;
import wtf.oraculus.utility.data.SaveUtility;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public class Property<T> {

    private final String name;

    @Expose
    @SerializedName("name")
    private String id;

    @Expose
    @SerializedName("value")
    private T value;

    private boolean focused;

    private BooleanSupplier hiddenSupplier;

    protected Property(final String name) {
        this.name = name;
        this.id = name;
    }

    protected Property(final String name, final ModuleMode<?> parent) {
        this.name = name;
        this.id = name;
        final Module module = parent.getModule();
        final ModeProperty<?> modeProperty = module.getModeProperty();
        if (modeProperty == null) {
            LogUtils.getLogger().error(module.getName() + " does not have a mode property.");
            return;
        }
        for (final Enum<?> e : modeProperty.getValues()) {
            if (e.equals(parent.getEnumValue())) {
//                addParent(modeProperty, m -> m.getValue().equals(parent.getEnumValue()));
                module.addProperties(this);
                break;
            }
        }
    }

    @SuppressWarnings("unchecked")
    public final <R extends Property<T>> R hideIf(BooleanSupplier hiddenSupplier) {
        this.hiddenSupplier = hiddenSupplier;
        return (R) this;
    }

    public final String getName() {
        return name;
    }

    public final String getId() {
        return id;
    }

    @SuppressWarnings("unchecked")
    public final <R extends Property<T>> R id(String id) {
        this.id = id;
        return (R) this;
    }

    public T getValue() {
        return value;
    }

    public void setValue(final T value) {
        final T prevValue = this.value;
        this.value = value;
        EventDispatcher.dispatch(new PropertyUpdateEvent(this));
        this.callChangeListeners(prevValue, value);
        SaveUtility.autoSaveDefaultConfig();
    }

    public final boolean isHidden() {
        return this.hiddenSupplier != null && this.hiddenSupplier.getAsBoolean();
    }

    public PropertyPanel<?> createClickGUIComponent() {
        return null;
    }

    private List<IPropertyUpdateListener<T>> propertyUpdateListeners;

    @SuppressWarnings("unchecked")
    public final <P extends Property<T>> P addUpdateListener(IPropertyUpdateListener<T> listener) {
        if (this.propertyUpdateListeners == null) {
            this.propertyUpdateListeners = new ArrayList<>(1);
        }
        this.propertyUpdateListeners.add(listener);
        return (P) this;
    }

    @SuppressWarnings("unchecked")
    public final <P extends Property<T>> P addUpdateListener(Runnable listener) {
        if (this.propertyUpdateListeners == null) {
            this.propertyUpdateListeners = new ArrayList<>(1);
        }
        this.propertyUpdateListeners.add((p, v) -> listener.run());
        return (P) this;
    }

    public final void callChangeListeners(T prevValue, T value) {
        if (this.propertyUpdateListeners != null) {
            this.propertyUpdateListeners.forEach(l -> l.onChange(prevValue, value));
        }
    }

    public final boolean isFocused() {
        return focused;
    }

    public final void setFocused(final boolean focused) {
        this.focused = focused;
    }

    public void applyValue(final Object propertyValue) {
    }
}

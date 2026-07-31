package wtf.oraculus.client.feature.module;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.binding.IBindable;
import wtf.oraculus.client.feature.helper.impl.render.ScreenPositionManager;
import wtf.oraculus.client.feature.module.impl.visual.overlay.OverlayModule;
import wtf.oraculus.client.feature.module.impl.visual.overlay.impl.notifications.NotificationSettings;
import wtf.oraculus.client.feature.module.property.IPropertyListProvider;
import wtf.oraculus.client.feature.module.property.Property;
import wtf.oraculus.client.feature.module.property.impl.GroupProperty;
import wtf.oraculus.client.feature.module.property.impl.ScreenPositionProperty;
import wtf.oraculus.client.feature.module.property.impl.mode.ModeProperty;
import wtf.oraculus.client.feature.module.property.impl.mode.ModuleMode;
import wtf.oraculus.client.notification.NotificationType;
import wtf.oraculus.event.EventDispatcher;
import wtf.oraculus.event.impl.client.ModuleToggleEvent;
import wtf.oraculus.event.subscriber.IEventSubscriber;
import wtf.oraculus.utility.data.SaveUtility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Module implements IBindable, IPropertyListProvider, IEventSubscriber {

    private final String name, description;

    @Expose
    @SerializedName("name")
    private final String id;

    private final ModuleCategory category;

    @Expose
    @SerializedName("enabled")
    private boolean enabled;

    @Expose
    @SerializedName("visible")
    private boolean visible = true;

    @Expose
    @SerializedName("properties")
    private final List<Property<?>> propertyList = new ArrayList<>();

    private final List<ModuleMode<?>> moduleModeList = new ArrayList<>();
    private ModeProperty<?> modeProperty;

    private boolean expanded;
    private int propertyIndex;

    protected Module(final String name, final String description, final ModuleCategory category) {
        this.name = name;
        this.id = name.toLowerCase().replace(' ', '_');
        this.description = description;
        this.category = category;
    }

    public final void setEnabled(final boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        final ModuleToggleEvent event = new ModuleToggleEvent(this, enabled);
        EventDispatcher.dispatch(event);
        if (event.isCancelled()) {
            return;
        }
        this.enabled = enabled;
        if (enabled) {
            this.onEnable();
            // Auto-show deprecated modules in ClickGUI when enabled
            if (this instanceof DeprecatedModule) {
                this.visible = true;
            }
        } else {
            this.onDisable();
        }
        SaveUtility.autoSaveDefaultConfig();
    }

    public final void toggle() {
        this.setEnabled(!this.isEnabled());

        final OverlayModule overlayModule = OraculusClient.getInstance().getModuleRepository().getModule(OverlayModule.class);
        if (overlayModule.isEnabled()) {
            final NotificationSettings notificationSettings = overlayModule.getNotifications().getSettings();
            if (notificationSettings.isEnabled() && notificationSettings.isModuleToggleNotifications()) {
                OraculusClient.getInstance().getNotificationManager()
                        .builder(NotificationType.INFO)
                        .duration(1000)
                        .title(this.name)
                        .description("Module " + (this.enabled ? "enabled." : "disabled."))
                        .buildAndPublish();
            }
        }
    }

    protected void onEnable() {
        if (getActiveMode() != null)
            getActiveMode().onEnable();
    }

    protected void onDisable() {
        if (getActiveMode() != null)
            getActiveMode().onDisable();
    }

    public final String getName() {
        return name;
    }

    public final String getId() {
        return id;
    }

    public final String getDescription() {
        return description;
    }

    public final ModuleCategory getCategory() {
        return category;
    }

    public final boolean isEnabled() {
        return enabled;
    }

    public final boolean isVisible() {
        return visible;
    }

    public final void setVisible(boolean visible) {
        this.visible = visible;
        SaveUtility.autoSaveDefaultConfig();
    }

    public final void addProperties(Property<?>... properties) {
        for (final Property<?> property : properties) {
            if (property == null) continue;
            propertyList.add(property);
            if (property instanceof ScreenPositionProperty screenPositionProperty) {
                ScreenPositionManager.getInstance().register(this, screenPositionProperty);
            } else if (property instanceof GroupProperty group) {
                for (final Property<?> groupProperty : group.getPropertyList()) {
                    if (groupProperty instanceof ScreenPositionProperty screenPositionProperty) {
                        ScreenPositionManager.getInstance().register(this, screenPositionProperty);
                    }
                }
            }
        }
    }

    @SafeVarargs
    public final <T extends Module> void addModuleModes(final ModeProperty<?> modeProperty, final ModuleMode<T>... modes) {
        this.modeProperty = modeProperty;
        Collections.addAll(moduleModeList, modes);
    }

    public final List<ModuleMode<?>> getModuleModes() {
        return moduleModeList;
    }

    public final ModuleMode<?> getActiveMode() {
        return moduleModeList.stream().filter(m -> m.getEnumValue().equals(modeProperty.getValue())).findFirst().orElse(null);
    }

    public final ModeProperty<?> getModeProperty() {
        return modeProperty;
    }

    public final void setModeProperty(ModeProperty<?> modeProperty) {
        this.modeProperty = modeProperty;
    }

    public String getSuffix() {
        return null;
    }

    public final boolean isExpanded() {
        return expanded;
    }

    public final int getPropertyIndex() {
        return propertyIndex;
    }

    public final void setPropertyIndex(final int propertyIndex) {
        this.propertyIndex = propertyIndex;
    }

    public final void setExpanded(final boolean expanded) {
        this.expanded = expanded;
    }

    @Override
    public final List<Property<?>> getPropertyList() {
        return propertyList;
    }

    @Override
    public final void onBindingInteraction() {
        toggle();
    }

    @Override
    public final boolean isHandlingEvents() {
        return enabled;
    }

}

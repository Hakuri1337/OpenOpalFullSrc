package wtf.oraculus.client.feature.module.impl.visual.overlay.impl.notifications;

import wtf.oraculus.client.feature.module.impl.visual.overlay.OverlayModule;
import wtf.oraculus.client.feature.module.property.impl.GroupProperty;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;

public final class NotificationSettings {

    private final BooleanProperty enabled;
    private final BooleanProperty moduleToggleNotifications;

    NotificationSettings(final OverlayModule module) {
        this.enabled = new BooleanProperty("Enabled", true);
        this.moduleToggleNotifications = new BooleanProperty("On module toggle", false);
        module.addProperties(new GroupProperty("Notifications", moduleToggleNotifications));
    }

    public boolean isEnabled() {
        return enabled.getValue();
    }

    public boolean isModuleToggleNotifications() {
        return moduleToggleNotifications.getValue();
    }

}

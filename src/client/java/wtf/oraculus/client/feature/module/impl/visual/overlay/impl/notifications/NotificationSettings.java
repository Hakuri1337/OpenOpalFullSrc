package wtf.oraculus.client.feature.module.impl.visual.overlay.impl.notifications;

import wtf.oraculus.client.feature.module.impl.visual.overlay.OverlayModule;
import wtf.oraculus.client.feature.module.impl.visual.overlay.LiquidGlassV2Settings;
import wtf.oraculus.client.feature.module.property.impl.GroupProperty;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.mode.ModeProperty;

public final class NotificationSettings {

    private final BooleanProperty enabled;
    private final BooleanProperty moduleToggleNotifications;
    private final ModeProperty<DisplayMode> displayMode;
    private final BooleanProperty colorOutline;
    private final LiquidGlassV2Settings liquidGlassV2;

    NotificationSettings(final OverlayModule module) {
        this.enabled = new BooleanProperty("Enabled", true);
        this.moduleToggleNotifications = new BooleanProperty("On module toggle", false);
        this.displayMode = new ModeProperty<>("Mode", DisplayMode.LEGACY);
        this.colorOutline = new BooleanProperty("Color Outline", true).hideIf(() -> !this.displayMode.is(DisplayMode.ISLAND));
        this.liquidGlassV2 = new LiquidGlassV2Settings(
                "legacy-notification", "legacy-liquid-glass-v2",
                () -> this.enabled.getValue() && this.displayMode.is(DisplayMode.LEGACY)
        );
        module.addProperties(new GroupProperty("Notifications", this.liquidGlassV2.after(
                enabled, displayMode, colorOutline, moduleToggleNotifications
        )));
    }

    public boolean isEnabled() {
        return enabled.getValue();
    }

    public boolean isModuleToggleNotifications() {
        return moduleToggleNotifications.getValue();
    }

    public boolean isIsland() {
        return this.displayMode.is(DisplayMode.ISLAND);
    }

    public boolean isLiquidGlassV2() {
        return this.liquidGlassV2.isEnabled();
    }

    public LiquidGlassV2Settings getLiquidGlassV2Settings() {
        return this.liquidGlassV2;
    }

    public boolean showIslandIconBackground() {
        return this.colorOutline.getValue();
    }

    public enum DisplayMode {
        LEGACY("Legacy"),
        ISLAND("Island");

        private final String name;

        DisplayMode(final String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

}

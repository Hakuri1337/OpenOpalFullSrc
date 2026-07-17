package wtf.opal.client.feature.module.impl.visual.overlay.impl.targetinfo;

import wtf.opal.client.feature.helper.impl.render.ScaleProperty;
import wtf.opal.client.feature.module.impl.visual.overlay.OverlayModule;
import wtf.opal.client.feature.module.property.impl.GroupProperty;
import wtf.opal.client.feature.module.property.impl.ScreenPositionProperty;
import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.client.feature.module.property.impl.mode.ModeProperty;

public final class TargetInfoSettings {

    private final BooleanProperty enabled;
    private final ModeProperty<DisplayMode> displayMode;
    private final ScreenPositionProperty screenPosition;
    private final ScaleProperty scale;

    TargetInfoSettings(final OverlayModule module) {
        this.enabled = new BooleanProperty("Enabled", true);
        this.displayMode = new ModeProperty<>("Mode", DisplayMode.PANEL);
        this.screenPosition = new ScreenPositionProperty("Screen Position", 0.43F, 0.65F);
        this.scale = ScaleProperty.newNVGElement();

        this.screenPosition.hideIf(this::isDynamicIsland);
        this.scale.get().hideIf(this::isDynamicIsland);

        module.addProperties(new GroupProperty("Target information", this.enabled, this.displayMode, this.screenPosition, this.scale.get()));
    }

    public boolean isEnabled() {
        return this.enabled.getValue();
    }

    public boolean isDynamicIsland() {
        return this.displayMode.getValue() == DisplayMode.DYNAMIC_ISLAND;
    }

    public ScreenPositionProperty getScreenPosition() {
        return this.screenPosition;
    }

    public float getScale() {
        return scale.getScale();
    }

    public enum DisplayMode {
        PANEL("Panel"),
        DYNAMIC_ISLAND("Dynamic Island");

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

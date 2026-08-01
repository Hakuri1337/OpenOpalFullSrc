package wtf.oraculus.client.feature.module.impl.visual.overlay.impl.targetinfo;

import wtf.oraculus.client.feature.helper.impl.render.ScaleProperty;
import wtf.oraculus.client.feature.module.impl.visual.overlay.OverlayModule;
import wtf.oraculus.client.feature.module.property.impl.GroupProperty;
import wtf.oraculus.client.feature.module.property.impl.ScreenPositionProperty;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.mode.ModeProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;

public final class TargetInfoSettings {

    private final BooleanProperty enabled;
    private final ModeProperty<DisplayMode> displayMode;
    private final ScreenPositionProperty screenPosition;
    private final ScaleProperty scale;
    private final NumberProperty backgroundOpacity;
    private final BooleanProperty blur;
    private final BooleanProperty shadow;
    private final BooleanProperty blackOverlay;

    TargetInfoSettings(final OverlayModule module) {
        this.enabled = new BooleanProperty("Enabled", true);
        this.displayMode = new ModeProperty<>("Mode", DisplayMode.PANEL);
        this.screenPosition = new ScreenPositionProperty("Screen Position", 0.43F, 0.65F);
        this.scale = ScaleProperty.newNVGElement();
        this.backgroundOpacity = new NumberProperty("Background opacity", 0.72, 0, 1, 0.01);
        this.blur = new BooleanProperty("Blur", true);
        this.shadow = new BooleanProperty("Shadow", true);
        this.blackOverlay = new BooleanProperty("Black overlay", true);

        this.screenPosition.hideIf(this::isDynamicIsland);
        this.scale.get().hideIf(this::isDynamicIsland);
        this.backgroundOpacity.hideIf(() -> !this.isCompact());
        this.blur.hideIf(() -> !this.isCompact());
        this.shadow.hideIf(() -> !this.isCompact());
        this.blackOverlay.hideIf(() -> !this.isGay());

        module.addProperties(new GroupProperty("Target information", this.enabled, this.displayMode, this.screenPosition,
                this.scale.get(), this.backgroundOpacity, this.blur, this.shadow, this.blackOverlay));
    }

    public boolean isEnabled() {
        return this.enabled.getValue();
    }

    public boolean isDynamicIsland() {
        return this.displayMode.getValue() == DisplayMode.DYNAMIC_ISLAND;
    }

    public boolean isCompact() {
        return this.displayMode.getValue() == DisplayMode.COMPACT;
    }

    public boolean isGay() {
        return this.displayMode.getValue() == DisplayMode.GAY;
    }

    public ScreenPositionProperty getScreenPosition() {
        return this.screenPosition;
    }

    public float getScale() {
        return scale.getScale();
    }

    public float getBackgroundOpacity() {
        return backgroundOpacity.getValue().floatValue();
    }

    public boolean isBlur() {
        return blur.getValue();
    }

    public boolean isShadow() {
        return shadow.getValue();
    }

    public boolean isBlackOverlay() {
        return this.blackOverlay.getValue();
    }

    public enum DisplayMode {
        PANEL("Panel"),
        COMPACT("Compact"),
        GAY("Gay"),
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

package wtf.oraculus.client.feature.module.impl.visual.overlay.impl.targetinfo;

import wtf.oraculus.client.feature.helper.impl.render.ScaleProperty;
import wtf.oraculus.client.edition.EditionBuildInfo;
import wtf.oraculus.client.feature.module.impl.visual.overlay.LiquidGlassV2Settings;
import wtf.oraculus.client.feature.module.impl.visual.overlay.OverlayModule;
import wtf.oraculus.client.feature.module.property.Property;
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
    private final LiquidGlassV2Settings liquidGlassV2;
    private final NumberProperty rvnBackgroundCornerRadius;
    private final NumberProperty rvnLiquidGlassCornerRadius;
    private final ModeProperty<RvnHealthBarMode> rvnHealthBarMode;
    private final ModeProperty<RvnFontMode> rvnFontMode;
    private final BooleanProperty rvnOutline;
    private final LiquidGlassV2Settings rvnLiquidGlassV2;
    private final LiquidGlassV2Settings liquidGlassModeV2;

    TargetInfoSettings(final OverlayModule module) {
        this.enabled = new BooleanProperty("Enabled", true);
        this.displayMode = new ModeProperty<>("Mode", DisplayMode.PANEL, this.getAvailableDisplayModes());
        this.screenPosition = new ScreenPositionProperty("Screen Position", 0.43F, 0.65F);
        this.scale = ScaleProperty.newNVGElement();
        this.backgroundOpacity = new NumberProperty("Background opacity", 0.72, 0, 1, 0.01);
        this.blur = new BooleanProperty("Blur", true);
        this.shadow = new BooleanProperty("Shadow", true);
        this.blackOverlay = new BooleanProperty("Black overlay", true);
        this.liquidGlassV2 = new LiquidGlassV2Settings(
                "panel-target-hud", "panel-target-hud-liquid-glass-v2",
                () -> this.enabled.getValue() && this.isPanel()
        );
        this.rvnBackgroundCornerRadius = new NumberProperty("Rvn Background Corner Radius", 5, 0, 16, 0.25)
                .hideIf(() -> !this.isRvn() || this.isRvnLiquidGlassV2())
                .id("rvn-background-corner-radius");
        this.rvnLiquidGlassCornerRadius = new NumberProperty("Rvn LiquidGlass Corner Radius", 5, 0, 16, 0.25)
                .hideIf(() -> !this.isRvn() || !this.isRvnLiquidGlassV2())
                .id("rvn-liquid-glass-corner-radius");
        this.rvnHealthBarMode = new ModeProperty<>("Rvn Health Bar", RvnHealthBarMode.HEALTH)
                .hideIf(() -> !this.isRvn())
                .id("rvn-health-bar-mode");
        this.rvnFontMode = new ModeProperty<>("Rvn Font", RvnFontMode.ORACULUS)
                .hideIf(() -> !this.isRvn())
                .id("rvn-font-mode");
        this.rvnOutline = new BooleanProperty("Outline", false)
                .hideIf(() -> !this.isRvn())
                .id("rvn-outline");
        this.rvnLiquidGlassV2 = new LiquidGlassV2Settings(
                "rvn-target-hud", "rvn-target-hud-liquid-glass-v2",
                () -> this.enabled.getValue() && this.isRvn()
        );
        this.liquidGlassModeV2 = new LiquidGlassV2Settings(
                "liquid-glass-target-hud", "liquid-glass-target-hud-liquid-glass-v2",
                () -> this.enabled.getValue() && this.isLiquidGlassMode()
        );

        this.screenPosition.hideIf(this::isDynamicIsland);
        this.scale.get().hideIf(this::isDynamicIsland);
        this.backgroundOpacity.hideIf(() -> !this.isCompact());
        this.blur.hideIf(() -> !this.isCompact());
        this.shadow.hideIf(() -> !this.isCompact());
        this.blackOverlay.hideIf(() -> !this.isGay());

        final Property<?>[] baseProperties = {
                this.enabled, this.displayMode, this.screenPosition,
                this.scale.get(), this.backgroundOpacity, this.blur, this.shadow, this.blackOverlay,
                this.rvnBackgroundCornerRadius, this.rvnLiquidGlassCornerRadius,
                this.rvnHealthBarMode, this.rvnFontMode, this.rvnOutline
        };
        module.addProperties(new GroupProperty("Target information", concat(
                baseProperties,
                this.liquidGlassV2.getProperties(),
                this.rvnLiquidGlassV2.getProperties(),
                this.liquidGlassModeV2.getProperties()
        )));
    }

    public boolean isEnabled() {
        return this.enabled.getValue();
    }

    public boolean isDynamicIsland() {
        return this.displayMode.getValue() == DisplayMode.DYNAMIC_ISLAND;
    }

    public boolean isPanel() {
        return this.displayMode.getValue() == DisplayMode.PANEL;
    }

    public boolean isCompact() {
        return this.displayMode.getValue() == DisplayMode.COMPACT;
    }

    public boolean isGay() {
        return this.displayMode.getValue() == DisplayMode.GAY;
    }

    public boolean isRvn() {
        return this.displayMode.getValue() == DisplayMode.RVN;
    }

    public boolean isLiquidGlassMode() {
        return this.displayMode.getValue() == DisplayMode.LIQUID_GLASS;
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

    public boolean isLiquidGlassV2() {
        return this.liquidGlassV2.isEnabled()
                || this.rvnLiquidGlassV2.isEnabled()
                || this.liquidGlassModeV2.isEnabled();
    }

    public LiquidGlassV2Settings getLiquidGlassV2Settings() {
        if (this.isRvn()) {
            return this.rvnLiquidGlassV2;
        }
        if (this.isLiquidGlassMode()) {
            return this.liquidGlassModeV2;
        }
        return this.liquidGlassV2;
    }

    public boolean isRvnLiquidGlassV2() {
        return this.rvnLiquidGlassV2.isEnabled();
    }

    public LiquidGlassV2Settings getRvnLiquidGlassV2Settings() {
        return this.rvnLiquidGlassV2;
    }

    public boolean isLiquidGlassModeV2() {
        return this.liquidGlassModeV2.isEnabled();
    }

    public LiquidGlassV2Settings getLiquidGlassModeV2Settings() {
        return this.liquidGlassModeV2;
    }

    public float getRvnBackgroundCornerRadius() {
        return this.rvnBackgroundCornerRadius.getValue().floatValue();
    }

    public float getRvnLiquidGlassCornerRadius() {
        return this.rvnLiquidGlassCornerRadius.getValue().floatValue();
    }

    public boolean isRvnHealthBarTheme() {
        return this.rvnHealthBarMode.is(RvnHealthBarMode.THEME);
    }

    public boolean isRvnFontVanilla() {
        return this.rvnFontMode.is(RvnFontMode.VANILLA);
    }

    public boolean isRvnOutline() {
        return this.rvnOutline.getValue();
    }

    private static Property<?>[] concat(final Property<?>[]... arrays) {
        int length = 0;
        for (final Property<?>[] array : arrays) {
            length += array.length;
        }
        final Property<?>[] result = new Property<?>[length];
        int offset = 0;
        for (final Property<?>[] array : arrays) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }

    private DisplayMode[] getAvailableDisplayModes() {
        if (!EditionBuildInfo.isFree()) {
            return DisplayMode.values();
        }
        return new DisplayMode[]{
                DisplayMode.PANEL,
                DisplayMode.RVN,
                DisplayMode.DYNAMIC_ISLAND
        };
    }

    public enum DisplayMode {
        PANEL("Panel"),
        COMPACT("Compact"),
        GAY("Gay"),
        RVN("Rvn"),
        LIQUID_GLASS("LiquidGlass"),
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

    public enum RvnHealthBarMode {
        HEALTH("Health"),
        THEME("Theme");

        private final String name;

        RvnHealthBarMode(final String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

    public enum RvnFontMode {
        ORACULUS("Oraculus"),
        VANILLA("Vanilla");

        private final String name;

        RvnFontMode(final String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

}

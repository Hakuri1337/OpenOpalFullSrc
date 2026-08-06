package wtf.oraculus.client.feature.module.impl.visual.overlay.impl.lyrics;

import wtf.oraculus.client.feature.helper.impl.render.ScaleProperty;
import wtf.oraculus.client.feature.module.impl.visual.overlay.LiquidGlassV2Settings;
import wtf.oraculus.client.feature.module.impl.visual.overlay.OverlayModule;
import wtf.oraculus.client.feature.module.property.impl.GroupProperty;
import wtf.oraculus.client.feature.module.property.impl.ScreenPositionProperty;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;

public final class LyricsSettings {

    private final BooleanProperty enabled;
    private final ScreenPositionProperty screenPosition;
    private final ScaleProperty scale;
    private final NumberProperty backgroundOpacity;
    private final LiquidGlassV2Settings liquidGlassV2;

    LyricsSettings(final OverlayModule module) {
        this.enabled = new BooleanProperty("Enabled", false);
        this.screenPosition = new ScreenPositionProperty("Screen Position", 0.75F, 0.08F)
                .hideIf(() -> !this.enabled.getValue());
        this.scale = ScaleProperty.newNVGElement();
        this.scale.get().hideIf(() -> !this.enabled.getValue());
        this.backgroundOpacity = new NumberProperty("Background opacity", 0.5, 0, 1, 0.01)
                .hideIf(() -> !this.enabled.getValue()).id("lyrics-background-opacity");
        this.liquidGlassV2 = new LiquidGlassV2Settings(
                "lyrics", "lyrics-liquid-glass-v2", this.enabled::getValue
        );
        this.backgroundOpacity.hideIf(() -> !this.enabled.getValue() || this.liquidGlassV2.isEnabled());

        module.addProperties(new GroupProperty("Lyrics", this.liquidGlassV2.after(
                this.enabled, this.screenPosition, this.scale.get(), this.backgroundOpacity
        )));
    }

    public boolean isEnabled() {
        return this.enabled.getValue();
    }

    public ScreenPositionProperty getScreenPosition() {
        return this.screenPosition;
    }

    public float getScale() {
        return this.scale.getScale();
    }

    public float getBackgroundOpacity() {
        return this.backgroundOpacity.getValue().floatValue();
    }

    public boolean isLiquidGlassV2() {
        return this.liquidGlassV2.isEnabled();
    }

    public LiquidGlassV2Settings getLiquidGlassV2Settings() {
        return this.liquidGlassV2;
    }
}

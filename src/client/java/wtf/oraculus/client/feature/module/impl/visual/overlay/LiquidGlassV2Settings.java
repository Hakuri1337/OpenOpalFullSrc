package wtf.oraculus.client.feature.module.impl.visual.overlay;

import wtf.oraculus.client.feature.module.property.Property;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;

import java.util.function.BooleanSupplier;

public final class LiquidGlassV2Settings {

    private final BooleanSupplier available;
    private final BooleanProperty enabled;
    private final NumberProperty blurRadius;
    private final NumberProperty refraction;
    private final NumberProperty refractionWidth;
    private final NumberProperty dispersion;
    private final NumberProperty edgeGlow;
    private final NumberProperty edgeWidth;
    private final NumberProperty noise;
    private final NumberProperty brightness;

    public LiquidGlassV2Settings(
            final String idPrefix,
            final String enabledId,
            final BooleanSupplier available
    ) {
        this.available = available;
        this.enabled = new BooleanProperty("LiquidGlass V2", false)
                .hideIf(() -> !this.available.getAsBoolean())
                .id(enabledId);
        this.blurRadius = this.number(
                "Blur Radius", 0, 0, 12, 1, idPrefix, "blur-radius"
        );
        this.refraction = this.number(
                "Refraction", 2.2, 0, 5, 0.05, idPrefix, "refraction"
        );
        this.refractionWidth = this.number(
                "Refraction Width", 0.6, 0.05, 1, 0.05, idPrefix, "refraction-width"
        );
        this.dispersion = this.number(
                "Dispersion", 0.003, 0, 0.02, 0.0005, idPrefix, "dispersion"
        );
        this.edgeGlow = this.number(
                "Edge Glow", 0.75, 0, 2, 0.05, idPrefix, "edge-glow"
        );
        this.edgeWidth = this.number(
                "Edge Width", 0.3, 0.01, 0.75, 0.01, idPrefix, "edge-width"
        );
        this.noise = this.number(
                "Noise", 0.015, 0, 0.15, 0.005, idPrefix, "noise"
        );
        this.brightness = this.number(
                "Brightness", 1.1, 0.5, 2, 0.05, idPrefix, "brightness"
        );
    }

    private NumberProperty number(
            final String name,
            final double defaultValue,
            final double minimum,
            final double maximum,
            final double increment,
            final String idPrefix,
            final String id
    ) {
        final String propertyId = idPrefix.isEmpty()
                ? "liquid-glass-" + id
                : idPrefix + "-liquid-glass-" + id;
        return new NumberProperty(name, defaultValue, minimum, maximum, increment)
                .hideIf(() -> !this.isEnabled())
                .id(propertyId);
    }

    public Property<?>[] after(final Property<?>... leading) {
        final Property<?>[] settings = this.getProperties();
        final Property<?>[] properties = new Property<?>[leading.length + settings.length];
        System.arraycopy(leading, 0, properties, 0, leading.length);
        System.arraycopy(settings, 0, properties, leading.length, settings.length);
        return properties;
    }

    public Property<?>[] getProperties() {
        return new Property<?>[]{
                this.enabled,
                this.blurRadius,
                this.refraction,
                this.refractionWidth,
                this.dispersion,
                this.edgeGlow,
                this.edgeWidth,
                this.noise,
                this.brightness
        };
    }

    public boolean isEnabled() {
        return this.available.getAsBoolean() && this.enabled.getValue();
    }

    public int getBlurRadius() {
        return this.blurRadius.getValue().intValue();
    }

    public float getRefraction() {
        return this.refraction.getValue().floatValue();
    }

    public float getRefractionWidth() {
        return this.refractionWidth.getValue().floatValue();
    }

    public float getDispersion() {
        return this.dispersion.getValue().floatValue();
    }

    public float getEdgeGlow() {
        return this.edgeGlow.getValue().floatValue();
    }

    public float getEdgeWidth() {
        return this.edgeWidth.getValue().floatValue();
    }

    public float getNoise() {
        return this.noise.getValue().floatValue();
    }

    public float getBrightness() {
        return this.brightness.getValue().floatValue();
    }
}

package wtf.oraculus.client.feature.module.impl.visual.overlay.impl.client;

import wtf.oraculus.client.feature.helper.impl.render.ScaleProperty;
import wtf.oraculus.client.feature.module.impl.visual.overlay.OverlayModule;
import wtf.oraculus.client.feature.module.property.impl.GroupProperty;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.bool.MultipleBooleanProperty;

public final class ClientElementSettings {

    private final ScaleProperty scale;
    private final MultipleBooleanProperty options;
    private final BooleanProperty lowercase;

    ClientElementSettings(final OverlayModule module) {
        this.scale = ScaleProperty.newNVGElement();
        this.options = new MultipleBooleanProperty("Options",
                new BooleanProperty("Status effects", true),
                new BooleanProperty("FPS", true),
                new BooleanProperty("BPS", true),
                new BooleanProperty("XYZ", false)
        );
        this.lowercase = new BooleanProperty("Lowercase", true);
        module.addProperties(new GroupProperty("Client elements", scale.get(), options, lowercase));
    }

    public float getScale() {
        return scale.getScale();
    }

    public MultipleBooleanProperty getOptions() {
        return options;
    }

    public boolean isLowercase() {
        return lowercase.getValue();
    }

}

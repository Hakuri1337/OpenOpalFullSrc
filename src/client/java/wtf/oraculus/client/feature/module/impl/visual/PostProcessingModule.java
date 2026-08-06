package wtf.oraculus.client.feature.module.impl.visual;

import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.property.impl.GroupProperty;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;

public final class PostProcessingModule extends Module {

    private final BooleanProperty blur = new BooleanProperty("Enabled", true).id("blurEnabled");
    private final BooleanProperty bloom = new BooleanProperty("Enabled", true).id("bloomEnabled");

    private final NumberProperty blurRadius = new NumberProperty("Radius", 7, 1, 20, 1).id("blurRadius");
    private final NumberProperty bloomRadius = new NumberProperty("Radius", 7, 1, 20, 1).id("bloomRadius");

    public PostProcessingModule() {
        super("Post Processing", "Allows you to configure post processing effects.", ModuleCategory.VISUAL);
        setEnabled(true);
        addProperties(
                new GroupProperty("Blur", blur, blurRadius),
                new GroupProperty("Bloom", bloom, bloomRadius).hideIf(() -> !blur.getValue())
        );
    }

    public boolean isBlur() {
        return blur.getValue();
    }

    public boolean isBloom() {
        return bloom.getValue() && isBlur();
    }

    public int getBlurRadius() {
        return blurRadius.getValue().intValue();
    }

    public int getBloomRadius() {
        return bloomRadius.getValue().intValue();
    }

}

package wtf.oraculus.client.feature.module.impl.visual;

import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.client.renderer.motionblur.MotionBlurRenderer;

public final class MotionBlurModule extends Module {

    private final NumberProperty strength = new NumberProperty("Strength", 7.0D, 0.0D, 10.0D, 0.1D);

    public MotionBlurModule() {
        super("Motion Blur", "Blends the world with the preceding rendered frame.", ModuleCategory.VISUAL);
        this.addProperties(this.strength);
    }

    public float getStrength() {
        return this.strength.getValue().floatValue();
    }

    @Override
    protected void onEnable() {
        MotionBlurRenderer.invalidateHistory();
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        MotionBlurRenderer.invalidateHistory();
        super.onDisable();
    }
}

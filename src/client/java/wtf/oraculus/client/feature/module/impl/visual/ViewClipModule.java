package wtf.oraculus.client.feature.module.impl.visual;

import net.minecraft.client.option.Perspective;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.event.impl.render.RenderScreenEvent;
import wtf.oraculus.event.subscriber.Subscribe;

import static wtf.oraculus.client.Constants.mc;

/** Third-person camera clipping bypass with Naven's scale and entrance animation. */
public final class ViewClipModule extends Module {

    private final NumberProperty scale = new NumberProperty("Scale", 1.0D, 0.5D, 2.0D, 0.01D);
    private final BooleanProperty animation = new BooleanProperty("Animation", true);
    private final NumberProperty animationSpeed = new NumberProperty("Animation Speed", 0.3D, 0.01D, 0.5D, 0.01D)
            .hideIf(() -> !this.animation.getValue());
    private final BooleanProperty fixSkipTickUpdateAnimation = new BooleanProperty("Fix Skip Tick Update Animation", false);

    private Perspective lastPerspective;
    private float personViewAnimation = 100.0F;
    private long lastFrameTime = System.currentTimeMillis();

    public ViewClipModule() {
        super("ViewClip", "Allows the third-person camera to see through blocks.", ModuleCategory.VISUAL);
        this.addProperties(this.scale, this.animation, this.animationSpeed, this.fixSkipTickUpdateAnimation);
    }

    @Subscribe
    public void onRenderScreen(final RenderScreenEvent event) {
        final Perspective perspective = mc.options.getPerspective();
        if (this.lastPerspective != perspective) {
            this.lastPerspective = perspective;
            if (perspective == Perspective.FIRST_PERSON || perspective == Perspective.THIRD_PERSON_BACK) {
                this.personViewAnimation = 0.0F;
            }
        }

        final long now = System.currentTimeMillis();
        final long delta = Math.max(1L, now - this.lastFrameTime);
        this.lastFrameTime = now;
        final float target = this.animation.getValue() ? 100.0F : 100.0F;
        final float speed = this.animation.getValue() ? this.animationSpeed.getValue().floatValue() : 1.0F;
        final float amount = delta * Math.max(10.0F, Math.abs(this.personViewAnimation - target) * 40.0F) * speed / 1000.0F;
        this.personViewAnimation = Math.min(target, this.personViewAnimation + amount);
    }

    public double getCameraDistance(final double vanillaDistance) {
        final double animationScale = this.animation.getValue() ? this.personViewAnimation / 100.0D : 1.0D;
        return vanillaDistance * this.scale.getValue() * animationScale;
    }

    @Override
    protected void onEnable() {
        this.lastPerspective = null;
        this.personViewAnimation = 100.0F;
        this.lastFrameTime = System.currentTimeMillis();
        super.onEnable();
    }
}

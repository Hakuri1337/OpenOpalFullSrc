package wtf.oraculus.client.renderer.component;

import com.ibm.icu.impl.Pair;
import wtf.oraculus.client.renderer.NVGRenderer;
import wtf.oraculus.utility.misc.HoverUtility;
import wtf.oraculus.utility.render.ColorUtility;
import wtf.oraculus.utility.render.ScreenPosition;
import wtf.oraculus.utility.render.animation.Animation;
import wtf.oraculus.utility.render.animation.Easing;

import java.util.function.BooleanSupplier;

public final class ToggleSwitchComponent extends ScreenPosition {

    private Animation toggleAnimation;

    private final Runnable toggleAction;
    private final BooleanSupplier stateSupplier;

    private Pair<Integer, Integer> boxColors = Pair.of(-1, 0xff818582);

    public ToggleSwitchComponent(final Runnable toggleAction, final BooleanSupplier stateSupplier) {
        this.toggleAction = toggleAction;
        this.stateSupplier = stateSupplier;
    }

    public void reset() {
        this.toggleAnimation = null;
    }

    public void render(final float x, final float y, final float scale) {
        this.x = x;
        this.y = y;

        this.width = 20;
        this.height = 10;

        NVGRenderer.scale(scale, x, y, width, height, () -> {
            final float destination = this.stateSupplier.getAsBoolean() ? 1.0F : 0.0F;
            if (this.toggleAnimation == null) {
                this.toggleAnimation = new Animation(Easing.DECELERATE, 150);
                this.toggleAnimation.setValue(destination);
            } else {
                this.toggleAnimation.run(destination);
            }

            final int color1 = ColorUtility.interpolateColors(boxColors.second, boxColors.first, this.toggleAnimation.getValue());
            final int color2 = ColorUtility.darker(color1, 0.4F);

            NVGRenderer.roundedRectGradient(x, y, width, height, height / 2, color1, color2, 90);
            NVGRenderer.roundedRectGradient(x + 1 + (this.toggleAnimation.getValue() * 9.5F), y + 1f, height - 2f, height - 2f, (height - 2f) / 2, -1, ColorUtility.darker(-1, 0.1F), 90);
        });
    }

    public void setBoxColors(final Pair<Integer, Integer> boxColors) {
        this.boxColors = boxColors;
    }

    public void mouseClicked(final double mouseX, final double mouseY, final float scale) {
        if (HoverUtility.isHovering(x, y, width, height, mouseX, mouseY, scale)) {
            toggle();
        }
    }

    public void mouseClicked(final float x, final float y, final float width, final float height, final double mouseX, final double mouseY) {
        if (HoverUtility.isHovering(x, y, width, height, mouseX, mouseY)) {
            toggle();
        }
    }

    public void toggle() {
        if (toggleAction != null) {
            toggleAction.run();
        }
    }
}

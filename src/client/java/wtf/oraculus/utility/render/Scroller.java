package wtf.oraculus.utility.render;

import wtf.oraculus.utility.render.animation.Animation;
import wtf.oraculus.utility.render.animation.Easing;

public final class Scroller {

    private final Animation animation = new Animation(Easing.EASE_OUT_EXPO, 250);
    private float value;

    public Animation getAnimation() {
        return this.animation;
    }

    public void onScroll(float maxOffset) {
        this.value = Math.min(0, Math.max(-maxOffset, this.value));
        this.animation.run(this.value);
    }

    public void addScroll(double verticalScroll, float maxOffset) {
        this.value += (float) (verticalScroll * 50);

        // Prevent scrolling past the bottom
        this.value = Math.max(-maxOffset, this.value);

        // Prevent scrolling past the top
        this.value = Math.min(0, this.value);

        this.animation.run(this.value);
    }

    public void reset() {
        this.value = 0;
        this.animation.setValue(0);
    }

}

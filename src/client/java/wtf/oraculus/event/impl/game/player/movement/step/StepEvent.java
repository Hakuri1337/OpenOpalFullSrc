package wtf.oraculus.event.impl.game.player.movement.step;

public final class StepEvent {

    private float stepHeight;

    public StepEvent(final float stepHeight) {
        this.stepHeight = stepHeight;
    }

    public float getStepHeight() {
        return stepHeight;
    }

    public void setStepHeight(final float stepHeight) {
        this.stepHeight = stepHeight;
    }
}


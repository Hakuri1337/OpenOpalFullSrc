package wtf.oraculus.event.impl.game.input;

public final class MoveInputEvent {

    private float forward, sideways;

    private boolean jump, sneak;

    public MoveInputEvent(float forward, float sideways, boolean jump, boolean sneak) {
        this.forward = forward;
        this.sideways = sideways;
        this.jump = jump;
        this.sneak = sneak;
    }

    public float getForward() {
        return forward;
    }

    public void setJump(boolean jump) {
        this.jump = jump;
    }

    public boolean isJump() {
        return jump;
    }

    public void setForward(float forward) {
        this.forward = forward;
    }

    public float getSideways() {
        return sideways;
    }

    public void setSideways(float sideways) {
        this.sideways = sideways;
    }

    public boolean isSneak() {
        return sneak;
    }

    public void setSneak(boolean sneak) {
        this.sneak = sneak;
    }

}

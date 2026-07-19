package wtf.oraculus.event.impl.game.player.movement;

import wtf.oraculus.event.EventCancellable;

public final class SlowdownEvent extends EventCancellable {

    private float slowdown;

    public SlowdownEvent(final float slowdown) {
        this.slowdown = slowdown;
    }

    public void setSlowdown(float slowdown) {
        this.slowdown = slowdown;
    }

    public float getSlowdown() {
        return slowdown;
    }
}

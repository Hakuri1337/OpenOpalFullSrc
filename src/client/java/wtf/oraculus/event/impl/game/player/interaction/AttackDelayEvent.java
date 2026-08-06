package wtf.oraculus.event.impl.game.player.interaction;

public final class AttackDelayEvent {
    private int delay;

    public AttackDelayEvent(int delay) {
        this.delay = delay;
    }

    public int getDelay() {
        return delay;
    }

    public void setDelay(int delay) {
        this.delay = delay;
    }
}

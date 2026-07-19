package wtf.oraculus.event.impl.game;

public final class ScheduledExecutablesEvent {
    private final boolean tick;

    public ScheduledExecutablesEvent(final boolean tick) {
        this.tick = tick;
    }

    public boolean isTick() {
        return tick;
    }
}

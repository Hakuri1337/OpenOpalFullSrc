package wtf.oraculus.event;

public class EventCancellable {
    private boolean cancelled;

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled() {
        cancelled = true;
    }
}

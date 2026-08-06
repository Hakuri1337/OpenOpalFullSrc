package wtf.oraculus.utility.misc;

import net.minecraft.text.ClickEvent;

public final class RunnableClickEvent implements ClickEvent {

    private final Runnable runnable;

    public RunnableClickEvent(final Runnable runnable) {
        this.runnable = runnable;
    }

    public Runnable getRunnable() {
        return runnable;
    }

    @Override
    public Action getAction() {
        return null;
    }
}

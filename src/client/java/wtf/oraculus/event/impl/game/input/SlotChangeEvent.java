package wtf.oraculus.event.impl.game.input;

public final class SlotChangeEvent {

    private int slot;

    public SlotChangeEvent(final int slot) {
        this.slot = slot;
    }

    public int getSlot() {
        return slot;
    }
}

package wtf.oraculus.event.impl.game.player.movement;

public final class SprintEvent {
    private boolean canStartSprinting;

    public SprintEvent(boolean canStartSprinting) {
        this.canStartSprinting = canStartSprinting;
    }

    public boolean isCanStartSprinting() {
        return canStartSprinting;
    }

    public void setCanStartSprinting(boolean canStartSprinting) {
        this.canStartSprinting = canStartSprinting;
    }
}

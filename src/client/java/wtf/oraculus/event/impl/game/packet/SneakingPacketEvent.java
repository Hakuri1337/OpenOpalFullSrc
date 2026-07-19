package wtf.oraculus.event.impl.game.packet;

public final class
SneakingPacketEvent {
    private boolean sneaking;

    public SneakingPacketEvent(boolean sneaking) {
        this.sneaking = sneaking;
    }

    public boolean isSneaking() {
        return sneaking;
    }

    public void setSneaking(boolean sneaking) {
        this.sneaking = sneaking;
    }
}

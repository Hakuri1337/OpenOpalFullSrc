package wtf.oraculus.event.impl.game.player.movement;

public final class JumpingCooldownEvent {
    private int cooldown;

    public JumpingCooldownEvent(int cooldown) {
        this.cooldown = cooldown;
    }

    public int getCooldown() {
        return cooldown;
    }

    public void setCooldown(int cooldown) {
        this.cooldown = cooldown;
    }
}

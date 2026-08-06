package wtf.oraculus.event.impl.game.player.interaction;

import net.minecraft.entity.Entity;

public final class AttackEvent {

    private final Entity target;

    public AttackEvent(final Entity target) {
        this.target = target;
    }

    public Entity getTarget() {
        return target;
    }
}

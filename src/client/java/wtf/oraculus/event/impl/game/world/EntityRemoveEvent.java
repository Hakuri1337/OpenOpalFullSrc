package wtf.oraculus.event.impl.game.world;

import net.minecraft.entity.Entity;

public final class EntityRemoveEvent {
    private final Entity entity;
    private final boolean dead;

    public EntityRemoveEvent(final Entity entity, final boolean dead) {
        this.entity = entity;
        this.dead = dead;
    }

    public Entity getEntity() {
        return entity;
    }

    public boolean isDead() {
        return dead;
    }
}

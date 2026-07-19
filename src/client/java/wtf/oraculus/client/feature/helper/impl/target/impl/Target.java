package wtf.oraculus.client.feature.helper.impl.target.impl;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

public abstract class Target<T extends LivingEntity> {
    protected T entity;
    private boolean invalid;

    public Target(T entity) {
        this.entity = entity;
    }

    public void tick() {
    }

    public abstract boolean isMatchingFlags(final int flags);

    public T getEntity() {
        return entity;
    }

    public void setEntity(Entity entity) {
        //noinspection unchecked
        this.entity = (T) entity;
    }

    public int getEntityId() {
        return this.entity.getId();
    }

    public boolean isLocal() {
        return false;
    }

    public boolean isInvalid() {
        return invalid;
    }

    public void setInvalid() {
        this.invalid = true;
    }
}

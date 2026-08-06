package mixin;

import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import wtf.oraculus.duck.EntityRenderStateAccess;

@Mixin(EntityRenderState.class)
public final class EntityRenderStateMixin implements EntityRenderStateAccess {

    @Unique
    private Entity entity;

    private EntityRenderStateMixin() {
    }

    @Override
    public Entity oraculus$getEntity() {
        return entity;
    }

    @Override
    public void oraculus$setEntity(final Entity entity) {
        this.entity = entity;
    }
}

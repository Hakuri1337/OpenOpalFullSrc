package mixin;

import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import wtf.oraculus.duck.BipedEntityRenderStateAccess;

@Mixin(BipedEntityRenderState.class)
public final class BipedEntityRenderStateMixin implements BipedEntityRenderStateAccess {

    @Unique
    public LivingEntity entity;

    @Override
    public LivingEntity oraculus$getEntity() {
        return entity;
    }

    @Override
    public void oraculus$setEntity(final LivingEntity entity) {
        this.entity = entity;
    }
}

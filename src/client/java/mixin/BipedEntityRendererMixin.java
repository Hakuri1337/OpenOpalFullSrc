package mixin;

import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.oraculus.duck.BipedEntityRenderStateAccess;

@Mixin(BipedEntityRenderer.class)
public final class BipedEntityRendererMixin {

    @Inject(
            method = "updateBipedRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/state/ArmedEntityRenderState;updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/ArmedEntityRenderState;Lnet/minecraft/client/item/ItemModelManager;)V", shift = At.Shift.AFTER)
    )
    private static void updateEntityField(LivingEntity entity, BipedEntityRenderState state, float tickDelta, ItemModelManager itemModelResolver, CallbackInfo ci) {
        ((BipedEntityRenderStateAccess) state).oraculus$setEntity(entity);
    }

}

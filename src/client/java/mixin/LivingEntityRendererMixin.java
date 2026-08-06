package mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.helper.impl.LocalDataWatch;
import wtf.oraculus.client.feature.module.impl.visual.ChamsModule;
import wtf.oraculus.client.feature.module.impl.visual.NoHurtCameraModule;
import wtf.oraculus.client.feature.module.impl.visual.esp.ESPModule;

import static org.lwjgl.opengl.GL11.*;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>>
        extends EntityRenderer<T, S>
        implements FeatureRendererContext<S, M> {

    private LivingEntityRendererMixin(EntityRendererFactory.Context context) {
        super(context);
    }
// 10j3k chams are gone as entity rendering is now batched.
//    @WrapWithCondition(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V"))
//    private boolean render$render(M instance, MatrixStack matrixStack, VertexConsumer vertexConsumer, int light, int overlay, int color, S state, MatrixStack matrices, VertexConsumerProvider consumers, int i) {
//        final ChamsModule chamsModule = OraculusClient.getInstance().getModuleRepository().getModule(ChamsModule.class);
//
//        if (!chamsModule.isEnabled() || !chamsModule.isColorOverlay() || !(((EntityRenderStateAccess) state).oraculus$getEntity() instanceof PlayerEntity))
//            return true;
//
//        instance.render(matrixStack, vertexConsumer, light, overlay, chamsModule.getRGBAColor());
//
//        return false;
//    }

//    @ModifyReturnValue(method = "getRenderLayer", at = @At("RETURN"))
//    private RenderLayer removePlayerTexture(RenderLayer original, S state, boolean showBody, boolean translucent, boolean showOutline) {
//        final ChamsModule chamsModule = OraculusClient.getInstance().getModuleRepository().getModule(ChamsModule.class);
//
//        if (!chamsModule.isEnabled()
//                || !(((EntityRenderStateAccess) state).oraculus$getEntity() instanceof PlayerEntity)
//                || chamsModule.shouldKeepTextures()) {
//            return original;
//        }
//
//        return RenderLayer.getItemEntityTranslucentCull(Identifier.of("oraculus", "textures/blank.png"));
//    }

//    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("HEAD"), cancellable = true)
//    private void setPolygonStates(S state, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i, CallbackInfo info) {
//        Entity entity = ((EntityRenderStateAccess) state).oraculus$getEntity();
//        if (!(entity instanceof LivingEntity)) return;
//
//        final ChamsModule chamsModule = OraculusClient.getInstance().getModuleRepository().getModule(ChamsModule.class);
//
//        if (chamsModule.isEnabled()) {
//            glEnable(GL_POLYGON_OFFSET_FILL);
//            glPolygonOffset(1.0f, -1100000.0f);
//        }
//    }
//
//    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("TAIL"))
//    private void revertPolygonStates(S state, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i, CallbackInfo info) {
//        Entity entity = ((EntityRenderStateAccess) state).oraculus$getEntity();
//        if (!(entity instanceof LivingEntity)) return;
//
//        final ChamsModule chamsModule = OraculusClient.getInstance().getModuleRepository().getModule(ChamsModule.class);
//
//        if (chamsModule.isEnabled()) {
//            glPolygonOffset(1.0f, 1100000.0f);
//            glDisable(GL_POLYGON_OFFSET_FILL);
//        }
//    }

    @Inject(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
    private void hookHasLabel(T livingEntity, double d, CallbackInfoReturnable<Boolean> cir) {
        if (livingEntity instanceof PlayerEntity) {
            final ESPModule espModule = OraculusClient.getInstance().getModuleRepository().getModule(ESPModule.class);
            if (espModule.isEnabled() && espModule.getSettings().areNameTagsEnabled() && LocalDataWatch.getTargetList().hasTarget(livingEntity.getId())) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL")
    )
    private void hookUpdateRenderStateTail(T livingEntity, S livingEntityRenderState, float f, CallbackInfo ci) {
        if (livingEntity instanceof ClientPlayerEntity) {
            final NoHurtCameraModule noHurtCameraModule = OraculusClient.getInstance().getModuleRepository().getModule(NoHurtCameraModule.class);
            if (noHurtCameraModule.isEnabled() && noHurtCameraModule.isHideModelDamage()) {
                livingEntityRenderState.hurt = false;
            }
        }
    }
}

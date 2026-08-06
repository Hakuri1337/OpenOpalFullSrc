package wtf.oraculus.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import wtf.oraculus.duck.EntityRenderStateAccess;

@Mixin(EntityRenderManager.class)
//10j3k -- entity rendering is batched, this is useless
public abstract class EntityRenderDispatcherMixin {

    @Shadow
    public abstract <S extends EntityRenderState> EntityRenderer<?, ? super S> getRenderer(S state);

    private EntityRenderDispatcherMixin() {
    }

//    @ModifyExpressionValue(method = "render", at = @At(value = "HEAD"))
//    private <E extends Entity, S extends EntityRenderState> S updateEntityInState(S renderState, CameraRenderState cameraRenderState, double d, double e, double f, MatrixStack matrixStack, OrderedRenderCommandQueue orderedRenderCommandQueue) {
////        ((EntityRenderStateAccess) renderState).oraculus$setEntity(this.getRenderer(renderState).getEntity());
//        return renderState;
//    }

}

package wtf.opal.mixin;

import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.module.impl.visual.LowFireModule;

@Mixin(InGameOverlayRenderer.class)
public final class LowFireMixin {

    @Inject(method = "renderFireOverlay", at = @At("HEAD"))
    private static void opal$lowerFire(final MatrixStack matrices, final VertexConsumerProvider vertexConsumers,
                                       final Sprite sprite, final CallbackInfo ci) {
        if (OpalClient.getInstance().getModuleRepository() != null
                && OpalClient.getInstance().getModuleRepository().getModule(LowFireModule.class).isEnabled()) {
            matrices.translate(0.0F, -0.3F, 0.0F);
        }
    }
}

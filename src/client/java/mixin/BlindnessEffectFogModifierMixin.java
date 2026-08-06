package mixin;

import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.fog.BlindnessEffectFogModifier;
import net.minecraft.client.render.fog.StatusEffectFogModifier;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wtf.oraculus.client.feature.module.impl.visual.NoRenderModule;

@Mixin(StatusEffectFogModifier.class)
public final class BlindnessEffectFogModifierMixin {

    @Inject(method = "shouldApply", at = @At("HEAD"), cancellable = true)
    private void oraculus$disableBlindnessFog(final CameraSubmersionType submersionType, final Entity cameraEntity,
                                          final CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof BlindnessEffectFogModifier && NoRenderModule.shouldSuppressBlindness()) {
            cir.setReturnValue(false);
        }
    }
}

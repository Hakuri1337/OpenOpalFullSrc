package wtf.oraculus.mixin;

import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.module.impl.visual.FullBrightModule;

@Mixin(LightmapTextureManager.class)
public final class LightmapTextureManagerMixin {

    private LightmapTextureManagerMixin() {
    }

    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 1))
    public float redirectGammaValue(Double d) {
        final float factor = OraculusClient.getInstance().getModuleRepository().getModule(FullBrightModule.class).isEnabled()
                ? 15.F
                : 1.F;
        return d.floatValue() * factor;
    }

}

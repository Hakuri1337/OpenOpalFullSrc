package wtf.oraculus.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.module.impl.world.ssngscaffold.SsngScaffoldModule;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {

    @Inject(method = "getFovMultiplier", at = @At("RETURN"), cancellable = true)
    private void hookSsngFovMultiplier(final boolean firstPerson, final float fovEffectScale,
                                       final CallbackInfoReturnable<Float> cir) {
        final OraculusClient client = OraculusClient.getInstance();
        if (!client.isPostInitialization() || client.getModuleRepository() == null) return;
        final SsngScaffoldModule scaffold = client.getModuleRepository().getModule(SsngScaffoldModule.class);
        if (scaffold != null && scaffold.isEnabled() && scaffold.shouldKeepFov()) {
            cir.setReturnValue(scaffold.configuredFov());
        }
    }
}

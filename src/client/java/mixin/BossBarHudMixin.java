package mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.module.impl.visual.overlay.OverlayModule;

@Mixin(BossBarHud.class)
public final class BossBarHudMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void render(final DrawContext context, final CallbackInfo ci) {
        final OverlayModule overlayModule = OraculusClient.getInstance().getModuleRepository().getModule(OverlayModule.class);
        if (overlayModule.isEnabled() && !overlayModule.isBossbarEnabled()) {
            ci.cancel();
        }
    }

}

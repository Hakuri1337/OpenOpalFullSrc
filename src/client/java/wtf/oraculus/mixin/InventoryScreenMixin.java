package wtf.oraculus.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.oraculus.client.feature.helper.impl.player.rotation.RotationHelper;

import static wtf.oraculus.client.Constants.mc;

@Mixin(InventoryScreen.class)
public final class InventoryScreenMixin {
    private InventoryScreenMixin() {
    }

    @Inject(
            method = "drawEntity(Lnet/minecraft/client/gui/DrawContext;IIIIIFFFLnet/minecraft/entity/LivingEntity;)V",
            at = @At("HEAD")
    )
    private static void hookDrawEntityHead(DrawContext context, int x1, int y1, int x2, int y2, int size, float f, float mouseX, float mouseY, LivingEntity entity, CallbackInfo ci) {
        if (mc.player != null && entity == mc.player) {
            RotationHelper.getClientHandler().setTicking(true);
        }
    }

    @Inject(
            method = "drawEntity(Lnet/minecraft/client/gui/DrawContext;IIIIIFFFLnet/minecraft/entity/LivingEntity;)V",
            at = @At("TAIL")
    )
    private static void hookDrawEntityTail(DrawContext context, int x1, int y1, int x2, int y2, int size, float f, float mouseX, float mouseY, LivingEntity entity, CallbackInfo ci) {
        if (mc.player != null && entity == mc.player) {
            RotationHelper.getClientHandler().setTicking(false);
        }
    }
}

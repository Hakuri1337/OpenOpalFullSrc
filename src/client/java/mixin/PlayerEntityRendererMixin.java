package mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShieldItem;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.module.impl.visual.AnimationsModule;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin extends LivingEntityRenderer<AbstractClientPlayerEntity, PlayerEntityRenderState, PlayerEntityModel> {

    private PlayerEntityRendererMixin(EntityRendererFactory.Context ctx, PlayerEntityModel model, float shadowRadius) {
        super(ctx, model, shadowRadius);
    }

    @Redirect(
            method = "getArmPose(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/Hand;)Lnet/minecraft/client/render/entity/model/BipedEntityModel$ArmPose;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;isEmpty()Z")
    )
    private static boolean cancelShieldPose(ItemStack instance, @Local(argsOnly = true) Hand hand) {
        final AnimationsModule animationsModule = OraculusClient.getInstance().getModuleRepository().getModule(AnimationsModule.class);
        if (animationsModule.isEnabled() && animationsModule.isHideShield() && hand == Hand.OFF_HAND && instance.getItem() instanceof ShieldItem) {
            return true;
        }

        return instance.isEmpty();
    }
}

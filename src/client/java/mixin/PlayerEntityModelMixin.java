package mixin;

import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import wtf.oraculus.duck.BipedEntityRenderStateAccess;
import wtf.oraculus.utility.player.BlockUtility;

import static wtf.oraculus.client.Constants.mc;

@Mixin(PlayerEntityModel.class)
public final class PlayerEntityModelMixin {

    @ModifyVariable(
            method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private PlayerEntityRenderState modifyThirdPersonRenderState(PlayerEntityRenderState state) {
        final LivingEntity livingEntity = ((BipedEntityRenderStateAccess) state).oraculus$getEntity();

        if (livingEntity == mc.player && BlockUtility.isThirdPersonBlockingState(state)) {
            state.isUsingItem = true;
            state.activeHand = Hand.MAIN_HAND;
            if (state.mainArm == Arm.RIGHT) {
                state.leftArmPose = BipedEntityModel.ArmPose.EMPTY;
                state.rightArmPose = BipedEntityModel.ArmPose.BLOCK;
            } else {
                state.leftArmPose = BipedEntityModel.ArmPose.BLOCK;
                state.rightArmPose = BipedEntityModel.ArmPose.EMPTY;
            }
        }

        return state;
    }


}

package wtf.oraculus.mixin;

import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import wtf.oraculus.client.feature.helper.impl.player.slot.SlotHelper;
import wtf.oraculus.utility.player.BlockUtility;

import static wtf.oraculus.client.Constants.mc;

@Mixin(ArmedEntityRenderState.class)
public final class ArmedEntityRenderStateMixin {

    @Redirect(
            method = "updateRenderState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getStackInArm(Lnet/minecraft/util/Arm;)Lnet/minecraft/item/ItemStack;")
    )
    private static ItemStack hookGetStackInArm(LivingEntity entity, Arm arm) {
        if (entity == mc.player && arm == mc.player.getMainArm() && BlockUtility.isNoSlowBlockingState()) {
            return SlotHelper.getInstance().getMainHandStack(mc.player);
        }
        return entity.getStackInArm(arm);
    }

}

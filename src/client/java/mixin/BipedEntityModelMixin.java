package mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.ModelWithArms;
import net.minecraft.client.render.entity.model.ModelWithHead;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShieldItem;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.module.impl.visual.AnimationsModule;
import wtf.oraculus.duck.BipedEntityRenderStateAccess;

@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelMixin<T extends BipedEntityRenderState> extends EntityModel<T> implements ModelWithArms, ModelWithHead {

    @Final
    @Shadow
    public ModelPart head;

    private BipedEntityModelMixin(ModelPart root) {
        super(root);
    }

    @WrapOperation(method = "positionBlockingArm", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;clamp(FFF)F"))
    private float fixThirdPersonBlockRotation(float value, float min, float max, Operation<Float> original) {
        final AnimationsModule animationsModule = OraculusClient.getInstance().getModuleRepository().getModule(AnimationsModule.class);
        return animationsModule.isEnabled() && animationsModule.isSwordBlocking() ? 0 : original.call(value, min, max);
    }

    @WrapOperation(method = {"positionLeftArm", "positionRightArm"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/BipedEntityModel;positionBlockingArm(Lnet/minecraft/client/model/ModelPart;Z)V"))
    private void fixThirdPersonBlockPosition(BipedEntityModel<?> instance, ModelPart arm, boolean rightArm, Operation<Void> original, @Local(argsOnly = true) T state) {
        original.call(instance, arm, rightArm);
        final AnimationsModule animationsModule = OraculusClient.getInstance().getModuleRepository().getModule(AnimationsModule.class);
        if (animationsModule.isEnabled() && animationsModule.isSwordBlocking()) {
            final LivingEntity entity = ((BipedEntityRenderStateAccess) state).oraculus$getEntity();
            if (entity instanceof LivingEntity livingEntity && state instanceof BipedEntityRenderState) {
                ItemStack stack = rightArm ? livingEntity.getStackInArm(Arm.RIGHT) : livingEntity.getStackInArm(Arm.LEFT);
                if (!(stack.getItem() instanceof ShieldItem)) {
                    arm.pitch = arm.pitch * 0.5F - ((float) Math.PI / 10F) * 2F;
                    arm.yaw = 0;
                }
            }
        }
    }

}

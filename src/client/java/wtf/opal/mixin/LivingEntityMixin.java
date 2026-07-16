package wtf.opal.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.opal.client.feature.module.impl.movement.MovementFixModule;
import wtf.opal.client.feature.module.impl.movement.SprintModule;
import wtf.opal.client.feature.module.impl.visual.AnimationsModule;
import wtf.opal.client.feature.module.impl.world.blockfly.rotation.BlockFlyRotationBridge;
import wtf.opal.duck.ClientPlayerEntityAccess;
import wtf.opal.event.EventDispatcher;
import wtf.opal.event.impl.game.player.movement.JumpEvent;
import wtf.opal.event.impl.game.player.movement.JumpingCooldownEvent;
import wtf.opal.event.impl.game.player.movement.step.StepEvent;

import static wtf.opal.client.Constants.mc;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {

    @Shadow
    private int jumpingCooldown;

    private LivingEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @ModifyReturnValue(method = "getStepHeight", at = @At("RETURN"))
    private float hookStepHeight(float original) {
        if ((Object) this == mc.player) {
            final StepEvent stepEvent = new StepEvent(original);
            EventDispatcher.dispatch(stepEvent);
            return stepEvent.getStepHeight();
        }
        return original;
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;abs(F)F"))
    private float modifyBackwardsWalkingRotation(float value, Operation<Float> original) {
        final AnimationsModule animationsModule = OpalClient.getInstance().getModuleRepository().getModule(AnimationsModule.class);
        if (animationsModule.isEnabled() && animationsModule.isOldBackwardsWalking()) {
            return 0;
        }
        return original.call(value);
    }

    @Redirect(
            method = "tickMovement",
            at = @At(value = "FIELD", target = "Lnet/minecraft/entity/LivingEntity;jumpingCooldown:I", opcode = Opcodes.PUTFIELD, ordinal = 1)
    )
    private void modifyJumpingCooldown(LivingEntity instance, int jumpingCooldown) {
        if (instance == mc.player) {
            final JumpingCooldownEvent event = new JumpingCooldownEvent(jumpingCooldown);
            EventDispatcher.dispatch(event);
            this.jumpingCooldown = event.getCooldown();
        } else {
            this.jumpingCooldown = 10;
        }
    }

    @ModifyConstant(
            method = "getHandSwingDuration",
            constant = @Constant(intValue = 6)
    )
    private int modifyHandSwingDuration(final int value) {
        final AnimationsModule animationsModule = OpalClient.getInstance().getModuleRepository().getModule(AnimationsModule.class);
        return animationsModule.isEnabled() ? (int) (value * animationsModule.getSwingSlowdown()) : value;
    }

    @Redirect(
            method = "jump",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getYaw()F")
    )
    private float redirectYaw(LivingEntity instance) {
        if (instance instanceof ClientPlayerEntity player) {
            if (BlockFlyRotationBridge.ownsRotation()) {
                return BlockFlyRotationBridge.logicalYawOr(instance.getYaw());
            }
            if (OpalClient.getInstance().getModuleRepository().getModule(MovementFixModule.class).isFixMovement()) {
                return instance.getYaw();
            }

            final float yaw = RotationHelper.getClientHandler().getYawOr(instance.getYaw());
            if (SprintModule.isOmniSprint() && !player.input.hasForwardMovement()) {
                if (player.input.getMovementInput().y < -1.0E-5F) {
                    return yaw + 180.0F;
                } else {
                    return player.input.getMovementInput().x > 0.0F ? yaw - 90.0F : yaw + 90.0F;
                }
            }
            return yaw;
        }
        return instance.getYaw();
    }

    @Redirect(
            method = "dropItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;swingHand(Lnet/minecraft/util/Hand;)V")
    )
    private void redirectDropSwing(LivingEntity instance, Hand hand) {
        if (instance instanceof ClientPlayerEntity clientPlayer) {
            final AnimationsModule animationsModule = OpalClient.getInstance().getModuleRepository().getModule(AnimationsModule.class);
            if (animationsModule.isEnabled() && animationsModule.isHideDropSwing()) {
                ((ClientPlayerEntityAccess) clientPlayer).opal$swingHandServerside(hand);
                return;
            }
        }
        instance.swingHand(hand);
    }

    @Inject(
            method = "jump", at = @At("HEAD"), cancellable = true
    )
    private void hookJumpEvent(CallbackInfo ci) {
        if ((Object) this != mc.player) return;

        final JumpEvent jumpEvent = new JumpEvent(isSprinting());
        EventDispatcher.dispatch(jumpEvent);

        if (jumpEvent.isCancelled())
            ci.cancel();
    }

}

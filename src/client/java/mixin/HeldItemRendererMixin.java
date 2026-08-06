package mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.*;
import net.minecraft.item.consume.UseAction;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.oraculus.client.feature.helper.impl.player.slot.SlotHelper;
import wtf.oraculus.client.feature.module.impl.visual.AnimationsModule;
import wtf.oraculus.client.feature.module.impl.visual.SilenceItemRotationModule;
import wtf.oraculus.client.feature.module.impl.world.scaffold.render.ScaffoldRenderSpoof;
import wtf.oraculus.duck.PlayerEntityAccess;
import wtf.oraculus.utility.player.BlockUtility;

import static wtf.oraculus.client.Constants.mc;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    @Shadow
    private ItemStack mainHand;

    @Shadow
    private float equipProgressMainHand;

    @Shadow
    protected abstract void applySwingOffset(MatrixStack matrices, Arm arm, float swingProgress);

    private HeldItemRendererMixin() {
    }

    @Inject(method = "renderFirstPersonItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;push()V", shift = At.Shift.AFTER))
    private void hookRenderFirstPersonItem(AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue orderedRenderCommandQueue, int light, CallbackInfo ci) {
        final AnimationsModule animationModule = OraculusClient.getInstance().getModuleRepository().getModule(AnimationsModule.class);
        if (animationModule.isEnabled() && Hand.MAIN_HAND == hand) {
            matrices.translate(animationModule.getMainHandX(), animationModule.getMainHandY(), animationModule.getMainHandScale());
        }
    }

    @Inject(
            method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
            at = @At("HEAD")
    )
    private void applySilenceItemRotation(
            LivingEntity entity,
            ItemStack stack,
            ItemDisplayContext displayContext,
            MatrixStack matrices,
            OrderedRenderCommandQueue orderedRenderCommandQueue,
            int light,
            CallbackInfo ci
    ) {
        if (mc.player == null || entity != mc.player
                || (displayContext != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                && displayContext != ItemDisplayContext.FIRST_PERSON_LEFT_HAND)) {
            return;
        }

        final boolean renderedOnLeft = displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        final boolean mainHandOnLeft = mc.player.getMainArm() == Arm.LEFT;
        final Hand hand = renderedOnLeft == mainHandOnLeft ? Hand.MAIN_HAND : Hand.OFF_HAND;
        final SilenceItemRotationModule module = OraculusClient.getInstance()
                .getModuleRepository()
                .getModule(SilenceItemRotationModule.class);

        if (module.isEnabled() && module.shouldRotate(mc.player, stack, hand)) {
            final float tickDelta = mc.getRenderTickCounter().getTickProgress(false);
            module.applyRotation(matrices, mc.player.age + tickDelta);
        }
    }

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void hideShield(AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue orderedRenderCommandQueue, int light, CallbackInfo ci) {
        final AnimationsModule animationsModule = OraculusClient.getInstance().getModuleRepository().getModule(AnimationsModule.class);
        if (hand == Hand.OFF_HAND
                && item.getItem() instanceof ShieldItem
                && animationsModule.isEnabled()
                && animationsModule.isHideShield()) {
            ci.cancel();
        }
    }

    @ModifyArg(
            method = "updateHeldItems",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;clamp(FFF)F", ordinal = 2),
            index = 0
    )
    private float modifyMainHandEquipProgress(float value, @Local(ordinal = 0) ItemStack itemStack) {
        final AnimationsModule animationsModule = OraculusClient.getInstance().getModuleRepository().getModule(AnimationsModule.class);
        final boolean oldCooldownAnimation = animationsModule.isEnabled() && animationsModule.isOldCooldownAnimation();
        if (oldCooldownAnimation && this.mainHand == itemStack) {
            return 1.0F - this.equipProgressMainHand;
        }
        return value;
    }

    @Redirect(
            method = "updateHeldItems",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getAttackCooldownProgress(F)F")
    )
    private float redirectGetAttackCooldown(ClientPlayerEntity instance, float v) {
        return ((PlayerEntityAccess) instance).oraculus$getVisualAttackCooldownProgress(v);
    }

    @ModifyArg(
            method = "renderFirstPersonItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applyEquipOffset(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/Arm;F)V",
                    ordinal = 3
            ),
            index = 2
    )
    private float applyEquipOffset(float equipProgress) {
        final AnimationsModule animationModule = OraculusClient.getInstance().getModuleRepository().getModule(AnimationsModule.class);
        if (animationModule.isEnabled() && !animationModule.isEquipOffset()) {
            return 0;
        }
        return equipProgress;
    }

    @Inject(
            method = "renderFirstPersonItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
                    ordinal = 1
            )
    )
    private void applySwordBlockingTransformation(AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue orderedRenderCommandQueue, int light, CallbackInfo ci) {
        final AnimationsModule animationsModule = OraculusClient.getInstance().getModuleRepository().getModule(AnimationsModule.class);

        if (animationsModule.isEnabled() && animationsModule.isSwordBlocking() && this.isSwordBlockingFirstPerson(player, hand, item)) {
            animationsModule.applyTransformations(matrices, swingProgress);
        }
    }

    @Redirect(
            method = "renderFirstPersonItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;isUsingItem()Z")
    )
    private boolean fakeABUseState(AbstractClientPlayerEntity player, @Local(argsOnly = true) Hand hand, @Local(argsOnly = true) ItemStack item) {
        return this.isFakeABFirstPerson(player, hand, item) || player.isUsingItem();
    }

    @Redirect(
            method = "renderFirstPersonItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;getItemUseTimeLeft()I")
    )
    private int fakeABUseTime(AbstractClientPlayerEntity player, @Local(argsOnly = true) Hand hand, @Local(argsOnly = true) ItemStack item) {
        return this.isFakeABFirstPerson(player, hand, item) ? 1 : player.getItemUseTimeLeft();
    }

    @Redirect(
            method = "renderFirstPersonItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;getActiveHand()Lnet/minecraft/util/Hand;")
    )
    private Hand fakeABActiveHand(AbstractClientPlayerEntity player, @Local(argsOnly = true) Hand hand, @Local(argsOnly = true) ItemStack item) {
        return this.isFakeABFirstPerson(player, hand, item) ? Hand.MAIN_HAND : player.getActiveHand();
    }

    @Redirect(
            method = "renderFirstPersonItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;getUseAction()Lnet/minecraft/item/consume/UseAction;")
    )
    private UseAction fakeABUseAction(ItemStack instance, @Local(argsOnly = true) AbstractClientPlayerEntity player, @Local(argsOnly = true) Hand hand) {
        return this.isFakeABFirstPerson(player, hand, instance) ? UseAction.BLOCK : instance.getUseAction();
    }

    @Inject(
            method = "swingArm",
            at = @At(value = "HEAD"),
            cancellable = true)
    private void cancelSwingArm(float swingProgress, float equipProgress, MatrixStack matrices, int armX, Arm arm, CallbackInfo ci) {
        if (mc.player == null || arm != mc.player.getMainArm()) {
            return;
        }

        if (BlockUtility.isForceBlockUseState(mc.player) || BlockUtility.isNoSlowBlockingState() || BlockUtility.isFakeABBlockingState(mc.player)) {
            matrices.translate(0.56F, -0.52F + 0 * -0.6F, -0.72F);
            ci.cancel();
        }
    }

    @Unique
    private boolean isFakeABFirstPerson(AbstractClientPlayerEntity player, Hand hand, ItemStack item) {
        return hand == Hand.MAIN_HAND && item.isIn(ItemTags.SWORDS) && BlockUtility.isFakeABBlockingState(player);
    }

    @Unique
    private boolean isSwordBlockingFirstPerson(AbstractClientPlayerEntity player, Hand hand, ItemStack item) {
        return hand == Hand.MAIN_HAND
                && item.isIn(ItemTags.SWORDS)
                && (BlockUtility.isForceBlockUseState(player)
                || BlockUtility.isBlockUseState(player)
                || BlockUtility.isNoSlowBlockingState()
                || BlockUtility.isFakeABBlockingState(player));
    }

    @Redirect(
            method = "renderFirstPersonItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;getItem()Lnet/minecraft/item/Item;")
    )
    private Item cancelBlockTransformation(ItemStack instance, @Local(argsOnly = true) AbstractClientPlayerEntity player, @Local(argsOnly = true) Hand hand) {
        final AnimationsModule animationsModule = OraculusClient.getInstance().getModuleRepository().getModule(AnimationsModule.class);
        if (animationsModule.isEnabled() && animationsModule.isSwordBlocking() && this.isSwordBlockingFirstPerson(player, hand, instance)) {
            return Items.SHIELD;
        }
        return instance.getItem();
    }

    @Inject(
            method = "renderFirstPersonItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applyEquipOffset(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/Arm;F)V",
                    ordinal = 2,
                    shift = At.Shift.AFTER
            )
    )
    private void applyEatingAndDrinkingOffset(AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue orderedRenderCommandQueue, int light, CallbackInfo ci) {
        final AnimationsModule animationsModule = OraculusClient.getInstance().getModuleRepository().getModule(AnimationsModule.class);
        if (animationsModule.isEnabled() && player.handSwinging) {
            applySwingOffset(matrices, this.getArmForHand(player, hand), swingProgress);
        }
    }

    @Inject(
            method = "renderFirstPersonItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applyEquipOffset(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/Arm;F)V",
                    ordinal = 4,
                    shift = At.Shift.AFTER
            )
    )
    private void applyBowOffset(AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue orderedRenderCommandQueue, int light, CallbackInfo ci) {
        final AnimationsModule animationsModule = OraculusClient.getInstance().getModuleRepository().getModule(AnimationsModule.class);
        if (animationsModule.isEnabled()) {
            applySwingOffset(matrices, this.getArmForHand(player, hand), swingProgress);
        }
    }

    @Unique
    private Arm getArmForHand(AbstractClientPlayerEntity player, Hand hand) {
        return hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
    }

    @Redirect(
            method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getYaw(F)F")
    )
    private float redirectItemYaw(ClientPlayerEntity instance, float tickDelta) {
        return RotationHelper.getClientHandler().getYawOr(instance.getYaw(tickDelta));
    }

    @Redirect(
            method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getPitch(F)F")
    )
    private float redirectItemPitch(ClientPlayerEntity instance, float tickDelta) {
        return RotationHelper.getClientHandler().getPitchOr(instance.getPitch(tickDelta));
    }

    @Redirect(
            method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/network/ClientPlayerEntity;lastRenderYaw:F", opcode = Opcodes.GETFIELD)
    )
    private float redirectItemLastRenderYaw(ClientPlayerEntity instance) {
        return RotationHelper.getClientHandler().getLastRenderYawOr(instance.lastRenderYaw);
    }

    @Redirect(
            method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/network/ClientPlayerEntity;lastRenderPitch:F", opcode = Opcodes.GETFIELD)
    )
    private float redirectItemLastRenderPitch(ClientPlayerEntity instance) {
        return RotationHelper.getClientHandler().getLastRenderPitchOr(instance.lastRenderPitch);
    }

    @Redirect(
            method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/network/ClientPlayerEntity;renderYaw:F", opcode = Opcodes.GETFIELD)
    )
    private float redirectItemRenderYaw(ClientPlayerEntity instance) {
        return RotationHelper.getClientHandler().getRenderYawOr(instance.renderYaw);
    }

    @Redirect(
            method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/network/ClientPlayerEntity;renderPitch:F", opcode = Opcodes.GETFIELD)
    )
    private float redirectItemRenderPitch(ClientPlayerEntity instance) {
        return RotationHelper.getClientHandler().getRenderPitchOr(instance.renderPitch);
    }

    @Redirect(
            method = "updateHeldItems",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getMainHandStack()Lnet/minecraft/item/ItemStack;")
    )
    private ItemStack getMainHandStack(ClientPlayerEntity instance) {
        final ItemStack stack = SlotHelper.getInstance().getMainHandStack(instance);
        return ScaffoldRenderSpoof.mainHandStackOr(instance, stack);
    }
}

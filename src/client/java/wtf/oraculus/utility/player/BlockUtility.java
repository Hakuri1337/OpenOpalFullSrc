package wtf.oraculus.utility.player;

import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ShieldItem;
import net.minecraft.item.consume.UseAction;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.helper.impl.player.mouse.MouseHelper;
import wtf.oraculus.client.feature.helper.impl.player.slot.SlotHelper;
import wtf.oraculus.client.feature.module.impl.combat.BlockModule;
import wtf.oraculus.client.feature.module.impl.combat.killaura.KillAuraModule;
import wtf.oraculus.client.feature.module.impl.movement.noslow.NoSlowModule;
import wtf.oraculus.client.feature.module.impl.visual.AnimationsModule;
import wtf.oraculus.client.feature.module.repository.ModuleRepository;
import wtf.oraculus.duck.BipedEntityRenderStateAccess;

import static wtf.oraculus.client.Constants.mc;

public final class BlockUtility {

    private BlockUtility() {
    }

    public static void applyBlockTransformation(final MatrixStack matrices) {
        matrices.translate(-0.15F, 0.16F, 0.15F);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-18.0F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(82.0F));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(112.0F));
    }

    public static void applySwingTransformation(final MatrixStack matrices, final float swingProgress, final float convertedProgress) {
        final float f = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45.0F + f * -20.0F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(convertedProgress * -20.0F));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(convertedProgress * -80.0F));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-45.0F));
    }

    public static boolean isBlockUseState(final PlayerEntity player) {
        return player.getMainHandStack().isIn(ItemTags.SWORDS) && player.getMainHandStack().getUseAction().equals(UseAction.BLOCK) && player.getItemUseTime() > 0;
    }

    public static boolean isForceBlockUseState(final PlayerEntity player) {
        final AnimationsModule animationsModule = OraculusClient.getInstance().getModuleRepository().getModule(AnimationsModule.class);
        return player.getMainHandStack().isIn(ItemTags.SWORDS) && player.getActiveItem().getItem() instanceof ShieldItem && player.getItemUseTime() > 0 && animationsModule.isEnabled() && animationsModule.isSwordBlocking() && !isBlockUseState(player);
    }

    public static boolean isFakeABBlockingState(final PlayerEntity player) {
        if (player != mc.player) {
            return false;
        }

        if (isUsingOffhandItem(player)) {
            return false;
        }

        final ModuleRepository moduleRepository = OraculusClient.getInstance().getModuleRepository();
        final AnimationsModule animationsModule = moduleRepository.getModule(AnimationsModule.class);
        if (!(animationsModule.isEnabled() && animationsModule.isSwordBlocking())) {
            return false;
        }

        final SlotHelper slotHelper = SlotHelper.getInstance();
        if (!slotHelper.getMainHandStack(mc.player).isIn(ItemTags.SWORDS)) {
            return false;
        }

        if (isManualFakeABBlockingState(player)) {
            return true;
        }

        final KillAuraModule killAuraModule = moduleRepository.getModule(KillAuraModule.class);
        return killAuraModule.isEnabled() && killAuraModule.isFakeBlocking();
    }

    public static boolean isUsingOffhandItem(final PlayerEntity player) {
        return player.isUsingItem() && player.getActiveHand() == Hand.OFF_HAND && !player.getOffHandStack().isEmpty();
    }

    public static boolean isManualFakeABBlockingState(final PlayerEntity player) {
        if (player != mc.player) {
            return false;
        }

        final ModuleRepository moduleRepository = OraculusClient.getInstance().getModuleRepository();
        final AnimationsModule animationsModule = moduleRepository.getModule(AnimationsModule.class);
        final SlotHelper slotHelper = SlotHelper.getInstance();

        return animationsModule.isEnabled()
                && animationsModule.isSwordBlocking()
                && animationsModule.isFakeAB()
                && mc.options.useKey.isPressed()
                && !isUsingOffhandItem(player)
                && slotHelper.getMainHandStack(mc.player).isIn(ItemTags.SWORDS);
    }

    public static boolean isThirdPersonBlockingState(final ArmedEntityRenderState entityState) {
        final AnimationsModule animationsModule = OraculusClient.getInstance().getModuleRepository().getModule(AnimationsModule.class);

        if (!(animationsModule.isEnabled() && animationsModule.isSwordBlocking())) {
            return false;
        }

        if (!(entityState instanceof BipedEntityRenderState state)) {
            return false;
        }

        final LivingEntity livingEntity = ((BipedEntityRenderStateAccess) state).oraculus$getEntity();

        if (state.isUsingItem
                && entityState.mainArm == Arm.LEFT
                && (entityState.rightArmPose == BipedEntityModel.ArmPose.EMPTY || entityState.rightArmPose == BipedEntityModel.ArmPose.BLOCK)
                && livingEntity.getStackInArm(Arm.RIGHT).getItem() instanceof ShieldItem
                && livingEntity.getStackInArm(Arm.LEFT).isIn(ItemTags.SWORDS)) {
            return true;
        }

        if (state.isUsingItem
                && entityState.mainArm == Arm.RIGHT
                && (entityState.leftArmPose == BipedEntityModel.ArmPose.EMPTY || entityState.leftArmPose == BipedEntityModel.ArmPose.BLOCK)
                && livingEntity.getStackInArm(Arm.LEFT).getItem() instanceof ShieldItem
                && livingEntity.getStackInArm(Arm.RIGHT).isIn(ItemTags.SWORDS)) {
            return true;
        }

        if (livingEntity == mc.player && (isNoSlowBlockingState() || isFakeABBlockingState(mc.player))) {
            return true;
        }

        if (livingEntity instanceof PlayerEntity player && (isBlockUseState(player) || isForceBlockUseState(player) || isFakeABBlockingState(player))) {
            return true;
        }

        return false;
    }

    public static boolean isNoSlowBlockingState() {
        final ModuleRepository moduleRepository = OraculusClient.getInstance().getModuleRepository();

        final AnimationsModule animationsModule = moduleRepository.getModule(AnimationsModule.class);
        final NoSlowModule noSlowModule = moduleRepository.getModule(NoSlowModule.class);
        final BlockModule blockModule = moduleRepository.getModule(BlockModule.class);

        final SlotHelper slotHelper = SlotHelper.getInstance();

        return animationsModule.isEnabled() && animationsModule.isSwordBlocking() && noSlowModule.isEnabled()
                && noSlowModule.getAction() == NoSlowModule.Action.BLOCKABLE
                && (MouseHelper.getRightButton().isPressed() || (blockModule.isEnabled() && blockModule.isBlocking()))
                && slotHelper.getMainHandStack(mc.player).isIn(ItemTags.SWORDS)
                && !InventoryUtility.isBlockInteractable(PlayerUtility.getBlockOver())
                && (mc.player.getOffHandStack().getItem() instanceof ShieldItem || slotHelper.getMainHandStack(mc.player).getUseAction() == UseAction.BLOCK);
    }

}

package wtf.oraculus.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.ModelWithArms;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShieldItem;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.module.impl.visual.AnimationsModule;
import wtf.oraculus.duck.BipedEntityRenderStateAccess;
import wtf.oraculus.utility.player.BlockUtility;

import static wtf.oraculus.client.Constants.mc;

@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemFeatureRendererMixin<S extends ArmedEntityRenderState, M extends EntityModel<S> & ModelWithArms> extends FeatureRenderer<S, M> {

    public HeldItemFeatureRendererMixin(final FeatureRendererContext<S, M> context) {
        super(context);
    }

    @Inject(method = "renderItem", at = @At("HEAD"))
    private void setThirdPersonStackRef(S entityState, ItemRenderState itemRenderState, Arm arm, MatrixStack matrices, OrderedRenderCommandQueue orderedRenderCommandQueue, int light, CallbackInfo ci, @Share("stack") LocalRef<ItemStack> stackRef) {
        if (BlockUtility.isThirdPersonBlockingState(entityState)) {
            stackRef.set(((BipedEntityRenderStateAccess) entityState).oraculus$getEntity().getStackInArm(arm));
        }
    }

    @ModifyArgs(method = "renderItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V"))
    private void translateThirdPersonBlock(Args args, @Local(argsOnly = true) S entityState, @Share("stack") LocalRef<ItemStack> stackRef) {
        if (BlockUtility.isThirdPersonBlockingState(entityState)) {
            args.setAll((float) args.get(0) * -1.0F, 0.4375F, (float) args.get(2) / 10 * -1.0F);
        }
    }

    @WrapWithCondition(method = "renderItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;multiply(Lorg/joml/Quaternionfc;)V"))
    private boolean removeThirdPersonMultiplyTransformations(MatrixStack instance, Quaternionfc quaternion, @Local(argsOnly = true) S entityState, @Share("stack") LocalRef<ItemStack> stackRef) {
        return !BlockUtility.isThirdPersonBlockingState(entityState);
    }

    @Inject(method = "renderItem", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/render/item/ItemRenderState;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;III)V"))
    private void applyThirdPersonBlockRotation(S entityState, ItemRenderState itemRenderState, Arm arm, MatrixStack matrices, OrderedRenderCommandQueue orderedRenderCommandQueue, int light, CallbackInfo ci) {
        if (BlockUtility.isThirdPersonBlockingState(entityState)) {
            final int direction = arm == Arm.RIGHT ? 1 : -1;
            final float scale = 0.625F;

            matrices.translate(direction * 0.05F, 0.0F, -0.1F);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(direction * -50.0F));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-10.0F));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(direction * -60.0F));


            matrices.translate(direction * -0.0625F, 0.1875F, 0.0F);
            matrices.scale(scale, scale, scale);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(100));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(direction * -145));
            matrices.translate(-0.011765625F, 0.0F, 0.002125F);


            matrices.translate(0.0F, -0.3F, 0.0F);
            matrices.scale(1.5F, 1.5F, 1.5F);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(direction * 50.0F));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(direction * 335.0F));
            matrices.translate(direction * -0.9375F, -0.0625F, 0.0F);

            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(direction * 180.0F));
            matrices.translate(direction * -0.5F, 0.5F, 0.03125F);


            matrices.scale(1 / 0.85F, 1 / 0.85F, 1 / 0.85F);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(direction * -55.0F));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(direction * 90.0F));

            matrices.translate(0.0F, -4.0F * 0.0625F, -0.5F * 0.0625F);
        }
    }

    @SuppressWarnings("MixinExtrasOperationParameters")
    @WrapOperation(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/ArmedEntityRenderState;FF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/feature/HeldItemFeatureRenderer;renderItem(Lnet/minecraft/client/render/entity/state/ArmedEntityRenderState;Lnet/minecraft/client/render/item/ItemRenderState;Lnet/minecraft/util/Arm;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V")
    )
    private void cancelShieldRender(HeldItemFeatureRenderer instance, S entityState, ItemRenderState itemRenderState, Arm arm, MatrixStack matrices, OrderedRenderCommandQueue orderedRenderCommandQueue, int light, Operation<Void> original) {
        final AnimationsModule animationsModule = OraculusClient.getInstance().getModuleRepository().getModule(AnimationsModule.class);
        if (animationsModule.isEnabled()
                && animationsModule.isHideShield()
                && arm == mc.player.getMainArm().getOpposite()
                && mc.player.getStackInHand(Hand.OFF_HAND).getItem() instanceof ShieldItem) {
            return;
        }
        original.call(instance, entityState, itemRenderState, arm, matrices, orderedRenderCommandQueue, light);
    }

}

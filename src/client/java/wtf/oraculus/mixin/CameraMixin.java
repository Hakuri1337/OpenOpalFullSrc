package wtf.oraculus.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.oraculus.client.feature.module.impl.visual.AnimationsModule;
import wtf.oraculus.client.feature.module.impl.visual.ViewClipModule;

@Mixin(Camera.class)
public abstract class CameraMixin {
    private CameraMixin() {
    }

    @Redirect(
            method = "update",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw(F)F")
    )
    private float redirectYaw(Entity instance, float tickDelta) {
        return RotationHelper.getClientHandler().getYawOr(instance.getYaw(tickDelta));
    }

    @Redirect(
            method = "update",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getPitch(F)F")
    )
    private float redirectPitch(Entity instance, float tickDelta) {
        return RotationHelper.getClientHandler().getPitchOr(instance.getPitch(tickDelta));
    }

    @Shadow
    public abstract Entity getFocusedEntity();

    @Shadow
    private float cameraY;

    @Shadow
    private float lastCameraY;

    @Unique
    private EntityPose prevPose;

    @Inject(method = "clipToSpace", at = @At("HEAD"), cancellable = true)
    private void oraculus$viewClip(final float desiredCameraDistance, final CallbackInfoReturnable<Float> cir) {
        if (OraculusClient.getInstance().getModuleRepository() == null) {
            return;
        }
        final ViewClipModule viewClip = OraculusClient.getInstance().getModuleRepository().getModule(ViewClipModule.class);
        if (viewClip != null && viewClip.isEnabled()) {
            cir.setReturnValue((float) viewClip.getCameraDistance(desiredCameraDistance));
        }
    }

    @Inject(
            method = "updateEyeHeight",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/Camera;lastCameraY:F", opcode = Opcodes.PUTFIELD),
            cancellable = true
    )
    private void modifyCameraY(final CallbackInfo ci) {
        final AnimationsModule animationsModule = OraculusClient.getInstance().getModuleRepository().getModule(AnimationsModule.class);
        final Entity focusedEntity = this.getFocusedEntity();
        final EntityPose pose = focusedEntity.getPose();
        if (animationsModule.isOldSneaking() && this.prevPose != null &&
                (pose == EntityPose.CROUCHING || pose == EntityPose.STANDING && this.prevPose == EntityPose.CROUCHING)) {
            this.cameraY = focusedEntity.getStandingEyeHeight();
            if (pose == EntityPose.CROUCHING) {
                this.cameraY += 0.27F;
            }
            this.lastCameraY = this.cameraY;
            ci.cancel();
        }
        this.prevPose = pose;
    }
}

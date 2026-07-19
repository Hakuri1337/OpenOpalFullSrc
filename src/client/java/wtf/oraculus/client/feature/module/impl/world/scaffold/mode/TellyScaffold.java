package wtf.oraculus.client.feature.module.impl.world.scaffold.mode;

import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import wtf.oraculus.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.oraculus.client.feature.helper.impl.player.rotation.model.IRotationModel;
import wtf.oraculus.client.feature.helper.impl.player.rotation.model.impl.HeypixelRotationModel;
import wtf.oraculus.client.feature.helper.impl.player.rotation.model.impl.InstantRotationModel;
import wtf.oraculus.client.feature.module.impl.world.scaffold.ScaffoldModule;
import wtf.oraculus.client.feature.module.impl.world.scaffold.ScaffoldSettings;
import wtf.oraculus.client.feature.module.property.impl.mode.ModuleMode;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.input.MoveInputEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.mixin.LivingEntityAccessor;
import wtf.oraculus.utility.player.MoveUtility;
import wtf.oraculus.utility.player.PlayerUtility;
import wtf.oraculus.utility.player.RotationUtility;

import static wtf.oraculus.client.Constants.mc;

public final class TellyScaffold extends ModuleMode<ScaffoldModule> {
    private static final float HEYPIXEL_ROTATION_SPEED = 180.0F;

    private int targetYLevel;
    private int groundTicks;
    private int airTicks;
    private Vec2f lastRotation;

    public TellyScaffold(final ScaffoldModule module) {
        super(module);
    }

    @Override
    public void onEnable() {
        this.targetYLevel = mc.player == null ? -1 : MathHelper.floor(mc.player.getY()) - 1;
        this.groundTicks = 0;
        this.airTicks = 0;
        this.lastRotation = null;
        super.onEnable();
    }

    @Subscribe(priority = 2)
    public void onMoveInput(final MoveInputEvent event) {
        if (mc.player == null || mc.world == null || mc.player.hasStatusEffect(StatusEffects.JUMP_BOOST)) {
            return;
        }
        if (!MoveUtility.isMoving() && Math.abs(event.getForward()) < 1.0E-4F && Math.abs(event.getSideways()) < 1.0E-4F) {
            return;
        }
        if (this.canAutoJump(event)) {
            ((LivingEntityAccessor) mc.player).setJumpingCooldown(0);
            event.setJump(true);
        }
    }

    @Subscribe(priority = 2)
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        if (mc.player.isOnGround()) {
            this.groundTicks++;
            this.airTicks = 0;
        } else {
            this.airTicks++;
            this.groundTicks = 0;
        }

        final boolean manualJump = mc.options.jumpKey.isPressed();
        if (this.targetYLevel == -1 || mc.player.isOnGround() || !MoveUtility.isMoving() || manualJump) {
            this.targetYLevel = MathHelper.floor(mc.player.getY()) - 1;
        }

        final ScaffoldModule.BlockData data = module.findBestTellyBlockData(this.targetYLevel);
        if (data == null) {
            module.blockCache = null;
            module.setRotation(null);
            return;
        }

        module.blockCache = data;
        final Vec2f targetRotation = this.legalizeRotation(this.getTargetRotation(data.rotation().rotation()));
        module.setRotation(data.rotation().withRotation(targetRotation));
        RotationHelper.getHandler().rotate(targetRotation, this.createRotationModel());
        this.lastRotation = targetRotation;
    }

    private IRotationModel createRotationModel() {
        return module.getSettings().isTellyHeypixel()
                ? new HeypixelRotationModel(HEYPIXEL_ROTATION_SPEED)
                : InstantRotationModel.INSTANCE;
    }

    private boolean canAutoJump(final MoveInputEvent event) {
        if (!mc.player.isOnGround() || mc.player.isSneaking() || event.isSneak()) {
            return false;
        }
        if (!module.hasHotbarPlaceableBlock()) {
            return false;
        }
        if (mc.player.isTouchingWater() || mc.player.isInLava() || mc.player.isClimbing()) {
            return false;
        }
        return PlayerUtility.isBoxEmpty(mc.player.getBoundingBox().offset(0.0D, 0.42D, 0.0D));
    }

    private Vec2f getTargetRotation(final Vec2f blockRotation) {
        if (mc.player == null) {
            return blockRotation;
        }
        if (this.groundTicks > 0) {
            if (!mc.options.jumpKey.isPressed()) {
                return new Vec2f(mc.player.getYaw(), 75.5F);
            }
            if (this.groundTicks == 1 && this.lastRotation != null) {
                final float yawDelta = MathHelper.wrapDegrees(blockRotation.x - this.lastRotation.x);
                final float halfDelta = Math.max(1.0F, Math.abs(yawDelta) * 0.5F);
                return new Vec2f(this.lastRotation.x + MathHelper.clamp(yawDelta, -halfDelta, halfDelta), 75.5F);
            }
            return new Vec2f(mc.player.getYaw(), 75.5F);
        }

        if (this.lastRotation == null) {
            return blockRotation;
        }
        final float limit = this.airTicks == 1 ? 90.0F : 50.0F;
        final float yawDelta = MathHelper.wrapDegrees(blockRotation.x - this.lastRotation.x);
        return new Vec2f(this.lastRotation.x + MathHelper.clamp(yawDelta, -limit, limit), blockRotation.y);
    }

    private Vec2f legalizeRotation(final Vec2f rotation) {
        final Vec2f clamped = new Vec2f(rotation.x, MathHelper.clamp(rotation.y, -89.5F, 89.5F));
        final Vec2f reference = this.lastRotation == null ? RotationUtility.getRotation() : this.lastRotation;
        final Vec2f patched = RotationUtility.patchConstantRotation(clamped, reference);
        return new Vec2f(patched.x, MathHelper.clamp(patched.y, -89.5F, 89.5F));
    }

    @Override
    public Enum<?> getEnumValue() {
        return ScaffoldSettings.Mode.TELLY;
    }
}

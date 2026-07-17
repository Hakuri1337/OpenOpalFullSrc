package wtf.opal.client.feature.module.impl.combat.velocity.impl;

import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import wtf.opal.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.opal.client.feature.helper.impl.player.rotation.model.impl.InstantRotationModel;
import wtf.opal.client.feature.module.impl.combat.velocity.VelocityMode;
import wtf.opal.client.feature.module.impl.combat.velocity.VelocityModule;
import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.client.feature.module.property.impl.number.NumberProperty;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.impl.game.input.MoveInputEvent;
import wtf.opal.event.impl.game.packet.ReceivePacketEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.mixin.LivingEntityAccessor;
import wtf.opal.utility.misc.chat.ChatUtility;

import static wtf.opal.client.Constants.mc;

public final class JumpResetVelocity extends VelocityMode {

    private final BooleanProperty rotate = new BooleanProperty("Rotate", false)
            .hideIf(() -> this.module.getActiveMode() != this);
    private final BooleanProperty followDirection = new BooleanProperty("Follow Direction", false)
            .hideIf(() -> this.module.getActiveMode() != this);
    private final NumberProperty rotateTicks = new NumberProperty("Rotate Ticks", 12.0D, 3.0D, 20.0D, 1.0D)
            .hideIf(() -> this.module.getActiveMode() != this || (!this.rotate.getValue() && !this.followDirection.getValue()));
    private final BooleanProperty debug = new BooleanProperty("Debug", false)
            .hideIf(() -> this.module.getActiveMode() != this);

    private int jumpTicks;
    private int rotationHeldTicks;
    private Vec2f heldRotation;

    public JumpResetVelocity(final VelocityModule module) {
        super(module);
        module.addProperties(this.rotate, this.followDirection, this.rotateTicks, this.debug);
    }

    @Subscribe(priority = 1)
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (mc.player == null || mc.world == null || this.module.isInvalid()
                || !(event.getPacket() instanceof EntityVelocityUpdateS2CPacket velocityPacket)
                || velocityPacket.getEntityId() != mc.player.getId()) {
            return;
        }

        this.setHeldRotation(this.getKnockbackRotation(velocityPacket.getVelocity()));

        if (mc.player.isOnGround()) {
            this.jumpTicks = 1;
            this.debugLog("queue jump reset");
        }
    }

    @Subscribe
    public void onPreTick(final PreGameTickEvent event) {
        if (mc.player == null || mc.world == null || this.module.isInvalid()) {
            this.resetAll();
            return;
        }

        this.tickHeldRotation();
    }

    @Subscribe(priority = 2)
    public void onMoveInput(final MoveInputEvent event) {
        if (mc.player == null) {
            return;
        }

        if (this.followDirection.getValue() && this.heldRotation != null) {
            event.setForward(1.0F);
            event.setSideways(0.0F);
        }

        if (this.jumpTicks > 0) {
            ((LivingEntityAccessor) mc.player).setJumpingCooldown(0);
            event.setJump(true);
            this.jumpTicks--;
            this.debugLog("jump reset");
        }
    }

    private Vec2f getKnockbackRotation(final Vec3d velocity) {
        if (!this.rotate.getValue() && !this.followDirection.getValue()) {
            return null;
        }

        final float yaw = (float) Math.toDegrees(Math.atan2(velocity.x, -velocity.z));
        return new Vec2f(yaw, mc.player.getPitch());
    }

    private void tickHeldRotation() {
        if (this.heldRotation == null || mc.player == null) {
            return;
        }

        RotationHelper.getHandler().rotate(this.heldRotation, InstantRotationModel.INSTANCE);
        this.rotationHeldTicks++;
        if (mc.player.hurtTime == 0
                || this.rotationHeldTicks > this.rotateTicks.getValue().intValue()
                || (!this.rotate.getValue() && !this.followDirection.getValue())) {
            this.clearRotation();
        }
    }

    private void setHeldRotation(final Vec2f rotation) {
        if (rotation == null) {
            return;
        }

        this.heldRotation = rotation;
        this.rotationHeldTicks = 0;
        RotationHelper.getHandler().rotate(rotation, InstantRotationModel.INSTANCE);
    }

    private void clearRotation() {
        this.heldRotation = null;
        this.rotationHeldTicks = 0;
    }

    private void resetAll() {
        this.jumpTicks = 0;
        this.clearRotation();
    }

    private void debugLog(final String message) {
        if (!this.debug.getValue()) {
            return;
        }

        final String text = "AntiKB JumpReset | " + message;
        if (mc.isOnThread()) {
            ChatUtility.print(text);
        } else {
            mc.execute(() -> ChatUtility.print(text));
        }
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.resetAll();
    }

    @Override
    public void onDisable() {
        this.resetAll();
        super.onDisable();
    }

    @Override
    public boolean isDelaying() {
        return false;
    }

    @Override
    public boolean hasQueuedPackets() {
        return false;
    }

    @Override
    public boolean shouldStopBacktrack() {
        return false;
    }

    @Override
    public Enum<?> getEnumValue() {
        return VelocityModule.Mode.JUMP_RESET;
    }

    @Override
    public String getSuffix() {
        return "JumpReset";
    }
}

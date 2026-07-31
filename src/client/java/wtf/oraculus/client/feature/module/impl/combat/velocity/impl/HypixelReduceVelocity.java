package wtf.oraculus.client.feature.module.impl.combat.velocity.impl;

import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import wtf.oraculus.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.oraculus.client.feature.helper.impl.player.rotation.model.impl.InstantRotationModel;
import wtf.oraculus.client.feature.module.impl.combat.velocity.VelocityMode;
import wtf.oraculus.client.feature.module.impl.combat.velocity.VelocityModule;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.input.MoveInputEvent;
import wtf.oraculus.event.impl.game.packet.ReceivePacketEvent;
import wtf.oraculus.event.impl.game.player.interaction.AttackEvent;
import wtf.oraculus.event.impl.game.player.movement.KeepSprintEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.mixin.LivingEntityAccessor;
import wtf.oraculus.utility.misc.chat.ChatUtility;
import wtf.oraculus.utility.player.MoveUtility;

import static wtf.oraculus.client.Constants.mc;

/**
 * A packet-safe interpretation of the legacy Reduce strategy. The reduction is
 * applied to the player's real attack instead of injecting a duplicate attack.
 */
public final class HypixelReduceVelocity extends VelocityMode {

    private final NumberProperty horizontal = new NumberProperty("Horizontal", "%", 60.0D, 0.0D, 100.0D, 1.0D)
            .id("hypixelReduceHorizontal")
            .hideIf(() -> this.module.getActiveMode() != this);
    private final NumberProperty windowTicks = new NumberProperty("Window", " ticks", 3.0D, 1.0D, 10.0D, 1.0D)
            .id("hypixelReduceWindow")
            .hideIf(() -> this.module.getActiveMode() != this);
    private final BooleanProperty jumpReset = new BooleanProperty("Jump Reset", true)
            .id("hypixelReduceJumpReset")
            .hideIf(() -> this.module.getActiveMode() != this);
    private final BooleanProperty rotate = new BooleanProperty("Rotate", false)
            .id("hypixelReduceRotate")
            .hideIf(() -> this.module.getActiveMode() != this);
    private final BooleanProperty followDirection = new BooleanProperty("Follow Direction", false)
            .id("hypixelReduceFollowDirection")
            .hideIf(() -> this.module.getActiveMode() != this);
    private final NumberProperty rotateTicks = new NumberProperty("Rotate Ticks", 2.0D, 1.0D, 12.0D, 1.0D)
            .id("hypixelReduceRotateTicks")
            .hideIf(() -> this.module.getActiveMode() != this
                    || (!this.rotate.getValue() && !this.followDirection.getValue()));
    private final BooleanProperty debug = new BooleanProperty("Debug", false)
            .id("hypixelReduceDebug")
            .hideIf(() -> this.module.getActiveMode() != this);

    private int reduceTicks;
    private int rotationTicks;
    private int previousHurtTime;
    private boolean jumpQueued;
    private boolean suppressVanillaSlowdown;
    private Vec2f heldRotation;

    public HypixelReduceVelocity(final VelocityModule module) {
        super(module);
        module.addProperties(this.horizontal, this.windowTicks, this.jumpReset, this.rotate,
                this.followDirection, this.rotateTicks, this.debug);
    }

    @Subscribe(priority = 1)
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (!this.canOperate()
                || !(event.getPacket() instanceof EntityVelocityUpdateS2CPacket velocityPacket)
                || velocityPacket.getEntityId() != mc.player.getId()) {
            return;
        }

        this.reduceTicks = this.windowTicks.getValue().intValue();
        this.setHeldRotation(this.getKnockbackRotation(velocityPacket.getVelocity()));
        this.debugLog("armed for " + this.reduceTicks + " ticks");
    }

    @Subscribe(priority = 2)
    public void onAttack(final AttackEvent event) {
        if (!this.canOperate() || this.reduceTicks <= 0 || event.getTarget() == null
                || !event.getTarget().isAlive() || !MoveUtility.isMoving() || !mc.player.isSprinting()) {
            return;
        }

        final double multiplier = this.horizontal.getValue() / 100.0D;
        final Vec3d velocity = mc.player.getVelocity();
        mc.player.setVelocity(velocity.x * multiplier, velocity.y, velocity.z * multiplier);

        // PlayerEntity.attack applies its own 0.6 slowdown immediately after
        // AttackEvent. Cancelling KeepSprint for this one attack prevents an
        // unintended second multiplication.
        this.suppressVanillaSlowdown = true;
        this.reduceTicks = 0;
        this.debugLog("reduced horizontal velocity to " + this.horizontal.getValue().intValue() + "%");
    }

    @Subscribe(priority = 100)
    public void onKeepSprint(final KeepSprintEvent event) {
        if (this.suppressVanillaSlowdown) {
            event.setCancelled();
            this.suppressVanillaSlowdown = false;
        }
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (!this.canOperate()) {
            this.reset();
            return;
        }

        final int hurtTime = mc.player.hurtTime;
        if (this.jumpReset.getValue() && hurtTime == 9 && this.previousHurtTime != 9
                && mc.player.isOnGround() && mc.player.isSprinting()) {
            this.jumpQueued = true;
        }
        this.previousHurtTime = hurtTime;

        if (this.reduceTicks > 0) {
            this.reduceTicks--;
        }
        if (this.rotationTicks > 0 && this.heldRotation != null) {
            RotationHelper.getHandler().rotate(this.heldRotation, InstantRotationModel.INSTANCE);
            if (--this.rotationTicks == 0 || hurtTime == 0) {
                this.clearRotation();
            }
        }
        this.suppressVanillaSlowdown = false;
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
        if (this.jumpQueued) {
            ((LivingEntityAccessor) mc.player).setJumpingCooldown(0);
            event.setJump(true);
            this.jumpQueued = false;
            this.debugLog("jump reset");
        }
    }

    private boolean canOperate() {
        return mc.player != null && mc.world != null && !this.module.isInvalid()
                && !mc.player.isSpectator() && !mc.player.isInFluid()
                && !mc.player.isClimbing() && !mc.player.hasVehicle();
    }

    private Vec2f getKnockbackRotation(final Vec3d velocity) {
        if (!this.rotate.getValue() && !this.followDirection.getValue()) {
            return null;
        }
        return new Vec2f((float) Math.toDegrees(Math.atan2(velocity.x, -velocity.z)), mc.player.getPitch());
    }

    private void setHeldRotation(final Vec2f rotation) {
        if (rotation == null) {
            this.clearRotation();
            return;
        }
        this.heldRotation = rotation;
        this.rotationTicks = this.rotateTicks.getValue().intValue();
        RotationHelper.getHandler().rotate(rotation, InstantRotationModel.INSTANCE);
    }

    private void clearRotation() {
        this.heldRotation = null;
        this.rotationTicks = 0;
    }

    private void reset() {
        this.reduceTicks = 0;
        this.previousHurtTime = 0;
        this.jumpQueued = false;
        this.suppressVanillaSlowdown = false;
        this.clearRotation();
    }

    private void debugLog(final String message) {
        if (!this.debug.getValue()) {
            return;
        }
        ChatUtility.print("AntiKB Reduce | " + message);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.reset();
    }

    @Override
    public void onDisable() {
        this.reset();
        super.onDisable();
    }

    @Override
    public Enum<?> getEnumValue() {
        return VelocityModule.Mode.HYPIXEL_REDUCE;
    }
}

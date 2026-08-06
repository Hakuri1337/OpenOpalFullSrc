package wtf.oraculus.client.feature.module.impl.combat.velocity.impl;

import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import wtf.oraculus.client.feature.helper.impl.player.packet.blockage.block.holder.BlockHolder;
import wtf.oraculus.client.feature.helper.impl.player.packet.blockage.impl.InboundNetworkBlockage;
import wtf.oraculus.client.feature.module.impl.combat.velocity.VelocityMode;
import wtf.oraculus.client.feature.module.impl.combat.velocity.VelocityModule;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.input.MoveInputEvent;
import wtf.oraculus.event.impl.game.packet.InstantaneousReceivePacketEvent;
import wtf.oraculus.event.impl.game.player.movement.knockback.VelocityUpdateEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import mixin.LivingEntityAccessor;
import wtf.oraculus.utility.misc.time.Stopwatch;

import static wtf.oraculus.client.Constants.mc;

public final class NormalVelocity extends VelocityMode {

    private final NumberProperty horizontal = new NumberProperty("Horizontal", "%", 0, 0, 100, 1).hideIf(() -> this.module.getActiveMode() != this),
            vertical = new NumberProperty("Vertical", "%", 100, 0, 100, 1).hideIf(() -> this.module.getActiveMode() != this);

    private final BooleanProperty onlyWhileTargeting = new BooleanProperty("Only while targeting", false).hideIf(() -> this.module.getActiveMode() != this);
    private final BooleanProperty delayUntilGround = new BooleanProperty("Delay until ground", false).id("delayUntilGroundNormal").hideIf(() -> this.module.getActiveMode() != this);
    private final BooleanProperty jumpOnGround = new BooleanProperty("Jump on ground", false).hideIf(() -> this.module.getActiveMode() != this);

    public NormalVelocity(VelocityModule module) {
        super(module);
        module.addProperties(this.horizontal, this.vertical, this.onlyWhileTargeting, this.delayUntilGround, this.jumpOnGround);
    }

    private final BlockHolder blockHolder = new BlockHolder(InboundNetworkBlockage.get());
    private final Stopwatch blockStopwatch = new Stopwatch();

    @Override
    public String getSuffix() {
        return this.horizontal.getValue().intValue() + " " + this.vertical.getValue().intValue();
    }

    @Subscribe
    public void onInstantaneousReceivePacket(final InstantaneousReceivePacketEvent event) {
        if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket velocity) {
            if (mc.player == null || velocity.getEntityId() != mc.player.getId() || !this.delayUntilGround.getValue()) {
                return;
            }
            if (mc.player.isOnGround()) {
                return;
            }

            this.blockHolder.block();
            this.blockStopwatch.reset();
        }
    }

    private boolean jump;

    @Subscribe
    public void onMoveInput(MoveInputEvent event) {
        if (this.jump) {
            ((LivingEntityAccessor) mc.player).setJumpingCooldown(0);
            event.setJump(true);
            this.jump = false;
        }
    }

    public NumberProperty getVertical() {
        return vertical;
    }

    public NumberProperty getHorizontal() {
        return horizontal;
    }

    @Subscribe
    public void onVelocityUpdate(final VelocityUpdateEvent event) {
        if (this.module.isInvalid()) {
            return;
        }

        final double horizontal = this.horizontal.getValue() / 100;
        final double vertical = this.vertical.getValue() / 100;

        event.setCancelled();

        if (!event.isExplosion() && (horizontal == 0 && vertical == 0)) {
            return;
        }

        final double velocityX = event.getVelocityX() * horizontal;
        final double velocityY = event.getVelocityY() * vertical;
        final double velocityZ = event.getVelocityZ() * horizontal;

        if (mc.player.isOnGround() && this.jumpOnGround.getValue()) {
            this.jump = true;
        }

        if (horizontal != 0) {
            mc.player.setVelocity(velocityX, mc.player.getVelocity().getY(), velocityZ);
        }
        if (vertical != 0) {
            mc.player.setVelocity(mc.player.getVelocity().getX(), velocityY, mc.player.getVelocity().getZ());
        }
    }

    @Subscribe
    public void onScheduledExecutables(final PreGameTickEvent event) {
        if (this.blockHolder.isBlocking()) {
            if (mc.player == null || mc.player.isOnGround() || mc.player.isInFluid() || mc.player.isClimbing() || this.blockStopwatch.hasTimeElapsed(1000L)) {
                this.blockHolder.release();
            }
        }
    }

    @Override
    public void onDisable() {
        this.blockHolder.release();
        super.onDisable();
    }

    public BooleanProperty getDelayUntilGround() {
        return delayUntilGround;
    }

    @Override
    public Enum<?> getEnumValue() {
        return VelocityModule.Mode.NORMAL;
    }
}

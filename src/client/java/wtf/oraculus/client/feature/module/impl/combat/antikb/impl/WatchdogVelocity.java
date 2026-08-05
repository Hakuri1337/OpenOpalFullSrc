package wtf.oraculus.client.feature.module.impl.combat.antikb.impl;

import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.MathHelper;
import wtf.oraculus.client.feature.helper.impl.LocalDataWatch;
import wtf.oraculus.client.feature.module.impl.combat.antikb.packet.block.holder.BlockHolder;
import wtf.oraculus.client.feature.module.impl.combat.antikb.packet.impl.InboundNetworkBlockage;
import wtf.oraculus.client.feature.helper.impl.server.impl.HypixelServer;
import wtf.oraculus.client.feature.module.impl.combat.antikb.AntiKBMode;
import wtf.oraculus.client.feature.module.impl.combat.antikb.AntiKBModule;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.input.MoveInputEvent;
import wtf.oraculus.event.impl.game.packet.InstantaneousReceivePacketEvent;
import wtf.oraculus.event.impl.game.player.movement.knockback.VelocityUpdateEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.mixin.LivingEntityAccessor;
import wtf.oraculus.utility.misc.time.Stopwatch;
import wtf.oraculus.client.feature.module.impl.combat.antikb.utility.AmadeusMoveUtility;

import static wtf.oraculus.client.Constants.mc;

public final class WatchdogVelocity extends AntiKBMode {

    private final BooleanProperty delayUntilGround = new BooleanProperty("Delay until ground", true).id("delayUntilGroundWatchdog").hideIf(() -> this.module.getActiveMode() != this);

    public WatchdogVelocity(AntiKBModule module) {
        super(module);
        module.addProperties(this.delayUntilGround);
    }

    private final BlockHolder blockHolder = new BlockHolder(InboundNetworkBlockage.get());
    private final Stopwatch blockStopwatch = new Stopwatch();

    @Override
    public String getSuffix() {
        return "Watchdog";
    }

    public BooleanProperty getDelayUntilGround() {
        return delayUntilGround;
    }

    @Subscribe
    public void onInstantaneousReceivePacket(final InstantaneousReceivePacketEvent event) {
        if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket velocity) {
            if (mc.player == null || velocity.getEntityId() != mc.player.getId() || !this.delayUntilGround.getValue()) {
                return;
            }
            if (getModule().isInvalid()) {
                return;
            }

            this.blockHolder.block(null, InboundNetworkBlockage.VISUAL_VALIDATOR);
            this.blockStopwatch.reset();
        } else if (event.getPacket() instanceof PlayerPositionLookS2CPacket) {
            this.blockHolder.release();
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

    @Subscribe
    public void onVelocityUpdate(final VelocityUpdateEvent event) {
        if (this.module.isInvalid()) {
            return;
        }

        final double velocityY = event.getVelocityY();

        if (event.isExplosion()) {
            this.blockHolder.release();
            return;
        }

        if (mc.player.isOnGround() && this.delayUntilGround.getValue() && LocalDataWatch.get().getKnownServerManager().getCurrentServer() instanceof HypixelServer) {
            final LivingEntityAccessor accessor = (LivingEntityAccessor) mc.player;
            if (mc.player.input.playerInput.jump() || accessor.callGetJumpVelocity() < velocityY) {
                this.jump = true;
            }
        }

        this.sprintResetTicks = 10;
    }

    private int sprintResetTicks;

    public boolean isSprintReset() {
        return this.sprintResetTicks > 0 && MathHelper.angleBetween(AmadeusMoveUtility.getMoveYaw(), AmadeusMoveUtility.getDirectionDegrees()) >= 70.0F;
    }

    @Subscribe
    public void onScheduledExecutables(final PreGameTickEvent event) {
//        if (event.isTick()) {
        if (this.blockHolder.isBlocking()) {
            if (mc.player == null || mc.player.isOnGround() || mc.player.isClimbing() || mc.player.isInFluid() || this.blockStopwatch.hasTimeElapsed(1000L)) {
                this.blockHolder.release();
            }
        }

        if (this.sprintResetTicks > 0) {
            this.sprintResetTicks--;
        }
//        }
    }

    @Override
    public void onDisable() {
        this.blockHolder.release();
        super.onDisable();
    }

    @Override
    public Enum<?> getEnumValue() {
        return AntiKBModule.Mode.WATCHDOG;
    }
}

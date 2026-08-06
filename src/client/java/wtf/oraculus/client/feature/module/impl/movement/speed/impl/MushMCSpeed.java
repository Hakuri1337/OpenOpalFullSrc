package wtf.oraculus.client.feature.module.impl.movement.speed.impl;

import wtf.oraculus.client.feature.helper.impl.LocalDataWatch;
import wtf.oraculus.client.feature.module.impl.movement.speed.SpeedModule;
import wtf.oraculus.client.feature.module.property.impl.mode.ModuleMode;
import wtf.oraculus.event.impl.game.input.MoveInputEvent;
import wtf.oraculus.event.impl.game.player.movement.JumpEvent;
import wtf.oraculus.event.impl.game.player.movement.PostMoveEvent;
import wtf.oraculus.event.impl.game.player.movement.PreMovementPacketEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.player.MoveUtility;

import static wtf.oraculus.client.Constants.mc;

public final class MushMCSpeed extends ModuleMode<SpeedModule> {
    public MushMCSpeed(SpeedModule module) {
        super(module);
    }

    @Override
    public Enum<?> getEnumValue() {
        return SpeedModule.Mode.MUSHMC;
    }

    private int offset;
    private double speed;

    @Subscribe
    public void onJump(final JumpEvent event) {
        if (this.offset > 0) {
            event.setCancelled();
        } else {
            event.setSprinting(true);
        }
    }

    @Subscribe
    public void onMoveInput(final MoveInputEvent event) {
//        event.setJump(true);
    }

    @Subscribe
    public void onPostMove(final PostMoveEvent event) {
        if (MoveUtility.isMoving()) {
            double speed = MoveUtility.getSpeed();
            if (mc.player.isOnGround()) {
                if (mc.player.getVelocity().getY() < 0.0D) {
                    if (this.offset == 0 && LocalDataWatch.get().groundTicks > 1) {
                        speed = this.speed;
                        this.speed = Math.min(this.speed + 0.05D, 0.5D);
                        this.offset = 2;
                    }
                } else {
                    mc.player.setVelocity(mc.player.getVelocity().subtract(0.0D, 0.02D, 0.0D));
                    this.resetSpeed();
                }
            } else {
                final int airTicks = LocalDataWatch.get().airTicks;
                // you can make this much lower (ground speed is legit y port) but no point
                if (airTicks == 4) {
                    mc.player.setVelocity(mc.player.getVelocity().subtract(0.0D, 0.2D, 0.0D));
                } else if (airTicks == 5) {
                    mc.player.setVelocity(mc.player.getVelocity().subtract(0.0D, 0.13D, 0.0D));
                }
                this.resetSpeed();
            }
            if (speed < 0.29D) {
                speed = 0.29D;
            }
            MoveUtility.setSpeed(speed);
        } else {
            MoveUtility.setSpeed(0);
            this.resetSpeed();
        }
    }

    @Subscribe
    public void onPreMovementPacket(final PreMovementPacketEvent event) {
        if (this.offset > 0) {
            if (mc.player.isOnGround()) {
                if (this.offset == 2) {
                    event.setY(event.getY() + 0.0625D);
                    event.setOnGround(false);
                }
                this.offset--;
            } else {
                this.offset = 0;
            }
        }
    }

    @Override
    public void onEnable() {
        this.resetSpeed();
        super.onEnable();
    }

    private void resetSpeed() {
        this.speed = Math.max(0.29D, mc.player == null ? 0.0D : MoveUtility.getSpeed());
    }
}

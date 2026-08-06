package wtf.oraculus.client.feature.module.impl.movement.speed.impl;

import wtf.oraculus.client.feature.module.impl.movement.speed.SpeedModule;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.mode.ModuleMode;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.event.impl.game.input.MoveInputEvent;
import wtf.oraculus.event.impl.game.player.movement.PostMoveEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.player.MoveUtility;

import static wtf.oraculus.client.Constants.mc;

public final class VanillaSpeed extends ModuleMode<SpeedModule> {

    private final NumberProperty speedProperty = new NumberProperty("Speed", this, 1.D, 0.1D, 10.D, 0.1D).hideIf(() -> this.module.getActiveMode() != this);
    private final BooleanProperty autoJump = new BooleanProperty("Auto jump", this, true).hideIf(() -> this.module.getActiveMode() != this);

    public VanillaSpeed(SpeedModule module) {
        super(module);
    }

    @Override
    public Enum<?> getEnumValue() {
        return SpeedModule.Mode.VANILLA;
    }

    @Subscribe
    public void onPostMove(PostMoveEvent event) {
        final double speed = this.speedProperty.getValue();
        if (MoveUtility.isMoving()) {
            MoveUtility.setSpeed(speed);
        } else {
            MoveUtility.setSpeed(0);
        }
    }

    @Subscribe
    public void onMoveInput(MoveInputEvent event) {
        if (this.autoJump.getValue() && MoveUtility.isMoving()) {
            event.setJump(true);
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (mc.player == null) return;
        final double maxSpeed = MoveUtility.getSwiftnessSpeed(0.221D);
        MoveUtility.setSpeed(Math.min(MoveUtility.getSpeed(), maxSpeed));
    }
}

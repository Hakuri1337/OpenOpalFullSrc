package wtf.oraculus.client.feature.module.impl.movement.speed.impl;

import wtf.oraculus.client.feature.module.impl.movement.speed.SpeedModule;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.mode.ModuleMode;
import wtf.oraculus.event.impl.game.input.MoveInputEvent;
import wtf.oraculus.event.impl.game.player.movement.PostMoveEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.player.MoveUtility;

public final class StrafeSpeed extends ModuleMode<SpeedModule> {
    private final BooleanProperty fastStop = new BooleanProperty("Fast stop", true).hideIf(() -> module.getActiveMode() != this);

    public StrafeSpeed(SpeedModule module) {
        super(module);
        module.addProperties(this.fastStop);
    }

    @Override
    public Enum<?> getEnumValue() {
        return SpeedModule.Mode.STRAFE;
    }

    @Subscribe
    public void onPostMove(PostMoveEvent event) {
        if (MoveUtility.isMoving()) {
            MoveUtility.setSpeed(MoveUtility.getSpeed());
        } else if (this.fastStop.getValue()) {
            MoveUtility.setSpeed(0);
        }
    }

    @Subscribe
    public void onMoveInput(MoveInputEvent event) {
        if (MoveUtility.isMoving()) {
            event.setJump(true);
        }
    }
}

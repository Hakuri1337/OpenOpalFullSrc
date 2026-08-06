package wtf.oraculus.event.impl.game.player.movement.step;

import net.minecraft.util.math.Vec3d;
import wtf.oraculus.event.EventCancellable;

public final class StepSuccessEvent extends EventCancellable {

    private Vec3d movement;
    private Vec3d adjustedVec;

    public StepSuccessEvent(final Vec3d movement, final Vec3d adjustedVec) {
        this.movement = movement;
        this.adjustedVec = adjustedVec;
    }

    public Vec3d getMovement() {
        return movement;
    }

    public void setAdjustedVec(Vec3d adjustedVec) {
        this.adjustedVec = adjustedVec;
    }

    public void setMovement(Vec3d movement) {
        this.movement = movement;
    }

    public Vec3d getAdjustedVec() {
        return adjustedVec;
    }
}

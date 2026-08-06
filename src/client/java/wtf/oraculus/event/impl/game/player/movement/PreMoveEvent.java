package wtf.oraculus.event.impl.game.player.movement;

import net.minecraft.util.math.Vec3d;
import wtf.oraculus.event.EventCancellable;

public final class PreMoveEvent extends EventCancellable {

    private final float speed;
    private final Vec3d movementInput;

    public PreMoveEvent(float speed, Vec3d movementInput) {
        this.speed = speed;
        this.movementInput = movementInput;
    }

    public float getSpeed() {
        return speed;
    }

    public Vec3d getMovementInput() {
        return movementInput;
    }
}

package wtf.oraculus.event.impl.game.player.movement;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.Vec3d;
import wtf.oraculus.event.EventCancellable;

public final class StuckInBlockEvent extends EventCancellable {

    private final BlockState blockState;
    private Vec3d motion;

    public StuckInBlockEvent(final BlockState blockState, final Vec3d motion) {
        this.blockState = blockState;
        this.motion = motion;
    }

    public BlockState getBlockState() {
        return blockState;
    }

    public Vec3d getMotion() {
        return motion;
    }

    public void setMotion(final Vec3d motion) {
        this.motion = motion;
    }
}

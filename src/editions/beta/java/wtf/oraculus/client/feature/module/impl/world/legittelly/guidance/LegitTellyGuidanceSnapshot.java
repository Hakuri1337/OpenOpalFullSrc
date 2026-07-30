package wtf.oraculus.client.feature.module.impl.world.legittelly.guidance;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public record LegitTellyGuidanceSnapshot(
        BlockPos supportPos,
        Direction face,
        Vec3d aimPoint,
        String stage,
        Severity severity,
        boolean activationWindow
) {
    public enum Severity {
        INFO,
        ADJUST,
        READY,
        BLOCKED
    }
}

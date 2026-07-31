package wtf.oraculus.client.feature.module.impl.world.fucker;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public record FuckerTarget(BlockPos pos, FuckerModule.Action action, Vec3d aimPoint, boolean directTarget, FuckerPathInfo pathInfo) implements Comparable<FuckerTarget> {
    @Override
    public int compareTo(final FuckerTarget other) {
        if (directTarget != other.directTarget) return directTarget ? -1 : 1;
        if (pathInfo == null && other.pathInfo != null) return -1;
        if (pathInfo != null && other.pathInfo == null) return 1;
        return pathInfo == null ? 0 : pathInfo.compareTo(other.pathInfo);
    }
}

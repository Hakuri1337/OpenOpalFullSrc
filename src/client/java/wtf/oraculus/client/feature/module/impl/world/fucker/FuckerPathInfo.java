package wtf.oraculus.client.feature.module.impl.world.fucker;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public record FuckerPathInfo(BlockPos actualTarget, Vec3d targetPoint, double resistance, int blockerCount,
                             double firstBlockDistanceToTarget, double firstBlockDistanceToEyes) implements Comparable<FuckerPathInfo> {
    @Override public int compareTo(final FuckerPathInfo other) {
        int result = Double.compare(resistance, other.resistance);
        if (result != 0) return result;
        result = Integer.compare(blockerCount, other.blockerCount);
        if (result != 0) return result;
        result = Double.compare(firstBlockDistanceToTarget, other.firstBlockDistanceToTarget);
        return result != 0 ? result : Double.compare(firstBlockDistanceToEyes, other.firstBlockDistanceToEyes);
    }
}

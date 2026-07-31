package wtf.oraculus.client.feature.module.impl.world.fucker;

import net.minecraft.util.math.BlockPos;
import java.util.List;

public record FuckerPath(BlockPos firstBlock, List<BlockPos> blocks, FuckerPathInfo info) implements Comparable<FuckerPath> {
    @Override public int compareTo(final FuckerPath other) { return info.compareTo(other.info()); }
}

package wtf.oraculus.client.feature.module.impl.world.legittelly;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;

public record LegitTellyTarget(
        BlockPos placePos,
        BlockPos supportPos,
        BlockHitResult hit,
        Vec2f rotation,
        double score
) {
}

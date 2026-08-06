package wtf.oraculus.mixin;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wtf.oraculus.event.EventDispatcher;
import wtf.oraculus.event.impl.game.player.interaction.block.BlockBreakHardnessEvent;

@Mixin(AbstractBlock.AbstractBlockState.class)
public final class AbstractBlockStateMixin {

    @Inject(
            method = "getHardness",
            at = @At("RETURN"),
            cancellable = true
    )
    private void hookGetHardness(BlockView world, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (((AbstractBlock.AbstractBlockState) (Object) this) instanceof BlockState blockState) {
            final BlockBreakHardnessEvent event = new BlockBreakHardnessEvent(blockState, cir.getReturnValue());
            EventDispatcher.dispatch(event);
            cir.setReturnValue(event.getHardness());
        }
    }
}

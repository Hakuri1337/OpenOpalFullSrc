package mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import wtf.oraculus.event.EventDispatcher;
import wtf.oraculus.event.impl.game.player.interaction.block.BlockBreakCanHarvestEvent;

@Mixin(AbstractBlock.class)
public final class AbstractBlockMixin {
    private AbstractBlockMixin() {
    }

    @ModifyExpressionValue(
            method = "calcBlockBreakingDelta",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;canHarvest(Lnet/minecraft/block/BlockState;)Z")
    )
    private boolean redirectCanHarvest(boolean original, @Local(argsOnly = true) BlockState state) {
        final BlockBreakCanHarvestEvent event = new BlockBreakCanHarvestEvent(state, original);
        EventDispatcher.dispatch(event);
        return event.isCanHarvest();
    }
}

package mixin;

import net.minecraft.client.render.RenderTickCounter;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wtf.oraculus.client.feature.helper.impl.player.timer.TimerHelper;

@Mixin(RenderTickCounter.Dynamic.class)
public final class RenderTickCounterDynamicMixin {

    @Shadow
    private float dynamicDeltaTicks;

    private RenderTickCounterDynamicMixin() {
    }

    @Inject(at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/render/RenderTickCounter$Dynamic;lastTimeMillis:J",
            opcode = Opcodes.PUTFIELD,
            ordinal = 0), method = "beginRenderTick(J)I")
    public void onBeginRenderTick(long timeMillis,
                                  CallbackInfoReturnable<Integer> cir) {
        final float timer = TimerHelper.getInstance().timer;
        if (timer > 0) {
            dynamicDeltaTicks *= timer;
        }
    }

}

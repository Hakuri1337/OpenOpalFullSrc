package wtf.oraculus.mixin;

import net.minecraft.client.Keyboard;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.oraculus.event.EventDispatcher;
import wtf.oraculus.event.impl.press.KeyPressEvent;

@Mixin(Keyboard.class)
public final class KeyboardMixin {

    private KeyboardMixin() {
    }

    @Inject(
            at = @At("HEAD"),
            method = "onKey"
    )
    public void onKey(long window, int action, KeyInput input, CallbackInfo ci) {
        if (action == 1) {
            if (input.key() == -1) {
                return;
            }

            EventDispatcher.dispatch(new KeyPressEvent(input.key()));
        }
    }
}

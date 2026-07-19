package wtf.oraculus.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.PlayerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import wtf.oraculus.event.EventDispatcher;
import wtf.oraculus.event.impl.game.input.MoveInputEvent;

import static wtf.oraculus.client.Constants.mc;

@Mixin(KeyboardInput.class)
public final class KeyboardInputMixin {

    @Unique
    private MoveInputEvent moveInputEvent;

    private KeyboardInputMixin() {
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/KeyBinding;isPressed()Z", ordinal = 4))
    private boolean hookMoveInputEventJump(KeyBinding instance) {
        return moveInputEvent != null && moveInputEvent.isJump();
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/KeyboardInput;getMovementMultiplier(ZZ)F", ordinal = 0))
    private float hookMoveInputEventForward(final boolean positive, boolean negative) {
        return moveInputEvent == null ? 0 : moveInputEvent.getForward();
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/KeyboardInput;getMovementMultiplier(ZZ)F", ordinal = 1))
    private float hookMoveInputEventStrafe(final boolean positive, boolean negative) {
        return moveInputEvent == null ? 0 : moveInputEvent.getSideways();
    }

    @ModifyExpressionValue(method = "tick", at = @At(value = "NEW", target = "(ZZZZZZZ)Lnet/minecraft/util/PlayerInput;"))
    private PlayerInput modifyInput(PlayerInput original) {
        moveInputEvent = new MoveInputEvent(
                getMovementMultiplier(mc.options.forwardKey.isPressed(), mc.options.backKey.isPressed()),
                getMovementMultiplier(mc.options.leftKey.isPressed(), mc.options.rightKey.isPressed()),
                mc.options.jumpKey.isPressed(), original.sneak()
        );
        EventDispatcher.dispatch(moveInputEvent);

        return new PlayerInput(
                moveInputEvent.getForward() > 0,
                moveInputEvent.getForward() < 0,
                moveInputEvent.getSideways() > 0,
                moveInputEvent.getSideways() < 0,
                moveInputEvent.isJump(),
                moveInputEvent.isSneak(),
                original.sprint()
        );
    }

    @Unique
    private static float getMovementMultiplier(boolean positive, boolean negative) {
        if (positive == negative) {
            return 0.0f;
        }
        return positive ? 1.0f : -1.0f;
    }

}

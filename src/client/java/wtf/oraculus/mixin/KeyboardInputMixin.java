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
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.module.impl.movement.InventoryMoveModule;

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
        final InventoryMoveModule inventoryMove = OraculusClient.getInstance()
                .getModuleRepository().getModule(InventoryMoveModule.class);
        final boolean forward = inventoryMove != null && inventoryMove.isPressed(mc.options.forwardKey)
                || mc.options.forwardKey.isPressed();
        final boolean back = inventoryMove != null && inventoryMove.isPressed(mc.options.backKey)
                || mc.options.backKey.isPressed();
        final boolean left = inventoryMove != null && inventoryMove.isPressed(mc.options.leftKey)
                || mc.options.leftKey.isPressed();
        final boolean right = inventoryMove != null && inventoryMove.isPressed(mc.options.rightKey)
                || mc.options.rightKey.isPressed();
        final boolean jump = inventoryMove != null && inventoryMove.isPressed(mc.options.jumpKey)
                || mc.options.jumpKey.isPressed();
        final boolean sneak = inventoryMove != null && inventoryMove.isPressed(mc.options.sneakKey)
                || mc.options.sneakKey.isPressed();
        final boolean sprint = inventoryMove == null ? original.sprint()
                : inventoryMove.shouldForceClientSprint(original.sprint(), forward || back || left || right);
        moveInputEvent = new MoveInputEvent(
                getMovementMultiplier(forward, back), getMovementMultiplier(left, right), jump, sneak
        );
        EventDispatcher.dispatch(moveInputEvent);

        return new PlayerInput(
                moveInputEvent.getForward() > 0,
                moveInputEvent.getForward() < 0,
                moveInputEvent.getSideways() > 0,
                moveInputEvent.getSideways() < 0,
                moveInputEvent.isJump(),
                moveInputEvent.isSneak(),
                sprint
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

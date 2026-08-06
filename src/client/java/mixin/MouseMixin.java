package mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.input.Scroller;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.entity.player.PlayerInventory;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.oraculus.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.oraculus.client.feature.helper.impl.player.slot.SlotHelper;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.edition.EditionHooks;
import wtf.oraculus.event.EventDispatcher;
import wtf.oraculus.event.impl.game.input.MouseUpdateEvent;
import wtf.oraculus.event.impl.press.MousePressEvent;

@Mixin(Mouse.class)
public final class
MouseMixin {

    private MouseMixin() {
    }

    @Inject(
            method = "onMouseButton",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onMouseButton(long window, MouseInput input, int action, CallbackInfo ci) {
        if (action == 1) {
            if (input.button() == -1) {
                return;
            }

            EventDispatcher.dispatch(new MousePressEvent(input.button()));

            if (OraculusClient.getInstance().isPostInitialization()) {
                if (EditionHooks.shouldCancelMouseButton(input.button())) {
                    ci.cancel();
                }
            }
        }
    }

    @Shadow
    private double cursorDeltaX;
    @Shadow
    private double cursorDeltaY;

    @Unique
    private MouseUpdateEvent event;

    @Redirect(
            method = "onMouseScroll",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerInventory;setSelectedSlot(I)V")
    )
    private void setSelectedSlot(PlayerInventory instance, int slot, @Local int i) {
        SlotHelper slotHelper = SlotHelper.getInstance();
        if (slotHelper.isActive()) {
            if (slotHelper.getSilence() != SlotHelper.Silence.NONE) {
                slotHelper.setVisualSlot(Scroller.scrollCycling(i, slotHelper.getVisualSlot(), PlayerInventory.getHotbarSize()));
            }
        } else {
            instance.setSelectedSlot(slot);
        }
    }

    @Redirect(
            method = "updateMouse",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/option/GameOptions;smoothCameraEnabled:Z", opcode = Opcodes.GETFIELD)
    )
    private boolean redirectCheck(GameOptions instance, @Local(ordinal = 3) double multiplier) {
        this.event = new MouseUpdateEvent(this.cursorDeltaX, this.cursorDeltaY, multiplier, this.unlockCursorRun);
        EventDispatcher.dispatch(this.event);
        return instance.smoothCameraEnabled && !this.event.isHandled();
    }

    @Unique
    private boolean unlockCursorRun;

    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Mouse;isCursorLocked()Z")
    )
    private boolean redirectTickCursorLock(Mouse instance) {
        if (instance.isCursorLocked()) {
            return true;
        }
        if (RotationHelper.getHandler().isUnlockCursor()) {
            this.unlockCursorRun = true;
            return true;
        }
        return false;
    }

    @Inject(
            method = "updateMouse",
            at = @At("TAIL")
    )
    private void updateMouseTail(double timeDelta, CallbackInfo ci) {
        RotationHelper.getClientHandler().onPostMouseUpdate();
        this.unlockCursorRun = false;
        this.event = null;
    }

    @Redirect(
            method = "updateMouse",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingSpyglass()Z")
    )
    private boolean redirectCheck(ClientPlayerEntity instance) {
        return instance.isUsingSpyglass() && (this.event == null || !this.event.isHandled());
    }

    @Redirect(
            method = "updateMouse",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/Mouse;cursorDeltaX:D", opcode = Opcodes.GETFIELD)
    )
    private double redirectCursorX(Mouse instance) {
        return this.event == null ? 0 : this.event.getDeltaX();
    }

    @Redirect(
            method = "updateMouse",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/Mouse;cursorDeltaY:D", opcode = Opcodes.GETFIELD)
    )
    private double redirectCursorY(Mouse instance) {
        return this.event == null ? 0 : this.event.getDeltaY();
    }
}

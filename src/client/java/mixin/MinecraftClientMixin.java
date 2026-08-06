package mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.RunArgs;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.helper.impl.player.mouse.MouseButton;
import wtf.oraculus.client.feature.helper.impl.player.mouse.MouseHelper;
import wtf.oraculus.client.feature.helper.impl.player.slot.SlotHelper;
import wtf.oraculus.client.feature.module.impl.combat.BlockModule;
import wtf.oraculus.client.feature.module.impl.visual.AnimationsModule;
import wtf.oraculus.duck.ClientPlayerEntityAccess;
import wtf.oraculus.event.EventDispatcher;
import wtf.oraculus.event.impl.game.JoinWorldEvent;
import wtf.oraculus.event.impl.game.PostGameTickEvent;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.ScheduledExecutablesEvent;
import wtf.oraculus.event.impl.game.input.MouseHandleInputEvent;
import wtf.oraculus.event.impl.game.input.PostHandleInputEvent;
import wtf.oraculus.event.impl.game.player.rotation.SsngRotationAppliedEvent;
import wtf.oraculus.event.impl.game.player.rotation.SsngRotationCalculationEvent;
import wtf.oraculus.event.impl.game.player.interaction.AttackDelayEvent;
import wtf.oraculus.event.impl.game.player.interaction.ItemUseEvent;
import wtf.oraculus.event.impl.game.player.interaction.block.BlockPlacedEvent;
import wtf.oraculus.event.impl.game.player.interaction.block.SsngVanillaPlaceEvent;
import wtf.oraculus.event.impl.game.server.ServerDisconnectEvent;
import wtf.oraculus.event.impl.render.ResolutionChangeEvent;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Shadow
    protected abstract boolean doAttack();

    @Shadow
    protected int attackCooldown;

    @Shadow
    @Nullable
    public HitResult crosshairTarget;

    @Shadow
    @Nullable
    public ClientPlayerEntity player;

    private MinecraftClientMixin() {
    }

    @Inject(
            method = "<init>",
            at = @At("TAIL")
    )
    private void postInitialization(final RunArgs args, final CallbackInfo ci) {
        OraculusClient.getInstance().runBootstrapInitializations();
    }

    @Inject(
            method = "handleInputEvents",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z", ordinal = 0),
            cancellable = true
    )
    private void handleInputEventsMouse(final CallbackInfo info) {
        final MouseHandleInputEvent event = new MouseHandleInputEvent();
        EventDispatcher.dispatch(event);
        if (event.isCancelled()) {
            info.cancel();
        }
    }

    @Inject(
            method = "handleInputEvents",
            at = @At("TAIL")
    )
    private void handleInputEventsTail(final CallbackInfo ci) {
        MouseHelper.getInstance().tick();

        EventDispatcher.dispatch(new PostHandleInputEvent());
    }

    @Redirect(
            method = "doAttack",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;swingHand(Lnet/minecraft/util/Hand;)V")
    )
    private void redirectAttackSwings(ClientPlayerEntity instance, Hand hand) {
        final MouseButton leftButton = MouseHelper.getLeftButton();
        if (leftButton.isShowSwings()) {
            instance.swingHand(hand);
        } else {
            ((ClientPlayerEntityAccess) instance).oraculus$swingHandServerside(hand);
        }
    }

    @Redirect(
            method = "doItemUse",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;swingHand(Lnet/minecraft/util/Hand;)V")
    )
    private void redirectUseSwings(ClientPlayerEntity instance, Hand hand) {
        final MouseButton rightButton = MouseHelper.getRightButton();
        if (rightButton.isShowSwings()) {
            instance.swingHand(hand);
        } else {
            ((ClientPlayerEntityAccess) instance).oraculus$swingHandServerside(hand);
        }
    }

    @Redirect(
            method = "doItemUse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;interactEntityAtLocation(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/hit/EntityHitResult;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;"
            )
    )
    private ActionResult normalizeLivingEntityInteractionPoint(
            ClientPlayerInteractionManager interactionManager,
            PlayerEntity player,
            Entity entity,
            EntityHitResult hitResult,
            Hand hand
    ) {
        // Some server/proxy stacks expose the vanilla INTERACT fallback as
        // INTERACT_AT at the entity origin. Keep both packet positions equal.
        final EntityHitResult interactionHit = entity instanceof LivingEntity && !(entity instanceof ArmorStandEntity)
                ? new EntityHitResult(entity)
                : hitResult;
        return interactionManager.interactEntityAtLocation(player, entity, interactionHit, hand);
    }

    @Redirect(
            method = "handleInputEvents",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerInventory;setSelectedSlot(I)V")
    )
    private void redirectSelectedSlot(PlayerInventory instance, int value) {
        SlotHelper slotHelper = SlotHelper.getInstance();
        if (slotHelper.isActive()) {
            if (slotHelper.getSilence() != SlotHelper.Silence.NONE) {
                slotHelper.setVisualSlot(value);
            }
        } else {
            instance.setSelectedSlot(value);
        }
    }

    @Inject(
            method = "handleInputEvents",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/option/GameOptions;socialInteractionsKey:Lnet/minecraft/client/option/KeyBinding;", shift = At.Shift.BEFORE)
    )
    private void postSlotHandleInput(CallbackInfo ci) {
        SlotHelper.getInstance().sync(false, false);
    }

    @Redirect(
            method = "handleInputEvents",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/KeyBinding;isPressed()Z")
    )
    private boolean redirectIsPressed(KeyBinding instance) {
        final MouseButton mouseButton = MouseHelper.getButtonFromBinding(instance);
        if (mouseButton != null) {
            return mouseButton.isPressed();
        }
        return instance.isPressed();
    }

    @Redirect(
            method = "handleInputEvents",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/KeyBinding;wasPressed()Z"),
            slice = @Slice(
                    from = @At(value = "FIELD", target = "Lnet/minecraft/client/option/GameOptions;useKey:Lnet/minecraft/client/option/KeyBinding;", ordinal = 1),
                    to = @At("TAIL")
            )
    )
    private boolean redirectWasPressed(KeyBinding instance) {
        final MouseButton mouseButton = MouseHelper.getButtonFromBinding(instance);
        if (mouseButton != null) {
            return mouseButton.wasPressed();
        }
        return instance.wasPressed();
    }

    @Redirect(
            method = "handleInputEvents",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/KeyBinding;wasPressed()Z", ordinal = 11)
    )
    private boolean redirectUsingAttack(KeyBinding instance, @Local LocalBooleanRef bl3) {
        if (this.isSwingWhileUsing() && MouseHelper.getLeftButton().wasPressed()) {
            final boolean currentValue = bl3.get();
            final boolean newValue = currentValue | doAttack();
            bl3.set(newValue);
            return true;
        }
        return false;
    }

    @Inject(
            method = "doItemUse",
            at = @At("HEAD"),
            cancellable = true
    )
    private void hookItemUse(CallbackInfo ci) {
        final ItemUseEvent event = new ItemUseEvent();
        EventDispatcher.dispatch(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "handleInputEvents",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/option/GameOptions;useKey:Lnet/minecraft/client/option/KeyBinding;", ordinal = 1)
    )
    private void onItemUseMouseHandle(CallbackInfo ci) {
        final OraculusClient oraculus = OraculusClient.getInstance();
        if (!oraculus.isPostInitialization() || oraculus.getModuleRepository() == null) return;
        final AnimationsModule animationsModule = oraculus.getModuleRepository().getModule(AnimationsModule.class);
        final MouseButton leftButton = MouseHelper.getLeftButton();
        if (animationsModule.isEnabled() && animationsModule.isSwingWhileUsing() && leftButton.isPressed() && leftButton.isShowSwings()) {
            if ((this.crosshairTarget != null && this.crosshairTarget.getType() == HitResult.Type.BLOCK) || leftButton.wasPressed()) {
                ((ClientPlayerEntityAccess) this.player).oraculus$swingHandClientside(Hand.MAIN_HAND);
            }
        }

        //noinspection StatementWithEmptyBody
        while (leftButton.wasPressed()) ;
    }

    @Unique
    private boolean isSwingWhileUsing() {
        final BlockModule blockModule = OraculusClient.getInstance().getModuleRepository().getModule(BlockModule.class);
        return blockModule.isEnabled() && blockModule.isSwingAllowed();
    }

    @Redirect(
            method = "doAttack",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;attackCooldown:I", opcode = Opcodes.PUTFIELD)
    )
    private void onAttackCooldown(MinecraftClient instance, int value) {
        final AttackDelayEvent event = new AttackDelayEvent(value);
        EventDispatcher.dispatch(event);
        this.attackCooldown = event.getDelay();
    }

    @Inject(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;runTasks()V", shift = At.Shift.BEFORE)
    )
    private void onGameLoop(boolean tick, CallbackInfo ci, @Local(ordinal = 0) int ticks) {
        EventDispatcher.dispatch(new ScheduledExecutablesEvent(ticks > 0));
    }

    @Inject(
            method = "tick",
            at = @At("HEAD")
    )
    private void tickHead(final CallbackInfo info) {
        EventDispatcher.dispatch(new PreGameTickEvent());
    }

    @Inject(
            method = "doItemUse",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;interactBlock(Lnet/minecraft/client/network/ClientPlayerEntity;Lnet/minecraft/util/Hand;Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/util/ActionResult;"),
            cancellable = true
    )
    private void hookSsngVanillaPlace(final CallbackInfo ci) {
        final SsngVanillaPlaceEvent event = new SsngVanillaPlaceEvent();
        EventDispatcher.dispatch(event);
        if (event.isCancelled()) ci.cancel();
    }

    @Inject(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;updateCrosshairTarget(F)V", shift = At.Shift.BEFORE)
    )
    private void hookSsngRotationCalculation(final CallbackInfo info) {
        EventDispatcher.dispatch(new SsngRotationCalculationEvent());
    }

    @Inject(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;handleInputEvents()V", shift = At.Shift.BEFORE)
    )
    private void hookSsngRotationApplied(final CallbackInfo info) {
        EventDispatcher.dispatch(new SsngRotationAppliedEvent());
    }

    @Inject(
            method = "joinWorld",
            at = @At("HEAD")
    )
    private void hookJoinWorld(ClientWorld world, CallbackInfo ci) {
        EventDispatcher.dispatch(new JoinWorldEvent());
    }

    @Inject(
            method = "tick",
            at = @At("TAIL")
    )
    private void tickTail(final CallbackInfo info) {
        EventDispatcher.dispatch(new PostGameTickEvent());
    }

    @Inject(
            method = "onDisconnected",
            at = @At("HEAD")
    )
    private void disconnected(final CallbackInfo ci) {
        EventDispatcher.dispatch(new ServerDisconnectEvent());
    }

    @Inject(
            method = "isTelemetryEnabledByApi",
            at = @At("HEAD"),
            cancellable = true
    )
    private void disableTelemetry(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    @Inject(
            method = "onResolutionChanged",
            at = @At("HEAD")
    )
    private void resolutionChange(CallbackInfo ci) {
        EventDispatcher.dispatch(new ResolutionChangeEvent());
    }

    @Inject(
            method = "doItemUse",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ActionResult$Success;swingSource()Lnet/minecraft/util/ActionResult$SwingSource;", ordinal = 1)
    )
    private void hookBlockPlaceEvent(CallbackInfo ci, @Local BlockHitResult blockHitResult) {
        EventDispatcher.dispatch(new BlockPlacedEvent(blockHitResult));
    }

    @Redirect(
            method = "doItemUse",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;resetEquipProgress(Lnet/minecraft/util/Hand;)V", ordinal = 0)
    )
    private void redirectResetEquipProgress(HeldItemRenderer instance, Hand hand) {
        // prevent equip progress reset if placing a block but the visual item is not a block
        SlotHelper slotHelper = SlotHelper.getInstance();
        if (hand == Hand.MAIN_HAND && slotHelper.isActive() && !(slotHelper.getMainHandStack(player).getItem() instanceof BlockItem)) {
            return;
        }
        instance.resetEquipProgress(hand);
    }

}

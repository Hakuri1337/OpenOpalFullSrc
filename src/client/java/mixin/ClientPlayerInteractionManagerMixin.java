package mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.helper.impl.player.slot.SlotHelper;
import wtf.oraculus.client.irc.IrcAttackPolicy;
import wtf.oraculus.client.feature.module.impl.world.breaker.BreakerModule;
import wtf.oraculus.client.feature.module.impl.world.FastBreakModule;
import wtf.oraculus.duck.ClientPlayerInteractionManagerAccess;
import wtf.oraculus.event.EventDispatcher;
import wtf.oraculus.event.impl.game.player.interaction.AttackEvent;
import wtf.oraculus.event.impl.game.player.interaction.CancelBlockBreakingEvent;
import wtf.oraculus.event.impl.game.player.interaction.block.PostBlockInteractEvent;
import wtf.oraculus.event.impl.game.player.interaction.block.PreBlockInteractEvent;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin implements ClientPlayerInteractionManagerAccess {

    @Shadow
    private BlockPos currentBreakingPos;

    @Shadow
    private float currentBreakingProgress;

    private ClientPlayerInteractionManagerMixin() {
    }

    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void enforceIrcAttackPolicy(PlayerEntity player, Entity target, CallbackInfo callbackInfo) {
        if (!IrcAttackPolicy.canAttack(target)) {
            IrcAttackPolicy.notifyBlocked();
            callbackInfo.cancel();
        }
    }

    @Inject(
            method = "attackEntity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;syncSelectedSlot()V", shift = At.Shift.AFTER)
    )
    private void hookAttackEvent(PlayerEntity player, Entity target, CallbackInfo callbackInfo) {
        EventDispatcher.dispatch(new AttackEvent(target));
    }

    @Inject(
            method = "interactBlock",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;sendSequencedPacket(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/client/network/SequencedPacketCreator;)V")
    )
    private void handleBlockPlacementHead(final ClientPlayerEntity player, final Hand hand, final BlockHitResult hitResult, final CallbackInfoReturnable<ActionResult> cir) {
        EventDispatcher.dispatch(new PreBlockInteractEvent());
    }

    @Inject(
            method = "interactBlock",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;sendSequencedPacket(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/client/network/SequencedPacketCreator;)V", shift = At.Shift.AFTER)
    )
    private void handleBlockPlacementTail(final ClientPlayerEntity player, final Hand hand, final BlockHitResult hitResult, final CallbackInfoReturnable<ActionResult> cir) {
        EventDispatcher.dispatch(new PostBlockInteractEvent());
    }

    @Inject(
            method = "tick",
            at = @At("HEAD")
    )
    private void tick(CallbackInfo ci) {
        SlotHelper.getInstance().tick();
    }

    @Override
    public BlockPos oraculus$getCurrentBreakingPos() {
        return this.currentBreakingPos;
    }

    @Override
    public float oraculus$currentBreakingProgress() {
        return this.currentBreakingProgress;
    }

    @Redirect(
            method = "updateBlockBreakingProgress",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;blockBreakingCooldown:I", opcode = Opcodes.PUTFIELD, ordinal = 2)
    )
    private void redirectBreakingCooldown(ClientPlayerInteractionManager instance, int value) {
        final FastBreakModule fastBreakModule = OraculusClient.getInstance().getModuleRepository().getModule(FastBreakModule.class);
        if (fastBreakModule.isEnabled() && fastBreakModule.isBreakCooldownEnabled()) {
            value = fastBreakModule.getBreakCooldown();
        }

        ((ClientPlayerInteractionManagerAccessor) instance).setBlockBreakingCooldown(value);
    }

    @Inject(
            method = "isCurrentlyBreaking",
            at = @At("HEAD"),
            cancellable = true)
    private void redirectCurrentlyBreaking(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        final BreakerModule breakerModule = OraculusClient.getInstance().getModuleRepository().getModule(BreakerModule.class);

        if (breakerModule.isEnabled() && breakerModule.isBreaking() && breakerModule.getSlot() != -1) {
            cir.setReturnValue(pos.equals(this.currentBreakingPos));
        }
    }

    @Inject(method = "cancelBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void hookCancelBlockBreaking(final CallbackInfo ci) {
        final CancelBlockBreakingEvent event = new CancelBlockBreakingEvent();
        EventDispatcher.dispatch(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

}

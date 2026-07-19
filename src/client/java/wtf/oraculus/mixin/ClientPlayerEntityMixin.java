package wtf.oraculus.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.stat.StatHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.oraculus.client.feature.module.impl.movement.SprintModule;
import wtf.oraculus.client.feature.module.impl.movement.noslow.NoSlowModule;
import wtf.oraculus.duck.ClientPlayerEntityAccess;
import wtf.oraculus.event.EventDispatcher;
import wtf.oraculus.event.impl.game.player.PlayerCreateEvent;
import wtf.oraculus.event.impl.game.player.PreUpdateEvent;
import wtf.oraculus.event.impl.game.player.interaction.SwingEvent;
import wtf.oraculus.event.impl.game.player.interaction.VisualSwingEvent;
import wtf.oraculus.event.impl.game.player.movement.*;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin extends AbstractClientPlayerEntity implements ClientPlayerEntityAccess {

    public ClientPlayerEntityMixin(ClientWorld world, GameProfile profile) {
        super(world, profile);
    }

    @Shadow
    protected abstract boolean isCamera();

    @Shadow
    public Input input;

    @Shadow
    @Final
    public ClientPlayNetworkHandler networkHandler;

    @Shadow
    public abstract boolean isSubmergedInWater();

    @Shadow
    public abstract boolean isSneaking();

    @Shadow public float renderPitch;
    @Unique
    private PreMovementPacketEvent preMovementPacketEvent;

    @Unique
    private boolean prevHandSwinging;

    @Unique
    private int prevHandSwingTicks;

    @Inject(
            method = "<init>",
            at = @At("TAIL")
    )
    private void init(MinecraftClient client, ClientWorld world, ClientPlayNetworkHandler networkHandler, StatHandler stats, ClientRecipeBook recipeBook, PlayerInput lastPlayerInput, boolean lastSprinting, CallbackInfo ci) {
        EventDispatcher.dispatch(new PlayerCreateEvent());
    }

    @Inject(
            method = "tickMovementInput",
            at = @At("TAIL")
    )
    private void tickNewAi(CallbackInfo ci) {
        if (this.isCamera()) {
            RotationHelper.getClientHandler().tickCamera();
        }
    }

    @Inject(
            method = "swingHand",
            at = @At("HEAD")
    )
    private void hookSwingHandHead(final Hand hand, final CallbackInfo ci) {
        this.prevHandSwinging = this.handSwinging;
        this.prevHandSwingTicks = this.handSwingTicks;
    }

    @Inject(
            method = "swingHand",
            at = @At("TAIL")
    )
    private void hookSwingHandTail(final Hand hand, final CallbackInfo ci) {
        EventDispatcher.dispatch(new SwingEvent(hand));

        final VisualSwingEvent visualSwingEvent = new VisualSwingEvent(hand);
        EventDispatcher.dispatch(visualSwingEvent);

        if (visualSwingEvent.isCancelled()) {
            this.handSwinging = this.prevHandSwinging;
            this.handSwingTicks = this.prevHandSwingTicks;
        }
    }

    @Inject(
            method = "pushOutOfBlocks",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onPushOutOfBlocks(double x, double z, CallbackInfo ci) {
        PushOutOfBlocksEvent event = new PushOutOfBlocksEvent();
        EventDispatcher.dispatch(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Redirect(method = "canStartSprinting", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"))
    private boolean hookAllowSprint(final ClientPlayerEntity playerEntity) {
        final NoSlowModule noSlowModule = OraculusClient.getInstance().getModuleRepository().getModule(NoSlowModule.class);

        if (noSlowModule.isEnabled() && noSlowModule.isSprintingAllowed()) {
            return false;
        }

        return playerEntity.isUsingItem();
    }

    @Redirect(
            method = "applyMovementSpeedFactors",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/Vec2f;multiply(F)Lnet/minecraft/util/math/Vec2f;", ordinal = 1)
    )
    private Vec2f redirectUseSlowdown(Vec2f instance, float value) {
        final SlowdownEvent slowdownEvent = new SlowdownEvent(value);
        EventDispatcher.dispatch(slowdownEvent);

        return instance.multiply(slowdownEvent.isCancelled() ? 1 : slowdownEvent.getSlowdown());
    }

    @Inject(method = "sendMovementPackets", at = @At("HEAD"), cancellable = true)
    private void hookSendMovementPacketsHead(final CallbackInfo callbackInfo) {
        if (preMovementPacketEvent.isCancelled())
            callbackInfo.cancel();
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/PlayerInput;equals(Ljava/lang/Object;)Z"))
    private boolean redirectInputEquals(PlayerInput instance, Object object) {
        if (preMovementPacketEvent.isForceInput()) {
            return false;
        }
        return instance.equals(object);
    }

    @Redirect(
            method = "sendMovementPackets",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/network/ClientPlayerEntity;horizontalCollision:Z", opcode = Opcodes.GETFIELD)
    )
    private boolean redirectHorizontalCollision(ClientPlayerEntity instance) {
        return preMovementPacketEvent.isHorizontalCollision();
    }

    @Redirect(
            method = "sendSprintingPacket",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isSprinting()Z")
    )
    private boolean redirectPreMovementPacketSprinting(ClientPlayerEntity instance) {
        return preMovementPacketEvent != null && preMovementPacketEvent.isSprinting();
    }

    @Inject(
            method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;tick()V", shift = At.Shift.AFTER)
    )
    private void hookPostTick(CallbackInfo ci) {
        preMovementPacketEvent = new PreMovementPacketEvent(getX(), getY(), getZ(), getYaw(), getPitch(), isOnGround(), isSprinting(), this.horizontalCollision);
        EventDispatcher.dispatch(preMovementPacketEvent);
    }

    @Inject(method = "sendMovementPackets", at = @At("TAIL"))
    private void hookSendMovementPacketsTail(final CallbackInfo callbackInfo) {
        if (preMovementPacketEvent == null) return;
        EventDispatcher.dispatch(new PostMovementPacketEvent(
                preMovementPacketEvent.getX(), preMovementPacketEvent.getY(), preMovementPacketEvent.getZ(),
                preMovementPacketEvent.getYaw(), preMovementPacketEvent.getPitch(),
                preMovementPacketEvent.isOnGround(), preMovementPacketEvent.isSprinting()
        ));
    }

    @Redirect(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getYaw()F"))
    private float hookSendMovementPacketsYaw(final ClientPlayerEntity instance) {
        return preMovementPacketEvent == null ? 0 : preMovementPacketEvent.getYaw();
    }

    @Redirect(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getPitch()F"))
    private float hookSendMovementPacketsPitch(final ClientPlayerEntity instance) {
        return preMovementPacketEvent == null ? 0 : preMovementPacketEvent.getPitch();
    }

    @Redirect(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isOnGround()Z"))
    private boolean hookSendMovementPacketsGround(final ClientPlayerEntity instance) {
        return preMovementPacketEvent != null && preMovementPacketEvent.isOnGround();
    }

    @Redirect(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getX()D"))
    private double hookSendMovementPacketsPosX(final ClientPlayerEntity instance) {
        return preMovementPacketEvent == null ? 0 : preMovementPacketEvent.getX();
    }

    @Redirect(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getY()D"))
    private double hookSendMovementPacketsPosY(final ClientPlayerEntity instance) {
        return preMovementPacketEvent == null ? 0 : preMovementPacketEvent.getY();
    }

    @Redirect(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getZ()D"))
    private double hookSendMovementPacketsPosZ(final ClientPlayerEntity instance) {
        return preMovementPacketEvent == null ? 0 : preMovementPacketEvent.getZ();
    }

    @Redirect(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getEntityPos()Lnet/minecraft/util/math/Vec3d;"))
    private Vec3d hookSendMovementPacketsGetPos(ClientPlayerEntity instance) {
        if (preMovementPacketEvent == null) {
            return instance.getEntityPos();
        }
        return new Vec3d(preMovementPacketEvent.getX(), preMovementPacketEvent.getY(), preMovementPacketEvent.getZ());
    }

    @Inject(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Cooldown;tick()V")
    )
    private void hookUpdate(CallbackInfo ci) {
        EventDispatcher.dispatch(new PreUpdateEvent());
    }

    @Redirect(
            method = "canStartSprinting",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;hasForwardMovement()Z")
    )
    private boolean redirectHasForwardMovementStartSprint(Input instance) {
        if (SprintModule.isOmniSprint()) {
            return this.isOmniForwardMovement();
        }
        return instance.hasForwardMovement();
    }

    @Redirect(
            method = "shouldStopSprinting",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;hasForwardMovement()Z")
    )
    private boolean redirectHasForwardMovementStopSprint(Input instance) {
        if (SprintModule.isOmniSprint()) {
            return this.isOmniForwardMovement();
        }
        return instance.hasForwardMovement();
    }

    @ModifyExpressionValue(
            method = "tickMovement",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;hasForwardMovement()Z")
    )
    private boolean redirectTickMovementHasForwardMovement(boolean original) {
        if (!original && SprintModule.isOmniSprint()) {
            return this.isOmniForwardMovement();
        }
        return original;
    }

    @Unique
    private boolean isOmniForwardMovement() {
        return Math.abs(this.input.getMovementInput().y) > 1.0E-5F || Math.abs(this.input.getMovementInput().x) > 1.0E-5F;
    }

    @ModifyReturnValue(
            method = "canStartSprinting",
            at = @At(value = "RETURN")
    )
    private boolean redirectCanStartSprinting(boolean original) {
        final SprintEvent sprintEvent = new SprintEvent(original);
        EventDispatcher.dispatch(sprintEvent);
        return sprintEvent.isCanStartSprinting();
    }

    @Unique
    @Override
    public void oraculus$swingHandClientside(Hand hand) {
        super.swingHand(hand);
    }

    @Unique
    @Override
    public void oraculus$swingHandServerside(Hand hand) {
        this.networkHandler.sendPacket(new HandSwingC2SPacket(hand));
        EventDispatcher.dispatch(new SwingEvent(hand));
    }

}

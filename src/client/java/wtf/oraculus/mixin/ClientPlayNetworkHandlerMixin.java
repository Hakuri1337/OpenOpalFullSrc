package wtf.oraculus.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.client.network.ClientConnectionState;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.encryption.NetworkEncryptionUtils;
import net.minecraft.network.message.LastSeenMessagesCollector;
import net.minecraft.network.message.MessageBody;
import net.minecraft.network.message.MessageChain;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.command.repository.CommandRepository;
import wtf.oraculus.client.feature.module.impl.visual.AnimationsModule;
import wtf.oraculus.client.screen.click.dropdown.DropdownClickGUI;
import wtf.oraculus.event.EventDispatcher;
import wtf.oraculus.event.impl.game.player.movement.knockback.VelocityUpdateEvent;
import wtf.oraculus.event.impl.game.player.teleport.PostTeleportEvent;
import wtf.oraculus.event.impl.game.player.teleport.PreTeleportEvent;
import wtf.oraculus.utility.misc.chat.ChatUtility;

import java.time.Instant;

import static wtf.oraculus.client.Constants.mc;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class
ClientPlayNetworkHandlerMixin extends ClientCommonNetworkHandler {

    @Shadow
    private LastSeenMessagesCollector lastSeenMessagesCollector;
    @Shadow
    private MessageChain.Packer messagePacker;

    protected ClientPlayNetworkHandlerMixin(MinecraftClient client, ClientConnection connection, ClientConnectionState connectionState) {
        super(client, connection, connectionState);
    }

    @WrapWithCondition(method = "onCloseScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;closeScreen()V"))
    private boolean preventScreenClose(ClientPlayerEntity instance) {
        return !(mc.currentScreen instanceof ChatScreen || mc.currentScreen instanceof DropdownClickGUI);
    }

    @Unique
    private PreTeleportEvent preTeleportEvent;

    @Inject(
            method = "onPlayerPositionLook",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V", shift = At.Shift.AFTER),
            cancellable = true
    )
    private void hookOnPlayerPositionLook(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
        this.preTeleportEvent = new PreTeleportEvent(packet.teleportId(), packet.change(), packet.relatives());
        EventDispatcher.dispatch(this.preTeleportEvent);
        if (this.preTeleportEvent.isCancelled()) {
            ci.cancel();
            this.preTeleportEvent = null;
        }
    }

    @Inject(
            method = "onPlayerPositionLook",
            at = @At("TAIL")
    )
    private void hookOnPlayerPositionLookTail(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
        if (this.preTeleportEvent == null) return;
        EventDispatcher.dispatch(new PostTeleportEvent(this.preTeleportEvent.getTeleportId(), this.preTeleportEvent.getChange(), this.preTeleportEvent.getRelatives()));
        this.preTeleportEvent = null;
    }

    @Redirect(
            method = "onPlayerPositionLook",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/packet/s2c/play/PlayerPositionLookS2CPacket;teleportId()I")
    )
    private int redirectTeleportId(PlayerPositionLookS2CPacket instance) {
        return this.preTeleportEvent == null ? 0 : this.preTeleportEvent.getTeleportId();
    }

    @Inject(
            method = "sendChatMessage",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onSendChatMessage(String message, CallbackInfo ci) {
        String trimmedMessage = message.trim();

        if (trimmedMessage.startsWith(".")) {
            try {
                CommandRepository.dispatch(trimmedMessage.substring(1));
            } catch (CommandSyntaxException e) {
                ChatUtility.error(e.getMessage());
            }

            client.inGameHud.getChatHud().addToMessageHistory(message);
            ci.cancel();
        } else {
            Instant instant = Instant.now();
            long l = NetworkEncryptionUtils.SecureRandomUtil.nextLong();
            LastSeenMessagesCollector.LastSeenMessages lastSeenMessages = this.lastSeenMessagesCollector.collect();
            MessageSignatureData messageSignatureData = this.messagePacker.pack(new MessageBody(message, instant, l, lastSeenMessages.lastSeen()));
            this.sendPacket(new ChatMessageC2SPacket(message, instant, l, messageSignatureData, lastSeenMessages.update()));

            ci.cancel();
        }
    }

    @Inject(
            method = "onEntityTrackerUpdate",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/data/DataTracker;writeUpdatedEntries(Ljava/util/List;)V", shift = At.Shift.BEFORE)
    )
    private void onEntityTrackerUpdate(EntityTrackerUpdateS2CPacket packet, CallbackInfo ci) {
        if (mc.player != null && packet.id() == mc.player.getId()) {
            final AnimationsModule animationsModule = OraculusClient.getInstance().getModuleRepository().getModule(AnimationsModule.class);
            if (animationsModule.isEnabled() && animationsModule.isFixPoseRepeat()) {
                packet.trackedValues().removeIf(entry -> isBadId(entry.id()));
            }
        }
    }

    @Unique
    private static boolean isBadId(int id) {
        return id == EntityAccessor.getTrackedPose().id() || id == LivingEntityAccessor.getTrackedLivingFlags().id();
    }

    @Inject(
            method = "onEntityVelocityUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;setVelocityClient(Lnet/minecraft/util/math/Vec3d;)V"), cancellable = true
    )
    private void hookVelocityUpdate(EntityVelocityUpdateS2CPacket packet, CallbackInfo ci) {
        if (packet.getEntityId() != mc.player.getId()) return;

        final VelocityUpdateEvent velocityUpdateEvent = new VelocityUpdateEvent(packet.getVelocity().x, packet.getVelocity().y, packet.getVelocity().z, false);
        EventDispatcher.dispatch(velocityUpdateEvent);

        if (!velocityUpdateEvent.isCancelled())
            mc.player.setVelocityClient(new Vec3d(velocityUpdateEvent.getVelocityX(), velocityUpdateEvent.getVelocityY(), velocityUpdateEvent.getVelocityZ()));

        ci.cancel();
    }

    @Inject(
            method = "onExplosion", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/packet/s2c/play/ExplosionS2CPacket;playerKnockback()Ljava/util/Optional;"),
            cancellable = true)
    private void hookVelocityUpdateExplosion(ExplosionS2CPacket packet, CallbackInfo ci) {
        packet.playerKnockback().ifPresent(knockback -> {
            final Vec3d velocityAdded = mc.player.getVelocity().add(knockback);

            final VelocityUpdateEvent velocityUpdateEvent = new VelocityUpdateEvent(velocityAdded.getX(), velocityAdded.getY(), velocityAdded.getZ(), true);
            EventDispatcher.dispatch(velocityUpdateEvent);

            if (!velocityUpdateEvent.isCancelled())
                mc.player.addVelocityInternal(new Vec3d(velocityUpdateEvent.getVelocityX(), velocityUpdateEvent.getVelocityY(), velocityUpdateEvent.getVelocityZ()));

            ci.cancel();
        });
    }

}

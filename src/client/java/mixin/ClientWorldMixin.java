package mixin;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.oraculus.event.EventDispatcher;
import wtf.oraculus.event.impl.game.world.EntityRemoveEvent;
import wtf.oraculus.event.impl.game.world.PlaySoundEvent;
import wtf.oraculus.utility.player.SkipTickUtility;

import static wtf.oraculus.client.Constants.mc;

@Mixin(ClientWorld.class)
public final class ClientWorldMixin {

    private ClientWorldMixin() {
    }

    @Shadow
    public Entity getEntityById(int id) {
        return null;
    }

    @Inject(method = "playSound(DDDLnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FFZJ)V", at = @At("HEAD"), cancellable = true)
    private void playSound(double x, double y, double z, SoundEvent event, SoundCategory category, float volume, float pitch, boolean useDistance, long seed, CallbackInfo ci) {
        final PlaySoundEvent playSoundEvent = new PlaySoundEvent(event, x, y, z);
        EventDispatcher.dispatch(playSoundEvent);
        if (playSoundEvent.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "removeEntity", at = @At("HEAD"))
    private void hookRemoveEntity(int entityId, Entity.RemovalReason removalReason, CallbackInfo ci) {
        final Entity entity = this.getEntityById(entityId);
        if (entity != null) {
            EventDispatcher.dispatch(new EntityRemoveEvent(entity, removalReason == Entity.RemovalReason.KILLED));
        }
    }

    @Redirect(
            method = "tickEntity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;tick()V")
    )
    private void hookSkipTicks(final Entity instance) {
        if (mc.player != null && instance == mc.player && SkipTickUtility.consumeSkipTick()) {
            return;
        }
        instance.tick();
    }

}

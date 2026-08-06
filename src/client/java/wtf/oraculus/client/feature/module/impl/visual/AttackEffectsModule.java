package wtf.oraculus.client.feature.module.impl.visual;

import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.bool.MultipleBooleanProperty;
import wtf.oraculus.event.impl.game.player.interaction.AttackEvent;
import wtf.oraculus.event.impl.game.world.PlaySoundEvent;
import wtf.oraculus.event.subscriber.Subscribe;

import java.util.Map;
import java.util.function.Supplier;

import static wtf.oraculus.client.Constants.mc;

public final class AttackEffectsModule extends Module {

    private final MultipleBooleanProperty particles = new MultipleBooleanProperty("Particles",
            new BooleanProperty("Critical", false),
            new BooleanProperty("Sharpness", true));

    private final MultipleBooleanProperty sounds = new MultipleBooleanProperty("Sounds",
            new BooleanProperty("Critical", false),
            new BooleanProperty("Knockback", false),
            new BooleanProperty("Strong", false),
            new BooleanProperty("Sweep", false),
            new BooleanProperty("Weak", false),
            new BooleanProperty("No damage", false));

    private final Map<SoundEvent, Supplier<Boolean>> soundValues = Map.of(
            SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, sounds.getProperty("Critical")::getValue,
            SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, sounds.getProperty("Knockback")::getValue,
            SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, sounds.getProperty("Strong")::getValue,
            SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, sounds.getProperty("Sweep")::getValue,
            SoundEvents.ENTITY_PLAYER_ATTACK_WEAK, sounds.getProperty("Weak")::getValue,
            SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE, sounds.getProperty("No damage")::getValue
    );

    public AttackEffectsModule() {
        super("Attack Effects", "Adds or changes effects that happen when attacking an entity.", ModuleCategory.VISUAL);
        setEnabled(true);
        addProperties(particles, sounds);
    }

    @Subscribe
    public void onAttack(final AttackEvent event) {
        if (mc.player == null) return;

        if (particles.getProperty("Critical").getValue()) {
            mc.player.addCritParticles(event.getTarget());
        }

        if (particles.getProperty("Sharpness").getValue()) {
            mc.player.addEnchantedHitParticles(event.getTarget());
        }
    }

    @Subscribe
    public void onPlaySound(final PlaySoundEvent event) {
        final Supplier<Boolean> supplier = this.soundValues.get(event.getSoundEvent());
        if (supplier != null && !supplier.get()) {
            event.setCancelled();
        }
    }

}

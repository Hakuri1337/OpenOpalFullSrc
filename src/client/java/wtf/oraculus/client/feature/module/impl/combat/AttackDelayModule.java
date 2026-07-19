package wtf.oraculus.client.feature.module.impl.combat;

import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.event.impl.game.player.interaction.AttackDelayEvent;
import wtf.oraculus.event.subscriber.Subscribe;

public final class AttackDelayModule extends Module {
    private final NumberProperty maxCooldown = new NumberProperty("Max cooldown", 0, 0, 9, 1);

    public AttackDelayModule() {
        super("Attack Delay", "Removes the delay after missing an attack.", ModuleCategory.COMBAT);
        this.addProperties(this.maxCooldown);
    }

    @Subscribe
    public void onAttackCooldown(AttackDelayEvent event) {
        event.setDelay(this.maxCooldown.getValue().intValue());
    }
}

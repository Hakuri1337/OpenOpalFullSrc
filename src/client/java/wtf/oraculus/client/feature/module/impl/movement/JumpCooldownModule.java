package wtf.oraculus.client.feature.module.impl.movement;

import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.event.impl.game.player.movement.JumpingCooldownEvent;
import wtf.oraculus.event.subscriber.Subscribe;

public final class JumpCooldownModule extends Module {

    private final NumberProperty maxCooldown = new NumberProperty("Max cooldown", 0, 0, 9, 1);

    public JumpCooldownModule() {
        super("Jump Cooldown", "Modifies the vanilla jump cooldown.", ModuleCategory.MOVEMENT);
        this.addProperties(this.maxCooldown);
    }

    @Subscribe
    public void onJumpingCooldown(JumpingCooldownEvent event) {
        event.setCooldown(this.maxCooldown.getValue().intValue());
    }

}

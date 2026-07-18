package wtf.opal.client.feature.module.impl.combat;

import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.impl.game.player.combat.AttackSlowdownEvent;
import wtf.opal.event.subscriber.Subscribe;

import static wtf.opal.client.Constants.mc;

/**
 * Mirrors Amadeus's MoreKB timing: every other client tick lets the attack
 * use vanilla sprint-reset knockback instead of retaining sprint.
 */
public final class SuperKnockBackModule extends Module {

    private int tickCounter;

    public SuperKnockBackModule() {
        super("SuperKnockBack", "Enhances sprint knockback timing.", ModuleCategory.COMBAT);
        setEnabled(true);
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        this.tickCounter++;
    }

    @Subscribe
    public void onAttackSlowdown(final AttackSlowdownEvent event) {
        if (event.isCancelled() || mc.player == null) {
            return;
        }

        if ((this.tickCounter & 1) == 1 && mc.player.isSprinting()) {
            mc.player.setSprinting(false);
        }
    }
}

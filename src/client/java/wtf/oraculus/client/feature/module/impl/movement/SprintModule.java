package wtf.oraculus.client.feature.module.impl.movement;

import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.impl.combat.killaura.KillAuraModule;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.player.movement.KeepSprintEvent;
import wtf.oraculus.event.subscriber.Subscribe;

import static wtf.oraculus.client.Constants.mc;

public final class SprintModule extends Module {

    private final BooleanProperty omniSprint = new BooleanProperty("Omnidirectional", false);
    private final BooleanProperty keepSprint = new BooleanProperty("Keep sprint", true);

    public SprintModule() {
        super("Sprint", "Modifies the logic behind sprinting.", ModuleCategory.MOVEMENT);
        addProperties(omniSprint, keepSprint);
        setEnabled(true);
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        mc.options.sprintKey.setPressed(true);
    }

    @Subscribe
    public void onKeepSprint(final KeepSprintEvent event) {
        if (!keepSprint.getValue()) return;

        final KillAuraModule killAuraModule = OraculusClient.getInstance().getModuleRepository().getModule(KillAuraModule.class);
        if (killAuraModule.isEnabled() && killAuraModule.isTargeting()) {
            return;
        }

        event.setCancelled();
    }

    public static boolean isOmniSprint() {
        final SprintModule sprintModule = OraculusClient.getInstance().getModuleRepository().getModule(SprintModule.class);
        return sprintModule.isEnabled() && sprintModule.omniSprint.getValue();
    }
}

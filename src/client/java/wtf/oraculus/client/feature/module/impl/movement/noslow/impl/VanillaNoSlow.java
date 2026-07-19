package wtf.oraculus.client.feature.module.impl.movement.noslow.impl;

import wtf.oraculus.client.feature.module.impl.movement.noslow.NoSlowModule;
import wtf.oraculus.client.feature.module.property.impl.mode.ModuleMode;
import wtf.oraculus.event.impl.game.player.movement.SlowdownEvent;
import wtf.oraculus.event.subscriber.Subscribe;

public final class VanillaNoSlow extends ModuleMode<NoSlowModule> {

    public VanillaNoSlow(final NoSlowModule module) {
        super(module);
    }

    @Subscribe
    public void onSlowdown(final SlowdownEvent event) {
        event.setCancelled();
    }

    @Override
    public Enum<?> getEnumValue() {
        return NoSlowModule.Mode.VANILLA;
    }

}

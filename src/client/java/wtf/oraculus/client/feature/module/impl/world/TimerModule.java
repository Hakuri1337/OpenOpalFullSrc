package wtf.oraculus.client.feature.module.impl.world;

import wtf.oraculus.client.feature.helper.impl.player.timer.TimerHelper;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.subscriber.Subscribe;

public final class
TimerModule extends Module {

    private final NumberProperty gameSpeed = new NumberProperty("Game speed", "x", 2F, 0.05F, 10F, 0.05F);

    public TimerModule() {
        super("Timer", "Modifies your game speed.", ModuleCategory.WORLD);

        addProperties(gameSpeed);
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        TimerHelper.getInstance().timer = gameSpeed.getValue().floatValue();
    }

    @Override
    protected void onDisable() {
        TimerHelper.getInstance().timer = 1F;
        super.onDisable();
    }
}

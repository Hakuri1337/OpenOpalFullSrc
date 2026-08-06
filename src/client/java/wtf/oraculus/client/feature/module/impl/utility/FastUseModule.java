package wtf.oraculus.client.feature.module.impl.utility;

import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.property.impl.GroupProperty;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import mixin.MinecraftClientAccessor;

import static wtf.oraculus.client.Constants.mc;

public final class FastUseModule extends Module {

    private final BooleanProperty fastPlaceEnabled = new BooleanProperty("Enabled", true);

    public FastUseModule() {
        super("Fast Use", "Uses things faster.", ModuleCategory.UTILITY);
        this.addProperties(new GroupProperty("Placements", fastPlaceEnabled));
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (!fastPlaceEnabled.getValue()) return;

        final MinecraftClientAccessor minecraftClientAccessor = (MinecraftClientAccessor) mc;

        minecraftClientAccessor.setItemUseCooldown(0);
    }

}

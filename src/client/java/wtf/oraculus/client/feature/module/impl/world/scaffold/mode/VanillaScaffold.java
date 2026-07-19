package wtf.oraculus.client.feature.module.impl.world.scaffold.mode;

import net.minecraft.entity.effect.StatusEffects;
import wtf.oraculus.client.feature.module.impl.world.scaffold.ScaffoldModule;
import wtf.oraculus.client.feature.module.impl.world.scaffold.ScaffoldSettings;
import wtf.oraculus.client.feature.module.property.impl.mode.ModuleMode;
import wtf.oraculus.event.impl.game.player.interaction.block.BlockPlacedEvent;
import wtf.oraculus.event.subscriber.Subscribe;

import static wtf.oraculus.client.Constants.mc;

public final class VanillaScaffold extends ModuleMode<ScaffoldModule> {

    public VanillaScaffold(final ScaffoldModule module) {
        super(module);
    }

    @Subscribe
    public void onBlockPlaced(final BlockPlacedEvent event) {
        if (mc.player.hasStatusEffect(StatusEffects.JUMP_BOOST) || !module.getSettings().isTowerEnabled() || mc.options.useKey.isPressed()) {
            return;
        }

        if (mc.options.jumpKey.isPressed()) {
            mc.player.jump();
        }
    }

    @Override
    public Enum<?> getEnumValue() {
        return ScaffoldSettings.Mode.VANILLA;
    }
}

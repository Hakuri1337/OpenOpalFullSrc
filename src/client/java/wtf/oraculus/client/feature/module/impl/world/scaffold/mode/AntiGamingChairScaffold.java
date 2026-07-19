package wtf.oraculus.client.feature.module.impl.world.scaffold.mode;

import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.Direction;
import wtf.oraculus.client.feature.module.impl.world.scaffold.ScaffoldModule;
import wtf.oraculus.client.feature.module.impl.world.scaffold.ScaffoldSettings;
import wtf.oraculus.client.feature.module.property.impl.mode.ModuleMode;
import wtf.oraculus.event.impl.game.player.interaction.block.BlockPlacedEvent;
import wtf.oraculus.event.subscriber.Subscribe;

import static wtf.oraculus.client.Constants.mc;

public final class AntiGamingChairScaffold extends ModuleMode<ScaffoldModule> {

    public AntiGamingChairScaffold(ScaffoldModule module) {
        super(module);
    }

    @Subscribe
    public void onBlockPlaced(final BlockPlacedEvent event) {
        if (mc.player.hasStatusEffect(StatusEffects.JUMP_BOOST) || !module.getSettings().isTowerEnabled() || mc.options.useKey.isPressed()) {
            return;
        }

        if (mc.options.jumpKey.isPressed()) {
            mc.player.setVelocity(mc.player.getVelocity().withAxis(Direction.Axis.Y, 0.42F));
        }
    }

    @Override
    public Enum<?> getEnumValue() {
        return ScaffoldSettings.Mode.ANTI_GAMING_CHAIR;
    }
}

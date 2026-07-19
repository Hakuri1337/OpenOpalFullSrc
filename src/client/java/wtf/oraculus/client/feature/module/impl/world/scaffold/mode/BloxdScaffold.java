package wtf.oraculus.client.feature.module.impl.world.scaffold.mode;

import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import wtf.oraculus.client.feature.module.impl.world.scaffold.ScaffoldModule;
import wtf.oraculus.client.feature.module.impl.world.scaffold.ScaffoldSettings;
import wtf.oraculus.client.feature.module.property.impl.mode.ModuleMode;
import wtf.oraculus.event.impl.game.player.interaction.block.BlockPlacedEvent;
import wtf.oraculus.event.impl.game.player.movement.PostMoveEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.player.PlayerUtility;

import static wtf.oraculus.client.Constants.mc;

public final class BloxdScaffold extends ModuleMode<ScaffoldModule> {

    public BloxdScaffold(final ScaffoldModule module) {
        super(module);
    }

    @Subscribe
    public void onBlockPlaced(final BlockPlacedEvent event) {
        if (mc.player.hasStatusEffect(StatusEffects.JUMP_BOOST) || !module.getSettings().isTowerEnabled() || mc.options.useKey.isPressed()) {
            return;
        }

        if (mc.options.jumpKey.isPressed()) {
            mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(
                    Hand.MAIN_HAND,
                    event.getBlockHitResult().withBlockPos(event.getBlockHitResult().getBlockPos().up()),
                    0
            ));
        }
    }

    @Subscribe
    public void onPostMove(final PostMoveEvent event) {
        if (!PlayerUtility.isBoxEmpty(mc.player.getBoundingBox().expand(-0.005, 0, -0.005))) {
            mc.player.setPosition(mc.player.getEntityPos().add(0, 2, 0));
        }
    }

    @Override
    public Enum<?> getEnumValue() {
        return ScaffoldSettings.Mode.BLOXD;
    }
}

package wtf.oraculus.client.feature.module.impl.movement.noslow.impl;

import wtf.oraculus.client.feature.helper.impl.LocalDataWatch;
import wtf.oraculus.client.feature.module.impl.movement.noslow.NoSlowModule;
import wtf.oraculus.client.feature.module.property.impl.mode.ModuleMode;
import wtf.oraculus.event.impl.game.input.MoveInputEvent;
import wtf.oraculus.event.impl.game.player.movement.SlowdownEvent;
import wtf.oraculus.event.subscriber.Subscribe;

import static wtf.oraculus.client.Constants.mc;

public final class GrimJumpNoSlow extends ModuleMode<NoSlowModule> {

    public GrimJumpNoSlow(final NoSlowModule module) {
        super(module);
    }

    @Subscribe
    public void onSlowdown(final SlowdownEvent event) {
        if (mc.player == null || !mc.player.isUsingItem()) {
            return;
        }

        if (LocalDataWatch.get().groundTicks == 1) {
            event.setCancelled();
            if (this.module.isSprintingAllowed() && !mc.player.isSprinting()) {
                mc.player.setSprinting(true);
            }
        }
    }

    @Subscribe
    public void onMoveInput(final MoveInputEvent event) {
        if (mc.player == null || !mc.player.isOnGround() || !mc.player.isUsingItem()) {
            return;
        }

        if (event.getForward() != 0.0F || event.getSideways() != 0.0F) {
            event.setJump(true);
        }
    }

    @Override
    public Enum<?> getEnumValue() {
        return NoSlowModule.Mode.GRIM_JUMP;
    }
}

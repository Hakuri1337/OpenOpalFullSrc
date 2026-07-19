package wtf.oraculus.client.feature.module.impl.world.scaffold.mode.watchdog;

import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.Direction;
import wtf.oraculus.client.feature.helper.impl.LocalDataWatch;
import wtf.oraculus.client.feature.helper.impl.player.timer.TimerHelper;
import wtf.oraculus.client.feature.module.impl.world.scaffold.ScaffoldModule;
import wtf.oraculus.client.feature.module.impl.world.scaffold.ScaffoldSettings;
import wtf.oraculus.client.feature.module.property.impl.mode.ModuleMode;
import wtf.oraculus.event.impl.game.input.MoveInputEvent;
import wtf.oraculus.event.impl.game.player.movement.JumpEvent;
import wtf.oraculus.event.impl.game.player.movement.PostMoveEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.misc.chat.ChatUtility;
import wtf.oraculus.utility.player.MoveUtility;

import static wtf.oraculus.client.Constants.mc;

public final class WatchdogScaffold extends ModuleMode<ScaffoldModule> {

    public WatchdogScaffold(ScaffoldModule module) {
        super(module);
    }

    @Subscribe
    public void onMoveInput(final MoveInputEvent event) {
        if (mc.player.isOnGround() && event.isJump() && !mc.player.isSneaking()) {
//            event.setSneak(true);
        }
    }

    private final WatchdogTellyBlink tellyBlink = new WatchdogTellyBlink(this);

    @Subscribe
    public void onJump(final JumpEvent event) {
//        if (mc.player.isOnGround() && mc.player.input.playerInput.jump() && MoveUtility.isMoving()) {
//            if (!event.isSprinting() || !mc.player.input.playerInput.sneak()) {
//                event.setCancelled();
//            }
//        }
    }

    @Subscribe
    public void onPostMove(final PostMoveEvent event) {
//        if (mc.player.hasStatusEffect(StatusEffects.JUMP_BOOST) || !module.getSettings().isTowerEnabled() || mc.options.useKey.isPressed() || !mc.options.jumpKey.isPressed()) {
//            return;
//        }

    }

    @Override
    public void onEnable() {
//        this.tellyBlink.enable();
        super.onEnable();
    }

    @Override
    public Enum<?> getEnumValue() {
        return ScaffoldSettings.Mode.WATCHDOG;
    }
}
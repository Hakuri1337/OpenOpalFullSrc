package wtf.oraculus.client.feature.module.impl.world.scaffold.mode.watchdog;

import net.minecraft.entity.EntityPose;
import wtf.oraculus.client.feature.helper.impl.player.packet.blockage.block.holder.BlockHolder;
import wtf.oraculus.client.feature.helper.impl.player.packet.blockage.impl.OutboundNetworkBlockage;
import wtf.oraculus.event.EventDispatcher;
import wtf.oraculus.event.impl.game.player.movement.PostMovementPacketEvent;
import wtf.oraculus.event.impl.game.player.movement.PreMovementPacketEvent;
import wtf.oraculus.event.subscriber.IEventSubscriber;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.mixin.ClientPlayerEntityAccessor;

import static wtf.oraculus.client.Constants.mc;

public final class
WatchdogTellyBlink implements IEventSubscriber {
    private final WatchdogScaffold watchdogScaffold;

    WatchdogTellyBlink(final WatchdogScaffold watchdogScaffold) {
        this.watchdogScaffold = watchdogScaffold;
        EventDispatcher.subscribe(this);
    }

    private boolean enabled;

    private final BlockHolder blockHolder = new BlockHolder(OutboundNetworkBlockage.get());

    private boolean sneaking;

    @Subscribe(priority = -1)
    public void onPreMovementPacket(final PreMovementPacketEvent event) {
//        if (mc.player.input.playerInput.sneak() && !mc.options.sneakKey.isPressed()) {
//            this.blockHolder.block();
//        } else if (mc.options.sneakKey.isPressed() || !mc.player.input.playerInput.sneak()) {
//            this.blockHolder.release();
//        }
//
//        this.sneaking = mc.player.input.playerInput.sneak();
    }

    @Subscribe
    public void onPostMovementPacket(final PostMovementPacketEvent event) {
//        if (!mc.options.sneakKey.isPressed()) { // making sneak bypass silent
//            if (mc.player.getPose() == EntityPose.CROUCHING) {
//                mc.player.setPose(EntityPose.STANDING);
//            }
//            final ClientPlayerEntityAccessor accessor = (ClientPlayerEntityAccessor) mc.player;
//            accessor.setInSneakingPose(false);
//        }
//
//        if (!this.watchdogScaffold.isHandlingEvents() && !this.blockHolder.isBlocking()) {
//            this.enabled = false;
//        }
    }

    public void enable() {
        this.enabled = true;
    }

    @Override
    public boolean isHandlingEvents() {
        return enabled;
    }
}

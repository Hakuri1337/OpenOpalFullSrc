package wtf.oraculus.client.feature.module.impl.utility.nofall.impl;

import net.hypixel.data.type.GameType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import wtf.oraculus.client.feature.helper.impl.LocalDataWatch;
import wtf.oraculus.client.feature.helper.impl.player.packet.blockage.block.holder.BlockHolder;
import wtf.oraculus.client.feature.helper.impl.player.packet.blockage.impl.InboundNetworkBlockage;
import wtf.oraculus.client.feature.helper.impl.player.packet.blockage.impl.OutboundNetworkBlockage;
import wtf.oraculus.client.feature.helper.impl.server.impl.HypixelServer;
import wtf.oraculus.client.feature.module.impl.utility.nofall.NoFallModule;
import wtf.oraculus.client.feature.module.property.impl.mode.ModuleMode;
import wtf.oraculus.client.feature.simulation.PlayerSimulation;
import wtf.oraculus.event.impl.game.player.movement.PostMoveEvent;
import wtf.oraculus.event.impl.game.player.movement.PostMovementPacketEvent;
import wtf.oraculus.event.impl.game.player.movement.PreMoveEvent;
import wtf.oraculus.event.impl.game.player.movement.PreMovementPacketEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.mixin.ClientPlayerEntityAccessor;
import wtf.oraculus.utility.player.PlayerUtility;

import static wtf.oraculus.client.Constants.mc;

public final class WatchdogNoFall extends ModuleMode<NoFallModule> {

    public WatchdogNoFall(NoFallModule module) {
        super(module);
    }

    private final BlockHolder inboundHolder = new BlockHolder(InboundNetworkBlockage.get()), outboundHolder = new BlockHolder(OutboundNetworkBlockage.get());

    private void block() {
        this.inboundHolder.block();
        this.outboundHolder.block();
        this.blocked = true;
    }

    private void release() {
        this.inboundHolder.release();
        this.outboundHolder.release();
    }

    private Vec3d prevMotion;
    private Vec3d nextPos;

    @Subscribe
    public void onPreMove(final PreMoveEvent event) {
        if (this.nextPos != null) {
            mc.player.setPos(this.nextPos.getX(), this.nextPos.getY(), this.nextPos.getZ());
            this.nextPos = null;
            return;
        }

        if (LocalDataWatch.get().getKnownServerManager().getCurrentServer() instanceof HypixelServer) {
            final HypixelServer.ModAPI.Location currentLocation = HypixelServer.ModAPI.get().getCurrentLocation();
            if (currentLocation != null && (currentLocation.isLobby() || currentLocation.serverType() == GameType.PIT || currentLocation.serverType() == GameType.WOOL_GAMES || currentLocation.serverType() == GameType.MURDER_MYSTERY)) {
                return;
            }
        }

        if (this.isGoingToFall()) {
            return;
        }

        final double fallDistance = module.getFallDifference() - (mc.player.getVelocity().getY() - 0.08D) * 0.98F;
        if (fallDistance >= PlayerUtility.getMaxFallDistance()) {
            this.prevMotion = mc.player.getVelocity();
        }
    }

    private boolean isGoingToFall() {
        if (mc.player.isOnGround()) {
            return true;
        }
        if (PlayerUtility.isOverVoid()) {
            PlayerSimulation simulation = new PlayerSimulation(mc.player);
            for (int i = 0; i < 14; i++) {
                simulation.simulateTick();
                if (!PlayerUtility.isOverVoid(simulation.getSimulatedEntity().getBoundingBox())) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    @Subscribe
    public void onPostMove(PostMoveEvent event) {
        if (this.prevMotion != null) {
            Vec3d velocity = mc.player.getVelocity().multiply(0.5D);
            if (PlayerUtility.isBoxEmpty(mc.player.getBoundingBox().offset(velocity.getX(), velocity.getY(), velocity.getZ()))) {
                this.nextPos = mc.player.getEntityPos().add(velocity);
            }
            mc.player.setVelocity(0.0D, 0.0D, 0.0D);
        }
    }

    private boolean blocked;

    @Subscribe(priority = -5)
    public void onPreMovementPacket(PreMovementPacketEvent event) {
        if (this.prevMotion != null) {
            ClientPlayerEntityAccessor accessor = (ClientPlayerEntityAccessor) mc.player;
            double diffX = event.getX() - accessor.getLastXClient();
            double diffY = event.getY() - accessor.getLastYClient();
            double diffZ = event.getZ() - accessor.getLastZClient();
            boolean moved = MathHelper.squaredMagnitude(diffX, diffY, diffZ) > MathHelper.square(2.0E-4);
            if (!moved) {
                int ticksSinceLastPositionPacketSent = accessor.getTicksSinceLastPositionPacketSent();
                if (ticksSinceLastPositionPacketSent >= 20) {
                    accessor.setTicksSinceLastPositionPacketSent(18);
                }

                event.setOnGround(true);
                this.module.syncFallDifference();

                this.block();
            }
            mc.player.setVelocity(this.prevMotion);
            this.prevMotion = null;
        }
    }

    @Subscribe
    public void onPostMovementPacket(PostMovementPacketEvent event) {
        if (this.nextPos != null) {
            Vec3d nextPos = this.nextPos;
            this.nextPos = mc.player.getEntityPos();
            mc.player.setPos(nextPos.getX(), nextPos.getY(), nextPos.getZ());
        }

        if (this.blocked) {
            this.blocked = false;
        } else {
            this.release();
        }
    }

    @Override
    public void onDisable() {
        this.release();
        this.blocked = false;
        this.nextPos = null;
        super.onDisable();
    }

    @Override
    public Enum<?> getEnumValue() {
        return NoFallModule.Mode.WATCHDOG;
    }

}

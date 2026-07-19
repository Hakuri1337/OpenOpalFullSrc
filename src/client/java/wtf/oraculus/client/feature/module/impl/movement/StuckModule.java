package wtf.oraculus.client.feature.module.impl.movement;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.duck.ClientConnectionAccess;
import wtf.oraculus.event.impl.game.JoinWorldEvent;
import wtf.oraculus.event.impl.game.input.MoveInputEvent;
import wtf.oraculus.event.impl.game.packet.ReceivePacketEvent;
import wtf.oraculus.event.impl.game.packet.SendPacketEvent;
import wtf.oraculus.event.impl.game.player.movement.PreMovementPacketEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.mixin.PlayerMoveC2SPacketAccessor;

import java.util.concurrent.ConcurrentLinkedQueue;

import static wtf.oraculus.client.Constants.mc;

public final class StuckModule extends Module {

    private final ConcurrentLinkedQueue<Packet<?>> packetQueue = new ConcurrentLinkedQueue<>();
    private Packet<?> interactPacket;
    private int interactStage;
    private double frozenX;
    private double frozenY;
    private double frozenZ;
    private long enableTime;

    public StuckModule() {
        super("Stuck", "Freezes your server position while allowing local view control.", ModuleCategory.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        this.packetQueue.clear();
        this.interactPacket = null;
        this.interactStage = 0;
        this.enableTime = System.currentTimeMillis();
        if (mc.player != null) {
            this.frozenX = mc.player.getX();
            this.frozenY = mc.player.getY();
            this.frozenZ = mc.player.getZ();
        }
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        if (mc.player != null) {
            this.sendPacketSilent(new PlayerMoveC2SPacket.Full(
                    mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    mc.player.getYaw(), mc.player.getPitch(),
                    mc.player.isOnGround(), false
            ));
        }

        while (!this.packetQueue.isEmpty()) {
            this.sendPacketSilent(this.packetQueue.poll());
        }

        super.onDisable();
    }

    @Subscribe
    public void onPreMovementPacket(final PreMovementPacketEvent event) {
        if (System.currentTimeMillis() - this.enableTime > 3500L) {
            this.setEnabled(false);
            return;
        }

        if (mc.player == null) {
            return;
        }

        mc.player.setVelocity(0.0D, 0.0D, 0.0D);

        if (this.interactStage == 1) {
            this.interactStage = 2;
            this.sendPacketSilent(new PlayerMoveC2SPacket.LookAndOnGround(
                    mc.player.getYaw(),
                    mc.player.getPitch(),
                    mc.player.isOnGround(),
                    false
            ));

            while (!this.packetQueue.isEmpty()) {
                this.sendPacketSilent(this.packetQueue.poll());
            }

            if (this.interactPacket != null) {
                this.sendPacketSilent(this.interactPacket);
                this.interactPacket = null;
            }
        }
    }

    @Subscribe
    public void onMoveInput(final MoveInputEvent event) {
        event.setForward(0.0F);
        event.setSideways(0.0F);
        event.setJump(false);
        event.setSneak(false);
    }

    @Subscribe
    public void onSendPacket(final SendPacketEvent event) {
        if (mc.player == null) {
            return;
        }

        final Packet<?> packet = event.getPacket();

        if (packet instanceof PlayerMoveC2SPacket movePacket) {
            if (movePacket instanceof PlayerMoveC2SPacket.LookAndOnGround) {
                return;
            }

            if (movePacket instanceof PlayerMoveC2SPacket.Full || movePacket instanceof PlayerMoveC2SPacket.PositionAndOnGround) {
                final PlayerMoveC2SPacketAccessor accessor = (PlayerMoveC2SPacketAccessor) movePacket;
                accessor.setX(this.frozenX);
                accessor.setY(this.frozenY);
                accessor.setZ(this.frozenZ);
                return;
            }

            event.setCancelled();
            return;
        }

        if (packet instanceof CommonPongC2SPacket) {
            this.packetQueue.add(packet);
            event.setCancelled();
            return;
        }

        if ((packet instanceof PlayerInteractItemC2SPacket || packet instanceof PlayerActionC2SPacket)
                && this.shouldBufferInteraction(packet)) {
            this.interactPacket = packet;
            this.interactStage = 1;
            event.setCancelled();
        }
    }

    @Subscribe
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (event.getPacket() instanceof PlayerPositionLookS2CPacket) {
            event.setCancelled();
        }
    }

    @Subscribe
    public void onJoinWorld(final JoinWorldEvent event) {
        this.setEnabled(false);
    }

    private boolean shouldBufferInteraction(final Packet<?> packet) {
        if (mc.player == null) {
            return false;
        }

        if (packet instanceof PlayerInteractItemC2SPacket useItem) {
            final ItemStack item = mc.player.getStackInHand(useItem.getHand());
            if (item.isOf(Items.ENDER_PEARL)) {
                return false;
            }
            return !item.getComponents().contains(DataComponentTypes.FOOD) && !(item.getItem() instanceof BowItem);
        }

        if (packet instanceof PlayerActionC2SPacket action) {
            return action.getAction() == PlayerActionC2SPacket.Action.RELEASE_USE_ITEM
                    && mc.player.getActiveItem().getItem() instanceof BowItem;
        }

        return false;
    }

    private void sendPacketSilent(final Packet<?> packet) {
        if (packet != null && mc.getNetworkHandler() != null && mc.getNetworkHandler().getConnection() instanceof ClientConnectionAccess access) {
            access.oraculus$sendPacketSilent(packet);
        }
    }
}

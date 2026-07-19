package wtf.oraculus.client.feature.module.impl.utility.disabler.impl;

import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import wtf.oraculus.client.feature.helper.impl.LocalDataWatch;
import wtf.oraculus.client.feature.helper.impl.player.packet.blockage.block.holder.BlockHolder;
import wtf.oraculus.client.feature.helper.impl.player.packet.blockage.impl.OutboundNetworkBlockage;
import wtf.oraculus.client.feature.helper.impl.server.impl.HypixelServer;
import wtf.oraculus.client.feature.module.impl.utility.disabler.DisablerModule;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.bool.MultipleBooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.mode.ModuleMode;
import wtf.oraculus.event.impl.game.JoinWorldEvent;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.packet.InstantaneousSendPacketEvent;
import wtf.oraculus.event.subscriber.Subscribe;

import static wtf.oraculus.client.Constants.mc;

/**
 * Amadeus Watchdog's inventory-move disabler adapted to Oraculus's packet
 * blockage API. It only engages on a recognised Hypixel connection.
 */
public final class HypixelInventoryDisabler extends ModuleMode<DisablerModule> {

    private final MultipleBooleanProperty options = new MultipleBooleanProperty(
            "Options",
            new BooleanProperty("Inventory Move", true)
    ).hideIf(() -> this.module.getActiveMode() != this);

    private final BlockHolder blockHolder = new BlockHolder(OutboundNetworkBlockage.get());
    private boolean shouldBlink;

    public HypixelInventoryDisabler(final DisablerModule module) {
        super(module);
        module.addProperties(this.options);
    }

    @Override
    public Enum<?> getEnumValue() {
        return DisablerModule.Mode.HYPIXEL_INVENTORY;
    }

    public boolean isInventoryMoveDisabler() {
        final BooleanProperty inventoryMove = this.options.getProperty("Inventory Move");
        return inventoryMove != null
                && inventoryMove.getValue()
                && LocalDataWatch.get().getKnownServerManager().getCurrentServer() instanceof HypixelServer;
    }

    @Subscribe
    public void onInstantaneousSendPacket(final InstantaneousSendPacketEvent event) {
        if (!isInventoryMoveDisabler() || mc.player == null) {
            return;
        }

        final Packet<?> packet = event.getPacket();
        if (packet instanceof ClickSlotC2SPacket clickSlot) {
            final HypixelServer.ModAPI.Location location = HypixelServer.ModAPI.get().getCurrentLocation();
            if (location != null && location.isLobby()) {
                this.shouldBlink = false;
                this.blockHolder.release();
                return;
            }

            final SlotActionType action = clickSlot.actionType();
            final boolean allowedAction = action == SlotActionType.QUICK_MOVE
                    || action == SlotActionType.SWAP
                    || action == SlotActionType.THROW;

            if (clickSlot.syncId() == mc.player.playerScreenHandler.syncId && allowedAction) {
                sendClosePacket(clickSlot.syncId());
            } else {
                this.shouldBlink = true;
            }
            return;
        }

        if (packet instanceof CloseHandledScreenC2SPacket closeScreen
                && closeScreen.getSyncId() == mc.player.playerScreenHandler.syncId) {
            this.shouldBlink = false;
        }
    }

    @Subscribe(priority = 2)
    public void onPreGameTick(final PreGameTickEvent event) {
        if (!isInventoryMoveDisabler()) {
            this.shouldBlink = false;
            this.blockHolder.release();
            return;
        }

        if (mc.currentScreen == null) {
            this.shouldBlink = false;
        }

        if (this.shouldBlink) {
            this.blockHolder.block(packet -> packet, this::shouldBlock);
        } else {
            this.blockHolder.release();
        }
    }

    @Subscribe
    public void onJoinWorld(final JoinWorldEvent event) {
        this.shouldBlink = false;
        this.blockHolder.release();
    }

    @Override
    public void onDisable() {
        this.shouldBlink = false;
        this.blockHolder.release();
        super.onDisable();
    }

    private boolean shouldBlock(final Packet<?> packet) {
        return !(packet instanceof ClickSlotC2SPacket)
                && !(packet instanceof CloseHandledScreenC2SPacket)
                && !(packet instanceof CommonPongC2SPacket)
                && !(packet instanceof KeepAliveC2SPacket);
    }

    private static void sendClosePacket(final int syncId) {
        if (mc.getNetworkHandler() != null) {
            OutboundNetworkBlockage.sendPacketDirect(new CloseHandledScreenC2SPacket(syncId));
        }
    }
}

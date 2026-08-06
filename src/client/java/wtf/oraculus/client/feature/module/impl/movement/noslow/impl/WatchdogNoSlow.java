package wtf.oraculus.client.feature.module.impl.movement.noslow.impl;

import net.minecraft.block.Block;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import wtf.oraculus.client.feature.helper.impl.player.mouse.MouseButton;
import wtf.oraculus.client.feature.helper.impl.player.mouse.MouseHelper;
import wtf.oraculus.client.feature.helper.impl.player.packet.blockage.block.holder.BlockHolder;
import wtf.oraculus.client.feature.helper.impl.player.packet.blockage.impl.InboundNetworkBlockage;
import wtf.oraculus.client.feature.helper.impl.player.packet.blockage.impl.OutboundNetworkBlockage;
import wtf.oraculus.client.feature.helper.impl.player.slot.SlotHelper;
import wtf.oraculus.client.feature.module.impl.movement.noslow.NoSlowModule;
import wtf.oraculus.client.feature.module.property.impl.mode.ModuleMode;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.input.MouseHandleInputEvent;
import wtf.oraculus.event.impl.game.input.PostHandleInputEvent;
import wtf.oraculus.event.impl.game.input.SlotChangeEvent;
import wtf.oraculus.event.impl.game.packet.ReceivePacketEvent;
import wtf.oraculus.event.impl.game.packet.SendPacketEvent;
import wtf.oraculus.event.impl.game.player.movement.SlowdownEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.misc.chat.ChatUtility;
import wtf.oraculus.utility.player.InventoryUtility;
import wtf.oraculus.utility.player.PlayerUtility;

import static wtf.oraculus.client.Constants.mc;

public final class WatchdogNoSlow extends ModuleMode<NoSlowModule> {

    public WatchdogNoSlow(final NoSlowModule module) {
        super(module);
    }

    private final BlockHolder oBlockHolder = new BlockHolder(OutboundNetworkBlockage.get());
    private final BlockHolder iBlockHolder = new BlockHolder(InboundNetworkBlockage.get());
    private boolean stopUse;

    private int nextCycleTick = -1, slotChangeTick;
    private boolean runThisTick = false;

    @Subscribe
    public void onSlowdown(final SlowdownEvent event) {
        if (module.getAction() != NoSlowModule.Action.BOW && (module.getAction() != NoSlowModule.Action.USEABLE || this.oBlockHolder.isBlocking()) && mc.player.age - slotChangeTick != 1) {
            event.setCancelled();
        }
    }

    @Subscribe
    public void onSlotChange(final SlotChangeEvent event) {
        release();
        resetCycle();

        if (mc.player != null) {
            slotChangeTick = mc.player.age;
        }
    }

    @Subscribe
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (event.getPacket() instanceof EntityStatusS2CPacket statusS2CPacket) {
            if (mc.player != null && statusS2CPacket.getEntity(mc.world) == mc.player) {
                release();
            }
        }
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player == null || mc.currentScreen != null || mc.getOverlay() != null) {
            resetCycle();
            this.release();
        }
    }

    private void block() {
        this.oBlockHolder.block();
    }

    private void release() {
        this.oBlockHolder.release();
    }

    private void resetCycle() {
        this.stopUse = false;
        this.runThisTick = false;
        this.nextCycleTick = -1;
    }

    @Subscribe
    public void onPostHandleInput(final PostHandleInputEvent event) {
        if (mc.player == null || module.getAction() != NoSlowModule.Action.BLOCKABLE) {
            return;
        }

        if (this.stopUse && mc.player.isUsingItem()) {
            this.block();
            mc.interactionManager.stopUsingItem(mc.player);

            this.stopUse = false;
        }
    }

    @Subscribe(priority = 1)
    public void onMouseHandleInput(final MouseHandleInputEvent event) {
        if (mc.player == null) {
            return;
        }

        final MouseButton rightButton = MouseHelper.getRightButton();

        runThisTick = false;

        if (rightButton.isPressed() && module.getAction() == NoSlowModule.Action.BLOCKABLE) {
            final int age = mc.player.age;

            if (nextCycleTick < 0) {
                nextCycleTick = age;
            }

            if (age >= nextCycleTick) {
                if (this.oBlockHolder.isBlocking()) {
                    release();
                }

                runThisTick = true;
                nextCycleTick = age + 2;
            } else if (!this.oBlockHolder.isBlocking()){
                block();
            }
        } else {
            resetCycle();
            if (!mc.player.isUsingItem()) {
                release();
            } else if (!this.oBlockHolder.isBlocking() && module.getAction() == NoSlowModule.Action.BLOCKABLE){
                block();
            }
        }

        if (module.getAction() == NoSlowModule.Action.BLOCKABLE) {
            if (rightButton.isPressed()) {
                if (runThisTick) {
                    if (!mc.player.isUsingItem() || !this.oBlockHolder.isBlocking()) {
                        final Block blockOver = PlayerUtility.getBlockOver();
                        if (InventoryUtility.isBlockInteractable(blockOver) || mc.interactionManager.isBreakingBlock()) {
                            return;
                        }

                        this.stopUse = true;
                        rightButton.setPressed();
                    } else {
                        rightButton.setDisabled();
                    }
                } else {
                    rightButton.setDisabled();
                    if (!this.oBlockHolder.isBlocking()) {
                        block();
                    }
                }
            } else {
                this.stopUse = false;
            }
        } else {
            this.stopUse = false;
        }
    }

    @Override
    public void onDisable() {
        this.release();
        resetCycle();
        super.onDisable();
    }

    @Override
    public Enum<?> getEnumValue() {
        return NoSlowModule.Mode.WATCHDOG;
    }


}

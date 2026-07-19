package wtf.oraculus.client.feature.module.impl.movement.noslow.impl;

import net.minecraft.block.Block;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import wtf.oraculus.client.feature.helper.impl.player.mouse.MouseButton;
import wtf.oraculus.client.feature.helper.impl.player.mouse.MouseHelper;
import wtf.oraculus.client.feature.helper.impl.player.packet.blockage.block.holder.BlockHolder;
import wtf.oraculus.client.feature.helper.impl.player.packet.blockage.impl.OutboundNetworkBlockage;
import wtf.oraculus.client.feature.module.impl.movement.noslow.NoSlowModule;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.mode.ModuleMode;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.input.MouseHandleInputEvent;
import wtf.oraculus.event.impl.game.input.MoveInputEvent;
import wtf.oraculus.event.impl.game.packet.ReceivePacketEvent;
import wtf.oraculus.event.impl.game.player.movement.PreMovementPacketEvent;
import wtf.oraculus.event.impl.game.player.movement.SlowdownEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.player.InventoryUtility;
import wtf.oraculus.utility.player.PlayerUtility;

import static wtf.oraculus.client.Constants.mc;

public final class UniversalNoSlow extends ModuleMode<NoSlowModule> {

    private final BooleanProperty slowdown = new BooleanProperty("Slow down", false).hideIf(() -> this.module.getActiveMode() != this);

    public UniversalNoSlow(final NoSlowModule module) {
        super(module);
        module.addProperties(slowdown);
    }

    private final BlockHolder oBlockHolder = new BlockHolder(OutboundNetworkBlockage.get());
    private boolean stopUse;

    @Subscribe
    public void onSlowdown(final SlowdownEvent event) {
        if (!slowdown.getValue()) {
            event.setCancelled();
        }
    }

    @Subscribe
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (event.getPacket() instanceof EntityStatusS2CPacket statusS2CPacket) {
            if (mc.player != null && statusS2CPacket.getEntity(mc.world) == mc.player) {
                this.oBlockHolder.release();
            }
        }
    }

    @Subscribe(priority = 2)
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player == null || mc.currentScreen != null || mc.getOverlay() != null) {
            this.release();
            return;
        }

        if (this.stopUse) {
            this.block();
            MouseHelper.getRightButton().setDisabled();

            this.stopUse = false;
        } else if (module.getAction() == NoSlowModule.Action.BLOCKABLE || !mc.player.isUsingItem()) {
            this.release();
        }
    }

    private void block() {
        this.oBlockHolder.block();
    }

    private void release() {
        this.oBlockHolder.release();
    }

    @Subscribe(priority = 1)
    public void onMouseHandleInput(final MouseHandleInputEvent event) {
        if (mc.player == null) {
            return;
        }

        final MouseButton rightButton = MouseHelper.getRightButton();
        if (module.getAction() == NoSlowModule.Action.BLOCKABLE) {
            if (rightButton.isPressed()) {
                if (!mc.player.input.playerInput.jump() || !mc.player.isOnGround() && (mc.player.getVelocity().getY() >= 0.0D || PlayerUtility.isBoxEmpty(mc.player.getBoundingBox().offset(0.0D, mc.player.getVelocity().getY(), 0.0D)))) {
                    if (!mc.player.isUsingItem() || !this.oBlockHolder.isBlocking()) {
                        final Block blockOver = PlayerUtility.getBlockOver();
                        if (InventoryUtility.isBlockInteractable(blockOver) || mc.interactionManager.isBreakingBlock()) {
                            return;
                        }

                        this.stopUse = true;

                        rightButton.setPressed();
                    } else if (mc.player.isUsingItem()) {
                        rightButton.setDisabled();
                    }
                } else {
                    rightButton.setDisabled();
                }
            }
        } else {
            this.stopUse = false;
        }
    }

    @Override
    public void onDisable() {
        this.release();
        this.stopUse = false;
        super.onDisable();
    }

    @Override
    public Enum<?> getEnumValue() {
        return NoSlowModule.Mode.UNIVERSAL;
    }

}

package wtf.oraculus.client.feature.module.impl.movement.longjump.impl;

import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.math.Vec2f;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.helper.impl.player.mouse.MouseHelper;
import wtf.oraculus.client.feature.helper.impl.player.packet.blockage.block.holder.BlockHolder;
import wtf.oraculus.client.feature.helper.impl.player.packet.blockage.impl.OutboundNetworkBlockage;
import wtf.oraculus.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.oraculus.client.feature.helper.impl.player.rotation.model.impl.InstantRotationModel;
import wtf.oraculus.client.feature.helper.impl.player.slot.SlotHelper;
import wtf.oraculus.client.feature.helper.impl.player.timer.TimerHelper;
import wtf.oraculus.client.feature.module.impl.movement.longjump.LongJumpModule;
import wtf.oraculus.client.feature.module.property.impl.mode.ModuleMode;
import wtf.oraculus.client.notification.NotificationType;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.input.MouseHandleInputEvent;
import wtf.oraculus.event.impl.game.input.MoveInputEvent;
import wtf.oraculus.event.impl.game.packet.ReceivePacketEvent;
import wtf.oraculus.event.impl.game.packet.SendPacketEvent;
import wtf.oraculus.event.impl.game.player.movement.PostMoveEvent;
import wtf.oraculus.event.impl.game.player.movement.PreMovementPacketEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.player.InventoryUtility;
import wtf.oraculus.utility.player.MoveUtility;

import static wtf.oraculus.client.Constants.mc;

public final class
AntiGamingChairFireballLongJump extends ModuleMode<LongJumpModule> {

    private boolean thrown, damaged, swapBack, ticked, blinking;
    private int ticksSinceDamaged;

    private final BlockHolder oBlockHolder = new BlockHolder(OutboundNetworkBlockage.get());

    public AntiGamingChairFireballLongJump(final LongJumpModule module) {
        super(module);
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player != null && !thrown) {
            RotationHelper.getHandler().rotate(
                    new Vec2f(mc.player.getYaw(), 90),
                    InstantRotationModel.INSTANCE
            );
        }
    }

    @Subscribe
    public void onMoveInput(final MoveInputEvent event) {
        if (!damaged) {
            event.setForward(0);
            event.setSideways(0);
        }
    }

    @Subscribe
    public void onPostMove(final PostMoveEvent event) {
        if (thrown && damaged) {
            if (ticksSinceDamaged == 0 && MoveUtility.isMoving()) {
                MoveUtility.setSpeed(9.5D);
                TimerHelper.getInstance().timer = 0.3F;
            }

            if (ticksSinceDamaged > 10 && (mc.player.isOnGround() || mc.player.getAbilities().allowFlying || mc.player.getAbilities().flying)) {
                getModule().toggle();
            }
        }
    }

    @Subscribe
    public void onPreMovementPacket(final PreMovementPacketEvent event) {
        // Ensure you are rotated backwards before throwing fireball
        if (event.getPitch() == 90.0F && mc.player.isOnGround()) {
            ticked = true;
        }

        if (thrown && damaged) {
            ticksSinceDamaged++;
        }
    }

    @Subscribe
    public void onHandleInput(final MouseHandleInputEvent event) {
        if (!ticked) {
            return;
        }

        final SlotHelper slotHelper = SlotHelper.getInstance();

        if (!thrown) {
            final int slot = InventoryUtility.findItemInHotbar(Items.FIRE_CHARGE);
            if (slot == -1) {
                OraculusClient.getInstance().getNotificationManager()
                        .builder(NotificationType.ERROR)
                        .duration(1000)
                        .title(module.getName())
                        .description("No fireball in hotbar!")
                        .buildAndPublish();

                module.toggle();
                return;
            }

            slotHelper.setTargetItem(slot).silence(SlotHelper.Silence.DEFAULT);
            MouseHelper.getRightButton().setPressed();
        }

        if (swapBack) {
            slotHelper.stop();
            slotHelper.sync(true, true);

            swapBack = false;
        }
    }

    @Subscribe
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (mc.player != null && event.getPacket() instanceof EntityVelocityUpdateS2CPacket velocity && velocity.getEntityId() == mc.player.getId() && !damaged) {
            damaged = true;
            ticksSinceDamaged = 0;
        }
    }

    @Subscribe
    public void onSendPacket(final SendPacketEvent event) {
        if (!thrown && event.getPacket() instanceof PlayerInteractBlockC2SPacket) {
            thrown = true;
            swapBack = true;
        }
    }

    @Override
    public void onEnable() {
        damaged = thrown = swapBack = ticked = false;
        ticksSinceDamaged = 0;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        if (mc.player != null) {
            MoveUtility.setSpeed(0);
            TimerHelper.getInstance().timer = 1;
        }
        super.onDisable();
    }

    @Override
    public Enum<?> getEnumValue() {
        return LongJumpModule.Mode.ANTI_GAMING_CHAIR_FIREBALL;
    }

}

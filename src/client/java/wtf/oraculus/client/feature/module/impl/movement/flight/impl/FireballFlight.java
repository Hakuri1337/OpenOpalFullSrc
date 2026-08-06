package wtf.oraculus.client.feature.module.impl.movement.flight.impl;

import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.helper.impl.player.mouse.MouseHelper;
import wtf.oraculus.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.oraculus.client.feature.helper.impl.player.rotation.model.impl.InstantRotationModel;
import wtf.oraculus.client.feature.helper.impl.player.slot.SlotHelper;
import wtf.oraculus.client.feature.module.impl.movement.flight.FlightModule;
import wtf.oraculus.client.feature.module.property.impl.mode.ModuleMode;
import wtf.oraculus.client.notification.NotificationType;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.input.MouseHandleInputEvent;
import wtf.oraculus.event.impl.game.packet.ReceivePacketEvent;
import wtf.oraculus.event.impl.game.packet.SendPacketEvent;
import wtf.oraculus.event.impl.game.player.movement.PostMoveEvent;
import wtf.oraculus.event.impl.game.player.movement.PreMovementPacketEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.player.InventoryUtility;
import wtf.oraculus.utility.player.MoveUtility;

import static wtf.oraculus.client.Constants.FIRST_FALL_MOTION;
import static wtf.oraculus.client.Constants.mc;

public final class
FireballFlight extends ModuleMode<FlightModule> {

    private boolean thrown, damaged, swapBack, ticked;
    private int ticksSinceDamaged;

    private double yOffset;

    public FireballFlight(final FlightModule module) {
        super(module);
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player != null && !thrown) {
            RotationHelper.getHandler().rotate(
                    new Vec2f(MoveUtility.getDirectionDegrees() + 180, 45),
                    InstantRotationModel.INSTANCE
            );
        }
    }

    @Subscribe
    public void onPostMove(final PostMoveEvent event) {
        if (!damaged) {
            MoveUtility.setSpeed(0);
        } else if (thrown) {
            ticksSinceDamaged++;

            if (ticksSinceDamaged >= 33) {
                mc.player.setVelocity(mc.player.getVelocity().add(0, 0.028F, 0));
            } else {
                mc.player.setVelocity(mc.player.getVelocity().withAxis(Direction.Axis.Y, ticksSinceDamaged == 1 ? 0.43F : 0));
            }

            if (MoveUtility.isMoving() && ticksSinceDamaged >= 2) {
                if (ticksSinceDamaged == 2) {
                    MoveUtility.setSpeed(MoveUtility.getSpeed() * 2.7);
                } else {
                    MoveUtility.setSpeed(MoveUtility.getSpeed());
                }
            }

            if (ticksSinceDamaged > 10 && (mc.player.isOnGround() || mc.player.getAbilities().allowFlying || mc.player.getAbilities().flying)) {
                getModule().toggle();
            }
        }
    }

    @Subscribe
    public void onPreMovementPacket(final PreMovementPacketEvent event) {
        // Ensure you are rotated backwards before throwing fireball
        if (Math.abs(MathHelper.subtractAngles((float) MoveUtility.getDirectionDegrees(), mc.player.getYaw())) > 170 && mc.player.isOnGround()) {
            ticked = true;
        }

        if (mc.player.getVelocity().getY() == -FIRST_FALL_MOTION && !mc.player.isOnGround() && ticksSinceDamaged > 1) {
            if (ticksSinceDamaged % 3 != 0) {
                yOffset += 1 / 64D;
            } else {
                yOffset -= (1 / 64D) * 2;
            }

            event.setY(event.getY() + yOffset);
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
        } else if (swapBack) {
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
        yOffset = 0;
        super.onEnable();
    }

    @Override
    public Enum<?> getEnumValue() {
        return FlightModule.Mode.FIREBALL;
    }
}

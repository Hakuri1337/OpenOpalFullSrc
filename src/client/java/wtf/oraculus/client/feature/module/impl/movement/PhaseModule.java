package wtf.oraculus.client.feature.module.impl.movement;

import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.MathHelper;
import wtf.oraculus.client.feature.helper.impl.LocalDataWatch;
import wtf.oraculus.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.packet.ReceivePacketEvent;
import wtf.oraculus.event.impl.game.player.movement.PostMoveEvent;
import wtf.oraculus.event.impl.game.player.movement.PreMovementPacketEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.player.MoveUtility;
import wtf.oraculus.utility.player.PlayerUtility;

import static wtf.oraculus.client.Constants.mc;

public final class PhaseModule extends Module {

    private final BooleanProperty autoDisable = new BooleanProperty("Auto disable", true);

    public PhaseModule() {
        super("Phase", "Allows you to walk through walls.", ModuleCategory.MOVEMENT);
        addProperties(this.autoDisable);
    }

    private boolean collision, phased, shouldForward;

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        this.collision = mc.player != null && mc.player.horizontalCollision;
    }

    @Subscribe
    public void onPreMovementPacket(final PreMovementPacketEvent event) {
        if (mc.player == null) {
            return;
        }

        final double yaw = MoveUtility.getDirectionRadians(RotationHelper.getClientHandler().getYawOr(mc.player.getYaw()));

        if (!phased) {
            if (collision) {
                final double amount = 0.005D;
                mc.player.setPosition(mc.player.getX() - MathHelper.sin((float) yaw) * amount, mc.player.getY(), mc.player.getZ() + MathHelper.cos((float) yaw) * amount);

                this.phased = true;
            }
        } else {
            if (!PlayerUtility.isInsideBlock() && this.shouldForward) {
                if (this.autoDisable.getValue()) {
                    this.setEnabled(false);
                } else {
                    this.phased = false;
                }
            }
        }

        if (phased) {
            if (LocalDataWatch.get().ticksSinceTeleport == 3) {
                final double amount = 0.8;
                mc.player.setPosition(mc.player.getX() - MathHelper.sin((float) yaw) * amount, mc.player.getY(), mc.player.getZ() + MathHelper.cos((float) yaw) * amount);
            }
        }
    }

    @Subscribe
    public void onPostMove(final PostMoveEvent event) {
        if (shouldForward) {
            MoveUtility.setSpeed(0);
        }
    }

    @Subscribe
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (event.getPacket() instanceof PlayerPositionLookS2CPacket posLook) {
            shouldForward = true;
        }
    }

    @Override
    protected void onEnable() {
        this.phased = false;
        this.shouldForward = false;
        super.onEnable();
    }
}
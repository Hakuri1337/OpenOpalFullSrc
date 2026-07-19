package wtf.oraculus.client.feature.module.impl.movement;

import net.minecraft.util.math.MathHelper;
import wtf.oraculus.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.mode.ModeProperty;
import wtf.oraculus.event.impl.game.input.MoveInputEvent;
import wtf.oraculus.event.impl.game.player.movement.JumpEvent;
import wtf.oraculus.event.impl.game.player.movement.PreMovementPacketEvent;
import wtf.oraculus.event.impl.game.player.movement.SprintEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.player.MoveUtility;

import static wtf.oraculus.client.Constants.mc;

public final class MovementFixModule extends Module {

    private final ModeProperty<Mode> modeProperty = new ModeProperty<>("Mode", Mode.NORMAL);
    private final BooleanProperty packetOnly = new BooleanProperty("Packet only", false).hideIf(() -> this.modeProperty.getValue() != Mode.SPRINT_ONLY);

    public MovementFixModule() {
        super("Movement Fix", "Locks your movement to your rotations.", ModuleCategory.MOVEMENT);
        addProperties(modeProperty, packetOnly);
    }

    @Subscribe
    public void onMoveInput(final MoveInputEvent event) {
        if (this.modeProperty.getValue() != Mode.NORMAL) {
            return;
        }

        final float forward = event.getForward();
        final float strafe = event.getSideways();

        if (forward == 0 && strafe == 0) {
            return;
        }

        final float angle = (float) Math.toDegrees(MoveUtility.getDirection(RotationHelper.getClientHandler().getYawOr(mc.player.getYaw()), forward, strafe));

        float closestForward = 0, closestSideways = 0, closestDifference = Float.MAX_VALUE;

        for (float predictedForward = -1F; predictedForward <= 1F; predictedForward += 1F) {
            for (float predictedStrafe = -1F; predictedStrafe <= 1F; predictedStrafe += 1F) {
                if (predictedStrafe == 0 && predictedForward == 0) continue;

                final float predictedAngle = (float) Math.toDegrees(MoveUtility.getDirection(mc.player.getYaw(), predictedForward, predictedStrafe));
                final double difference = MathHelper.angleBetween(angle, predictedAngle);

                if (difference < closestDifference) {
                    closestDifference = (float) difference;
                    closestForward = predictedForward;
                    closestSideways = predictedStrafe;
                }
            }
        }

        event.setForward(closestForward);
        event.setSideways(closestSideways);
    }

    @Subscribe
    public void onSprint(final SprintEvent event) {
        if (!this.packetOnly.getValue() && this.isResetSprint()) {
            mc.player.setSprinting(false);
            event.setCanStartSprinting(false);
        }
    }

    @Subscribe
    public void onJump(final JumpEvent event) {
        if (event.isSprinting() && !this.packetOnly.getValue() && this.isResetSprint()) {
            event.setSprinting(false);
        }
    }

    @Subscribe
    public void onPreMovementPacket(final PreMovementPacketEvent event) {
        if (this.packetOnly.getValue() && this.isResetSprint()) {
            event.setSprinting(false);
        }
    }

    private boolean isResetSprint() {
        if (this.modeProperty.getValue() != Mode.SPRINT_ONLY) {
            return false;
        }
        final float rotationYaw = mc.player.getYaw();
        final float movementYaw = MoveUtility.getDirectionDegrees(RotationHelper.getClientHandler().getYawOr(rotationYaw));
        final float diff = MathHelper.angleBetween(movementYaw, rotationYaw);
        return diff > 45.0F + 0.005F;
    }

    public boolean isFixMovement() {
        return this.isEnabled() && this.modeProperty.getValue() == Mode.NORMAL;
    }

    public enum Mode {
        NORMAL("Normal"),
        SPRINT_ONLY("Sprint only");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}

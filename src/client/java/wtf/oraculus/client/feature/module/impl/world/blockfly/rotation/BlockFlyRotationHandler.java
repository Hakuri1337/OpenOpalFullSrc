package wtf.oraculus.client.feature.module.impl.world.blockfly.rotation;

import wtf.oraculus.event.impl.game.player.movement.PreMovementPacketEvent;

public final class BlockFlyRotationHandler {
    private static boolean active;
    private static BlockFlyRotation targetRotation;
    private static BlockFlyRotation previousRotation;
    private static BlockFlyRotation sentRotation;
    private static BlockFlyRotation previousSentRotation;

    private BlockFlyRotationHandler() {
    }

    public static void activate(final float yaw, final float pitch) {
        active = true;
        final BlockFlyRotation initial = new BlockFlyRotation(yaw, pitch);
        targetRotation = initial.copy();
        previousRotation = initial.copy();
        sentRotation = initial.copy();
        previousSentRotation = initial.copy();
    }

    public static void deactivate() {
        active = false;
        targetRotation = null;
        previousRotation = null;
        sentRotation = null;
        previousSentRotation = null;
    }

    public static void setTargetRotation(final BlockFlyRotation rotation) {
        targetRotation = rotation == null ? null : rotation.copy();
    }

    public static BlockFlyRotation targetRotation() {
        return targetRotation == null ? null : targetRotation.copy();
    }

    public static BlockFlyRotation previousRotation() {
        return previousRotation == null ? null : previousRotation.copy();
    }

    public static BlockFlyRotation sentRotation() {
        return sentRotation == null ? null : sentRotation.copy();
    }

    public static BlockFlyRotation previousSentRotation() {
        return previousSentRotation == null ? null : previousSentRotation.copy();
    }

    public static void setPreviousSentRotation(final BlockFlyRotation rotation) {
        previousSentRotation = rotation == null ? null : rotation.copy();
    }

    public static void markSentRotation(final BlockFlyRotation rotation) {
        if (rotation == null) {
            return;
        }
        previousSentRotation = sentRotation == null ? null : sentRotation.copy();
        sentRotation = rotation.copy();
        previousRotation = rotation.copy();
    }

    public static boolean isOwningRotation() {
        return active && targetRotation != null;
    }

    public static void applyToMovementPacket(final PreMovementPacketEvent event) {
        if (!isOwningRotation()) {
            return;
        }
        previousSentRotation = sentRotation == null ? null : sentRotation.copy();
        sentRotation = targetRotation.copy();
        previousRotation = targetRotation.copy();
        event.setYaw(wireYaw(targetRotation.yaw()));
        event.setPitch(targetRotation.pitch());
    }

    public static float wireYaw(final float yaw) {
        return yaw > -360.0F && yaw < 360.0F ? yaw + 720.0F : yaw;
    }
}

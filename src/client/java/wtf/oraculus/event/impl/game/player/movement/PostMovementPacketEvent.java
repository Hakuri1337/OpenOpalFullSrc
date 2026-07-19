package wtf.oraculus.event.impl.game.player.movement;

public final class PostMovementPacketEvent {

    private final double x, y, z;
    private final float yaw, pitch;
    private final boolean onGround, sprinting;

    public PostMovementPacketEvent(final double x, final double y, final double z, final float yaw, final float pitch, final boolean onGround, final boolean sprinting) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.onGround = onGround;
        this.sprinting = sprinting;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public boolean isOnGround() {
        return onGround;
    }

    public boolean isSprinting() {
        return sprinting;
    }
}

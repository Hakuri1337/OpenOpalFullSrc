package wtf.oraculus.event.impl.game.player.movement;

import wtf.oraculus.event.EventCancellable;

public final class PreMovementPacketEvent extends EventCancellable {

    private double x, y, z;
    private float yaw, pitch;
    private boolean onGround, sprinting, horizontalCollision, forceInput;

    public PreMovementPacketEvent(final double x, final double y, final double z, final float yaw, final float pitch, final boolean onGround, final boolean sprinting, final boolean horizontalCollision) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.onGround = onGround;
        this.sprinting = sprinting;
        this.horizontalCollision = horizontalCollision;
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

    public void setX(final double x) {
        this.x = x;
    }

    public void setY(final double y) {
        this.y = y;
    }

    public void setZ(final double z) {
        this.z = z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setYaw(final float yaw) {
        this.yaw = yaw;
    }

    public void setPitch(final float pitch) {
        this.pitch = pitch;
    }

    public void setOnGround(final boolean onGround) {
        this.onGround = onGround;
    }

    public boolean isOnGround() {
        return onGround;
    }

    public boolean isSprinting() {
        return sprinting;
    }

    public void setSprinting(boolean sprinting) {
        this.sprinting = sprinting;
    }

    public boolean isHorizontalCollision() {
        return horizontalCollision;
    }

    public void setHorizontalCollision(boolean horizontalCollision) {
        this.horizontalCollision = horizontalCollision;
    }

    public boolean isForceInput() {
        return forceInput;
    }

    public void setForceInput(boolean forceInput) {
        this.forceInput = forceInput;
    }
}

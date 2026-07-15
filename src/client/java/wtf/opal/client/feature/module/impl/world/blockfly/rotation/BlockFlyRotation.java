package wtf.opal.client.feature.module.impl.world.blockfly.rotation;

public final class BlockFlyRotation {
    private float yaw;
    private float pitch;

    public BlockFlyRotation() {
        this(0.0F, 0.0F);
    }

    public BlockFlyRotation(final float yaw, final float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public float yaw() {
        return this.yaw;
    }

    public float pitch() {
        return this.pitch;
    }

    public void setYaw(final float yaw) {
        this.yaw = yaw;
    }

    public void setPitch(final float pitch) {
        this.pitch = pitch;
    }

    public void set(final float yaw, final float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public BlockFlyRotation copy() {
        return new BlockFlyRotation(this.yaw, this.pitch);
    }

    public BlockFlyRotation snapToSensitivity(final float sensitivity) {
        final float scaled = sensitivity * 0.6F + 0.2F;
        final float step = scaled * scaled * scaled * 1.2F;
        this.yaw -= this.yaw % step;
        this.pitch -= this.pitch % step;
        return this;
    }
}

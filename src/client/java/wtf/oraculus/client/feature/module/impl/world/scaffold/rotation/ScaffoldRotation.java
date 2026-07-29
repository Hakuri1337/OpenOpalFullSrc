package wtf.oraculus.client.feature.module.impl.world.scaffold.rotation;

public final class ScaffoldRotation {
    private float yaw;
    private float pitch;

    public ScaffoldRotation() {
        this(0.0F, 0.0F);
    }

    public ScaffoldRotation(final float yaw, final float pitch) {
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

    public ScaffoldRotation copy() {
        return new ScaffoldRotation(this.yaw, this.pitch);
    }

    public ScaffoldRotation snapToSensitivity(final float sensitivity) {
        final float scaled = sensitivity * 0.6F + 0.2F;
        final float step = scaled * scaled * scaled * 1.2F;
        this.yaw -= this.yaw % step;
        this.pitch -= this.pitch % step;
        return this;
    }
}

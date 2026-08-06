package wtf.oraculus.client.feature.module.impl.world.ssngscaffold;

import net.minecraft.util.math.MathHelper;

import static wtf.oraculus.client.Constants.mc;

/** SSNG's mutable rotation value. */
public final class SsngRotation {
    private float yaw;
    private float pitch;

    public SsngRotation() {
        this(0.0F, 0.0F);
    }

    public SsngRotation(final float yaw, final float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public float yaw() { return this.yaw; }
    public float pitch() { return this.pitch; }
    public void setYaw(final float yaw) { this.yaw = yaw; }
    public void setPitch(final float pitch) { this.pitch = pitch; }
    public SsngRotation copy() { return new SsngRotation(this.yaw, this.pitch); }

    public SsngRotation fixedSensitivity() {
        if (mc == null || mc.options == null) return this;
        final float sensitivity = mc.options.getMouseSensitivity().getValue().floatValue();
        final float step = (sensitivity * 0.6F + 0.2F);
        final float gcd = step * step * step * 1.2F;
        if (gcd > 0.0F) {
            this.yaw -= this.yaw % gcd;
            this.pitch -= this.pitch % gcd;
        }
        this.pitch = MathHelper.clamp(this.pitch, -90.0F, 90.0F);
        return this;
    }
}

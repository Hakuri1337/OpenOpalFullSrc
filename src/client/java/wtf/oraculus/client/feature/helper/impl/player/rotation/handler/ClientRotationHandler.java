package wtf.oraculus.client.feature.helper.impl.player.rotation.handler;

import net.minecraft.util.math.Vec2f;
import wtf.oraculus.event.EventDispatcher;
import wtf.oraculus.event.impl.game.input.MouseUpdateEvent;
import wtf.oraculus.event.subscriber.IEventSubscriber;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.player.RotationUtility;

import static wtf.oraculus.client.Constants.mc;

public final class ClientRotationHandler implements IEventSubscriber {

    public ClientRotationHandler() {
        EventDispatcher.subscribe(this);
    }

    private Vec2f rotation;
    private boolean ticking;

    @Subscribe(priority = 1)
    public void onMouseUpdate(MouseUpdateEvent event) {
        if (mc.player != null && !event.isUnlockCursorRun()) {
            if (this.rotation == null) {
                this.initializeRotation();
            }

            final double multiplier = event.getSensitivityMultiplier();
            final double cursorX = event.getDeltaX() * multiplier;
            final double cursorY = event.getDeltaY() * multiplier;

            int yMultiplier = 1;
            if (mc.options.getInvertMouseY().getValue()) {
                yMultiplier = -1;
            }

            final float deltaYaw = (float) cursorX * 0.15F;
            final float deltaPitch = (float) (cursorY * (double) yMultiplier) * 0.15F;
            final float yaw = this.rotation.x + deltaYaw;
            final float pitch = this.rotation.y + deltaPitch;
            this.rotation = new Vec2f(yaw, Math.clamp(pitch % 360.0F, -90.0F, 90.0F));
        }
        this.ticking = true;
    }

    private void initializeRotation() {
        this.rotation = RotationUtility.getRotation();
        this.lastRenderYaw = mc.player.lastRenderYaw;
        this.renderYaw = mc.player.renderYaw;
        this.lastRenderPitch = mc.player.lastRenderPitch;
        this.renderPitch = mc.player.renderPitch;
    }

    private float lastRenderYaw, renderYaw;
    private float lastRenderPitch, renderPitch;

    public void tickCamera() {
        if (this.rotation != null) {
            this.lastRenderYaw = this.renderYaw;
            this.lastRenderPitch = this.renderPitch;
            this.renderPitch = this.renderPitch + (this.rotation.y - this.renderPitch) * 0.5F;
            this.renderYaw = this.renderYaw + (this.rotation.x - this.renderYaw) * 0.5F;
        }
    }

    public void onPostMouseUpdate() {
        this.ticking = false;
    }

    public void onRotationSet() {
        if (!this.ticking) {
            this.rotation = null;
        }
    }

    public float getYawOr(float fallback) {
        return this.rotation == null ? fallback : this.rotation.x;
    }

    public float getPitchOr(float fallback) {
        return this.rotation == null ? fallback : this.rotation.y;
    }

    public float getLastRenderYawOr(float fallback) {
        return this.rotation == null ? fallback : this.lastRenderYaw;
    }

    public float getLastRenderPitchOr(float fallback) {
        return this.rotation == null ? fallback : this.lastRenderPitch;
    }

    public float getRenderYawOr(float fallback) {
        return this.rotation == null ? fallback : this.renderYaw;
    }

    public float getRenderPitchOr(float fallback) {
        return this.rotation == null ? fallback : this.renderPitch;
    }

    public Vec2f getRotation() {
        return rotation;
    }

    public void setRotation(Vec2f rotation) {
        this.rotation = rotation;
    }

    public void setTicking(boolean ticking) {
        this.ticking = ticking;
    }
}

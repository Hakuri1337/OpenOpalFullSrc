package wtf.oraculus.event.impl.game.player.movement.knockback;

import wtf.oraculus.event.EventCancellable;

public final class VelocityUpdateEvent extends EventCancellable {

    private double velocityX, velocityY, velocityZ;

    private boolean explosion;

    public VelocityUpdateEvent(final double velocityX, final double velocityY, final double velocityZ, final boolean explosion) {
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.velocityZ = velocityZ;
        this.explosion = explosion;
    }

    public boolean isExplosion() {
        return explosion;
    }

    public double getVelocityX() {
        return velocityX;
    }

    public double getVelocityY() {
        return velocityY;
    }

    public double getVelocityZ() {
        return velocityZ;
    }

    public void setVelocityX(final double velocityX) {
        this.velocityX = velocityX;
    }

    public void setVelocityY(final double velocityY) {
        this.velocityY = velocityY;
    }

    public void setVelocityZ(final double velocityZ) {
        this.velocityZ = velocityZ;
    }
}

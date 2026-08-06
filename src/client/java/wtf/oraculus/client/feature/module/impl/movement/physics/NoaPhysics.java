package wtf.oraculus.client.feature.module.impl.movement.physics;

public final class NoaPhysics {
    public static final double TICK_DELTA = 1.0D / 30.0D;

    public double impulse, force, velocity, gravity = -10.0D;
    private final double mass = 1.0D;

    public double getMotionForTick() {
        // forces
        final double massDiv = 1.0D / this.mass;
        this.force *= massDiv;
        // gravity
        this.force += this.gravity;
        this.force *= 2.0D;

        // impulses
        this.impulse *= massDiv;
        this.force *= TICK_DELTA;
        this.impulse += this.force;
        // velocity
        this.velocity += this.impulse;

        this.force = 0.0D;
        this.impulse = 0.0D;

        return this.velocity;
    }
}
package wtf.oraculus.client.feature.module.impl.world.ssngscaffold;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

/** SSNG's short-horizon movement predictor. */
public final class SsngFallingPlayer {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private double x, y, z;
    private Vec3d motion;
    private Vec3d eyePos;
    private final float yaw, strafe, forward, jumpMovementFactor;
    private boolean onGround;

    public SsngFallingPlayer(final ClientPlayerEntity player, final float serverYaw) {
        this.x = player.getX(); this.y = player.getY(); this.z = player.getZ();
        this.motion = player.getVelocity(); this.yaw = serverYaw;
        this.strafe = player.input.getMovementInput().x; this.forward = player.input.getMovementInput().y;
        this.onGround = player.isOnGround();
        this.jumpMovementFactor = player.isSprinting() ? 0.026F : 0.02F;
        this.eyePos = player.getEyePos();
    }

    private void calculateForTick() {
        updateVelocity(this.jumpMovementFactor, new Vec3d(this.strafe, 0.0D, this.forward));
        this.x += this.motion.x; this.y += this.motion.y; this.z += this.motion.z;
        updateGroundState();
        this.motion = this.motion.add(0.0D, -0.08D, 0.0D);
        this.eyePos = new Vec3d(this.x, this.y + this.mc.player.getEyeHeight(this.mc.player.getPose()), this.z);
        this.motion = new Vec3d(this.motion.x * 0.91D, this.motion.y * 0.98D, this.motion.z * 0.91D);
    }

    private void updateGroundState() {
        final BlockHitResult result = rayTraceHit(new Vec3d(this.x, this.y, this.z), new Vec3d(this.x, this.y - 0.2D, this.z));
        this.onGround = result != null && result.getType() == HitResult.Type.BLOCK && result.getSide() == Direction.UP;
    }

    private void updateVelocity(final float speed, final Vec3d input) {
        final double length = input.lengthSquared();
        if (length < 1.0E-7D) return;
        final Vec3d normalized = (length > 1.0D ? input.normalize() : input).multiply(speed);
        final float sin = MathHelper.sin(this.yaw * ((float) Math.PI / 180.0F));
        final float cos = MathHelper.cos(this.yaw * ((float) Math.PI / 180.0F));
        this.motion = this.motion.add(normalized.x * cos - normalized.z * sin, 0.0D, normalized.z * cos + normalized.x * sin);
    }

    public void calculate(final int ticks) {
        for (int i = 0; i < ticks; i++) calculateForTick();
    }

    public BlockPos findCollision(final int ticks) {
        final double width = this.mc.player == null ? 0.3D : this.mc.player.getWidth() / 2.0D;
        for (int i = 0; i < ticks; i++) {
            final Vec3d start = getPos(); calculateForTick(); final Vec3d end = getPos();
            final Vec3d[] offsets = {Vec3d.ZERO, new Vec3d(width, 0, width), new Vec3d(-width, 0, width), new Vec3d(width, 0, -width), new Vec3d(-width, 0, -width), new Vec3d(width, 0, 0), new Vec3d(-width, 0, 0), new Vec3d(0, 0, width), new Vec3d(0, 0, -width)};
            for (final Vec3d offset : offsets) {
                final BlockHitResult hit = rayTraceHit(start.add(offset), end.add(offset));
                if (hit != null && hit.getSide() == Direction.UP) return hit.getBlockPos();
            }
        }
        return null;
    }

    private BlockHitResult rayTraceHit(final Vec3d start, final Vec3d end) {
        if (mc.world == null || start.squaredDistanceTo(end) < 1.0E-14D) return null;
        return mc.world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
    }

    public double getY() { return this.y; }
    public Vec3d getEyePos() { return this.eyePos.add(0.0D, 0.0D, 0.0D); }
    public Vec3d getPos() { return new Vec3d(this.x, this.y, this.z); }
}

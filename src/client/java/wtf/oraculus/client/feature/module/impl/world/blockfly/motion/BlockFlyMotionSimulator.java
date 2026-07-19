package wtf.oraculus.client.feature.module.impl.world.blockfly.motion;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

import static wtf.oraculus.client.Constants.mc;

public final class BlockFlyMotionSimulator {
    private double x;
    private double y;
    private double z;
    private double motionX;
    private double motionY;
    private double motionZ;
    private final float yaw;
    private final float sideways;
    private final float forward;
    private final float jumpPower;
    private final boolean sprinting;

    public BlockFlyMotionSimulator(final ClientPlayerEntity player) {
        final Vec3d velocity = player.getVelocity();
        final Vec2f input = player.input.getMovementInput();
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
        this.motionX = velocity.x;
        this.motionY = velocity.y;
        this.motionZ = velocity.z;
        this.yaw = player.getYaw();
        this.sideways = input.x;
        this.forward = input.y;
        this.sprinting = player.isSprinting();

        final ClientWorld world = mc.world;
        final float currentFactor = world == null ? 1.0F : world.getBlockState(player.getBlockPos())
                .getBlock().getJumpVelocityMultiplier();
        final float belowFactor = world == null ? 1.0F : world.getBlockState(player.getSteppingPos())
                .getBlock().getJumpVelocityMultiplier();
        this.jumpPower = 0.42F * (currentFactor == 1.0F ? belowFactor : currentFactor)
                + player.getJumpBoostVelocityModifier();
    }

    public void simulateWithFriction(final int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            this.tickWithFriction();
        }
    }

    public double y() {
        return this.y;
    }

    private void tickWithFriction() {
        float strafe = this.sideways * 0.98F;
        float moveForward = this.forward * 0.98F;
        float magnitude = strafe * strafe + moveForward * moveForward;
        if (magnitude >= 1.0E-4F) {
            magnitude = MathHelper.sqrt(magnitude);
            if (magnitude < 1.0F) {
                magnitude = 1.0F;
            }
            float speed = this.jumpPower;
            if (this.sprinting) {
                speed *= 1.3F;
            }
            magnitude = speed / magnitude;
            final float sine = MathHelper.sin(this.yaw * (float) Math.PI / 180.0F);
            final float cosine = MathHelper.cos(this.yaw * (float) Math.PI / 180.0F);
            strafe *= magnitude;
            moveForward *= magnitude;
            this.motionX += strafe * cosine - moveForward * sine;
            this.motionZ += moveForward * cosine + strafe * sine;
        }

        this.motionY -= 0.08D;
        this.motionY *= 0.98F;
        this.x += this.motionX;
        this.y += this.motionY;
        this.z += this.motionZ;
        this.motionX *= 0.91D;
        this.motionZ *= 0.91D;
    }
}

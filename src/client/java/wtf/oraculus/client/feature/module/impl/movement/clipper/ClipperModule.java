package wtf.oraculus.client.feature.module.impl.movement.clipper;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.lwjgl.glfw.GLFW;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.impl.movement.physics.NoaPhysics;
import wtf.oraculus.client.feature.module.impl.movement.physics.PhysicsModule;
import wtf.oraculus.client.feature.module.impl.visual.overlay.impl.dynamicisland.DynamicIslandElement;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.press.KeyPressEvent;
import wtf.oraculus.event.impl.press.MousePressEvent;
import wtf.oraculus.event.subscriber.Subscribe;

import java.util.concurrent.atomic.AtomicReference;

import static wtf.oraculus.client.Constants.mc;

public final class ClipperModule extends Module {
    private final BooleanProperty vanillaLimit = new BooleanProperty("Limit distance", true);
    private final BooleanProperty upwards = new BooleanProperty("Upwards", true);

    public ClipperModule() {
        super("Clipper", "Gives you the option to clip up or down when available.", ModuleCategory.MOVEMENT);
        this.addProperties(this.vanillaLimit, this.upwards);
    }

    private final ClipperIsland dynamicIsland = new ClipperIsland(this);

    private Double upPos, downPos;

    Double getUpPos() {
        return upPos;
    }

    Double getDownPos() {
        return downPos;
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        this.upPos = null;
        this.downPos = null;

        if (mc.player != null) {
            if (this.upwards.getValue()) {
                final PhysicsModule physicsModule = OraculusClient.getInstance().getModuleRepository().getModule(PhysicsModule.class);
                if (mc.player.isOnGround() || !physicsModule.isEnabled()) {
                    this.searchUpwards();
                }
            }
            this.searchDownwards();
        }

        if (this.upPos == null && this.downPos == null) {
            DynamicIslandElement.removeTrigger(this.dynamicIsland);
        } else {
            DynamicIslandElement.addTrigger(this.dynamicIsland);
        }
    }

    @Subscribe

    public void onKeyPress(final KeyPressEvent event) {
        if (mc.player == null || mc.currentScreen != null) return;
        if (event.getInteractionCode() == GLFW.GLFW_KEY_UP) {
            this.clipUp();
        } else if (event.getInteractionCode() == GLFW.GLFW_KEY_DOWN) {
            this.clipDown();
        }
    }

    @Subscribe
    public void onMousePress(final MousePressEvent event) {
        if (mc.player == null || mc.currentScreen != null) return;
        if (event.getInteractionCode() == 4) {
            this.clipUp();
        } else if (event.getInteractionCode() == 3) {
            this.clipDown();
        }
    }

    private void clipUp() {
        if (this.upPos != null) {
            this.setPosY(this.upPos);
        }
    }

    private void clipDown() {
        if (this.downPos != null) {
            this.setPosY(this.downPos);
        }
    }

    private void setPosY(final double posY) {
        mc.player.setPosition(mc.player.getEntityPos().withAxis(Direction.Axis.Y, posY));
        mc.player.setVelocity(new Vec3d(0, 0, 0));
        final PhysicsModule physicsModule = OraculusClient.getInstance().getModuleRepository().getModule(PhysicsModule.class);
        if (physicsModule.isEnabled()) {
            final NoaPhysics physics = physicsModule.getPhysics();
            physics.velocity = 0.0D;
        }
    }

    private void searchUpwards() {
        int blocks = 1;
        final AtomicReference<VoxelShape> collision = new AtomicReference<>();
        while ((blocks < 10 || !this.vanillaLimit.getValue()) && !mc.world.isOutOfHeightLimit((int) mc.player.getY() + blocks)) {
            blocks++;
            if (BlockPos.stream(mc.player.getBoundingBox().offset(0.0D, blocks, 0.0D)).noneMatch(pos -> {
                final BlockState blockState = mc.world.getBlockState(pos);
                final VoxelShape voxelShape = blockState.getCollisionShape(mc.world, pos);
                if (!voxelShape.isEmpty()) {
                    collision.set(voxelShape.offset(pos.getX(), pos.getY(), pos.getZ()));
                    return true;
                }
                return false;
            })) {
                final VoxelShape voxelShape = collision.get();
                if (voxelShape != null) {
                    this.upPos = voxelShape.getMax(Direction.Axis.Y);
                    break;
                }
            }
        }
    }

    private void searchDownwards() {
        final Box boundingBox = mc.player.getBoundingBox().offset(0.0D, -(mc.player.getY() % 1.0D), 0.0D);
        int blocks = 1;
        boolean found = false, air = false;
        final AtomicReference<VoxelShape> collision = new AtomicReference<>();
        while ((blocks <= 10 || !this.vanillaLimit.getValue()) && !mc.world.isOutOfHeightLimit((int) mc.player.getY() - blocks)) {
            blocks++;
            if (BlockPos.stream(boundingBox.offset(0.0D, -blocks, 0.0D)).anyMatch(pos -> {
                final BlockState blockState = mc.world.getBlockState(pos);
                final VoxelShape voxelShape = blockState.getCollisionShape(mc.world, pos);
                if (!voxelShape.isEmpty()) {
                    collision.set(voxelShape.offset(pos.getX(), pos.getY(), pos.getZ()));
                    return true;
                }
                return false;
            })) {
                if (air) {
                    final VoxelShape voxelShape = collision.get();
                    this.downPos = voxelShape.getMax(Direction.Axis.Y);
                    break;
                }
                found = true;
            } else if (found) {
                air = true;
            }
        }
    }

    @Override
    protected void onDisable() {
        DynamicIslandElement.removeTrigger(this.dynamicIsland);
        this.upPos = null;
        this.downPos = null;
    }
}

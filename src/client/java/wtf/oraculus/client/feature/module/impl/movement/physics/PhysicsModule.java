package wtf.oraculus.client.feature.module.impl.movement.physics;

import net.minecraft.block.*;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShapes;
import wtf.oraculus.client.feature.helper.impl.LocalDataWatch;
import wtf.oraculus.client.feature.helper.impl.player.timer.TimerHelper;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.packet.ReceivePacketEvent;
import wtf.oraculus.event.impl.game.player.interaction.block.BlockBreakCanHarvestEvent;
import wtf.oraculus.event.impl.game.player.interaction.block.BlockBreakHardnessEvent;
import wtf.oraculus.event.impl.game.player.movement.PostMoveEvent;
import wtf.oraculus.event.impl.game.player.movement.PreMovementPacketEvent;
import wtf.oraculus.event.impl.game.player.movement.step.StepEvent;
import wtf.oraculus.event.impl.game.world.BlockShapeEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import mixin.ClientPlayerEntityAccessor;
import wtf.oraculus.utility.player.MoveUtility;

import static wtf.oraculus.client.Constants.mc;

public final class
PhysicsModule extends Module {
    private final BooleanProperty updateTimer = new BooleanProperty("Update timer", true);

    public PhysicsModule() {
        super("Physics", "Modifies game physics.", ModuleCategory.MOVEMENT);
        this.addProperties(this.updateTimer);
    }

    private final NoaPhysics physics = new NoaPhysics();
    private double jump;

    @Subscribe
    public void onBlockShape(final BlockShapeEvent event) {
        final BlockState blockState = event.getBlockState();
        final Block block = blockState.getBlock();
        if (block instanceof ChestBlock || block instanceof BedBlock) {
            event.setVoxelShape(VoxelShapes.cuboid(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D));
        }
    }

    @Subscribe
    public void onBlockBreakHardness(final BlockBreakHardnessEvent event) {
        final BlockState blockState = event.getBlockState();
        final Block block = blockState.getBlock();
        switch (block) {
            case BedBlock ignored -> event.setHardness(1.5F);
            case PillarBlock ignored -> event.setHardness(1.0F); // wood
            default -> {
                if (blockState.isIn(BlockTags.TERRACOTTA)) {
                    event.setHardness(0.5F);
                } else if (blockState.isIn(BlockTags.WOOL)) {
                    event.setHardness(0.7F);
                } else if (block == Blocks.CHEST) {
                    event.setHardness(1.75F);
                }
            }
        }
    }

    @Subscribe
    public void onBlockBreakCanHarvest(final BlockBreakCanHarvestEvent event) {
        final BlockState blockState = event.getBlockState();
        final Block block = blockState.getBlock();
        if (block instanceof BedBlock) {
            event.setCanHarvest(mc.player.canHarvest(Blocks.STONE.getDefaultState()));
        }
    }

    @Subscribe
    public void onPreMovementPacket(final PreMovementPacketEvent event) {
        final ClientPlayerEntityAccessor accessor = (ClientPlayerEntityAccessor) mc.player;
        if (event.getX() != accessor.getLastXClient() || event.getY() != accessor.getLastYClient() || event.getZ() != accessor.getLastZClient()) {
            accessor.setTicksSinceLastPositionPacketSent(20);
        } else {
            accessor.setTicksSinceLastPositionPacketSent(0);
        }
    }

    @Subscribe(priority = 3)
    public void onPostMove(final PostMoveEvent event) {
        if (mc.player.isOnGround() && this.physics.velocity < 0.0D) {
            this.physics.velocity = 0.0D;
        }

        if (mc.player.getVelocity().getY() == 0.42F) {
            this.jump = Math.min(this.jump + 1, 3);
            this.physics.impulse += 8.0D;
        }

        if (LocalDataWatch.get().groundTicks > 5) {
            this.jump = 0;
        }

        final double speed;
        if (!MoveUtility.isMoving()) {
            speed = 0.0D;
        } else if (mc.player.isUsingItem() && mc.player.getMainHandStack().getUseAction() != UseAction.BLOCK) {
            speed = 0.06D;
        } else {
            speed = 0.26D + 0.025D * this.jump;
        }
        MoveUtility.setSpeed(speed);
        mc.player.setVelocity(mc.player.getVelocity().withAxis(Direction.Axis.Y, this.physics.getMotionForTick() * NoaPhysics.TICK_DELTA));
    }

    @Subscribe(priority = 1)
    public void onPreGameTick(final PreGameTickEvent event) {
        if (this.updateTimer.getValue()) {
            TimerHelper.getInstance().timer = 1.5F;
        }
    }

    @Override
    protected void onDisable() {
        TimerHelper.getInstance().timer = 1.0F;
    }

    public NoaPhysics getPhysics() {
        return physics;
    }

    @Subscribe
    public void onStepHeight(final StepEvent event) {
        event.setStepHeight(1.0F);
    }

    @Subscribe
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (event.getPacket() instanceof CustomPayloadS2CPacket(
                CustomPayload payload
        )) {
            if (payload instanceof ResyncPhysicsPayload(float motionX, float motionY, float motionZ)) {
                this.physics.impulse = 0.0D;
                this.physics.force = 0.0D;
                this.physics.velocity = motionY;
            }
        } else if (event.getPacket() instanceof GameJoinS2CPacket) {
            this.physics.impulse = 0.0D;
            this.physics.force = 0.0D;
            this.physics.velocity = 0.0D;
        }
    }

    public record ResyncPhysicsPayload(float motionX, float motionY, float motionZ) implements CustomPayload {
        public static final Id<ResyncPhysicsPayload> ID = new Id<>(Identifier.of("bloxd", "resyncphysics"));
        public static final PacketCodec<PacketByteBuf, ResyncPhysicsPayload> CODEC = PacketCodec.of((value, buf) -> {
        }, buf -> {
            final float motionX = buf.readFloat();
            final float motionY = buf.readFloat();
            final float motionZ = buf.readFloat();
            return new ResyncPhysicsPayload(motionX, motionY, motionZ);
        });

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}

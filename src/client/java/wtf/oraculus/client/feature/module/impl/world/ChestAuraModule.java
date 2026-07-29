package wtf.oraculus.client.feature.module.impl.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockEventS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.oraculus.client.feature.helper.impl.player.rotation.model.impl.InstantRotationModel;
import wtf.oraculus.client.feature.module.DeprecatedModule;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.impl.combat.killaura.KillAuraModule;
import wtf.oraculus.client.feature.module.impl.movement.StuckModule;
import wtf.oraculus.client.feature.module.impl.utility.AutoBucketModule;
import wtf.oraculus.client.feature.module.impl.world.scaffold.ScaffoldModule;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.duck.ClientConnectionAccess;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.JoinWorldEvent;
import wtf.oraculus.event.impl.game.packet.ReceivePacketEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.misc.time.Stopwatch;
import wtf.oraculus.utility.player.RotationUtility;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static wtf.oraculus.client.Constants.mc;

/** Naven-style nearest unopened chest opener. */
public final class ChestAuraModule extends Module implements DeprecatedModule {

    private final NumberProperty range = new NumberProperty("Range", 4.5D, 2.0D, 5.0D, 0.1D);
    private final NumberProperty nextDelay = new NumberProperty("Next Delay", "ms", 500.0D, 0.0D, 5000.0D, 50.0D);
    private final Stopwatch delayTimer = new Stopwatch();
    private final Set<BlockPos> openedChests = new HashSet<>();

    private Target target;
    private int rotateTicks;
    private int waitTicks;

    public ChestAuraModule() {
        super("ChestAura", "Automatically aims at and opens the nearest unopened chest.", ModuleCategory.WORLD);
        this.setVisible(false);
        this.addProperties(this.range, this.nextDelay);
    }

    @Subscribe
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (event.getPacket() instanceof BlockEventS2CPacket packet
                && (packet.getBlock() == Blocks.CHEST || packet.getBlock() == Blocks.TRAPPED_CHEST)
                && packet.getType() == 1 && packet.getData() == 1) {
            this.openedChests.add(this.normalizeChestPos(packet.getPos()).toImmutable());
        }
    }

    @Subscribe
    public void onJoinWorld(final JoinWorldEvent event) {
        this.openedChests.clear();
        this.clearTarget();
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }

        if (mc.currentScreen instanceof GenericContainerScreen) {
            if (this.target != null) {
                this.applyRotation();
                this.startCooldown();
            }
            return;
        }

        if (this.shouldPause()) {
            this.clearTarget();
            return;
        }

        if (this.target != null && this.target.phase == Phase.COOLDOWN) {
            this.applyRotation();
            if (this.delayTimer.hasTimeElapsed(this.nextDelay.getValue().longValue())) {
                this.clearTarget();
            }
            return;
        }

        if (this.target == null) {
            this.findTarget().ifPresent(found -> {
                this.target = found;
                this.rotateTicks = 0;
                this.waitTicks = 0;
            });
        }
        if (this.target == null) {
            return;
        }

        this.applyRotation();
        if (this.target.phase == Phase.ROTATING) {
            if (++this.rotateTicks >= 2) {
                this.interact(this.target);
                this.target.phase = Phase.WAITING_SCREEN;
                this.waitTicks = 0;
            }
            return;
        }
        if (this.target.phase == Phase.WAITING_SCREEN && ++this.waitTicks > 10) {
            this.startCooldown();
        }
    }

    private Optional<Target> findTarget() {
        final double maxDistanceSquared = this.range.getValue() * this.range.getValue();
        final int centerX = mc.player.getBlockX() >> 4;
        final int centerZ = mc.player.getBlockZ() >> 4;
        Target closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (int chunkX = centerX - 1; chunkX <= centerX + 1; chunkX++) {
            for (int chunkZ = centerZ - 1; chunkZ <= centerZ + 1; chunkZ++) {
                if (!mc.world.isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }
                for (final BlockEntity blockEntity : mc.world.getChunk(chunkX, chunkZ).getBlockEntities().values()) {
                    if (!(blockEntity instanceof ChestBlockEntity)) {
                        continue;
                    }
                    final BlockPos pos = this.normalizeChestPos(blockEntity.getPos());
                    if (!this.isClickableChest(pos) || this.openedChests.contains(pos)) {
                        continue;
                    }
                    final double distance = mc.player.squaredDistanceTo(Vec3d.ofCenter(pos));
                    if (distance > maxDistanceSquared || distance >= closestDistance) {
                        continue;
                    }
                    final BlockHitResult hit = this.createHitResult(pos);
                    closest = new Target(pos, hit, RotationUtility.getRotationFromPosition(hit.getPos()), Phase.ROTATING);
                    closestDistance = distance;
                }
            }
        }
        return Optional.ofNullable(closest);
    }

    private void interact(final Target target) {
        this.sendPacketSilent(new PlayerMoveC2SPacket.LookAndOnGround(
                target.rotation.x, target.rotation.y, mc.player.isOnGround(), false));
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, target.hit);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void applyRotation() {
        if (this.target != null) {
            RotationHelper.getHandler().rotate(this.target.rotation, InstantRotationModel.INSTANCE);
        }
    }

    private BlockHitResult createHitResult(final BlockPos pos) {
        final Direction face = this.getFacingFace(pos);
        final Vec3d hitPos = Vec3d.ofCenter(pos).add(face.getOffsetX() * 0.5D, face.getOffsetY() * 0.5D, face.getOffsetZ() * 0.5D);
        return new BlockHitResult(hitPos, face, pos, false);
    }

    private Direction getFacingFace(final BlockPos pos) {
        final Vec3d delta = mc.player.getEyePos().subtract(Vec3d.ofCenter(pos));
        return Direction.getFacing(delta.x, delta.y, delta.z);
    }

    private boolean isClickableChest(final BlockPos pos) {
        final BlockState state = mc.world.getBlockState(pos);
        return state.getBlock() instanceof ChestBlock
                && state.contains(ChestBlock.CHEST_TYPE)
                && state.get(ChestBlock.CHEST_TYPE) != ChestType.LEFT;
    }

    private BlockPos normalizeChestPos(final BlockPos pos) {
        final BlockState state = mc.world.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock) || !state.contains(ChestBlock.CHEST_TYPE)) {
            return pos;
        }
        return state.get(ChestBlock.CHEST_TYPE) == ChestType.LEFT ? pos.offset(ChestBlock.getFacing(state)) : pos;
    }

    private boolean shouldPause() {
        final var repository = OraculusClient.getInstance().getModuleRepository();
        final ScaffoldModule scaffold = repository.getModule(ScaffoldModule.class);
        final KillAuraModule killAura = repository.getModule(KillAuraModule.class);
        final AutoBucketModule autoBucket = repository.getModule(AutoBucketModule.class);
        final StuckModule stuck = repository.getModule(StuckModule.class);
        return scaffold != null && scaffold.isEnabled()
                || killAura != null && killAura.isEnabled()
                || autoBucket != null && autoBucket.isEnabled()
                || stuck != null && stuck.isEnabled();
    }

    private void startCooldown() {
        if (this.target != null) {
            this.target.phase = Phase.COOLDOWN;
            this.delayTimer.reset();
        }
    }

    private void clearTarget() {
        this.target = null;
        this.rotateTicks = 0;
        this.waitTicks = 0;
    }

    private void sendPacketSilent(final Packet<?> packet) {
        if (mc.getNetworkHandler() == null) {
            return;
        }
        final ClientConnection connection = mc.getNetworkHandler().getConnection();
        ((ClientConnectionAccess) connection).oraculus$sendPacketSilent(packet);
    }

    @Override
    protected void onEnable() {
        this.clearTarget();
        this.delayTimer.reset();
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        this.clearTarget();
        super.onDisable();
    }

    private enum Phase { ROTATING, WAITING_SCREEN, COOLDOWN }

    private static final class Target {
        private final BlockPos pos;
        private final BlockHitResult hit;
        private final Vec2f rotation;
        private Phase phase;

        private Target(final BlockPos pos, final BlockHitResult hit, final Vec2f rotation, final Phase phase) {
            this.pos = pos;
            this.hit = hit;
            this.rotation = rotation;
            this.phase = phase;
        }
    }
}

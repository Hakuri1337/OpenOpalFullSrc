package wtf.oraculus.client.feature.module.impl.world.fucker;

import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import wtf.oraculus.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.oraculus.client.feature.helper.impl.player.rotation.model.impl.InstantRotationModel;
import wtf.oraculus.client.feature.helper.impl.player.slot.SlotHelper;
import wtf.oraculus.client.feature.helper.impl.LocalDataWatch;
import wtf.oraculus.client.feature.helper.impl.server.impl.HypixelServer;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.mode.ModeProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.PostGameTickEvent;
import wtf.oraculus.event.impl.game.packet.ReceivePacketEvent;
import wtf.oraculus.event.impl.press.KeyPressEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.player.RotationUtility;
import wtf.oraculus.utility.misc.chat.ChatUtility;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.lwjgl.glfw.GLFW;

import static wtf.oraculus.client.Constants.mc;

/**
 * Port of LiquidBounce ModuleFucker.  It intentionally does not share Breaker's
 * reduced four-neighbour search: target selection, entrance handling and path
 * scoring remain local to this module.
 */
public final class FuckerModule extends Module {
    private static final double[] OUTLINE_SAMPLES = {0.1D, 0.3D, 0.5D, 0.7D, 0.9D};
    private static final int MAX_SURROUNDING_PATH_BLOCKS = 8;

    private final NumberProperty range = new NumberProperty("Range", 5.0D, 1.0D, 6.0D, 0.1D);
    private final NumberProperty wallRange = new NumberProperty("WallRange", 0.0D, 0.0D, 6.0D, 0.1D);
    private final BooleanProperty entrance = new BooleanProperty("Entrance", false);
    private final BooleanProperty breakFree = new BooleanProperty("BreakFree", true);
    private final BooleanProperty surroundings = new BooleanProperty("Surroundings", true);
    private final BooleanProperty beds = new BooleanProperty("Beds", true);
    private final BooleanProperty dragonEgg = new BooleanProperty("DragonEgg", true);
    private final NumberProperty delay = new NumberProperty("Delay", "ticks", 0.0D, 0.0D, 20.0D, 1.0D);
    private final ModeProperty<Action> action = new ModeProperty<>("Action", Action.DESTROY);
    private final BooleanProperty forceImmediateBreak = new BooleanProperty("ForceImmediateBreak", false);
    private final BooleanProperty ignoreOpenInventory = new BooleanProperty("IgnoreOpenInventory", true);
    private final BooleanProperty ignoreUsingItem = new BooleanProperty("IgnoreUsingItem", true);
    private final BooleanProperty prioritizeOverKillAura = new BooleanProperty("PrioritizeOverKillAura", false);
    private final BooleanProperty chestAsFullBlock = new BooleanProperty("ChestAsFullBlock", false);
    private final ModeProperty<SelfBed> selfBed = new ModeProperty<>("SelfBed", SelfBed.NONE);
    private final NumberProperty manualBedX = new NumberProperty("Manual bed X", 0.0D, -30_000_000D, 30_000_000D, 1.0D);
    private final NumberProperty manualBedY = new NumberProperty("Manual bed Y", 64.0D, -2048D, 2048D, 1.0D);
    private final NumberProperty manualBedZ = new NumberProperty("Manual bed Z", 0.0D, -30_000_000D, 30_000_000D, 1.0D);

    private FuckerTarget currentTarget;
    private Vec3d trackedSpawnLocation;
    private BlockPos trackedManualBed;
    private int ticks;
    private int actionAfterTick;

    public FuckerModule() {
        super("Fucker", "Destroys or uses selected blocks around you.", ModuleCategory.WORLD);
        addProperties(range, wallRange, entrance, breakFree, surroundings, beds, dragonEgg, delay, action,
                forceImmediateBreak, ignoreOpenInventory, ignoreUsingItem, prioritizeOverKillAura,
                chestAsFullBlock, selfBed, manualBedX, manualBedY, manualBedZ);
        breakFree.hideIf(() -> !entrance.getValue());
        manualBedX.hideIf(() -> !selfBed.is(SelfBed.MANUAL));
        manualBedY.hideIf(() -> !selfBed.is(SelfBed.MANUAL));
        manualBedZ.hideIf(() -> !selfBed.is(SelfBed.MANUAL));
    }

    @Override
    protected void onDisable() {
        clearCurrentTarget();
        trackedSpawnLocation = null;
        trackedManualBed = null;
        super.onDisable();
    }

    @Subscribe
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (!(event.getPacket() instanceof PlayerPositionLookS2CPacket packet) || mc.player == null) return;
        final Vec3d position = packet.change().position();
        if (mc.player.getEntityPos().squaredDistanceTo(position) > 256.0D) trackedSpawnLocation = position;
    }

    @Subscribe
    public void onKeyPress(final KeyPressEvent event) {
        if (!selfBed.is(SelfBed.MANUAL) || mc.player == null || mc.world == null) return;
        if (event.getInteractionCode() == GLFW.GLFW_KEY_KP_ADD) {
            BlockPos nearest = null;
            double bestDistance = Double.MAX_VALUE;
            final BlockPos center = mc.player.getBlockPos();
            for (int x = -16; x <= 16; x++) for (int y = -16; y <= 16; y++) for (int z = -16; z <= 16; z++) {
                final BlockPos pos = center.add(x, y, z);
                if (!(mc.world.getBlockState(pos).getBlock() instanceof BedBlock)) continue;
                final double distance = pos.getSquaredDistance(mc.player.getEyePos());
                if (distance < bestDistance) { bestDistance = distance; nearest = pos; }
            }
            trackedManualBed = nearest;
            if (nearest != null) ChatUtility.success("Fucker: tracked self bed at " + nearest.toShortString());
            else ChatUtility.error("Fucker: no bed found within 16 blocks.");
        } else if (event.getInteractionCode() == GLFW.GLFW_KEY_KP_SUBTRACT && trackedManualBed != null) {
            ChatUtility.print("Fucker: self bed untracked.");
            trackedManualBed = null;
        }
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        ticks++;
        if (mc.player == null || mc.world == null || mc.interactionManager == null || !shouldRun()) {
            clearCurrentTarget();
            return;
        }

        final FuckerTarget previous = currentTarget;
        updateCurrentTarget();
        if (previous != null && currentTarget == null) {
            mc.interactionManager.cancelBlockBreaking();
        }
        // LB's tick handler waits whenever the target changed, including the
        // initial null -> target transition.
        if (!java.util.Objects.equals(previous, currentTarget) && delay.getValue().intValue() > 0) {
            mc.interactionManager.cancelBlockBreaking();
            actionAfterTick = ticks + delay.getValue().intValue();
        }

        if (currentTarget != null && ticks >= actionAfterTick) {
            final Vec2f rotation = RotationUtility.getRotationFromPosition(currentTarget.aimPoint());
            RotationHelper.getHandler().rotate(rotation, InstantRotationModel.INSTANCE);
        }
    }

    @Subscribe
    public void onPostGameTick(final PostGameTickEvent event) {
        if (currentTarget == null || ticks < actionAfterTick || mc.player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }
        final BlockState state = mc.world.getBlockState(currentTarget.pos());
        if (state.isAir()) {
            clearCurrentTarget();
            return;
        }
        /*
         * ModuleFucker's breaker is a tick handler, not a movement-packet
         * callback.  A stationary player therefore must still mine every
         * client tick.  RotationMouseHandler has already applied the requested
         * rotation during PreGameTick, so use the actual current orientation.
         */
        final Vec2f currentRotation = RotationUtility.getRotation();
        final BlockHitResult hit = FuckerRaycast.raycast(currentRotation.x, currentRotation.y, maxRange());
        if (hit == null || !hit.getBlockPos().equals(currentTarget.pos())) {
            return;
        }

        selectBestTool(state);
        if (currentTarget.action() == Action.USE) {
            final ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            if (result.isAccepted()) mc.player.swingHand(Hand.MAIN_HAND);
            actionAfterTick = ticks + delay.getValue().intValue();
            return;
        }

        if (forceImmediateBreak.getValue()) {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, hit.getBlockPos(), hit.getSide()));
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, hit.getBlockPos(), hit.getSide()));
            mc.player.swingHand(Hand.MAIN_HAND);
            actionAfterTick = ticks + delay.getValue().intValue();
        } else if (mc.interactionManager.updateBlockBreakingProgress(hit.getBlockPos(), hit.getSide())) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private boolean shouldRun() {
        return (ignoreOpenInventory.getValue() || !(mc.currentScreen instanceof HandledScreen<?>))
                && (ignoreUsingItem.getValue() || !mc.player.isUsingItem());
    }

    private void updateCurrentTarget() {
        final List<BlockPos> targetBlocks = searchPossibleTargetPositions();
        validateCurrentTarget(targetBlocks);
        if (currentTarget != null || targetBlocks.isEmpty()) return;

        for (final BlockPos target : targetBlocks) {
            final double effectiveWallRange = entrance.getValue() && hasEntrance(target) ? range.getValue() : wallRange.getValue();
            final Vec3d point = findReachablePoint(target, range.getValue(), effectiveWallRange);
            if (point != null && consider(new FuckerTarget(target, action.getValue(), point, true, null), range.getValue(), effectiveWallRange)) return;
        }

        for (final BlockPos target : targetBlocks) {
            if (entrance.getValue() && breakFree.getValue()) {
                final BlockPos weak = weakestNeighbor(target);
                if (weak != null && consider(new FuckerTarget(weak, Action.DESTROY, weak.toCenterPos(), false, null), range.getValue(), range.getValue())) return;
            } else if (surroundings.getValue()) {
                final FuckerPath path = findBestSurroundingPath(target);
                if (path != null && consider(new FuckerTarget(path.firstBlock(), Action.DESTROY, path.info().targetPoint(), false, path.info()), range.getValue(), wallRange.getValue())) return;
            }
        }
    }

    private void validateCurrentTarget(final List<BlockPos> targets) {
        if (currentTarget == null) return;
        final BlockPos actual = currentTarget.pathInfo() == null ? currentTarget.pos() : currentTarget.pathInfo().actualTarget();
        if (!targets.contains(actual) || (currentTarget.directTarget() && currentTarget.action() != action.getValue())
                || !isReachable(currentTarget.pos(), range.getValue(), wallRange.getValue())) {
            clearCurrentTarget();
        }
    }

    private boolean consider(final FuckerTarget candidate, final double normalRange, final double throughWallsRange) {
        if (!isReachable(candidate.pos(), normalRange, throughWallsRange)) return false;
        if (currentTarget != null && candidate.compareTo(currentTarget) >= 0) return false;
        clearCurrentTarget();
        currentTarget = candidate;
        return true;
    }

    private List<BlockPos> searchPossibleTargetPositions() {
        final double max = range.getValue();
        final BlockPos center = BlockPos.ofFloored(mc.player.getEyePos());
        final int radius = (int) Math.ceil(max);
        final List<BlockPos> result = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) for (int y = -radius; y <= radius; y++) for (int z = -radius; z <= radius; z++) {
            final BlockPos pos = center.add(x, y, z);
            final BlockState state = mc.world.getBlockState(pos);
            if (isTargetBlock(state, pos) && distanceToOutline(pos, state) <= max * max) result.add(pos);
        }
        result.sort(Comparator.comparingDouble(pos -> distanceToOutline(pos, mc.world.getBlockState(pos))));
        return result;
    }

    private boolean isTargetBlock(final BlockState state, final BlockPos pos) {
        if (state.isAir()) return false;
        if (beds.getValue() && state.getBlock() instanceof BedBlock) return !isSelfBed((BedBlock) state.getBlock(), pos);
        return dragonEgg.getValue() && state.isOf(Blocks.DRAGON_EGG);
    }

    private boolean isSelfBed(final BedBlock bed, final BlockPos pos) {
        return switch (selfBed.getValue()) {
            case NONE -> false;
            case COLOR -> ownBedColor() != null && ownBedColor().mapColorId == bed.getColor().getMapColor().id;
            case SPAWN_LOCATION -> trackedSpawnLocation != null && trackedSpawnLocation.squaredDistanceTo(pos.toCenterPos()) <= 24.0D * 24.0D;
            case MANUAL -> (trackedManualBed != null && trackedManualBed.getManhattanDistance(pos) <= 1)
                    || new BlockPos(manualBedX.getValue().intValue(), manualBedY.getValue().intValue(), manualBedZ.getValue().intValue()).getManhattanDistance(pos) <= 1;
        };
    }

    private boolean isReachable(final BlockPos pos, final double normalRange, final double throughWallsRange) {
        return findReachablePoint(pos, normalRange, throughWallsRange) != null;
    }

    private Vec3d findReachablePoint(final BlockPos pos, final double normalRange, final double throughWallsRange) {
        final Vec3d eyes = mc.player.getEyePos();
        for (final Vec3d point : FuckerRaycast.sampleOutline(pos, mc.world.getBlockState(pos), chestAsFullBlock.getValue())) {
            final double distance = eyes.squaredDistanceTo(point);
            if (distance > normalRange * normalRange) continue;
            final BlockHitResult hit = FuckerRaycast.trace(eyes, point);
            if (hit != null && hit.getBlockPos().equals(pos)) return point;
            if (distance <= throughWallsRange * throughWallsRange) return point;
        }
        return null;
    }

    private FuckerPath findBestSurroundingPath(final BlockPos target) {
        FuckerPath best = null;
        final Vec3d eyes = mc.player.getEyePos();
        for (final Vec3d point : FuckerRaycast.sampleOutline(target, mc.world.getBlockState(target), chestAsFullBlock.getValue())) {
            if (eyes.squaredDistanceTo(point) > range.getValue() * range.getValue()) continue;
            final List<BlockPos> blockers = traceBlockers(target, eyes, point);
            if (blockers == null || blockers.isEmpty()) continue;
            double resistance = 0.0D;
            for (final BlockPos blocker : blockers) resistance += miningDuration(blocker, mc.world.getBlockState(blocker));
            final BlockPos first = blockers.getFirst();
            final FuckerPathInfo info = new FuckerPathInfo(target, point, resistance, blockers.size(), first.getSquaredDistance(point), first.getSquaredDistance(eyes));
            final FuckerPath candidate = new FuckerPath(first, blockers, info);
            if (best == null || candidate.compareTo(best) < 0) best = candidate;
        }
        return best;
    }

    /** Version adapter for LB's World.raycast(exclude): sample the exact sight line into block cells. */
    private List<BlockPos> traceBlockers(final BlockPos target, final Vec3d start, final Vec3d end) {
        final Vec3d delta = end.subtract(start);
        final int steps = Math.max(1, (int) Math.ceil(delta.length() * 32.0D));
        final List<BlockPos> blockers = new ArrayList<>();
        final Set<BlockPos> visited = new HashSet<>();
        for (int i = 0; i <= steps; i++) {
            final BlockPos pos = BlockPos.ofFloored(start.add(delta.multiply(i / (double) steps)));
            if (pos.equals(target)) return blockers;
            if (!visited.add(pos)) continue;
            final BlockState state = mc.world.getBlockState(pos);
            if (!state.isAir() && !state.getOutlineShape(mc.world, pos).isEmpty()) {
                if (blockers.size() >= MAX_SURROUNDING_PATH_BLOCKS || state.getHardness(mc.world, pos) < 0.0F) return null;
                blockers.add(pos);
            }
        }
        return null;
    }

    private boolean hasEntrance(final BlockPos target) {
        final BlockState own = mc.world.getBlockState(target);
        for (final Direction direction : Direction.values()) {
            if (direction == Direction.DOWN) continue;
            final BlockPos neighbor = target.offset(direction);
            final BlockState state = mc.world.getBlockState(neighbor);
            if (state.getOutlineShape(mc.world, neighbor).isEmpty() && state.getBlock() != own.getBlock()) return true;
        }
        return false;
    }

    private BlockPos weakestNeighbor(final BlockPos target) {
        BlockPos best = null;
        double score = Double.MAX_VALUE;
        final BlockState own = mc.world.getBlockState(target);
        for (final Direction direction : Direction.values()) {
            if (direction == Direction.DOWN) continue;
            final BlockPos neighbor = target.offset(direction);
            final BlockState state = mc.world.getBlockState(neighbor);
            if (state.isAir() || state.getBlock() == own.getBlock() || state.getHardness(mc.world, neighbor) < 0.0F) continue;
            final double candidate = miningDuration(neighbor, state) + distanceToOutline(neighbor, state);
            if (candidate < score) { score = candidate; best = neighbor; }
        }
        return best;
    }

    private double miningDuration(final BlockPos pos, final BlockState state) {
        float speed = 1.0F;
        for (int slot = 0; slot < 9; slot++) speed = Math.max(speed, mc.player.getInventory().getStack(slot).getMiningSpeedMultiplier(state));
        return state.getHardness(mc.world, pos) / speed;
    }

    private double distanceToOutline(final BlockPos pos, final BlockState state) {
        final Box box = state.getOutlineShape(mc.world, pos).getBoundingBoxes().stream().findFirst().orElse(new Box(0, 0, 0, 1, 1, 1)).offset(pos);
        final Vec3d eyes = mc.player.getEyePos();
        final double x = Math.max(box.minX, Math.min(eyes.x, box.maxX));
        final double y = Math.max(box.minY, Math.min(eyes.y, box.maxY));
        final double z = Math.max(box.minZ, Math.min(eyes.z, box.maxZ));
        return eyes.squaredDistanceTo(x, y, z);
    }

    private HypixelServer.BedColor ownBedColor() {
        return LocalDataWatch.get().getKnownServerManager().getCurrentServer() instanceof HypixelServer
                ? HypixelServer.BedColor.fromTeamColor(mc.player.getTeamColorValue()) : null;
    }

    private void selectBestTool(final BlockState state) {
        int best = -1;
        float bestSpeed = 1.0F;
        for (int slot = 0; slot < 9; slot++) {
            final ItemStack stack = mc.player.getInventory().getStack(slot);
            final float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) { bestSpeed = speed; best = slot; }
        }
        if (best >= 0) SlotHelper.setCurrentItem(best).silence(SlotHelper.Silence.NONE);
    }

    private double maxRange() { return Math.max(range.getValue(), wallRange.getValue()); }

    private void clearCurrentTarget() {
        if (mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking();
        currentTarget = null;
    }

    public enum Action { DESTROY("Destroy"), USE("Use"); private final String label; Action(String label) { this.label = label; } @Override public String toString() { return label; } }
    public enum SelfBed { NONE("None"), COLOR("Color"), SPAWN_LOCATION("SpawnLocation"), MANUAL("Manual"); private final String label; SelfBed(String label) { this.label = label; } @Override public String toString() { return label; } }
}

package wtf.oraculus.client.feature.module.impl.world.breaker;

import net.hypixel.data.type.GameType;
import net.minecraft.block.AirBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import wtf.oraculus.client.feature.helper.impl.LocalDataWatch;
import wtf.oraculus.client.feature.helper.impl.player.mouse.MouseHelper;
import wtf.oraculus.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.oraculus.client.feature.helper.impl.player.rotation.model.impl.InstantRotationModel;
import wtf.oraculus.client.feature.helper.impl.player.slot.SlotHelper;
import wtf.oraculus.client.feature.helper.impl.server.impl.HypixelServer;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.impl.visual.overlay.impl.dynamicisland.DynamicIslandElement;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.mode.ModeProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.duck.ClientPlayerInteractionManagerAccess;
import wtf.oraculus.event.impl.game.PostGameTickEvent;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.player.interaction.CancelBlockBreakingEvent;
import wtf.oraculus.event.impl.game.player.interaction.VisualSwingEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.misc.chat.ChatUtility;
import wtf.oraculus.utility.player.PlayerUtility;
import wtf.oraculus.utility.player.RotationUtility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static wtf.oraculus.client.Constants.mc;

public final class BreakerModule extends Module {

    private static final Direction[] SURROUNDING_DIRECTIONS = {
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST
    };

    private final ModeProperty<BreakerMode> mode = new ModeProperty<>("Mode", BreakerMode.NORMAL);
    private final ModeProperty<SwingMode> swingMode = new ModeProperty<>("Swing mode", SwingMode.CLIENT);
    private final NumberProperty range = new NumberProperty("Range", 4.5F, 0.5F, 6F, 0.5F);
    private final BooleanProperty breakSurroundings = new BooleanProperty("Break surroundings", true);

    private static final double[] OUTLINE_SAMPLES = {0.1D, 0.3D, 0.5D, 0.7D, 0.9D};

    private BlockTarget currentTarget;
    private BlockTarget pendingTarget;

    private boolean breaking, breakingBed, cancelVisualSwing;
    private boolean allowingBreakingCancellation;
    private int remainingTicks, slot;
    private long lastBedBreak;
    private BlockPos breakingPos;
    private Direction breakingSide;
    private BypassStage bypassStage = BypassStage.SEARCH_BED;
    private BlockPos bypassBedPos;
    private BlockPos bypassOtherBedPos;
    private BlockPos bypassCoverPos;

    private final BreakerIsland breakerIsland = new BreakerIsland(this);

    public BreakerModule() {
        super("Breaker", "Breaks relevant blocks for mini-games.", ModuleCategory.WORLD);
        addProperties(mode, swingMode, range, breakSurroundings);
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (!shouldRun()) {
            this.resetBreakingState(true);
            return;
        }

        this.updateTargetBlock();

        if (this.currentTarget == null) {
            this.resetBreakingState(false);
            return;
        }

        final BlockState currentTargetState = mc.world.getBlockState(this.currentTarget.candidate.getPos());
        if (currentTargetState.getBlock() instanceof AirBlock) {
            this.resetBreakingState(true);
            return;
        }

        final BlockPos blockPos = this.currentTarget.candidate.pos;
        final Vec3d aimPoint = this.findReachablePoint(blockPos);
        if (aimPoint == null) {
            this.clearLocalBreakingForTargetChange();
            this.currentTarget = null;
            this.pendingTarget = null;
            this.disableIsland();
            return;
        }

        this.currentTarget = this.currentTarget.withAimPoint(aimPoint);
        final Vec2f rotation = RotationUtility.getRotationFromPosition(aimPoint);
        RotationHelper.getHandler().rotate(rotation, InstantRotationModel.INSTANCE);
        if (this.slot != -1) {
            SlotHelper.setCurrentItem(this.slot).silence(SlotHelper.Silence.NONE);
        }
        this.pendingTarget = this.currentTarget;
    }

    @Subscribe
    public void onPostGameTick(final PostGameTickEvent event) {
        final BlockTarget target = this.pendingTarget;
        this.pendingTarget = null;
        if (target == null || mc.player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }

        final BlockPos blockPos = target.candidate().pos;
        final BlockState state = mc.world.getBlockState(blockPos);
        if (state.isAir()) {
            this.resetBreakingState(true);
            return;
        }

        // This is intentionally performed after RotationHelper applied the
        // pre-tick look. It mirrors Fucker's current-rotation raycast gate.
        final BlockHitResult hitResult = this.getRaycastHitResult();
        if (hitResult == null || !hitResult.getBlockPos().equals(blockPos)) {
            this.clearLocalBreakingForTargetChange();
            this.disableIsland();
            return;
        }

        final Direction side = hitResult.getSide();
        if (!this.breaking || !blockPos.equals(this.breakingPos) || side != this.breakingSide) {
            this.clearLocalBreakingForTargetChange();
            if (!mc.interactionManager.attackBlock(blockPos, side)) {
                this.disableIsland();
                return;
            }
            this.breaking = true;
            this.breakingBed = state.getBlock() instanceof BedBlock;
            this.breakingPos = blockPos.toImmutable();
            this.breakingSide = side;
            this.remainingTicks = this.estimateRemainingTicks(state, blockPos);
        }

        if (!mc.interactionManager.updateBlockBreakingProgress(blockPos, side)) {
            this.disableIsland();
            return;
        }

        MouseHelper.getRightButton().setDisabled();
        MouseHelper.getLeftButton().setDisabled();
        this.remainingTicks = Math.max(0, this.remainingTicks - 1);
        this.cancelVisualSwing = this.swingMode.is(SwingMode.SERVER);
        mc.player.swingHand(Hand.MAIN_HAND);
        DynamicIslandElement.addTrigger(breakerIsland);
    }

    public BlockTarget getCurrentTarget() {
        return currentTarget;
    }

    @Subscribe
    public void onCancelBlockBreaking(final CancelBlockBreakingEvent event) {
        if (this.breaking && !this.allowingBreakingCancellation) {
            event.setCancelled();
        }
    }

    @Subscribe
    public void onVisualSwing(final VisualSwingEvent event) {
        if (this.cancelVisualSwing) {
            this.cancelVisualSwing = false;
            event.setCancelled();
        }
    }

    private void updateTargetBlock() {
        if (this.mode.is(BreakerMode.HEYPIXEL_BYPASS)) {
            this.updateHeypixelBypassTarget();
            return;
        }
        this.resetBypassState();
        this.updateNormalTargetBlock();
    }

    private void updateNormalTargetBlock() {
        this.slot = -1;

        final Vec3d eyePos = mc.player.getEyePos();
        final float range = this.range.getValue().floatValue();

        final int fromX = (int) Math.floor(eyePos.x - range - 1);
        final int fromY = (int) Math.floor(eyePos.y - range - 1);
        final int fromZ = (int) Math.floor(eyePos.z - range - 1);

        final int toX = (int) Math.ceil(eyePos.x + range + 1);
        final int toY = (int) Math.ceil(eyePos.y + range + 1);
        final int toZ = (int) Math.ceil(eyePos.z + range + 1);

        final List<BlockCandidate> targetCandidates = new ArrayList<>();

        final HypixelServer.BedColor ownBedColor = LocalDataWatch.get().getKnownServerManager().getCurrentServer() instanceof HypixelServer
                ? HypixelServer.BedColor.fromTeamColor(mc.player.getTeamColorValue())
                : null;

        for (int x = fromX; x <= toX; x++) {
            for (int y = fromY; y <= toY; y++) {
                for (int z = fromZ; z <= toZ; z++) {
                    final BlockPos blockPos = new BlockPos(x, y, z);
                    final BlockState blockState = mc.world.getBlockState(blockPos);

                    // TODO: egg
                    if (!(blockState.getBlock() instanceof BedBlock bedBlock)) {
                        continue;
                    }

                    if (ownBedColor != null && ownBedColor.mapColorId == bedBlock.getColor().getMapColor().id) {
                        continue;
                    }

                    final BlockCandidate candidate = new BlockCandidate(blockPos);
                    targetCandidates.add(candidate);

                    final BlockCandidate otherBedPartCandidate = candidate.offset(BedBlock.getOppositePartDirection(blockState));
                    targetCandidates.add(otherBedPartCandidate);
                }
            }
        }

        final BlockCandidate closestCandidate = targetCandidates.stream()
                .filter(candidate -> candidate.distance <= range)
                .min(Comparator.comparingDouble(candidate -> candidate.distance))
                .orElse(null);

        if (closestCandidate == null) {
            this.currentTarget = null;
            return;
        }
        final BlockTarget closestTarget = this.createReachableTarget(closestCandidate, 0.01D);

        if (!this.breakSurroundings.getValue()) {
            if (closestTarget == null) {
                this.currentTarget = null;
            } else {
                this.setTargetBlock(closestTarget);
            }
            return;
        }

        List<BlockCandidate> adjacentCandidates = Arrays.stream(SURROUNDING_DIRECTIONS)
                .map(closestCandidate::offset)
                .collect(Collectors.toList());

        // add adjacent candidates of the other bed part
        final BlockState bedState = mc.world.getBlockState(closestCandidate.pos);
        if (bedState.getBlock() instanceof BedBlock) {
            final BlockCandidate otherBedPart = closestCandidate.offset(BedBlock.getOppositePartDirection(bedState));

            Arrays.stream(SURROUNDING_DIRECTIONS)
                    .map(otherBedPart::offset)
                    .forEach(adjacentCandidates::add);
        }

        adjacentCandidates = adjacentCandidates.stream()
                .filter(c -> c.distance <= range)
                .sorted(Comparator.comparingDouble(c -> c.distance))
                .toList();

        for (final BlockCandidate adjacentCandidate : adjacentCandidates) {
            final BlockState blockState = mc.world.getBlockState(adjacentCandidate.pos);

            if (blockState.isAir() || !blockState.getFluidState().isEmpty()) {
                if (closestTarget != null) {
                    this.setTargetBlock(closestTarget);
                    return;
                }
                // A bed may be geometrically exposed while the player still
                // cannot see a valid outline point. Continue into the cover
                // search instead of sending a through-block dig request.
                break;
            }
        }

        MiningChoice weakestChoice = null;

        for (final BlockCandidate adjacentCandidate : adjacentCandidates) {
            final BlockState blockState = mc.world.getBlockState(adjacentCandidate.pos);

            if (blockState.getBlock() instanceof BedBlock || blockState.getHardness(mc.world, adjacentCandidate.pos) < 0.0F) {
                continue;
            }

            final Vec3d aimPoint = this.findReachablePoint(adjacentCandidate.pos);
            if (aimPoint == null) {
                continue;
            }

            double fastestMiningSpeed = SlotHelper.getInstance().getMainHandStack(mc.player).getMiningSpeedMultiplier(blockState);
            int bestSlotForCandidate = SlotHelper.getInstance().getSelectedSlot(mc.player.getInventory());

            for (int i = 0; i < 9; i++) {
                if (i == SlotHelper.getInstance().getSelectedSlot(mc.player.getInventory())) {
                    continue;
                }

                float miningSpeed = mc.player.getInventory().getStack(i).getMiningSpeedMultiplier(blockState);
                if (miningSpeed > fastestMiningSpeed) {
                    fastestMiningSpeed = miningSpeed;
                    bestSlotForCandidate = i;
                }
            }

            double resistance = Math.max(0.01, blockState.getHardness(mc.world, adjacentCandidate.pos)) / fastestMiningSpeed;
            if (!breaking) {
                final ClientPlayerInteractionManagerAccess access = (ClientPlayerInteractionManagerAccess) mc.interactionManager;
                final BlockPos currentBreakingPos = access.oraculus$getCurrentBreakingPos();

                if (currentBreakingPos != null && currentBreakingPos.equals(adjacentCandidate.pos)) {
                    resistance *= 1 - access.oraculus$currentBreakingProgress();
                }
            }

            final MiningChoice choice = new MiningChoice(adjacentCandidate, resistance, bestSlotForCandidate, aimPoint);
            if (weakestChoice == null || choice.resistance() < weakestChoice.resistance()) {
                weakestChoice = choice;
            }
        }

        if (weakestChoice == null) {
            return;
        }

        if (System.currentTimeMillis() - this.lastBedBreak < 500) {
            this.currentTarget = null;
            return;
        }

        this.slot = weakestChoice.slot();
        this.setTargetBlock(new BlockTarget(weakestChoice.candidate(), weakestChoice.resistance(), weakestChoice.aimPoint()));
    }

    private void updateHeypixelBypassTarget() {
        this.slot = -1;
        final float configuredRange = this.range.getValue().floatValue();
        BlockPos bedPos = this.resolveLockedBed(configuredRange);

        if (bedPos == null) {
            this.resetBypassState();
            bedPos = this.findClosestEnemyBed(configuredRange);
            if (bedPos == null) {
                this.currentTarget = null;
                return;
            }
            this.lockBed(bedPos);
        }

        if (this.bypassStage == BypassStage.SEARCH_BED) {
            final MiningChoice cover = this.findBestDirectCover(configuredRange);
            if (cover == null) {
                // A direct cover may still exist just outside the configured
                // attack range. Waiting keeps the mode from treating a range
                // failure as an exposed bed.
                if (this.hasMineableDirectCover()) {
                    this.clearLocalBreakingForTargetChange();
                    this.currentTarget = null;
                    return;
                }
                this.bypassStage = BypassStage.LOCK_BED;
            } else {
                this.bypassStage = BypassStage.LOCK_COVER;
                this.bypassCoverPos = cover.candidate().pos.toImmutable();
                this.slot = cover.slot();
                this.setBypassTarget(new BlockTarget(cover.candidate(), cover.resistance(), cover.aimPoint()));
                return;
            }
        }

        if (this.bypassStage == BypassStage.LOCK_COVER) {
            final BlockState lockedCoverState = mc.world.getBlockState(this.bypassCoverPos);
            final MiningChoice cover = this.miningChoice(this.bypassCoverPos, configuredRange);
            if (cover != null) {
                this.slot = cover.slot();
                this.setBypassTarget(new BlockTarget(cover.candidate(), cover.resistance(), cover.aimPoint()));
                return;
            }

            if (!this.isRemovedCover(lockedCoverState)) {
                // The block still exists but is currently out of range or no
                // longer mineable. Preserve the lock instead of advancing.
                this.clearLocalBreakingForTargetChange();
                this.currentTarget = null;
                return;
            }

            // A zero local progress estimate is not enough. Reaching this
            // branch means the client world has confirmed that the locked
            // adjacent block is no longer a real mineable block.
            this.bypassCoverPos = null;
            this.bypassStage = BypassStage.LOCK_BED;
            this.clearLocalBreakingForTargetChange();
            this.currentTarget = null;
        }

        bedPos = this.resolveLockedBed(configuredRange);
        if (bedPos == null) {
            this.currentTarget = null;
            this.resetBypassState();
            return;
        }
        final BlockTarget bedTarget = this.createReachableTarget(new BlockCandidate(bedPos), 0.01D);
        if (bedTarget == null) {
            this.clearLocalBreakingForTargetChange();
            this.currentTarget = null;
            return;
        }
        this.setBypassTarget(bedTarget);
    }

    private BlockPos findClosestEnemyBed(final float configuredRange) {
        final Vec3d eyePos = mc.player.getEyePos();
        final int fromX = (int) Math.floor(eyePos.x - configuredRange - 1);
        final int fromY = (int) Math.floor(eyePos.y - configuredRange - 1);
        final int fromZ = (int) Math.floor(eyePos.z - configuredRange - 1);
        final int toX = (int) Math.ceil(eyePos.x + configuredRange + 1);
        final int toY = (int) Math.ceil(eyePos.y + configuredRange + 1);
        final int toZ = (int) Math.ceil(eyePos.z + configuredRange + 1);
        final HypixelServer.BedColor ownBedColor = this.ownBedColor();

        BlockCandidate closest = null;
        for (int x = fromX; x <= toX; x++) {
            for (int y = fromY; y <= toY; y++) {
                for (int z = fromZ; z <= toZ; z++) {
                    final BlockPos pos = new BlockPos(x, y, z);
                    final BlockState state = mc.world.getBlockState(pos);
                    if (!this.isEnemyBed(state, ownBedColor)) {
                        continue;
                    }
                    final BlockCandidate candidate = new BlockCandidate(pos);
                    if (candidate.distance <= configuredRange
                            && (closest == null || candidate.distance < closest.distance)) {
                        closest = candidate;
                    }
                }
            }
        }
        return closest == null ? null : closest.pos.toImmutable();
    }

    private void lockBed(final BlockPos bedPos) {
        final BlockState state = mc.world.getBlockState(bedPos);
        this.bypassBedPos = bedPos.toImmutable();
        this.bypassOtherBedPos = state.getBlock() instanceof BedBlock
                ? bedPos.offset(BedBlock.getOppositePartDirection(state)).toImmutable()
                : null;
        this.bypassCoverPos = null;
        this.bypassStage = BypassStage.SEARCH_BED;
    }

    private BlockPos resolveLockedBed(final float configuredRange) {
        final HypixelServer.BedColor ownBedColor = this.ownBedColor();
        for (final BlockPos pos : new BlockPos[]{this.bypassBedPos, this.bypassOtherBedPos}) {
            if (pos == null || !this.isEnemyBed(mc.world.getBlockState(pos), ownBedColor)) {
                continue;
            }
            final double distance = PlayerUtility.getDistanceToBlock(pos);
            if (distance <= configuredRange) {
                if (!pos.equals(this.bypassBedPos)) {
                    this.bypassBedPos = pos.toImmutable();
                }
                return pos;
            }
        }
        return null;
    }

    private MiningChoice findBestDirectCover(final float configuredRange) {
        MiningChoice best = null;
        for (final BlockPos pos : this.directCoverPositions()) {
            final MiningChoice choice = this.miningChoice(pos, configuredRange);
            if (choice == null) {
                continue;
            }
            if (best == null
                    || choice.resistance() < best.resistance()
                    || (choice.resistance() == best.resistance()
                    && choice.candidate().distance < best.candidate().distance)) {
                best = choice;
            }
        }
        return best;
    }

    private boolean hasMineableDirectCover() {
        for (final BlockPos pos : this.directCoverPositions()) {
            final BlockState state = mc.world.getBlockState(pos);
            if (this.isMineableCover(state, pos)) {
                return true;
            }
        }
        return false;
    }

    private Set<BlockPos> directCoverPositions() {
        final Set<BlockPos> positions = new LinkedHashSet<>();
        for (final BlockPos bedPart : new BlockPos[]{this.bypassBedPos, this.bypassOtherBedPos}) {
            if (bedPart == null || !(mc.world.getBlockState(bedPart).getBlock() instanceof BedBlock)) {
                continue;
            }
            for (final Direction direction : SURROUNDING_DIRECTIONS) {
                positions.add(bedPart.offset(direction).toImmutable());
            }
        }
        return positions;
    }

    private MiningChoice miningChoice(final BlockPos pos, final float configuredRange) {
        if (pos == null) {
            return null;
        }
        final BlockState state = mc.world.getBlockState(pos);
        if (!this.isMineableCover(state, pos)) {
            return null;
        }

        final BlockCandidate candidate = new BlockCandidate(pos);
        if (candidate.distance > configuredRange) {
            return null;
        }
        final Vec3d aimPoint = this.findReachablePoint(pos);
        if (aimPoint == null) {
            return null;
        }

        final float hardness = state.getHardness(mc.world, pos);
        final int selectedSlot = SlotHelper.getInstance().getSelectedSlot(mc.player.getInventory());
        int bestSlot = selectedSlot;
        double bestSpeed = SlotHelper.getInstance().getMainHandStack(mc.player)
                .getMiningSpeedMultiplier(state);
        for (int candidateSlot = 0; candidateSlot < 9; candidateSlot++) {
            final double speed = mc.player.getInventory().getStack(candidateSlot)
                    .getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = candidateSlot;
            }
        }
        final double resistance = Math.max(0.01D, hardness) / Math.max(0.01D, bestSpeed);
        return new MiningChoice(candidate, resistance, bestSlot, aimPoint);
    }

    private BlockTarget createReachableTarget(final BlockCandidate candidate, final double resistance) {
        final Vec3d aimPoint = this.findReachablePoint(candidate.pos);
        return aimPoint == null ? null : new BlockTarget(candidate, resistance, aimPoint);
    }

    private boolean isMineableCover(final BlockState state, final BlockPos pos) {
        return !this.isRemovedCover(state)
                && !(state.getBlock() instanceof BedBlock)
                && state.getHardness(mc.world, pos) >= 0.0F;
    }

    private boolean isRemovedCover(final BlockState state) {
        return state.isAir() || state.isReplaceable() || !state.getFluidState().isEmpty();
    }

    private HypixelServer.BedColor ownBedColor() {
        return LocalDataWatch.get().getKnownServerManager().getCurrentServer() instanceof HypixelServer
                ? HypixelServer.BedColor.fromTeamColor(mc.player.getTeamColorValue())
                : null;
    }

    private boolean isEnemyBed(
            final BlockState state,
            final HypixelServer.BedColor ownBedColor
    ) {
        if (!(state.getBlock() instanceof BedBlock bedBlock)) {
            return false;
        }
        return ownBedColor == null
                || ownBedColor.mapColorId != bedBlock.getColor().getMapColor().id;
    }

    private void setBypassTarget(final BlockTarget target) {
        final boolean changed = this.currentTarget == null
                || !this.currentTarget.candidate.pos.equals(target.candidate.pos);
        if (changed) {
            this.clearLocalBreakingForTargetChange();
        }
        this.currentTarget = target;
    }

    private void setTargetBlock(final BlockTarget newTarget) {
        if (this.shouldUpdateTarget(newTarget)) {
            if (this.currentTarget != null
                    && !this.currentTarget.candidate.pos.equals(newTarget.candidate.pos)) {
                this.clearLocalBreakingForTargetChange();
            }
            this.currentTarget = newTarget;
        }
    }

    private boolean shouldUpdateTarget(final BlockTarget newTarget) {
        if (this.currentTarget == null) {
            return true;
        }

        final BlockState currentBlockState = mc.world.getBlockState(this.currentTarget.candidate.pos);
        if (currentBlockState.isAir() || !currentBlockState.getFluidState().isEmpty()) {
            return true;
        }

        // bed no longer exposed, update target to surrounding block
        if (this.breakSurroundings.getValue()
                && currentBlockState.getBlock() instanceof BedBlock
                && !(mc.world.getBlockState(newTarget.candidate.pos).getBlock() instanceof BedBlock)) {
            return true;
        }

        this.currentTarget.candidate.updateDistance();

        if (this.currentTarget.candidate.distance > this.range.getValue().floatValue()) {
            return true;
        }

        final float breakingProgress = ((ClientPlayerInteractionManagerAccess) mc.interactionManager).oraculus$currentBreakingProgress();
        final double remainingResistance = this.currentTarget.resistance * (1 - breakingProgress);
        if (remainingResistance < newTarget.resistance) {
            return false;
        }

        return true;
    }

    private BlockHitResult getRaycastHitResult() {
        final Vec2f rotation = RotationUtility.getRotation();
        final Vec3d eyes = mc.player.getEyePos();
        final Vec3d end = eyes.add(RotationUtility.getRotationVector(rotation.y, rotation.x)
                .multiply(this.range.getValue()));
        final HitResult hitResult = mc.world.raycast(new RaycastContext(
                eyes,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));
        if (!(hitResult instanceof BlockHitResult blockHitResult)) {
            return null;
        }

        return blockHitResult;
    }

    /**
     * Kept equivalent to Fucker's outline sampling: Breaker only chooses a
     * point that the normal block raycast can hit at the configured range.
     */
    private Vec3d findReachablePoint(final BlockPos pos) {
        final Vec3d eyes = mc.player.getEyePos();
        final double maximumDistanceSquared = this.range.getValue() * this.range.getValue();
        final BlockState state = mc.world.getBlockState(pos);
        for (final Box localBox : state.getOutlineShape(mc.world, pos).getBoundingBoxes()) {
            final Box box = localBox.offset(pos);
            for (final double a : OUTLINE_SAMPLES) {
                for (final double b : OUTLINE_SAMPLES) {
                    final Vec3d[] points = {
                            new Vec3d(box.minX + (box.maxX - box.minX) * a, box.minY + (box.maxY - box.minY) * b, box.minZ),
                            new Vec3d(box.minX + (box.maxX - box.minX) * a, box.minY + (box.maxY - box.minY) * b, box.maxZ),
                            new Vec3d(box.minX, box.minY + (box.maxY - box.minY) * a, box.minZ + (box.maxZ - box.minZ) * b),
                            new Vec3d(box.maxX, box.minY + (box.maxY - box.minY) * a, box.minZ + (box.maxZ - box.minZ) * b),
                            new Vec3d(box.minX + (box.maxX - box.minX) * a, box.minY, box.minZ + (box.maxZ - box.minZ) * b),
                            new Vec3d(box.minX + (box.maxX - box.minX) * a, box.maxY, box.minZ + (box.maxZ - box.minZ) * b)
                    };
                    for (final Vec3d point : points) {
                        if (eyes.squaredDistanceTo(point) > maximumDistanceSquared) {
                            continue;
                        }
                        final HitResult hit = mc.world.raycast(new RaycastContext(
                                eyes,
                                point,
                                RaycastContext.ShapeType.OUTLINE,
                                RaycastContext.FluidHandling.NONE,
                                mc.player
                        ));
                        if (hit instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(pos)) {
                            return point;
                        }
                    }
                }
            }
        }
        return null;
    }

    private int estimateRemainingTicks(final BlockState state, final BlockPos pos) {
        final float delta = state.calcBlockBreakingDelta(mc.player, mc.world, pos);
        return delta <= 0.0F ? 0 : Math.max(1, (int) Math.ceil(1.0F / delta));
    }

    private boolean shouldRun() {
        if (mc.player == null) {
            return false;
        }

        if (LocalDataWatch.get().getKnownServerManager().getCurrentServer() instanceof HypixelServer) {
            final HypixelServer.ModAPI.Location currentLocation = HypixelServer.ModAPI.get().getCurrentLocation();
            if (currentLocation != null && (currentLocation.isLobby() || currentLocation.serverType() == GameType.REPLAY || "BEDWARS_PRACTICE".equals(currentLocation.mode()))) {
                return false;
            }
        }

        return true;
    }

    private void resetBreakingState(final boolean clearTarget) {
        if (this.breakingBed) {
            this.lastBedBreak = System.currentTimeMillis();
        }

        this.clearLocalBreakingForTargetChange();

        if (clearTarget) {
            this.currentTarget = null;
            this.resetBypassState();
        }

        this.disableIsland();
    }

    private void clearLocalBreakingForTargetChange() {
        if (this.breaking && mc.interactionManager != null) {
            this.allowingBreakingCancellation = true;
            try {
                mc.interactionManager.cancelBlockBreaking();
            } finally {
                this.allowingBreakingCancellation = false;
            }
        }
        this.breaking = false;
        this.breakingBed = false;
        this.remainingTicks = 0;
        this.breakingPos = null;
        this.breakingSide = null;
        this.pendingTarget = null;
    }

    private void resetBypassState() {
        this.bypassStage = BypassStage.SEARCH_BED;
        this.bypassBedPos = null;
        this.bypassOtherBedPos = null;
        this.bypassCoverPos = null;
    }

    private void disableIsland() {
        DynamicIslandElement.removeTrigger(breakerIsland);
        breakerIsland.onDisable();
    }

    @Override
    protected void onDisable() {
        this.resetBreakingState(true);
        super.onDisable();
    }

    public boolean isBreaking() {
        return breaking;
    }

    public int getSlot() {
        return slot;
    }

    public static class BlockCandidate {
        private final BlockPos pos;
        private double distance;

        private BlockCandidate(final BlockPos pos) {
            this.pos = pos;
            this.updateDistance();
        }

        private BlockCandidate offset(final Direction direction) {
            return new BlockCandidate(pos.offset(direction));
        }

        private void updateDistance() {
            this.distance = PlayerUtility.getDistanceToBlock(pos);
        }

        public BlockPos getPos() {
            return pos;
        }
    }

    public record BlockTarget(BlockCandidate candidate, double resistance, Vec3d aimPoint) {
        private BlockTarget withAimPoint(final Vec3d point) {
            return new BlockTarget(candidate, resistance, point);
        }
    }

    private record MiningChoice(BlockCandidate candidate, double resistance, int slot, Vec3d aimPoint) {
    }

    private enum BypassStage {
        SEARCH_BED,
        LOCK_COVER,
        LOCK_BED
    }

    private enum BreakerMode {
        NORMAL("Normal"),
        HEYPIXEL_BYPASS("Heypixel Bypass");

        private final String name;

        BreakerMode(final String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

    private enum SwingMode {
        CLIENT("Client"),
        SERVER("Server");

        private final String name;

        SwingMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

}

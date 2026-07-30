package wtf.oraculus.client.feature.module.impl.world.breaker;

import net.hypixel.data.type.GameType;
import net.minecraft.block.AirBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
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
import wtf.oraculus.utility.player.RaycastUtility;
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

    private BlockTarget currentTarget;
    private Vec2f rotation;

    private boolean breaking, breakingBed, cancelVisualSwing;
    private int remainingTicks, slot;
    private long lastBedBreak;
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
        boolean runIsland = false;

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
        final ClientPlayerInteractionManagerAccess access = (ClientPlayerInteractionManagerAccess) mc.interactionManager;

        final float breakingDelta = mc.world.getBlockState(blockPos).calcBlockBreakingDelta(mc.player, mc.world, blockPos);
        final float breakingProgress = access.oraculus$currentBreakingProgress() + breakingDelta;

        this.rotation = RotationUtility.getRotationFromPosition(blockPos.toCenterPos());

        final double value = breakingProgress + breakingDelta;
        if ((value >= 1 || breakingProgress - breakingDelta == 0) && value < Double.MAX_VALUE) {
            RotationHelper.getHandler().rotate(this.rotation, InstantRotationModel.INSTANCE);

            if (this.slot != -1)
                SlotHelper.setCurrentItem(this.slot).silence(SlotHelper.Silence.NONE);
        }

        final BlockHitResult hitResult = this.getRaycastHitResult();
        if (hitResult == null) {
            this.disableIsland();
            return;
        }

        final Direction direction = hitResult.getSide();

        if (!this.breaking) {
            final boolean success = mc.interactionManager.attackBlock(blockPos, direction);
            if (!success) {
                this.disableIsland();
                return;
            }

            this.remainingTicks = (int) (mc.world.getBlockState(blockPos).getHardness(mc.world, blockPos) * 20);
            this.breaking = true;
            this.breakingBed = currentTargetState.getBlock() instanceof BedBlock;
        }

        if (mc.interactionManager.updateBlockBreakingProgress(blockPos, direction)) {
            MouseHelper.getRightButton().setDisabled();
            MouseHelper.getLeftButton().setDisabled();
// TODO: add particles
//            mc.particleManager.addBlockBreakingParticles(blockPos, direction);
            this.remainingTicks--;

            this.cancelVisualSwing = this.swingMode.is(SwingMode.SERVER);
            mc.player.swingHand(Hand.MAIN_HAND);

            runIsland = true;
        }

        if (runIsland) {
            DynamicIslandElement.addTrigger(breakerIsland);
        } else {
            this.disableIsland();
        }
    }

    @Subscribe
    public void onPostGameTick(final PostGameTickEvent event) {
        if (!this.breaking || mc.player == null) {
            return;
        }

        this.cancelVisualSwing = this.swingMode.is(SwingMode.SERVER);
        mc.player.swingHand(Hand.MAIN_HAND);

        if (this.remainingTicks < 0) {
            if (this.mode.is(BreakerMode.HEYPIXEL_BYPASS)) {
                // The estimate is UI-only. The bypass may switch from the
                // cover to the bed only after the world confirms removal.
                this.remainingTicks = 0;
                return;
            }
            if (this.breakingBed) {
                this.lastBedBreak = System.currentTimeMillis();
            }

            this.resetBreakingState(true);
        }
    }

    public BlockTarget getCurrentTarget() {
        return currentTarget;
    }

    @Subscribe
    public void onCancelBlockBreaking(final CancelBlockBreakingEvent event) {
        if (this.breaking) {
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
                .filter(c -> c.distance <= range)
                .min(Comparator.comparingDouble(c -> c.distance))
                .orElse(null);

        if (closestCandidate == null) {
            this.currentTarget = null;
            return;
        }

        if (!this.breakSurroundings.getValue()) {
            this.setTargetBlock(new BlockTarget(closestCandidate, 0.01));
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
                this.setTargetBlock(new BlockTarget(closestCandidate, 0.01));
                return;
            }
        }

        BlockCandidate weakestCandidate = null;
        double weakestCandidateResistance = Float.MAX_VALUE;
        int bestSlot = -1;

        for (final BlockCandidate adjacentCandidate : adjacentCandidates) {
            final BlockState blockState = mc.world.getBlockState(adjacentCandidate.pos);

            if (blockState.getBlock() instanceof BedBlock) {
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

            if (weakestCandidate == null || resistance < weakestCandidateResistance) {
                weakestCandidate = adjacentCandidate;
                weakestCandidateResistance = resistance;
                bestSlot = bestSlotForCandidate;
            }
        }

        if (weakestCandidate == null) {
            return;
        }

        if (System.currentTimeMillis() - this.lastBedBreak < 500) {
            this.currentTarget = null;
            return;
        }

        this.slot = bestSlot;
        this.setTargetBlock(new BlockTarget(weakestCandidate, weakestCandidateResistance));
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
                this.setBypassTarget(new BlockTarget(cover.candidate(), cover.resistance()));
                return;
            }
        }

        if (this.bypassStage == BypassStage.LOCK_COVER) {
            final BlockState lockedCoverState = mc.world.getBlockState(this.bypassCoverPos);
            final MiningChoice cover = this.miningChoice(this.bypassCoverPos, configuredRange);
            if (cover != null) {
                this.slot = cover.slot();
                this.setBypassTarget(new BlockTarget(cover.candidate(), cover.resistance()));
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
        this.setBypassTarget(new BlockTarget(new BlockCandidate(bedPos), 0.01D));
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
        return new MiningChoice(candidate, resistance, bestSlot);
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
        if (this.rotation == null) {
            return null;
        }

        final HitResult hitResult = RaycastUtility.raycastBlock(this.range.getValue(), 1, false, this.rotation.x, this.rotation.y);
        if (!(hitResult instanceof BlockHitResult blockHitResult)) {
            return null;
        }

        return blockHitResult;
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
        this.breaking = false;
        this.breakingBed = false;
        this.remainingTicks = 0;
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

    public record BlockTarget(BlockCandidate candidate, double resistance) {
    }

    private record MiningChoice(BlockCandidate candidate, double resistance, int slot) {
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

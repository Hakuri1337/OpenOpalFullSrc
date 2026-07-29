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
import java.util.List;
import java.util.stream.Collectors;

import static wtf.oraculus.client.Constants.mc;

public final class BreakerModule extends Module {

    private static final Direction[] SURROUNDING_DIRECTIONS = {
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST
    };

    private final ModeProperty<SwingMode> swingMode = new ModeProperty<>("Swing mode", SwingMode.CLIENT);
    private final NumberProperty range = new NumberProperty("Range", 4.5F, 0.5F, 6F, 0.5F);
    private final BooleanProperty breakSurroundings = new BooleanProperty("Break surroundings", true);

    private BlockTarget currentTarget;
    private Vec2f rotation;

    private boolean breaking, breakingBed, cancelVisualSwing;
    private int remainingTicks, slot;
    private long lastBedBreak;

    private final BreakerIsland breakerIsland = new BreakerIsland(this);

    public BreakerModule() {
        super("Breaker", "Breaks relevant blocks for mini-games.", ModuleCategory.WORLD);
        addProperties(swingMode, range, breakSurroundings);
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

    private void setTargetBlock(final BlockTarget newTarget) {
        if (this.shouldUpdateTarget(newTarget)) {
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

        this.breaking = false;
        this.breakingBed = false;

        if (clearTarget) {
            this.currentTarget = null;
        }

        this.disableIsland();
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

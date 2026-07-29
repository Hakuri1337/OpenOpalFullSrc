package wtf.oraculus.client.feature.module.impl.world.legittelly;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import wtf.oraculus.mixin.ClientPlayerInteractionManagerAccessor;
import wtf.oraculus.utility.player.RotationUtility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static wtf.oraculus.client.Constants.mc;

final class LegitTellyPlacementEngine {
    private static final Direction[] SUPPORT_DIRECTIONS = {
            Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };
    private static final double[] HIT_OFFSETS = {
            0.50D, 0.25D, 0.75D, 0.15D, 0.85D, 0.35D, 0.65D, 0.05D, 0.95D
    };
    private static final int REJECT_TICKS = 4;
    private static final int CONFIRM_TICKS = 3;

    private final Map<BlockPos, Integer> rejected = new HashMap<>();

    private Direction travel;
    private int bridgeLaneBlock;
    private double startProgress;
    private Vec3d lastServerPosition;
    private BlockPos pendingPlace;
    private int pendingTicks;
    private int originalSlot = -1;
    private int lastAttemptAge = Integer.MIN_VALUE;
    private Direction lastClickedFace;
    private boolean ownPlacementPacket;
    private boolean autoSwap;

    void begin(final LegitTellyActivation.ActivationSnapshot activation, final boolean autoSwap) {
        this.travel = activation.travel();
        this.bridgeLaneBlock = activation.bridgeLaneBlock();
        this.startProgress = activation.startProgress();
        this.autoSwap = autoSwap;
        this.lastServerPosition = mc.player.getEntityPos();
        this.pendingPlace = null;
        this.pendingTicks = 0;
        this.originalSlot = mc.player.getInventory().getSelectedSlot();
        this.lastAttemptAge = Integer.MIN_VALUE;
        this.lastClickedFace = null;
        this.rejected.clear();
    }

    void tickConfirmation() {
        this.rejected.replaceAll((pos, ticks) -> ticks - 1);
        this.rejected.entrySet().removeIf(entry -> entry.getValue() <= 0);
        if (this.pendingPlace == null || mc.world == null) {
            return;
        }
        if (!LegitTellyBlockPolicy.isReplaceable(this.pendingPlace)) {
            this.pendingPlace = null;
            this.pendingTicks = 0;
            return;
        }
        if (++this.pendingTicks > CONFIRM_TICKS) {
            this.rejected.put(this.pendingPlace, REJECT_TICKS);
            this.pendingPlace = null;
            this.pendingTicks = 0;
        }
    }

    LegitTellyTarget prepare(final Vec2f desired, final boolean adaptiveYaw) {
        if (mc.player == null || mc.world == null || this.travel == null
                || !this.ensureBlockSlot()) {
            return null;
        }
        final List<BlockPos> placePositions = this.collectPlacePositions();
        LegitTellyTarget best = null;
        for (final BlockPos placePos : placePositions) {
            final LegitTellyTarget candidate = this.findTarget(placePos, desired, adaptiveYaw);
            if (candidate != null && (best == null || candidate.score() < best.score())) {
                best = candidate;
            }
        }
        return best;
    }

    boolean place(final LegitTellyTarget target, final Vec2f appliedRotation) {
        if (target == null || mc.player == null || mc.world == null || mc.interactionManager == null
                || mc.player.age == this.lastAttemptAge || !this.ensureBlockSlot()
                || !this.isAllowedTarget(target.placePos())) {
            return false;
        }
        this.lastAttemptAge = mc.player.age;

        final BlockHitResult verified = this.raycast(appliedRotation);
        if (!matches(verified, target) || !this.noPlayerCollision(target.placePos())) {
            return false;
        }

        this.ownPlacementPacket = true;
        try {
            final ActionResult result = mc.interactionManager.interactBlock(
                    mc.player, Hand.MAIN_HAND, verified
            );
            if (!result.isAccepted()) {
                this.rejected.put(target.placePos(), REJECT_TICKS);
                return false;
            }
            mc.player.swingHand(Hand.MAIN_HAND);
            this.pendingPlace = target.placePos().toImmutable();
            this.pendingTicks = 0;
            this.lastClickedFace = target.hit().getSide();
            return true;
        } finally {
            this.ownPlacementPacket = false;
        }
    }

    boolean isOwnPlacementPacket() {
        return this.ownPlacementPacket;
    }

    void updateServerPosition(final double x, final double y, final double z) {
        this.lastServerPosition = new Vec3d(x, y, z);
    }

    int countBlocks() {
        if (mc.player == null) {
            return 0;
        }
        int count = 0;
        for (int slot = 0; slot < 9; slot++) {
            final ItemStack stack = mc.player.getInventory().getStack(slot);
            if (LegitTellyBlockPolicy.isPlaceable(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    boolean ensureReadyStack() {
        return mc.player != null && mc.interactionManager != null && this.ensureBlockSlot();
    }

    void restoreSlot() {
        if (this.originalSlot >= 0 && this.originalSlot < 9
                && mc.player != null && mc.interactionManager != null) {
            mc.player.getInventory().setSelectedSlot(this.originalSlot);
            ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).callSyncSelectedSlot();
        }
        this.originalSlot = -1;
        this.travel = null;
        this.pendingPlace = null;
        this.rejected.clear();
    }

    private List<BlockPos> collectPlacePositions() {
        final Set<BlockPos> result = new LinkedHashSet<>();
        this.addBelow(result, mc.player.getEntityPos());

        final Vec3d velocity = mc.player.getVelocity();
        this.addBelow(result, mc.player.getEntityPos().add(
                velocity.x * 1.75D, 0.0D, velocity.z * 1.75D
        ));
        this.addBelow(result, new Vec3d(mc.player.lastX, mc.player.getY(), mc.player.lastZ));
        if (this.lastServerPosition != null) {
            this.addBelow(result, this.lastServerPosition);
        }

        final BlockPos current = BlockPos.ofFloored(
                mc.player.getX(), mc.player.getY() - 0.5D, mc.player.getZ()
        );
        result.add(current.offset(this.travel));
        return new ArrayList<>(result);
    }

    private void addBelow(final Set<BlockPos> positions, final Vec3d position) {
        positions.add(BlockPos.ofFloored(position.x, mc.player.getY() - 0.5D, position.z));
    }

    private LegitTellyTarget findTarget(
            final BlockPos placePos,
            final Vec2f desired,
            final boolean adaptiveYaw
    ) {
        if (!this.isAllowedTarget(placePos) || !this.noPlayerCollision(placePos)) {
            return null;
        }

        LegitTellyTarget best = directCursorTarget(placePos, desired);
        for (final Direction source : SUPPORT_DIRECTIONS) {
            final BlockPos support = placePos.offset(source);
            final Direction clickedFace = source.getOpposite();
            if (!LegitTellyBlockPolicy.isSafeSupport(support)) {
                continue;
            }
            if (this.lastClickedFace != null && clickedFace != this.lastClickedFace
                    && clickedFace.getAxis() != Direction.Axis.Y
                    && lipDistance(placePos) > 0.24D) {
                continue;
            }
            for (final double first : HIT_OFFSETS) {
                for (final double second : HIT_OFFSETS) {
                    final Vec3d hitPoint = facePoint(support, clickedFace, first, second);
                    final Vec2f exact = RotationUtility.getRotationFromPosition(
                            mc.player.getEyePos(), hitPoint
                    );
                    final float candidateYaw = adaptiveYaw
                            ? exact.x
                            : desired.x;
                    final Vec2f candidateRotation = RotationUtility.patchConstantRotation(
                            new Vec2f(candidateYaw, exact.y),
                            new Vec2f(mc.player.getYaw(), mc.player.getPitch())
                    );
                    final BlockHitResult ray = this.raycast(candidateRotation);
                    if (!matches(ray, placePos, support, clickedFace)) {
                        continue;
                    }
                    final double centerPenalty = Math.abs(first - 0.5D) + Math.abs(second - 0.5D);
                    final double facePenalty = this.lastClickedFace != null
                            && this.lastClickedFace != clickedFace ? 2.0D : 0.0D;
                    final double score = MathHelper.angleBetween(candidateRotation.x, desired.x)
                            + Math.abs(candidateRotation.y - desired.y)
                            + centerPenalty * 0.35D + facePenalty;
                    final LegitTellyTarget candidate = new LegitTellyTarget(
                            placePos.toImmutable(), support.toImmutable(), ray,
                            candidateRotation, score
                    );
                    if (best == null || candidate.score() < best.score()) {
                        best = candidate;
                    }
                }
            }
        }
        return best;
    }

    private LegitTellyTarget directCursorTarget(final BlockPos expectedPlace, final Vec2f desired) {
        final BlockHitResult ray = this.raycast(desired);
        if (ray == null || ray.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        final BlockPos place = ray.getBlockPos().offset(ray.getSide());
        if (!place.equals(expectedPlace)
                || !LegitTellyBlockPolicy.isSafeSupport(ray.getBlockPos())) {
            return null;
        }
        return new LegitTellyTarget(
                place.toImmutable(), ray.getBlockPos().toImmutable(), ray,
                desired, 0.0D
        );
    }

    private boolean isAllowedTarget(final BlockPos pos) {
        if (pos == null || !LegitTellyBlockPolicy.isReplaceable(pos)
                || this.rejected.containsKey(pos)
                || (this.pendingPlace != null && this.pendingPlace.equals(pos))) {
            return false;
        }
        final boolean alongX = this.travel.getAxis() == Direction.Axis.X;
        final int targetLane = alongX ? pos.getZ() : pos.getX();
        if (targetLane != this.bridgeLaneBlock) {
            return false;
        }
        final double progress = LegitTellyActivation.progress(pos, this.travel);
        final double playerProgress = mc.player.getX() * this.travel.getOffsetX()
                + mc.player.getZ() * this.travel.getOffsetZ();
        return progress >= this.startProgress - 0.2D
                && progress <= playerProgress + 2.1D;
    }

    private boolean ensureBlockSlot() {
        final int selected = mc.player.getInventory().getSelectedSlot();
        final ItemStack held = mc.player.getInventory().getStack(selected);
        if (LegitTellyBlockPolicy.isPlaceable(held) && (held.getCount() > 5 || !this.autoSwap)) {
            return true;
        }
        if (!this.autoSwap) {
            return false;
        }

        int bestSlot = LegitTellyBlockPolicy.isPlaceable(held) ? selected : -1;
        int bestCount = bestSlot == -1 ? 0 : held.getCount();
        for (int slot = 0; slot < 9; slot++) {
            final ItemStack stack = mc.player.getInventory().getStack(slot);
            if (LegitTellyBlockPolicy.isPlaceable(stack) && stack.getCount() > bestCount) {
                bestSlot = slot;
                bestCount = stack.getCount();
            }
        }
        if (bestSlot == -1) {
            return false;
        }
        if (bestSlot != selected) {
            mc.player.getInventory().setSelectedSlot(bestSlot);
            ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).callSyncSelectedSlot();
        }
        return true;
    }

    private BlockHitResult raycast(final Vec2f rotation) {
        if (rotation == null || mc.player == null || mc.world == null) {
            return null;
        }
        final Vec3d eye = mc.player.getEyePos();
        final Vec3d end = eye.add(RotationUtility.getRotationVector(
                rotation.y, rotation.x
        ).multiply(mc.player.getBlockInteractionRange()));
        return mc.world.raycast(new RaycastContext(
                eye, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, mc.player
        ));
    }

    private boolean noPlayerCollision(final BlockPos pos) {
        final Box block = new Box(pos);
        if (mc.player.getBoundingBox().intersects(block)) {
            return false;
        }
        final double dx = mc.player.getX() - mc.player.lastX;
        final double dz = mc.player.getZ() - mc.player.lastZ;
        if (mc.player.getBoundingBox().offset(-dx, 0.0D, -dz).intersects(block)) {
            return false;
        }
        if (this.lastServerPosition != null) {
            final double serverDx = this.lastServerPosition.x - mc.player.getX();
            final double serverDy = this.lastServerPosition.y - mc.player.getY();
            final double serverDz = this.lastServerPosition.z - mc.player.getZ();
            return !mc.player.getBoundingBox().offset(serverDx, serverDy, serverDz).intersects(block);
        }
        return true;
    }

    private double lipDistance(final BlockPos placePos) {
        return switch (this.travel) {
            case NORTH -> Math.abs(mc.player.getZ() - (placePos.getZ() + 1.0D));
            case SOUTH -> Math.abs(placePos.getZ() - mc.player.getZ());
            case WEST -> Math.abs(mc.player.getX() - (placePos.getX() + 1.0D));
            case EAST -> Math.abs(placePos.getX() - mc.player.getX());
            default -> 1.0D;
        };
    }

    private static Vec3d facePoint(
            final BlockPos support,
            final Direction face,
            final double first,
            final double second
    ) {
        double x = support.getX() + 0.5D;
        double y = support.getY() + 0.5D;
        double z = support.getZ() + 0.5D;
        switch (face) {
            case UP -> {
                x = support.getX() + first;
                y = support.getY() + 0.999D;
                z = support.getZ() + second;
            }
            case DOWN -> {
                x = support.getX() + first;
                y = support.getY() + 0.001D;
                z = support.getZ() + second;
            }
            case NORTH -> {
                x = support.getX() + first;
                y = support.getY() + second;
                z = support.getZ() + 0.001D;
            }
            case SOUTH -> {
                x = support.getX() + first;
                y = support.getY() + second;
                z = support.getZ() + 0.999D;
            }
            case WEST -> {
                x = support.getX() + 0.001D;
                y = support.getY() + second;
                z = support.getZ() + first;
            }
            case EAST -> {
                x = support.getX() + 0.999D;
                y = support.getY() + second;
                z = support.getZ() + first;
            }
        }
        return new Vec3d(x, y, z);
    }

    private static boolean matches(final BlockHitResult result, final LegitTellyTarget target) {
        return matches(result, target.placePos(), target.supportPos(), target.hit().getSide());
    }

    private static boolean matches(
            final BlockHitResult result,
            final BlockPos place,
            final BlockPos support,
            final Direction face
    ) {
        return result != null
                && result.getType() == HitResult.Type.BLOCK
                && result.getBlockPos().equals(support)
                && result.getSide() == face
                && result.getBlockPos().offset(result.getSide()).equals(place);
    }
}

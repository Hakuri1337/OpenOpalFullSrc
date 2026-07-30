package wtf.oraculus.client.feature.module.impl.world.legittelly;

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

/**
 * Per-tick placement resolver ported from the reference script.
 *
 * <p>Yaw is always supplied by the visible recording. This resolver searches
 * only pitch and support-face hit position, verifies the synthetic ray, and
 * never turns the player camera itself.</p>
 */
final class LegitTellyPlacementEngine {
    private static final Direction[] SUPPORT_SOURCES = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST,
            Direction.DOWN
    };
    private static final double[] FACE_OFFSETS = {
            0.50D, 0.25D, 0.75D, 0.15D, 0.85D,
            0.35D, 0.65D, 0.05D, 0.95D
    };
    private static final int REJECT_TICKS = 4;
    private static final int CONFIRM_TICKS = 3;

    private final Map<BlockPos, Integer> rejected = new HashMap<>();

    private Direction travel;
    private int bridgeLaneBlock;
    private int bridgeY;
    private double startProgress;
    private Vec3d lastServerPosition;
    private BlockPos pendingPlace;
    private int pendingTicks;
    private int originalSlot = -1;
    private int lastAttemptAge = Integer.MIN_VALUE;
    private BlockPos lastSupport;
    private Direction lastClickedFace;
    private boolean ownPlacementPacket;
    private boolean autoSwap;

    void begin(
            final LegitTellyActivation.ActivationSnapshot activation,
            final boolean autoSwap
    ) {
        this.travel = activation.travel();
        this.bridgeLaneBlock = activation.bridgeLaneBlock();
        this.bridgeY = activation.block().getY();
        this.startProgress = activation.startProgress();
        this.autoSwap = autoSwap;
        this.lastServerPosition = mc.player.getEntityPos();
        this.pendingPlace = null;
        this.pendingTicks = 0;
        this.originalSlot = mc.player.getInventory().getSelectedSlot();
        this.lastAttemptAge = Integer.MIN_VALUE;
        this.lastSupport = activation.block().toImmutable();
        this.lastClickedFace = activation.travel();
        this.ownPlacementPacket = false;
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

    boolean isBlockBelowPlayerReplaceable() {
        return mc.player != null
                && LegitTellyBlockPolicy.isReplaceable(this.currentBelowPlayer());
    }

    LegitTellyTarget resolveCurrent(final Vec2f visibleRotation) {
        if (visibleRotation == null || mc.player == null || mc.world == null
                || this.travel == null || !this.ensureBlockSlot()
                || !this.isBlockBelowPlayerReplaceable()) {
            return null;
        }

        final LegitTellyTarget cursor = this.directCursorTarget(visibleRotation);
        if (cursor != null) {
            return cursor;
        }

        LegitTellyTarget best = null;
        for (final BlockPos target : this.collectTargets()) {
            if (!this.isAllowedTarget(target) || !this.noPlayerCollision(target)) {
                continue;
            }
            final LegitTellyTarget candidate = this.findPitchTarget(
                    target, visibleRotation
            );
            if (candidate != null && (best == null || candidate.score() < best.score())) {
                best = candidate;
            }
        }
        return best;
    }

    boolean place(final LegitTellyTarget target, final Vec2f placementRotation) {
        if (target == null || placementRotation == null
                || mc.player == null || mc.world == null || mc.interactionManager == null
                || mc.player.age == this.lastAttemptAge
                || !this.ensureBlockSlot()
                || !this.isBlockBelowPlayerReplaceable()
                || !this.isAllowedTarget(target.placePos())
                || !this.noPlayerCollision(target.placePos())) {
            return false;
        }
        this.lastAttemptAge = mc.player.age;

        final BlockHitResult verified = this.raycast(placementRotation);
        if (!matches(verified, target.placePos(), target.supportPos(), target.hit().getSide())) {
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
            this.lastSupport = target.supportPos().toImmutable();
            this.lastClickedFace = target.hit().getSide();
            return true;
        } finally {
            this.ownPlacementPacket = false;
        }
    }

    boolean isOwnPlacementPacket() {
        return this.ownPlacementPacket;
    }

    boolean isAllowedExternalPlacement(final BlockHitResult hit) {
        if (hit == null || hit.getType() != HitResult.Type.BLOCK
                || !LegitTellyBlockPolicy.isSafeSupport(hit.getBlockPos())) {
            return false;
        }
        final BlockPos target = hit.getBlockPos().offset(hit.getSide());
        return this.isAllowedTarget(target) && this.noPlayerCollision(target);
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
        return mc.player != null && mc.interactionManager != null
                && this.ensureBlockSlot();
    }

    void restoreSlot() {
        if (this.originalSlot >= 0 && this.originalSlot < 9
                && mc.player != null && mc.interactionManager != null) {
            mc.player.getInventory().setSelectedSlot(this.originalSlot);
            ((ClientPlayerInteractionManagerAccessor) mc.interactionManager)
                    .callSyncSelectedSlot();
        }
        this.originalSlot = -1;
        this.travel = null;
        this.pendingPlace = null;
        this.lastSupport = null;
        this.lastClickedFace = null;
        this.rejected.clear();
    }

    private List<BlockPos> collectTargets() {
        final Set<BlockPos> result = new LinkedHashSet<>();
        // A telly jump crosses an integer Y boundary near its apex.  Using
        // floor(playerY)-1 there briefly selects the layer above the bridge,
        // while the reference keeps the straight bridge layer available via
        // its previous/strict-Y fallback chain.
        final int targetY = this.bridgeY;
        final Vec3d position = mc.player.getEntityPos();
        final Vec3d velocity = mc.player.getVelocity();

        this.addTarget(result, position, targetY);
        this.addTarget(result, position.add(
                velocity.x, 0.0D, velocity.z
        ), targetY);
        this.addTarget(result, position.add(
                velocity.x * 1.7D, 0.0D, velocity.z * 1.7D
        ), targetY);

        if (this.lastSupport != null && this.lastClickedFace != null) {
            result.add(this.lastSupport.offset(this.lastClickedFace));
        }
        return new ArrayList<>(result);
    }

    private void addTarget(
            final Set<BlockPos> targets,
            final Vec3d position,
            final int y
    ) {
        targets.add(new BlockPos(
                MathHelper.floor(position.x), y, MathHelper.floor(position.z)
        ));
    }

    private LegitTellyTarget directCursorTarget(final Vec2f visibleRotation) {
        final BlockHitResult ray = this.raycast(visibleRotation);
        if (ray == null || ray.getType() != HitResult.Type.BLOCK
                || ray.getSide().getAxis().isVertical()) {
            return null;
        }
        final BlockPos target = ray.getBlockPos().offset(ray.getSide());
        if (!this.isAllowedTarget(target)
                || !this.noPlayerCollision(target)
                || !LegitTellyBlockPolicy.isSafeSupport(ray.getBlockPos())) {
            return null;
        }
        return new LegitTellyTarget(
                target.toImmutable(), ray.getBlockPos().toImmutable(),
                ray, visibleRotation, 0.0D
        );
    }

    private LegitTellyTarget findPitchTarget(
            final BlockPos target,
            final Vec2f visibleRotation
    ) {
        LegitTellyTarget best = null;
        for (final Direction source : SUPPORT_SOURCES) {
            final BlockPos support = target.offset(source);
            final Direction clickedFace = source.getOpposite();
            if (!LegitTellyBlockPolicy.isSafeSupport(support)
                    || !this.isWithinReach(support)
                    || this.rejectSideSwitch(target, clickedFace)) {
                continue;
            }

            for (final double primary : FACE_OFFSETS) {
                for (final double secondary : FACE_OFFSETS) {
                    final Vec3d hitPoint = facePoint(
                            support, clickedFace, primary, secondary
                    );
                    final Vec2f exact = RotationUtility.getRotationFromPosition(
                            mc.player.getEyePos(), hitPoint
                    );
                    final Vec2f placementRotation = new Vec2f(
                            visibleRotation.x,
                            MathHelper.clamp(exact.y, -89.0F, 89.0F)
                    );
                    final BlockHitResult ray = this.raycast(placementRotation);
                    if (!matches(ray, target, support, clickedFace)) {
                        continue;
                    }

                    final double centerPenalty =
                            Math.abs(primary - 0.5D) + Math.abs(secondary - 0.5D);
                    final double facePenalty =
                            clickedFace == Direction.UP ? 0.0D : 0.35D;
                    final double switchPenalty =
                            this.lastClickedFace != null
                                    && clickedFace != this.lastClickedFace ? 0.8D : 0.0D;
                    final double score =
                            Math.abs(placementRotation.y - visibleRotation.y)
                                    + centerPenalty * 2.0D
                                    + facePenalty + switchPenalty;
                    final LegitTellyTarget candidate = new LegitTellyTarget(
                            target.toImmutable(), support.toImmutable(),
                            ray, placementRotation, score
                    );
                    if (best == null || candidate.score() < best.score()) {
                        best = candidate;
                    }
                }
            }
        }
        return best;
    }

    private boolean rejectSideSwitch(
            final BlockPos target,
            final Direction clickedFace
    ) {
        if (this.lastClickedFace == null
                || !clickedFace.getAxis().isHorizontal()
                || !this.lastClickedFace.getAxis().isHorizontal()
                || clickedFace == this.lastClickedFace) {
            return false;
        }
        final BlockPos preferredSupport = target.offset(
                this.lastClickedFace.getOpposite()
        );
        return LegitTellyBlockPolicy.isSafeSupport(preferredSupport)
                && this.isWithinReach(preferredSupport);
    }

    private boolean isAllowedTarget(final BlockPos pos) {
        if (pos == null || this.travel == null
                || !LegitTellyBlockPolicy.isReplaceable(pos)
                || this.rejected.containsKey(pos)
                || (this.pendingPlace != null && this.pendingPlace.equals(pos))
                || pos.getY() != this.bridgeY) {
            return false;
        }
        final boolean alongX = this.travel.getAxis() == Direction.Axis.X;
        final int targetLane = alongX ? pos.getZ() : pos.getX();
        if (targetLane != this.bridgeLaneBlock) {
            return false;
        }
        final double progress = LegitTellyActivation.progress(pos, this.travel);
        final double playerProgress =
                mc.player.getX() * this.travel.getOffsetX()
                        + mc.player.getZ() * this.travel.getOffsetZ();
        return progress >= this.startProgress
                && progress <= playerProgress + 2.0D;
    }

    private boolean ensureBlockSlot() {
        final int selected = mc.player.getInventory().getSelectedSlot();
        final ItemStack held = mc.player.getInventory().getStack(selected);
        if (LegitTellyBlockPolicy.isPlaceable(held)
                && (held.getCount() > 5 || !this.autoSwap)) {
            return true;
        }
        if (!this.autoSwap) {
            return false;
        }

        int bestSlot = LegitTellyBlockPolicy.isPlaceable(held) ? selected : -1;
        int bestCount = bestSlot == -1 ? 0 : held.getCount();
        for (int slot = 0; slot < 9; slot++) {
            final ItemStack stack = mc.player.getInventory().getStack(slot);
            if (LegitTellyBlockPolicy.isPlaceable(stack)
                    && stack.getCount() > bestCount) {
                bestSlot = slot;
                bestCount = stack.getCount();
            }
        }
        if (bestSlot == -1) {
            return false;
        }
        if (bestSlot != selected) {
            mc.player.getInventory().setSelectedSlot(bestSlot);
            ((ClientPlayerInteractionManagerAccessor) mc.interactionManager)
                    .callSyncSelectedSlot();
        }
        return true;
    }

    private boolean isWithinReach(final BlockPos pos) {
        final Vec3d eye = mc.player.getEyePos();
        final double x = MathHelper.clamp(
                eye.x, pos.getX(), pos.getX() + 1.0D
        );
        final double y = MathHelper.clamp(
                eye.y, pos.getY(), pos.getY() + 1.0D
        );
        final double z = MathHelper.clamp(
                eye.z, pos.getZ(), pos.getZ() + 1.0D
        );
        return eye.squaredDistanceTo(x, y, z)
                <= MathHelper.square(mc.player.getBlockInteractionRange());
    }

    private BlockPos currentBelowPlayer() {
        return new BlockPos(
                MathHelper.floor(mc.player.getX()),
                this.bridgeY,
                MathHelper.floor(mc.player.getZ())
        );
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
                eye, end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));
    }

    private boolean noPlayerCollision(final BlockPos pos) {
        final Box target = new Box(pos);
        if (mc.player.getBoundingBox().intersects(target)) {
            return false;
        }
        // The reference consults historical positions only while grounded and
        // only for blocks above the normal below-player level.
        if (mc.player.isOnGround()
                && pos.getY() > MathHelper.floor(mc.player.getY()) - 1) {
            final double dx = mc.player.lastX - mc.player.getX();
            final double dz = mc.player.lastZ - mc.player.getZ();
            if (mc.player.getBoundingBox().offset(dx, 0.0D, dz).intersects(target)) {
                return false;
            }
            if (this.lastServerPosition != null) {
                final Vec3d serverOffset = this.lastServerPosition.subtract(
                        mc.player.getEntityPos()
                );
                return !mc.player.getBoundingBox().offset(serverOffset).intersects(target);
            }
        }
        return true;
    }

    private static Vec3d facePoint(
            final BlockPos support,
            final Direction face,
            final double primary,
            final double secondary
    ) {
        return switch (face) {
            case NORTH -> new Vec3d(
                    support.getX() + primary,
                    support.getY() + secondary,
                    support.getZ() + 0.001D
            );
            case SOUTH -> new Vec3d(
                    support.getX() + primary,
                    support.getY() + secondary,
                    support.getZ() + 0.999D
            );
            case WEST -> new Vec3d(
                    support.getX() + 0.001D,
                    support.getY() + primary,
                    support.getZ() + secondary
            );
            case EAST -> new Vec3d(
                    support.getX() + 0.999D,
                    support.getY() + primary,
                    support.getZ() + secondary
            );
            case DOWN -> new Vec3d(
                    support.getX() + primary,
                    support.getY() + 0.001D,
                    support.getZ() + secondary
            );
            case UP -> new Vec3d(
                    support.getX() + primary,
                    support.getY() + 0.999D,
                    support.getZ() + secondary
            );
        };
    }

    private static boolean matches(
            final BlockHitResult result,
            final BlockPos target,
            final BlockPos support,
            final Direction face
    ) {
        return result != null
                && result.getType() == HitResult.Type.BLOCK
                && result.getBlockPos().equals(support)
                && result.getSide() == face
                && result.getBlockPos().offset(result.getSide()).equals(target);
    }
}

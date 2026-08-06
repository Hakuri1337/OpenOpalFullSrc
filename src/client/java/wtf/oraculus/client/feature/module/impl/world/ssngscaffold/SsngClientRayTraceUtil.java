package wtf.oraculus.client.feature.module.impl.world.ssngscaffold;

import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Predicate;

/** SSNG's DDA ray tracer, kept separate from Oraculus' general raycast helpers. */
public final class SsngClientRayTraceUtil {
    private static final double EPSILON = 1.0E-7D;
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static Vec3d eyePos;

    private SsngClientRayTraceUtil() { }

    public static void updateEyePos() {
        if (mc.player != null) eyePos = mc.player.getEyePos();
    }

    public static boolean didHitBlockFace(final SsngRotation rotation, final BlockPos targetPos,
                                          final Direction expectedFace, final boolean strict) {
        return rotation != null && didHitBlockFace(mc.player, rotation.yaw(), rotation.pitch(), targetPos, expectedFace, strict, SsngClientRayTraceUtil::isIgnoredBlock);
    }

    public static boolean didHitBlockFace(final SsngRotation rotation, final BlockPos targetPos,
                                          final Direction expectedFace, final boolean strict,
                                          final Predicate<BlockState> ignorePredicate) {
        return rotation != null && didHitBlockFace(mc.player, rotation.yaw(), rotation.pitch(), targetPos,
                expectedFace, strict, ignorePredicate);
    }

    public static boolean didHitBlockFace(final PlayerEntity player, final float yaw, final float pitch,
                                          final BlockPos targetPos, final Direction expectedFace,
                                          final boolean strict) {
        return didHitBlockFace(player, yaw, pitch, targetPos, expectedFace, strict,
                SsngClientRayTraceUtil::isIgnoredBlock);
    }

    public static boolean didHitBlockFace(final PlayerEntity player, final float yaw, final float pitch,
                                          final BlockPos targetPos, final Direction expectedFace,
                                          final boolean strict, final Predicate<BlockState> ignorePredicate) {
        if (player == null || targetPos == null || expectedFace == null) return false;
        final BlockHitResult result = getFacedBlock(yaw, pitch, ignorePredicate);
        return result != null && targetPos.equals(result.getBlockPos()) && (!strict || expectedFace == result.getSide());
    }

    public static BlockHitResult getFacedBlock(final float yaw, final float pitch) {
        return getFacedBlock(yaw, pitch, SsngClientRayTraceUtil::isIgnoredBlock);
    }

    public static BlockHitResult getFacedBlock(final float yaw, final float pitch,
                                               final Predicate<BlockState> ignorePredicate) {
        if (mc.player == null || eyePos == null || (yaw == 0.0F && pitch == 0.0F)) return null;
        final double reach = mc.player.getBlockInteractionRange();
        Vec3d direction = Vec3d.fromPolar(pitch, yaw);
        final Vec3d start = eyePos;
        final Vec3d end = start.add(direction.multiply(reach));
        if (direction.x == 0) direction = new Vec3d(EPSILON, direction.y, direction.z);
        if (direction.y == 0) direction = new Vec3d(direction.x, EPSILON, direction.z);
        if (direction.z == 0) direction = new Vec3d(direction.x, direction.y, EPSILON);

        BlockPos current = BlockPos.ofFloored(start);
        final int stepX = Integer.signum((int) Math.signum(direction.x));
        final int stepY = Integer.signum((int) Math.signum(direction.y));
        final int stepZ = Integer.signum((int) Math.signum(direction.z));
        final double nextX = stepX > 0 ? current.getX() + 1 : current.getX();
        final double nextY = stepY > 0 ? current.getY() + 1 : current.getY();
        final double nextZ = stepZ > 0 ? current.getZ() + 1 : current.getZ();
        double tMaxX = (nextX - start.x) / direction.x;
        double tMaxY = (nextY - start.y) / direction.y;
        double tMaxZ = (nextZ - start.z) / direction.z;
        final double tDeltaX = stepX / direction.x;
        final double tDeltaY = stepY / direction.y;
        final double tDeltaZ = stepZ / direction.z;

        while (start.distanceTo(current.toCenterPos()) <= reach) {
            if (!mc.world.isAir(current)) {
                final BlockState state = mc.world.getBlockState(current);
                if (!ignorePredicate.test(state)) {
                    final FluidState fluid = mc.world.getFluidState(current);
                    final VoxelShape shape = fluid != null && fluid.isStill() && fluid.getFluid() == Fluids.WATER
                            ? VoxelShapes.fullCube()
                            : state.getCollisionShape(mc.world, current, ShapeContext.of(mc.player));
                    for (final Box local : shape.getBoundingBoxes()) {
                        final Box box = local.offset(current);
                        final Optional<Vec3d> hit = box.raycast(start, end);
                        if (hit.isPresent()) {
                            final Vec3d hitVec = hit.get();
                            return new BlockHitResult(hitVec, hitFace(hitVec, box), current, box.contains(start));
                        }
                    }
                }
            }
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) { current = current.add(stepX, 0, 0); tMaxX += tDeltaX; }
                else { current = current.add(0, 0, stepZ); tMaxZ += tDeltaZ; }
            } else if (tMaxY < tMaxZ) {
                current = current.add(0, stepY, 0); tMaxY += tDeltaY;
            } else { current = current.add(0, 0, stepZ); tMaxZ += tDeltaZ; }
        }
        return null;
    }

    public static boolean isIgnoredBlock(final BlockState state) {
        final Block block = state.getBlock();
        return block instanceof PlantBlock || block instanceof SnowBlock || block instanceof AirBlock
                || block instanceof ShortPlantBlock || block instanceof FluidBlock;
    }

    @Nullable
    private static Direction hitFace(final Vec3d hit, final Box box) {
        if (Math.abs(hit.x - box.minX) <= EPSILON) return Direction.WEST;
        if (Math.abs(hit.x - box.maxX) <= EPSILON) return Direction.EAST;
        if (Math.abs(hit.y - box.minY) <= EPSILON) return Direction.DOWN;
        if (Math.abs(hit.y - box.maxY) <= EPSILON) return Direction.UP;
        if (Math.abs(hit.z - box.minZ) <= EPSILON) return Direction.NORTH;
        if (Math.abs(hit.z - box.maxZ) <= EPSILON) return Direction.SOUTH;
        double min = Math.abs(hit.x - box.minX); Direction face = Direction.WEST;
        if (Math.abs(hit.x - box.maxX) < min) { min = Math.abs(hit.x - box.maxX); face = Direction.EAST; }
        if (Math.abs(hit.y - box.minY) < min) { min = Math.abs(hit.y - box.minY); face = Direction.DOWN; }
        if (Math.abs(hit.y - box.maxY) < min) { min = Math.abs(hit.y - box.maxY); face = Direction.UP; }
        if (Math.abs(hit.z - box.minZ) < min) { min = Math.abs(hit.z - box.minZ); face = Direction.NORTH; }
        if (Math.abs(hit.z - box.maxZ) < min) face = Direction.SOUTH;
        return face;
    }

    public static BlockHitResult getFacedContainerBlock(final float yaw, final float pitch) {
        if (mc.player == null || eyePos == null) return null;
        final double reach = mc.player.getBlockInteractionRange();
        Vec3d direction = Vec3d.fromPolar(pitch, yaw);
        final Vec3d start = eyePos, end = start.add(direction.multiply(reach));
        if (direction.x == 0) direction = new Vec3d(EPSILON, direction.y, direction.z);
        if (direction.y == 0) direction = new Vec3d(direction.x, EPSILON, direction.z);
        if (direction.z == 0) direction = new Vec3d(direction.x, direction.y, EPSILON);
        BlockPos current = BlockPos.ofFloored(start);
        final int sx = (int) Math.signum(direction.x), sy = (int) Math.signum(direction.y), sz = (int) Math.signum(direction.z);
        double tx = ((sx > 0 ? current.getX() + 1 : current.getX()) - start.x) / direction.x;
        double ty = ((sy > 0 ? current.getY() + 1 : current.getY()) - start.y) / direction.y;
        double tz = ((sz > 0 ? current.getZ() + 1 : current.getZ()) - start.z) / direction.z;
        final double dx = sx / direction.x, dy = sy / direction.y, dz = sz / direction.z;
        while (start.distanceTo(current.toCenterPos()) <= reach) {
            final BlockState state = mc.world.getBlockState(current);
            if (!state.isAir() && isContainer(state)) {
                final Box box = new Box(current);
                final Optional<Vec3d> hit = box.raycast(start, end);
                if (hit.isPresent()) return new BlockHitResult(hit.get(), hitFace(hit.get(), box), current, box.contains(start));
            }
            if (tx < ty) { if (tx < tz) { current = current.add(sx, 0, 0); tx += dx; } else { current = current.add(0, 0, sz); tz += dz; } }
            else if (ty < tz) { current = current.add(0, sy, 0); ty += dy; }
            else { current = current.add(0, 0, sz); tz += dz; }
        }
        return null;
    }

    private static boolean isContainer(final BlockState state) {
        return state.getBlock() instanceof ChestBlock || state.getBlock() instanceof EnderChestBlock || state.getBlock() instanceof ShulkerBoxBlock;
    }

    public static double intersectRayAabb(final Vec3d origin, final Vec3d direction, final Box box) {
        double minimum = 0.0D;
        double maximum = Double.POSITIVE_INFINITY;
        if (!axisSlab(origin.x, direction.x, box.minX, box.maxX, SlabHolder.TEMP)) return Double.POSITIVE_INFINITY;
        minimum = Math.max(minimum, SlabHolder.TEMP[0]); maximum = Math.min(maximum, SlabHolder.TEMP[1]);
        if (maximum < minimum) return Double.POSITIVE_INFINITY;
        if (!axisSlab(origin.y, direction.y, box.minY, box.maxY, SlabHolder.TEMP)) return Double.POSITIVE_INFINITY;
        minimum = Math.max(minimum, SlabHolder.TEMP[0]); maximum = Math.min(maximum, SlabHolder.TEMP[1]);
        if (maximum < minimum) return Double.POSITIVE_INFINITY;
        if (!axisSlab(origin.z, direction.z, box.minZ, box.maxZ, SlabHolder.TEMP)) return Double.POSITIVE_INFINITY;
        minimum = Math.max(minimum, SlabHolder.TEMP[0]); maximum = Math.min(maximum, SlabHolder.TEMP[1]);
        return maximum < minimum ? Double.POSITIVE_INFINITY : minimum;
    }

    private static boolean axisSlab(final double origin, final double direction, final double minimum,
                                    final double maximum, final double[] output) {
        if (Math.abs(direction) < 1.0E-12D) {
            if (origin < minimum || origin > maximum) return false;
            output[0] = Double.NEGATIVE_INFINITY;
            output[1] = Double.POSITIVE_INFINITY;
            return true;
        }
        final double inverse = 1.0D / direction;
        double first = (minimum - origin) * inverse;
        double second = (maximum - origin) * inverse;
        if (first > second) {
            final double temporary = first;
            first = second;
            second = temporary;
        }
        output[0] = first;
        output[1] = second;
        return true;
    }

    private static final class SlabHolder {
        private static final double[] TEMP = new double[2];
    }

    public static double getDistance(final float yaw, final float pitch, final Entity target) {
        return target == null || eyePos == null ? Double.POSITIVE_INFINITY
                : intersectRayAabb(eyePos, Vec3d.fromPolar(pitch, yaw).normalize(), target.getBoundingBox());
    }

    public static boolean didHitEntity(final float yaw, final float pitch, final double range, final Entity target) {
        return target != null && overBox(yaw, pitch, range, target.getBoundingBox());
    }

    public static boolean overBox(final float yaw, final float pitch, final double range, final Box box) {
        if (box == null || eyePos == null || mc.world == null) return false;
        final Vec3d direction = Vec3d.fromPolar(pitch, yaw).normalize();
        if (direction.lengthSquared() < 1.0E-12D) return false;
        final double hit = intersectRayAabb(eyePos, direction, box);
        return Double.isFinite(hit) && hit >= 0.0D && hit <= range
                && !isOccludedBefore(mc.world, eyePos, direction, hit, range, mc.player);
    }

    private static boolean isOccludedBefore(final World world, final Vec3d origin, final Vec3d direction,
                                            final double stop, final double range, final Entity viewer) {
        int x = MathHelper.floor(origin.x), y = MathHelper.floor(origin.y), z = MathHelper.floor(origin.z);
        final int sx = direction.x > 0 ? 1 : direction.x < 0 ? -1 : 0;
        final int sy = direction.y > 0 ? 1 : direction.y < 0 ? -1 : 0;
        final int sz = direction.z > 0 ? 1 : direction.z < 0 ? -1 : 0;
        double tx = nextBoundary(origin.x, direction.x, x, sx), ty = nextBoundary(origin.y, direction.y, y, sy), tz = nextBoundary(origin.z, direction.z, z, sz);
        final double dx = sx == 0 ? Double.POSITIVE_INFINITY : 1.0D / Math.abs(direction.x);
        final double dy = sy == 0 ? Double.POSITIVE_INFINITY : 1.0D / Math.abs(direction.y);
        final double dz = sz == 0 ? Double.POSITIVE_INFINITY : 1.0D / Math.abs(direction.z);
        final double limit = Math.min(stop, range);
        if (blockOccludes(world, origin, direction, new BlockPos(x, y, z), stop, viewer)) return true;
        while (true) {
            if (tx < ty) {
                if (tx < tz) { if (tx > limit) break; x += sx; tx += dx; }
                else { if (tz > limit) break; z += sz; tz += dz; }
            } else if (ty < tz) { if (ty > limit) break; y += sy; ty += dy; }
            else { if (tz > limit) break; z += sz; tz += dz; }
            if (blockOccludes(world, origin, direction, new BlockPos(x, y, z), stop, viewer)) return true;
        }
        return false;
    }

    private static double nextBoundary(final double origin, final double direction, final int cell, final int step) {
        if (step == 0) return Double.POSITIVE_INFINITY;
        return ((step > 0 ? cell + 1 : cell) - origin) / direction;
    }

    private static boolean blockOccludes(final World world, final Vec3d origin, final Vec3d direction,
                                         final BlockPos pos, final double entityDistance, final Entity viewer) {
        final BlockState state = world.getBlockState(pos);
        if (state.isAir()) return false;
        final VoxelShape shape = state.getOutlineShape(world, pos, viewer == null ? ShapeContext.absent() : ShapeContext.of(viewer));
        for (final Box local : shape.getBoundingBoxes()) {
            final double distance = intersectRayAabb(origin, direction, local.offset(pos));
            if (Double.isFinite(distance) && distance >= 0.0D && distance < entityDistance) return true;
        }
        return false;
    }
}

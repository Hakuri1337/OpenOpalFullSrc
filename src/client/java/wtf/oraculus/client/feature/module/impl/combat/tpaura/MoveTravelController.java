package wtf.oraculus.client.feature.module.impl.combat.tpaura;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static wtf.oraculus.client.Constants.mc;

/**
 * Drives TpAura's optional real-movement travel path.  It deliberately owns no
 * packet state: vanilla movement packets must describe the player's actual
 * client position while this controller is active.
 */
final class MoveTravelController {
    private static final double ARRIVAL_RADIUS = 0.14D;
    private static final double PROGRESS_EPSILON = 0.025D;
    private static final int MAX_STALLED_TICKS = 10;

    private final List<Vec3d> confirmedOutboundWaypoints = new ArrayList<>();

    private List<Vec3d> waypoints = List.of();
    private Vec3d origin;
    private int waypointIndex;
    private int stalledTicks;
    private double previousDistance = Double.MAX_VALUE;
    private Direction direction = Direction.NONE;

    void beginOutbound(final Vec3d start, final List<Vec3d> route) {
        reset();
        if (route.isEmpty()) {
            direction = Direction.FAILED;
            return;
        }
        origin = start;
        waypoints = List.copyOf(route);
        direction = Direction.OUTBOUND;
    }

    void beginReturn() {
        if (origin == null || confirmedOutboundWaypoints.isEmpty()) {
            direction = Direction.FAILED;
            stopHorizontalMotion();
            return;
        }

        final List<Vec3d> reverseRoute = new ArrayList<>(confirmedOutboundWaypoints);
        Collections.reverse(reverseRoute);
        if (reverseRoute.isEmpty() || reverseRoute.getLast().squaredDistanceTo(origin) > ARRIVAL_RADIUS * ARRIVAL_RADIUS) {
            reverseRoute.add(origin);
        }

        waypoints = List.copyOf(reverseRoute);
        waypointIndex = 0;
        stalledTicks = 0;
        previousDistance = Double.MAX_VALUE;
        direction = Direction.RETURNING;
    }

    void tick(final double speed) {
        if (!isTraveling()) {
            return;
        }
        if (mc.player == null || mc.world == null || waypointIndex >= waypoints.size()) {
            fail();
            return;
        }

        final Vec3d currentPosition = mc.player.getEntityPos();
        final Vec3d waypoint = waypoints.get(waypointIndex);
        final Vec3d difference = waypoint.subtract(currentPosition);
        final double distance = difference.length();

        if (distance <= ARRIVAL_RADIUS) {
            confirmWaypoint();
            return;
        }

        if (distance < previousDistance - PROGRESS_EPSILON) {
            stalledTicks = 0;
        } else if (++stalledTicks > MAX_STALLED_TICKS) {
            fail();
            return;
        }
        previousDistance = distance;

        final Vec3d velocity = difference.multiply(Math.min(speed, distance) / distance);
        final Box candidateBox = mc.player.getBoundingBox().offset(velocity);
        if (mc.world.getBlockCollisions(mc.player, candidateBox).iterator().hasNext()) {
            fail();
            return;
        }

        mc.player.setVelocity(velocity);
    }

    boolean isComplete() {
        return direction == Direction.COMPLETE;
    }

    boolean hasFailed() {
        return direction == Direction.FAILED;
    }

    boolean isTraveling() {
        return direction == Direction.OUTBOUND || direction == Direction.RETURNING;
    }

    void reset() {
        stopHorizontalMotion();
        confirmedOutboundWaypoints.clear();
        waypoints = List.of();
        origin = null;
        waypointIndex = 0;
        stalledTicks = 0;
        previousDistance = Double.MAX_VALUE;
        direction = Direction.NONE;
    }

    private void confirmWaypoint() {
        if (direction == Direction.OUTBOUND) {
            confirmedOutboundWaypoints.add(waypoints.get(waypointIndex));
        }
        waypointIndex++;
        stalledTicks = 0;
        previousDistance = Double.MAX_VALUE;

        if (waypointIndex >= waypoints.size()) {
            stopHorizontalMotion();
            direction = Direction.COMPLETE;
        }
    }

    private void fail() {
        stopHorizontalMotion();
        direction = Direction.FAILED;
    }

    private void stopHorizontalMotion() {
        if (mc.player == null) {
            return;
        }
        final Vec3d velocity = mc.player.getVelocity();
        mc.player.setVelocity(0.0D, velocity.y, 0.0D);
    }

    private enum Direction {
        NONE,
        OUTBOUND,
        RETURNING,
        COMPLETE,
        FAILED
    }
}

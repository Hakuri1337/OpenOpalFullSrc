package wtf.oraculus.client.feature.module.impl.combat.tpaura;

import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.helper.impl.LocalDataWatch;
import wtf.oraculus.client.feature.helper.impl.target.TargetProperty;
import wtf.oraculus.client.feature.helper.impl.target.impl.TargetLivingEntity;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.impl.combat.TeamsModule;
import wtf.oraculus.client.feature.module.impl.movement.flight.FlightModule;
import wtf.oraculus.client.feature.module.impl.movement.longjump.LongJumpModule;
import wtf.oraculus.client.feature.module.impl.movement.speed.SpeedModule;
import wtf.oraculus.client.feature.module.impl.utility.AntiBotsModule;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.mode.ModeProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.packet.ReceivePacketEvent;
import wtf.oraculus.event.impl.game.packet.SendPacketEvent;
import wtf.oraculus.event.impl.game.player.movement.PreMovementPacketEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.mixin.PlayerMoveC2SPacketAccessor;
import wtf.oraculus.utility.misc.chat.ChatUtility;
import wtf.oraculus.utility.player.RotationUtility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import static wtf.oraculus.client.Constants.mc;

/** Complete Java lifecycle port of LiquidBounce ModuleTpAura and its Immediate/AStar modes. */
public final class TpAuraModule extends Module {
    private final NumberProperty attackRange = new NumberProperty("AttackRange", 4.2D, 3.0D, 5.0D, 0.1D);
    private final NumberProperty minCps = new NumberProperty("MinCPS", 5.0D, 1.0D, 60.0D, 1.0D);
    private final NumberProperty maxCps = new NumberProperty("MaxCPS", 8.0D, 1.0D, 60.0D, 1.0D);
    private final BooleanProperty attackCooldown19 = new BooleanProperty("1.9+ CoolDown", false);
    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.A_STAR);
    private final ModeProperty<Priority> priority = new ModeProperty<>("Priority", Priority.HURT_TIME);
    private final NumberProperty fov = new NumberProperty("FOV", 180.0D, 1.0D, 180.0D, 1.0D);
    private final NumberProperty hurtTime = new NumberProperty("HurtTime", 10.0D, 0.0D, 10.0D, 1.0D);
    // Keep the same safe default as the combat target selector: players only.
    // Hostile and passive entities must be explicitly enabled in Targets.
    private final TargetProperty targets = new TargetProperty(true, false, false, false, false, true);

    private final NumberProperty maximumDistance = new NumberProperty("MaximumDistance", 95.0D, 50.0D, 250.0D, 1.0D);
    private final NumberProperty maximumCost = new NumberProperty("MaximumCost", 250.0D, 50.0D, 500.0D, 1.0D);
    private final NumberProperty tickDistance = new NumberProperty("TickDistance", 3.0D, 1.0D, 7.0D, 1.0D);
    private final wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty allowDiagonal = new wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty("AllowDiagonal", false);
    private final wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty tpBack = new wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty("TpBack", true);
    private final NumberProperty stick = new NumberProperty("Stick", "ticks", 5.0D, 1.0D, 10.0D, 1.0D);
    private final BooleanProperty moveInstandOfTp = new BooleanProperty("MoveInstandOfTP", false);
    private final NumberProperty moveSpeed = new NumberProperty("MoveSpeed", "blocks/tick", 1.20D, 0.10D, 3.00D, 0.05D);

    private Vec3d serverPosition;
    private BlockPos pathStart = BlockPos.ORIGIN;
    private List<BlockPos> pathCache;
    private TravelState state = TravelState.IDLE;
    private int stateTicks;
    private long nextClickAt;
    private LivingEntity currentEnemy;
    private boolean sendingTravelPacket;
    private final MoveTravelController moveTravelController = new MoveTravelController();

    public TpAuraModule() {
        super("TpAura", "Teleports to targets, attacks, and returns.", ModuleCategory.COMBAT);
        addProperties(attackRange, minCps, maxCps, attackCooldown19, mode, priority, fov, hurtTime, targets.get(),
                maximumDistance, maximumCost, tickDistance, allowDiagonal, tpBack, stick,
                moveInstandOfTp, moveSpeed);
        maximumDistance.hideIf(() -> !mode.is(Mode.A_STAR));
        maximumCost.hideIf(() -> !mode.is(Mode.A_STAR));
        tickDistance.hideIf(() -> !mode.is(Mode.A_STAR));
        allowDiagonal.hideIf(() -> !mode.is(Mode.A_STAR));
        tpBack.hideIf(() -> !mode.is(Mode.A_STAR));
        stick.hideIf(() -> !mode.is(Mode.A_STAR));
        moveSpeed.hideIf(() -> !moveInstandOfTp.getValue());
        minCps.hideIf(() -> attackCooldown19.getValue());
        maxCps.hideIf(() -> attackCooldown19.getValue());
    }

    @Override public String getSuffix() { return mode.getValue().toString(); }

    @Override protected void onEnable() { reset(); super.onEnable(); }
    @Override protected void onDisable() { reset(); super.onDisable(); }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) { reset(); return; }

        if (moveInstandOfTp.getValue()) {
            tickMoveTravel();
            return;
        }

        tickPacketTravel();
    }

    private void tickPacketTravel() {
        stateTicks++;
        // A runtime option change must never continue a real-movement state
        // through the packet-travel lifecycle.
        if (state == TravelState.OUTBOUND || state == TravelState.RETURNING) {
            reset();
            return;
        }
        if (state == TravelState.IDLE) {
            if (!canClick()) return;
            if (mode.is(Mode.IMMEDIATE)) beginImmediate(); else beginAStar();
            return;
        }
        if (state == TravelState.STICKING && stateTicks >= stick.getValue().intValue()) {
            if (mode.is(Mode.IMMEDIATE)) returnImmediate(); else returnAStar();
        }
    }

    private void tickMoveTravel() {
        if (hasMoveConflict()) {
            reset();
            return;
        }

        stateTicks++;
        switch (state) {
            case IDLE -> {
                if (canClick()) {
                    beginMoveTravel();
                }
            }
            case OUTBOUND, RETURNING -> {
                moveTravelController.tick(moveSpeed.getValue());
                if (moveTravelController.hasFailed()) {
                    reset();
                    return;
                }
                if (!moveTravelController.isComplete()) {
                    return;
                }
                if (state == TravelState.RETURNING) {
                    reset();
                    return;
                }

                final Vec3d position = mc.player.getEntityPos();
                if (!attackNearby(position)) {
                    reset();
                    return;
                }
                state = TravelState.STICKING;
                stateTicks = 0;
            }
            case STICKING -> {
                if (stateTicks < stick.getValue().intValue()) {
                    return;
                }
                // Immediate has always returned unconditionally in the
                // LiquidBounce-derived packet mode. Preserve that contract;
                // TpBack only controls AStar travel.
                if (mode.is(Mode.A_STAR) && !tpBack.getValue()) {
                    reset();
                    return;
                }
                moveTravelController.beginReturn();
                if (moveTravelController.hasFailed()) {
                    reset();
                    return;
                }
                state = TravelState.RETURNING;
                stateTicks = 0;
            }
        }
    }

    @Subscribe
    public void onPreMovementPacket(final PreMovementPacketEvent event) {
        if (!moveInstandOfTp.getValue() && serverPosition != null) {
            event.setX(serverPosition.x);
            event.setY(serverPosition.y);
            event.setZ(serverPosition.z);
        }
    }

    @Subscribe
    public void onSendPacket(final SendPacketEvent event) {
        if (!moveInstandOfTp.getValue() && !sendingTravelPacket && serverPosition != null && event.getPacket() instanceof PlayerMoveC2SPacket move) {
            final PlayerMoveC2SPacketAccessor access = (PlayerMoveC2SPacketAccessor) move;
            access.setX(serverPosition.x);
            access.setY(serverPosition.y);
            access.setZ(serverPosition.z);
        }
    }

    @Subscribe
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (event.getPacket() instanceof PlayerPositionLookS2CPacket
                && (serverPosition != null || moveTravelController.isTraveling())) {
            ChatUtility.error("TpAura: server setback detected, travel cancelled.");
            reset();
        }
    }

    private void beginMoveTravel() {
        final LivingEntity enemy = selectTarget(mc.player.getEntityPos(), maximumDistance.getValue());
        if (enemy == null) {
            return;
        }

        final List<Vec3d> route;
        if (mode.is(Mode.IMMEDIATE)) {
            final Vec3d destination = closestAttackPosition(enemy, mc.player.getEntityPos());
            if (!hasClearPath(mc.player.getEntityPos(), destination)) {
                return;
            }
            route = List.of(destination);
        } else {
            final List<BlockPos> path = findPath(
                    mc.player.getBlockPos(), enemy.getBlockPos(),
                    maximumCost.getValue().intValue(), allowDiagonal.getValue()
            );
            if (path.isEmpty()) {
                return;
            }
            route = path.stream()
                    .map(position -> new Vec3d(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D))
                    .toList();
        }

        currentEnemy = enemy;
        moveTravelController.beginOutbound(mc.player.getEntityPos(), route);
        if (moveTravelController.hasFailed()) {
            reset();
            return;
        }
        state = TravelState.OUTBOUND;
        stateTicks = 0;
    }

    private void beginImmediate() {
        final LivingEntity enemy = selectTarget(mc.player.getEntityPos(), maximumDistance.getValue());
        if (enemy == null) return;
        currentEnemy = enemy;
        travelImmediate(enemy.getEntityPos());
        attackNearby();
        state = TravelState.STICKING;
        stateTicks = 0;
    }

    private void returnImmediate() {
        if (serverPosition == null) { reset(); return; }
        travelImmediate(mc.player.getEntityPos());
        reset();
    }

    private void beginAStar() {
        final BlockPos start = pathStart.equals(BlockPos.ORIGIN) ? mc.player.getBlockPos() : pathStart;
        final LivingEntity enemy = selectTarget(start.toCenterPos(), maximumDistance.getValue());
        if (enemy == null) return;
        final List<BlockPos> path = findPath(start, enemy.getBlockPos(), maximumCost.getValue().intValue(), allowDiagonal.getValue());
        if (path.isEmpty()) return;
        currentEnemy = enemy;
        pathCache = path;
        travelPath(path);
        attackNearby();
        state = TravelState.STICKING;
        stateTicks = 0;
    }

    private void returnAStar() {
        if (pathCache == null) { reset(); return; }
        if (tpBack.getValue()) {
            final List<BlockPos> reverse = new ArrayList<>(pathCache);
            Collections.reverse(reverse);
            travelPath(reverse);
            pathStart = BlockPos.ORIGIN;
        } else if (serverPosition != null) {
            pathStart = BlockPos.ofFloored(serverPosition);
        }
        resetTransient();
    }

    /** LiquidBounce's ImmediateMode redundant packet calculation is preserved verbatim. */
    private void travelImmediate(final Vec3d position) {
        final Vec3d origin = mc.player.getEntityPos();
        final Vec3d delta = position.subtract(origin);
        final int times = Math.max(0, (int) Math.floor((Math.abs(delta.x) + Math.abs(delta.y) + Math.abs(delta.z)) / 10.0D) - 1);
        for (int i = 0; i < times; i++) sendPosition(origin);
        sendPosition(position);
    }

    private void travelPath(final List<BlockPos> path) {
        final int chunkSize = tickDistance.getValue().intValue();
        for (int offset = 0; offset < path.size(); offset += chunkSize) {
            final List<BlockPos> chunk = path.subList(offset, Math.min(path.size(), offset + chunkSize));
            final Vec3d start = chunk.getFirst().toCenterPos();
            final Vec3d end = chunk.getLast().toCenterPos();
            if (!mc.world.getBlockCollisions(mc.player, new Box(start, end)).iterator().hasNext()) {
                sendPosition(end);
            } else {
                for (final BlockPos position : chunk) sendPosition(new Vec3d(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D));
            }
        }
    }

    private void sendPosition(final Vec3d position) {
        sendingTravelPacket = true;
        try {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(position.x, position.y, position.z,
                    mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround(), false));
            serverPosition = position;
        } finally {
            sendingTravelPacket = false;
        }
    }

    private boolean attackNearby() {
        final Vec3d base = serverPosition == null ? mc.player.getEntityPos() : serverPosition;
        return attackNearby(base);
    }

    private boolean attackNearby(final Vec3d base) {
        if (!canClick()) return false;
        final LivingEntity enemy = currentEnemy != null
                && isValidTarget(currentEnemy, base, attackRange.getValue())
                ? currentEnemy : selectTarget(base, attackRange.getValue());
        if (enemy == null) return false;
        mc.interactionManager.attackEntity(mc.player, enemy);
        mc.player.swingHand(Hand.MAIN_HAND);
        if (!attackCooldown19.getValue()) {
            scheduleClick();
        }
        return true;
    }

    private boolean canClick() {
        return attackCooldown19.getValue()
                ? mc.player != null && mc.player.getAttackCooldownProgress(0.5F) >= 1.0F
                : System.currentTimeMillis() >= nextClickAt;
    }
    private void scheduleClick() {
        final double low = Math.min(minCps.getValue(), maxCps.getValue());
        final double high = Math.max(minCps.getValue(), maxCps.getValue());
        final double cps = low + Math.random() * (high - low);
        nextClickAt = System.currentTimeMillis() + Math.max(1L, Math.round(1000.0D / cps));
    }

    private LivingEntity selectTarget(final Vec3d from, final double maximumDistance) {
        if (LocalDataWatch.getTargetList() == null) return null;
        final List<TargetLivingEntity> candidates = LocalDataWatch.getTargetList().collectTargets(targets.getTargetFlags(), TargetLivingEntity.class);
        candidates.removeIf(target -> target.isLocal() || !isValidTarget(target.getEntity(), from, maximumDistance));
        candidates.sort(switch (priority.getValue()) {
            case DISTANCE -> Comparator.comparingDouble(target -> squaredBoxDistance(target.getEntity(), from));
            case HEALTH -> Comparator.comparingDouble(TargetLivingEntity::getFullHealth);
            case HURT_TIME -> Comparator.comparingInt(target -> target.getEntity().hurtTime);
        });
        return candidates.isEmpty() ? null : candidates.getFirst().getEntity();
    }

    private boolean isValidTarget(final LivingEntity entity, final Vec3d from, final double maximumDistance) {
        if (!entity.isAlive() || !entity.isAttackable() || entity.hurtTime > hurtTime.getValue()) return false;
        if (AntiBotsModule.shouldFilter(entity) || TeamsModule.isTeammate(entity)) return false;
        if (LocalDataWatch.getFriendList().contains(entity.getName().getString().toUpperCase())) return false;
        if (!RotationUtility.isEntityInFOV(entity, fov.getValue().floatValue())) return false;
        return squaredBoxDistance(entity, from) <= maximumDistance * maximumDistance;
    }

    private static double squaredBoxDistance(final LivingEntity entity, final Vec3d point) {
        final Box box = entity.getBoundingBox();
        final double x = Math.max(box.minX, Math.min(point.x, box.maxX));
        final double y = Math.max(box.minY, Math.min(point.y, box.maxY));
        final double z = Math.max(box.minZ, Math.min(point.z, box.maxZ));
        return point.squaredDistanceTo(x, y, z);
    }

    /** Direct Java equivalent of LiquidBounce AStarPathBuilder: 22 direct edges, optional diagonals, 500 iterations. */
    private List<BlockPos> findPath(final BlockPos start, final BlockPos goal, final int maxCost, final boolean diagonal) {
        if (start.getSquaredDistance(goal) < 4.0D) return List.of();
        final PriorityQueue<PathNode> queue = new PriorityQueue<>(Comparator.comparingDouble(PathNode::f));
        final Map<BlockPos, Double> gScores = new HashMap<>();
        final Map<BlockPos, BlockPos> previous = new HashMap<>();
        gScores.put(start, 0.0D);
        queue.add(new PathNode(start, 0.0D, start.getSquaredDistance(goal)));
        int iterations = 0;
        while (!queue.isEmpty() && ++iterations <= 500) {
            final PathNode current = queue.poll();
            if (current.g() > gScores.getOrDefault(current.pos(), Double.POSITIVE_INFINITY) || current.g() > maxCost) continue;
            if (current.pos().getSquaredDistance(goal) < 4.0D) return reconstruct(start, current.pos(), previous);
            for (final BlockPos adjacent : neighbours(current.pos(), diagonal)) {
                if (!isPassable(adjacent)) continue;
                final double candidate = current.g() + current.pos().getSquaredDistance(adjacent);
                if (candidate > maxCost || candidate >= gScores.getOrDefault(adjacent, Double.POSITIVE_INFINITY)) continue;
                gScores.put(adjacent, candidate);
                previous.put(adjacent, current.pos());
                queue.add(new PathNode(adjacent, candidate, candidate + adjacent.getSquaredDistance(goal)));
            }
        }
        return List.of();
    }

    private List<BlockPos> neighbours(final BlockPos position, final boolean diagonal) {
        final List<BlockPos> result = new ArrayList<>(26);
        result.add(position.east()); result.add(position.west()); result.add(position.north()); result.add(position.south());
        for (int y = -9; y <= 9; y++) if (y != 0) result.add(position.add(0, y, 0));
        if (diagonal) {
            for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) {
                final BlockPos candidate = position.add(x, 0, z);
                if (isPassable(position.add(x, 0, 0)) && isPassable(position.add(0, 0, z))) result.add(candidate);
            }
        }
        return result;
    }

    private boolean isPassable(final BlockPos position) {
        return !mc.world.getBlockCollisions(mc.player, new Box(position.getX(), position.getY(), position.getZ(), position.getX() + 1, position.getY() + 2, position.getZ() + 1)).iterator().hasNext();
    }

    private Vec3d closestAttackPosition(final LivingEntity entity, final Vec3d origin) {
        final Box box = entity.getBoundingBox();
        final double x = Math.max(box.minX, Math.min(origin.x, box.maxX));
        final double y = Math.max(box.minY, Math.min(origin.y, box.maxY));
        final double z = Math.max(box.minZ, Math.min(origin.z, box.maxZ));
        return new Vec3d(x, y, z);
    }

    private boolean hasClearPath(final Vec3d origin, final Vec3d destination) {
        final Vec3d delta = destination.subtract(origin);
        final int steps = Math.max(1, (int) Math.ceil(delta.length() / 0.1D));
        for (int index = 1; index <= steps; index++) {
            final Vec3d step = delta.multiply(index / (double) steps);
            if (mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().offset(step)).iterator().hasNext()) {
                return false;
            }
        }
        return true;
    }

    private boolean hasMoveConflict() {
        final var moduleRepository = OraculusClient.getInstance().getModuleRepository();
        if (moduleRepository == null) {
            return false;
        }
        return isEnabled(moduleRepository.getModule(FlightModule.class))
                || isEnabled(moduleRepository.getModule(SpeedModule.class))
                || isEnabled(moduleRepository.getModule(LongJumpModule.class))
                // TargetStrafe is Beta-only, so use the repository's optional
                // lookup rather than linking the shared TpAura source to it.
                || isEnabled(moduleRepository.getOptionalModule("target_strafe"));
    }

    private static boolean isEnabled(final Module module) {
        return module != null && module.isEnabled();
    }

    private List<BlockPos> reconstruct(final BlockPos start, final BlockPos goal, final Map<BlockPos, BlockPos> previous) {
        final List<BlockPos> path = new ArrayList<>();
        BlockPos current = goal;
        path.add(current);
        while (!current.equals(start)) {
            current = previous.get(current);
            if (current == null) return List.of();
            path.add(current);
        }
        Collections.reverse(path);
        path.removeFirst();
        return path;
    }

    private void resetTransient() {
        moveTravelController.reset();
        serverPosition = null;
        pathCache = null;
        currentEnemy = null;
        state = TravelState.IDLE;
        stateTicks = 0;
    }
    private void reset() { resetTransient(); pathStart = BlockPos.ORIGIN; }

    private record PathNode(BlockPos pos, double g, double f) { }
    private enum TravelState { IDLE, OUTBOUND, STICKING, RETURNING }
    public enum Mode { A_STAR("AStar"), IMMEDIATE("Immediate"); private final String label; Mode(String label) { this.label = label; } @Override public String toString() { return label; } }
    public enum Priority { HURT_TIME("HurtTime"), DISTANCE("Distance"), HEALTH("Health"); private final String label; Priority(String label) { this.label = label; } @Override public String toString() { return label; } }
}

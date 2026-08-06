package wtf.oraculus.client.feature.module.impl.combat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.helper.impl.LocalDataWatch;
import wtf.oraculus.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.oraculus.client.feature.helper.impl.player.rotation.model.impl.LinearRotationModel;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.impl.movement.StuckModule;
import wtf.oraculus.client.feature.module.impl.utility.AntiBotsModule;
import wtf.oraculus.client.feature.module.impl.utility.BlinkModule;
import wtf.oraculus.client.feature.module.impl.world.scaffold.ScaffoldModule;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.bool.MultipleBooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.player.movement.PostMovementPacketEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.misc.time.Stopwatch;
import wtf.oraculus.utility.player.PlayerUtility;
import wtf.oraculus.utility.player.RotationUtility;

import java.util.Comparator;
import java.util.Optional;

import static wtf.oraculus.client.Constants.mc;

/** Naven-compatible automatic egg/snowball throwing. */
public final class AutoThrowModule extends Module {

    private static final double PROJECTILE_SPEED = 1.5D;
    private static final double PROJECTILE_GRAVITY = 0.03D;
    private static final int MAX_TURN_TICKS = 4;

    private final NumberProperty minDistance = new NumberProperty("Min Distance", 5.0D, 3.0D, 30.0D, 1.0D);
    private final NumberProperty maxDistance = new NumberProperty("Max Distance", 10.0D, 3.0D, 30.0D, 1.0D);
    private final NumberProperty delay = new NumberProperty("Delay", "ms", 500.0D, 50.0D, 2000.0D, 50.0D);
    private final NumberProperty fov = new NumberProperty("FOV", 90.0D, 15.0D, 180.0D, 5.0D);
    private final NumberProperty turnSpeed = new NumberProperty("Turn Speed", "deg/tick", 35.0D, 10.0D, 90.0D, 5.0D);
    private final MultipleBooleanProperty targets = new MultipleBooleanProperty("Target",
            new BooleanProperty("Player", true),
            new BooleanProperty("Invisible", true),
            new BooleanProperty("Animals", false),
            new BooleanProperty("Mobs", false));

    private final Stopwatch stopwatch = new Stopwatch();
    private ThrowPlan pendingPlan;
    private Vec2f pendingRotation;
    private int rotationTicks;
    private int restoreSlot = -1;

    public AutoThrowModule() {
        super("AutoThrow", "Automatically throws snowballs and eggs.", ModuleCategory.COMBAT);
        this.addProperties(this.minDistance, this.maxDistance, this.delay, this.fov, this.turnSpeed, this.targets);
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            this.clearPlan();
            return;
        }
        if (this.shouldPause()) {
            this.clearPlan();
            return;
        }

        if (this.pendingPlan != null) {
            this.updatePendingAim();
            return;
        }

        if (!this.stopwatch.hasTimeElapsed(this.delay.getValue().longValue())) {
            return;
        }

        final Optional<ThrowPlan> plan = this.findThrowPlan();
        final Optional<LivingEntity> target = this.findTarget();
        if (plan.isEmpty() || target.isEmpty() || mc.player.isUsingItem()) {
            return;
        }

        this.pendingRotation = this.getRotationToEntity(target.get());
        if (this.pendingRotation == null) {
            return;
        }

        this.pendingPlan = plan.get().withTarget(target.get().getId());
        this.rotationTicks = this.getRequiredRotationTicks(this.pendingRotation);
        this.stopwatch.reset();
    }

    @Subscribe
    public void onPostMovementPacket(final PostMovementPacketEvent event) {
        this.restoreSlot();
    }

    private void throwPending() {
        if (this.pendingPlan == null || mc.player == null || mc.interactionManager == null) {
            this.clearPlan();
            return;
        }

        final ThrowPlan plan = this.pendingPlan;
        if (!this.isPlanThrowable(plan)) {
            this.clearPlan();
            return;
        }
        if (plan.hand == Hand.MAIN_HAND && plan.slot != mc.player.getInventory().getSelectedSlot()) {
            this.restoreSlot = mc.player.getInventory().getSelectedSlot();
            mc.player.getInventory().setSelectedSlot(plan.slot);
        }

        mc.interactionManager.interactItem(mc.player, plan.hand);
        mc.player.swingHand(plan.hand);
        this.clearPlan();
    }

    private Optional<ThrowPlan> findThrowPlan() {
        if (this.isThrowable(mc.player.getOffHandStack())) {
            return Optional.of(new ThrowPlan(Hand.OFF_HAND, -1));
        }

        final int selected = mc.player.getInventory().getSelectedSlot();
        if (this.isThrowable(mc.player.getInventory().getStack(selected))) {
            return Optional.of(new ThrowPlan(Hand.MAIN_HAND, selected));
        }

        for (int slot = 0; slot < 9; slot++) {
            if (this.isThrowable(mc.player.getInventory().getStack(slot))) {
                return Optional.of(new ThrowPlan(Hand.MAIN_HAND, slot));
            }
        }
        return Optional.empty();
    }

    private Optional<LivingEntity> findTarget() {
        final double min = Math.min(this.minDistance.getValue(), this.maxDistance.getValue());
        final double max = Math.max(this.minDistance.getValue(), this.maxDistance.getValue());
        return mc.world.getNonSpectatingEntities(LivingEntity.class, mc.player.getBoundingBox().expand(max)).stream()
                .filter(entity -> entity != mc.player && entity.isAlive() && !entity.isSpectator())
                .filter(entity -> !AntiBotsModule.shouldFilter(entity))
                .filter(entity -> !TeamsModule.isTeammate(entity))
                .filter(entity -> !LocalDataWatch.getFriendList().contains(entity.getName().getString().toUpperCase()))
                .filter(entity -> !entity.isInvisibleTo(mc.player) || this.targets.getProperty("Invisible").getValue())
                .filter(this::isSelectedTargetType)
                .filter(mc.player::canSee)
                .filter(entity -> RotationUtility.isEntityInFOV(entity, this.fov.getValue().floatValue()))
                .filter(entity -> {
                    final double distance = this.getHorizontalDistance(entity);
                    return distance >= min && distance <= max;
                })
                .min(Comparator.comparingDouble(entity -> mc.player.squaredDistanceTo(entity)));
    }

    private boolean isSelectedTargetType(final LivingEntity entity) {
        if (entity instanceof PlayerEntity) {
            return this.targets.getProperty("Player").getValue();
        }
        if (entity instanceof AnimalEntity) {
            return this.targets.getProperty("Animals").getValue();
        }
        return (entity instanceof HostileEntity || entity instanceof MobEntity)
                && this.targets.getProperty("Mobs").getValue();
    }

    private Vec2f getRotationToEntity(final LivingEntity target) {
        final Vec3d start = mc.player.getEyePos();
        final Vec3d end = PlayerUtility.getClosestVectorToBoundingBox(start, target);
        final Vec3d difference = end.subtract(start);
        final double horizontalDistance = Math.hypot(difference.x, difference.z);
        if (horizontalDistance < 1.0E-4D) {
            return null;
        }

        final double speedSquared = PROJECTILE_SPEED * PROJECTILE_SPEED;
        final double discriminant = speedSquared * speedSquared
                - PROJECTILE_GRAVITY * (PROJECTILE_GRAVITY * horizontalDistance * horizontalDistance
                + 2.0D * difference.y * speedSquared);
        if (discriminant < 0.0D) {
            return null;
        }

        final double tangent = (speedSquared - Math.sqrt(discriminant)) / (PROJECTILE_GRAVITY * horizontalDistance);
        final float yaw = (float) Math.toDegrees(-Math.atan2(difference.x, difference.z));
        final float pitch = (float) -Math.toDegrees(Math.atan(tangent));
        if (Float.isNaN(yaw) || Float.isNaN(pitch)) {
            return null;
        }

        return RotationUtility.getVanillaRotation(new Vec2f(yaw, MathHelper.clamp(pitch, -90.0F, 90.0F)));
    }

    private double getHorizontalDistance(final LivingEntity entity) {
        return Math.hypot(entity.getX() - mc.player.getX(), entity.getZ() - mc.player.getZ());
    }

    private boolean isThrowable(final ItemStack stack) {
        return !stack.isEmpty() && (stack.isOf(Items.EGG) || stack.isOf(Items.SNOWBALL));
    }

    private boolean shouldPause() {
        if (mc.currentScreen != null || mc.getOverlay() != null) {
            return true;
        }

        final var repository = OraculusClient.getInstance().getModuleRepository();
        final ScaffoldModule scaffold = repository.getModule(ScaffoldModule.class);
        final StuckModule stuck = repository.getModule(StuckModule.class);
        final BlinkModule blink = repository.getModule(BlinkModule.class);
        return scaffold != null && scaffold.isEnabled()
                || stuck != null && stuck.isEnabled()
                || blink != null && blink.isEnabled();
    }

    private void updatePendingAim() {
        final LivingEntity target = this.getPendingTarget();
        if (target == null || !this.isPlanThrowable(this.pendingPlan)) {
            this.clearPlan();
            return;
        }

        this.pendingRotation = this.getRotationToEntity(target);
        if (this.pendingRotation == null) {
            this.clearPlan();
            return;
        }

        RotationHelper.getHandler().rotate(
                this.pendingRotation,
                new LinearRotationModel(this.turnSpeed.getValue())
        );
        if (--this.rotationTicks <= 0) {
            this.throwPending();
        }
    }

    private LivingEntity getPendingTarget() {
        if (this.pendingPlan == null || mc.world == null) {
            return null;
        }

        final Entity entity = mc.world.getEntityById(this.pendingPlan.targetId());
        if (!(entity instanceof LivingEntity target)
                || target == mc.player
                || !target.isAlive()
                || target.isSpectator()
                || !mc.player.canSee(target)
                || !this.isSelectedTargetType(target)
                || AntiBotsModule.shouldFilter(target)
                || TeamsModule.isTeammate(target)
                || LocalDataWatch.getFriendList().contains(target.getName().getString().toUpperCase())) {
            return null;
        }

        final double min = Math.min(this.minDistance.getValue(), this.maxDistance.getValue());
        final double max = Math.max(this.minDistance.getValue(), this.maxDistance.getValue());
        final double distance = this.getHorizontalDistance(target);
        return distance >= min && distance <= max ? target : null;
    }

    private boolean isPlanThrowable(final ThrowPlan plan) {
        if (plan == null || mc.player == null) {
            return false;
        }

        if (plan.hand == Hand.OFF_HAND) {
            return this.isThrowable(mc.player.getOffHandStack());
        }

        return plan.slot >= 0 && plan.slot < 9
                && this.isThrowable(mc.player.getInventory().getStack(plan.slot));
    }

    private int getRequiredRotationTicks(final Vec2f rotation) {
        final Vec2f currentRotation = RotationUtility.getRotation();
        final float yawDifference = Math.abs(MathHelper.wrapDegrees(rotation.x - currentRotation.x));
        final float pitchDifference = Math.abs(rotation.y - currentRotation.y);
        final double difference = Math.hypot(yawDifference, pitchDifference);
        final int ticks = (int) Math.ceil(difference / this.turnSpeed.getValue());
        return Math.max(1, Math.min(MAX_TURN_TICKS, ticks));
    }

    private void restoreSlot() {
        if (mc.player != null && this.restoreSlot >= 0 && this.restoreSlot < 9) {
            mc.player.getInventory().setSelectedSlot(this.restoreSlot);
        }
        this.restoreSlot = -1;
    }

    private void clearPlan() {
        this.pendingPlan = null;
        this.pendingRotation = null;
        this.rotationTicks = 0;
    }

    @Override
    public String getSuffix() {
        final double min = Math.min(this.minDistance.getValue(), this.maxDistance.getValue());
        final double max = Math.max(this.minDistance.getValue(), this.maxDistance.getValue());
        return min + " - " + max;
    }

    @Override
    protected void onEnable() {
        this.clearPlan();
        this.restoreSlot();
        this.stopwatch.reset();
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        this.restoreSlot();
        this.clearPlan();
        super.onDisable();
    }

    private record ThrowPlan(Hand hand, int slot, int targetId) {
        private ThrowPlan(final Hand hand, final int slot) {
            this(hand, slot, -1);
        }

        private ThrowPlan withTarget(final int targetId) {
            return new ThrowPlan(this.hand, this.slot, targetId);
        }
    }
}

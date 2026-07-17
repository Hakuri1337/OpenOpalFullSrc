package wtf.opal.client.feature.module.impl.combat;

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
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.helper.impl.LocalDataWatch;
import wtf.opal.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.opal.client.feature.helper.impl.player.rotation.model.impl.InstantRotationModel;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.impl.movement.StuckModule;
import wtf.opal.client.feature.module.impl.utility.AntiBotsModule;
import wtf.opal.client.feature.module.impl.utility.BlinkModule;
import wtf.opal.client.feature.module.impl.world.blockfly.BlockFlyModule;
import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.client.feature.module.property.impl.bool.MultipleBooleanProperty;
import wtf.opal.client.feature.module.property.impl.number.NumberProperty;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.impl.game.player.movement.PostMovementPacketEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.misc.time.Stopwatch;

import java.util.Comparator;
import java.util.Optional;

import static wtf.opal.client.Constants.mc;

/** Naven-compatible automatic egg/snowball throwing. */
public final class AutoThrowModule extends Module {

    private final NumberProperty minDistance = new NumberProperty("Min Distance", 5.0D, 3.0D, 30.0D, 1.0D);
    private final NumberProperty maxDistance = new NumberProperty("Max Distance", 10.0D, 3.0D, 30.0D, 1.0D);
    private final NumberProperty delay = new NumberProperty("Delay", "ms", 500.0D, 50.0D, 2000.0D, 50.0D);
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
        this.addProperties(this.minDistance, this.maxDistance, this.delay, this.targets);
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

        if (this.pendingPlan != null && this.rotationTicks > 0) {
            RotationHelper.getHandler().rotate(this.pendingRotation, InstantRotationModel.INSTANCE);
            if (--this.rotationTicks == 0) {
                this.throwPending();
            }
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

        this.pendingPlan = plan.get();
        this.pendingRotation = this.getRotationToEntity(target.get());
        this.rotationTicks = 2;
        RotationHelper.getHandler().rotate(this.pendingRotation, InstantRotationModel.INSTANCE);
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
        final Vec3d velocity = target.getVelocity();
        final double targetY = target.getY() + target.getHeight() * 0.55D;
        double time = 0.0D;
        for (int i = 0; i < 3; i++) {
            final double dx = target.getX() + velocity.x * time - mc.player.getX();
            final double dz = target.getZ() + velocity.z * time - mc.player.getZ();
            time = Math.sqrt(dx * dx + dz * dz) / 0.6D;
        }

        final double x = target.getX() + velocity.x * time - mc.player.getX();
        final double y = targetY + velocity.y * time - mc.player.getEyeY();
        final double z = target.getZ() + velocity.z * time - mc.player.getZ();
        final double horizontal = Math.sqrt(x * x + z * z);
        final float yaw = (float) Math.toDegrees(Math.atan2(z, x)) - 90.0F;
        final float pitch = -this.getLowArcPitch((float) horizontal, (float) y, 0.6F, 0.006F);
        return new Vec2f(yaw, MathHelper.clamp(pitch, -90.0F, 90.0F));
    }

    private float getLowArcPitch(final float distance, final float height, final float velocity, final float gravity) {
        final float velocitySquared = velocity * velocity;
        final float root = velocitySquared * velocitySquared - gravity * (gravity * distance * distance + 2.0F * height * velocitySquared);
        if (root <= 0.0F) {
            return (float) Math.toDegrees(Math.atan2(height, distance));
        }
        return (float) Math.toDegrees(Math.atan((velocitySquared - Math.sqrt(root)) / (gravity * distance)));
    }

    private double getHorizontalDistance(final LivingEntity entity) {
        return Math.hypot(entity.getX() - mc.player.getX(), entity.getZ() - mc.player.getZ());
    }

    private boolean isThrowable(final ItemStack stack) {
        return !stack.isEmpty() && (stack.isOf(Items.EGG) || stack.isOf(Items.SNOWBALL));
    }

    private boolean shouldPause() {
        final var repository = OpalClient.getInstance().getModuleRepository();
        final BlockFlyModule blockFly = repository.getModule(BlockFlyModule.class);
        final StuckModule stuck = repository.getModule(StuckModule.class);
        final BlinkModule blink = repository.getModule(BlinkModule.class);
        return blockFly != null && blockFly.isEnabled()
                || stuck != null && stuck.isEnabled()
                || blink != null && blink.isEnabled();
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

    private record ThrowPlan(Hand hand, int slot) {
    }
}

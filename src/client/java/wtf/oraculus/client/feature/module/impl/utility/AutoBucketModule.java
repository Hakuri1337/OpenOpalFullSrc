package wtf.oraculus.client.feature.module.impl.utility;

import net.minecraft.block.BlockState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import wtf.oraculus.client.feature.helper.impl.player.rotation.RotationHelper;
import wtf.oraculus.client.feature.helper.impl.player.rotation.model.impl.InstantRotationModel;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.helper.impl.player.packet.blockage.impl.OutboundNetworkBlockage;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.event.impl.game.PostGameTickEvent;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.input.MoveInputEvent;
import wtf.oraculus.event.impl.game.player.movement.PreMovementPacketEvent;
import wtf.oraculus.event.impl.game.player.movement.PostMovementPacketEvent;
import wtf.oraculus.event.impl.game.packet.ReceivePacketEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.mixin.ClientPlayerInteractionManagerAccessor;
import wtf.oraculus.utility.player.RotationUtility;

import static wtf.oraculus.client.Constants.mc;

public final class AutoBucketModule extends Module {

    private static final float BUCKET_ROTATION_READY_DIFFERENCE = 3.5F;
    private static final float MAX_BUCKET_PITCH = 89.5F;
    private static final float MAX_PICKUP_PITCH = 90.0F;
    private static final int PICKUP_PREPARE_TICKS = 3;
    private static final int PICKUP_CONFIRM_TICKS = 10;
    private static final int PICKUP_MAX_ATTEMPTS = 24;
    private static final int PICKUP_SOURCE_APPEAR_WAIT_TICKS = 20;
    private static final int PICKUP_BLOCKED_RETRY_TICKS = 20;
    private static final int WATER_PLACE_CONFIRM_TICKS = 8;
    private static final int PLAYER_SCREEN_HOTBAR_START = 36;

    private final BooleanProperty mlg = new BooleanProperty("MLG", true);
    private final NumberProperty fallDistance = new NumberProperty("Fall Distance", 3.0D, 1.0D, 10.0D, 0.1D)
            .hideIf(() -> !this.mlg.getValue());
    private final NumberProperty predictTicks = new NumberProperty("Predict Ticks", "ticks", 2.0D, 1.0D, 5.0D, 1.0D)
            .hideIf(() -> !this.mlg.getValue());
    private final BooleanProperty solidCheck = new BooleanProperty("Solid Check", true)
            .hideIf(() -> !this.mlg.getValue());
    private final BooleanProperty recovery = new BooleanProperty("Recovery", true)
            .hideIf(() -> !this.mlg.getValue());
    private final NumberProperty pickupDelay = new NumberProperty(
            "Pickup Delay", "ms", 100.0D, 5.0D, 500.0D, 5.0D
    ).hideIf(() -> !this.mlg.getValue() || !this.recovery.getValue());
    private final BooleanProperty extinguish = new BooleanProperty("Extinguish", true);

    private int restoreSlot = -1;

    private float accumulatedFall;
    private double lastY;
    private boolean waterPlaced;
    private PickupTask pickupTask;
    private BlockHitResult pendingMlgHit;
    private Vec2f pendingMlgRotation;
    private BlockPos pendingWaterPos;
    private int pendingWaterConfirmTicks;
    private boolean waterPlacementConfirmed;
    private float lastRequestedYaw = Float.NaN;
    private float lastRequestedPitch = Float.NaN;
    private float lastSentYaw = Float.NaN;
    private float lastSentPitch = Float.NaN;
    private int postPlaceCooldown;
    private int postActionCooldown;
    private boolean rotationRequestedThisTick;
    private int fallStateAge = Integer.MIN_VALUE;

    private int helperCooldownTicks;

    public AutoBucketModule() {
        super("AutoBucket", "Handles water bucket MLG and helper recovery.", ModuleCategory.UTILITY);
        this.addProperties(this.mlg, this.fallDistance, this.predictTicks, this.solidCheck,
                this.recovery, this.pickupDelay, this.extinguish);
    }

    public boolean isEmergencyActive() {
        return this.pickupTask != null
                || this.waterPlaced
                || this.postActionCooldown > 0
                || this.isMlgFallEmergency();
    }

    @Subscribe(priority = -20)
    public void onPreGameTick(final PreGameTickEvent event) {
        this.rotationRequestedThisTick = false;
        if (mc.player == null || mc.world == null || mc.interactionManager == null
                || mc.player.isSpectator() || mc.player.getAbilities().allowFlying
                || mc.player.getAbilities().flying || mc.currentScreen != null) {
            this.restoreActionSlot();
            this.resetState();
            return;
        }

        this.updateFallStateOnce();
        this.cancelPickupTaskDisabledBySettings();
        this.preemptNonMlgPickupForEmergency();
        final PickupTask task = this.pickupTask;
        if (task != null && task.expectedWaterPos != null) {
            if (!task.waitForSafeLanding || this.isSafeToRecoverMlgWater()) {
                final BlockPos source = this.findRecoverableWaterSource(task.expectedWaterPos, 2);
                if (source != null) {
                    task.expectedWaterPos = source;
                    this.requestRotation(this.getPickupRotation(source));
                }
            }
            return;
        }

        final double downwardSpeed = Math.max(0.0D, -mc.player.getVelocity().y);
        final double effectiveFallDistance = Math.max(this.accumulatedFall, mc.player.fallDistance);
        if (this.mlg.getValue()
                && effectiveFallDistance + downwardSpeed >= this.fallDistance.getValue().floatValue()
                && downwardSpeed > 0.08D && this.findWaterBucketSlot() != -1
                && this.updatePendingMlgPlacement()
                && this.pendingMlgRotation != null
                && this.pendingMlgDistance() <= this.getMlgPlaceDistance() + downwardSpeed) {
            this.requestRotation(this.pendingMlgRotation);
            return;
        }

        if (this.extinguish.getValue() && this.shouldExtinguish()
                && this.helperCooldownTicks <= 0 && this.findWaterBucketSlot() != -1) {
            final BlockHitResult hit = this.findSelfBucketPlacementHit();
            if (hit != null) {
                this.requestRotation(this.getSafeRotationTo(
                        hit.getPos(), mc.player.getYaw(), MAX_BUCKET_PITCH
                ));
            }
        }
    }

    @Subscribe(priority = -200)
    public void onPreMovementPacket(final PreMovementPacketEvent event) {
        if (!this.rotationRequestedThisTick
                || Float.isNaN(this.lastRequestedYaw)
                || Float.isNaN(this.lastRequestedPitch)) {
            return;
        }
        event.setYaw(this.lastRequestedYaw);
        event.setPitch(this.lastRequestedPitch);
    }

    @Subscribe
    public void onPostGameTick(final PostGameTickEvent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            this.restoreActionSlot();
            this.resetState();
            return;
        }

        if (mc.player.isSpectator() || mc.player.getAbilities().allowFlying
                || mc.player.getAbilities().flying || mc.currentScreen != null) {
            this.restoreActionSlot();
            this.resetState();
            return;
        }

        this.updateFallStateOnce();
        this.updateWaterPlacementConfirmation();
        this.tickCooldowns();
        this.cancelPickupTaskDisabledBySettings();
        this.preemptNonMlgPickupForEmergency();

        if (this.pickupTask != null) {
            this.handlePickupTask();
            this.restoreSlotWhenIdle();
            return;
        }

        final boolean mlgConsumedTick = this.handleMlgTick();
        if (mlgConsumedTick) {
            this.restoreSlotWhenIdle();
            return;
        }

        this.handleHelperTick();
        this.restoreSlotWhenIdle();
    }

    @Subscribe
    public void onMoveInput(final MoveInputEvent event) {
        if (this.postActionCooldown > 0 || this.hasPickupTask(PickupCause.MLG)) {
            event.setSneak(false);
        }
    }

    @Subscribe
    public void onPostMovementPacket(final PostMovementPacketEvent event) {
        this.lastSentYaw = event.getYaw();
        this.lastSentPitch = event.getPitch();
    }

    @Subscribe
    public void onReceivePacket(final ReceivePacketEvent event) {
        final PickupTask task = this.pickupTask;
        if (task == null || task.bucketSlot < 0 || mc.player == null) {
            return;
        }

        if (this.confirmsWaterBucket(event.getPacket(), task)) {
            task.serverConfirmed = true;
        }
    }

    private boolean handleMlgTick() {
        if (!this.mlg.getValue()) {
            this.resetMlgState();
            return false;
        }

        if (mc.player.isOnGround() || this.accumulatedFall <= 0.0F) {
            this.waterPlaced = false;
            this.pendingWaterPos = null;
            this.pendingWaterConfirmTicks = 0;
            this.waterPlacementConfirmed = false;
            this.clearPendingMlgPlacement();
        }

        if (this.tryFillWaterBucket()) {
            return true;
        }

        if (this.waterPlaced) {
            return true;
        }

        if (this.postPlaceCooldown > 0 || this.postActionCooldown > 0) {
            return true;
        }

        final double effectiveFallDistance = Math.max(this.accumulatedFall, mc.player.fallDistance);
        if (effectiveFallDistance < this.fallDistance.getValue().floatValue() || mc.player.getVelocity().y >= -0.08D) {
            return false;
        }

        final int waterSlot = this.findWaterBucketSlot();
        if (waterSlot == -1) {
            return true;
        }

        if (!this.updatePendingMlgPlacement()) {
            return true;
        }

        final double remainingDistance = this.pendingMlgDistance();
        if (!(remainingDistance > 0.0D && remainingDistance <= this.getMlgPlaceDistance())) {
            this.clearPendingMlgPlacement();
            return true;
        }

        this.placeMlgWaterBucket(waterSlot, true);
        return true;
    }

    private void handleHelperTick() {
        if (!this.extinguish.getValue()) {
            this.clearPickupTask(PickupCause.EXTINGUISH);
            return;
        }

        if (!this.shouldExtinguish() || this.helperCooldownTicks > 0) {
            return;
        }

        final int waterSlot = this.findWaterBucketSlot();
        if (waterSlot == -1) {
            return;
        }

        final BlockHitResult targetHit = this.findSelfBucketPlacementHit();
        if (targetHit == null) {
            return;
        }
        final Vec2f rotation = this.getSafeRotationTo(
                targetHit.getPos(), mc.player.getYaw(), MAX_BUCKET_PITCH
        );
        this.requestRotation(rotation);
        if (!this.isRotationReady(rotation)) {
            return;
        }

        final BlockHitResult verifiedHit = this.getVerifiedSolidHit(rotation, targetHit);
        if (verifiedHit == null) {
            return;
        }
        final BlockPos placedWaterPos = verifiedHit.getBlockPos().offset(verifiedHit.getSide());

        if (!this.isServerRotationReady(rotation)) {
            return;
        }
        if (!this.selectActionSlot(waterSlot, Items.WATER_BUCKET)) {
            return;
        }
        if (!this.useItem(Items.WATER_BUCKET)) {
            return;
        }

        this.waterPlaced = true;
        this.pendingWaterPos = placedWaterPos.toImmutable();
        this.pendingWaterConfirmTicks = WATER_PLACE_CONFIRM_TICKS;
        this.waterPlacementConfirmed = false;
        this.beginPickup(placedWaterPos, PickupCause.EXTINGUISH, false, waterSlot);
        this.helperCooldownTicks = 8;
    }

    private void updateFallStateOnce() {
        if (mc.player == null || this.fallStateAge == mc.player.age) {
            return;
        }
        this.fallStateAge = mc.player.age;
        if (mc.player.isOnGround()
                || mc.player.isTouchingWater()
                || mc.player.isInLava()
                || mc.player.isClimbing()) {
            this.accumulatedFall = 0.0F;
        } else {
            final double deltaY = mc.player.getY() - this.lastY;
            if (deltaY < 0.0D) {
                this.accumulatedFall -= (float) deltaY;
            }
        }
        this.lastY = mc.player.getY();
    }

    private void tickCooldowns() {
        if (this.postPlaceCooldown > 0) {
            this.postPlaceCooldown--;
        }
        if (this.postActionCooldown > 0) {
            this.postActionCooldown--;
        }
        if (this.helperCooldownTicks > 0) {
            this.helperCooldownTicks--;
        }
    }

    private void updateWaterPlacementConfirmation() {
        if (this.pendingWaterPos == null || mc.world == null) {
            return;
        }
        if (this.isWaterSource(this.pendingWaterPos)) {
            this.waterPlacementConfirmed = true;
            this.pendingWaterConfirmTicks = 0;
            return;
        }
        if (this.waterPlacementConfirmed) {
            return;
        }
        if (this.pendingWaterConfirmTicks > 0) {
            this.pendingWaterConfirmTicks--;
            return;
        }

        this.waterPlaced = false;
        this.pendingWaterPos = null;
        if (this.pickupTask != null && this.pickupTask.cause != PickupCause.FILL) {
            this.clearPickupTask();
        }
        this.postPlaceCooldown = 0;
    }

    private void restoreSlotWhenIdle() {
        if (this.restoreSlot == -1
                || this.pickupTask != null
                || this.postPlaceCooldown > 0
                || this.postActionCooldown > 0) {
            return;
        }
        this.restoreActionSlot();
    }

    private boolean tryFillWaterBucket() {
        if (this.waterPlaced
                || this.pickupTask != null
                || this.postPlaceCooldown > 0
                || this.postActionCooldown > 0
                || this.accumulatedFall > 0.5F
                || !this.isSafeToRecoverMlgWater()
                || this.findWaterBucketSlot() != -1) {
            return false;
        }

        final int emptySlot = this.findEmptyBucketSlot();
        if (emptySlot == -1) {
            return false;
        }

        final BlockPos waterPos = this.findNearestWaterSource();
        if (waterPos == null) {
            return false;
        }

        this.beginPickup(waterPos, PickupCause.FILL, false, emptySlot);
        return true;
    }

    private boolean isMlgFallEmergency() {
        return this.mlg.getValue()
                && mc.player != null
                && !mc.player.isOnGround()
                && !mc.player.isTouchingWater()
                && !mc.player.isSwimming()
                && mc.player.getVelocity().y < -0.08D
                && Math.max(this.accumulatedFall, mc.player.fallDistance)
                >= this.fallDistance.getValue().floatValue();
    }

    private void preemptNonMlgPickupForEmergency() {
        if (this.pickupTask == null
                || this.pickupTask.cause == PickupCause.MLG
                || !this.isMlgFallEmergency()
                || this.findWaterBucketSlot() == -1) {
            return;
        }

        this.clearPickupTask();
        this.waterPlaced = false;
        this.pendingWaterPos = null;
        this.pendingWaterConfirmTicks = 0;
        this.waterPlacementConfirmed = false;
        this.postPlaceCooldown = 0;
        this.postActionCooldown = 0;
    }

    private void cancelPickupTaskDisabledBySettings() {
        if (this.pickupTask == null) {
            return;
        }
        if (this.pickupTask.cause == PickupCause.MLG
                && (!this.mlg.getValue() || !this.recovery.getValue())) {
            this.clearPickupTask();
        } else if (this.pickupTask.cause == PickupCause.EXTINGUISH
                && !this.extinguish.getValue()) {
            this.clearPickupTask();
        }
    }

    private void handlePickupTask() {
        final PickupTask task = this.pickupTask;
        if (task == null || mc.player == null || mc.world == null) {
            this.clearPickupTask();
            return;
        }

        if (task.serverConfirmed
                || (task.waterBucketCountBefore >= 0
                && this.countWaterBuckets() > task.waterBucketCountBefore)) {
            this.finishPickupTask();
            return;
        }

        if (task.confirmTicks > 0) {
            task.confirmTicks--;
            if (task.confirmTicks == 0) {
                this.retryPickupTask();
            }
            return;
        }

        if (task.delayTicks > 0) {
            task.delayTicks--;
            return;
        }

        if (System.nanoTime() < task.rightClickAtNanos) {
            return;
        }

        if (task.waitForSafeLanding && !this.isSafeToRecoverMlgWater()) {
            if (--task.landingWaitTicks <= 0) {
                this.clearPickupTask();
            }
            return;
        }

        final BlockPos waterSource = this.findRecoverableWaterSource(task.expectedWaterPos, 2);
        if (waterSource == null) {
            // The place interaction can be accepted before the water source is
            // visible in the local world. Do not spend all recovery attempts
            // while waiting for the server block update to arrive.
            if (++task.sourceAppearWaitTicks >= PICKUP_SOURCE_APPEAR_WAIT_TICKS) {
                this.retryPickupTask();
            }
            return;
        }

        task.sourceAppearWaitTicks = 0;
        task.expectedWaterPos = waterSource.toImmutable();
        final Vec2f rotation = this.getPickupRotation(task.expectedWaterPos);
        this.requestRotation(rotation);
        if (!this.isRotationReady(rotation)) {
            if (++task.rotationWaitTicks >= 8) {
                this.retryPickupTask();
            }
            return;
        }
        task.rotationWaitTicks = 0;

        final BlockHitResult hit = this.raycastFluid(this.getCurrentRotation(), mc.player.getBlockInteractionRange());
        if (hit.getType() == HitResult.Type.MISS || !this.isWaterSource(hit.getBlockPos())
                || hit.getBlockPos().getSquaredDistance(task.expectedWaterPos) > 4.0D) {
            this.retryPickupTask();
            return;
        }

        task.expectedWaterPos = hit.getBlockPos().toImmutable();
        if (this.isPickupInteractionBlocked()) {
            if (++task.blockedWaitTicks >= PICKUP_BLOCKED_RETRY_TICKS) {
                this.retryPickupTask();
            }
            return;
        }
        task.blockedWaitTicks = 0;

        final int bucketSlot = this.findEmptyBucketSlot(task.preferredSlot);
        if (bucketSlot == -1) {
            this.retryPickupTask();
            return;
        }

        if (!this.selectActionSlot(bucketSlot, Items.BUCKET)) {
            this.retryPickupTask();
            return;
        }

        final int waterBucketCountBefore = this.countWaterBuckets();
        final boolean[] waterSlotsBefore = this.snapshotWaterBucketSlots();
        if (!this.isServerRotationReady(rotation)) {
            return;
        }
        if (this.pickupWaterSource(rotation, task.expectedWaterPos)) {
            task.bucketSlot = bucketSlot;
            task.waterBucketCountBefore = waterBucketCountBefore;
            task.waterSlotsBefore = waterSlotsBefore;
            task.confirmTicks = PICKUP_CONFIRM_TICKS;
            task.serverConfirmed = false;
        } else {
            this.retryPickupTask();
        }
    }

    private void placeMlgWaterBucket(final int slot, final boolean markPlaced) {
        if (this.pendingMlgRotation == null || this.pendingMlgHit == null
                || !this.isUsableMlgHit(this.pendingMlgHit)) {
            return;
        }

        final Vec2f rotation = this.pendingMlgRotation;
        this.requestRotation(rotation);
        if (!this.isRotationReady(rotation)) {
            return;
        }

        final BlockHitResult verifiedHit = this.getVerifiedSolidHit(rotation, this.pendingMlgHit);
        if (verifiedHit == null) {
            this.clearPendingMlgPlacement();
            return;
        }
        final BlockPos placedPos = verifiedHit.getBlockPos().offset(verifiedHit.getSide());

        if (!this.isServerRotationReady(rotation)) {
            return;
        }
        if (!this.selectActionSlot(slot, Items.WATER_BUCKET)) {
            return;
        }
        if (!this.useItem(Items.WATER_BUCKET)) {
            return;
        }

        if (markPlaced) {
            this.waterPlaced = true;
            this.pendingWaterPos = placedPos.toImmutable();
            this.pendingWaterConfirmTicks = WATER_PLACE_CONFIRM_TICKS;
            this.waterPlacementConfirmed = false;
            this.postPlaceCooldown = Math.max(this.postPlaceCooldown, 2);
        }

        if (this.recovery.getValue()) {
            this.beginPickup(placedPos, PickupCause.MLG, true, slot);
        }
        this.clearPendingMlgPlacement();
    }

    private boolean shouldExtinguish() {
        if (mc.player == null) {
            return false;
        }
        if (this.mlg.getValue() && this.accumulatedFall > 0.5F) {
            return false;
        }
        if (mc.player.isTouchingWater() || mc.player.isSwimming()) {
            return false;
        }
        return mc.player.isOnFire();
    }

    private int ticksUntilGround() {
        if (mc.player.getVelocity().y >= 0.0D) {
            return 999;
        }

        final double distance = this.distanceToGround(30.0D);
        if (distance == Double.POSITIVE_INFINITY) {
            return 999;
        }

        double simulatedDrop = 0.0D;
        double simulatedVelocity = mc.player.getVelocity().y;
        for (int i = 1; i <= 20; i++) {
            simulatedDrop += simulatedVelocity;
            simulatedVelocity = (simulatedVelocity - 0.08D) * 0.98D;
            if (Math.abs(simulatedDrop) >= distance) {
                return i;
            }
        }
        return 999;
    }

    private double distanceToGround(final double maxDistance) {
        final Vec3d start = new Vec3d(mc.player.getX(), mc.player.getBoundingBox().minY, mc.player.getZ());
        final Vec3d end = start.add(0.0D, -maxDistance, 0.0D);
        final BlockHitResult hit = mc.world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));

        if (hit.getType() == HitResult.Type.MISS) {
            return Double.POSITIVE_INFINITY;
        }
        return start.y - hit.getPos().y;
    }

    private BlockPos findNearestWaterSource() {
        final BlockPos playerPos = mc.player.getBlockPos();
        BlockPos closest = null;
        double closestDistance = Double.POSITIVE_INFINITY;

        for (int y = -2; y <= 2; y++) {
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    final BlockPos candidate = playerPos.add(x, y, z);
                    if (!this.isWaterSource(candidate)) {
                        continue;
                    }

                    final double distance = mc.player.squaredDistanceTo(candidate.getX() + 0.5D, candidate.getY() + 0.5D, candidate.getZ() + 0.5D);
                    if (distance >= closestDistance) {
                        continue;
                    }

                    final Vec2f rotation = this.getPickupRotation(candidate);
                    final BlockHitResult hit = this.raycastFluid(
                            rotation, mc.player.getBlockInteractionRange()
                    );
                    if (hit.getType() == HitResult.Type.MISS || !hit.getBlockPos().equals(candidate)) {
                        continue;
                    }

                    closest = candidate.toImmutable();
                    closestDistance = distance;
                }
            }
        }

        return closest;
    }

    private boolean updatePendingMlgPlacement() {
        if (this.pendingMlgHit != null && this.pendingMlgRotation != null
                && this.isUsableMlgHit(this.pendingMlgHit)) {
            final Vec2f refreshedRotation = this.getSafeRotationTo(
                    this.pendingMlgHit.getPos(), mc.player.getYaw(), MAX_BUCKET_PITCH
            );
            final BlockHitResult refreshedHit = this.raycastSolid(
                    refreshedRotation, mc.player.getBlockInteractionRange()
            );
            if (this.sameBlockFace(refreshedHit, this.pendingMlgHit)
                    && this.isUsableMlgHit(refreshedHit)) {
                this.pendingMlgHit = refreshedHit;
                this.pendingMlgRotation = refreshedRotation;
                return true;
            }
        }

        final BlockHitResult hit = this.findMlgPlacementHit();
        if (hit == null) {
            this.clearPendingMlgPlacement();
            return false;
        }

        this.pendingMlgHit = hit;
        this.pendingMlgRotation = this.getSafeRotationTo(
                hit.getPos(), mc.player.getYaw(), MAX_BUCKET_PITCH
        );
        return true;
    }

    private BlockHitResult findMlgPlacementHit() {
        final Vec3d eyePos = mc.player.getEyePos();
        final int predictedTicks = Math.max(1, Math.min(
                this.predictTicks.getValue().intValue(),
                this.ticksUntilGround()
        ));
        final Vec3d velocity = mc.player.getVelocity();
        final Vec3d predictedCenter = mc.player.getEntityPos().add(
                velocity.x * predictedTicks, 0.0D, velocity.z * predictedTicks
        );
        final double maxDistance = Math.min(
                6.0D,
                mc.player.getBlockInteractionRange() + mc.player.getEyeHeight(mc.player.getPose())
        );
        BlockHitResult bestHit = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (final double predictionFactor : new double[]{0.0D, 0.5D, 1.0D}) {
            final double centerX = MathHelper.lerp(
                    predictionFactor, mc.player.getX(), predictedCenter.x
            );
            final double centerZ = MathHelper.lerp(
                    predictionFactor, mc.player.getZ(), predictedCenter.z
            );
            for (double x = -0.4D; x <= 0.4D; x += 0.4D) {
                for (double z = -0.4D; z <= 0.4D; z += 0.4D) {
                    final Vec3d start = new Vec3d(centerX + x, eyePos.y, centerZ + z);
                    final BlockHitResult verticalHit = mc.world.raycast(new RaycastContext(
                            start,
                            start.add(0.0D, -maxDistance, 0.0D),
                            RaycastContext.ShapeType.COLLIDER,
                            RaycastContext.FluidHandling.NONE,
                            mc.player
                    ));
                    if (verticalHit.getType() == HitResult.Type.MISS
                            || !this.isUsableMlgHit(verticalHit)) {
                        continue;
                    }

                    final Vec2f rotation = this.getSafeRotationTo(
                            verticalHit.getPos(), mc.player.getYaw(), MAX_BUCKET_PITCH
                    );
                    final BlockHitResult visibleHit = this.raycastSolid(
                            rotation, mc.player.getBlockInteractionRange()
                    );
                    if (!this.sameBlockFace(visibleHit, verticalHit)
                            || !this.isUsableMlgHit(visibleHit)) {
                        continue;
                    }

                    final double horizontal = visibleHit.getPos().squaredDistanceTo(
                            predictedCenter.x, visibleHit.getPos().y, predictedCenter.z
                    );
                    final double vertical = Math.abs(
                            mc.player.getBoundingBox().minY - visibleHit.getPos().y
                    );
                    final double score = horizontal + vertical * 0.02D;
                    if (score < bestScore) {
                        bestScore = score;
                        bestHit = visibleHit;
                    }
                }
            }
        }

        return bestHit;
    }

    private BlockHitResult findSelfBucketPlacementHit() {
        final Vec3d eyePos = mc.player.getEyePos();
        final Vec3d playerCenter = mc.player.getEntityPos();
        BlockHitResult bestHit = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (double x = -0.35D; x <= 0.35D; x += 0.35D) {
            for (double z = -0.35D; z <= 0.35D; z += 0.35D) {
                final Vec3d start = eyePos.add(x, 0.0D, z);
                final Vec3d end = start.add(0.0D, -4.5D, 0.0D);
                final BlockHitResult hit = mc.world.raycast(new RaycastContext(
                        start,
                        end,
                        RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE,
                        mc.player
                ));

                if (hit.getType() == HitResult.Type.MISS || !this.isUsableMlgHit(hit)) {
                    continue;
                }

                final double score = hit.getPos().squaredDistanceTo(playerCenter.x, hit.getPos().y, playerCenter.z);
                if (score < bestScore) {
                    bestScore = score;
                    bestHit = hit;
                }
            }
        }

        return bestHit;
    }

    private boolean isUsableMlgHit(final BlockHitResult hit) {
        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            return false;
        }
        final BlockPos placedPos = hit.getBlockPos().offset(hit.getSide());
        final BlockState supportState = mc.world.getBlockState(hit.getBlockPos());
        final boolean solidSupport = !supportState.getCollisionShape(
                mc.world, hit.getBlockPos()
        ).isEmpty();
        final boolean safeSupport = !this.solidCheck.getValue()
                || supportState.createScreenHandlerFactory(mc.world, hit.getBlockPos()) == null;
        return hit.getSide() == net.minecraft.util.math.Direction.UP
                && solidSupport
                && safeSupport
                && mc.world.getBlockState(placedPos).isReplaceable()
                && mc.world.getFluidState(placedPos).isEmpty();
    }

    private BlockHitResult raycastSolid(final Vec2f rotation, final double range) {
        final Vec3d start = mc.player.getEyePos();
        final Vec3d direction = RotationUtility.getRotationVector(rotation.y, rotation.x);
        final Vec3d end = start.add(direction.multiply(range));
        return mc.world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));
    }

    private BlockHitResult raycastFluid(final Vec2f rotation, final double range) {
        final Vec3d start = mc.player.getEyePos();
        final Vec3d direction = RotationUtility.getRotationVector(rotation.y, rotation.x);
        final Vec3d end = start.add(direction.multiply(range));
        return mc.world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.SOURCE_ONLY,
                mc.player
        ));
    }

    private boolean isWaterSource(final BlockPos pos) {
        return mc.world.getFluidState(pos).getFluid() == Fluids.WATER
                && mc.world.getFluidState(pos).isStill();
    }

    private boolean isSafeToRecoverMlgWater() {
        return mc.player.isOnGround() || mc.player.isTouchingWater() || mc.player.isSwimming();
    }

    private BlockPos findRecoverableWaterSource(final BlockPos expected, final int radius) {
        if (expected == null) return null;
        BlockPos best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int y = -1; y <= 1; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    final BlockPos candidate = expected.add(x, y, z);
                    if (!this.isWaterSource(candidate)) continue;
                    final double reach = mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(candidate));
                    if (reach > mc.player.getBlockInteractionRange() * mc.player.getBlockInteractionRange()) continue;
                    final double score = candidate.getSquaredDistance(expected) + reach * 0.01D;
                    if (score < bestScore) {
                        bestScore = score;
                        best = candidate.toImmutable();
                    }
                }
            }
        }
        return best;
    }

    private void beginPickup(
            final BlockPos waterPos,
            final PickupCause cause,
            final boolean waitForSafeLanding,
            final int preferredSlot
    ) {
        if (waterPos == null) {
            return;
        }
        final long delayMs = cause == PickupCause.MLG ? this.pickupDelay.getValue().longValue() : 0L;
        this.pickupTask = new PickupTask(
                waterPos.toImmutable(), cause, waitForSafeLanding, preferredSlot, delayMs
        );
    }

    private boolean hasPickupTask(final PickupCause cause) {
        return this.pickupTask != null && this.pickupTask.cause == cause;
    }

    private void retryPickupTask() {
        final PickupTask task = this.pickupTask;
        if (task == null) {
            return;
        }

        task.bucketSlot = -1;
        task.confirmTicks = 0;
        task.delayTicks = 1;
        task.rotationWaitTicks = 0;
        task.blockedWaitTicks = 0;
        task.sourceAppearWaitTicks = 0;
        task.serverConfirmed = false;
        task.waterBucketCountBefore = -1;
        task.waterSlotsBefore = null;
        if (--task.attemptsLeft <= 0) {
            this.clearPickupTask();
        }
    }

    private void finishPickupTask() {
        final PickupTask completedTask = this.pickupTask;
        if (completedTask != null && completedTask.cause != PickupCause.FILL) {
            this.waterPlaced = false;
            this.pendingWaterPos = null;
            this.pendingWaterConfirmTicks = 0;
            this.waterPlacementConfirmed = false;
        }
        this.postPlaceCooldown = Math.max(this.postPlaceCooldown, 1);
        this.postActionCooldown = Math.max(this.postActionCooldown, 2);
        this.clearPickupTask();
        this.restoreActionSlot();
    }

    private double getMlgPlaceDistance() {
        final double downwardSpeed = mc.player == null ? 0.0D : Math.max(0.0D, -mc.player.getVelocity().y);
        final double prediction = 0.75D + this.predictTicks.getValue() * 0.75D
                + downwardSpeed * Math.max(1.0D, this.predictTicks.getValue() - 1.0D);
        return MathHelper.clamp(prediction, 1.35D, 4.25D);
    }

    private Vec2f getSafeRotationTo(final Vec3d target, final float yawReference, final float maxPitch) {
        final Vec2f raw = RotationUtility.getRotationFromPosition(target);
        final float yaw = RotationUtility.getDuplicateWrapped(raw.x, yawReference);
        final float pitch = MathHelper.clamp(raw.y, -maxPitch, maxPitch);
        return new Vec2f(yaw, pitch);
    }

    private Vec2f getPickupRotation(final BlockPos waterPos) {
        // Amadeus aims at the recorded water source itself. Unlike placement,
        // pickup must permit a true vertical look at the player's feet.
        return this.getSafeRotationTo(Vec3d.ofCenter(waterPos), this.getCurrentRotation().x, MAX_PICKUP_PITCH);
    }

    private void requestRotation(final Vec2f rotation) {
        if (rotation == null) {
            return;
        }
        this.lastRequestedYaw = rotation.x;
        this.lastRequestedPitch = rotation.y;
        this.rotationRequestedThisTick = true;
        RotationHelper.getHandler().rotate(rotation, InstantRotationModel.INSTANCE);
    }

    private Vec2f getCurrentRotation() {
        // ClientRotationHandler is the preserved user camera, not the rotation
        // applied to movement/item packets by RotationMouseHandler.
        return new Vec2f(mc.player.getYaw(), mc.player.getPitch());
    }

    private boolean isRotationReady(final Vec2f rotation) {
        final Vec2f current = this.getCurrentRotation();
        return RotationUtility.getRotationDifference(current, rotation) <= BUCKET_ROTATION_READY_DIFFERENCE
                && Math.abs(MathHelper.wrapDegrees(current.x - this.lastRequestedYaw)) <= BUCKET_ROTATION_READY_DIFFERENCE
                && Math.abs(current.y - this.lastRequestedPitch) <= BUCKET_ROTATION_READY_DIFFERENCE;
    }

    private boolean isServerRotationReady(final Vec2f rotation) {
        return !Float.isNaN(this.lastSentYaw)
                && !Float.isNaN(this.lastSentPitch)
                && Math.abs(MathHelper.wrapDegrees(this.lastSentYaw - rotation.x)) <= BUCKET_ROTATION_READY_DIFFERENCE
                && Math.abs(this.lastSentPitch - rotation.y) <= BUCKET_ROTATION_READY_DIFFERENCE;
    }

    private BlockHitResult getVerifiedSolidHit(final Vec2f rotation, final BlockHitResult expectedHit) {
        if (!this.isRotationReady(rotation)) {
            return null;
        }

        final BlockHitResult hit = this.raycastSolid(
                this.getCurrentRotation(), mc.player.getBlockInteractionRange()
        );
        if (hit.getType() == HitResult.Type.MISS || !this.isUsableMlgHit(hit)) {
            return null;
        }

        if (!hit.getBlockPos().equals(expectedHit.getBlockPos()) || hit.getSide() != expectedHit.getSide()) {
            return null;
        }

        return hit;
    }

    private boolean sameBlockFace(
            final BlockHitResult first,
            final BlockHitResult second
    ) {
        return first != null && second != null
                && first.getType() != HitResult.Type.MISS
                && second.getType() != HitResult.Type.MISS
                && first.getBlockPos().equals(second.getBlockPos())
                && first.getSide() == second.getSide();
    }

    private double pendingMlgDistance() {
        if (this.pendingMlgHit == null || mc.player == null) {
            return Double.POSITIVE_INFINITY;
        }
        return mc.player.getBoundingBox().minY - this.pendingMlgHit.getPos().y;
    }

    private boolean selectActionSlot(final int targetSlot, final Item expectedItem) {
        if (mc.player == null || mc.interactionManager == null || targetSlot < 0 || targetSlot > 8) {
            return false;
        }

        final ItemStack targetStack = mc.player.getInventory().getStack(targetSlot);
        if (!targetStack.isOf(expectedItem)) {
            return false;
        }

        if (this.restoreSlot == -1) {
            this.restoreSlot = mc.player.getInventory().getSelectedSlot();
        }

        mc.player.getInventory().setSelectedSlot(targetSlot);
        ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).callSyncSelectedSlot();
        return mc.player.getInventory().getSelectedSlot() == targetSlot
                && mc.player.getMainHandStack().isOf(expectedItem);
    }

    private void restoreActionSlot() {
        if (this.restoreSlot == -1 || mc.player == null || mc.interactionManager == null) {
            return;
        }

        final int slot = this.restoreSlot;
        this.restoreSlot = -1;
        mc.player.getInventory().setSelectedSlot(slot);
        ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).callSyncSelectedSlot();
    }

    private boolean useItem(final Item expectedItem) {
        if (!this.isHolding(expectedItem)) {
            return false;
        }

        ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).callSyncSelectedSlot();
        final ActionResult result = mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        if (result.isAccepted()) {
            mc.player.swingHand(Hand.MAIN_HAND);
            return true;
        }
        return false;
    }

    private boolean pickupWaterSource(final Vec2f rotation, final BlockPos expectedWaterPos) {
        if (!this.isHolding(Items.BUCKET) || mc.world == null || expectedWaterPos == null) {
            return false;
        }

        ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).callSyncSelectedSlot();
        final float originalYaw = mc.player.getYaw();
        final float originalPitch = mc.player.getPitch();
        mc.player.setYaw(rotation.x);
        mc.player.setPitch(rotation.y);

        try {
            // Empty buckets use BucketItem.use(), whose SOURCE_ONLY raycast and
            // PlayerInteractItem packet are the authoritative pickup path.
            final BlockHitResult hit = this.raycastFluid(rotation, mc.player.getBlockInteractionRange());
            if (hit.getType() == HitResult.Type.MISS
                    || !hit.getBlockPos().equals(expectedWaterPos)
                    || !this.isWaterSource(hit.getBlockPos())) {
                return false;
            }

            final ActionResult itemResult = mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            if (itemResult.isAccepted()) {
                mc.player.swingHand(Hand.MAIN_HAND);
                return true;
            }
            return false;
        } finally {
            mc.player.setYaw(originalYaw);
            mc.player.setPitch(originalPitch);
        }
    }

    private boolean isPickupInteractionBlocked() {
        if (OutboundNetworkBlockage.get().isAnyBlockages()) {
            return true;
        }

        final var client = OraculusClient.getInstance();
        if (client == null || client.getModuleRepository() == null) {
            return false;
        }

        for (final String moduleName : new String[]{"Blink", "Stuck", "FakeLag"}) {
            final Module module = client.getModuleRepository().getOptionalModule(moduleName);
            if (module != null && module.isEnabled()) {
                return true;
            }
        }
        return false;
    }

    private boolean confirmsWaterBucket(final Packet<?> packet, final PickupTask task) {
        if (task.waterBucketCountBefore < 0) {
            return false;
        }
        if (packet instanceof ScreenHandlerSlotUpdateS2CPacket slotUpdate) {
            final int slot = slotUpdate.getSlot();
            return slotUpdate.getStack().isOf(Items.WATER_BUCKET)
                    && !this.wasWaterBucketBefore(task.waterSlotsBefore, slot);
        }

        if (packet instanceof InventoryS2CPacket inventoryUpdate
                && inventoryUpdate.syncId() == mc.player.playerScreenHandler.syncId) {
            int waterBucketCount = 0;
            for (final ItemStack stack : inventoryUpdate.contents()) {
                if (stack.isOf(Items.WATER_BUCKET)) {
                    waterBucketCount += stack.getCount();
                }
            }
            return waterBucketCount > task.waterBucketCountBefore;
        }
        return false;
    }

    private int countWaterBuckets() {
        if (mc.player == null) {
            return 0;
        }
        int count = 0;
        for (int slot = 0; slot < mc.player.getInventory().size(); slot++) {
            final ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isOf(Items.WATER_BUCKET)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private boolean[] snapshotWaterBucketSlots() {
        final boolean[] snapshot = new boolean[mc.player.getInventory().size()];
        for (int slot = 0; slot < snapshot.length; slot++) {
            snapshot[slot] = mc.player.getInventory().getStack(slot).isOf(Items.WATER_BUCKET);
        }
        return snapshot;
    }

    private boolean wasWaterBucketBefore(final boolean[] snapshot, final int packetSlot) {
        if (snapshot == null) {
            return false;
        }

        final int inventorySlot;
        if (packetSlot >= PLAYER_SCREEN_HOTBAR_START
                && packetSlot < PLAYER_SCREEN_HOTBAR_START + 9) {
            inventorySlot = packetSlot - PLAYER_SCREEN_HOTBAR_START;
        } else if (packetSlot >= 9 && packetSlot < snapshot.length) {
            inventorySlot = packetSlot;
        } else if (packetSlot >= 0 && packetSlot < 9) {
            // Keep compatibility with packet hooks that expose raw inventory
            // indices instead of PlayerScreenHandler slot indices.
            inventorySlot = packetSlot;
        } else {
            return false;
        }
        return inventorySlot < snapshot.length && snapshot[inventorySlot];
    }

    private boolean isHolding(final Item expectedItem) {
        return mc.player != null
                && mc.interactionManager != null
                && mc.player.getMainHandStack().isOf(expectedItem);
    }

    private int findWaterBucketSlot() {
        if (mc.player == null) {
            return -1;
        }
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.WATER_BUCKET)) {
                return i;
            }
        }
        return -1;
    }

    private int findEmptyBucketSlot() {
        return this.findEmptyBucketSlot(-1);
    }

    private int findEmptyBucketSlot(final int preferredSlot) {
        if (mc.player == null) {
            return -1;
        }
        if (preferredSlot >= 0 && preferredSlot < 9
                && mc.player.getInventory().getStack(preferredSlot).isOf(Items.BUCKET)
                && mc.player.getInventory().getStack(preferredSlot).getCount() == 1) {
            return preferredSlot;
        }
        for (int i = 0; i < 9; i++) {
            final ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(Items.BUCKET) && stack.getCount() == 1) {
                return i;
            }
        }
        if (preferredSlot >= 0 && preferredSlot < 9
                && mc.player.getInventory().getStack(preferredSlot).isOf(Items.BUCKET)) {
            return preferredSlot;
        }
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.BUCKET)) {
                return i;
            }
        }
        return -1;
    }

    private void clearPickupTask() {
        this.pickupTask = null;
    }

    private void clearPickupTask(final PickupCause cause) {
        if (this.hasPickupTask(cause)) {
            this.clearPickupTask();
        }
    }

    private void clearPendingMlgPlacement() {
        this.pendingMlgHit = null;
        this.pendingMlgRotation = null;
        this.lastRequestedYaw = Float.NaN;
        this.lastRequestedPitch = Float.NaN;
    }

    private void resetMlgState() {
        this.accumulatedFall = 0.0F;
        this.lastY = mc.player == null ? 0.0D : mc.player.getY();
        this.waterPlaced = false;
        this.pendingWaterPos = null;
        this.pendingWaterConfirmTicks = 0;
        this.waterPlacementConfirmed = false;
        this.clearPickupTask(PickupCause.MLG);
        this.postPlaceCooldown = 0;
        this.postActionCooldown = 0;
        this.lastSentYaw = Float.NaN;
        this.lastSentPitch = Float.NaN;
        this.fallStateAge = Integer.MIN_VALUE;
        this.rotationRequestedThisTick = false;
        this.clearPendingMlgPlacement();
    }

    private void resetState() {
        this.restoreSlot = -1;
        this.resetMlgState();
        this.clearPickupTask();
        this.helperCooldownTicks = 0;
    }

    @Override
    protected void onEnable() {
        this.resetState();
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        this.restoreActionSlot();
        this.resetState();
        super.onDisable();
    }

    private enum PickupCause {
        MLG,
        EXTINGUISH,
        FILL
    }

    private static final class PickupTask {
        private BlockPos expectedWaterPos;
        private final PickupCause cause;
        private final boolean waitForSafeLanding;
        private int landingWaitTicks = 100;
        private int delayTicks;
        private int confirmTicks;
        private int rotationWaitTicks;
        private int blockedWaitTicks;
        private int sourceAppearWaitTicks;
        private int attemptsLeft = PICKUP_MAX_ATTEMPTS;
        private int bucketSlot = -1;
        private final int preferredSlot;
        private boolean serverConfirmed;
        private int waterBucketCountBefore = -1;
        private boolean[] waterSlotsBefore;
        private final long rightClickAtNanos;

        private PickupTask(
                final BlockPos expectedWaterPos,
                final PickupCause cause,
                final boolean waitForSafeLanding,
                final int preferredSlot,
                final long rightClickDelayMs
        ) {
            this.expectedWaterPos = expectedWaterPos;
            this.cause = cause;
            this.waitForSafeLanding = waitForSafeLanding;
            this.preferredSlot = preferredSlot;
            this.delayTicks = cause == PickupCause.MLG ? 0 : PICKUP_PREPARE_TICKS;
            this.rightClickAtNanos = System.nanoTime() + rightClickDelayMs * 1_000_000L;
        }
    }
}

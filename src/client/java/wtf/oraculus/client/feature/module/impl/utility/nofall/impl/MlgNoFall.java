package wtf.oraculus.client.feature.module.impl.utility.nofall.impl;

import net.minecraft.block.BlockState;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import wtf.oraculus.client.feature.helper.impl.player.rotation.model.impl.InstantRotationModel;
import wtf.oraculus.client.feature.helper.impl.player.slot.SlotHelper;
import wtf.oraculus.client.feature.module.impl.utility.nofall.NoFallModule;
import wtf.oraculus.client.feature.module.property.impl.mode.ModeProperty;
import wtf.oraculus.client.feature.module.property.impl.mode.ModuleMode;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.misc.time.Stopwatch;
import wtf.oraculus.utility.player.InventoryUtility;
import wtf.oraculus.utility.player.RotationInjector;
import wtf.oraculus.utility.player.RotationUtility;

import static wtf.oraculus.client.Constants.mc;

/**
 * Direct port of Amadeus NoFall's MLG mode.
 *
 * <p>It waits until the player is close enough to a collision surface,
 * places water from the offhand or a silently selected hotbar slot, picks the
 * water back up after the original delay, then restores the previous slot.</p>
 */
public final class MlgNoFall extends ModuleMode<NoFallModule> {

    private static final long PICKUP_DELAY_MS = 60L;
    private static final long RESTORE_DELAY_MS = 50L;
    private static final int GROUND_SCAN_DEPTH = 10;
    private static final double MAX_TRIGGER_HEIGHT = 4.0D;
    private static final double MIN_TRIGGER_HEIGHT = 0.1D;
    private static final double MIN_FALL_SPEED = 0.5D;

    private final NumberProperty minHeight =
            new NumberProperty("Min Height", 3.0D, 1.0D, 10.0D, 1.0D)
                    .hideIf(() -> this.module.getActiveMode() != this);
    private final ModeProperty<RotationInjector.RotationMode> rotationMode =
            new ModeProperty<>("Rotation Mode", this, RotationInjector.RotationMode.NORMAL);

    private final Stopwatch pickupStopwatch = new Stopwatch();

    private boolean placedWater;
    private boolean pickedUpWater;
    private int previousSlot = -1;
    private int usedWaterBucketSlot = -1;
    private BlockPos waterPlacementPos;
    private boolean rotating;
    private Vec2f targetRotation;

    public MlgNoFall(final NoFallModule module) {
        super(module);
        module.addProperties(this.minHeight, this.rotationMode);
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            this.reset();
            return;
        }

        if (this.placedWater) {
            this.handlePickup();
            return;
        }

        if (this.shouldTriggerMlg()) {
            this.executeMlg();
        }
    }

    private boolean shouldTriggerMlg() {
        if (mc.player.isOnGround() || mc.player.isInFluid() || mc.player.isClimbing()) {
            return false;
        }
        if (mc.player.getVelocity().y >= 0.0D
                || mc.player.fallDistance < this.minHeight.getValue().floatValue()) {
            return false;
        }

        final Vec3d playerPos = mc.player.getEntityPos();
        final BlockPos feetPos = BlockPos.ofFloored(
                playerPos.x,
                playerPos.y - 1.0D,
                playerPos.z
        );
        double distanceToGround = GROUND_SCAN_DEPTH;
        for (int offset = 0; offset < GROUND_SCAN_DEPTH; offset++) {
            final BlockPos checkPos = feetPos.down(offset);
            final BlockState state = mc.world.getBlockState(checkPos);
            if (state.isAir() || state.getCollisionShape(mc.world, checkPos).isEmpty()) {
                continue;
            }

            distanceToGround = playerPos.y - (checkPos.getY() + 1.0D);
            break;
        }

        return distanceToGround < MAX_TRIGGER_HEIGHT
                && distanceToGround > MIN_TRIGGER_HEIGHT
                && Math.abs(mc.player.getVelocity().y) >= MIN_FALL_SPEED;
    }

    private void executeMlg() {
        this.previousSlot = mc.player.getInventory().getSelectedSlot();
        if (mc.player.getOffHandStack().isOf(Items.WATER_BUCKET)) {
            this.usedWaterBucketSlot = -1;
            this.lookAtFeet();
            this.placeWater(Hand.OFF_HAND);
            return;
        }

        final int waterBucketSlot = InventoryUtility.findItemInHotbar(Items.WATER_BUCKET);
        if (waterBucketSlot == -1) {
            this.previousSlot = -1;
            return;
        }

        this.usedWaterBucketSlot = waterBucketSlot;
        SlotHelper.setCurrentItem(waterBucketSlot).silence(SlotHelper.Silence.FULL);
        this.lookAtFeet();
        this.placeWater(Hand.MAIN_HAND);
    }

    private void placeWater(final Hand hand) {
        final ActionResult result = mc.interactionManager.interactItem(mc.player, hand);
        if (!result.isAccepted()) {
            return;
        }

        this.placedWater = true;
        this.waterPlacementPos = BlockPos.ofFloored(
                mc.player.getX(),
                mc.player.getY() - 1.0D,
                mc.player.getZ()
        );
        this.pickupStopwatch.reset();
    }

    private void lookAtFeet() {
        final Vec3d feetPos = mc.player.getEntityPos().add(0.0D, -1.0D, 0.0D);
        this.targetRotation = RotationUtility.getRotationFromPosition(feetPos);
        RotationInjector.applyRotation(
                this.targetRotation,
                this.rotationMode.getValue(),
                InstantRotationModel.INSTANCE
        );
        this.rotating = true;
    }

    private void handlePickup() {
        if (!this.pickupStopwatch.hasTimeElapsed(PICKUP_DELAY_MS)) {
            return;
        }

        if (!this.pickedUpWater) {
            this.pickupWater();
            this.pickedUpWater = true;
            this.pickupStopwatch.reset();
            return;
        }

        if (!this.pickupStopwatch.hasTimeElapsed(RESTORE_DELAY_MS)) {
            return;
        }

        this.restorePreviousSlot();
        this.reset();
    }

    private void pickupWater() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }

        if (this.waterPlacementPos != null) {
            final Vec2f pickupRotation = RotationUtility.getRotationFromPosition(
                    this.waterPlacementPos.toCenterPos()
            );
            RotationInjector.applyRotation(
                    pickupRotation,
                    this.rotationMode.getValue(),
                    InstantRotationModel.INSTANCE
            );
        }

        if (this.usedWaterBucketSlot == -1) {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            return;
        }

        SlotHelper.setCurrentItem(this.usedWaterBucketSlot).silence(SlotHelper.Silence.FULL);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
    }

    private void restorePreviousSlot() {
        if (this.previousSlot == -1 || mc.player == null) {
            return;
        }

        if (this.usedWaterBucketSlot == -1) {
            mc.player.getInventory().setSelectedSlot(this.previousSlot);
        } else {
            SlotHelper.getInstance().stop();
        }
    }

    private void reset() {
        this.placedWater = false;
        this.pickedUpWater = false;
        this.previousSlot = -1;
        this.usedWaterBucketSlot = -1;
        this.waterPlacementPos = null;
        this.rotating = false;
        this.targetRotation = null;
    }

    @Override
    public void onDisable() {
        if (this.previousSlot != -1 && mc.player != null) {
            if (!this.pickedUpWater && this.placedWater) {
                this.pickupWater();
            }
            this.restorePreviousSlot();
        }
        this.reset();
        super.onDisable();
    }

    @Override
    public Enum<?> getEnumValue() {
        return NoFallModule.Mode.MLG;
    }
}

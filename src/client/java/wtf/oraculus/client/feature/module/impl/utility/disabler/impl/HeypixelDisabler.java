package wtf.oraculus.client.feature.module.impl.utility.disabler.impl;

import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.helper.impl.player.packet.blockage.impl.OutboundNetworkBlockage;
import wtf.oraculus.client.feature.module.impl.utility.disabler.DisablerModule;
import wtf.oraculus.client.feature.module.impl.world.blockfly.BlockFlyModule;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.bool.MultipleBooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.mode.ModuleMode;
import wtf.oraculus.event.impl.game.JoinWorldEvent;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.packet.InstantaneousReceivePacketEvent;
import wtf.oraculus.event.impl.game.packet.InstantaneousSendPacketEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.mixin.PlayerInteractItemC2SPacketAccessor;
import wtf.oraculus.mixin.PlayerMoveC2SPacketAccessor;
import wtf.oraculus.utility.misc.chat.ChatUtility;
import wtf.oraculus.utility.misc.time.Stopwatch;

import java.util.Random;

import static wtf.oraculus.client.Constants.mc;

public final class HeypixelDisabler extends ModuleMode<DisablerModule> {
    private static final double[] PERFECT_ROTATION_STEPS = {
            0.0D, 5.625D, 11.25D, 16.875D, 22.5D, 28.125D, 33.75D, 39.375D,
            45.0D, 50.625D, 56.25D, 61.875D, 67.5D, 73.125D, 78.75D, 84.375D, 90.0D
    };

    private final MultipleBooleanProperty options = new MultipleBooleanProperty("Options",
            new BooleanProperty("Grim Bad PacketsA", true),
            new BooleanProperty("Grim Duplicate RotPlace", true),
            new BooleanProperty("Grim Aim360", false),
            new BooleanProperty("ACA Fast Switch", true),
            new BooleanProperty("ACA Inventory Frequency", false),
            new BooleanProperty("ACA Aim Step", true),
            new BooleanProperty("ACA Perfect Rotation", true),
            new BooleanProperty("Themis Blink", true),
            new BooleanProperty("Only Remote Server", false),
            new BooleanProperty("Logging", false)
    ).hideIf(() -> this.module.getActiveMode() != this);

    private final Stopwatch inventoryTimer = new Stopwatch();
    private final Random random = new Random();

    private int lastSentSlot = -1;
    private long inventoryOpenTime;
    private boolean inventoryOpen;
    private CloseHandledScreenC2SPacket storedClosePacket;
    private long inventoryCloseDelay;
    private long themisBlinkLastSend = System.currentTimeMillis();
    private int themisBlinkCount;
    private float lastYaw;
    private float lastPitch;
    private float currentYaw;
    private float currentPitch;
    private float yawDiff;
    private float pitchDiff;
    private float lastPlacedYawDiff;
    private float lastPlacedPitchDiff;
    private boolean rotated;
    private float lastAim360Yaw;
    private float lastAim360Pitch;
    private boolean aim360Initialized;

    public HeypixelDisabler(final DisablerModule module) {
        super(module);
        module.addProperties(options);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
        super.onDisable();
    }

    @Override
    public Enum<?> getEnumValue() {
        return DisablerModule.Mode.HEYPIXEL;
    }

    @Subscribe
    public void onJoinWorld(final JoinWorldEvent event) {
        resetState();
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (!this.isAim360Active()) {
            this.aim360Initialized = false;
        }
        if (!canProcess()) {
            return;
        }

        if (storedClosePacket != null && inventoryTimer.hasTimeElapsed(inventoryCloseDelay)) {
            OutboundNetworkBlockage.sendPacketDirect(storedClosePacket);
            log("InventoryFrequency: Released stored close packet");
            storedClosePacket = null;
        }

        if (isEnabled("Themis Blink") && System.currentTimeMillis() - themisBlinkLastSend > 200L) {
            if (themisBlinkCount == 0) {
                OutboundNetworkBlockage.sendPacketDirect(new CommonPongC2SPacket(0));
            }
            themisBlinkLastSend = System.currentTimeMillis();
            themisBlinkCount = 0;
        }
    }

    @Subscribe
    public void onInstantaneousReceivePacket(final InstantaneousReceivePacketEvent event) {
        if (mc.player == null || (isEnabled("Only Remote Server") && mc.isInSingleplayer())) {
            return;
        }

        final Packet<?> packet = event.getPacket();
        if (packet instanceof GameJoinS2CPacket) {
            resetState();
            return;
        }

        if (shouldResetForPlayerState()) {
            resetState();
            return;
        }

        if (packet instanceof OpenScreenS2CPacket) {
            inventoryOpenTime = System.currentTimeMillis();
            inventoryOpen = true;
            log("Inventory opened at: " + inventoryOpenTime);
        }
    }

    @Subscribe
    public void onInstantaneousSendPacket(final InstantaneousSendPacketEvent event) {
        if (!canProcess()) {
            return;
        }

        final Packet<?> packet = event.getPacket();

        if (packet instanceof UpdateSelectedSlotC2SPacket slotPacket) {
            handleSelectedSlot(event, slotPacket);
            return;
        }

        if (isEnabled("ACA Inventory Frequency") && packet instanceof CloseHandledScreenC2SPacket closePacket) {
            handleInventoryClose(event, closePacket);
            if (event.isCancelled()) {
                return;
            }
        }

        if (isEnabled("Themis Blink")) {
            if (packet instanceof PlayerMoveC2SPacket.OnGroundOnly || packet instanceof CommonPongC2SPacket) {
                themisBlinkCount++;
            }
        }

        if (isEnabled("Grim Duplicate RotPlace")) {
            if (packet instanceof PlayerMoveC2SPacket movePacket && movePacket.changesLook()) {
                handleDuplicateRotPlace(movePacket);
            } else if (packet instanceof PlayerInteractBlockC2SPacket && rotated) {
                lastPlacedYawDiff = yawDiff;
                lastPlacedPitchDiff = pitchDiff;
                rotated = false;
            }
        }

        if ((isEnabled("ACA Aim Step") || isEnabled("ACA Perfect Rotation"))
                && packet instanceof PlayerMoveC2SPacket movePacket
                && movePacket.changesLook()) {
            handleRotationAdjustments(movePacket);
        }

        if (this.isAim360Active()
                && packet instanceof PlayerMoveC2SPacket movePacket
                && movePacket.changesLook()) {
            handleGrimAim360(movePacket);
        }

        if (this.isAim360Active() && packet instanceof PlayerInteractItemC2SPacket interactItemPacket) {
            handleGrimAim360UseItem(interactItemPacket);
        }
    }

    private void handleSelectedSlot(final InstantaneousSendPacketEvent event, final UpdateSelectedSlotC2SPacket slotPacket) {
        final int slot = slotPacket.getSelectedSlot();
        if (isEnabled("Grim Bad PacketsA") && slot == lastSentSlot && slot != -1) {
            event.setCancelled();
            log("BadPacketsA: Cancelled duplicate slot packet: " + slot);
            return;
        }

        if (isEnabled("ACA Fast Switch") && lastSentSlot != -1 && slot != lastSentSlot) {
            sendIntermediateSlots(lastSentSlot, slot);
        }

        lastSentSlot = slot;
        log("Processed slot switch: " + lastSentSlot + " -> " + slot);
    }

    private void handleInventoryClose(final InstantaneousSendPacketEvent event, final CloseHandledScreenC2SPacket closePacket) {
        if (!inventoryOpen) {
            return;
        }

        final long now = System.currentTimeMillis();
        final long openDuration = now - inventoryOpenTime;
        if (openDuration <= 150L) {
            event.setCancelled();
            storedClosePacket = closePacket;
            inventoryCloseDelay = 151L - openDuration;
            inventoryTimer.reset();
            log("InventoryFrequency: Storing close packet, will send after " + inventoryCloseDelay + "ms");
            inventoryOpen = false;
            return;
        }

        inventoryOpen = false;
        log("InventoryFrequency: Allowed close packet after " + openDuration + "ms");
    }

    private void handleDuplicateRotPlace(final PlayerMoveC2SPacket movePacket) {
        final float previousYaw = currentYaw;
        final float previousPitch = currentPitch;
        currentYaw = movePacket.getYaw(mc.player.getYaw());
        currentPitch = movePacket.getPitch(mc.player.getPitch());
        yawDiff = Math.abs(currentYaw - previousYaw);
        pitchDiff = Math.abs(currentPitch - previousPitch);
        rotated = true;

        final PlayerMoveC2SPacketAccessor accessor = (PlayerMoveC2SPacketAccessor) movePacket;
        if (yawDiff > 2.0F && Math.abs(yawDiff - lastPlacedYawDiff) < 1.0E-4F) {
            final float jitter = 0.001F + random.nextFloat() * 0.009F;
            final float newYaw = currentYaw - jitter;
            accessor.setYaw(newYaw);
            log("DuplicateRotPlace: Modified yaw from " + currentYaw + " to " + newYaw);
        }

        if (pitchDiff > 2.0F && Math.abs(pitchDiff - lastPlacedPitchDiff) < 1.0E-4F) {
            final float jitter = 0.001F + random.nextFloat() * 0.009F;
            final float newPitch = MathHelper.clamp(currentPitch - jitter, -90.0F, 90.0F);
            accessor.setPitch(newPitch);
            log("DuplicateRotPlace: Modified pitch from " + currentPitch + " to " + newPitch);
        }
    }

    private void handleRotationAdjustments(final PlayerMoveC2SPacket movePacket) {
        float yaw = movePacket.getYaw(mc.player.getYaw());
        float pitch = movePacket.getPitch(mc.player.getPitch());
        boolean modified = false;

        if (isEnabled("ACA Aim Step") && isAimStepRotation(yaw, pitch)) {
            final float[] adjusted = applyAimStep(yaw, pitch);
            yaw = adjusted[0];
            pitch = adjusted[1];
            modified = true;
        }

        if (isEnabled("ACA Perfect Rotation")) {
            final float[] adjusted = applyPerfectRotation(yaw, pitch);
            if (adjusted[0] != yaw || adjusted[1] != pitch) {
                yaw = adjusted[0];
                pitch = adjusted[1];
                modified = true;
                log("PerfectRotation: Modified rotation");
            }
        }

        if (modified) {
            final PlayerMoveC2SPacketAccessor accessor = (PlayerMoveC2SPacketAccessor) movePacket;
            accessor.setYaw(yaw);
            accessor.setPitch(MathHelper.clamp(pitch, -90.0F, 90.0F));
        }

        lastYaw = movePacket.getYaw(mc.player.getYaw());
        lastPitch = movePacket.getPitch(mc.player.getPitch());
    }

    private void handleGrimAim360(final PlayerMoveC2SPacket movePacket) {
        final float yaw = movePacket.getYaw(mc.player.getYaw());
        final float pitch = movePacket.getPitch(mc.player.getPitch());
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            return;
        }

        if (!this.aim360Initialized) {
            this.lastAim360Yaw = mc.player.getYaw();
            this.aim360Initialized = true;
        }

        // BMW's GrimAim360 logic: keep yaw on the nearest equivalent turn.
        final float continuousYaw = this.lastAim360Yaw + MathHelper.wrapDegrees(yaw - this.lastAim360Yaw);
        final float normalizedPitch = MathHelper.clamp(pitch, -90.0F, 90.0F);
        final PlayerMoveC2SPacketAccessor accessor = (PlayerMoveC2SPacketAccessor) movePacket;
        accessor.setYaw(continuousYaw);
        accessor.setPitch(normalizedPitch);
        this.lastAim360Yaw = continuousYaw;
        this.lastAim360Pitch = normalizedPitch;
    }

    private void handleGrimAim360UseItem(final PlayerInteractItemC2SPacket packet) {
        if (mc.player.getStackInHand(packet.getHand()).isOf(Items.BUCKET)
                || mc.player.getStackInHand(packet.getHand()).isOf(Items.WATER_BUCKET)) {
            final PlayerInteractItemC2SPacketAccessor accessor = (PlayerInteractItemC2SPacketAccessor) packet;
            accessor.setYaw(mc.player.getYaw());
            accessor.setPitch(mc.player.getPitch());
            return;
        }
        if (!this.aim360Initialized) {
            return;
        }

        final PlayerInteractItemC2SPacketAccessor accessor = (PlayerInteractItemC2SPacketAccessor) packet;
        accessor.setYaw(this.lastAim360Yaw);
        accessor.setPitch(this.lastAim360Pitch);
    }

    public Vec2f getUseItemPacketRotation(final float fallbackYaw, final float fallbackPitch) {
        if (!this.module.isEnabled()
                || this.module.getActiveMode() != this
                || !this.isAim360Active()
                || !this.aim360Initialized
                || mc.player == null
                || (isEnabled("Only Remote Server") && mc.isInSingleplayer())) {
            return new Vec2f(fallbackYaw, fallbackPitch);
        }
        return new Vec2f(this.lastAim360Yaw, this.lastAim360Pitch);
    }

    private boolean canProcess() {
        if (mc.player == null || (isEnabled("Only Remote Server") && mc.isInSingleplayer())) {
            return false;
        }

        if (shouldResetForPlayerState()) {
            resetState();
            return false;
        }

        return true;
    }

    private boolean isAim360Active() {
        if (!isEnabled("Grim Aim360")) {
            return false;
        }

        final var repository = OraculusClient.getInstance().getModuleRepository();
        if (repository == null) {
            return false;
        }

        final BlockFlyModule blockFly = repository.getModule(BlockFlyModule.class);
        return blockFly != null && blockFly.isEnabled();
    }

    private boolean shouldResetForPlayerState() {
        return mc.player.isSpectator() || !mc.player.isAlive() || mc.player.isDead();
    }

    private boolean isEnabled(final String name) {
        final BooleanProperty property = options.getProperty(name);
        return property != null && property.getValue();
    }

    private void resetState() {
        lastSentSlot = -1;
        inventoryOpenTime = 0L;
        inventoryOpen = false;
        storedClosePacket = null;
        inventoryCloseDelay = 0L;
        themisBlinkLastSend = System.currentTimeMillis();
        themisBlinkCount = 0;
        lastYaw = 0.0F;
        lastPitch = 0.0F;
        currentYaw = 0.0F;
        currentPitch = 0.0F;
        yawDiff = 0.0F;
        pitchDiff = 0.0F;
        lastPlacedYawDiff = 0.0F;
        lastPlacedPitchDiff = 0.0F;
        rotated = false;
        lastAim360Yaw = 0.0F;
        lastAim360Pitch = 0.0F;
        aim360Initialized = false;
    }

    private void log(final String message) {
        if (module.isEnabled() && isEnabled("Logging")) {
            ChatUtility.print("[Disabler] " + message);
        }
    }

    private boolean isAimStepRotation(final float yaw, final float pitch) {
        if (lastYaw == 0.0F && lastPitch == 0.0F) {
            return false;
        }

        final double yawDelta = Math.abs(MathHelper.wrapDegrees(yaw - lastYaw));
        final double pitchDelta = Math.abs(pitch - lastPitch);
        final boolean yawStuck = yawDelta < 1.0E-5D && pitchDelta > 1.0D;
        final boolean pitchStuck = pitchDelta < 1.0E-5D && yawDelta > 1.0D;
        return yawStuck || pitchStuck;
    }

    private float[] applyAimStep(final float yaw, final float pitch) {
        final double yawDelta = Math.abs(MathHelper.wrapDegrees(yaw - lastYaw));
        final double pitchDelta = Math.abs(pitch - lastPitch);
        float newYaw = yaw;
        float newPitch = pitch;

        if (yawDelta < 1.0E-5D && pitchDelta > 1.0D) {
            newYaw = lastYaw + (float) (random.nextGaussian() * 0.001D);
        }

        if (pitchDelta < 1.0E-5D && yawDelta > 1.0D) {
            newPitch = lastPitch + (float) (random.nextGaussian() * 0.001D);
        }

        return new float[]{newYaw, newPitch};
    }

    private float[] applyPerfectRotation(final float yaw, final float pitch) {
        if (lastYaw == 0.0F && lastPitch == 0.0F) {
            return new float[]{yaw, pitch};
        }

        final double yawDelta = Math.abs(MathHelper.wrapDegrees(yaw - lastYaw));
        final double pitchDelta = Math.abs(pitch - lastPitch);
        float newYaw = yaw;
        float newPitch = pitch;

        if (!isNearZeroOrMultiple(yawDelta) && isKnownRotationStep(yawDelta)) {
            newYaw = yaw + (float) (random.nextGaussian() * 0.005D);
        }

        if (!isNearZeroOrMultiple(pitchDelta) && isKnownRotationStep(pitchDelta)) {
            newPitch = pitch + (float) (random.nextGaussian() * 0.005D);
        }

        return new float[]{newYaw, newPitch};
    }

    private boolean isNearZeroOrMultiple(final double value) {
        return Math.abs(value) <= 1.0E-10D || isMultipleOf(360.0D, value);
    }

    private boolean isKnownRotationStep(final double value) {
        if (Double.isInfinite(value) || Double.isNaN(value)) {
            return false;
        }

        for (double step : PERFECT_ROTATION_STEPS) {
            if (isMultipleOf(step, value)) {
                return true;
            }
        }

        return false;
    }

    private boolean isMultipleOf(final double base, final double value) {
        if (base == 0.0D) {
            return Math.abs(value) <= 1.0E-10D;
        }

        final double ratio = value / base;
        return Math.abs(ratio - Math.round(ratio)) <= 1.0E-10D;
    }

    private void sendIntermediateSlots(final int fromSlot, final int toSlot) {
        final int distance = Math.abs(fromSlot - toSlot);
        if (distance <= 1 || isWrapAroundSlot(fromSlot, toSlot)) {
            return;
        }

        final int step = fromSlot > toSlot ? -1 : 1;
        for (int slot = fromSlot + step; slot != toSlot; slot += step) {
            if (slot < 0 || slot > 8) {
                continue;
            }

            OutboundNetworkBlockage.sendPacketDirect(new UpdateSelectedSlotC2SPacket(slot));
            log("Sent intermediate slot: " + slot);
        }
    }

    private boolean isWrapAroundSlot(final int fromSlot, final int toSlot) {
        return (fromSlot == 0 && toSlot == 8) || (fromSlot == 8 && toSlot == 0);
    }
}

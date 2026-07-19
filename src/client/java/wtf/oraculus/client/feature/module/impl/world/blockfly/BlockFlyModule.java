package wtf.oraculus.client.feature.module.impl.world.blockfly;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.item.ItemStack;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.impl.visual.overlay.impl.dynamicisland.IslandTrigger;
import wtf.oraculus.client.feature.module.impl.world.blockfly.block.BlockFlyBlockUtil;
import wtf.oraculus.client.feature.module.impl.world.blockfly.block.BlockFlyPlacementSearch;
import wtf.oraculus.client.feature.module.impl.world.blockfly.input.BlockFlyKeyStateController;
import wtf.oraculus.client.feature.module.impl.world.blockfly.inventory.BlockFlySlotController;
import wtf.oraculus.client.feature.module.impl.world.blockfly.math.BlockFlyMathUtil;
import wtf.oraculus.client.feature.module.impl.world.blockfly.motion.BlockFlyMotionSimulator;
import wtf.oraculus.client.feature.module.impl.world.blockfly.movement.BlockFlyMovementUtil;
import wtf.oraculus.client.feature.module.impl.world.blockfly.raycast.BlockFlyRayTraceUtil;
import wtf.oraculus.client.feature.module.impl.world.blockfly.render.BlockFlyRenderSpoof;
import wtf.oraculus.client.feature.module.impl.world.blockfly.rotation.BlockFlyRotation;
import wtf.oraculus.client.feature.module.impl.world.blockfly.rotation.BlockFlyRotationBridge;
import wtf.oraculus.client.feature.module.impl.world.blockfly.rotation.BlockFlyRotationHandler;
import wtf.oraculus.client.feature.module.impl.world.blockfly.rotation.BlockFlyRotationUtil;
import wtf.oraculus.client.feature.module.impl.world.blockfly.state.BlockFlyPlacementTarget;
import wtf.oraculus.client.feature.module.impl.world.blockfly.tick.BlockFlyDelayedTickQueue;
import wtf.oraculus.client.feature.module.impl.visual.overlay.impl.dynamicisland.DynamicIslandElement;
import wtf.oraculus.client.renderer.MinecraftRenderer;
import wtf.oraculus.client.renderer.world.WorldRenderer;
import wtf.oraculus.duck.ClientConnectionAccess;
import wtf.oraculus.event.impl.game.JoinWorldEvent;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.input.MouseHandleInputEvent;
import wtf.oraculus.event.impl.game.input.MoveInputEvent;
import wtf.oraculus.event.impl.game.packet.ReceivePacketEvent;
import wtf.oraculus.event.impl.game.player.movement.JumpEvent;
import wtf.oraculus.event.impl.game.player.movement.PostMovementPacketEvent;
import wtf.oraculus.event.impl.game.player.movement.PreMovementPacketEvent;
import wtf.oraculus.event.impl.game.server.ServerDisconnectEvent;
import wtf.oraculus.event.impl.render.RenderScreenEvent;
import wtf.oraculus.event.impl.render.RenderWorldEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.mixin.LivingEntityAccessor;
import wtf.oraculus.utility.render.ColorUtility;
import wtf.oraculus.utility.render.CustomRenderLayers;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

import static wtf.oraculus.client.Constants.mc;

public final class BlockFlyModule extends Module implements IslandTrigger {
    private final BlockFlySettings settings = new BlockFlySettings();
    private final BlockFlySlotController slotController = new BlockFlySlotController();
    private final BlockFlyKeyStateController keyController = new BlockFlyKeyStateController();
    private final BlockFlyIsland island = new BlockFlyIsland(this);
    private final CopyOnWriteArrayList<CopyOnWriteArrayList<Packet<?>>> packetBatches = new CopyOnWriteArrayList<>();

    private final BlockFlyRotation correctRotation = new BlockFlyRotation();
    private final BlockFlyRotation rotations = new BlockFlyRotation();
    private final BlockFlyRotation lastRotations = new BlockFlyRotation();

    private BlockFlyPlacementTarget currentPlacement;
    private int targetYLevel = -1;
    private int velocityDelay;
    private int eagleTimer;
    private int groundTicks;
    private int airTicks;
    private int rotationDelay;
    private int jitterCounter;
    private double yawDifference;
    private double pitchDifference;
    private double lastYawDifference = Double.NaN;
    private double lastPitchDifference = Double.NaN;
    private boolean canBuildNow = true;
    private boolean runtimeInitialized;

    public BlockFlyModule() {
        super("BlockFly", "Places blocks below you using OpenZen's complete scaffold flow.", ModuleCategory.WORLD);
        this.addProperties(
                this.settings.modeProperty(),
                this.settings.eagleProperty(),
                this.settings.sneakProperty(),
                this.settings.snapProperty(),
                this.settings.renderItemSpoofProperty(),
                this.settings.rotationTickProperty(),
                this.settings.clutchProperty()
        );
    }

    @Override
    protected void onEnable() {
        this.initializeRuntime();
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        this.cleanupRuntime(true);
        super.onDisable();
    }

    @Override
    public String getSuffix() {
        return this.settings.mode().toString();
    }

    @Subscribe(priority = 100)
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            this.cleanupRuntime(false);
            return;
        }
        if (!this.runtimeInitialized) {
            this.initializeRuntime();
        }
        this.runTick();
    }

    @Subscribe
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (mc.player == null || mc.world == null) {
            return;
        }
        if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket velocityPacket
                && velocityPacket.getEntityId() == mc.player.getId()) {
            final Vec3d velocity = velocityPacket.getVelocity();
            if (Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z) >= 1.5D) {
                this.velocityDelay = 60;
            }
        }
    }

    @Subscribe(priority = 100)
    public void onMouseHandleInput(final MouseHandleInputEvent event) {
        event.setCancelled();
        if (mc.currentScreen != null || mc.player == null || this.currentPlacement == null) {
            return;
        }
        if (this.isTellyMode() && this.airTicks < 1) {
            return;
        }
        final BlockFlyRotation targetRotation = BlockFlyRotationHandler.targetRotation();
        final boolean canRayTrace = BlockFlyRayTraceUtil.canRayTrace(
                targetRotation,
                this.currentPlacement.clickedFace(),
                this.currentPlacement.supportPos(),
                true
        );
        if (!this.canBuildNow && !this.isPlacementReachable(this.currentPlacement)) {
            return;
        }
        if (this.rotationDelay <= 0 && this.settings.mode() != BlockFlyMode.OLD_TELLY && !canRayTrace) {
            return;
        }
        this.doSnap();
    }

    @Subscribe(priority = 100)
    public void onJump(final JumpEvent event) {
        if (!this.canBuildNow && this.currentPlacement != null && this.rotationDelay > 0) {
            event.setCancelled();
        }
    }

    @Subscribe(priority = -100)
    public void onMoveInput(final MoveInputEvent event) {
        if (BlockFlyRotationHandler.isOwningRotation()) {
            BlockFlyMovementUtil.correctInput(event, BlockFlyRotationBridge.logicalYawOr(mc.player.getYaw()));
        }
    }

    @Subscribe(priority = -100)
    public void onPreMovementPacket(final PreMovementPacketEvent event) {
        BlockFlyRotationHandler.applyToMovementPacket(event);
    }

    @Subscribe
    public void onPostMovementPacket(final PostMovementPacketEvent event) {
        if (mc.player == null) {
            return;
        }
        if (mc.player.isOnGround()) {
            this.airTicks = 0;
            this.groundTicks++;
        } else {
            this.groundTicks = 0;
            this.airTicks++;
        }
    }

    @Subscribe
    public void onRenderWorld(final RenderWorldEvent event) {
        if (this.currentPlacement == null || mc.gameRenderer == null) {
            return;
        }
        final Vec3d min = Vec3d.of(this.currentPlacement.placePos());
        final Vec3d max = min.add(1.0D, 1.0D, 1.0D);
        final int fill = ColorUtility.rgbaToHex(74, 144, 226, 64);
        final int outline = ColorUtility.rgbaToHex(74, 144, 226, 191);
        final VertexConsumerProvider.Immediate consumers = VertexConsumerProvider.immediate(new BufferAllocator(2048));
        final WorldRenderer renderer = new WorldRenderer(consumers);
        renderer.drawFilledCube(event.matrixStack(), CustomRenderLayers.getPositionColorQuads(true), min,
                new Vec3d(1.0D, 1.0D, 1.0D), fill);
        this.drawOutline(renderer, event, min, max, outline);
        consumers.draw();
    }

    @Subscribe
    public void onRenderScreen(final RenderScreenEvent event) {
        if (mc.player == null || mc.world == null) {
            return;
        }
        final int blockCount = this.slotController.countAllBlocks();
        if (blockCount == 0) {
            return;
        }
        final String count = Integer.toString(blockCount);
        final String suffix = " Blocks";
        final int x = (mc.getWindow().getScaledWidth() - mc.textRenderer.getWidth(count + suffix)) / 2;
        final int y = mc.getWindow().getScaledHeight() / 2 - 20;
        MinecraftRenderer.addToQueue(() -> {
            event.drawContext().drawText(mc.textRenderer, Text.literal(count), x, y, 0xFF4A90E2, true);
            event.drawContext().drawText(mc.textRenderer, Text.literal(suffix),
                    x + mc.textRenderer.getWidth(count), y, -1, true);
        });
    }

    @Subscribe
    public void onJoinWorld(final JoinWorldEvent event) {
        this.cleanupRuntime(false);
    }

    @Subscribe
    public void onDisconnect(final ServerDisconnectEvent event) {
        this.cleanupRuntime(true);
        DynamicIslandElement.removeTrigger(this);
    }

    private void initializeRuntime() {
        if (mc.player == null) {
            this.runtimeInitialized = false;
            return;
        }
        this.slotController.capture();
        this.rotations.set(mc.player.getYaw() - 180.0F, mc.player.getPitch());
        this.lastRotations.set(mc.player.lastYaw - 180.0F, mc.player.lastPitch);
        this.correctRotation.set(0.0F, 0.0F);
        this.currentPlacement = null;
        this.targetYLevel = 10000;
        this.velocityDelay = 0;
        this.eagleTimer = 0;
        this.groundTicks = 0;
        this.airTicks = 0;
        this.rotationDelay = 0;
        this.jitterCounter = 0;
        this.yawDifference = 0.0D;
        this.pitchDifference = 0.0D;
        this.lastYawDifference = Double.NaN;
        this.lastPitchDifference = Double.NaN;
        this.canBuildNow = true;
        this.packetBatches.clear();
        this.packetBatches.add(new CopyOnWriteArrayList<>());
        BlockFlyDelayedTickQueue.clear();
        BlockFlyRotationHandler.activate(mc.player.getYaw(), mc.player.getPitch());
        BlockFlyRotationHandler.setTargetRotation(this.rotations);
        BlockFlyRenderSpoof.update(this.settings.renderItemSpoof(), this.slotController.originalSlot());
        this.runtimeInitialized = true;
    }

    private void cleanupRuntime(final boolean restoreSlot) {
        for (final List<Packet<?>> batch : this.packetBatches) {
            this.processBatch(batch);
        }
        this.packetBatches.clear();
        if (mc.player != null) {
            this.keyController.restorePhysicalStates();
            if (restoreSlot) {
                this.slotController.restore();
            }
        }
        BlockFlyDelayedTickQueue.clear();
        if (restoreSlot) {
            this.handoffRotationToVanilla();
        }
        BlockFlyRotationHandler.deactivate();
        BlockFlyRenderSpoof.clear();
        this.island.reset();
        this.currentPlacement = null;
        this.canBuildNow = true;
        this.runtimeInitialized = false;
    }

    private void runTick() {
        if (mc.player == null || mc.world == null) {
            return;
        }
        this.packetBatches.add(new CopyOnWriteArrayList<>());
        if (this.velocityDelay > 0) {
            this.velocityDelay--;
        }
        if (mc.player.isOnGround() && this.velocityDelay <= 30) {
            this.velocityDelay = 0;
        }

        final int placeableSlot = this.slotController.selectPlaceableSlot();
        BlockFlyRenderSpoof.update(this.settings.renderItemSpoof(), this.slotController.originalSlot());
        if (placeableSlot == -1) {
            this.setEnabled(false);
            return;
        }
        final boolean jumpHeld = this.keyController.isPhysicalJumpDown();
        if (this.targetYLevel == -1
                || this.targetYLevel > MathHelper.floor(mc.player.getY()) - 1
                || mc.player.isOnGround()
                || !BlockFlyMovementUtil.isMoving()
                || jumpHeld
                || this.settings.mode() == BlockFlyMode.NORMAL) {
            this.targetYLevel = MathHelper.floor(mc.player.getY()) - 1;
        }

        this.applyRotations();
        boolean firstGroundTick = false;
        this.canBuildNow = true;
        if (this.currentPlacement != null && placeableSlot != -1) {
            if (this.groundTicks == 1 && mc.options.jumpKey.isPressed()) {
                firstGroundTick = true;
            }
            if (this.settings.clutch() && mc.player.getVelocity().y < -0.1D) {
                final BlockFlyMotionSimulator simulator = new BlockFlyMotionSimulator(mc.player);
                simulator.simulateWithFriction(2);
                if (this.currentPlacement.supportPos().getY() > simulator.y()) {
                    this.canBuildNow = false;
                }
            }
        }
        if (mc.player.isOnGround()) {
            this.canBuildNow = true;
        }

        final BlockFlyRotation desired = this.settings.mode() == BlockFlyMode.TELLY_BRIDGE && this.canBuildNow
                ? this.getTargetRotation(firstGroundTick)
                : this.getPlayerYawRotation();
        this.correctRotation.set(desired.yaw(), desired.pitch());

        if (this.currentPlacement == null) {
            BlockFlyDelayedTickQueue.clear();
        } else if (this.settings.clutch()
                && (!this.canBuildNow || this.velocityDelay > 0)
                && this.rotationDelay <= 8) {
            this.scheduleDelayedPlacement();
        } else {
            this.canBuildNow = true;
            BlockFlyDelayedTickQueue.clear();
            this.rotationDelay = 0;
            if (this.settings.mode() == BlockFlyMode.NORMAL && this.settings.snap()) {
                this.rotations.setYaw(this.correctRotation.yaw());
            } else {
                this.rotations.setYaw(BlockFlyRotationUtil.moveTowards(
                        (float) this.getBlockDistance(),
                        this.rotations.yaw(),
                        this.correctRotation.yaw()
                ));
            }
            this.rotations.setPitch(this.correctRotation.pitch());
            this.applySneakCycle();

            if (this.isTellyMode()) {
                this.keyController.setJump(BlockFlyMovementUtil.isMoving() || jumpHeld);
                if (this.airTicks < 1 && BlockFlyMovementUtil.isMoving()) {
                    if (this.settings.mode() == BlockFlyMode.OLD_TELLY) {
                        this.rotations.setYaw(mc.player.getYaw());
                    }
                    this.finishRotationTick();
                    return;
                }
            } else if (this.settings.mode() == BlockFlyMode.KEEP_Y) {
                this.keyController.setJump(BlockFlyMovementUtil.isMoving() || jumpHeld);
            } else {
                if (this.settings.eagle()) {
                    this.keyController.setSneak(mc.player.isOnGround() && isOnBlockEdge(0.3F));
                }
                if (this.settings.snap() && !jumpHeld) {
                    this.resetSnap();
                }
            }
        }
        this.finishRotationTick();
    }

    private void scheduleDelayedPlacement() {
        if (!BlockFlyDelayedTickQueue.isEmpty()) {
            return;
        }
        final BlockFlyRotation rotationToBlock = BlockFlyRotationUtil.rotationFromVec(
                getHitVec(this.currentPlacement.supportPos(), this.currentPlacement.clickedFace()));
        final BlockFlyPlacementTarget delayedPlacement = this.currentPlacement;
        this.rotations.set(rotationToBlock.yaw(), rotationToBlock.pitch());
        BlockFlyRotationHandler.setTargetRotation(this.rotations);
        this.rotationDelay++;
        BlockFlyDelayedTickQueue.add(() -> {
            if (mc.player == null) {
                BlockFlyDelayedTickQueue.clear();
                return;
            }
            BlockFlyRotationHandler.markSentRotation(rotationToBlock);
            this.sendPacketSilent(new PlayerMoveC2SPacket.LookAndOnGround(
                    BlockFlyRotationHandler.wireYaw(rotationToBlock.yaw()),
                    rotationToBlock.pitch(),
                    mc.player.isOnGround(),
                    mc.player.horizontalCollision
            ));
        }, true);
        BlockFlyDelayedTickQueue.add(() -> {
            if (mc.player == null || mc.world == null) {
                BlockFlyDelayedTickQueue.clear();
                return;
            }
            this.currentPlacement = delayedPlacement;
            this.rotations.set(rotationToBlock.yaw(), rotationToBlock.pitch());
            BlockFlyRotationHandler.setTargetRotation(this.rotations);
            this.doSnap();
            this.canBuildNow = true;
            this.rotationDelay = 0;
        }, false);
    }

    private void applyRotations() {
        Vec3d eye = mc.player.getEyePos();
        if (!this.canBuildNow) {
            eye = eye.add(mc.player.getVelocity().multiply(2.0D));
        }
        if (this.settings.clutch() && mc.player.getVelocity().y < 0.01D) {
            final BlockFlyMotionSimulator simulator = new BlockFlyMotionSimulator(mc.player);
            simulator.simulateWithFriction(2);
            eye = new Vec3d(eye.x, Math.max(simulator.y() + mc.player.getEyeHeight(mc.player.getPose()), eye.y), eye.z);
        }
        final BlockFlyPlacementTarget target = BlockFlyPlacementSearch.findShellTarget(eye, this.targetYLevel);
        this.currentPlacement = target;
    }

    private BlockFlyRotation getPlayerYawRotation() {
        if (this.currentPlacement == null) {
            return new BlockFlyRotation();
        }
        return BlockFlyRotationUtil.rotationFromVec(
                getHitVec(this.currentPlacement.supportPos(), this.currentPlacement.clickedFace()));
    }

    private BlockFlyRotation getTargetRotation(final boolean firstGroundTick) {
        if (!BlockFlyMovementUtil.isInputActive()) {
            return this.getPlayerYawRotation();
        }
        if (this.currentPlacement == null) {
            return new BlockFlyRotation(mc.player.getYaw(), mc.player.getPitch());
        }

        final Vec3d hitVec = getHitVec(this.currentPlacement.supportPos(), this.currentPlacement.clickedFace());
        BlockFlyRotation rotation = BlockFlyRotationUtil.rotationFromVec(hitVec);
        final BlockFlyRotation previous = this.referenceRotation();
        final double yawDelta = BlockFlyRotationUtil.angleDifference(rotation.yaw(), previous.yaw());
        if (this.groundTicks > 0) {
            if (!mc.options.jumpKey.isPressed()) {
                return new BlockFlyRotation(mc.player.getYaw(), 75.5F);
            }
            switch (this.groundTicks) {
                case 1 -> {
                    if (!firstGroundTick) {
                        rotation.setYaw(previous.yaw() + BlockFlyRotationUtil.clampAngle(
                                (float) yawDelta, (float) (yawDelta / 2.0D)));
                        rotation.setPitch(75.5F);
                    } else {
                        rotation = BlockFlyRotationUtil.rotationFromVec(hitVec);
                    }
                    ((LivingEntityAccessor) mc.player).setJumpingCooldown(2);
                }
                case 2 -> {
                    return new BlockFlyRotation(mc.player.getYaw(), 75.5F);
                }
                default -> {
                }
            }
        } else {
            float limit = this.airTicks == 1 ? 90.0F : 50.0F;
            limit -= ThreadLocalRandom.current().nextFloat(0.001F, 0.006F);
            rotation.setYaw(previous.yaw()
                    + BlockFlyRotationUtil.clampAngle((float) yawDelta, limit));
        }
        rotation = this.findValidRotation(rotation, firstGroundTick);
        return this.applyStuckRotationJitter(rotation);
    }

    private BlockFlyRotation findValidRotation(
            final BlockFlyRotation rotation,
            final boolean firstGroundTick
    ) {
        if (firstGroundTick) {
            return rotation;
        }
        final BlockFlyRotation reference = this.referenceRotation();
        final BlockFlyRotation optimal = rotation.copy();
        final double delta = MathHelper.wrapDegrees(optimal.yaw() - reference.yaw());
        if (Math.abs(delta) > 90.0D) {
            optimal.setYaw((float) (reference.yaw() + Math.copySign(90.0D, delta)));
        }
        double maximumStep = Math.max(45.0D, 180.0D / Math.max(1.0D, this.settings.rotationTick()));
        if (this.settings.mode() == BlockFlyMode.TELLY_BRIDGE) {
            maximumStep = Math.max(maximumStep, 75.0D);
        }
        return BlockFlyRotationUtil.smoothRotation(reference, optimal, maximumStep);
    }

    private BlockFlyRotation applyStuckRotationJitter(BlockFlyRotation rotation) {
        final BlockFlyRotation reference = this.referenceRotation();
        this.yawDifference = Math.abs(MathHelper.wrapDegrees(rotation.yaw() - reference.yaw()));
        this.pitchDifference = Math.abs(rotation.pitch() - reference.pitch());
        final boolean stuckPitch = this.pitchDifference > 2.0D
                && !Double.isNaN(this.lastPitchDifference)
                && Math.abs(this.pitchDifference - this.lastPitchDifference) < 1.0E-4D;
        final boolean stuckYaw = this.yawDifference > 2.0D
                && !Double.isNaN(this.lastYawDifference)
                && Math.abs(this.yawDifference - this.lastYawDifference) < 1.0E-4D;
        if (stuckPitch || stuckYaw) {
            rotation = rotation.copy();
            float yawJitter = BlockFlyMathUtil.randomFloat(0.095F, 0.19F);
            final float pitchJitter = BlockFlyMathUtil.randomFloat(0.016F, 0.055F);
            if ((this.jitterCounter++ & 1) == 0) {
                yawJitter = -yawJitter;
            }
            rotation.setYaw(rotation.yaw() + yawJitter);
            rotation.setPitch(MathHelper.clamp(rotation.pitch() + pitchJitter, -89.5F, 89.5F));
            this.yawDifference = Math.abs(MathHelper.wrapDegrees(rotation.yaw() - reference.yaw()));
            this.pitchDifference = Math.abs(rotation.pitch() - reference.pitch());
        }
        this.lastYawDifference = this.yawDifference;
        this.lastPitchDifference = this.pitchDifference;
        return rotation;
    }

    private BlockFlyRotation referenceRotation() {
        final BlockFlyRotation previous = BlockFlyRotationHandler.previousRotation();
        if (previous != null) {
            return previous;
        }
        final BlockFlyRotation target = BlockFlyRotationHandler.targetRotation();
        return target != null ? target : new BlockFlyRotation(mc.player.getYaw(), mc.player.getPitch());
    }

    private void doSnap() {
        if (this.currentPlacement == null || mc.player == null || mc.interactionManager == null
                || !BlockFlyBlockUtil.isPlaceable(this.slotController.selectedStack())
                || !this.isCurrentPlacementValid()) {
            return;
        }
        final Direction face = this.currentPlacement.clickedFace();
        final boolean jumpHeld = this.keyController.isPhysicalJumpDown();
        if (face == Direction.UP
                && !mc.player.isOnGround()
                && BlockFlyMovementUtil.isMoving()
                && !jumpHeld
                && this.settings.mode() != BlockFlyMode.NORMAL) {
            return;
        }
        if (!this.shouldBuild()) {
            return;
        }
        final BlockFlyRotation targetRotation = BlockFlyRotationHandler.targetRotation();
        if (!BlockFlyRayTraceUtil.canRayTrace(
                targetRotation,
                face,
                this.currentPlacement.supportPos(),
                true
        )) {
            return;
        }
        final BlockHitResult hit = new BlockHitResult(
                getHitVec(this.currentPlacement.supportPos(), face),
                face,
                this.currentPlacement.supportPos(),
                false
        );
        final ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        if (result.isAccepted()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private boolean shouldBuild() {
        final BlockPos below = BlockPos.ofFloored(mc.player.getX(), mc.player.getY() - 0.5D, mc.player.getZ());
        return mc.world.isAir(below) && BlockFlyBlockUtil.isPlaceable(this.slotController.selectedStack());
    }

    private boolean isPlacementReachable(final BlockFlyPlacementTarget target) {
        if (target == null || mc.player == null) {
            return false;
        }
        final Vec3d faceNormal = Vec3d.of(target.clickedFace().getVector());
        final Vec3d hitPoint = getHitVec(target.supportPos(), target.clickedFace());
        final Vec3d delta = hitPoint.subtract(mc.player.getEyePos());
        return delta.lengthSquared() <= 20.25D
                && delta.normalize().dotProduct(faceNormal.multiply(-1.0D).normalize()) >= 0.0D;
    }

    private boolean isCurrentPlacementValid() {
        if (this.currentPlacement == null || mc.player == null || mc.world == null) {
            return false;
        }
        final BlockPos supportPos = this.currentPlacement.supportPos();
        final Direction clickedFace = this.currentPlacement.clickedFace();
        if (!BlockFlyBlockUtil.isAir(this.currentPlacement.placePos())
                || !BlockFlyBlockUtil.isSupportFace(supportPos, clickedFace)) {
            return false;
        }
        final Vec3d hitPoint = getHitVec(supportPos, clickedFace);
        final Vec3d eyeToFace = hitPoint.subtract(mc.player.getEyePos());
        if (eyeToFace.lengthSquared() > 20.25D) {
            return false;
        }
        return eyeToFace.dotProduct(Vec3d.of(clickedFace.getVector())) < 0.0D;
    }

    private void resetSnap() {
        if (this.currentPlacement == null) {
            return;
        }
        boolean lookingAtBlock = false;
        final BlockHitResult result = BlockFlyRayTraceUtil.rayTrace(5.0D, this.rotations);
        if (result != null && result.getType() == HitResult.Type.BLOCK
                && result.getBlockPos().equals(this.currentPlacement.supportPos())
                && result.getSide() != Direction.UP) {
            lookingAtBlock = true;
        }
        if (!lookingAtBlock && mc.player.age % 4 == 0) {
            this.rotations.setYaw(mc.player.getYaw() + ThreadLocalRandom.current().nextFloat(0.0F, 0.5F) - 0.25F);
        }
    }

    private void applySneakCycle() {
        if (!this.settings.sneak()) {
            return;
        }
        this.eagleTimer++;
        if (this.eagleTimer == 18) {
            if (mc.player.isSprinting()) {
                this.keyController.setSprint(false);
                mc.player.setSprinting(false);
            }
            this.keyController.setSneak(true);
        } else if (this.eagleTimer >= 21) {
            this.keyController.setSneak(false);
            this.eagleTimer = 0;
        }
    }

    private void finishRotationTick() {
        this.lastRotations.set(this.rotations.yaw(), this.rotations.pitch());
        BlockFlyRotationHandler.setTargetRotation(this.rotations);
    }

    private void handoffRotationToVanilla() {
        if (mc.player == null || !BlockFlyRotationHandler.isOwningRotation()) {
            return;
        }

        final BlockFlyRotation sent = BlockFlyRotationHandler.sentRotation();
        final BlockFlyRotation target = BlockFlyRotationHandler.targetRotation();
        final BlockFlyRotation referenceRotation = sent != null ? sent : target;
        if (referenceRotation == null) {
            return;
        }

        final float wireReference = BlockFlyRotationHandler.wireYaw(referenceRotation.yaw());
        final float exitYaw = wireReference + MathHelper.wrapDegrees(mc.player.getYaw() - wireReference);
        mc.player.setYaw(exitYaw);
        mc.player.lastYaw = exitYaw;
        mc.player.setHeadYaw(exitYaw);
        mc.player.setBodyYaw(exitYaw);
    }

    private double getBlockDistance() {
        if (this.settings.mode() == BlockFlyMode.OLD_TELLY) {
            return 180.0D;
        }
        return Math.max(Math.max(60.0D, 360.0D / this.settings.rotationTick()), 180.0D);
    }

    private boolean isTellyMode() {
        return this.settings.mode() == BlockFlyMode.TELLY_BRIDGE
                || this.settings.mode() == BlockFlyMode.OLD_TELLY;
    }

    private void processBatch(final List<Packet<?>> batch) {
        for (final Packet<?> packet : List.copyOf(batch)) {
            batch.remove(packet);
            this.sendPacketSilent(packet);
        }
    }

    private void sendPacketSilent(final Packet<?> packet) {
        if (packet == null || mc.getNetworkHandler() == null) {
            return;
        }
        final ClientConnection connection = mc.getNetworkHandler().getConnection();
        ((ClientConnectionAccess) connection).oraculus$sendPacketSilent(packet);
    }

    private void drawOutline(
            final WorldRenderer renderer,
            final RenderWorldEvent event,
            final Vec3d min,
            final Vec3d max,
            final int color
    ) {
        final Vec3d[] corners = {
                new Vec3d(min.x, min.y, min.z), new Vec3d(max.x, min.y, min.z),
                new Vec3d(max.x, min.y, max.z), new Vec3d(min.x, min.y, max.z),
                new Vec3d(min.x, max.y, min.z), new Vec3d(max.x, max.y, min.z),
                new Vec3d(max.x, max.y, max.z), new Vec3d(min.x, max.y, max.z)
        };
        final int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0},
                {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        for (final int[] edge : edges) {
            renderer.drawLine(event.matrixStack(), CustomRenderLayers.getLines(1.5F, true),
                    corners[edge[0]], corners[edge[1]], color);
        }
    }

    public ItemStack getDisplayedBlockStack() {
        final ItemStack selected = this.slotController.selectedStack();
        if (selected.getItem() instanceof net.minecraft.item.BlockItem) {
            return selected;
        }
        final int slot = this.slotController.findPlaceableSlot();
        return slot == -1 || mc.player == null ? ItemStack.EMPTY : mc.player.getInventory().getStack(slot);
    }

    public static Vec3d getHitVec(final BlockPos pos, final Direction direction) {
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        switch (direction) {
            case DOWN -> y = pos.getY() + 0.001D;
            case UP -> y = pos.getY() + 0.999D;
            case NORTH -> z = pos.getZ() + 0.001D;
            case SOUTH -> z = pos.getZ() + 0.999D;
            case WEST -> x = pos.getX() + 0.001D;
            case EAST -> x = pos.getX() + 0.999D;
        }
        return new Vec3d(x, y, z);
    }

    public static boolean isOnBlockEdge(final float inflate) {
        if (mc.player == null || mc.world == null) {
            return false;
        }
        final Box box = mc.player.getBoundingBox().offset(0.0D, -0.5D, 0.0D)
                .expand(-inflate, 0.0D, -inflate);
        return !mc.world.getBlockCollisions(mc.player, box).iterator().hasNext();
    }

    @Override
    public void renderIsland(
            final DrawContext context,
            final float posX,
            final float posY,
            final float width,
            final float height,
            final float progress
    ) {
        this.island.render(context, posX, posY);
    }

    @Override
    public float getIslandWidth() {
        return this.island.width();
    }

    @Override
    public float getIslandHeight() {
        return 25.0F;
    }

    @Override
    public int getIslandPriority() {
        return 2;
    }
}

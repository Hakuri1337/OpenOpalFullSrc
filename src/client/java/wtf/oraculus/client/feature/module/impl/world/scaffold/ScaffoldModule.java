package wtf.oraculus.client.feature.module.impl.world.scaffold;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.item.ItemStack;
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
import wtf.oraculus.client.feature.module.impl.world.scaffold.block.ScaffoldBlockUtil;
import wtf.oraculus.client.feature.module.impl.world.scaffold.block.ScaffoldPlacementSearch;
import wtf.oraculus.client.feature.module.impl.world.scaffold.input.ScaffoldKeyStateController;
import wtf.oraculus.client.feature.module.impl.world.scaffold.inventory.ScaffoldSlotController;
import wtf.oraculus.client.feature.module.impl.world.scaffold.math.ScaffoldMathUtil;
import wtf.oraculus.client.feature.module.impl.world.scaffold.movement.ScaffoldMovementUtil;
import wtf.oraculus.client.feature.module.impl.world.scaffold.raycast.ScaffoldRayTraceUtil;
import wtf.oraculus.client.feature.module.impl.world.scaffold.render.ScaffoldRenderSpoof;
import wtf.oraculus.client.feature.module.impl.world.scaffold.rotation.ScaffoldRotation;
import wtf.oraculus.client.feature.module.impl.world.scaffold.rotation.ScaffoldRotationBridge;
import wtf.oraculus.client.feature.module.impl.world.scaffold.rotation.ScaffoldRotationHandler;
import wtf.oraculus.client.feature.module.impl.world.scaffold.rotation.ScaffoldRotationUtil;
import wtf.oraculus.client.feature.module.impl.world.scaffold.state.ScaffoldPlacementTarget;
import wtf.oraculus.client.feature.module.impl.visual.overlay.impl.dynamicisland.DynamicIslandElement;
import wtf.oraculus.client.renderer.MinecraftRenderer;
import wtf.oraculus.client.renderer.world.WorldRenderer;
import wtf.oraculus.event.impl.game.JoinWorldEvent;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.input.MouseHandleInputEvent;
import wtf.oraculus.event.impl.game.input.MoveInputEvent;
import wtf.oraculus.event.impl.game.player.movement.PostMovementPacketEvent;
import wtf.oraculus.event.impl.game.player.movement.PreMovementPacketEvent;
import wtf.oraculus.event.impl.game.server.ServerDisconnectEvent;
import wtf.oraculus.event.impl.render.RenderScreenEvent;
import wtf.oraculus.event.impl.render.RenderWorldEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.mixin.LivingEntityAccessor;
import wtf.oraculus.utility.render.ColorUtility;
import wtf.oraculus.utility.render.CustomRenderLayers;

import java.util.concurrent.ThreadLocalRandom;

import static wtf.oraculus.client.Constants.mc;

public final class ScaffoldModule extends Module implements IslandTrigger {
    private final ScaffoldSettings settings = new ScaffoldSettings();
    private final ScaffoldSlotController slotController = new ScaffoldSlotController();
    private final ScaffoldKeyStateController keyController = new ScaffoldKeyStateController();
    private final ScaffoldIsland island = new ScaffoldIsland(this);

    private final ScaffoldRotation correctRotation = new ScaffoldRotation();
    private final ScaffoldRotation rotations = new ScaffoldRotation();
    private final ScaffoldRotation lastRotations = new ScaffoldRotation();

    private ScaffoldPlacementTarget currentPlacement;
    private int targetYLevel = -1;
    private int eagleTimer;
    private int groundTicks;
    private int airTicks;
    private int jitterCounter;
    private double yawDifference;
    private double pitchDifference;
    private double lastYawDifference = Double.NaN;
    private double lastPitchDifference = Double.NaN;
    private boolean runtimeInitialized;

    public ScaffoldModule() {
        super("Scaffold", "Places blocks below you using OpenZen's complete scaffold flow.", ModuleCategory.WORLD);
        this.addProperties(
                this.settings.implementationMarkerProperty(),
                this.settings.modeProperty(),
                this.settings.eagleProperty(),
                this.settings.sneakProperty(),
                this.settings.snapProperty(),
                this.settings.renderItemSpoofProperty(),
                this.settings.rotationTickProperty()
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

    @Subscribe(priority = 100)
    public void onMouseHandleInput(final MouseHandleInputEvent event) {
        event.setCancelled();
        if (mc.currentScreen != null || mc.player == null || this.currentPlacement == null) {
            return;
        }
        if (this.isTellyMode() && this.airTicks < 1) {
            return;
        }
        final ScaffoldRotation targetRotation = ScaffoldRotationHandler.targetRotation();
        final boolean canRayTrace = ScaffoldRayTraceUtil.canRayTrace(
                targetRotation,
                this.currentPlacement.clickedFace(),
                this.currentPlacement.supportPos(),
                false
        );
        if (this.settings.mode() != ScaffoldMode.OLD_TELLY && !canRayTrace) {
            return;
        }
        this.doSnap();
    }

    @Subscribe(priority = -100)
    public void onMoveInput(final MoveInputEvent event) {
        if (ScaffoldRotationHandler.isOwningRotation()) {
            ScaffoldMovementUtil.correctInput(event, ScaffoldRotationBridge.logicalYawOr(mc.player.getYaw()));
        }
    }

    @Subscribe(priority = -100)
    public void onPreMovementPacket(final PreMovementPacketEvent event) {
        ScaffoldRotationHandler.applyToMovementPacket(event);
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
        this.eagleTimer = 0;
        this.groundTicks = 0;
        this.airTicks = 0;
        this.jitterCounter = 0;
        this.yawDifference = 0.0D;
        this.pitchDifference = 0.0D;
        this.lastYawDifference = Double.NaN;
        this.lastPitchDifference = Double.NaN;
        ScaffoldRotationHandler.activate(mc.player.getYaw(), mc.player.getPitch());
        ScaffoldRotationHandler.setTargetRotation(this.rotations);
        ScaffoldRenderSpoof.update(this.settings.renderItemSpoof(), this.slotController.originalSlot());
        this.runtimeInitialized = true;
    }

    private void cleanupRuntime(final boolean restoreSlot) {
        if (mc.player != null) {
            this.keyController.restorePhysicalStates();
            if (restoreSlot) {
                this.slotController.restore();
            }
        }
        if (restoreSlot) {
            this.handoffRotationToVanilla();
        }
        ScaffoldRotationHandler.deactivate();
        ScaffoldRenderSpoof.clear();
        this.island.reset();
        this.currentPlacement = null;
        this.runtimeInitialized = false;
    }

    private void runTick() {
        if (mc.player == null || mc.world == null) {
            return;
        }
        final int placeableSlot = this.slotController.selectPlaceableSlot();
        ScaffoldRenderSpoof.update(this.settings.renderItemSpoof(), this.slotController.originalSlot());
        final boolean jumpHeld = this.keyController.isPhysicalJumpDown();
        if (this.targetYLevel == -1
                || this.targetYLevel > MathHelper.floor(mc.player.getY()) - 1
                || mc.player.isOnGround()
                || !ScaffoldMovementUtil.isMoving()
                || jumpHeld
                || this.settings.mode() == ScaffoldMode.NORMAL) {
            this.targetYLevel = MathHelper.floor(mc.player.getY()) - 1;
        }

        this.applyRotations();
        boolean firstGroundTick = false;
        if (this.currentPlacement != null && placeableSlot != -1) {
            if (this.groundTicks == 1 && mc.options.jumpKey.isPressed()) {
                firstGroundTick = true;
            }
        }

        final ScaffoldRotation desired = this.settings.mode() == ScaffoldMode.TELLY_BRIDGE
                ? this.getTargetRotation(firstGroundTick)
                : this.getPlayerYawRotation();
        this.correctRotation.set(desired.yaw(), desired.pitch());

        if (this.settings.mode() == ScaffoldMode.NORMAL && this.settings.snap()) {
            this.rotations.setYaw(this.correctRotation.yaw());
        } else {
            this.rotations.setYaw(ScaffoldRotationUtil.moveTowards(
                    (float) this.getBlockDistance(),
                    this.rotations.yaw(),
                    this.correctRotation.yaw()
            ));
        }
        this.rotations.setPitch(this.correctRotation.pitch());
        this.applySneakCycle();

        if (this.isTellyMode()) {
            this.keyController.setJump(ScaffoldMovementUtil.isMoving() || jumpHeld);
            if (this.airTicks < 1 && ScaffoldMovementUtil.isMoving()) {
                if (this.settings.mode() == ScaffoldMode.OLD_TELLY) {
                    this.rotations.setYaw(mc.player.getYaw());
                }
                this.finishRotationTick();
                return;
            }
        } else if (this.settings.mode() == ScaffoldMode.KEEP_Y) {
            this.keyController.setJump(ScaffoldMovementUtil.isMoving() || jumpHeld);
        } else {
            if (this.settings.eagle()) {
                this.keyController.setSneak(mc.player.isOnGround() && isOnBlockEdge(0.3F));
            }
            if (this.settings.snap() && !jumpHeld) {
                this.resetSnap();
            }
        }
        this.finishRotationTick();
    }

    private void applyRotations() {
        final Vec3d eye = mc.player.getEyePos();
        final ScaffoldPlacementTarget target = ScaffoldPlacementSearch.findShellTarget(eye, this.targetYLevel);
        if (target != null) {
            this.currentPlacement = target;
        }
    }

    private ScaffoldRotation getPlayerYawRotation() {
        if (this.currentPlacement == null) {
            return new ScaffoldRotation();
        }
        return ScaffoldRotationUtil.rotationToBlock(this.currentPlacement.supportPos(), 0.0F);
    }

    private ScaffoldRotation getTargetRotation(final boolean firstGroundTick) {
        if (!ScaffoldMovementUtil.isInputActive()) {
            return this.getPlayerYawRotation();
        }
        if (this.currentPlacement == null) {
            return new ScaffoldRotation(mc.player.getYaw(), mc.player.getPitch());
        }

        final Vec3d hitVec = getHitVec(this.currentPlacement.supportPos(), this.currentPlacement.clickedFace());
        ScaffoldRotation rotation = ScaffoldRotationUtil.rotationFromVec(hitVec);
        final ScaffoldRotation previous = this.referenceRotation();
        final double yawDelta = ScaffoldRotationUtil.angleDifference(rotation.yaw(), previous.yaw());
        if (this.groundTicks > 0) {
            if (!mc.options.jumpKey.isPressed()) {
                return new ScaffoldRotation(mc.player.getYaw(), 75.5F);
            }
            switch (this.groundTicks) {
                case 1 -> {
                    if (!firstGroundTick) {
                        rotation.setYaw(previous.yaw() + ScaffoldRotationUtil.clampAngle(
                                (float) yawDelta, (float) (yawDelta / 2.0D)));
                        rotation.setPitch(75.5F);
                    } else {
                        rotation = ScaffoldRotationUtil.rotationFromVec(hitVec);
                    }
                    ((LivingEntityAccessor) mc.player).setJumpingCooldown(2);
                }
                case 2 -> {
                    return new ScaffoldRotation(mc.player.getYaw(), 75.5F);
                }
                default -> {
                }
            }
        } else {
            float limit = this.airTicks == 1 ? 90.0F : 50.0F;
            limit -= ThreadLocalRandom.current().nextFloat(0.001F, 0.006F);
            rotation.setYaw(previous.yaw()
                    + ScaffoldRotationUtil.clampAngle((float) yawDelta, limit));
        }
        rotation = this.findValidRotation(rotation, firstGroundTick);
        return this.applyStuckRotationJitter(rotation);
    }

    private ScaffoldRotation findValidRotation(
            final ScaffoldRotation rotation,
            final boolean firstGroundTick
    ) {
        if (firstGroundTick) {
            return rotation;
        }
        final ScaffoldRotation reference = this.referenceRotation();
        final ScaffoldRotation optimal = rotation.copy();
        final double delta = MathHelper.wrapDegrees(optimal.yaw() - reference.yaw());
        if (Math.abs(delta) > 90.0D) {
            optimal.setYaw((float) (reference.yaw() + Math.copySign(90.0D, delta)));
        }
        double maximumStep = Math.max(45.0D, 180.0D / Math.max(1.0D, this.settings.rotationTick()));
        if (this.settings.mode() == ScaffoldMode.TELLY_BRIDGE) {
            maximumStep = Math.max(maximumStep, 75.0D);
        }
        return ScaffoldRotationUtil.smoothRotation(reference, optimal, maximumStep);
    }

    private ScaffoldRotation applyStuckRotationJitter(ScaffoldRotation rotation) {
        final ScaffoldRotation reference = this.referenceRotation();
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
            float yawJitter = ScaffoldMathUtil.randomFloat(0.095F, 0.19F);
            final float pitchJitter = ScaffoldMathUtil.randomFloat(0.016F, 0.055F);
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

    private ScaffoldRotation referenceRotation() {
        final ScaffoldRotation previous = ScaffoldRotationHandler.previousRotation();
        if (previous != null) {
            return previous;
        }
        final ScaffoldRotation target = ScaffoldRotationHandler.targetRotation();
        return target != null ? target : new ScaffoldRotation(mc.player.getYaw(), mc.player.getPitch());
    }

    private void doSnap() {
        if (this.currentPlacement == null || mc.player == null || mc.interactionManager == null
                || !ScaffoldBlockUtil.isPlaceable(this.slotController.selectedStack())) {
            return;
        }
        final Direction face = this.currentPlacement.clickedFace();
        final boolean jumpHeld = this.keyController.isPhysicalJumpDown();
        if (face == Direction.UP
                && !mc.player.isOnGround()
                && ScaffoldMovementUtil.isMoving()
                && !jumpHeld
                && this.settings.mode() != ScaffoldMode.NORMAL) {
            return;
        }
        if (!this.shouldBuild()) {
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
        return mc.world.isAir(below) && ScaffoldBlockUtil.isPlaceable(this.slotController.selectedStack());
    }

    private void resetSnap() {
        if (this.currentPlacement == null) {
            return;
        }
        boolean lookingAtBlock = false;
        final BlockHitResult result = ScaffoldRayTraceUtil.rayTrace(5.0D, this.rotations);
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
        ScaffoldRotationHandler.setTargetRotation(this.rotations);
    }

    private void handoffRotationToVanilla() {
        if (mc.player == null || !ScaffoldRotationHandler.isOwningRotation()) {
            return;
        }

        final ScaffoldRotation sent = ScaffoldRotationHandler.sentRotation();
        final ScaffoldRotation target = ScaffoldRotationHandler.targetRotation();
        final ScaffoldRotation referenceRotation = sent != null ? sent : target;
        if (referenceRotation == null) {
            return;
        }

        final float wireReference = ScaffoldRotationHandler.wireYaw(referenceRotation.yaw());
        final float exitYaw = wireReference + MathHelper.wrapDegrees(mc.player.getYaw() - wireReference);
        mc.player.setYaw(exitYaw);
        mc.player.lastYaw = exitYaw;
        mc.player.setHeadYaw(exitYaw);
        mc.player.setBodyYaw(exitYaw);
    }

    private double getBlockDistance() {
        if (this.settings.mode() == ScaffoldMode.OLD_TELLY) {
            return 180.0D;
        }
        return Math.max(Math.max(60.0D, 360.0D / this.settings.rotationTick()), 180.0D);
    }

    private boolean isTellyMode() {
        return this.settings.mode() == ScaffoldMode.TELLY_BRIDGE
                || this.settings.mode() == ScaffoldMode.OLD_TELLY;
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
        if (direction != Direction.UP && direction != Direction.DOWN) {
            y += ScaffoldMathUtil.randomDouble(0.3D, -0.3D);
        } else {
            x += ScaffoldMathUtil.randomDouble(0.3D, -0.3D);
            z += ScaffoldMathUtil.randomDouble(0.3D, -0.3D);
        }
        if (direction == Direction.WEST || direction == Direction.EAST) {
            z += ScaffoldMathUtil.randomDouble(0.3D, -0.3D);
        }
        if (direction == Direction.SOUTH || direction == Direction.NORTH) {
            x += ScaffoldMathUtil.randomDouble(0.3D, -0.3D);
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

package wtf.oraculus.client.feature.module.impl.world.ssngscaffold;

import net.minecraft.block.*;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.helper.impl.player.packet.blockage.impl.OutboundNetworkBlockage;
import wtf.oraculus.client.feature.helper.impl.player.slot.SlotHelper;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.impl.utility.BlinkModule;
import wtf.oraculus.client.feature.module.impl.world.scaffold.ScaffoldModule;
import wtf.oraculus.client.feature.module.property.impl.ColorProperty;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.mode.ModeProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.event.EventDispatcher;
import wtf.oraculus.event.impl.game.JoinWorldEvent;
import wtf.oraculus.event.impl.game.PostGameTickEvent;
import wtf.oraculus.event.impl.game.input.MoveInputEvent;
import wtf.oraculus.event.impl.game.player.interaction.block.BlockPlacedEvent;
import wtf.oraculus.event.impl.game.player.interaction.block.SsngVanillaPlaceEvent;
import wtf.oraculus.event.impl.game.player.movement.PostMovementPacketEvent;
import wtf.oraculus.event.impl.game.player.movement.PreMovementPacketEvent;
import wtf.oraculus.event.impl.game.player.movement.SlowdownEvent;
import wtf.oraculus.event.impl.game.server.ServerDisconnectEvent;
import wtf.oraculus.event.impl.game.player.rotation.SsngRotationCalculationEvent;
import wtf.oraculus.event.impl.render.RenderScreenEvent;
import wtf.oraculus.event.impl.render.RenderWorldEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.client.renderer.world.WorldRenderer;
import wtf.oraculus.client.renderer.NVGRenderer;
import wtf.oraculus.utility.render.CustomRenderLayers;
import mixin.LivingEntityAccessor;
import wtf.oraculus.utility.misc.chat.ChatUtility;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static wtf.oraculus.client.Constants.mc;

/** Independent SSNG Scaffold/Clutch module. */
public final class SsngScaffoldModule extends Module {
    private static final Set<Block> INVALID_SUPPORT_BLOCKS = Set.of(
            Blocks.ANVIL, Blocks.AIR, Blocks.WATER, Blocks.FIRE, Blocks.LAVA, Blocks.SKELETON_SKULL,
            Blocks.OAK_SIGN, Blocks.TRAPPED_CHEST, Blocks.CHEST, Blocks.ENCHANTING_TABLE,
            Blocks.ENDER_CHEST, Blocks.CRAFTING_TABLE, Blocks.DAYLIGHT_DETECTOR, Blocks.COBWEB,
            Blocks.SHORT_GRASS, Blocks.FLOWER_POT, Blocks.CHORUS_FLOWER, Blocks.SUNFLOWER,
            Blocks.CORNFLOWER, Blocks.TORCHFLOWER, Blocks.OAK_BUTTON, Blocks.ACACIA_BUTTON,
            Blocks.BIRCH_BUTTON, Blocks.CRIMSON_BUTTON, Blocks.CHERRY_BUTTON, Blocks.DARK_OAK_BUTTON,
            Blocks.JUNGLE_BUTTON, Blocks.STONE_BUTTON, Blocks.WARPED_BUTTON, Blocks.SPRUCE_BUTTON,
            Blocks.NOTE_BLOCK, Blocks.PLAYER_HEAD
    );

    public enum Mode { TELLY("Telly"), SNAP("Snap"), NORMAL("Normal");
        private final String text; Mode(String text) { this.text = text; } public String toString() { return text; } }
    public enum JumpMode { NORMAL("Normal"), PARKOUR("Parkour"), NONE("None");
        private final String text; JumpMode(String text) { this.text = text; } public String toString() { return text; } }
    public enum BlockCountStyle { RETRO("Retro"), OLD("Old");
        private final String text; BlockCountStyle(String text) { this.text = text; } public String toString() { return text; } }

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.TELLY);
    private final BooleanProperty alwaysUpdateRotation = new BooleanProperty("Always Update Rotation", false);
    private final NumberProperty placeTick = new NumberProperty("Place Tick", 1, 1, 5, 1);
    private final NumberProperty rotationTick = new NumberProperty("Rotation Tick", 1, 1, 5, 1);
    private final BooleanProperty spoofItem = new BooleanProperty("Spoof Item", true);
    private final BooleanProperty noSwing = new BooleanProperty("No Swing", false);
    private final BooleanProperty eagle = new BooleanProperty("Eagle", false);
    private final BooleanProperty snap = new BooleanProperty("Snap", false);
    private final BooleanProperty noUptelly = new BooleanProperty("No Uptelly", true);
    private final BooleanProperty godBridge = new BooleanProperty("GodBridge", false);
    private final BooleanProperty heypixelUpTelly = new BooleanProperty("Heypixel UpTelly", true);
    private final BooleanProperty safeMode = new BooleanProperty("Safe Mode", false);
    private final BooleanProperty testOnGround = new BooleanProperty("Test OnGround", false);
    private final BooleanProperty fixRotation = new BooleanProperty("Fix Rotation", true);
    private final BooleanProperty slowUpTelly = new BooleanProperty("Slow UpTelly", false);
    private final BooleanProperty blockFly = new BooleanProperty("Block Fly", false);
    private final ModeProperty<SsngInventoryUtil.BlockSlotMode> blockSlotMode = new ModeProperty<>("Block Slot Mode", SsngInventoryUtil.BlockSlotMode.FARTHEST);
    private final ModeProperty<JumpMode> jumpMode = new ModeProperty<>("Jump Mode", JumpMode.NORMAL);
    private final NumberProperty safeDistance = new NumberProperty("Clutch Safe Distance", 4.5D, 1.0D, 5.0D, 0.25D);
    private final NumberProperty eagleTick = new NumberProperty("Eagle Tick", 1, 1, 5, 1);
    private final NumberProperty keepEagleTick = new NumberProperty("Keep Eagle Tick", 1, 1, 5, 1);
    private final BooleanProperty debug = new BooleanProperty("Debug", false);
    private final BooleanProperty keepFov = new BooleanProperty("Keep Fov", true);
    private final NumberProperty fov = new NumberProperty("Fov", 1.1D, 1.0D, 2.1D, 0.05D);
    private final BooleanProperty mark = new BooleanProperty("Mark", true);
    private final ColorProperty markSideColor = new ColorProperty("Mark Side Color", 0x46FFFFFF);
    private final ColorProperty markLineColor = new ColorProperty("Mark Line Color", 0x96FFFFFF);
    private final BooleanProperty blockCount = new BooleanProperty("Block Count", true);
    private final ModeProperty<BlockCountStyle> blockCountStyle = new ModeProperty<>("Block Count Style", BlockCountStyle.RETRO);
    private final NumberProperty blockCountOffset = new NumberProperty("Block Count Y Offset", 0, 0, 200, 1);
    private final BooleanProperty duplicateRotPlace = new BooleanProperty("Duplicate Rot Place", true);

    private SsngInventoryUtil.SlotData blockSlot;
    private SsngBlockData blockData, lastBlockData;
    private SsngRotation lastRotation;
    private double posY;
    private int rotateCount, placeCount, tellyJumpTicks, ups;
    private boolean canPlace, waitingForEagleSneak;
    private int oldSlot = -1, startHotbarCount = 1;
    private BlockPos lastPlacePosition;
    private float lastPlacePitchDiff;

    public SsngScaffoldModule() {
        super("SSNG Scaffold", "Southside NextGen Scaffold and Clutch port.", ModuleCategory.WORLD);
        jumpMode.hideIf(() -> mode.getValue() != Mode.TELLY);
        placeTick.hideIf(() -> mode.getValue() != Mode.TELLY);
        heypixelUpTelly.hideIf(() -> mode.getValue() != Mode.TELLY);
        safeMode.hideIf(() -> !heypixelUpTelly.getValue());
        godBridge.hideIf(() -> mode.getValue() != Mode.NORMAL);
        eagleTick.hideIf(() -> !eagle.getValue());
        keepEagleTick.hideIf(() -> !eagle.getValue());
        fov.hideIf(() -> !keepFov.getValue());
        markSideColor.hideIf(() -> !mark.getValue());
        markLineColor.hideIf(() -> !mark.getValue());
        blockCountStyle.hideIf(() -> !blockCount.getValue());
        blockCountOffset.hideIf(() -> !blockCount.getValue());
        addProperties(mode, alwaysUpdateRotation, placeTick, rotationTick, spoofItem, noSwing, eagle, snap,
                noUptelly, godBridge, heypixelUpTelly, safeMode, testOnGround, fixRotation, slowUpTelly, blockFly,
                blockSlotMode, jumpMode, safeDistance, eagleTick, keepEagleTick, debug,
                keepFov, fov, mark, markSideColor, markLineColor, blockCount, blockCountStyle,
                blockCountOffset, duplicateRotPlace);
    }

    @Override
    protected void onEnable() {
        final ScaffoldModule old = OraculusClient.getInstance().getModuleRepository().getModule(ScaffoldModule.class);
        if (old != null && old.isEnabled()) old.setEnabled(false);
        this.oldSlot = mc.player == null ? -1 : mc.player.getInventory().getSelectedSlot();
        this.blockSlot = null; this.blockData = null; this.lastBlockData = null; this.lastRotation = null;
        this.lastPlacePosition = null; this.rotateCount = 0; this.placeCount = 0; this.tellyJumpTicks = 0;
        this.ups = 0; this.waitingForEagleSneak = false; this.canPlace = true;
        this.startHotbarCount = Math.max(1, SsngInventoryUtil.countHotbar());
        SsngMovementUtil.reset(); SsngRotationUtils.reset(); SsngPacketOrderManager.release();
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        if (mc.player != null) {
            SlotHelper.getInstance().stop();
            if (oldSlot >= 0 && oldSlot < 9) mc.player.getInventory().setSelectedSlot(oldSlot);
            restoreKey(mc.options.sneakKey); restoreKey(mc.options.jumpKey);
        }
        SsngPacketOrderManager.release(); SsngMovementUtil.reset(); SsngRotationUtils.reset();
        this.blockSlot = null; this.blockData = null; this.lastBlockData = null; this.lastRotation = null;
        super.onDisable();
    }

    private void restoreKey(final KeyBinding key) { key.setPressed(false); }

    @Override
    public String getSuffix() { return this.mode.getValue().toString(); }

    @Subscribe(priority = 100)
    public void onRotationCalculation(final SsngRotationCalculationEvent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) { resetRuntime(); return; }
        this.selectSlot();
        this.calculateRotation();
    }

    @Subscribe
    public void onPostGameTick(final PostGameTickEvent event) {
        SsngPacketOrderManager.tick();
        if (blockFly.getValue() && SsngPacketOrderManager.isDesyncing()
                && SsngPacketOrderManager.desyncTick() > 16) {
            SsngPacketOrderManager.release();
        }
    }

    @Subscribe(priority = 100)
    public void onMoveInput(final MoveInputEvent event) {
        if (mc.player == null) return;
        if (blockSlot != null && !blockSlot.invalid() && mode.getValue() == Mode.TELLY
                && SsngMovementUtil.onGroundTicks() > (safeMode.getValue() && heypixelUpTelly.getValue() && !testOnGround.getValue() ? 1 : 0)
                && !mc.options.jumpKey.isPressed() && SsngMovementUtil.isMoving()) {
            switch (jumpMode.getValue()) {
                case NORMAL -> event.setJump(true);
                case PARKOUR -> {
                    final float yaw = mc.player.getYaw();
                    final double rad = Math.toRadians(yaw);
                    final double x = -Math.sin(rad), z = Math.cos(rad);
                    final BlockPos p1 = new BlockPos((int) (mc.player.getX() + x),
                            (int) (mc.player.getY() - 0.1D), (int) (mc.player.getZ() + z));
                    final BlockPos p2 = new BlockPos((int) (mc.player.getX() + x * 2.0D),
                            (int) (mc.player.getY() - 0.1D), (int) (mc.player.getZ() + z * 2.0D));
                    if (mc.world.getBlockState(p1).isAir() || mc.world.getBlockState(p2).isAir()) event.setJump(true);
                }
                case NONE -> { }
            }
            if (eagle.getValue()) { waitingForEagleSneak = true; tellyJumpTicks = 0; }
        }
        if (mode.getValue() == Mode.TELLY && eagle.getValue()) event.setSneak(placeCount % 4 == 0);
    }

    @Subscribe(priority = -100)
    public void onMoveInputCorrection(final MoveInputEvent event) {
        SsngMovementUtil.apply(event);
        SsngRotationUtils.correctInput(event);
    }

    @Subscribe(priority = -100)
    public void onPreMovementPacket(final PreMovementPacketEvent event) { SsngRotationUtils.apply(event); }

    @Subscribe
    public void onPostMovementPacket(final PostMovementPacketEvent event) { SsngMovementUtil.tick(); }

    @Subscribe(priority = 100)
    public void onRotationApplied(final wtf.oraculus.event.impl.game.player.rotation.SsngRotationAppliedEvent event) { this.place(); }

    @Subscribe
    public void onVanillaPlace(final SsngVanillaPlaceEvent event) { event.setCancelled(); }

    @Subscribe
    public void onRenderScreen(final RenderScreenEvent event) {
        if (mc.player == null || !blockCount.getValue()) return;
        final int count = SsngInventoryUtil.countHotbar();
        if (count > startHotbarCount) startHotbarCount = count;
        final float rate = MathHelper.clamp((float) count / startHotbarCount, 0.0F, 1.0F);
        final float centerX = mc.getWindow().getScaledWidth() / 2.0F;
        final float y = mc.getWindow().getScaledHeight() / 2.0F + 15.0F + blockCountOffset.getValue().floatValue();
        final String countText = Integer.toString(count);
        final String label = "Blocks";
        final int countColor = blockCountColor(count, false);
        if (blockCountStyle.getValue() == BlockCountStyle.OLD) {
            final String text = countText + " " + label;
            event.drawContext().drawText(mc.textRenderer, text,
                    Math.round(centerX - mc.textRenderer.getWidth(text) / 2.0F), Math.round(y), countColor, true);
            return;
        }

        final float height = 26.0F;
        final float itemOffset = 4.0F;
        final float itemSize = 18.0F;
        final float padding = 5.0F;
        final int gap = 3;
        final int textWidth = mc.textRenderer.getWidth(label) + gap + mc.textRenderer.getWidth(countText);
        final float width = itemOffset + itemSize + padding + textWidth + padding;
        final float x = centerX - width / 2.0F;
        final float textX = x + itemOffset + itemSize + padding;
        final int textY = Math.round(y + (height - mc.textRenderer.fontHeight) / 2.0F);

        NVGRenderer.roundedRect(x, y, width, height, 5.0F, 0x5A000000);
        NVGRenderer.scissor(x, y, width * rate, height,
                () -> NVGRenderer.roundedRect(x, y, width, height, 5.0F, 0x5AFFFFFF));
        event.drawContext().drawText(mc.textRenderer, label, Math.round(textX), textY, 0xFFDCDCDC, false);
        event.drawContext().drawText(mc.textRenderer, countText,
                Math.round(textX) + mc.textRenderer.getWidth(label) + gap, textY, countColor, false);
        if (rate > 0.0F) {
            event.drawContext().enableScissor(Math.round(x), Math.round(y), Math.round(x + width * rate), Math.round(y + height));
            event.drawContext().drawText(mc.textRenderer, label, Math.round(textX), textY, 0xCC000000, false);
            event.drawContext().drawText(mc.textRenderer, countText,
                    Math.round(textX) + mc.textRenderer.getWidth(label) + gap, textY,
                    blockCountColor(count, true), false);
            event.drawContext().disableScissor();
        }

        final ItemStack stack = selectedBlockStack();
        if (!stack.isEmpty()) {
            final var matrices = event.drawContext().getMatrices();
            matrices.pushMatrix();
            matrices.translate(x + itemOffset, y + (height - itemSize) / 2.0F);
            matrices.scale(itemSize / 16.0F, itemSize / 16.0F);
            event.drawContext().drawItem(stack, 0, 0);
            matrices.popMatrix();
        }
    }

    @Subscribe
    public void onRenderWorld(final RenderWorldEvent event) {
        if (!mark.getValue() || lastPlacePosition == null) return;
        final Vec3d min = Vec3d.of(lastPlacePosition), max = min.add(1.0D, 1.0D, 1.0D);
        final VertexConsumerProvider.Immediate consumers = VertexConsumerProvider.immediate(new BufferAllocator(1024));
        final WorldRenderer renderer = new WorldRenderer(consumers);
        renderer.drawFilledCube(event.matrixStack(), CustomRenderLayers.getPositionColorQuads(true), min,
                new Vec3d(1.0D, 1.0D, 1.0D), markSideColor.getValue());
        final Vec3d[] corners = {new Vec3d(min.x,min.y,min.z),new Vec3d(max.x,min.y,min.z),new Vec3d(max.x,min.y,max.z),new Vec3d(min.x,min.y,max.z),new Vec3d(min.x,max.y,min.z),new Vec3d(max.x,max.y,min.z),new Vec3d(max.x,max.y,max.z),new Vec3d(min.x,max.y,max.z)};
        final int[][] edges = {{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}};
        for (final int[] edge : edges) renderer.drawLine(event.matrixStack(), CustomRenderLayers.getLines(1.5F, true),
                corners[edge[0]], corners[edge[1]], markLineColor.getValue());
        consumers.draw();
    }

    @Subscribe
    public void onJoinWorld(final JoinWorldEvent event) { resetRuntime(); }

    @Subscribe
    public void onDisconnect(final ServerDisconnectEvent event) { resetRuntime(); }

    private void resetRuntime() {
        SsngPacketOrderManager.release(); SsngMovementUtil.reset(); SsngRotationUtils.reset();
        SlotHelper.getInstance().stop();
        if (mc.player != null) {
            restoreKey(mc.options.sneakKey);
            restoreKey(mc.options.jumpKey);
        }
        blockSlot = null; blockData = null; lastBlockData = null; lastRotation = null; lastPlacePosition = null;
        rotateCount = 0; placeCount = 0; tellyJumpTicks = 0; ups = 0; waitingForEagleSneak = false;
        canPlace = true; lastPlacePitchDiff = 0.0F; startHotbarCount = 1;
    }

    private void selectSlot() {
        blockSlot = SsngInventoryUtil.choose(blockSlotMode.getValue());
        if (blockSlot == null || blockSlot.invalid()) return;
        if (blockSlot.hand() == Hand.MAIN_HAND) {
            if (spoofItem.getValue()) SlotHelper.setCurrentItem(blockSlot.slot()).silence(SlotHelper.Silence.FULL);
            else mc.player.getInventory().setSelectedSlot(blockSlot.slot());
        }
    }

    private void calculateRotation() {
        if (blockSlot == null || blockSlot.invalid()) return;
        if (mc.player.isOnGround()) posY = Math.floor(mc.player.getY() - 1.0D);
        else posY = mc.player.getBlockY() - 1.0D;
        if (mc.options.jumpKey.isPressed()) posY = mc.player.getBlockY() - 1.0D;
        final BlockPos target = BlockPos.ofFloored(mc.player.getX(), posY, mc.player.getZ());
        final SsngBlockData possible = isIgnored(mc.world.getBlockState(BlockPos.ofFloored(mc.player.getX(), mc.player.getY(), mc.player.getZ()))) ? getBlockData(target) : null;
        if (possible != null || !mc.player.isOnGround()) blockData = possible;
        lastBlockData = possible;
        canPlace = switch (mode.getValue()) {
            case NORMAL -> true;
            case SNAP -> SsngMovementUtil.isAirBelow(1);
            case TELLY -> SsngMovementUtil.offGroundTicks() >= placeTick.getValue().intValue();
        };
        if (mode.getValue() == Mode.TELLY && safeMode.getValue() && testOnGround.getValue()
                && !canPlace && mc.options.jumpKey.isPressed()) {
            canPlace = SsngMovementUtil.onGroundTicks() == 1;
        }

        final SsngFallingPlayer falling = new SsngFallingPlayer(mc.player, SsngRotationUtils.getServerRotation().yaw());
        falling.calculate(1); final Vec3d nextEye = falling.getEyePos(); falling.calculate(1);
        final SsngBlockData placement = getBlockData(BlockPos.ofFloored(mc.player.getX(), mc.player.getBlockY() - 1, mc.player.getZ()));
        boolean reachable = true, forceRotation = false;
        if (placement != null) {
            if (safeMode.getValue() && testOnGround.getValue() && SsngMovementUtil.onGroundTicks() == 1 && mc.options.jumpKey.isPressed()) forceRotation = true;
            if (nextEye.distanceTo(placement.pos().toCenterPos()) >= safeDistance.getValue().doubleValue() || placement.pos().getY() > falling.getY()) {
                canPlace = true; reachable = false; blockData = lastBlockData = placement;
            }
        }
        if (blockData != null && blockData.pos().getY() > falling.getY()) {
            final Box box = new Box(blockData.pos().getX(), blockData.pos().getY() - 1.0D, blockData.pos().getZ(),
                    blockData.pos().getX() + 1.0D, blockData.pos().getY() + 1.0D, blockData.pos().getZ() + 1.0D);
            if (!box.contains(mc.player.getEntityPos())) {
                canPlace = true; reachable = false; posY = mc.player.getBlockY() - 1.0D;
                blockData = lastBlockData = getBlockData(BlockPos.ofFloored(mc.player.getX(), posY, mc.player.getZ()));
            }
        }
        if (!reachable && rotateCount < 8) {
            if (debug.getValue() && rotateCount == 1) ChatUtility.print("working");
            SsngMovementUtil.cancelMove();
            rotateCount++;
        } else {
            rotateCount = 0;
        }
        SsngClientRayTraceUtil.updateEyePos();
        SsngRotation targetRotation = getBRot(forceRotation);
        if (targetRotation == null) return;
        if (duplicateRotPlace.getValue()) {
            targetRotation.setPitch(targetRotation.pitch() - randomFloat(0.001F, 0.003F));
            targetRotation.setYaw(targetRotation.yaw() - randomFloat(0.0001F, 0.0003F));
            do {
                targetRotation.setPitch(targetRotation.pitch() - randomFloat(0.001F, 0.003F));
            } while (targetRotation.pitch() > 90.0F);
            if (targetRotation.pitch() < -90.0F) targetRotation.setPitch(-90.0F);
        }
        if (didHitBlockFace(blockData, targetRotation)) {
            SsngMovementUtil.resetMove(); rotateCount = 0;
        }
        if (fixRotation.getValue()) targetRotation.fixedSensitivity();
        SsngRotationUtils.setRotation(targetRotation);
        if (mc.player.isSpectator()) {
            setEnabled(false);
            return;
        }
        if (mode.getValue() != Mode.TELLY) processEagle();
    }

    private SsngRotation getBRot(final boolean forceRotation) {
        if (blockData == null) return null;
        final SsngRotation server = SsngRotationUtils.getServerRotation();
        SsngRotation rotation = SsngRotationUtils.getClosestToBlockFace(blockData.pos(), blockData.facing(), server.yaw(), server.pitch());
        if (rotation == null) {
            final float plus = mc.player.getYaw() + 100.0F;
            final float minus = mc.player.getYaw() - 100.0F;
            rotation = new SsngRotation(Math.abs(SsngRotationUtils.yawDiff(plus, server.yaw()))
                    < Math.abs(SsngRotationUtils.yawDiff(minus, server.yaw())) ? plus : minus, server.pitch());
        }
        if (SsngMovementUtil.isCancelMove()) {
            return SsngRotationUtils.getClosestToBlockFace(blockData.pos(), blockData.facing(), server.yaw(), server.pitch());
        }
        final float difference = SsngRotationUtils.yawDiff(rotation.yaw(), server.yaw());
        if (mode.getValue() == Mode.TELLY) {
            if (mc.options.jumpKey.isPressed() && noUptelly.getValue()) return rotation;
            if (mc.options.jumpKey.isPressed() && slowUpTelly.getValue() && (++ups % 2 == 0)) return rotation;
            if (heypixelUpTelly.getValue() && (SsngMovementUtil.offGroundTicks() < rotationTick.getValue().intValue() || safeMode.getValue())) {
                if (SsngMovementUtil.onGroundTicks() > 0) {
                    if (safeMode.getValue() && (!testOnGround.getValue() || mc.options.jumpKey.isPressed())) {
                        switch (SsngMovementUtil.onGroundTicks()) {
                            case 1 -> {
                                if (!forceRotation) {
                                    rotation.setYaw(server.yaw() + SsngRotationUtils.smooth(difference, difference / 2.0F));
                                    rotation.setPitch(75.5F);
                                } else {
                                    rotation = SsngRotationUtils.getClosestToBlockFace(blockData.pos(), blockData.facing(),
                                            mc.player.getYaw(), server.pitch());
                                }
                                ((LivingEntityAccessor) mc.player).setJumpingCooldown(2);
                            }
                            case 2 -> { return new SsngRotation(mc.player.getYaw(), 75.5F); }
                            default -> { }
                        }
                    } else {
                        return new SsngRotation(mc.player.getYaw(), 75.5F);
                    }
                } else {
                    float limit = SsngMovementUtil.offGroundTicks() == 1 ? 80.0F : 50.0F;
                    limit -= randomFloat(0.001F, 0.005F);
                    rotation.setYaw(server.yaw() + SsngRotationUtils.smooth(difference, limit));
                }
            } else if (snap.getValue() && mc.options.jumpKey.isPressed()) {
                if (lastBlockData == null || SsngMovementUtil.offGroundTicks() < rotationTick.getValue().intValue()) {
                    return new SsngRotation(mc.player.getYaw(), 85.0F + (float) Math.random());
                }
            } else if (SsngMovementUtil.offGroundTicks() < rotationTick.getValue().intValue()) {
                return new SsngRotation(mc.player.getYaw(), 85.0F + (float) Math.random());
            }
        }
        if (lastRotation != null && blockData != null && SsngClientRayTraceUtil.didHitBlockFace(lastRotation, blockData.pos(), blockData.facing(), true)) return lastRotation.copy();
        if (blockData != null && !alwaysUpdateRotation.getValue() && SsngMovementUtil.offGroundTicks() >= rotationTick.getValue().intValue()
                && !SsngClientRayTraceUtil.didHitBlockFace(rotation, blockData.pos(), blockData.facing(), true) && lastRotation != null) {
            lastRotation.setYaw(lastRotation.yaw() + (float) Math.random()); return lastRotation.copy();
        }
        lastRotation = rotation.copy();
        return rotation;
    }

    private void processEagle() {
        if (!waitingForEagleSneak) return;
        tellyJumpTicks++;
        if (tellyJumpTicks == eagleTick.getValue().intValue() && !mc.options.sneakKey.isPressed()) mc.options.sneakKey.setPressed(true);
        if (tellyJumpTicks >= eagleTick.getValue().intValue() + keepEagleTick.getValue().intValue()) {
            mc.options.sneakKey.setPressed(false); waitingForEagleSneak = false; tellyJumpTicks = 0;
        }
    }

    private void place() {
        if (blockData == null || blockSlot == null || blockSlot.invalid() || !canPlace || mc.interactionManager == null || isBlinking()) return;
        final SsngRotation rotation = SsngRotationUtils.getRotation();
        if (rotation == null || !SsngClientRayTraceUtil.didHitBlockFace(rotation, blockData.pos(), blockData.facing(), true)) return;
        final BlockHitResult hit = SsngClientRayTraceUtil.getFacedBlock(rotation.yaw(), rotation.pitch());
        if (hit == null) return;
        if (blockSlot.hand() == Hand.MAIN_HAND) mc.player.getInventory().setSelectedSlot(blockSlot.slot());
        final float pitchDifference = SsngRotationUtils.getLastPitchDifference();
        if (duplicateRotPlace.getValue() && pitchDifference > 2.0F
                && Math.abs(pitchDifference - lastPlacePitchDiff) < 0.0001F) return;
        if (blockFly.getValue()) {
            if (!SsngPacketOrderManager.isDesyncing()) SsngPacketOrderManager.setup();
            if (placeCount == 0) {
                OutboundNetworkBlockage.sendPacketDirect(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
                SsngPacketOrderManager.markSwap();
            }
        }
        final ActionResult result = mc.interactionManager.interactBlock(mc.player, blockSlot.hand(), hit);
        SsngPacketOrderManager.markRightClicking();
        if (result == ActionResult.SUCCESS) {
            placeCount++;
            lastPlacePosition = blockData.placePos();
            if (pitchDifference > 0.0F) lastPlacePitchDiff = pitchDifference;
            if (noSwing.getValue()) mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(blockSlot.hand()));
            else mc.player.swingHand(blockSlot.hand());
            EventDispatcher.dispatch(new BlockPlacedEvent(hit));
        }
    }

    @Subscribe
    public void onSlowdown(final SlowdownEvent event) {
        if (SsngMovementUtil.onGroundTicks() == 1 && testOnGround.getValue() && heypixelUpTelly.getValue()
                && !noUptelly.getValue() && safeMode.getValue() && mc.options.jumpKey.isPressed()) {
            event.setSlowdown(event.getSlowdown() * 0.2F);
        }
    }

    private static boolean didHitBlockFace(final SsngBlockData data, final SsngRotation rotation) {
        return data == null || !SsngClientRayTraceUtil.didHitBlockFace(rotation, data.pos(), data.facing(), true);
    }

    private static float randomFloat(final float minimum, final float maximum) {
        return ThreadLocalRandom.current().nextFloat(minimum, maximum);
    }

    public boolean shouldKeepFov() { return keepFov.getValue() && SsngMovementUtil.isMoving(); }
    public float configuredFov() {
        final var speed = mc.player == null ? null : mc.player.getStatusEffect(StatusEffects.SPEED);
        return fov.getValue().floatValue() + (speed == null ? 0.0F : (speed.getAmplifier() + 1) * 0.13F);
    }

    private boolean isBlinking() {
        final BlinkModule blink = OraculusClient.getInstance().getModuleRepository().getModule(BlinkModule.class);
        return blink != null && blink.isEnabled();
    }

    private boolean isIgnored(final BlockState state) { return SsngClientRayTraceUtil.isIgnoredBlock(state); }

    private ItemStack selectedBlockStack() {
        if (mc.player == null || blockSlot == null || blockSlot.invalid()) return ItemStack.EMPTY;
        return blockSlot.hand() == Hand.OFF_HAND
                ? mc.player.getOffHandStack()
                : mc.player.getInventory().getStack(blockSlot.slot());
    }

    private static int blockCountColor(final int count, final boolean blackDefault) {
        if (count < 16) return 0xFFFF5050;
        if (count < 32) return 0xFFFFDC50;
        return blackDefault ? 0xFF000000 : 0xFFFFFFFF;
    }

    private SsngBlockData getBlockData(final BlockPos pos) {
        SsngBlockData data = getPos(pos);
        if (data == null) {
            final BlockPos support = getBlockPos();
            if (support == null) return null;
            final Direction direction = getPlaceSide(support);
            if (direction == null) return null;
            data = new SsngBlockData(support, direction);
        }
        return isIgnored(mc.world.getBlockState(data.placePos())) ? data : null;
    }

    private Direction getPlaceSide(final BlockPos support) {
        final BlockPos playerPos = BlockPos.ofFloored(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        final List<SsngBlockData> candidates = new ArrayList<>();
        for (final Direction direction : new Direction[]{Direction.EAST, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.DOWN}) {
            final BlockPos place = support.offset(direction);
            if (isAir(place) && !place.equals(playerPos)) candidates.add(new SsngBlockData(support, direction));
        }
        candidates.sort(Comparator.comparingDouble(value -> value.placePos().getSquaredDistance(playerPos)));
        candidates.removeIf(value -> !isIgnored(mc.world.getBlockState(value.placePos().offset(value.facing()))));
        return candidates.isEmpty() ? null : candidates.getFirst().facing();
    }

    private BlockPos getBlockPos() {
        final BlockPos playerPos = BlockPos.ofFloored(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        final List<BlockPos> positions = new ArrayList<>();
        for (int x = 5; x >= -4; x--) for (int y = 5; y >= -4; y--) for (int z = 5; z >= -4; z--) {
            final BlockPos pos = playerPos.add(x, y, z);
            if (isPosSolid(pos) && pos.getY() <= playerPos.getY() + 3) positions.add(pos);
        }
        positions.sort(Comparator.comparingDouble(pos -> pos.getSquaredDistance(playerPos)));
        return positions.isEmpty() ? null : positions.getFirst();
    }

    private SsngBlockData getPos(final BlockPos pos) {
        if (isPosSolid(pos.add(-1, 0, 0))) return new SsngBlockData(pos.add(-1, 0, 0), Direction.EAST);
        if (isPosSolid(pos.add(1, 0, 0))) return new SsngBlockData(pos.add(1, 0, 0), Direction.WEST);
        if (isPosSolid(pos.add(0, 0, 1))) return new SsngBlockData(pos.add(0, 0, 1), Direction.NORTH);
        if (isPosSolid(pos.add(0, 0, -1))) return new SsngBlockData(pos.add(0, 0, -1), Direction.SOUTH);
        if (isPosSolid(pos.add(0, -1, 0))) return new SsngBlockData(pos.add(0, -1, 0), Direction.UP);
        return null;
    }

    private boolean isAir(final BlockPos pos) { return isIgnored(mc.world.getBlockState(pos)); }

    private boolean isPosSolid(final BlockPos pos) {
        if (mc.world == null || mc.world.isOutOfHeightLimit(pos.getY())) return false;
        final BlockState state = mc.world.getBlockState(pos); final Block block = state.getBlock();
        if (block instanceof TrapdoorBlock || block instanceof DoorBlock || block instanceof FenceGateBlock) return false;
        return !INVALID_SUPPORT_BLOCKS.contains(block) && !isIgnored(state);
    }
}

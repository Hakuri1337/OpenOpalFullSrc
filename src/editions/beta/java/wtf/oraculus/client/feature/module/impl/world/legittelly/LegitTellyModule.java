package wtf.oraculus.client.feature.module.impl.world.legittelly;

import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.impl.utility.AutoBucketModule;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.event.impl.game.JoinWorldEvent;
import wtf.oraculus.event.impl.game.PostGameTickEvent;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.input.MouseHandleInputEvent;
import wtf.oraculus.event.impl.game.input.MoveInputEvent;
import wtf.oraculus.event.impl.game.packet.SendPacketEvent;
import wtf.oraculus.event.impl.game.player.movement.PostMovementPacketEvent;
import wtf.oraculus.event.impl.game.player.movement.PreMovementPacketEvent;
import wtf.oraculus.event.impl.game.player.teleport.PostTeleportEvent;
import wtf.oraculus.event.impl.game.server.ServerDisconnectEvent;
import wtf.oraculus.event.subscriber.Subscribe;

import static wtf.oraculus.client.Constants.mc;

/**
 * Independent reproduction of the reference Legit Telly recording.
 *
 * <p>This module intentionally has no dependency on the Scaffold package.
 * It owns input, visible rotation, slot selection and interaction while the
 * recorded cycle is running, then restores every state it touched.</p>
 */
public final class LegitTellyModule extends Module {
    private static final long MAX_TICK_GAP_NANOS = 300_000_000L;
    private static final float MAX_FALL_DISTANCE = 7.0F;
    private static final long GUIDE_MIN_INTERVAL_NANOS = 150_000_000L;
    private static final long GUIDE_REPEAT_NANOS = 1_250_000_000L;

    private final BooleanProperty autoSwap = new BooleanProperty("Auto Swap", true);
    private final BooleanProperty disableSafeWalk = new BooleanProperty("Disable SafeWalk", true);
    private final BooleanProperty antiSway = new BooleanProperty("Anti Sway", true);
    private final BooleanProperty actionbarGuide = new BooleanProperty("Actionbar Guide", true);
    private final NumberProperty activationTime = new NumberProperty(
            "Edge Hold", "ms", 1000.0D, 250.0D, 2000.0D, 50.0D
    );

    private final LegitTellyActivation activation = new LegitTellyActivation();
    private final LegitTellyInputController input = new LegitTellyInputController();
    private final LegitTellyRotationController rotation = new LegitTellyRotationController();
    private final LegitTellyPlacementEngine placement = new LegitTellyPlacementEngine();

    private RuntimeState state = RuntimeState.ARMED;
    private LegitTellyActivation.ActivationSnapshot lockedActivation;
    private int edgeHoldTicks;
    private int setupTick;
    private int phase;
    private boolean firstPlacement;
    private LegitTellyTarget preparedTarget;
    private Vec2f appliedRotation;
    private boolean placementWindow;
    private long lastTickNanos;
    private long takeoverDetectionAt;
    private long readyBrokenAt;
    private boolean activationMovementHold;
    private boolean safeWalkWasEnabled;
    private boolean safeWalkSuppressed;
    private String lastGuideMessage;
    private long lastGuideAtNanos;

    public LegitTellyModule() {
        super(
                "Legit Telly",
                "Replays a strict edge-activated telly bridge sequence.",
                ModuleCategory.WORLD
        );
        this.addProperties(
                this.autoSwap, this.disableSafeWalk, this.antiSway,
                this.actionbarGuide, this.activationTime
        );
    }

    @Override
    protected void onEnable() {
        this.resetToArmed(false);
        this.lastGuideMessage = null;
        this.lastGuideAtNanos = 0L;
        this.guide("已待命：先对准 45° 斜向，再低头瞄准脚下方块的前侧面。");
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        this.cleanupRuntime();
        this.state = RuntimeState.ARMED;
        super.onDisable();
    }

    @Override
    public String getSuffix() {
        return switch (this.state) {
            case ARMED -> "Armed";
            case PRIMING -> "Priming";
            case READY -> "Ready";
            case SETUP -> "Setup";
            case RUNNING -> "Running";
        };
    }

    @Subscribe(priority = 110)
    public void onPreGameTick(final PreGameTickEvent event) {
        final long now = System.nanoTime();
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            this.cleanupRuntime();
            this.state = RuntimeState.ARMED;
            return;
        }
        if (mc.currentScreen != null) {
            if (this.isSequenceActive()) {
                this.abortToArmed("已打开界面，Legit Telly 已安全中止。");
            }
            return;
        }

        if (this.isSequenceActive()) {
            if (this.lastTickNanos != 0L && now - this.lastTickNanos > MAX_TICK_GAP_NANOS) {
                this.abortToArmed("检测到客户端卡顿，Legit Telly 已安全中止。");
                return;
            }
            if (!mc.player.isAlive() || mc.player.fallDistance > MAX_FALL_DISTANCE) {
                this.abortToArmed("玩家状态不安全，Legit Telly 已安全中止。");
                return;
            }
            if (this.isAutoBucketEmergency()) {
                this.abortToArmed("AutoBucket 正在接管，Legit Telly 已让出控制。");
                return;
            }
            if (this.isScaffoldEnabled()) {
                this.abortToArmed("Scaffold 已启用，Legit Telly 已让出控制。");
                return;
            }
            if (!this.placement.ensureReadyStack()) {
                this.abortToArmed("方块已耗尽，Legit Telly 已安全中止。");
                return;
            }
            if (this.state == RuntimeState.RUNNING
                    && now >= this.takeoverDetectionAt
                    && (this.input.hasManualTakeover() || this.rotation.detectManualCamera())) {
                this.abortToArmed("检测到手动输入，Legit Telly 已归还控制。");
                return;
            }
        }
        this.lastTickNanos = now;
        this.placement.tickConfirmation();

        switch (this.state) {
            case ARMED, PRIMING, READY -> this.tickActivation();
            case SETUP -> this.tickSetup();
            case RUNNING -> this.tickRunning();
        }
    }

    @Subscribe(priority = -110)
    public void onMoveInput(final MoveInputEvent event) {
        if (this.isSequenceActive()) {
            this.input.apply(event);
        } else if (this.activationMovementHold) {
            event.setForward(-1.0F);
            event.setSideways(-1.0F);
        }
    }

    @Subscribe(priority = -110)
    public void onPreMovementPacket(final PreMovementPacketEvent event) {
        if (!this.isSequenceActive() || this.appliedRotation == null) {
            return;
        }
        event.setYaw(this.appliedRotation.x);
        event.setPitch(this.appliedRotation.y);
        event.setSprinting(this.input.sprinting());
        event.setForceInput(true);
    }

    @Subscribe(priority = 110)
    public void onPostGameTick(final PostGameTickEvent event) {
        if (!this.isSequenceActive() || this.preparedTarget == null
                || this.appliedRotation == null || !this.placementWindow) {
            return;
        }
        if (this.placement.place(this.preparedTarget, this.appliedRotation)) {
            this.firstPlacement = false;
        }
    }

    @Subscribe
    public void onPostMovementPacket(final PostMovementPacketEvent event) {
        if (this.isSequenceActive()) {
            this.placement.updateServerPosition(event.getX(), event.getY(), event.getZ());
        }
    }

    @Subscribe(priority = 110)
    public void onMouseHandleInput(final MouseHandleInputEvent event) {
        if (this.isSequenceActive()
                || this.state == RuntimeState.PRIMING
                || this.state == RuntimeState.READY) {
            event.setCancelled();
        }
    }

    @Subscribe(priority = 110)
    public void onSendPacket(final SendPacketEvent event) {
        if (!this.isSequenceActive()) {
            if ((this.state == RuntimeState.PRIMING || this.state == RuntimeState.READY)
                    && event.getPacket() instanceof PlayerActionC2SPacket action
                    && (action.getAction() == PlayerActionC2SPacket.Action.DROP_ITEM
                    || action.getAction() == PlayerActionC2SPacket.Action.DROP_ALL_ITEMS)) {
                event.setCancelled();
            }
            return;
        }
        if (event.getPacket() instanceof PlayerInteractBlockC2SPacket
                && !this.placement.isOwnPlacementPacket()) {
            event.setCancelled();
            return;
        }
        if (event.getPacket() instanceof PlayerInteractEntityC2SPacket
                || event.getPacket() instanceof PlayerInteractItemC2SPacket) {
            event.setCancelled();
            return;
        }
        if (event.getPacket() instanceof PlayerActionC2SPacket action) {
            switch (action.getAction()) {
                case START_DESTROY_BLOCK, STOP_DESTROY_BLOCK, ABORT_DESTROY_BLOCK,
                     DROP_ITEM, DROP_ALL_ITEMS, SWAP_ITEM_WITH_OFFHAND -> event.setCancelled();
                default -> {
                }
            }
            return;
        }
        if (event.getPacket() instanceof ClientCommandC2SPacket command
                && ("PRESS_SHIFT_KEY".equals(command.getMode().name())
                || "START_SNEAKING".equals(command.getMode().name()))) {
            event.setCancelled();
        }
    }

    @Subscribe
    public void onTeleport(final PostTeleportEvent event) {
        if (this.isSequenceActive()) {
            this.abortToArmed("服务器修正了位置，Legit Telly 已安全中止。");
        }
    }

    @Subscribe
    public void onJoinWorld(final JoinWorldEvent event) {
        this.resetToArmed(false);
    }

    @Subscribe
    public void onDisconnect(final ServerDisconnectEvent event) {
        this.cleanupRuntime();
        this.state = RuntimeState.ARMED;
    }

    private void tickActivation() {
        if (this.isScaffoldEnabled()) {
            this.resetActivation();
            this.guideStatus("请先关闭 Scaffold；两个搭路模块不能同时接管。");
            return;
        }

        final LegitTellyActivation.ActivationInspection inspection = this.activation.inspect();
        final LegitTellyActivation.ActivationSnapshot snapshot = inspection.snapshot();
        if (this.placement.countBlocks() == 0) {
            this.resetActivation();
            this.guideStatus("准备失败｜快捷栏中没有可用的完整安全方块。");
            return;
        }
        if (!this.autoSwap.getValue()
                && !LegitTellyBlockPolicy.isPlaceable(mc.player.getMainHandStack())) {
            this.resetActivation();
            this.guideStatus("准备失败｜Auto Swap 已关闭，请先手持完整方块。");
            return;
        }
        if (this.state == RuntimeState.READY) {
            final boolean stillLocked = sameActivation(snapshot, this.lockedActivation)
                    && this.input.isPhysicalSneakDown();
            if (stillLocked) {
                this.readyBrokenAt = 0L;
            } else if (this.readyBrokenAt == 0L) {
                this.readyBrokenAt = System.nanoTime();
            }

            this.activationMovementHold = this.input.isPhysicalUseDown();
            if (this.activationMovementHold) {
                this.suppressSafeWalk();
            } else {
                this.restoreSafeWalk();
            }
            if (!this.input.isPhysicalUseDown()) {
                this.guideStatus("③ 已锁定｜按住右键，然后松开潜行开始。");
            } else if (this.input.isPhysicalSneakDown()) {
                this.guideStatus("③ 右键已按住｜现在松开潜行。");
            } else if (!isDiagonalAligned(mc.player.getYaw())) {
                this.guideStatus("③ 保持右键，并把朝向重新对准 45° 斜向。");
            }
            if (this.input.isPhysicalUseDown()
                    && !this.input.isPhysicalSneakDown()
                    && isDiagonalAligned(mc.player.getYaw())) {
                this.beginSequence(this.lockedActivation);
                return;
            }
            if (this.readyBrokenAt != 0L
                    && System.nanoTime() - this.readyBrokenAt > 300_000_000L) {
                this.resetActivation();
            }
            return;
        }

        if (snapshot == null || !this.input.isPhysicalSneakDown()) {
            this.resetActivation();
            if (snapshot == null) {
                this.guideStatus(this.activationGuide(inspection));
            } else {
                this.guideStatus(
                        "② 位置正确（朝 "
                                + LegitTellyActivation.directionName(snapshot.travel())
                                + "）｜按住潜行开始锁定。"
                );
            }
            return;
        }
        if (!sameActivation(snapshot, this.lockedActivation)) {
            this.lockedActivation = snapshot;
            this.edgeHoldTicks = 0;
            this.state = RuntimeState.PRIMING;
        }

        this.edgeHoldTicks++;
        final int requiredTicks = Math.max(1, (int) Math.ceil(this.activationTime.getValue() / 50.0D));
        if (this.edgeHoldTicks >= requiredTicks) {
            this.state = RuntimeState.READY;
            this.readyBrokenAt = 0L;
            this.guide("③ 已锁定｜按住右键，然后松开潜行开始。");
        } else if (this.edgeHoldTicks == 1 || this.edgeHoldTicks % 5 == 0) {
            final int percent = Math.min(99, this.edgeHoldTicks * 100 / requiredTicks);
            this.guideStatus(
                    "② 边缘锁定 " + progressBar(percent) + " " + percent
                            + "%｜保持潜行、视角和站位"
            );
        }
    }

    private void beginSequence(final LegitTellyActivation.ActivationSnapshot snapshot) {
        if (snapshot == null || this.placement.countBlocks() == 0) {
            this.abortToArmed("快捷栏没有可用的安全方块。");
            return;
        }
        if (!this.autoSwap.getValue()
                && !LegitTellyBlockPolicy.isPlaceable(mc.player.getMainHandStack())) {
            this.abortToArmed("Auto Swap 已关闭，请手持完整方块。");
            return;
        }

        final LegitTellyActivation.ActivationSnapshot runtimeActivation =
                snapshot.withBaseYaw(mc.player.getYaw());
        this.lockedActivation = runtimeActivation;
        this.suppressSafeWalk();
        this.activationMovementHold = false;
        this.rotation.begin(runtimeActivation.lane());
        this.placement.begin(runtimeActivation, this.autoSwap.getValue());
        this.setupTick = 0;
        this.phase = LegitTellyProfile.FIRST_RUNNING_PHASE;
        this.firstPlacement = false;
        this.preparedTarget = null;
        this.placementWindow = true;
        this.appliedRotation = new Vec2f(mc.player.getYaw(), mc.player.getPitch());
        this.lastTickNanos = System.nanoTime();
        this.state = RuntimeState.SETUP;
        this.guide("④ 启动中｜模块已接管移动与视角；重新按移动键或移动鼠标可中止。");
        this.tickSetup();
    }

    private void tickSetup() {
        if (this.setupTick >= LegitTellyProfile.SETUP_TICKS) {
            this.state = RuntimeState.RUNNING;
            this.phase = LegitTellyProfile.FIRST_RUNNING_PHASE;
            this.firstPlacement = true;
            this.input.captureActivationInputs();
            this.takeoverDetectionAt = System.nanoTime() + 125_000_000L;
            this.tickRunning();
            return;
        }

        final int setupPercent = Math.min(
                99,
                this.setupTick * 100 / Math.max(1, LegitTellyProfile.SETUP_TICKS)
        );
        this.guideStatus(
                "④ 启动校准 " + progressBar(setupPercent) + " " + setupPercent + "%"
        );
        final boolean finalSetupTick = this.setupTick == LegitTellyProfile.SETUP_TICKS - 1;
        final Vec2f desired = finalSetupTick
                ? new Vec2f(
                this.lockedActivation.baseYaw() + LegitTellyProfile.yaw(19),
                LegitTellyProfile.pitch(19)
        )
                : new Vec2f(this.lockedActivation.baseYaw(), 74.52F);

        this.input.set(-1.0F, -1.0F, this.setupTick >= 6, false, false);
        this.placementWindow = true;
        this.prepareAndApply(desired, false, false);

        this.setupTick++;
    }

    private void tickRunning() {
        this.guideStatus(
                "⑤ 运行中｜可用方块 " + this.placement.countBlocks()
                        + "｜重新按移动键或移动鼠标可安全中止"
        );
        final int currentPhase = this.phase;
        final float forward = LegitTellyProfile.forward(currentPhase);
        final float recordedStrafe = LegitTellyProfile.sideways(currentPhase);
        final float correctedStrafe = this.rotation.correctStrafe(
                forward,
                recordedStrafe,
                this.lockedActivation.travel(),
                this.antiSway.getValue()
        );
        this.input.set(
                forward,
                correctedStrafe,
                LegitTellyProfile.jump(currentPhase),
                false,
                LegitTellyProfile.sprint(currentPhase)
        );
        final int nextPhase = (currentPhase + 1) % LegitTellyProfile.length();
        final Vec2f desired = new Vec2f(
                this.lockedActivation.baseYaw() + LegitTellyProfile.yaw(nextPhase),
                LegitTellyProfile.pitch(nextPhase)
        );
        this.placementWindow = LegitTellyProfile.useWindow(currentPhase);
        this.prepareAndApply(desired, this.firstPlacement, this.antiSway.getValue());
        this.phase = nextPhase;
    }

    private void prepareAndApply(
            final Vec2f desired,
            final boolean adaptiveYaw,
            final boolean useAntiSway
    ) {
        final Vec2f laneCorrected = this.rotation.correctForLane(
                desired,
                this.lockedActivation.travel(),
                useAntiSway && !adaptiveYaw
        );
        this.preparedTarget = this.placementWindow
                ? this.placement.prepare(laneCorrected, adaptiveYaw)
                : null;
        final Vec2f targetRotation = this.preparedTarget == null
                ? laneCorrected
                : this.preparedTarget.rotation();
        this.appliedRotation = this.rotation.apply(targetRotation);
    }

    private void abortToArmed(final String reason) {
        this.cleanupRuntime();
        this.state = RuntimeState.ARMED;
        this.guide(reason);
    }

    private void resetToArmed(final boolean guide) {
        this.cleanupRuntime();
        this.state = RuntimeState.ARMED;
        if (guide) {
            this.guide("Legit Telly 已重新待命。");
        }
    }

    private void cleanupRuntime() {
        this.input.restore();
        this.rotation.clear();
        this.placement.restoreSlot();
        this.restoreSafeWalk();
        this.resetActivationFields();
        this.setupTick = 0;
        this.phase = 0;
        this.firstPlacement = true;
        this.preparedTarget = null;
        this.appliedRotation = null;
        this.placementWindow = false;
        this.lastTickNanos = 0L;
        this.takeoverDetectionAt = 0L;
        this.readyBrokenAt = 0L;
        this.activationMovementHold = false;
    }

    private void resetActivation() {
        final boolean hadProgress = this.state == RuntimeState.PRIMING || this.state == RuntimeState.READY;
        this.resetActivationFields();
        this.state = RuntimeState.ARMED;
        if (hadProgress) {
            this.restoreSafeWalk();
        }
    }

    private void resetActivationFields() {
        this.lockedActivation = null;
        this.edgeHoldTicks = 0;
        this.readyBrokenAt = 0L;
        this.activationMovementHold = false;
    }

    private boolean isSequenceActive() {
        return this.state == RuntimeState.SETUP || this.state == RuntimeState.RUNNING;
    }

    private boolean isScaffoldEnabled() {
        final Module scaffold = OraculusClient.getInstance()
                .getModuleRepository().getOptionalModule("scaffold");
        return scaffold != null && scaffold.isEnabled();
    }

    private boolean isAutoBucketEmergency() {
        final Module module = OraculusClient.getInstance()
                .getModuleRepository().getOptionalModule("autobucket");
        return module instanceof AutoBucketModule autoBucket
                && autoBucket.isEnabled()
                && autoBucket.isEmergencyActive();
    }

    private void suppressSafeWalk() {
        if (this.safeWalkSuppressed || !this.disableSafeWalk.getValue()) {
            return;
        }
        final Module safeWalk = OraculusClient.getInstance()
                .getModuleRepository().getOptionalModule("safe_walk");
        this.safeWalkWasEnabled = safeWalk != null && safeWalk.isEnabled();
        if (this.safeWalkWasEnabled) {
            safeWalk.setEnabled(false);
        }
        this.safeWalkSuppressed = true;
    }

    private void restoreSafeWalk() {
        if (!this.safeWalkSuppressed) {
            return;
        }
        final Module safeWalk = OraculusClient.getInstance()
                .getModuleRepository().getOptionalModule("safe_walk");
        if (this.safeWalkWasEnabled && safeWalk != null && !safeWalk.isEnabled()) {
            safeWalk.setEnabled(true);
        }
        this.safeWalkWasEnabled = false;
        this.safeWalkSuppressed = false;
    }

    private void guide(final String message) {
        this.publishGuide(message, true);
    }

    private void guideStatus(final String message) {
        this.publishGuide(message, false);
    }

    private void publishGuide(final String message, final boolean force) {
        if (!this.actionbarGuide.getValue() || mc.player == null
                || message == null || message.isBlank()) {
            return;
        }

        final long now = System.nanoTime();
        final boolean changed = !message.equals(this.lastGuideMessage);
        if (!force && now - this.lastGuideAtNanos < GUIDE_MIN_INTERVAL_NANOS) {
            return;
        }
        if (!force && !changed && now - this.lastGuideAtNanos < GUIDE_REPEAT_NANOS) {
            return;
        }

        this.lastGuideMessage = message;
        this.lastGuideAtNanos = now;
        mc.player.sendMessage(Text.literal("[Legit Telly] " + message), true);
    }

    private String activationGuide(
            final LegitTellyActivation.ActivationInspection inspection
    ) {
        return switch (inspection.issue()) {
            case READY -> "② 位置正确｜按住潜行开始锁定。";
            case WORLD_UNAVAILABLE -> "正在等待进入世界。";
            case ALIGN_DIAGONAL -> "① 水平朝向对准 45° 斜向｜当前还偏 "
                    + oneDecimal(inspection.measurement()) + "°";
            case LOOK_DOWN -> "① 朝向正确｜继续向下看，Pitch 至少 "
                    + oneDecimal(LegitTellyActivation.requiredPitch()) + "°（当前 "
                    + oneDecimal(inspection.measurement()) + "°）";
            case MOVE_TO_EDGE -> "② 保持斜向，向前方边缘再靠近约 "
                    + twoDecimals(inspection.measurement()) + " 格";
            case FRONT_BLOCKED -> "② 前方起步空间被阻挡，请换到空旷边缘。";
            case AIM_AT_BLOCK -> "② 向下移动准星，瞄准脚下方块的前侧面。";
            case AIM_AT_FORWARD_SIDE -> "② 不要瞄方块顶面；准星应落在脚下方块的前侧面。";
            case AIM_AT_OWN_BLOCK -> "② 准星命中了别的方块，请瞄准自己脚下方块的前侧面。";
            case AIM_AT_SIDE_CENTER -> "② 将准星移到脚下方块前侧面的中央窄区。";
        };
    }

    private static String progressBar(final int percent) {
        final int filled = MathHelper.clamp(percent / 10, 0, 10);
        return "[" + "#".repeat(filled) + "-".repeat(10 - filled) + "]";
    }

    private static String oneDecimal(final double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static String twoDecimals(final double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static boolean sameActivation(
            final LegitTellyActivation.ActivationSnapshot first,
            final LegitTellyActivation.ActivationSnapshot second
    ) {
        return first != null && second != null
                && first.block().equals(second.block())
                && first.travel() == second.travel();
    }

    private static boolean isDiagonalAligned(final float yaw) {
        final float nearestDiagonal = Math.round((yaw - 45.0F) / 90.0F) * 90.0F + 45.0F;
        return MathHelper.angleBetween(yaw, nearestDiagonal) <= 2.0F;
    }

    private enum RuntimeState {
        ARMED,
        PRIMING,
        READY,
        SETUP,
        RUNNING
    }
}

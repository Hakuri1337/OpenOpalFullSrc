package wtf.oraculus.client.feature.module.impl.world.legittelly;

import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.module.DeprecatedModule;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.impl.world.legittelly.guidance.LegitTellyGuidanceController;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.event.impl.game.JoinWorldEvent;
import wtf.oraculus.event.impl.game.PreGameTickEvent;
import wtf.oraculus.event.impl.game.input.MouseHandleInputEvent;
import wtf.oraculus.event.impl.game.input.MouseUpdateEvent;
import wtf.oraculus.event.impl.game.input.MoveInputEvent;
import wtf.oraculus.event.impl.game.packet.SendPacketEvent;
import wtf.oraculus.event.impl.game.player.movement.PostMovementPacketEvent;
import wtf.oraculus.event.impl.game.player.movement.PreMovementPacketEvent;
import wtf.oraculus.event.impl.game.player.teleport.PostTeleportEvent;
import wtf.oraculus.event.impl.game.server.ServerDisconnectEvent;
import wtf.oraculus.event.impl.press.MousePressEvent;
import wtf.oraculus.event.impl.render.RenderWorldEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.player.RotationUtility;

import static wtf.oraculus.client.Constants.mc;

/**
 * Native port of the reference Legit Telly script.
 *
 * <p>The reference has two deliberately separate rotation paths:
 * a visible 50 ms recording curve and a per-tick placement pitch used only by
 * the outgoing movement state. They must never overwrite one another.</p>
 */
public final class LegitTellyModule extends Module implements DeprecatedModule {
    private static final long MAX_TICK_GAP_NANOS = 300_000_000L;
    private static final float MAX_FALL_DISTANCE = 7.0F;
    private static final long GUIDE_MIN_INTERVAL_NANOS = 150_000_000L;
    private static final long GUIDE_REPEAT_NANOS = 1_250_000_000L;
    private static final int ASSIST_READY_TICKS = 3;
    private static final long ASSIST_TIMEOUT_NANOS = 8_000_000_000L;

    private final BooleanProperty autoSwap = new BooleanProperty("Auto Swap", true);
    private final BooleanProperty disableSafeWalk = new BooleanProperty("Disable SafeWalk", true);
    private final BooleanProperty antiSway = new BooleanProperty("Anti Sway", true);
    private final BooleanProperty actionbarGuide = new BooleanProperty("Actionbar Guide", true);
    private final BooleanProperty aimAssistOverlay = new BooleanProperty("Aim Assist Overlay", true);
    private final BooleanProperty sideHighlight = new BooleanProperty("Side Highlight", true);
    private final BooleanProperty movementCoach = new BooleanProperty("Movement Coach", true);
    private final BooleanProperty dynamicIslandGuide = new BooleanProperty("Dynamic Island Guide", true);
    private final NumberProperty guideOpacity = new NumberProperty(
            "Guide Opacity", 0.28D, 0.10D, 0.65D, 0.05D
    );
    private final BooleanProperty showEyeLine = new BooleanProperty("Show Eye Line", false);
    private final NumberProperty activationTime = new NumberProperty(
            "Edge Hold", "ms", 1000.0D, 250.0D, 2000.0D, 50.0D
    );

    private final LegitTellyActivation activation = new LegitTellyActivation();
    private final LegitTellyInputController input = new LegitTellyInputController();
    private final LegitTellyRotationController rotation = new LegitTellyRotationController();
    private final LegitTellyPlacementEngine placement = new LegitTellyPlacementEngine();
    private final LegitTellyGuidanceController guidance = new LegitTellyGuidanceController();

    private RuntimeState state = RuntimeState.ARMED;
    private LegitTellyActivation.ActivationSnapshot lockedActivation;
    private int edgeHoldTicks;
    private int setupTick;
    private int phase;
    private boolean firstPlacementPending;
    private boolean placementWindow;
    private Vec2f visibleRotation;
    private Vec2f movementRotation;
    private Vec2f silentPlacementRotation;
    private LegitTellyTarget latestCandidate;
    private boolean suppressUseThisTick;
    private long lastTickNanos;
    private long takeoverDetectionAt;
    private long readyBrokenAt;
    private boolean activationMovementHold;
    private int assistReadyTicks;
    private long assistStartedAtNanos;
    private boolean safeWalkWasEnabled;
    private boolean safeWalkSuppressed;
    private String lastGuideMessage;
    private long lastGuideAtNanos;

    public LegitTellyModule() {
        super(
                "Legit Telly",
                "Native replay of the reference telly bridge recording.",
                ModuleCategory.WORLD
        );
        this.setVisible(false);
        this.addProperties(
                this.autoSwap, this.disableSafeWalk, this.antiSway,
                this.actionbarGuide, this.aimAssistOverlay, this.sideHighlight,
                this.movementCoach, this.dynamicIslandGuide, this.guideOpacity,
                this.showEyeLine, this.activationTime
        );
    }

    @Override
    protected void onEnable() {
        this.guidance.clear();
        this.resetToArmed(false);
        this.lastGuideMessage = null;
        this.lastGuideAtNanos = 0L;
        this.guide("已待命：按原版方式潜行瞄准脚下方块侧面，或按鼠标侧键 4 辅助就位。");
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        this.cleanupRuntime();
        this.guidance.clear();
        this.state = RuntimeState.ARMED;
        super.onDisable();
    }

    @Override
    public String getSuffix() {
        return switch (this.state) {
            case ARMED -> "Armed";
            case ASSISTING -> "Assist";
            case PRIMING -> "Priming";
            case READY -> "Ready";
            case SETUP -> "Setup";
            case RUNNING -> "Running";
        };
    }

    @Subscribe(priority = 110)
    public void onPreGameTick(final PreGameTickEvent event) {
        final long now = System.nanoTime();
        if (!this.hasGameContext()) {
            this.cleanupRuntime();
            this.state = RuntimeState.ARMED;
            this.guidance.clear();
            return;
        }

        this.guidance.setIslandEnabled(
                this.dynamicIslandGuide.getValue()
                        && this.movementCoach.getValue()
                        && mc.currentScreen == null
        );
        if (mc.currentScreen != null) {
            if (this.isControlActive()) {
                this.abortToArmed("已打开界面，Legit Telly 已中止。");
            }
            this.guidance.clearTarget();
            this.guidance.suspendIsland();
            return;
        }

        if (this.isControlActive() && !this.validateRuntime(now)) {
            return;
        }

        this.lastTickNanos = now;
        if (this.isControlActive()) {
            this.visibleRotation = this.rotation.update();
        }
        this.placement.tickConfirmation();

        // Reference onPreUpdate: first resolve/place using the input and use
        // window left by the previous onPostPlayerInput callback.
        if (this.isSequenceActive()) {
            this.processPlacementTick();
        }

        // Reference onPostPlayerInput: then publish this tick's movement and
        // select the next 50 ms camera target.
        switch (this.state) {
            case ARMED, PRIMING, READY -> this.tickActivation();
            case ASSISTING -> this.tickAssist();
            case SETUP -> this.tickSetup();
            case RUNNING -> this.tickRunning();
        }
        if (this.isControlActive() && this.visibleRotation != null) {
            // Freeze the exact yaw used by this tick's local movement.  The
            // reference does not advance the interpolation again between
            // movement simulation and onPreMotion.
            this.movementRotation = this.visibleRotation;
        }
    }

    @Subscribe(priority = -110)
    public void onMoveInput(final MoveInputEvent event) {
        if (this.isControlActive()) {
            this.input.apply(event);
        } else if (this.activationMovementHold) {
            event.setForward(-1.0F);
            event.setSideways(-1.0F);
        }
    }

    @Subscribe(priority = -110)
    public void onPreMovementPacket(final PreMovementPacketEvent event) {
        if (!this.isControlActive() || this.movementRotation == null) {
            return;
        }
        event.setYaw(this.movementRotation.x);
        event.setPitch(
                this.silentPlacementRotation == null
                        ? this.movementRotation.y
                        : this.silentPlacementRotation.y
        );
        event.setSprinting(this.input.sprinting());
        event.setForceInput(true);
    }

    @Subscribe
    public void onPostMovementPacket(final PostMovementPacketEvent event) {
        if (this.isSequenceActive()) {
            this.placement.updateServerPosition(event.getX(), event.getY(), event.getZ());
        }
        // The reference clears silentPitchActive at the next placement tick.
        // Clearing after this movement packet gives exactly one packet window.
        this.silentPlacementRotation = null;
    }

    @Subscribe
    public void onRenderWorld(final RenderWorldEvent event) {
        if (this.isControlActive()) {
            this.visibleRotation = this.rotation.update();
        }
        this.guidance.render(
                event,
                this.aimAssistOverlay.getValue() && this.sideHighlight.getValue(),
                this.guideOpacity.getValue(),
                this.showEyeLine.getValue()
        );
    }

    @Subscribe(priority = 120)
    public void onMouseUpdate(final MouseUpdateEvent event) {
        if (!this.isControlActive()) {
            return;
        }
        this.visibleRotation = this.rotation.update();
        event.setDeltaX(0.0D);
        event.setDeltaY(0.0D);
        event.setHandled();
    }

    @Subscribe(priority = 120)
    public void onMousePress(final MousePressEvent event) {
        if (event.getInteractionCode() != GLFW.GLFW_MOUSE_BUTTON_4
                || !this.hasGameContext() || mc.currentScreen != null) {
            return;
        }
        if (this.isControlActive()) {
            this.abortToArmed("侧键 4：已取消并归还移动与视角。");
            return;
        }
        this.startAssist();
    }

    @Subscribe(priority = 110)
    public void onMouseHandleInput(final MouseHandleInputEvent event) {
        if (this.state == RuntimeState.PRIMING
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
        if (event.getPacket() instanceof PlayerInteractEntityC2SPacket
                || event.getPacket() instanceof PlayerInteractItemC2SPacket) {
            event.setCancelled();
            return;
        }
        if (event.getPacket() instanceof PlayerInteractBlockC2SPacket interact
                && !this.placement.isOwnPlacementPacket()
                && !this.placement.isAllowedExternalPlacement(interact.getBlockHitResult())) {
            // Mirrors the source script's C08 straight-target guard.  This is
            // especially important when controlled right-click is used as the
            // direct-crosshair fallback.
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
        if (this.isControlActive()) {
            this.abortToArmed("服务器修正了位置，Legit Telly 已中止。");
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
        this.guidance.clear();
    }

    private boolean validateRuntime(final long now) {
        if (this.lastTickNanos != 0L && now - this.lastTickNanos > MAX_TICK_GAP_NANOS) {
            this.abortToArmed("检测到客户端卡顿，Legit Telly 已中止。");
            return false;
        }
        if (!mc.player.isAlive() || mc.player.fallDistance > MAX_FALL_DISTANCE) {
            this.abortToArmed("玩家状态不安全，Legit Telly 已中止。");
            return false;
        }
        if (this.isScaffoldEnabled()) {
            this.abortToArmed("Scaffold 已启用，Legit Telly 已让出控制。");
            return false;
        }
        if (this.isSequenceActive() && !this.placement.ensureReadyStack()) {
            this.abortToArmed("方块已经耗尽，Legit Telly 已中止。");
            return false;
        }
        return true;
    }

    private void processPlacementTick() {
        this.silentPlacementRotation = null;
        this.latestCandidate = null;
        this.suppressUseThisTick = false;
        if (!this.placementWindow) {
            this.input.setUse(false);
            this.guidance.clearTarget();
            return;
        }
        if (this.visibleRotation == null
                || !this.placement.isBlockBelowPlayerReplaceable()) {
            this.guidance.clearTarget();
            return;
        }

        final LegitTellyTarget candidate = this.placement.resolveCurrent(this.visibleRotation);
        this.latestCandidate = candidate;
        this.guidance.updateTarget(candidate, this.guidanceStage());
        if (candidate == null) {
            // Keep controlled use pressed. This is the source script's
            // direct-crosshair fallback when the extended resolver has no
            // candidate for the current frame.
            this.input.setUse(true);
            return;
        }

        this.suppressUseThisTick = true;
        this.input.setUse(false);
        // Candidate resolution keeps yaw fixed to the visible recording.
        // Only pitch is carried into the movement state, as in onPreMotion.
        this.silentPlacementRotation = new Vec2f(
                this.visibleRotation.x, candidate.rotation().y
        );
        if (this.placement.place(candidate, this.silentPlacementRotation)) {
            this.firstPlacementPending = false;
        }
    }

    private void tickActivation() {
        if (this.isScaffoldEnabled()) {
            this.resetActivation();
            this.guidance.clearTarget();
            this.guideStatus("请先关闭 Scaffold。");
            return;
        }

        final LegitTellyActivation.ActivationInspection inspection = this.activation.inspect();
        final LegitTellyActivation.ActivationSnapshot snapshot = inspection.snapshot();
        this.guidance.updateActivation(inspection, this.guidanceStage());
        if (this.placement.countBlocks() == 0) {
            this.resetActivation();
            this.guideStatus("快捷栏中没有可用的完整方块。");
            return;
        }
        if (!this.autoSwap.getValue()
                && !LegitTellyBlockPolicy.isPlaceable(mc.player.getMainHandStack())) {
            this.resetActivation();
            this.guideStatus("Auto Swap 已关闭，请先手持完整方块。");
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
                this.guideStatus("已锁定｜按住右键，然后松开潜行。");
            } else if (this.input.isPhysicalSneakDown()) {
                this.guideStatus("右键已按住｜现在松开潜行。");
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
            this.guideStatus(
                    snapshot == null
                            ? this.activationGuide(inspection)
                            : "位置正确｜按住潜行开始锁定。"
            );
            return;
        }
        if (!sameActivation(snapshot, this.lockedActivation)) {
            this.lockedActivation = snapshot;
            this.edgeHoldTicks = 0;
            this.state = RuntimeState.PRIMING;
        }

        final int requiredTicks = Math.max(
                1, (int) Math.ceil(this.activationTime.getValue() / 50.0D)
        );
        if (++this.edgeHoldTicks >= requiredTicks) {
            this.state = RuntimeState.READY;
            this.readyBrokenAt = 0L;
            this.guide("已锁定｜按住右键，然后松开潜行。");
        } else if (this.edgeHoldTicks == 1 || this.edgeHoldTicks % 5 == 0) {
            final int percent = Math.min(99, this.edgeHoldTicks * 100 / requiredTicks);
            this.guideStatus(
                    "边缘锁定 " + progressBar(percent) + " " + percent + "%"
            );
        }
    }

    private void startAssist() {
        if (!mc.player.isOnGround()) {
            this.guide("无法辅助：请先站稳在完整方块上。");
            return;
        }
        if (this.placement.countBlocks() == 0 || this.isScaffoldEnabled()) {
            this.guide("无法辅助：请准备方块并关闭 Scaffold。");
            return;
        }

        final float baseYaw = LegitTellyActivation.nearestDiagonalYaw(mc.player.getYaw());
        final Direction travel = LegitTellyActivation.travelDirectionForYaw(baseYaw);
        final BlockPos support = LegitTellyActivation.supportBlockForPlayer(travel);
        if (!LegitTellyBlockPolicy.isSafeSupport(support)
                || !LegitTellyBlockPolicy.isReplaceable(support.offset(travel))) {
            this.guide("无法辅助：起点或前方空间不符合要求。");
            return;
        }

        this.cleanupRuntime();
        final boolean alongX = travel.getAxis() == Direction.Axis.X;
        final double lane = alongX ? mc.player.getZ() : mc.player.getX();
        this.lockedActivation = new LegitTellyActivation.ActivationSnapshot(
                support.toImmutable(), travel, baseYaw, lane,
                MathHelper.floor(lane),
                LegitTellyActivation.progress(support, travel)
        );
        this.rotation.begin(lane);
        this.input.captureActivationInputs();
        this.visibleRotation = new Vec2f(mc.player.getYaw(), mc.player.getPitch());
        this.assistReadyTicks = 0;
        this.assistStartedAtNanos = System.nanoTime();
        this.lastTickNanos = this.assistStartedAtNanos;
        this.state = RuntimeState.ASSISTING;
        this.suppressSafeWalk();
        this.guide("辅助已接管：正在按原版激活站位移动并瞄准侧面。");
    }

    private void tickAssist() {
        final LegitTellyActivation.ActivationSnapshot snapshot = this.lockedActivation;
        if (snapshot == null
                || System.nanoTime() - this.assistStartedAtNanos > ASSIST_TIMEOUT_NANOS
                || !mc.player.isOnGround()
                || !LegitTellyBlockPolicy.isSafeSupport(snapshot.block())) {
            this.abortToArmed("辅助未能锁定安全起点，已中止。");
            return;
        }

        final Vec2f aim = LegitTellyActivation.findActivationAim(
                snapshot.block(), snapshot.travel(), snapshot.baseYaw()
        );
        final double edgeGap = LegitTellyActivation.edgeGap(
                snapshot.block(), snapshot.travel()
        );
        final Vec2f target = aim == null
                ? new Vec2f(snapshot.baseYaw(), 78.0F)
                : aim;
        this.visibleRotation = this.rotation.apply(target);
        this.input.set(
                edgeGap > 0.0D ? -1.0F : 0.0F,
                edgeGap > 0.0D ? -1.0F : 0.0F,
                false, true, false
        );
        this.input.setUse(false);

        final boolean aimed = aim != null && LegitTellyActivation.isAimingAtSide(
                snapshot.block(), snapshot.travel()
        );
        final boolean stable = mc.player.getVelocity().horizontalLengthSquared() < 0.0025D;
        this.guidance.updateAssist(
                snapshot.block(), snapshot.travel(),
                LegitTellyActivation.sideAimPoint(snapshot.block(), snapshot.travel()),
                edgeGap <= 0.0D && aimed && stable
        );
        if (edgeGap <= 0.0D && aimed && stable) {
            if (++this.assistReadyTicks >= ASSIST_READY_TICKS) {
                this.beginSequence(snapshot);
            }
        } else {
            this.assistReadyTicks = 0;
            this.guideStatus(
                    edgeGap > 0.0D
                            ? "辅助移动｜正在靠近安全边缘"
                            : "辅助瞄准｜正在锁定脚下方块侧面"
            );
        }
    }

    private void beginSequence(final LegitTellyActivation.ActivationSnapshot snapshot) {
        if (snapshot == null || this.placement.countBlocks() == 0) {
            this.abortToArmed("没有可用方块，无法开始。");
            return;
        }
        // The source captures yaw and anti-sway lane at the moment automation
        // actually starts.  Side-button assist has already moved the player,
        // so retaining its pre-assist lane creates a permanent lateral error.
        final boolean alongX = snapshot.travel().getAxis() == Direction.Axis.X;
        final double lane = alongX ? mc.player.getZ() : mc.player.getX();
        final BlockPos support = LegitTellyActivation.supportBlockForPlayer(snapshot.travel());
        final LegitTellyActivation.ActivationSnapshot runtime =
                new LegitTellyActivation.ActivationSnapshot(
                        support.toImmutable(),
                        snapshot.travel(),
                        mc.player.getYaw(),
                        lane,
                        MathHelper.floor(lane),
                        LegitTellyActivation.progress(support, snapshot.travel())
                );
        this.lockedActivation = runtime;
        this.suppressSafeWalk();
        this.activationMovementHold = false;
        this.rotation.begin(runtime.lane());
        this.placement.begin(runtime, this.autoSwap.getValue());
        this.input.captureActivationInputs();
        this.setupTick = 0;
        this.phase = LegitTellyProfile.FIRST_RUNNING_PHASE;
        this.firstPlacementPending = false;
        this.placementWindow = true;
        this.latestCandidate = null;
        this.silentPlacementRotation = null;
        this.visibleRotation = new Vec2f(mc.player.getYaw(), mc.player.getPitch());
        this.movementRotation = this.visibleRotation;
        this.lastTickNanos = System.nanoTime();
        this.state = RuntimeState.SETUP;
        this.guide("启动：正在执行原版 12 Tick 校准段。");
        this.tickSetup();
    }

    private void tickSetup() {
        if (this.setupTick >= LegitTellyProfile.SETUP_TICKS) {
            this.state = RuntimeState.RUNNING;
            this.phase = LegitTellyProfile.FIRST_RUNNING_PHASE;
            this.firstPlacementPending = true;
            this.takeoverDetectionAt = System.nanoTime() + 125_000_000L;
            this.tickRunning();
            return;
        }

        final boolean finalSetupTick =
                this.setupTick == LegitTellyProfile.SETUP_TICKS - 1;
        final Vec2f desired = finalSetupTick
                ? new Vec2f(
                this.lockedActivation.baseYaw()
                        + LegitTellyProfile.yaw(LegitTellyProfile.FIRST_RUNNING_PHASE),
                LegitTellyProfile.pitch(LegitTellyProfile.FIRST_RUNNING_PHASE)
        )
                : new Vec2f(this.lockedActivation.baseYaw(), 74.52F);

        this.input.set(-1.0F, -1.0F, this.setupTick >= 6, false, false);
        this.placementWindow = true;
        this.input.setUse(!this.suppressUseThisTick);
        this.visibleRotation = this.rotation.apply(desired);
        final int percent = Math.min(
                99,
                this.setupTick * 100 / Math.max(1, LegitTellyProfile.SETUP_TICKS)
        );
        this.guideStatus("启动校准 " + progressBar(percent) + " " + percent + "%");
        this.setupTick++;
    }

    private void tickRunning() {
        final int currentPhase = this.phase;
        final float forward = LegitTellyProfile.forward(currentPhase);
        final float recordedStrafe = LegitTellyProfile.sideways(currentPhase);
        final float strafe = this.rotation.correctStrafe(
                forward, recordedStrafe,
                this.lockedActivation.travel(), this.antiSway.getValue()
        );
        this.input.set(
                forward, strafe,
                LegitTellyProfile.jump(currentPhase),
                false,
                LegitTellyProfile.sprint(currentPhase)
        );
        this.placementWindow = LegitTellyProfile.useWindow(currentPhase);
        this.input.setUse(this.placementWindow && !this.suppressUseThisTick);

        final int nextPhase = (currentPhase + 1) % LegitTellyProfile.length();
        Vec2f desired = new Vec2f(
                this.lockedActivation.baseYaw() + LegitTellyProfile.yaw(nextPhase),
                LegitTellyProfile.pitch(nextPhase)
        );
        desired = this.rotation.correctForLane(
                desired, this.lockedActivation.travel(),
                this.antiSway.getValue() && !this.firstPlacementPending
        );

        // Reference updateAdaptivePlacementAim: only the first unresolved
        // placement may redirect the next visible target to the verified hit.
        if (this.firstPlacementPending && this.latestCandidate != null) {
            desired = RotationUtility.getRotationFromPosition(
                    mc.player.getEyePos(), this.latestCandidate.hit().getPos()
            );
        }
        this.visibleRotation = this.rotation.apply(desired);
        this.phase = nextPhase;
        this.guideStatus(
                "运行中｜方块 " + this.placement.countBlocks()
                        + "｜侧键 4 可中止"
        );
    }

    private void abortToArmed(final String reason) {
        this.cleanupRuntime();
        this.state = RuntimeState.ARMED;
        this.guide(reason);
    }

    private void resetToArmed(final boolean notify) {
        this.cleanupRuntime();
        this.state = RuntimeState.ARMED;
        if (notify) {
            this.guide("Legit Telly 已重新待命。");
        }
    }

    private void cleanupRuntime() {
        this.input.restore();
        this.rotation.clear();
        this.placement.restoreSlot();
        this.restoreSafeWalk();
        this.lockedActivation = null;
        this.edgeHoldTicks = 0;
        this.setupTick = 0;
        this.phase = LegitTellyProfile.FIRST_RUNNING_PHASE;
        this.firstPlacementPending = false;
        this.placementWindow = false;
        this.visibleRotation = null;
        this.movementRotation = null;
        this.silentPlacementRotation = null;
        this.latestCandidate = null;
        this.suppressUseThisTick = false;
        this.lastTickNanos = 0L;
        this.takeoverDetectionAt = 0L;
        this.readyBrokenAt = 0L;
        this.activationMovementHold = false;
        this.assistReadyTicks = 0;
        this.assistStartedAtNanos = 0L;
        this.guidance.clearTarget();
    }

    private void resetActivation() {
        final boolean hadProgress =
                this.state == RuntimeState.PRIMING || this.state == RuntimeState.READY;
        this.lockedActivation = null;
        this.edgeHoldTicks = 0;
        this.readyBrokenAt = 0L;
        this.activationMovementHold = false;
        this.state = RuntimeState.ARMED;
        if (hadProgress) {
            this.restoreSafeWalk();
        }
    }

    private boolean hasGameContext() {
        return mc.player != null && mc.world != null && mc.interactionManager != null;
    }

    private boolean isSequenceActive() {
        return this.state == RuntimeState.SETUP || this.state == RuntimeState.RUNNING;
    }

    private boolean isControlActive() {
        return this.state == RuntimeState.ASSISTING || this.isSequenceActive();
    }

    private boolean isScaffoldEnabled() {
        final Module scaffold = OraculusClient.getInstance()
                .getModuleRepository().getOptionalModule("scaffold");
        return scaffold != null && scaffold.isEnabled();
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
        if (safeWalk != null && this.safeWalkWasEnabled && !safeWalk.isEnabled()) {
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
        if (mc.player == null || message == null || message.isBlank()) {
            return;
        }
        final boolean actionbarEnabled = this.actionbarGuide.getValue();
        final boolean islandEnabled = this.dynamicIslandGuide.getValue()
                && this.movementCoach.getValue()
                && mc.currentScreen == null;
        if (!actionbarEnabled && !islandEnabled) {
            this.guidance.suspendIsland();
            return;
        }
        final long now = System.nanoTime();
        final boolean changed = !message.equals(this.lastGuideMessage);
        if (!force && ((!changed && now - this.lastGuideAtNanos < GUIDE_REPEAT_NANOS)
                || (changed && now - this.lastGuideAtNanos < GUIDE_MIN_INTERVAL_NANOS))) {
            return;
        }
        this.lastGuideMessage = message;
        this.lastGuideAtNanos = now;
        this.guidance.publishInstruction(this.guidanceStage(), message, islandEnabled);
        if (actionbarEnabled) {
            mc.player.sendMessage(Text.literal("[Legit Telly] " + message), true);
        }
    }

    private String guidanceStage() {
        return switch (this.state) {
            case ARMED -> "Align";
            case ASSISTING -> "Assist";
            case PRIMING -> "Edge";
            case READY -> "Ready";
            case SETUP -> "Setup";
            case RUNNING -> "Run";
        };
    }

    private String activationGuide(
            final LegitTellyActivation.ActivationInspection inspection
    ) {
        return switch (inspection.issue()) {
            case WORLD_UNAVAILABLE -> "等待进入世界。";
            case ALIGN_DIAGONAL -> "把朝向对准最近的 45° 斜向。";
            case LOOK_DOWN -> "继续低头，俯角至少 75°。";
            case MOVE_TO_EDGE -> "靠近高亮边缘。";
            case FRONT_BLOCKED -> "前方空间被方块阻挡。";
            case AIM_AT_BLOCK -> "瞄准脚下起始方块。";
            case AIM_AT_FORWARD_SIDE -> "瞄准脚下方块朝前的侧面。";
            case AIM_AT_OWN_BLOCK -> "准星必须落在自己脚下的方块。";
            case AIM_AT_SIDE_CENTER -> "把准星移到高亮侧面中央区域。";
            case READY -> "位置正确，按住潜行开始锁定。";
        };
    }

    private static boolean sameActivation(
            final LegitTellyActivation.ActivationSnapshot first,
            final LegitTellyActivation.ActivationSnapshot second
    ) {
        return first != null && second != null
                && first.block().equals(second.block())
                && first.travel() == second.travel()
                && MathHelper.angleBetween(first.baseYaw(), second.baseYaw()) <= 2.0F;
    }

    private static boolean isDiagonalAligned(final float yaw) {
        return MathHelper.angleBetween(
                yaw, LegitTellyActivation.nearestDiagonalYaw(yaw)
        ) <= 2.0F;
    }

    private static String progressBar(final int percent) {
        final int filled = Math.max(0, Math.min(10, percent / 10));
        return "▰".repeat(filled) + "▱".repeat(10 - filled);
    }

    private enum RuntimeState {
        ARMED,
        ASSISTING,
        PRIMING,
        READY,
        SETUP,
        RUNNING
    }
}

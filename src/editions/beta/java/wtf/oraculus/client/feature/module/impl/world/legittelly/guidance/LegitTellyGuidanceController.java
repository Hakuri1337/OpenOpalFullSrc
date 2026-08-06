package wtf.oraculus.client.feature.module.impl.world.legittelly.guidance;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import wtf.oraculus.client.feature.module.impl.visual.overlay.impl.dynamicisland.DynamicIslandElement;
import wtf.oraculus.client.feature.module.impl.world.legittelly.LegitTellyActivation;
import wtf.oraculus.client.feature.module.impl.world.legittelly.LegitTellyTarget;
import wtf.oraculus.event.impl.render.RenderWorldEvent;

import static wtf.oraculus.client.Constants.mc;

public final class LegitTellyGuidanceController {
    private final LegitTellyGuidanceIsland island = new LegitTellyGuidanceIsland();
    private final LegitTellySideFaceRenderer sideRenderer = new LegitTellySideFaceRenderer();

    private LegitTellyGuidanceSnapshot snapshot;
    private String latestInstruction = "";
    private String latestStage = "GUIDE";
    private boolean islandEnabled;
    private boolean islandRegistered;

    public void updateActivation(
            final LegitTellyActivation.ActivationInspection inspection,
            final String stage
    ) {
        if (mc.player == null || mc.world == null || inspection == null
                || inspection.issue() == LegitTellyActivation.ActivationIssue.WORLD_UNAVAILABLE) {
            this.snapshot = null;
            return;
        }

        final LegitTellyActivation.ActivationSnapshot activation = inspection.snapshot();
        final Direction travel = activation == null
                ? LegitTellyActivation.travelDirectionForYaw(mc.player.getYaw())
                : activation.travel();
        final BlockPos block = activation == null
                ? LegitTellyActivation.supportBlockForPlayer(travel)
                : activation.block();
        final LegitTellyGuidanceSnapshot.Severity severity = switch (inspection.issue()) {
            case READY -> LegitTellyGuidanceSnapshot.Severity.READY;
            case FRONT_BLOCKED, WORLD_UNAVAILABLE ->
                    LegitTellyGuidanceSnapshot.Severity.BLOCKED;
            case ALIGN_DIAGONAL, LOOK_DOWN, MOVE_TO_EDGE, AIM_AT_BLOCK,
                 AIM_AT_FORWARD_SIDE, AIM_AT_OWN_BLOCK, AIM_AT_SIDE_CENTER ->
                    LegitTellyGuidanceSnapshot.Severity.ADJUST;
        };
        this.snapshot = new LegitTellyGuidanceSnapshot(
                block.toImmutable(),
                travel,
                LegitTellyActivation.sideAimPoint(block, travel),
                normalizeStage(stage),
                severity,
                true
        );
        this.refreshIsland();
    }

    public void updateTarget(final LegitTellyTarget target, final String stage) {
        if (target == null || target.hit() == null
                || !target.hit().getSide().getAxis().isHorizontal()) {
            this.snapshot = null;
            return;
        }
        final Direction face = target.hit().getSide();
        final Vec3d aimPoint = target.hit().getPos().add(
                face.getOffsetX() * 0.002D,
                0.0D,
                face.getOffsetZ() * 0.002D
        );
        this.snapshot = new LegitTellyGuidanceSnapshot(
                target.supportPos().toImmutable(),
                face,
                aimPoint,
                normalizeStage(stage),
                LegitTellyGuidanceSnapshot.Severity.INFO,
                false
        );
        this.refreshIsland();
    }

    public void updateAssist(
            final BlockPos supportPos,
            final Direction face,
            final Vec3d aimPoint,
            final boolean ready
    ) {
        if (supportPos == null || face == null || !face.getAxis().isHorizontal()) {
            this.snapshot = null;
            return;
        }
        this.snapshot = new LegitTellyGuidanceSnapshot(
                supportPos.toImmutable(),
                face,
                aimPoint,
                "ASSIST",
                ready
                        ? LegitTellyGuidanceSnapshot.Severity.READY
                        : LegitTellyGuidanceSnapshot.Severity.ADJUST,
                true
        );
        this.refreshIsland();
    }

    public void publishInstruction(
            final String stage,
            final String instruction,
            final boolean enabled
    ) {
        this.latestStage = normalizeStage(stage);
        this.latestInstruction = instruction == null ? "" : instruction;
        this.islandEnabled = enabled;
        this.refreshIsland();
    }

    public void setIslandEnabled(final boolean enabled) {
        if (this.islandEnabled == enabled) {
            return;
        }
        this.islandEnabled = enabled;
        this.refreshIsland();
    }

    public void render(
            final RenderWorldEvent event,
            final boolean enabled,
            final double opacity,
            final boolean showEyeLine
    ) {
        if (enabled) {
            this.sideRenderer.render(event, this.snapshot, opacity, showEyeLine);
        }
    }

    public void clearTarget() {
        this.snapshot = null;
    }

    public void suspendIsland() {
        this.islandEnabled = false;
        this.removeIsland();
    }

    public void clear() {
        this.snapshot = null;
        this.latestInstruction = "";
        this.latestStage = "GUIDE";
        this.islandEnabled = false;
        this.removeIsland();
    }

    private void refreshIsland() {
        if (!this.islandEnabled || this.latestInstruction.isBlank()
                || mc.player == null || mc.world == null) {
            this.removeIsland();
            return;
        }
        final int accent = this.snapshot == null
                ? 0xFF4A90E2
                : switch (this.snapshot.severity()) {
                    case READY -> 0xFF4BD98B;
                    case ADJUST -> 0xFFF2B84B;
                    case BLOCKED -> 0xFFE65A5A;
                    case INFO -> 0xFF4A90E2;
                };
        this.island.update(this.latestStage, this.latestInstruction, accent);
        if (!this.islandRegistered) {
            DynamicIslandElement.addTrigger(this.island);
            this.islandRegistered = true;
        }
    }

    private void removeIsland() {
        if (this.islandRegistered) {
            DynamicIslandElement.removeTrigger(this.island);
            this.islandRegistered = false;
        }
    }

    private static String normalizeStage(final String stage) {
        return stage == null || stage.isBlank()
                ? "GUIDE"
                : stage.toUpperCase(java.util.Locale.ROOT);
    }
}

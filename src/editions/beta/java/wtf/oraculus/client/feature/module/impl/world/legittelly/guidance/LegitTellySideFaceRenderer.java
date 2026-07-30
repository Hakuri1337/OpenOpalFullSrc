package wtf.oraculus.client.feature.module.impl.world.legittelly.guidance;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import wtf.oraculus.client.feature.module.impl.world.legittelly.LegitTellyActivation;
import wtf.oraculus.client.renderer.world.WorldRenderer;
import wtf.oraculus.event.impl.render.RenderWorldEvent;
import wtf.oraculus.utility.render.ColorUtility;
import wtf.oraculus.utility.render.CustomRenderLayers;

final class LegitTellySideFaceRenderer {
    private static final double PLANE_OFFSET = 0.005D;
    private static final double CROSS_HALF_SIZE = 0.055D;

    void render(
            final RenderWorldEvent event,
            final LegitTellyGuidanceSnapshot snapshot,
            final double opacity,
            final boolean showEyeLine
    ) {
        if (snapshot == null || snapshot.supportPos() == null
                || snapshot.face() == null || !snapshot.face().getAxis().isHorizontal()) {
            return;
        }

        final int accent = accent(snapshot.severity());
        final int fill = ColorUtility.applyOpacity(accent, (int) Math.round(255.0D * opacity));
        final int windowOutline = ColorUtility.applyOpacity(accent, 220);
        final int faceOutline = ColorUtility.applyOpacity(accent, 95);
        final VertexConsumerProvider.Immediate consumers =
                VertexConsumerProvider.immediate(new BufferAllocator(4096));
        final WorldRenderer renderer = new WorldRenderer(consumers);
        final BlockPos block = snapshot.supportPos();
        final Direction face = snapshot.face();

        final Vec3d fullBottomLeft = point(block, face, 0.0D, 0.0D);
        final Vec3d fullBottomRight = point(block, face, 1.0D, 0.0D);
        final Vec3d fullTopRight = point(block, face, 1.0D, 1.0D);
        final Vec3d fullTopLeft = point(block, face, 0.0D, 1.0D);
        drawLoop(renderer, event, faceOutline,
                fullBottomLeft, fullBottomRight, fullTopRight, fullTopLeft);

        final Vec3d rawAimPoint = snapshot.aimPoint() == null
                ? LegitTellyActivation.sideAimPoint(block, face)
                : snapshot.aimPoint();
        final double targetAcross = localAcross(block, face, rawAimPoint);
        final double targetHeight = rawAimPoint.y - block.getY();
        final double minAcross = snapshot.activationWindow()
                ? LegitTellyActivation.SIDE_MIN_ACROSS
                : clampFaceCoordinate(targetAcross - 0.12D);
        final double maxAcross = snapshot.activationWindow()
                ? LegitTellyActivation.SIDE_MAX_ACROSS
                : clampFaceCoordinate(targetAcross + 0.12D);
        final double minHeight = snapshot.activationWindow()
                ? LegitTellyActivation.SIDE_MIN_HEIGHT
                : clampFaceCoordinate(targetHeight - 0.18D);
        final double maxHeight = snapshot.activationWindow()
                ? LegitTellyActivation.SIDE_MAX_HEIGHT
                : clampFaceCoordinate(targetHeight + 0.18D);
        final Vec3d bottomLeft = point(block, face, minAcross, minHeight);
        final Vec3d bottomRight = point(block, face, maxAcross, minHeight);
        final Vec3d topRight = point(block, face, maxAcross, maxHeight);
        final Vec3d topLeft = point(block, face, minAcross, maxHeight);

        renderer.drawFilledQuad(
                event.matrixStack(),
                CustomRenderLayers.getPositionColorQuads(true),
                bottomLeft, bottomRight, topRight, topLeft,
                fill
        );
        drawLoop(renderer, event, windowOutline, bottomLeft, bottomRight, topRight, topLeft);

        final Vec3d aimPoint = point(
                block,
                face,
                snapshot.activationWindow()
                        ? (minAcross + maxAcross) * 0.5D
                        : targetAcross,
                snapshot.activationWindow()
                        ? (minHeight + maxHeight) * 0.5D
                        : targetHeight
        );
        final double centerAcross = snapshot.activationWindow()
                ? (minAcross + maxAcross) * 0.5D
                : targetAcross;
        final double centerHeight = snapshot.activationWindow()
                ? (minHeight + maxHeight) * 0.5D
                : targetHeight;
        renderer.drawLine(
                event.matrixStack(),
                CustomRenderLayers.getLines(2.0F, true),
                point(block, face, centerAcross - CROSS_HALF_SIZE, centerHeight),
                point(block, face, centerAcross + CROSS_HALF_SIZE, centerHeight),
                windowOutline
        );
        renderer.drawLine(
                event.matrixStack(),
                CustomRenderLayers.getLines(2.0F, true),
                point(block, face, centerAcross, centerHeight - CROSS_HALF_SIZE),
                point(block, face, centerAcross, centerHeight + CROSS_HALF_SIZE),
                windowOutline
        );
        if (showEyeLine && wtf.oraculus.client.Constants.mc.player != null) {
            renderer.drawLine(
                    event.matrixStack(),
                    CustomRenderLayers.getLines(1.0F, true),
                    wtf.oraculus.client.Constants.mc.player.getEyePos(),
                    aimPoint,
                    ColorUtility.applyOpacity(accent, 130)
            );
        }
        consumers.draw();
    }

    private static Vec3d point(
            final BlockPos block,
            final Direction face,
            final double across,
            final double height
    ) {
        return LegitTellyActivation.sidePoint(block, face, across, height, PLANE_OFFSET);
    }

    private static double localAcross(
            final BlockPos block,
            final Direction face,
            final Vec3d point
    ) {
        double across = face.getAxis() == Direction.Axis.Z
                ? point.x - block.getX()
                : point.z - block.getZ();
        if (face == Direction.SOUTH || face == Direction.WEST) {
            across = 1.0D - across;
        }
        return across;
    }

    private static double clampFaceCoordinate(final double coordinate) {
        return Math.max(0.04D, Math.min(0.96D, coordinate));
    }

    private static void drawLoop(
            final WorldRenderer renderer,
            final RenderWorldEvent event,
            final int color,
            final Vec3d first,
            final Vec3d second,
            final Vec3d third,
            final Vec3d fourth
    ) {
        renderer.drawLine(event.matrixStack(), CustomRenderLayers.getLines(1.5F, true),
                first, second, color);
        renderer.drawLine(event.matrixStack(), CustomRenderLayers.getLines(1.5F, true),
                second, third, color);
        renderer.drawLine(event.matrixStack(), CustomRenderLayers.getLines(1.5F, true),
                third, fourth, color);
        renderer.drawLine(event.matrixStack(), CustomRenderLayers.getLines(1.5F, true),
                fourth, first, color);
    }

    private static int accent(final LegitTellyGuidanceSnapshot.Severity severity) {
        return switch (severity) {
            case READY -> 0xFF4BD98B;
            case ADJUST -> 0xFFF2B84B;
            case BLOCKED -> 0xFFE65A5A;
            case INFO -> 0xFF4A90E2;
        };
    }
}

package wtf.oraculus.client.renderer;

import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.util.Window;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.opengl.GL33C;
import wtf.oraculus.utility.render.ColorUtility;
import wtf.oraculus.utility.render.GLUtility;
import wtf.oraculus.utility.render.ScreenPosition;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static org.lwjgl.nanovg.NanoVG.*;
import static org.lwjgl.nanovg.NanoVGGL3.*;
import static wtf.oraculus.client.Constants.mc;

public final class NVGRenderer {

    private static final long VG = nvgCreate(NVG_ANTIALIAS | NVG_STENCIL_STROKES);

    public static final NVGPaint NVG_PAINT = NVGPaint.create();
    public static final NVGPaint BLUR_PAINT = NVGPaint.create();
    public static final NVGPaint GLOW_PAINT = NVGPaint.create();

    public static final NVGColor NVG_COLOR_1 = NVGColor.create();
    public static final NVGColor NVG_COLOR_2 = NVGColor.create();

    private static boolean frameStarted;
    public static float globalAlpha = 1;

    public static boolean beginFrame() {
        final Window window = mc.getWindow();
        final float scaleFactor = (float) window.getScaleFactor();

        if (!frameStarted) {
            GLUtility.setup();
            GLUtility.push();

            nvgBeginFrame(VG, window.getFramebufferWidth() / scaleFactor, window.getFramebufferHeight() / scaleFactor, scaleFactor);
            if (!scissors.isEmpty()) {
                useCurrentScissors();
            }
            frameStarted = true;

            return true;
        }

        return false;
    }

    public static void endFrameAndReset(final boolean createRenderPass) {
        endFrame(createRenderPass);
        clearScissors();
    }

    public static void endFrame(final boolean createRenderPass) {
        if (frameStarted) {
            if (createRenderPass) {
                final Framebuffer framebuffer = mc.getFramebuffer();
                try (RenderPass renderPass = RenderSystem.getDevice()
                        .createCommandEncoder()
                        .createRenderPass(() -> "oraculus/nvg", framebuffer.getColorAttachmentView(), OptionalInt.empty(), framebuffer.useDepthAttachment ? framebuffer.getDepthAttachmentView() : null, OptionalDouble.empty())) {
                    renderPass.setPipeline(RenderPipelines.GUI);

                    nvgEndFrame(VG);
                }
            } else {
                nvgEndFrame(VG);
            }

            GLUtility.pop();

            // founded by unc (trol1337)
            GL33C.glViewport(0, 0, mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight());

            frameStarted = false;
        }
    }

    public static void clearScissors() {
        scissors.clear();
    }

    public static void globalAlpha(final float alpha) {
        globalAlpha = alpha;
        nvgGlobalAlpha(VG, alpha);
    }

    public static void rect(final float x, final float y, final float width, final float height, final int color) {
        applyColor(color, NVG_COLOR_1);

        nvgBeginPath(VG);
        nvgFillColor(VG, NVG_COLOR_1);
        nvgRect(VG, x, y, width, height);
        nvgFill(VG);
        nvgClosePath(VG);
    }

    public static void rect(final float x, final float y, final float width, final float height, final NVGPaint nvgPaint) {
        nvgBeginPath(VG);
        nvgFillPaint(VG, nvgPaint);
        nvgRect(VG, x, y, width, height);
        nvgFill(VG);
        nvgClosePath(VG);
    }

    public static void scale(final float factor, final float x, final float y, final float width, final float height, final Runnable content) {
        final float translateX = x + width / 2F;
        final float translateY = y + height / 2F;

        nvgSave(VG);
        nvgTranslate(VG, translateX, translateY);
        nvgScale(VG, factor, factor);
        nvgTranslate(VG, -translateX, -translateY);

        content.run();

        nvgRestore(VG);
    }

    public static void rectStroke(final float x, final float y, final float width, final float height, final float strokeThickness, final int color, final int strokeColor) {
        rect(x - strokeThickness, y - strokeThickness, width + (strokeThickness * 2), height + (strokeThickness * 2), strokeColor);
        rect(x, y, width, height, color);
    }

    public static void rotate(final double degrees, final float x, final float y, final float width, final float height, final Runnable content) {
        final float translateX = x + width / 2f;
        final float translateY = y + height / 2f;

        nvgSave(VG);
        nvgTranslate(VG, translateX, translateY);
        nvgRotate(VG, (float) Math.toRadians(degrees));

        content.run();

        nvgRestore(VG);
    }

    public static void rectOutlineStroke(final float x, final float y, final float width, final float height, final float outlineThickness, final float strokeThickness, final int outlineColor, final int strokeColor) {
        rectOutline(x - outlineThickness, y - outlineThickness, width + (outlineThickness * 2), height + (outlineThickness * 2), strokeThickness, strokeColor);
        rectOutline(x, y, width, height, outlineThickness, outlineColor);
    }

    public static void rectOutline(final float x, final float y, final float width, final float height, final float thickness, final int color) {
        applyColor(color, NVG_COLOR_1);

        nvgBeginPath(VG);
        nvgFillColor(VG, NVG_COLOR_1);
        nvgRect(VG, x, y, width, thickness);
        nvgFill(VG);
        nvgClosePath(VG);

        nvgBeginPath(VG);
        nvgFillColor(VG, NVG_COLOR_1);
        nvgRect(VG, x + width - thickness, y + thickness, thickness, height - thickness);
        nvgFill(VG);
        nvgClosePath(VG);

        nvgBeginPath(VG);
        nvgFillColor(VG, NVG_COLOR_1);
        nvgRect(VG, x, y + height - thickness, width - thickness, thickness);
        nvgFill(VG);
        nvgClosePath(VG);

        nvgBeginPath(VG);
        nvgFillColor(VG, NVG_COLOR_1);
        nvgRect(VG, x, y + thickness, thickness, height - thickness);
        nvgFill(VG);
        nvgClosePath(VG);
    }

    public static void rainbowRect(final float x, final float y, final float width, final float height) {
        for (float i = y; i < y + height; i += 0.5f) {
            final float hue = (i - y) / height;
            final int rgbColor = Color.HSBtoRGB(hue, 1, 1);

            final float segmentHeight = Math.min(0.5f, y + height - i);

            nvgShapeAntiAlias(VG, false);
            rect(x, i, width, segmentHeight, rgbColor);
            nvgShapeAntiAlias(VG, true);
        }
    }

    public static void roundedRectOutline(final float x, final float y, final float width, final float height, final float radius, final float thickness, final int color) {
        applyColor(color, NVG_COLOR_1);

        nvgBeginPath(VG);
        nvgStrokeColor(VG, NVG_COLOR_1);
        nvgStrokeWidth(VG, thickness);
        nvgRoundedRect(
                VG,
                x,
                y,
                width,
                height,
                radius
        );
        nvgStroke(VG);
        nvgClosePath(VG);
    }

    private static final List<ScreenPosition> scissors = new ArrayList<>();

    public static void scissor(final float x, final float y, final float width, final float height, final Runnable content) {
        ScreenPosition scissor = new ScreenPosition(x, y, width, height);
        scissors.add(scissor);

        nvgIntersectScissor(VG, x, y, width, height);
        content.run();
        nvgResetScissor(VG);

        scissors.remove(scissor);
        useCurrentScissors();
    }

    private static void useCurrentScissors() {
        for (ScreenPosition scissor : scissors) {
            nvgIntersectScissor(VG, scissor.getX(), scissor.getY(), scissor.getWidth(), scissor.getHeight());
        }
    }

    public static void roundedRect(final float x, final float y, final float width, final float height, final float radius, final int color) {
        applyColor(color, NVG_COLOR_1);

        nvgBeginPath(VG);
        nvgFillColor(VG, NVG_COLOR_1);
        nvgRoundedRect(VG, x, y, width, height, radius);
        nvgFill(VG);
        nvgClosePath(VG);
    }

    public static void roundedRectGradient(final float x, final float y, final float width, final float height, final float radius, final int color1, final int color2, final float angleDegrees) {
        applyColor(color1, NVG_COLOR_1);
        applyColor(color2, NVG_COLOR_2);

        final float angleRadians = (float) Math.toRadians(angleDegrees);
        final float dx = (float) Math.cos(angleRadians);
        final float dy = (float) Math.sin(angleRadians);

        nvgLinearGradient(
                VG,
                x + width * 0.5f - dx * width * 0.5f,
                y + height * 0.5f - dy * height * 0.5f,
                x + width * 0.5f + dx * width * 0.5f,
                y + height * 0.5f + dy * height * 0.5f,
                NVG_COLOR_1,
                NVG_COLOR_2,
                NVG_PAINT
        );

        nvgBeginPath(VG);
        nvgFillPaint(VG, NVG_PAINT);
        nvgRoundedRect(
                VG,
                x,
                y,
                width,
                height,
                radius
        );
        nvgFill(VG);
        nvgClosePath(VG);
    }

    public static void rectGradient(final float x, final float y, final float width, final float height, final int color1, final int color2, final float angleDegrees) {
        applyColor(color1, NVG_COLOR_1);
        applyColor(color2, NVG_COLOR_2);

        final float angleRadians = (float) Math.toRadians(angleDegrees);
        final float dx = (float) Math.cos(angleRadians);
        final float dy = (float) Math.sin(angleRadians);

        nvgLinearGradient(
                VG,
                x + width * 0.5f - dx * width * 0.5f,
                y + height * 0.5f - dy * height * 0.5f,
                x + width * 0.5f + dx * width * 0.5f,
                y + height * 0.5f + dy * height * 0.5f,
                NVG_COLOR_1,
                NVG_COLOR_2,
                NVG_PAINT
        );

        nvgBeginPath(VG);
        nvgFillPaint(VG, NVG_PAINT);
        nvgRect(
                VG,
                x,
                y,
                width,
                height
        );
        nvgFill(VG);
        nvgClosePath(VG);
    }

    public static void roundedRect(final float x, final float y, final float width, final float height, final float radius, final NVGPaint nvgPaint) {
        nvgBeginPath(VG);
        nvgFillPaint(VG, nvgPaint);
        nvgRoundedRect(VG, x, y, width, height, radius);
        nvgFill(VG);
        nvgClosePath(VG);
    }

    public static void roundedRectVarying(final float x, final float y, final float width, final float height, final float radiusTopLeft, final float radiusTopRight, final float radiusBottomRight, final float radiusBottomLeft, final int color) {
        applyColor(color, NVG_COLOR_1);

        nvgBeginPath(VG);
        nvgFillColor(VG, NVG_COLOR_1);
        nvgRoundedRectVarying(
                VG,
                x,
                y,
                width,
                height,
                radiusTopLeft,
                radiusTopRight,
                radiusBottomRight,
                radiusBottomLeft
        );
        nvgFill(VG);
        nvgClosePath(VG);
    }

    public static void roundedRectVaryingGradient(final float x, final float y, final float width, final float height, final float radiusTopLeft, final float radiusTopRight, final float radiusBottomRight, final float radiusBottomLeft, final int color1, final int color2, final float angleDegrees) {
        applyColor(color1, NVG_COLOR_1);
        applyColor(color2, NVG_COLOR_2);

        final float angleRadians = (float) Math.toRadians(angleDegrees);
        final float dx = (float) Math.cos(angleRadians);
        final float dy = (float) Math.sin(angleRadians);

        nvgLinearGradient(
                VG,
                x + width * 0.5f - dx * width * 0.5f,
                y + height * 0.5f - dy * height * 0.5f,
                x + width * 0.5f + dx * width * 0.5f,
                y + height * 0.5f + dy * height * 0.5f,
                NVG_COLOR_1,
                NVG_COLOR_2,
                NVG_PAINT
        );

        nvgBeginPath(VG);
        nvgFillPaint(VG, NVG_PAINT);
        nvgRoundedRectVarying(
                VG,
                x,
                y,
                width,
                height,
                radiusTopLeft,
                radiusTopRight,
                radiusBottomRight,
                radiusBottomLeft
        );
        nvgFill(VG);
        nvgClosePath(VG);
    }

    public static void roundedRectVarying(final float x, final float y, final float width, final float height, final float radiusTopLeft, final float radiusTopRight, final float radiusBottomRight, final float radiusBottomLeft, final NVGPaint nvgPaint) {
        nvgBeginPath(VG);
        nvgFillPaint(VG, nvgPaint);
        nvgRoundedRectVarying(
                VG,
                x,
                y,
                width,
                height,
                radiusTopLeft,
                radiusTopRight,
                radiusBottomRight,
                radiusBottomLeft
        );
        nvgFill(VG);
        nvgClosePath(VG);
    }

    public static void applyColor(final int color, final NVGColor nvgColor) {
        final int[] rgba = ColorUtility.hexToRGBA(color);

        nvgRGBAf(rgba[0] / 255f, rgba[1] / 255f, rgba[2] / 255f, rgba[3] / 255f, nvgColor);
    }

    public static void createNVGPaintFromTex(final int width, final int height, final int glTex, final NVGPaint nvgPaint) {
        final int imageHandle = nvglCreateImageFromHandle(VG, glTex, width, height, NVG_IMAGE_GENERATE_MIPMAPS | NVG_IMAGE_FLIPY);

        nvgImagePattern(VG, 0, 0, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(), 0, imageHandle, NVG_IMAGE_GENERATE_MIPMAPS, nvgPaint);
    }

    public static long getContext() {
        return VG;
    }
}

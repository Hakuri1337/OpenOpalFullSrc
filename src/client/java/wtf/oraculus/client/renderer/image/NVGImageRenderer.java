package wtf.oraculus.client.renderer.image;

import wtf.oraculus.client.renderer.NVGRenderer;
import wtf.oraculus.utility.misc.system.IOUtility;

import java.io.InputStream;
import java.nio.ByteBuffer;

import static org.lwjgl.nanovg.NanoVG.*;
import static wtf.oraculus.client.Constants.VG;

public final class NVGImageRenderer {

    private final ByteBuffer imageData;
    private final int imageHandle;

    public NVGImageRenderer(final InputStream inputStream, final int flags) {
        this.imageData = IOUtility.ioResourceToByteBuffer(inputStream, 512 * 1024);
        this.imageHandle = nvgCreateImageMem(VG, flags, this.imageData);
    }

    public NVGImageRenderer(final InputStream inputStream) {
        this(inputStream, 0);
    }

    public void drawImage(final float x, final float y, final float width, final float height) {
        nvgImagePattern(
                VG,
                x,
                y,
                width,
                height,
                0,
                imageHandle,
                1,
                NVGRenderer.NVG_PAINT
        );

        nvgBeginPath(VG);
        nvgRect(
                VG,
                x,
                y,
                width,
                height
        );
        nvgImagePattern(
                VG,
                x,
                y,
                width,
                height,
                0,
                imageHandle,
                1,
                NVGRenderer.NVG_PAINT
        );
        nvgFillPaint(VG, NVGRenderer.NVG_PAINT);
        nvgFill(VG);
        nvgClosePath(VG);
    }

    public void drawRoundedImage(final float x, final float y, final float width, final float height, final float radius) {
        nvgBeginPath(VG);
        nvgRoundedRect(VG, x, y, width, height, radius);
        nvgImagePattern(VG, x, y, width, height, 0, imageHandle, 1, NVGRenderer.NVG_PAINT);
        nvgFillPaint(VG, NVGRenderer.NVG_PAINT);
        nvgFill(VG);
        nvgClosePath(VG);
    }

    public void drawRoundedImageCover(
            final float x,
            final float y,
            final float width,
            final float height,
            final float radius,
            final float sourceWidth,
            final float sourceHeight
    ) {
        final float coverScale = Math.max(width / sourceWidth, height / sourceHeight);
        final float imageWidth = sourceWidth * coverScale;
        final float imageHeight = sourceHeight * coverScale;
        final float imageX = x + (width - imageWidth) / 2.F;
        final float imageY = y + (height - imageHeight) / 2.F;

        nvgBeginPath(VG);
        nvgRoundedRect(VG, x, y, width, height, radius);
        nvgImagePattern(VG, imageX, imageY, imageWidth, imageHeight, 0, imageHandle, 1, NVGRenderer.NVG_PAINT);
        nvgFillPaint(VG, NVGRenderer.NVG_PAINT);
        nvgFill(VG);
        nvgClosePath(VG);
    }

    public void drawImage(final float x, final float y, final float width, final float height, final int colorOverlay) {
        nvgImagePattern(
                VG,
                x,
                y,
                width,
                height,
                0,
                imageHandle,
                1,
                NVGRenderer.NVG_PAINT
        );

        nvgBeginPath(VG);
        nvgRect(
                VG,
                x,
                y,
                width,
                height
        );
        nvgImagePattern(
                VG,
                x,
                y,
                width,
                height,
                0,
                imageHandle,
                1,
                NVGRenderer.NVG_PAINT
        );
        NVGRenderer.applyColor(colorOverlay, NVGRenderer.NVG_COLOR_1);
        NVGRenderer.NVG_PAINT.innerColor(NVGRenderer.NVG_COLOR_1);

        nvgFillPaint(VG, NVGRenderer.NVG_PAINT);
        nvgFill(VG);
        nvgClosePath(VG);
    }

}

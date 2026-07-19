package wtf.oraculus.utility.render;

import com.ibm.icu.impl.Pair;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.module.impl.visual.overlay.OverlayModule;
import wtf.oraculus.utility.misc.math.MathUtility;

import java.awt.*;

public final class ColorUtility {

    private ColorUtility() {
    }

    public static final int MUTED_COLOR = 0xFF808080;

    public static Pair<Integer, Integer> getClientTheme() {
        final OverlayModule overlayModule = OraculusClient.getInstance().getModuleRepository().getModule(OverlayModule.class);
        return overlayModule.getThemeMode().getValue().getColors();
    }

    public static int getShadowColor(final int color) {
        return (color & 0xFCFCFC) >> 2 | color & 0xFF000000;
    }

    public static int[] hexToRGBA(final int hex) {
        final int red = (hex >> 16) & 0xFF;
        final int green = (hex >> 8) & 0xFF;
        final int blue = hex & 0xFF;
        final int alpha = (hex >> 24) & 0xFF;

        return new int[]{red, green, blue, alpha};
    }

    public static int[] hexToRGB(final int hex) {
        final int red = (hex >> 16) & 0xFF;
        final int green = (hex >> 8) & 0xFF;
        final int blue = hex & 0xFF;

        return new int[]{red, green, blue};
    }

    public static int rgbToHex(final int red, final int green, final int blue) {
        return (red << 16) | (green << 8) | blue;
    }

    public static int rgbaToHex(final int red, final int green, final int blue, final int alpha) {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    public static int darker(final int color, final float factor) {
        final float f = 1 - factor;
        final int r = (int) ((color >> 16 & 0xFF) * f);
        final int g = (int) ((color >> 8 & 0xFF) * f);
        final int b = (int) ((color & 0xFF) * f);
        final int a = color >> 24 & 0xFF;
        return ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF) | ((a & 0xFF) << 24);
    }

    public static int brighter(final int color, final float factor) {
        final float f = 1 / (1 - factor);
        final int r = (color >> 16) & 0xFF;
        final int g = (color >> 8) & 0xFF;
        final int b = color & 0xFF;
        final int a = (color >> 24) & 0xFF;

        if (r == 0 && g == 0 && b == 0) {
            int grey = (int) (1.0 / (1.0 - factor));
            return ((a & 0xFF) << 24) | ((grey & 0xFF) << 16) | ((grey & 0xFF) << 8) | (grey & 0xFF);
        }

        int minBrightness = (int) (1.0 / (1.0 - factor));
        int newR = r > 0 && r < minBrightness ? minBrightness : r;
        int newG = g > 0 && g < minBrightness ? minBrightness : g;
        int newB = b > 0 && b < minBrightness ? minBrightness : b;

        newR = Math.min((int) (newR * f), 255);
        newG = Math.min((int) (newG * f), 255);
        newB = Math.min((int) (newB * f), 255);

        return ((a & 0xFF) << 24) | ((newR & 0xFF) << 16) | ((newG & 0xFF) << 8) | (newB & 0xFF);
    }

    public static int applyOpacity(final int color, float opacityFactor) {
        opacityFactor = Math.min(1, Math.max(0, opacityFactor));
        final int[] colorRGBA = hexToRGBA(color);
        return rgbaToHex(colorRGBA[0], colorRGBA[1], colorRGBA[2], (int) (opacityFactor * 255.F));
    }

    public static int applyOpacity(final int color, int opacity) {
        opacity = Math.min(255, Math.max(0, opacity));
        final int[] colorRGBA = hexToRGBA(color);
        return rgbaToHex(colorRGBA[0], colorRGBA[1], colorRGBA[2], opacity);
    }

    public static int interpolateColors(final int color1, final int color2, float amount) {
        amount = Math.min(1, Math.max(0, amount));

        final int[] color1RGBA = hexToRGBA(color1);
        final int[] color2RGBA = hexToRGBA(color2);

        final int r = (int) MathUtility.interpolate(color1RGBA[0], color2RGBA[0], amount);
        final int g = (int) MathUtility.interpolate(color1RGBA[1], color2RGBA[1], amount);
        final int b = (int) MathUtility.interpolate(color1RGBA[2], color2RGBA[2], amount);
        final int a = (int) MathUtility.interpolate(color1RGBA[3], color2RGBA[3], amount);

        return rgbaToHex(r, g, b, a);
    }

    public static int interpolateColorsHSB(final int color1, final int color2, float amount) {
        amount = Math.min(1, Math.max(0, amount));

        final int[] color1RGBA = hexToRGBA(color1);
        final int[] color2RGBA = hexToRGBA(color2);
        final float[] hsb1 = Color.RGBtoHSB(color1RGBA[0], color1RGBA[1], color1RGBA[2], null);
        final float[] hsb2 = Color.RGBtoHSB(color2RGBA[0], color2RGBA[1], color2RGBA[2], null);

        float hueDelta = hsb2[0] - hsb1[0];
        if (Math.abs(hueDelta) > 0.5F) {
            hueDelta += hueDelta > 0.0F ? -1.0F : 1.0F;
        }

        final float hue = (hsb1[0] + hueDelta * amount + 1.0F) % 1.0F;
        final float saturation = (float) MathUtility.interpolate(hsb1[1], hsb2[1], amount);
        final float brightness = (float) MathUtility.interpolate(hsb1[2], hsb2[2], amount);
        final int alpha = (int) MathUtility.interpolate(color1RGBA[3], color2RGBA[3], amount);
        return (alpha << 24) | (Color.HSBtoRGB(hue, saturation, brightness) & 0x00FFFFFF);
    }

    public static int rainbow(final int speed, final int index, final float saturation, final float brightness) {
        final int angle = (int) ((System.currentTimeMillis() / speed + index) % 360);
        final float hue = angle / 360f;

        return Color.HSBtoRGB(hue, saturation, brightness);
    }

    public static int interpolateColorsBackAndForth(final int speed, final int index, final int startColor, final int endColor) {
        int angle = (int) (((System.currentTimeMillis()) / speed - index) % 360);
        angle = (angle >= 180 ? 360 - angle : angle) * 2;
        return interpolateColors(startColor, endColor, angle / 360f);
    }

}

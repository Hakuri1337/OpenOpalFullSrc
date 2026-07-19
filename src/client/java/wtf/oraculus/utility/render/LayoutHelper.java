package wtf.oraculus.utility.render;

import net.minecraft.client.gui.DrawContext;

public final class LayoutHelper {
    public static float percentWidth(DrawContext ctx, float percent) {
        return ctx.getScaledWindowWidth() * percent;
    }

    public static float percentHeight(DrawContext ctx, float percent) {
        return ctx.getScaledWindowHeight() * percent;
    }

    public static float centerX(DrawContext ctx, float elementWidth) {
        return ctx.getScaledWindowWidth() / 2f - elementWidth / 2f;
    }

    public static float centerY(DrawContext ctx, float elementHeight) {
        return ctx.getScaledWindowHeight() / 2f - elementHeight / 2f;
    }
}


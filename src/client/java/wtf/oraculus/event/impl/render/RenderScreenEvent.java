package wtf.oraculus.event.impl.render;

import net.minecraft.client.gui.DrawContext;

public record RenderScreenEvent(DrawContext drawContext, float tickDelta, double mouseX, double mouseY) {
}

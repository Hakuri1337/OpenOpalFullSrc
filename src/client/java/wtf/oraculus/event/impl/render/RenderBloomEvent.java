package wtf.oraculus.event.impl.render;

import net.minecraft.client.gui.DrawContext;

public record RenderBloomEvent(DrawContext drawContext, float tickDelta) {
}

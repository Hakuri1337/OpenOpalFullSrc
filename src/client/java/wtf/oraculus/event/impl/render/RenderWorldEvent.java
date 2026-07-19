package wtf.oraculus.event.impl.render;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;

public record RenderWorldEvent(VertexConsumerProvider.Immediate vertexConsumers,
                               MatrixStack matrixStack, float tickDelta) {
}

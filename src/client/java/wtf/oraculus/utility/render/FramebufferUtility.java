package wtf.oraculus.utility.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.Framebuffer;

public final class FramebufferUtility {

    private FramebufferUtility() {
    }

    public static void blit(final Framebuffer sourceBuffer, final Framebuffer destinationBuffer) {
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(sourceBuffer.getColorAttachment(), destinationBuffer.getColorAttachment(), 0, 0, 0, 0, 0, destinationBuffer.textureWidth, destinationBuffer.textureHeight);
    }

}

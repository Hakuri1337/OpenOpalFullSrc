package wtf.oraculus.client.renderer.motionblur;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import wtf.oraculus.mixin.GameRendererAccessor;

import java.nio.ByteBuffer;

import static wtf.oraculus.client.Constants.mc;

/** Owns the persistent history framebuffer used by the Motion Blur post effect. */
public final class MotionBlurRenderer {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Identifier EFFECT_ID = Identifier.of("oraculus", "motion_blur");
    private static final int CONFIG_SIZE = new Std140SizeCalculator().putFloat().putFloat().putVec2().get();

    private static PostEffectProcessor processor;
    private static GpuBuffer configBuffer;
    private static ClientWorld renderedWorld;
    private static boolean historyInvalid = true;
    private static boolean rendering;

    private MotionBlurRenderer() {
    }

    public static void render(final float strength) {
        if (mc.player == null || mc.world == null || mc.player.age <= 10 || strength <= 0.0F) {
            invalidateHistory();
            return;
        }

        if (renderedWorld != mc.world) {
            renderedWorld = mc.world;
            historyInvalid = true;
        }

        try {
            if (processor == null) {
                processor = mc.getShaderLoader().loadPostEffect(EFFECT_ID, DefaultFramebufferSet.MAIN_ONLY);
                historyInvalid = true;
            }

            final float currentFrameWeight = 1.0F - Math.min(strength / 10.0F, 0.9F);
            final FrameGraphBuilder frameGraph = new FrameGraphBuilder();
            final PostEffectProcessor.FramebufferSet framebuffers = PostEffectProcessor.FramebufferSet.singleton(
                    PostEffectProcessor.MAIN,
                    frameGraph.createObjectNode("oraculus motion blur main", mc.getFramebuffer())
            );

            rendering = true;
            try {
                updateConfig(currentFrameWeight, historyInvalid);
                processor.render(frameGraph, mc.getFramebuffer().textureWidth, mc.getFramebuffer().textureHeight, framebuffers);
                frameGraph.run(((GameRendererAccessor) mc.gameRenderer).getPool());
                historyInvalid = false;
            } finally {
                rendering = false;
            }
        } catch (RuntimeException exception) {
            // Resource reloads can retire a post effect while it is still cached.
            LOGGER.debug("Discarding stale Oraculus motion blur post effect", exception);
            closeProcessor();
            historyInvalid = true;
        }
    }

    public static void invalidateHistory() {
        historyInvalid = true;
        renderedWorld = null;
    }

    public static boolean isRenderingMotionBlurPass(final String passId) {
        return rendering && passId != null && passId.endsWith("motion_blur/0");
    }

    public static GpuBuffer getConfigBuffer() {
        if (configBuffer == null || configBuffer.isClosed()) {
            configBuffer = RenderSystem.getDevice().createBuffer(() -> "Oraculus Motion Blur Config", 130, CONFIG_SIZE);
        }
        return configBuffer;
    }

    private static void updateConfig(final float currentFrameWeight, final boolean resetHistory) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final ByteBuffer data = Std140Builder.onStack(stack, CONFIG_SIZE)
                    .putFloat(currentFrameWeight)
                    .putFloat(resetHistory ? 1.0F : 0.0F)
                    .putVec2(0.0F, 0.0F)
                    .get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(getConfigBuffer().slice(), data);
        }
    }

    private static void closeProcessor() {
        if (processor != null) {
            processor.close();
            processor = null;
        }
    }
}

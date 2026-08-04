package wtf.oraculus.client.renderer.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.logging.LogUtils;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.WindowFramebuffer;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.util.Identifier;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.module.impl.visual.PostProcessingModule;
import wtf.oraculus.client.feature.module.impl.visual.PotionModule;
import wtf.oraculus.client.feature.module.impl.visual.ClickGUIModule;
import wtf.oraculus.client.feature.module.impl.visual.TabGUIModule;
import wtf.oraculus.client.feature.module.impl.visual.overlay.OverlayModule;
import wtf.oraculus.client.renderer.NVGRenderer;
import wtf.oraculus.mixin.GameRendererAccessor;
import wtf.oraculus.utility.render.FramebufferUtility;
import org.slf4j.Logger;

import static wtf.oraculus.client.Constants.mc;

public final class ShaderFramebuffer {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static Framebuffer blurFramebuffer, glowFramebuffer, liquidGlassFramebuffer;
    private static Framebuffer liquidGlassSourceFramebuffer;

    private static final Identifier BLUR_IDENTIFIER = Identifier.ofVanilla("blur");
    public static final CustomUniform CUSTOM_UNIFORM = new CustomUniform();

    private static PostEffectProcessor postEffectProcessor;

    public static void applyBlurToFullScreen() {
        if (blurFramebuffer == null) return;

        final PostProcessingModule postProcessingModule = OraculusClient.getInstance().getModuleRepository().getModule(PostProcessingModule.class);

        if (!postProcessingModule.isEnabled() || !postProcessingModule.isBlur()) {
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(blurFramebuffer.getColorAttachment(), 0, blurFramebuffer.getDepthAttachment(), 1.0);
            return;
        }

        final Framebuffer mainBuffer = mc.getFramebuffer();

        FramebufferUtility.blit(mainBuffer, blurFramebuffer);

        renderBlurToFramebuffer(blurFramebuffer, postProcessingModule.getBlurRadius());
    }

    public static void applyGlowToNVGObjects() {
        if (glowFramebuffer == null) return;

        final PostProcessingModule postProcessingModule = OraculusClient.getInstance().getModuleRepository().getModule(PostProcessingModule.class);

        if (!postProcessingModule.isEnabled() || !postProcessingModule.isBloom()) {
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(glowFramebuffer.getColorAttachment(), 0, glowFramebuffer.getDepthAttachment(), 1.0);
            return;
        }

        renderBlurToFramebuffer(glowFramebuffer, postProcessingModule.getBloomRadius());
    }

    public static void applyLiquidGlassToFullScreen() {
        liquidGlassSourceFramebuffer = null;

        final OverlayModule overlayModule = OraculusClient.getInstance().getModuleRepository().getModule(OverlayModule.class);
        final PotionModule potionModule = OraculusClient.getInstance().getModuleRepository().getModule(PotionModule.class);
        final ClickGUIModule clickGUI = OraculusClient.getInstance().getModuleRepository().getModule(ClickGUIModule.class);
        final TabGUIModule tabGUI = OraculusClient.getInstance().getModuleRepository().getModule(TabGUIModule.class);
        if ((!overlayModule.isEnabled()
                && !potionModule.isEnabled()
                && (clickGUI == null || !clickGUI.isEnabled())
                && (tabGUI == null || !tabGUI.isEnabled()))
                || !overlayModule.isAnyLiquidGlassV2()) {
            return;
        }

        ensureLiquidGlassFramebuffer();
        FramebufferUtility.blit(mc.getFramebuffer(), liquidGlassFramebuffer);
        liquidGlassSourceFramebuffer = liquidGlassFramebuffer;
    }

    private static void ensureLiquidGlassFramebuffer() {
        final int width = mc.getWindow().getFramebufferWidth();
        final int height = mc.getWindow().getFramebufferHeight();
        if (liquidGlassFramebuffer != null
                && liquidGlassFramebuffer.textureWidth == width
                && liquidGlassFramebuffer.textureHeight == height) {
            return;
        }

        if (liquidGlassFramebuffer != null) {
            liquidGlassFramebuffer.delete();
        }
        liquidGlassFramebuffer = new SimpleFramebuffer("Oraculus LiquidGlass V2", width, height, false);
        liquidGlassFramebuffer.setFilter(FilterMode.LINEAR);
        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(liquidGlassFramebuffer.getColorAttachment(), 0);
    }

    public static void captureMenuBlur(final int radius) {
        if (blurFramebuffer == null || mc.getOverlay() instanceof SplashOverlay) {
            return;
        }
        FramebufferUtility.blit(mc.getFramebuffer(), blurFramebuffer);
        renderBlurToFramebuffer(blurFramebuffer, radius);
    }

    private static void renderBlurToFramebuffer(final Framebuffer framebuffer, final int radius) {
        if (mc.getOverlay() instanceof SplashOverlay) {
            // A resource reload can begin while the current frame graph still owns this processor.
            // Closing it here invalidates its MappableRingBuffer before vanilla finishes renderBlur.
            postEffectProcessor = null;
            return;
        }

        if (postEffectProcessor == null) {
            postEffectProcessor = mc.getShaderLoader().loadPostEffect(
                    BLUR_IDENTIFIER, DefaultFramebufferSet.MAIN_ONLY
            );
        } else {
            final FrameGraphBuilder frameGraphBuilder = new FrameGraphBuilder();
            final PostEffectProcessor.FramebufferSet framebufferSet = PostEffectProcessor.FramebufferSet.singleton(
                    Identifier.ofVanilla("main"), frameGraphBuilder.createObjectNode("main", framebuffer)
            );
            CUSTOM_UNIFORM.use(mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight(), radius, () -> {
                try {
                    postEffectProcessor.render(frameGraphBuilder, framebuffer.textureWidth, framebuffer.textureHeight, framebufferSet);
                    frameGraphBuilder.run(((GameRendererAccessor) mc.gameRenderer).getPool());
                } catch (IllegalStateException exception) {
                    // Shader/resource reloads may retire a post effect between frames. Rebuild it on the next frame.
                    LOGGER.debug("Discarding stale Oraculus post effect after GPU resource invalidation", exception);
                    postEffectProcessor = null;
                }
            });
        }
    }

    public static void onResized(final int width, final int height) {
        if (blurFramebuffer != null)
            blurFramebuffer.delete();
        if (glowFramebuffer != null)
            glowFramebuffer.delete();
        if (liquidGlassFramebuffer != null)
            liquidGlassFramebuffer.delete();

        blurFramebuffer = new WindowFramebuffer(width, height);
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(blurFramebuffer.getColorAttachment(), 0, blurFramebuffer.getDepthAttachment(), 1.0);

        glowFramebuffer = new WindowFramebuffer(width, height);
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(glowFramebuffer.getColorAttachment(), 0, glowFramebuffer.getDepthAttachment(), 1.0);

        liquidGlassFramebuffer = null;
        liquidGlassSourceFramebuffer = null;

        NVGRenderer.createNVGPaintFromTex(width, height, Integer.parseInt(blurFramebuffer.getColorAttachment().getLabel()), NVGRenderer.BLUR_PAINT);
        NVGRenderer.createNVGPaintFromTex(width, height, Integer.parseInt(glowFramebuffer.getColorAttachment().getLabel()), NVGRenderer.GLOW_PAINT);
    }

    public static Framebuffer getGlowFramebuffer() {
        return glowFramebuffer;
    }

    public static Framebuffer getLiquidGlassSourceFramebuffer() {
        return liquidGlassSourceFramebuffer;
    }
}

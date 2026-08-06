package wtf.oraculus.client.renderer.shader;

import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import org.slf4j.Logger;
import wtf.oraculus.client.edition.EditionBuildInfo;
import wtf.oraculus.client.feature.module.impl.visual.overlay.LiquidGlassV2Settings;
import wtf.oraculus.client.renderer.NVGRenderer;
import wtf.oraculus.utility.render.GLUtility;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static org.lwjgl.opengl.GL33C.*;
import static wtf.oraculus.client.Constants.mc;

public final class LiquidGlassV2Renderer {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String VERTEX_SHADER = "/assets/oraculus/shaders/liquid_glass_v2.vert";
    private static final String FRAGMENT_SHADER = "/assets/oraculus/shaders/liquid_glass_v2.frag";

    private static int program;
    private static int vertexArray;
    private static int vertexBuffer;
    private static boolean initializationFailed;

    private LiquidGlassV2Renderer() {
    }

    public static boolean draw(
            final float x,
            final float y,
            final float width,
            final float height,
            final float radius,
            final LiquidGlassV2Settings settings
    ) {
        return draw(x, y, width, height, radius, settings, 1);
    }

    public static boolean draw(
            final float x,
            final float y,
            final float width,
            final float height,
            final float radius,
            final LiquidGlassV2Settings settings,
            final float opacity
    ) {
        return drawVarying(
                x, y, width, height,
                radius, radius, radius, radius,
                1, 1, 1, 1,
                settings, opacity
        );
    }

    public static boolean drawVarying(
            final float x,
            final float y,
            final float width,
            final float height,
            final float radiusTopLeft,
            final float radiusTopRight,
            final float radiusBottomRight,
            final float radiusBottomLeft,
            final float edgeTop,
            final float edgeRight,
            final float edgeBottom,
            final float edgeLeft,
            final LiquidGlassV2Settings settings,
            final float opacity
    ) {
        if (EditionBuildInfo.isFree()) {
            return false;
        }
        final Framebuffer source = ShaderFramebuffer.getLiquidGlassSourceFramebuffer();
        if (initializationFailed || settings == null || source == null
                || source.getColorAttachment() == null || width <= 0 || height <= 0 || opacity <= 0) {
            return false;
        }

        NVGRenderer.endFrame(true);
        try {
            if (!ensureInitialized()) {
                return false;
            }
            render(
                    source, x, y, width, height,
                    radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
                    edgeTop, edgeRight, edgeBottom, edgeLeft,
                    settings, opacity
            );
            return true;
        } catch (RuntimeException exception) {
            initializationFailed = true;
            LOGGER.error("Disabling LiquidGlass V2 after a rendering failure", exception);
            return false;
        } finally {
            NVGRenderer.beginFrame();
        }
    }

    private static void render(
            final Framebuffer source,
            final float x,
            final float y,
            final float width,
            final float height,
            final float radiusTopLeft,
            final float radiusTopRight,
            final float radiusBottomRight,
            final float radiusBottomLeft,
            final float edgeTop,
            final float edgeRight,
            final float edgeBottom,
            final float edgeLeft,
            final LiquidGlassV2Settings settings,
            final float opacity
    ) {
        RenderSystem.assertOnRenderThread();

        final Framebuffer target = mc.getFramebuffer();
        final float scale = (float) mc.getWindow().getScaleFactor();
        final float maximumRadius = Math.min(width, height) * scale * 0.5F;
        final int texture = Integer.parseInt(source.getColorAttachment().getLabel());

        GLUtility.setup();
        GLUtility.push();
        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                        () -> "oraculus/liquid_glass_v2",
                        target.getColorAttachmentView(),
                        OptionalInt.empty(),
                        target.useDepthAttachment ? target.getDepthAttachmentView() : null,
                        OptionalDouble.empty()
                )) {
            renderPass.setPipeline(RenderPipelines.GUI);

            glViewport(0, 0, target.textureWidth, target.textureHeight);
            glDisable(GL_DEPTH_TEST);
            glDisable(GL_CULL_FACE);
            glDisable(GL_SCISSOR_TEST);
            glEnable(GL_BLEND);
            glBlendEquationSeparate(GL_FUNC_ADD, GL_FUNC_ADD);
            glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

            glUseProgram(program);
            glUniform2f(glGetUniformLocation(program, "uScreenSize"), target.textureWidth, target.textureHeight);
            glUniform4f(glGetUniformLocation(program, "uRect"), x * scale, y * scale, width * scale, height * scale);
            glUniform4f(
                    glGetUniformLocation(program, "uRadii"),
                    Math.min(radiusTopLeft * scale, maximumRadius),
                    Math.min(radiusTopRight * scale, maximumRadius),
                    Math.min(radiusBottomRight * scale, maximumRadius),
                    Math.min(radiusBottomLeft * scale, maximumRadius)
            );
            glUniform4f(
                    glGetUniformLocation(program, "uEdgeMask"),
                    edgeTop, edgeRight, edgeBottom, edgeLeft
            );
            glUniform1f(glGetUniformLocation(program, "uBlurRadius"), settings.getBlurRadius());
            glUniform1f(glGetUniformLocation(program, "uNoise"), settings.getNoise());
            glUniform1f(glGetUniformLocation(program, "uRefractionPower"), settings.getRefraction());
            glUniform1f(glGetUniformLocation(program, "uRefractionWidth"), settings.getRefractionWidth());
            glUniform1f(glGetUniformLocation(program, "uDispersion"), settings.getDispersion());
            glUniform1f(glGetUniformLocation(program, "uEdgeGlow"), settings.getEdgeGlow());
            glUniform1f(glGetUniformLocation(program, "uEdgeWidth"), settings.getEdgeWidth());
            glUniform1f(glGetUniformLocation(program, "uBrightness"), settings.getBrightness());
            glUniform1f(glGetUniformLocation(program, "uOpacity"), Math.min(1, opacity));

            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, texture);
            glUniform1i(glGetUniformLocation(program, "uBlurTex"), 0);

            glBindVertexArray(vertexArray);
            glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        } finally {
            GLUtility.pop();
        }
    }

    private static boolean ensureInitialized() {
        if (program != 0) {
            return true;
        }

        RenderSystem.assertOnRenderThread();
        int vertexShader = 0;
        int fragmentShader = 0;
        GLUtility.setup();
        GLUtility.push();
        try {
            vertexShader = compileShader(GL_VERTEX_SHADER, VERTEX_SHADER);
            fragmentShader = compileShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER);

            program = glCreateProgram();
            glAttachShader(program, vertexShader);
            glAttachShader(program, fragmentShader);
            glLinkProgram(program);
            if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
                throw new IllegalStateException("LiquidGlass V2 program link failed: " + glGetProgramInfoLog(program));
            }

            vertexArray = glGenVertexArrays();
            vertexBuffer = glGenBuffers();
            glBindVertexArray(vertexArray);
            glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
            glBufferData(GL_ARRAY_BUFFER, new float[]{
                    0, 0,
                    1, 0,
                    0, 1,
                    1, 1
            }, GL_STATIC_DRAW);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(0, 2, GL_FLOAT, false, Float.BYTES * 2, 0L);
            glBindVertexArray(0);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            return true;
        } catch (IOException | RuntimeException exception) {
            initializationFailed = true;
            releaseObjects();
            LOGGER.error("Unable to initialize LiquidGlass V2", exception);
            return false;
        } finally {
            if (vertexShader != 0) {
                glDeleteShader(vertexShader);
            }
            if (fragmentShader != 0) {
                glDeleteShader(fragmentShader);
            }
            GLUtility.pop();
        }
    }

    private static int compileShader(final int type, final String path) throws IOException {
        final int shader = glCreateShader(type);
        glShaderSource(shader, readResource(path));
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            final String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("LiquidGlass V2 shader compilation failed for " + path + ": " + log);
        }
        return shader;
    }

    private static String readResource(final String path) throws IOException {
        try (InputStream input = LiquidGlassV2Renderer.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing shader resource " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void releaseObjects() {
        if (vertexBuffer != 0) {
            glDeleteBuffers(vertexBuffer);
            vertexBuffer = 0;
        }
        if (vertexArray != 0) {
            glDeleteVertexArrays(vertexArray);
            vertexArray = 0;
        }
        if (program != 0) {
            glDeleteProgram(program);
            program = 0;
        }
    }
}

package wtf.oraculus.utility.render;

import org.lwjgl.opengl.GL;

import static com.mojang.blaze3d.opengl.GlConst.GL_TEXTURE0;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER_BINDING;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL30.GL_MINOR_VERSION;
import static org.lwjgl.opengl.GL31.GL_PRIMITIVE_RESTART;
import static org.lwjgl.opengl.GL33.GL_SAMPLER_BINDING;
import static org.lwjgl.opengl.GL33.glBindSampler;

public final class GLUtility {

    private static final int[] lastActiveTexture = new int[1];
    private static final int[] lastProgram = new int[1];
    private static final int[] lastTexture = new int[1];
    private static final int[] lastSampler = new int[1];
    private static final int[] lastArrayBuffer = new int[1];
    private static final int[] lastVertexArrayObject = new int[1];
    private static final int[] lastPolygonMode = new int[2];
    private static final int[] lastViewport = new int[4];
    private static final int[] lastScissorBox = new int[4];
    private static final int[] lastBlendSrcRgb = new int[1];
    private static final int[] lastBlendDstRgb = new int[1];
    private static final int[] lastBlendSrcAlpha = new int[1];
    private static final int[] lastBlendDstAlpha = new int[1];
    private static final int[] lastBlendEquationRgb = new int[1];
    private static final int[] lastBlendEquationAlpha = new int[1];

    private static boolean lastEnableBlend;
    private static boolean lastEnableCullFace;
    private static boolean lastEnableDepthTest;
    private static boolean lastEnableStencilTest;
    private static boolean lastEnableScissorTest;
    private static boolean lastEnablePrimitiveRestart;

    private static boolean lastDepthMask;

    private static int glVersion = -1;

    private GLUtility() {
    }

    public static void setup() {
        final int[] major = new int[1];
        final int[] minor = new int[1];
        glGetIntegerv(GL_MAJOR_VERSION, major);
        glGetIntegerv(GL_MINOR_VERSION, minor);

        glVersion = major[0] * 100 + minor[0] * 10;
    }

    public static void push() {
        if (glVersion == -1) {
            throw new IllegalStateException("GlStateUtility.setup(glVersion) must be called before push/pop!");
        }

        glGetIntegerv(GL_ACTIVE_TEXTURE, lastActiveTexture);
        glActiveTexture(GL_TEXTURE0);

        glGetIntegerv(GL_CURRENT_PROGRAM, lastProgram);
        glGetIntegerv(GL_TEXTURE_BINDING_2D, lastTexture);

        if (glVersion >= 330 || GL.getCapabilities().GL_ARB_sampler_objects) {
            glGetIntegerv(GL_SAMPLER_BINDING, lastSampler);
        }

        glGetIntegerv(GL_ARRAY_BUFFER_BINDING, lastArrayBuffer);
        glGetIntegerv(GL_VERTEX_ARRAY_BINDING, lastVertexArrayObject);

        if (glVersion >= 200) {
            glGetIntegerv(GL_POLYGON_MODE, lastPolygonMode);
        }

        glGetIntegerv(GL_VIEWPORT, lastViewport);
        glGetIntegerv(GL_SCISSOR_BOX, lastScissorBox);

        glGetIntegerv(GL_BLEND_SRC_RGB, lastBlendSrcRgb);
        glGetIntegerv(GL_BLEND_DST_RGB, lastBlendDstRgb);
        glGetIntegerv(GL_BLEND_SRC_ALPHA, lastBlendSrcAlpha);
        glGetIntegerv(GL_BLEND_DST_ALPHA, lastBlendDstAlpha);
        glGetIntegerv(GL_BLEND_EQUATION_RGB, lastBlendEquationRgb);
        glGetIntegerv(GL_BLEND_EQUATION_ALPHA, lastBlendEquationAlpha);

        lastEnableBlend = glIsEnabled(GL_BLEND);
        lastEnableCullFace = glIsEnabled(GL_CULL_FACE);
        lastEnableDepthTest = glIsEnabled(GL_DEPTH_TEST);
        lastEnableStencilTest = glIsEnabled(GL_STENCIL_TEST);
        lastEnableScissorTest = glIsEnabled(GL_SCISSOR_TEST);

        if (glVersion >= 310) {
            lastEnablePrimitiveRestart = glIsEnabled(GL_PRIMITIVE_RESTART);
        }

        lastDepthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
    }

    public static void pop() {
        if (glVersion == -1) {
            throw new IllegalStateException("GlStateUtility.setup(glVersion) must be called before push/pop!");
        }

        glUseProgram(lastProgram[0]);
        glBindTexture(GL_TEXTURE_2D, lastTexture[0]);

        if (glVersion >= 330 || GL.getCapabilities().GL_ARB_sampler_objects) {
            glBindSampler(0, lastSampler[0]);
        }

        glActiveTexture(lastActiveTexture[0]);
        glBindVertexArray(lastVertexArrayObject[0]);
        glBindBuffer(GL_ARRAY_BUFFER, lastArrayBuffer[0]);

        glBlendEquationSeparate(lastBlendEquationRgb[0], lastBlendEquationAlpha[0]);
        glBlendFuncSeparate(lastBlendSrcRgb[0], lastBlendDstRgb[0], lastBlendSrcAlpha[0], lastBlendDstAlpha[0]);

        setGlState(GL_BLEND, lastEnableBlend);
        setGlState(GL_CULL_FACE, lastEnableCullFace);
        setGlState(GL_DEPTH_TEST, lastEnableDepthTest);
        setGlState(GL_STENCIL_TEST, lastEnableStencilTest);
        setGlState(GL_SCISSOR_TEST, lastEnableScissorTest);

        if (glVersion >= 310) {
            setGlState(GL_PRIMITIVE_RESTART, lastEnablePrimitiveRestart);
        }

        if (glVersion >= 200) {
            glPolygonMode(GL_FRONT_AND_BACK, lastPolygonMode[0]);
        }

        glViewport(lastViewport[0], lastViewport[1], lastViewport[2], lastViewport[3]);
        glScissor(lastScissorBox[0], lastScissorBox[1], lastScissorBox[2], lastScissorBox[3]);

        glDepthMask(lastDepthMask);
    }

    private static void setGlState(final int capability, final boolean enabled) {
        if (enabled) {
            glEnable(capability);
        } else {
            glDisable(capability);
        }
    }
}

package wtf.oraculus.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.util.Handle;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.oraculus.client.renderer.shader.ShaderFramebuffer;
import wtf.oraculus.client.renderer.motionblur.MotionBlurRenderer;

import java.util.Map;

@Mixin(PostEffectPass.class)
@Debug(export = true)
public final class PostEffectPassMixin {

    @Shadow @Final private String id;

    @Inject(method = "method_67884", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;setUniform(Ljava/lang/String;Lcom/mojang/blaze3d/buffers/GpuBuffer;)V", ordinal = 0))
    private void hookLambda(Handle handle, GpuBufferSlice gpuBufferSlice, Map map, CallbackInfo ci, @Local RenderPass renderPass) {
        if (ShaderFramebuffer.CUSTOM_UNIFORM.isUsed()) {
            renderPass.setUniform("Globals", ShaderFramebuffer.CUSTOM_UNIFORM.getBuffer());
        }
        if (MotionBlurRenderer.isRenderingMotionBlurPass(this.id)) {
            renderPass.setUniform("MotionBlurConfig", MotionBlurRenderer.getConfigBuffer());
        }
    }
}

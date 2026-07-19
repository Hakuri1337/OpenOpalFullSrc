#version 330

uniform sampler2D InSampler;
uniform sampler2D PreviousSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform MotionBlurConfig {
    float BlendFactor;
    float ResetHistory;
    vec2 Padding;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 current = texture(InSampler, texCoord);
    vec4 history = texture(PreviousSampler, texCoord);
    vec4 blended = mix(history, current, clamp(BlendFactor, 0.1, 1.0));
    fragColor = mix(blended, current, step(0.5, ResetHistory));
}

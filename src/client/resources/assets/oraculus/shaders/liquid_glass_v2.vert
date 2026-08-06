#version 330 core

layout (location = 0) in vec2 aLocalPosition;

uniform vec2 uScreenSize;
uniform vec4 uRect;

out vec2 vLocalUV;
out vec2 vMidPointNDC;
out vec2 vLocalOffsetNDC;

vec2 toNdc(vec2 pixel) {
    return vec2(
        pixel.x / uScreenSize.x * 2.0 - 1.0,
        1.0 - pixel.y / uScreenSize.y * 2.0
    );
}

void main() {
    vec2 pixel = uRect.xy + aLocalPosition * uRect.zw;
    vec2 positionNDC = toNdc(pixel);
    vLocalUV = aLocalPosition;
    vMidPointNDC = toNdc(uRect.xy + uRect.zw * 0.5);
    vLocalOffsetNDC = positionNDC - vMidPointNDC;
    gl_Position = vec4(positionNDC, 0.0, 1.0);
}

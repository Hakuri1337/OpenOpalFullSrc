#version 330 core

uniform sampler2D uBlurTex;
uniform vec2 uScreenSize;
uniform vec4 uRect;
uniform vec4 uRadii;
uniform vec4 uEdgeMask;
uniform float uBlurRadius;
uniform float uNoise;
uniform float uRefractionPower;
uniform float uRefractionWidth;
uniform float uDispersion;
uniform float uEdgeGlow;
uniform float uEdgeWidth;
uniform float uBrightness;
uniform float uOpacity;

in vec2 vLocalUV;
in vec2 vMidPointNDC;
in vec2 vLocalOffsetNDC;

out vec4 FragColor;

const float M_E = 2.718281828459045;

float rand(vec2 coordinate) {
    return fract(sin(dot(coordinate, vec2(12.9898, 78.233))) * 43758.5453);
}

float refractionCurve(float distanceToEdge) {
    return 1.0 - 2.3 * pow(5.2 * M_E, -6.9 * distanceToEdge - 0.7);
}

float cornerRadius(vec2 point) {
    if (point.y < 0.0) {
        return point.x < 0.0 ? uRadii.x : uRadii.y;
    }
    return point.x < 0.0 ? uRadii.w : uRadii.z;
}

float roundedRectDistance(vec2 point, vec2 halfSize) {
    float radius = cornerRadius(point);
    vec2 q = abs(point) - halfSize + vec2(radius);
    return length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
}

float selectedInteriorDistance(vec2 localPixel, float shapeDistance) {
    if (min(min(uEdgeMask.x, uEdgeMask.y), min(uEdgeMask.z, uEdgeMask.w)) > 0.999) {
        return -shapeDistance;
    }

    vec4 distances = vec4(
        localPixel.y,
        uRect.z - localPixel.x,
        uRect.w - localPixel.y,
        localPixel.x
    );
    vec4 selected = mix(vec4(1000000.0), distances, clamp(uEdgeMask, 0.0, 1.0));
    return min(min(selected.x, selected.y), min(selected.z, selected.w));
}

vec4 backgroundSample(vec2 uv) {
    vec2 sampleOffset = vec2(uBlurRadius) / uScreenSize;
    if (uBlurRadius < 0.01) {
        return texture(uBlurTex, clamp(uv, vec2(0.0), vec2(1.0)));
    }

    vec4 color = texture(uBlurTex, clamp(uv, vec2(0.0), vec2(1.0))) * 4.0;
    color += texture(uBlurTex, clamp(uv + vec2(sampleOffset.x, 0.0), vec2(0.0), vec2(1.0))) * 2.0;
    color += texture(uBlurTex, clamp(uv - vec2(sampleOffset.x, 0.0), vec2(0.0), vec2(1.0))) * 2.0;
    color += texture(uBlurTex, clamp(uv + vec2(0.0, sampleOffset.y), vec2(0.0), vec2(1.0))) * 2.0;
    color += texture(uBlurTex, clamp(uv - vec2(0.0, sampleOffset.y), vec2(0.0), vec2(1.0))) * 2.0;
    color += texture(uBlurTex, clamp(uv + sampleOffset, vec2(0.0), vec2(1.0)));
    color += texture(uBlurTex, clamp(uv - sampleOffset, vec2(0.0), vec2(1.0)));
    color += texture(uBlurTex, clamp(uv + vec2(sampleOffset.x, -sampleOffset.y), vec2(0.0), vec2(1.0)));
    color += texture(uBlurTex, clamp(uv + vec2(-sampleOffset.x, sampleOffset.y), vec2(0.0), vec2(1.0)));
    return color / 16.0;
}

float directionalGlow(vec2 uv) {
    vec2 glowUV = uv * 2.0 - 1.0;
    return sin(atan(glowUV.y, glowUV.x) - 0.5);
}

bool outOfBounds(vec2 uv) {
    return max(uv.x, uv.y) > 1.0 || min(uv.x, uv.y) < 0.0;
}

void main() {
    vec2 localPixel = vLocalUV * uRect.zw;
    vec2 halfSize = uRect.zw * 0.5;
    float shapeDistance = roundedRectDistance(localPixel - halfSize, halfSize);
    float antialiasWidth = max(fwidth(shapeDistance), 0.75);
    float edge = 1.0 - smoothstep(-antialiasWidth, antialiasWidth, shapeDistance);
    if (edge <= 0.0) {
        discard;
    }

    float interiorDistance = clamp(
        selectedInteriorDistance(localPixel, shapeDistance) / max(min(halfSize.x, halfSize.y), 1.0),
        0.0,
        1.0
    );
    float refractionDistance = clamp(interiorDistance / uRefractionWidth, 0.0, 1.0);
    float refraction = pow(refractionCurve(refractionDistance), uRefractionPower);
    vec2 sampleUV = (vMidPointNDC + vLocalOffsetNDC * refraction) * 0.5 + vec2(0.5);
    if (outOfBounds(sampleUV)) {
        discard;
    }

    float rim = 1.0 - smoothstep(0.0, uEdgeWidth, interiorDistance);
    vec2 chromaDirection = normalize(vLocalOffsetNDC + vec2(0.000001));
    vec2 chromaOffset = chromaDirection * rim * uDispersion;
    vec4 color = vec4(
        backgroundSample(sampleUV + chromaOffset).r,
        backgroundSample(sampleUV).g,
        backgroundSample(sampleUV - chromaOffset).b,
        1.0
    );
    float grain = (rand(gl_FragCoord.xy * 0.001) - 0.5) * uNoise;
    color.rgb += vec3(grain);

    float directionalLight = directionalGlow(vLocalUV) * 0.5 + 0.5;
    float specular = pow(rim, 1.35) * (0.35 + directionalLight * 0.65);
    float lowerEdge = rim * smoothstep(0.4, 1.0, vLocalUV.y);
    color.rgb *= uBrightness * (1.0 - lowerEdge * 0.12);
    color.rgb += vec3(0.82, 0.93, 1.0) * specular * uEdgeGlow;
    color.rgb *= edge;

    FragColor = vec4(color.rgb, edge * uOpacity);
}

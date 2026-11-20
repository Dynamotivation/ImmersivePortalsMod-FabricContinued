#version 330

#moj_import <minecraft:dynamictransforms.glsl>

// Opaque placeholder texture of a portal whose destination is still loading. ColorModulator.a fades it out.
uniform sampler2D Sampler0;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    fragColor = vec4(texture(Sampler0, texCoord0).rgb, 1.0) * ColorModulator;
}

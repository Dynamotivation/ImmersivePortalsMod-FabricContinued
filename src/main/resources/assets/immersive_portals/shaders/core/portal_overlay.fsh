#version 330

#moj_import <minecraft:dynamictransforms.glsl>

// Translucent overlay texture (the nether portal swirl) drawn over a portal pane. ColorModulator.a is the opacity.
uniform sampler2D Sampler0;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a < 0.01) {
        discard;
    }
    fragColor = color * ColorModulator;
}

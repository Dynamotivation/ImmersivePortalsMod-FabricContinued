#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

// Opaque placeholder texture of a portal whose destination is still loading. ColorModulator.a fades it out.
// Sampler0 is the vanilla nether portal strip (16 x 512: 32 animation frames, 2 ticks each), animated here by time.
uniform sampler2D Sampler0;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    float frames = 32.0;
    float frame = floor(mod(GameTime * 1200.0 / 0.1, frames));
    vec2 uv = vec2(fract(texCoord0.x), (frame + fract(texCoord0.y) * 0.999) / frames);
    fragColor = vec4(texture(Sampler0, uv).rgb, 1.0) * ColorModulator;
}

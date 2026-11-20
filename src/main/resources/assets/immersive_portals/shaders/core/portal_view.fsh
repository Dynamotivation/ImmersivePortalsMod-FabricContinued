#version 330

#moj_import <minecraft:dynamictransforms.glsl>

// Draws the pre-rendered view of the portal destination.
// The view was rendered with the same resolution and the same screen as the main view,
// so it is sampled by the screen position of the fragment.
uniform sampler2D Sampler0;

out vec4 fragColor;

void main() {
    vec2 uv = gl_FragCoord.xy / vec2(textureSize(Sampler0, 0));
    // mirrored views are rendered with flipped x (winding), flip back
    if (ColorModulator.r < 0.0) {
        uv.x = 1.0 - uv.x;
    }
    fragColor = vec4(texture(Sampler0, uv).rgb, 1.0);
}

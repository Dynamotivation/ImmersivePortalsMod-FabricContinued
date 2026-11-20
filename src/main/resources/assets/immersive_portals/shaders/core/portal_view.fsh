#version 330

// Draws the pre-rendered view of the portal destination.
// The view was rendered with the same resolution and the same screen as the main view,
// so it is sampled by the screen position of the fragment.
uniform sampler2D Sampler0;

out vec4 fragColor;

void main() {
    vec2 uv = gl_FragCoord.xy / vec2(textureSize(Sampler0, 0));
    fragColor = vec4(texture(Sampler0, uv).rgb, 1.0);
}

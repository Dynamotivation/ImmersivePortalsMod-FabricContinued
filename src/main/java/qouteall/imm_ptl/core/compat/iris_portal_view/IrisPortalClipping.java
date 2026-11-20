/*
 * Adapted from the Immersive Portals port for Minecraft 26.3 (other-ports/ImmersivePortalsMod,
 * compat/iris_portal_view/IrisPortalClipping.java), Copyright 2020 qouteall and contributors,
 * licensed under the Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0).
 * Changes: the notifier never fires (see NOTIFIER), runtime flags proving that the optional mixins work,
 * sky programs are not clipped (decided by MixinIrisShaderCreator).
 */
package qouteall.imm_ptl.core.compat.iris_portal_view;

import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Clips portal views inside the G-buffer programs of an Iris shader pack.
 * <p>
 * Without a shader pack the destination is clipped by an oblique near plane. Shader packs reconstruct distances
 * from depth with the standard perspective formula, so with a pack the view keeps the plain projection, and the
 * vertex shader of every G-buffer program (not shadow, composite, final or sky programs) computes the signed
 * distance to the clip plane from its final {@code gl_Position}; the fragment shader discards negative distances.
 * The coefficients ({@value #UNIFORM}) are {@link #NO_CLIPPING} outside of portal views.
 * <p>
 * Only loaded when Iris is present.
 */
public final class IrisPortalClipping {
    private static final Logger LOGGER = LoggerFactory.getLogger(IrisPortalClipping.class);

    public static final String UNIFORM = "iportal_ClipCoefficients";
    private static final String VARYING = "iportal_ClipDistance";
    private static final String INNER_MAIN = "iportal_originalMain";
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(void)?\\s*\\)");

    /** Keeps everything (distance 1 everywhere). */
    public static final Vector4fc NO_CLIPPING = new Vector4f(0, 0, 0, 1);

    private static final Vector4f coefficients = new Vector4f(NO_CLIPPING);

    /**
     * Never fires. Iris re-reads dynamic uniforms whenever a program is applied (ProgramUniforms.update), which
     * happens for every render pass while a pack is in use; the coefficients only change between whole level
     * renders. Firing the listener outside a draw would call glUniform on whatever GL program is bound at that
     * moment (not necessarily the program that registered it) and desynchronize the uniform's cached value.
     */
    public static final ValueUpdateNotifier NOTIFIER = runnable -> {};

    // proofs that the optional mixins work (set when Iris builds programs)
    private static volatile boolean uniformRegistered = false;
    private static volatile boolean shaderCreatorHooked = false;
    private static int patchedPrograms = 0;

    private IrisPortalClipping() {}

    public static Vector4f getCoefficients() {
        return new Vector4f(coefficients);
    }

    public static void setCoefficients(@Nullable Vector4fc value) {
        coefficients.set(value == null ? NO_CLIPPING : value);
    }

    public static void onUniformRegistered() {
        uniformRegistered = true;
    }

    public static void onShaderCreatorHooked() {
        shaderCreatorHooked = true;
    }

    public static @Nullable String getUnsupportedReason() {
        if (!shaderCreatorHooked) {
            return "the Iris ShaderCreator mixin did not run (not applied, or no shader program built yet)";
        }
        if (!uniformRegistered) {
            return "the Iris CommonUniforms mixin did not run (not applied, or no shader program built yet)";
        }
        return null;
    }

    /**
     * Adds the clipping to the sources of a G-buffer program (after Iris' own transformation).
     *
     * @return the patched sources, or the input if the program cannot be patched
     */
    public static Map<PatchShaderType, String> patch(String name, Map<PatchShaderType, String> sources) {
        if (sources == null) {
            return null;
        }
        String vertex = sources.get(PatchShaderType.VERTEX);
        String fragment = sources.get(PatchShaderType.FRAGMENT);
        if (vertex == null || fragment == null) {
            return sources;
        }
        if (sources.get(PatchShaderType.GEOMETRY) != null
            || sources.get(PatchShaderType.TESS_CONTROL) != null
            || sources.get(PatchShaderType.TESS_EVAL) != null) {
            // the distance would have to be passed through the other stages
            LOGGER.warn("Not clipping portal views in the shader program {}: it has geometry or tessellation stages", name);
            return sources;
        }
        if (vertex.contains(INNER_MAIN)) {
            return sources;
        }

        String patchedVertex = renameMain(vertex);
        String patchedFragment = renameMain(fragment);
        if (patchedVertex == null || patchedFragment == null) {
            LOGGER.warn("Not clipping portal views in the shader program {}: no unique main function", name);
            return sources;
        }

        patchedVertex += "\nuniform vec4 " + UNIFORM + ";\n"
            + "out float " + VARYING + ";\n"
            + "void main() {\n"
            + "    " + INNER_MAIN + "();\n"
            + "    " + VARYING + " = dot(" + UNIFORM + ".xyz, gl_Position.xyw) + " + UNIFORM + ".w;\n"
            + "}\n";
        patchedFragment += "\nin float " + VARYING + ";\n"
            + "void main() {\n"
            + "    if (" + VARYING + " < 0.0) {\n"
            + "        discard;\n"
            + "    }\n"
            + "    " + INNER_MAIN + "();\n"
            + "}\n";

        // never mutate Iris' map (TransformPatcher caches its results)
        Map<PatchShaderType, String> result = new EnumMap<>(PatchShaderType.class);
        result.putAll(sources);
        result.put(PatchShaderType.VERTEX, patchedVertex);
        result.put(PatchShaderType.FRAGMENT, patchedFragment);
        patchedPrograms++;
        return result;
    }

    private static @Nullable String renameMain(String source) {
        Matcher matcher = MAIN.matcher(source);
        if (!matcher.find()) {
            return null;
        }
        int start = matcher.start(), end = matcher.end();
        if (matcher.find()) {
            return null;
        }
        return source.substring(0, start) + "void " + INNER_MAIN + "()" + source.substring(end);
    }
}

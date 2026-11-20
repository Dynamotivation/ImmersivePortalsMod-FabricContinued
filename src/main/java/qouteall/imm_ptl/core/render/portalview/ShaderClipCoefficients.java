/*
 * Adapted from the Immersive Portals port for Minecraft 26.3 (other-ports/ImmersivePortalsMod,
 * portal_view/ShaderClipPlane.java), Copyright 2020 qouteall and contributors, licensed under the Apache License,
 * Version 2.0. Changes: solved for a general projection (bobbing/spin/mirror flip), not only a plain perspective.
 */
package qouteall.imm_ptl.core.render.portalview;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

/**
 * Clipping against a view-space plane inside shader pack programs (no Iris types: usable without Iris).
 * <p>
 * The patched vertex shader only knows the clip-space position, whose depth component shader packs and Iris may
 * rewrite. So the signed plane distance is expressed with x, y and w of the clip position only:
 * <pre>
 *     distance = k.x * x_clip + k.y * y_clip + k.z * w_clip + k.w
 * </pre>
 * With rows r0, r1, r3 of the projection P (x_clip = r0 . v, ...), k solves
 * {@code k.x r0 + k.y r1 + k.z r3 + k.w e4 = plane}. The distance is affine in the clip position, so it
 * interpolates exactly across a triangle. For a plain perspective projection this is the same as the 26.3 fork's
 * ShaderClipPlane formula.
 */
public final class ShaderClipCoefficients {
    private ShaderClipCoefficients() {}

    /**
     * @param projection the projection bound for the view (applied after the model-view of the view)
     * @param plane      view-space plane (a, b, c, d); points with a*x + b*y + c*z + d >= 0 are kept
     * @return the coefficients k, or null if they cannot be determined (degenerate projection)
     */
    public static @Nullable Vector4f compute(Matrix4fc projection, Vector4f plane) {
        // JOML: mXY = column X, row Y. Columns of B: rows 0, 1 and 3 of P, and e4.
        Matrix4f b = new Matrix4f(
            projection.m00(), projection.m10(), projection.m20(), projection.m30(),
            projection.m01(), projection.m11(), projection.m21(), projection.m31(),
            projection.m03(), projection.m13(), projection.m23(), projection.m33(),
            0, 0, 0, 1
        );
        float determinant = b.determinant();
        if (!Float.isFinite(determinant) || Math.abs(determinant) < 1e-12f) {
            return null;
        }
        Vector4f k = b.invert().transform(new Vector4f(plane));
        if (!Float.isFinite(k.x) || !Float.isFinite(k.y) || !Float.isFinite(k.z) || !Float.isFinite(k.w)) {
            return null;
        }
        return k;
    }
}

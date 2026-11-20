package qouteall.imm_ptl.core.render.portalview;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

/**
 * Oblique near-plane clipping (Eric Lengyel), adapted for the reversed-Z projection matrices of 26.x
 * (vanilla builds the projection with swapped near/far, so the physical near plane is at z_ndc = 1).
 * It makes the near plane of the projection coincide with an arbitrary plane, so everything between the
 * (virtual) camera and the portal plane is clipped without needing any shader support.
 */
public class ObliqueProjection {
    /**
     * @param projection the original reversed-Z perspective projection
     * @param a          eye-space clip plane a*x+b*y+c*z+d >= 0 is the kept side
     * @param zZeroToOne whether the clip space z range is [0,1] (otherwise [-1,1])
     */
    public static Matrix4f apply(Matrix4fc projection, float a, float b, float c, float d, boolean zZeroToOne) {
        Matrix4f result = new Matrix4f(projection);
        float zFarNdc = zZeroToOne ? 0.0f : -1.0f;
        
        Matrix4f inverse = projection.invert(new Matrix4f());
        Vector4f q = new Vector4f(Math.signum(a), Math.signum(b), zFarNdc, 1.0f);
        inverse.transform(q);
        float qx = q.x / q.w;
        float qy = q.y / q.w;
        float qz = q.z / q.w;
        
        float cDotQ = a * qx + b * qy + c * qz + d;
        float wClip = projection.m03() * qx + projection.m13() * qy + projection.m23() * qz + projection.m33();
        
        if (Math.abs(cDotQ) < 1e-8f) {
            return result;
        }
        
        float s = (1.0f - zFarNdc) * wClip / cDotQ;
        
        // new z row = w row - s * plane
        result.m02(projection.m03() - s * a);
        result.m12(projection.m13() - s * b);
        result.m22(projection.m23() - s * c);
        result.m32(projection.m33() - s * d);
        return result;
    }
}

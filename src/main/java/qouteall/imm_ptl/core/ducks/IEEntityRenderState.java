package qouteall.imm_ptl.core.ducks;

import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

/**
 * Extra data on an EntityRenderState that is a cross-portal projection (see CrossPortalEntityRenderer):
 * the portal's rotation and scale, applied around the entity anchor when the state is submitted.
 */
public interface IEEntityRenderState {
    void ip_setCrossPortalTransform(@Nullable Quaternionf rotation, float scale);

    @Nullable
    Quaternionf ip_getCrossPortalRotation();

    float ip_getCrossPortalScale();

    boolean ip_hasCrossPortalTransform();
}

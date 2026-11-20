package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import qouteall.imm_ptl.core.ducks.IEEntityRenderState;

@Mixin(EntityRenderState.class)
public class MixinEntityRenderState_CrossPortal implements IEEntityRenderState {
    @Unique
    private boolean ip_crossPortalTransform = false;
    @Unique
    private @Nullable Quaternionf ip_crossPortalRotation = null;
    @Unique
    private float ip_crossPortalScale = 1.0f;

    @Override
    public void ip_setCrossPortalTransform(@Nullable Quaternionf rotation, float scale) {
        ip_crossPortalTransform = true;
        ip_crossPortalRotation = rotation;
        ip_crossPortalScale = scale;
    }

    @Override
    public @Nullable Quaternionf ip_getCrossPortalRotation() {
        return ip_crossPortalRotation;
    }

    @Override
    public float ip_getCrossPortalScale() {
        return ip_crossPortalScale;
    }

    @Override
    public boolean ip_hasCrossPortalTransform() {
        return ip_crossPortalTransform;
    }
}

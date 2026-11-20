package qouteall.imm_ptl.core.mixin.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.render.CrossPortalEntityRenderer;

@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderDispatcher_CrossPortal {
    // right after the pose is moved to the entity anchor: rotation/scale of cross-portal projections
    @Inject(
        method = "submit",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V",
            ordinal = 0,
            shift = At.Shift.AFTER
        ),
        require = 0
    )
    private void ip_onSubmitAfterTranslate(
        EntityRenderState renderState, CameraRenderState camera,
        double x, double y, double z, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
        CallbackInfo ci
    ) {
        CrossPortalEntityRenderer.applyProjectionTransform(renderState, poseStack);
    }
}

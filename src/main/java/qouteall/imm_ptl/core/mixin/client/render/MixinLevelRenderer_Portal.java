package qouteall.imm_ptl.core.mixin.client.render;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.render.portalview.PortalViewRenderer;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer_Portal implements qouteall.imm_ptl.core.ducks.IELevelRendererPortal {
    @Shadow
    @Final
    private LevelTargetBundle targets;
    
    @Shadow
    @Final
    private LevelRenderState levelRenderState;
    
    // each portal view uses its own cloud renderer (its buffers can only be used once per frame)
    @Shadow
    @Final
    @Mutable
    private CloudRenderer cloudRenderer;
    
    @Override
    public CloudRenderer ip_getCloudRenderer() {
        return cloudRenderer;
    }
    
    @Override
    public void ip_setCloudRenderer(CloudRenderer renderer) {
        cloudRenderer = renderer;
    }
    
    // switch from the normal projection (used by the sky) to the oblique clipping projection of the portal view
    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;addMainPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/util/profiling/ProfilerFiller;Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;)V"
        )
    )
    private void ip_addObliqueProjectionPass(CallbackInfo ci, @Local FrameGraphBuilder frame) {
        com.mojang.blaze3d.buffers.GpuBufferSlice slice = PortalViewRenderer.getPendingObliqueSlice();
        if (!PortalViewRenderer.isRenderingViews() || slice == null) {
            return;
        }
        FramePass pass = frame.addPass("imm_ptl_oblique_projection");
        targets.main = pass.readsAndWrites(targets.main);
        pass.executes(() -> com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(
            slice, com.mojang.blaze3d.ProjectionType.PERSPECTIVE
        ));
    }

    // draw the pre-rendered portal views right after the main pass (terrain, entities, translucent stuff)
    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;addMainPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/util/profiling/ProfilerFiller;Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;)V",
            shift = At.Shift.AFTER
        )
    )
    private void ip_addPortalViewPass(CallbackInfo ci, @Local FrameGraphBuilder frame) {
        if (!PortalViewRenderer.shouldAddViewPass()) {
            // rendering a destination without portals of its own in view
            return;
        }
        
        FramePass pass = frame.addPass("imm_ptl_portal_views");
        targets.main = pass.readsAndWrites(targets.main);
        ResourceHandle<RenderTarget> mainHandle = targets.main;
        CameraRenderState cameraState = levelRenderState.cameraRenderState;
        pass.executes(() -> PortalViewRenderer.drawViews(
            mainHandle.get(), new Matrix4f(cameraState.viewRotationMatrix), cameraState.pos
        ));
    }
}

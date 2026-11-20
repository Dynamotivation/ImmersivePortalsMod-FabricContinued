package qouteall.imm_ptl.core.mixin.client.render;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.render.portalview.PortalViewRenderer;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer_Portal {
    @Shadow
    @Final
    private LevelTargetBundle targets;
    
    @Shadow
    @Final
    private LevelRenderState levelRenderState;
    
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
        if (PortalViewRenderer.isRenderingViews()) {
            // no recursion yet: portals inside portal views are not drawn
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

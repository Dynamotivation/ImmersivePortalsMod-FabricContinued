package qouteall.imm_ptl.core.mixin.client.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.ducks.IEGameRendererPortal;
import qouteall.imm_ptl.core.render.portalview.PortalViewRenderer;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer_Portal implements IEGameRendererPortal {
    @Shadow
    @Final
    private FogRenderer fogRenderer;
    
    @Shadow
    @Final
    private CrossFrameResourcePool resourcePool;
    
    @Shadow
    @Final
    private Minecraft minecraft;
    
    @Unique
    private ProjectionMatrixBuffer ip_portalProjectionBuffer;
    
    // the destinations of portals are rendered before the main level is extracted,
    // as every extraction overwrites the shared level render state
    @Inject(method = "extract", at = @At("HEAD"))
    private void ip_onBeforeExtract(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        if (minecraft.isGameLoadFinished() && advanceGameTime && minecraft.level != null) {
            PortalViewRenderer.onBeforeExtract((GameRenderer) (Object) this, deltaTracker);
        }
    }
    
    // make the level renderer draw into the offscreen target when rendering a portal destination
    @Inject(method = "mainRenderTarget", at = @At("HEAD"), cancellable = true)
    private void ip_overrideMainRenderTarget(CallbackInfoReturnable<RenderTarget> cir) {
        RenderTarget override = PortalViewRenderer.getActiveTargetOverride();
        if (override != null) {
            cir.setReturnValue(override);
        }
    }
    
    @Override
    public FogRenderer ip_getFogRenderer() {
        return fogRenderer;
    }
    
    @Override
    public GraphicsResourceAllocator ip_getResourcePool() {
        return resourcePool;
    }
    
    @Override
    public GpuBufferSlice ip_getPortalProjectionBuffer(Matrix4f projectionMatrix) {
        if (ip_portalProjectionBuffer == null) {
            ip_portalProjectionBuffer = new ProjectionMatrixBuffer("immersive portals view");
        }
        return ip_portalProjectionBuffer.getBuffer(projectionMatrix);
    }
}

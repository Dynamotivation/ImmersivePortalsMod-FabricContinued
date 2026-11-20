package qouteall.imm_ptl.core.mixin.client.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
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
    
    @Shadow
    @Final
    private Lightmap lightmap;
    
    @Shadow
    @Final
    private LightmapRenderStateExtractor lightmapRenderStateExtractor;
    
    @Shadow
    public abstract net.minecraft.client.renderer.state.GameRenderState gameRenderState();
    
    @Shadow
    @Final
    private net.minecraft.client.renderer.GlobalSettingsUniform globalSettingsUniform;
    
    @Unique
    private final ProjectionMatrixBuffer[] ip_portalProjectionBuffers = new ProjectionMatrixBuffer[2];
    
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
    public GpuBufferSlice ip_getPortalProjectionBuffer(Matrix4f projectionMatrix, int slot) {
        if (ip_portalProjectionBuffers[slot] == null) {
            ip_portalProjectionBuffers[slot] = new ProjectionMatrixBuffer("immersive portals view " + slot);
        }
        return ip_portalProjectionBuffers[slot].getBuffer(projectionMatrix);
    }

    @Override
    public void ip_renderLightmap(Lightmap destLightmap, net.minecraft.client.renderer.state.LightmapRenderState state) {
        IELightmapRenderStateExtractor access = (IELightmapRenderStateExtractor) lightmapRenderStateExtractor;
        access.ip_setNeedsUpdate(true);
        lightmapRenderStateExtractor.extract(state, 1.0F);
        destLightmap.render(state);
        // the extraction consumed the flag, the main view must still update its own lightmap this frame
        access.ip_setNeedsUpdate(true);
    }
    
    // sample the lightmap of the dimension that is being rendered into a portal view
    @Inject(method = "lightmap", at = @At("HEAD"), cancellable = true)
    private void ip_overrideLightmap(CallbackInfoReturnable<com.mojang.blaze3d.textures.GpuTextureView> cir) {
        com.mojang.blaze3d.textures.GpuTextureView override = PortalViewRenderer.getActiveLightmapOverride();
        if (override != null) {
            cir.setReturnValue(override);
        }
    }
    
    @Override
    public void ip_updateGlobalSettingsForView(DeltaTracker deltaTracker, net.minecraft.world.phys.Vec3 cameraPos) {
        net.minecraft.client.renderer.state.GameRenderState state = gameRenderState();
        globalSettingsUniform.update(
            state.windowRenderState.width, state.windowRenderState.height,
            state.optionsRenderState.glintStrength,
            minecraft.level == null ? 0L : minecraft.level.getGameTime(),
            deltaTracker,
            state.optionsRenderState.menuBackgroundBlurriness,
            cameraPos,
            state.optionsRenderState.textureFiltering == net.minecraft.client.TextureFilteringMethod.RGSS
        );
    }
}

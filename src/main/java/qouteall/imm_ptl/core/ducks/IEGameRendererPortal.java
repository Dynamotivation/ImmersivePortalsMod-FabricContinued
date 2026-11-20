package qouteall.imm_ptl.core.ducks;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Matrix4f;

public interface IEGameRendererPortal {
    FogRenderer ip_getFogRenderer();
    
    GraphicsResourceAllocator ip_getResourcePool();
    
    GpuBufferSlice ip_getPortalProjectionBuffer(Matrix4f projectionMatrix);
    
    /**
     * Extracts the lightmap state for the currently active level/camera into {@code state} and renders it
     * into {@code lightmap} (a per-dimension lightmap used by portal views).
     * Must be called at most once per frame per Lightmap object.
     */
    void ip_renderLightmap(net.minecraft.client.renderer.Lightmap lightmap,
                           net.minecraft.client.renderer.state.LightmapRenderState state);
    
    /**
     * Writes the Globals UBO (CameraBlockPos/CameraOffset used by vanilla terrain.vsh, GameTime) for a portal view camera.
     * GameRenderer.render rewrites it for the main view later in the frame.
     */
    void ip_updateGlobalSettingsForView(net.minecraft.client.DeltaTracker deltaTracker, net.minecraft.world.phys.Vec3 cameraPos);
}

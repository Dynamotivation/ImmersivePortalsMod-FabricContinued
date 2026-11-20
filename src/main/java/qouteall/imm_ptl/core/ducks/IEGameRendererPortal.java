package qouteall.imm_ptl.core.ducks;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Matrix4f;

public interface IEGameRendererPortal {
    FogRenderer ip_getFogRenderer();
    
    GraphicsResourceAllocator ip_getResourcePool();
    
    GpuBufferSlice ip_getPortalProjectionBuffer(Matrix4f projectionMatrix);
}

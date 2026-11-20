package qouteall.imm_ptl.core.ducks;

import net.minecraft.client.renderer.CloudRenderer;

public interface IELevelRendererPortal {
    CloudRenderer ip_getCloudRenderer();
    
    void ip_setCloudRenderer(CloudRenderer renderer);
}

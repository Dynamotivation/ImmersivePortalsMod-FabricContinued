package qouteall.imm_ptl.core.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import qouteall.imm_ptl.core.portal.Portal;

// TODO(port): portal rendering is deferred to the renderer redesign.
// 26.x draws entities through the extract/submit render-state pipeline,
// and the whole portal render pass has to be rebuilt on top of it.
@Environment(EnvType.CLIENT)
public class PortalEntityRenderer extends EntityRenderer<Portal, EntityRenderState> {
    
    public PortalEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }
    
    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }
}

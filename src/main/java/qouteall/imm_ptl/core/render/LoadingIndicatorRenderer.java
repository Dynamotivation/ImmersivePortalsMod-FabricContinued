package qouteall.imm_ptl.core.render;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import qouteall.imm_ptl.core.portal.LoadingIndicatorEntity;

// TODO(port): 26.x moved entity rendering to the extract/submit render-state pipeline.
// The loading indicator text rendering is deferred until the renderer redesign.
public class LoadingIndicatorRenderer extends EntityRenderer<LoadingIndicatorEntity, EntityRenderState> {
    public LoadingIndicatorRenderer(EntityRendererProvider.Context context) {
        super(context);
    }
    
    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }
}

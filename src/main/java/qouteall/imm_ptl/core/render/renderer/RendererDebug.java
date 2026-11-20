package qouteall.imm_ptl.core.render.renderer;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.QueryManager;
import qouteall.imm_ptl.core.render.ViewAreaRenderer;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;

import java.util.List;

public class RendererDebug extends PortalRenderer {
    @Override
    public void onBeforeTranslucentRendering(Matrix4f modelView) {
        renderPortals(modelView);
    }
    
    @Override
    public void onAfterTranslucentRendering(Matrix4f modelView) {
    
    }
    
    @Override
    public void onHandRenderingEnded() {
    
    }
    
    @Override
    public void prepareRendering() {
    
    }
    
    @Override
    public void finishRendering() {
    
    }
    
    @Override
    public void renderPortalInEntityRenderer(Portal portal) {
    
    }
    
    @Override
    public boolean replaceFrameBufferClearing() {
        return false;
    }
    
    protected void doRenderPortal(Portal portal, Matrix4f modelView) {
    }
    
    private boolean testShouldRenderPortal(
        Portal portal,
        Matrix4f modelView
    ) {
        return false;
    }
    
    protected void renderPortals(Matrix4f modelView) {
        List<Portal> portalsToRender = getPortalsToRender(modelView);
    
        for (Portal portal : portalsToRender) {
            doRenderPortal(portal, modelView);
        }
    }
}

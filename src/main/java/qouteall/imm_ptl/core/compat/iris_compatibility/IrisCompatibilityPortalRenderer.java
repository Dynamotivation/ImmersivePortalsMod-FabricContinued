package qouteall.imm_ptl.core.compat.iris_compatibility;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.compat.IPPortingLibCompat;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalRenderInfo;
import qouteall.imm_ptl.core.render.MyGameRenderer;
import qouteall.imm_ptl.core.render.MyRenderHelper;
import qouteall.imm_ptl.core.render.SecondaryFrameBuffer;
import qouteall.imm_ptl.core.render.ViewAreaRenderer;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.imm_ptl.core.render.renderer.PortalRenderer;

import java.util.List;

import static org.lwjgl.opengl.GL11.GL_STENCIL_TEST;

public class IrisCompatibilityPortalRenderer extends PortalRenderer {
    
    public static final IrisCompatibilityPortalRenderer instance = new IrisCompatibilityPortalRenderer(false);
    public static final IrisCompatibilityPortalRenderer debugModeInstance =
        new IrisCompatibilityPortalRenderer(true);
    
    private SecondaryFrameBuffer deferredBuffer = new SecondaryFrameBuffer();
    
    // TODO figure out why this field existed in old versions
    private Matrix4f passingModelView = new Matrix4f();
    
    public boolean isDebugMode;
    
    public IrisCompatibilityPortalRenderer(boolean isDebugMode) {
        this.isDebugMode = isDebugMode;
    }
    
    @Override
    public boolean replaceFrameBufferClearing() {
        return false;
    }
    
    @Override
    public void onBeforeTranslucentRendering(Matrix4f modelView) {
        if (PortalRendering.isRendering()) {
            return;
        }
        
        passingModelView = modelView;
        
        GL11.glDisable(GL_STENCIL_TEST);
    }
    
    @Override
    public void onAfterTranslucentRendering(Matrix4f modelView) {
    
    }
    
    @Override
    public void finishRendering() {
        GL11.glDisable(GL_STENCIL_TEST);
    }
    
    @Override
    public void prepareRendering() {
    }
    
    protected void doRenderPortal(Portal portal, Matrix4f modelView) {
    }
    
    @Override
    public void invokeWorldRendering(
        WorldRenderInfo worldRenderInfo
    ) {
        MyGameRenderer.renderWorldNew(
            worldRenderInfo,
            Runnable::run
        );
    }
    
    @Override
    public void renderPortalInEntityRenderer(Portal portal) {
    
    }
    
    private boolean testShouldRenderPortal(Portal portal, Matrix4f modelView) {
        return false;
    }
    
    @Override
    public void onBeforeHandRendering(Matrix4f modelView) {
    }
    
    @Override
    public void onHandRenderingEnded() {
    
    }
    
    protected void renderPortals(Matrix4f modelView) {
        List<Portal> portalsToRender = getPortalsToRender(modelView);
        
        for (Portal portal : portalsToRender) {
            doRenderPortal(portal, modelView);
        }
    }
}

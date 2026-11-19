package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.joml.Matrix4f;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.q_misc_util.my_util.SignalBiArged;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static org.lwjgl.opengl.GL11.GL_BACK;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_COMPONENT;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_FRONT;
import static org.lwjgl.opengl.GL11.GL_RED;
import static org.lwjgl.opengl.GL11.glCullFace;
import static org.lwjgl.opengl.GL11.glReadPixels;

public class MyRenderHelper {
    
    public static final Minecraft client = Minecraft.getInstance();
    
    
    public static void init() {
    }
    
    // vanilla hardcodes the shader namespace to be "minecraft"
    private static ResourceProvider getResourceFactory(ResourceProvider resourceManager) {
        ResourceProvider resourceFactory = new ResourceProvider() {
            @Override
            public Optional<Resource> getResource(Identifier resourceLocation) {
                Identifier corrected = McHelper.newResourceLocation(
                    "immersive_portals", resourceLocation.getPath());
                return resourceManager.getResource(corrected);
            }
        };
        return resourceFactory;
    }
    
    // TODO(26.2 render port): was a ShaderInstance subclass; shaders are now RenderPipelines
    public static class DrawFbInAreaShader {
        void loadWidthHeight(int w, int h) {
        }
    }
    
    public static DrawFbInAreaShader drawFbInAreaShader;
    
    public static void drawPortalAreaWithFramebuffer(
        Portal portal,
        RenderTarget textureProvider,
        Matrix4f modelViewMatrix,
        Matrix4f projectionMatrix
    ) {
    }
    
    public static void renderScreenTriangle() {
        renderScreenTriangle(255, 255, 255, 255);
    }
    
    public static void renderScreenTriangle(Vec3 color) {
        renderScreenTriangle(
            (int) (color.x * 255),
            (int) (color.y * 255),
            (int) (color.z * 255),
            255
        );
    }
    
    public static void testOneTriangle(int r, int g, int b, int a) {
    }
    
    /**
     * {@link RenderTarget#blitToScreen(int, int)}
     */
    @IPVanillaCopy
    public static void renderScreenTriangle(int r, int g, int b, int a) {
    }
    
    /**
     * {@link RenderTarget#blitToScreen(int, int)}
     */
    public static void drawScreenFrameBuffer(
        RenderTarget textureProvider,
        boolean doUseAlphaBlend,
        boolean doEnableModifyAlpha
    ) {
    }

    public static void drawFramebuffer(
            RenderTarget textureProvider, boolean doUseAlphaBlend, boolean doEnableModifyAlpha,
            float xMin, float xMax, float yMin, float yMax
    ) {
        drawFramebufferWithCoordinatesAndDimensions(
                textureProvider,
                doUseAlphaBlend, doEnableModifyAlpha,
                0, 0,
                client.getWindow().getWidth(),
                client.getWindow().getHeight()
        );
    }

    public static void drawFramebufferWithViewport(
            RenderTarget textureProvider, boolean doUseAlphaBlend, boolean doEnableModifyAlpha,
            float left, float right, float bottom, float up,
            int viewportWidth, int viewportHeight
    ) {
        drawFramebufferWithCoordinatesAndDimensions(
                textureProvider,
                doUseAlphaBlend, doEnableModifyAlpha,
                0, 0,
                viewportWidth, viewportHeight
        );
    }
    
    public static void drawFramebufferWithBounds(
        RenderTarget textureProvider, boolean doUseAlphaBlend, boolean doEnableModifyAlpha,
        int xMin, int xMax, int yMin, int yMax
    ) {

        drawFramebufferWithCoordinatesAndDimensions(
            textureProvider,
            doUseAlphaBlend, doEnableModifyAlpha,
            xMin, yMin,
            Mth.abs(xMax - xMin),
            Mth.abs(yMax - yMin)
        );
    }
    
    /**
     * {@link RenderTarget#blitToScreen(int, int)}
     */
    @IPVanillaCopy
    public static void drawFramebufferWithCoordinatesAndDimensions(
        RenderTarget textureProvider, boolean doUseAlphaBlend, boolean doEnableModifyAlpha,
        int x, int y, int viewportWidth, int viewportHeight
    ) {
    }
    
    // it will remove the light sections that are marked to be removed
    // if not, light data will cause minor memory leak
    // and wrongly remove the light data when the chunks get reloaded to client
    // this should not run before world rendering or the smooth lighting may become abnormal in section edge
    public static void lateUpdateLight() {
        if (!ClientWorldLoader.getIsInitialized()) {
            return;
        }
        
        ClientWorldLoader.getClientWorlds().forEach(world -> {
            if (!RenderStates.isDimensionRendered(world.dimension())) {
                world.getChunkSource().getLightEngine().runLightUpdates();
            }
        });
    }
    
    /**
     * If we don't do this
     * the future created in {@link SectionRenderDispatcher#uploadSectionLayer}
     * may never complete
     */
    public static void earlyRemoteUpload() {
    }
    
    public static void applyMirrorFaceCulling() {
        glCullFace(GL_FRONT);
    }
    
    public static void recoverFaceCulling() {
        glCullFace(GL_BACK);
    }
    
    public static void clearAlphaTo1(RenderTarget mcFrameBuffer) {
    }
    
    public static void restoreViewPort() {
        Minecraft client = Minecraft.getInstance();
        GlStateManager._viewport(
            0,
            0,
            client.getWindow().getWidth(),
            client.getWindow().getHeight()
        );
    }
    
    public static float transformFogDistance(float value) {
        if (!WorldRenderInfo.isFogEnabled()) {
            return value * 23333;
        }
        
        // just disable fog for fuse-view portals for now
        if (PortalRendering.isRendering()) {
            Portal renderingPortal = PortalRendering.getRenderingPortal();
            
            if (renderingPortal.isFuseView()) {
                return value * 23333;
            }
        }
        
        // as non-fuse-view portals does not apply scale transformation to modelview,
        // there is no need to transform fog distance (both with and without sodium)
        
        return value;
    }
    
    private static boolean debugEnabled = false;
    
    public static void debugFramebufferDepth() {
    }
    
    public static void debugFramebufferColorRed() {
    }
}

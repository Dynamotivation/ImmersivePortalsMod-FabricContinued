package qouteall.imm_ptl.core.compat.iris_compatibility;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.opengl.GlStateManager;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL43C;

import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_STENCIL_BUFFER_BIT;

public class IPIrisHelper {
    
    public static void copyDepthStencil(
        RenderTarget from, RenderTarget to,
        boolean copyDepth, boolean copyStencil
    ) {
    }
    
    private static boolean isCopyImageSubDataSupported() {
        return GL.getCapabilities().glCopyImageSubData != 0;
    }
    
    public static void newCopyDepthStencil(
        RenderTarget from, RenderTarget to
    ) {
    }
    
    public static void copyColor(
        RenderTarget from, RenderTarget to
    ) {
    }
    
}

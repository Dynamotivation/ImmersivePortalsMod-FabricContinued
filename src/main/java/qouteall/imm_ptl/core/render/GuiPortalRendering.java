package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.ducks.IECamera;
import qouteall.imm_ptl.core.ducks.IEMinecraftClient;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

import java.util.HashMap;

@Environment(EnvType.CLIENT)
public class GuiPortalRendering {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    @Nullable
    private static RenderTarget renderingFrameBuffer = null;
    
    @Nullable
    public static RenderTarget getRenderingFrameBuffer() {
        return renderingFrameBuffer;
    }
    
    public static boolean isRendering() {
        return getRenderingFrameBuffer() != null;
    }
    
    private static void renderWorldIntoFrameBuffer(
        WorldRenderInfo worldRenderInfo,
        RenderTarget framebuffer
    ) {
    }
    
    private static final HashMap<RenderTarget, WorldRenderInfo> renderingTasks = new HashMap<>();
    
    public static void submitNextFrameRendering(
        WorldRenderInfo worldRenderInfo,
        RenderTarget renderTarget
    ) {
    }
    
    // Not API
    public static void _onGameRenderEnd() {
        renderingTasks.forEach((frameBuffer, worldRendering) -> {
            renderWorldIntoFrameBuffer(
                worldRendering, frameBuffer
            );
        });
        renderingTasks.clear();
    }
    
    // not API
    public static void _init() {
        IPCGlobal.CLIENT_CLEANUP_EVENT.register(renderingTasks::clear);
    }
}

package qouteall.imm_ptl.core.render;

import net.minecraft.util.profiling.Profiler;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.block_manipulation.BlockManipulationClient;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;
import qouteall.imm_ptl.core.ducks.IEGameRenderer;
import qouteall.imm_ptl.core.ducks.IEMinecraftClient;
import qouteall.imm_ptl.core.ducks.IEParticleManager;
import qouteall.imm_ptl.core.ducks.IEWorldRenderer;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.mixin.client.render.IERenderSystem;
import qouteall.imm_ptl.core.mixin.client.render.IESectionRenderDispatcher;
import qouteall.imm_ptl.core.render.context_management.DimensionRenderHelper;
import qouteall.imm_ptl.core.render.context_management.FogRendererContext;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.q_misc_util.my_util.LimitedLogger;

import java.util.Stack;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class MyGameRenderer {
    public static final Minecraft client = Minecraft.getInstance();
    
    private static final LimitedLogger limitedLogger = new LimitedLogger(10);
    
//    public static final int MAX_SECONDARY_BUFFER_NUM = 2;
    
    // portal rendering and outer world rendering uses different buffer builder storages
    private static Stack<RenderBuffers> secondaryRenderBuffers = new Stack<>();
    private static int usingRenderBuffersObjectNum = 0;
    
    // the vanilla visibility sections discovery code is multithreaded
    // when the player teleports through a portal, on the first frame it will not work normally
    // so use IP's non-multi-threaded algorithm at the first frame
    public static int vanillaTerrainSetupOverride = 0;
    
    public static boolean enablePortalCaveCulling = true;
    
    public static void init() {
        IPCGlobal.CLIENT_CLEANUP_EVENT.register(() -> {
            secondaryRenderBuffers.clear();
        });
    }
    
    @Nullable
    private static RenderBuffers acquireRenderBuffersObject() {
//        if (usingRenderBuffersObjectNum >= MAX_SECONDARY_BUFFER_NUM) {
//            return null;
//        }
        usingRenderBuffersObjectNum++;
        
        if (secondaryRenderBuffers.isEmpty()) {
            return new RenderBuffers(0);
        }
        else {
            return secondaryRenderBuffers.pop();
        }
    }
    
    private static void returnRenderBuffersObject(RenderBuffers renderBuffers) {
        usingRenderBuffersObjectNum--;
        secondaryRenderBuffers.push(renderBuffers);
    }
    
    public static void renderWorldNew(
        WorldRenderInfo worldRenderInfo,
        Consumer<Runnable> invokeWrapper
    ) {
        WorldRenderInfo.pushRenderInfo(worldRenderInfo);
        
        switchAndRenderTheWorld(
            worldRenderInfo.world,
            worldRenderInfo.cameraPos,
            worldRenderInfo.cameraPos,
            invokeWrapper,
            worldRenderInfo.renderDistance,
            worldRenderInfo.doRenderHand
        );
        
        WorldRenderInfo.popRenderInfo();
    }
    
    private static void switchAndRenderTheWorld(
        ClientLevel newWorld,
        Vec3 thisTickCameraPos,
        Vec3 lastTickCameraPos,
        Consumer<Runnable> invokeWrapper,
        int renderDistance,
        boolean doRenderHand
    ) {
    }
    
    /**
     * {@link LevelRenderer#renderLevel}
     */
    @IPVanillaCopy
    public static void resetFogState() {
    }
    
    public static void updateFogColor() {
    }
    
    /**
     * {@link LevelRenderer#renderLevel}
     */
    @IPVanillaCopy
    public static void resetDiffuseLighting() {
    }
    
    
}

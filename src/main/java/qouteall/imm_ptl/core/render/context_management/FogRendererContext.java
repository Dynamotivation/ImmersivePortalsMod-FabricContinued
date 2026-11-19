package qouteall.imm_ptl.core.render.context_management;

import net.minecraft.util.profiling.Profiler;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.ducks.IECamera;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * {@link FogRenderer}
 * {@link qouteall.imm_ptl.core.mixin.client.multiworld_awareness.MixinFogRenderer}
 */
@SuppressWarnings("SpellCheckingInspection")
public class FogRendererContext {
    public float red;
    public float green;
    public float blue;
    public int targetBiomeFog = -1;
    public int previousBiomeFog = -1;
    public long biomeChangedTime = -1L;
    
    public static Consumer<FogRendererContext> copyContextFromObject;
    public static Consumer<FogRendererContext> copyContextToObject;
    public static Supplier<Vec3> getCurrentFogColor;
    
    public static StaticFieldsSwappingManager<FogRendererContext> swappingManager;
    
    public static void init() {
        //load the class and apply mixin
        FogRenderer.class.hashCode();
        
        swappingManager = new StaticFieldsSwappingManager<>(
            copyContextFromObject, copyContextToObject, false,
            FogRendererContext::new
        );
        
        
    }
    
    public static void update() {
        swappingManager.setOuterDimension(RenderStates.originalPlayerDimension);
        swappingManager.resetChecks();
        if (ClientWorldLoader.getIsInitialized()) {
            ClientWorldLoader.getClientWorlds().forEach(world -> {
                ResourceKey<Level> dimension = world.dimension();
                swappingManager.contextMap.computeIfAbsent(
                    dimension,
                    k -> new StaticFieldsSwappingManager.ContextRecord<>(
                        dimension,
                        new FogRendererContext(),
                        dimension != RenderStates.originalPlayerDimension
                    )
                );
            });
        }
    }
    
    public static Vec3 getFogColorOf(
        ClientLevel destWorld, Vec3 pos
    ) {
        return null;
    }
    
    public static void onPlayerTeleport(ResourceKey<Level> from, ResourceKey<Level> to) {
        swappingManager.updateOuterDimensionAndChangeContext(to);
    }
    
}

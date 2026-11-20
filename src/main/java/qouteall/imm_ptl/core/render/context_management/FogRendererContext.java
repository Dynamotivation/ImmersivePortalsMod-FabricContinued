package qouteall.imm_ptl.core.render.context_management;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 26.x: FogRenderer no longer keeps static colour/biome-blend state (fog is computed every frame from
 * the level's environment attributes), so the per-dimension fog state swapping of the 1.21 port is gone.
 * Kept as a no-op shell so existing callers still compile.
 * TODO(26.2): alternate-dimension fog fix (MixinFogRenderer_A_CVB) and fog of the portal destination
 * must be done in the new portal renderer.
 */
public class FogRendererContext {
    public static void init() {
    }
    
    public static void update() {
    }
    
    public static Vec3 getFogColorOf(ClientLevel destWorld, Vec3 pos) {
        return null;
    }
    
    public static void onPlayerTeleport(ResourceKey<Level> from, ResourceKey<Level> to) {
    }
}

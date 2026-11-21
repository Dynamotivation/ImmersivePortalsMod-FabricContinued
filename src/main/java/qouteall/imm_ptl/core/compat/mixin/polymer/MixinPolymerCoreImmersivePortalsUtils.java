package qouteall.imm_ptl.core.compat.mixin.polymer;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;

import java.util.List;

/**
 * Answers Polymer core's Immersive Portals bridge (used for light updates) with this mod's chunk tracking; some
 * Polymer builds throw "Not implemented!" there.
 * <p>
 * From the 26.3 fork by Kurokekkon (Apache-2.0). Only applied when polymer-virtual-entity is loaded
 * (IPCompatMixinPlugin); require = 0, so a different bridge only disables these overrides.
 */
@Pseudo
@Mixin(targets = "eu.pb4.polymer.core.impl.compat.ImmersivePortalsUtils", remap = false)
public abstract class MixinPolymerCoreImmersivePortalsUtils {
    @Inject(
        method = "getPlayerTracking(Lnet/minecraft/world/level/chunk/LevelChunk;)Ljava/util/List;",
        at = @At("HEAD"), cancellable = true, require = 0
    )
    private static void ip_getPlayerTracking(LevelChunk chunk, CallbackInfoReturnable<List<ServerPlayer>> cir) {
        var pos = chunk.getPos();
        cir.setReturnValue(ImmPtlChunkTracking.getPlayersViewingChunk(chunk.getLevel().dimension(), pos.x(), pos.z(), false));
    }

    @Inject(
        method = "getPlayerTracking(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/world/level/ChunkPos;)Ljava/util/List;",
        at = @At("HEAD"), cancellable = true, require = 0
    )
    private static void ip_getPlayerTracking(ResourceKey<Level> dimension, ChunkPos pos, CallbackInfoReturnable<List<ServerPlayer>> cir) {
        cir.setReturnValue(ImmPtlChunkTracking.getPlayersViewingChunk(dimension, pos.x(), pos.z(), false));
    }
}

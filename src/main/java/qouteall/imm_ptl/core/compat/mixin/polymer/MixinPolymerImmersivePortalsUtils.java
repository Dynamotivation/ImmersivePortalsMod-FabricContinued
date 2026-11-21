package qouteall.imm_ptl.core.compat.mixin.polymer;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;
import qouteall.imm_ptl.core.network.PacketRedirection;

import java.util.List;

/**
 * Answers Polymer's (virtual entity) Immersive Portals bridge with this mod's chunk tracking. Some Polymer builds ship
 * this bridge as null/false/no-context stubs.
 * <p>
 * From the 26.3 fork by Kurokekkon (Apache-2.0). Only applied when polymer-virtual-entity is loaded
 * (IPCompatMixinPlugin); require = 0, so a different bridge only disables these overrides.
 */
@Pseudo
@Mixin(targets = "eu.pb4.polymer.virtualentity.impl.compat.ImmersivePortalsUtils", remap = false)
public abstract class MixinPolymerImmersivePortalsUtils {
    @Inject(method = "getPlayerTracking", at = @At("HEAD"), cancellable = true, require = 0)
    private static void ip_getPlayerTracking(LevelChunk chunk, CallbackInfoReturnable<List<ServerPlayer>> cir) {
        var pos = chunk.getPos();
        cir.setReturnValue(ImmPtlChunkTracking.getPlayersViewingChunk(
            chunk.getLevel().dimension(), pos.x(), pos.z(), false
        ));
    }

    @Inject(method = "isPlayerTracking", at = @At("HEAD"), cancellable = true, require = 0)
    private static void ip_isPlayerTracking(ServerPlayer player, LevelChunk chunk, CallbackInfoReturnable<Boolean> cir) {
        var pos = chunk.getPos();
        cir.setReturnValue(ImmPtlChunkTracking.isPlayerWatchingChunk(
            player, chunk.getLevel().dimension(), pos.x(), pos.z()
        ));
    }

    @Inject(method = "callRedirected", at = @At("HEAD"), cancellable = true, require = 0)
    private static void ip_callRedirected(ServerLevel world, Runnable runnable, CallbackInfo ci) {
        PacketRedirection.withForceRedirect(world, runnable);
        ci.cancel();
    }
}

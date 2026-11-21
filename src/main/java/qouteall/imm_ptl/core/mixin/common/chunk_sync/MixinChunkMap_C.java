package qouteall.imm_ptl.core.mixin.common.chunk_sync;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;
import qouteall.imm_ptl.core.chunk_loading.PlayerChunkLoading;
import qouteall.imm_ptl.core.ducks.IEChunkMap;

@Mixin(value = ChunkMap.class, priority = 1100)
public abstract class MixinChunkMap_C implements IEChunkMap {
    
    @Shadow
    @Final
    private ServerLevel level;
    
    @Shadow
    protected abstract ChunkHolder getVisibleChunkIfPresent(long long_1);
    
    @Shadow
    @Final
    private ThreadedLevelLightEngine lightEngine;
    
    @Shadow
    abstract int getPlayerViewDistance(ServerPlayer serverPlayer);
    
    @Override
    public int ip_getPlayerViewDistance(ServerPlayer player) {
        return getPlayerViewDistance(player);
    }
    
    @Override
    public ServerLevel ip_getWorld() {
        return level;
    }
    
    @Override
    public ThreadedLevelLightEngine ip_getLightingProvider() {
        return lightEngine;
    }
    
    @Override
    public ChunkHolder ip_getChunkHolder(long chunkPosLong) {
        return getVisibleChunkIfPresent(chunkPosLong);
    }
    
    /**
     * packets will be sent on {@link PlayerChunkLoading}
     */
    @Inject(
        method = "applyChunkTrackingView",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onUpdateChunkTracking(
        ServerPlayer serverPlayer, ChunkTrackingView chunkTrackingView, CallbackInfo ci
    ) {
        ci.cancel();
    }
    
    /**
     * Chunks are sent to players by {@link ImmPtlChunkTracking} instead of the vanilla tracking view.
     * This was an @Overwrite; since 26.x the method also registers the chunk holder for change broadcasting
     * (ServerChunkCache.onChunkReadyToSend) and debug synchronization, which must keep running, otherwise pending
     * block/light changes of a newly ready chunk are never broadcast. (fix from the 26.3 port by CaseBacon)
     */
    @Inject(
        method = "onChunkReadyToSend(Lnet/minecraft/server/level/ChunkHolder;Lnet/minecraft/world/level/chunk/LevelChunk;)V",
        at = @At("HEAD")
    )
    private void onChunkReadyToSend(ChunkHolder chunkHolder, LevelChunk chunk, CallbackInfo ci) {
        ImmPtlChunkTracking.onChunkProvidedDeferred(chunk);
    }
    
    // vanilla's per-player sending stays disabled
    @WrapWithCondition(
        method = "onChunkReadyToSend(Lnet/minecraft/server/level/ChunkHolder;Lnet/minecraft/world/level/chunk/LevelChunk;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkMap;markChunkPendingToSend(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/chunk/LevelChunk;)V"
        )
    )
    private boolean shouldMarkChunkPendingToSend(ServerPlayer player, LevelChunk chunk) {
        return false;
    }
}

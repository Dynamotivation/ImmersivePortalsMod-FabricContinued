package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.render.CrossPortalEntityRenderer;

@Mixin(LevelExtractor.class)
public class MixinLevelExtractor_CrossPortal {
    @Shadow
    private @Nullable ClientLevel level;

    @Shadow
    @Final
    private LevelRenderer levelRenderer;

    // every view (main view, portal views, nested views) extracts its level's entities here: add the projections of
    // entities that are partly through a portal leading into this level
    @Inject(method = "extractVisibleEntities", at = @At("TAIL"), require = 0)
    private void ip_onExtractVisibleEntitiesEnd(
        Camera camera, Frustum frustum, DeltaTracker deltaTracker, LevelRenderState output, CallbackInfo ci
    ) {
        if (level == null) {
            return;
        }
        CrossPortalEntityRenderer.extractProjections(
            level, camera, frustum, deltaTracker, levelRenderer.entityRenderDispatcher(), output
        );
    }
}

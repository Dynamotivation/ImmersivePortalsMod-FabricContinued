package qouteall.imm_ptl.core.mixin.client.render.optimization;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.render.context_management.CloudContext;
import qouteall.imm_ptl.core.render.context_management.RenderStates;

// Optimize cloud rendering by storing the context and
// avoiding rebuild the cloud mesh every time
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer_Clouds {
    
    @Shadow
    private int prevCloudX;
    
    @Shadow
    private int prevCloudY;
    
    @Shadow
    private int prevCloudZ;
    
    
    @Shadow
    private ClientLevel level;
    
    @Shadow
    private boolean generateClouds;
    
    @Shadow
    private int ticks;
    
    @Inject(
        method = "renderClouds",
        at = @At("HEAD")
    )
    private void onBeginRenderClouds(
        PoseStack poseStack, Matrix4f projectionMatrix, Matrix4f frustrumMatrix, float partialTick, double camX, double camY, double camZ, CallbackInfo ci
    ) {
        if (RenderStates.getRenderedPortalNum() == 0) {
            return;
        }
        
        if (IPGlobal.cloudOptimization) {
            portal_onBeginCloudRendering(partialTick, camX, camY, camZ);
        }
    }
    
    @Inject(
        method = "renderClouds",
        at = @At("RETURN")
    )
    private void onEndRenderClouds(PoseStack poseStack, Matrix4f projectionMatrix, Matrix4f frustrumMatrix, float partialTick, double camX, double camY, double camZ, CallbackInfo ci) {
        if (RenderStates.getRenderedPortalNum() == 0) {
            return;
        }
        
        if (IPGlobal.cloudOptimization) {
            portal_onEndCloudRendering();
        }
    }
    
    private void portal_yieldCloudContext(CloudContext context) {
    }
    
    private void portal_loadCloudContext(CloudContext context) {
    }
    
    /**
     * {@link LevelRenderer#renderClouds}
     */
    @IPVanillaCopy
    private void portal_onBeginCloudRendering(
        float partialTick, double cameraX, double cameraY, double cameraZ
    ) {
    }
    
    private void portal_onEndCloudRendering() {
        if (!generateClouds) {
            final CloudContext newContext = new CloudContext();
            portal_yieldCloudContext(newContext);
            
            CloudContext.appendContext(newContext);
        }
    }
}

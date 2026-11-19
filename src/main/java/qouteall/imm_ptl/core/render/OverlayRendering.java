package qouteall.imm_ptl.core.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.nether_portal.BreakablePortalEntity;

@Environment(EnvType.CLIENT)
public class OverlayRendering {
    private static final RandomSource random = RandomSource.create();
    
    public static boolean shouldRenderOverlay(Portal portal) {
        if (portal instanceof BreakablePortalEntity breakablePortalEntity) {
            if (breakablePortalEntity.getActualOverlay() != null) {
                return breakablePortalEntity.isInFrontOfPortal(CHelper.getCurrentCameraPos());
            }
        }
        return false;
    }
    
    private static boolean shaderOverlayWarned = false;
    
    // TODO(port): overlay rendering is deferred with the rest of the renderer redesign.
    // 26.x replaced immediate-mode buffers with a submit-node pipeline.
    public static void onRenderPortalEntity(
        Portal portal,
        Object matrixStack,
        Object vertexConsumerProvider
    ) {
        if (shaderOverlayWarned) {
            return;
        }
        shaderOverlayWarned = true;
    }
    
    public static BlockState getOverlayBlockState(BreakablePortalEntity portal) {
        return portal.getActualOverlay();
    }
    
    static int getOverlayLight() {
        return OverlayTexture.NO_OVERLAY;
    }
    
    static Vec3 getNoiseOffset(float partialTick) {
        return new Vec3(0, 0, 0);
    }
}

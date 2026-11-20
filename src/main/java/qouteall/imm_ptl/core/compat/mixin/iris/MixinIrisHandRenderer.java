package qouteall.imm_ptl.core.compat.mixin.iris;

import net.irisshaders.iris.pathways.HandRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.render.portalview.PortalViewRenderer;

/**
 * With a shader pack, Iris draws the first-person hand inside LevelRenderer.render (HandRenderer). A portal view is
 * a LevelRenderer.render of the destination: do not draw the player's hand into it. Optional, require = 0.
 */
@Mixin(value = HandRenderer.class, remap = false)
public class MixinIrisHandRenderer {
    @Inject(method = "canRender", at = @At("HEAD"), cancellable = true, require = 0)
    private void ip_noHandInPortalViews(Camera camera, GameRenderer gameRenderer, CallbackInfoReturnable<Boolean> cir) {
        if (PortalViewRenderer.isRenderingViews()) {
            cir.setReturnValue(false);
        }
    }
}

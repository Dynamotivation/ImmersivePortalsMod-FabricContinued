package qouteall.imm_ptl.core.mixin.client.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.ParticlesRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.ducks.IEParticleManager;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;

@SuppressWarnings("resource")
@Mixin(ParticleEngine.class)
public class MixinParticleEngine implements IEParticleManager {
    @Shadow
    protected ClientLevel level;
    
    // skip particle rendering for far portals
    @Inject(
        method = "extract",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onBeginRenderParticles(
        ParticlesRenderState particlesRenderState, Frustum frustum, Camera camera,
        float partialTickTime, CallbackInfo ci
    ) {
        if (PortalRendering.isRendering()) {
            if (RenderStates.getRenderedPortalNum() > 4) {
                ci.cancel();
            }
        }
    }
    
    // a lava ember particle can generate a smoke particle during ticking
    // avoid generating the particle into the wrong dimension
    @Inject(method = "tickParticle", at = @At("HEAD"), cancellable = true)
    private void onTickParticle(Particle particle, CallbackInfo ci) {
        if (((IEParticle) particle).portal_getWorld() != Minecraft.getInstance().level) {
            ci.cancel();
        }
    }
    
    @Override
    public void ip_setWorld(ClientLevel world_) {
        level = world_;
    }
}

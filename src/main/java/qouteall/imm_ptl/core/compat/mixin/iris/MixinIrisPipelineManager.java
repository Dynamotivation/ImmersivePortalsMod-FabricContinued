/*
 * Adapted from the Immersive Portals port for Minecraft 26.3 (other-ports/ImmersivePortalsMod,
 * compat/iris_portal_view/mixin/MixinIrisPipelineManager.java + IrisPipelineManagerAccessor.java),
 * Copyright 2020 qouteall and contributors, licensed under the Apache License, Version 2.0.
 * Changes: the accessor is replaced by an interface outside the mixin package, injectors use require = 0.
 */
package qouteall.imm_ptl.core.compat.mixin.iris;

import net.irisshaders.iris.pipeline.PipelineManager;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.compat.iris_portal_view.IEIrisPipelineManager;
import qouteall.imm_ptl.core.compat.iris_portal_view.IrisViewPipelines;

/**
 * Optional (imm_ptl_compat.mixins.json, applied by IPCompatMixinPlugin only when Iris 1.11.x is loaded and the
 * targets below exist with these exact descriptors).
 */
@Mixin(value = PipelineManager.class, remap = false)
public class MixinIrisPipelineManager implements IEIrisPipelineManager {
    @Shadow
    private WorldRenderingPipeline pipeline;

    @Override
    public void ip_setCurrentPipeline(WorldRenderingPipeline pipeline) {
        this.pipeline = pipeline;
    }

    // while a portal view renders, Iris' LevelRenderer hook gets the view's camera pipeline
    @Inject(method = "preparePipeline", at = @At("HEAD"), cancellable = true, require = 0)
    private void ip_cameraPipeline(NamespacedId dimension, CallbackInfoReturnable<WorldRenderingPipeline> cir) {
        WorldRenderingPipeline selected = IrisViewPipelines.current();
        if (selected != null) {
            cir.setReturnValue(selected);
        }
    }

    @Inject(method = "destroyPipeline", at = @At("HEAD"), require = 0)
    private void ip_releaseCameraPipelines(CallbackInfo ci) {
        IrisViewPipelines.onIrisPipelinesDestroyed();
    }
}

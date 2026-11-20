package qouteall.imm_ptl.core.compat.iris_portal_view;

import net.irisshaders.iris.pipeline.WorldRenderingPipeline;

/**
 * Added to Iris' PipelineManager by {@code compat.mixin.iris.MixinIrisPipelineManager} (optional compat mixin).
 * Not in a mixin package, so {@code instanceof} checks are safe when the mixin did not apply.
 */
public interface IEIrisPipelineManager {
    /** Sets the pipeline Iris treats as current, without touching its per-dimension map. */
    void ip_setCurrentPipeline(WorldRenderingPipeline pipeline);
}

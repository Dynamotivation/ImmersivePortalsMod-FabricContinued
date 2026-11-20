/*
 * Adapted from the Immersive Portals port for Minecraft 26.3 (other-ports/ImmersivePortalsMod,
 * compat/iris_compatibility/IrisInterfaceOnPresent.java), Copyright 2020 qouteall and contributors,
 * licensed under the Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0).
 * Changes: Iris 1.11.4 / Minecraft 26.2 APIs, runtime check of the optional mixins, pipeline generation counter.
 */
package qouteall.imm_ptl.core.compat.iris_compatibility;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shadows.ShadowRenderer;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4fc;
import qouteall.imm_ptl.core.compat.iris_portal_view.IrisPortalClipping;
import qouteall.imm_ptl.core.compat.iris_portal_view.IrisViewPipelines;

import java.util.UUID;

/**
 * Only loaded when Iris is present (instantiated reflectively by IPModEntryClient).
 */
public class IrisInterfaceOnPresent extends IrisInterface.Invoker {
    @Override
    public boolean isIrisPresent() {
        return true;
    }

    @Override
    public boolean isShaders() {
        // the loaded pack; IrisApi.isShaderPackInUse depends on the pipeline of the last rendered dimension
        return Iris.getCurrentPack().isPresent();
    }

    @Override
    public boolean isRenderingShadowMap() {
        return ShadowRenderer.ACTIVE;
    }

    @Override
    public void reloadPipelines() {
        Iris.getPipelineManager().destroyPipeline();
    }

    @Nullable
    @Override
    public String getShaderpackName() {
        return Iris.getCurrentPackName();
    }

    @Override
    public @Nullable String getPortalViewUnsupportedReason() {
        String pipelines = IrisViewPipelines.getUnsupportedReason();
        if (pipelines != null) {
            return pipelines;
        }
        return IrisPortalClipping.getUnsupportedReason();
    }

    @Override
    public int getPipelineGeneration() {
        return IrisViewPipelines.getGeneration();
    }

    @Override
    public Runnable preserveCameraState() {
        return isShaders() ? IrisViewPipelines.preserveGlobals() : () -> {};
    }

    @Override
    public Runnable beginPortalView(UUID portalId) {
        return IrisViewPipelines.begin(portalId);
    }

    @Override
    public void setShaderClipping(@Nullable Vector4fc coefficients) {
        IrisPortalClipping.setCoefficients(coefficients);
    }

    @Override
    public void onFrameStart(long frame) {
        IrisViewPipelines.onFrameStart(frame);
    }

    @Override
    public void releasePortalView(UUID portalId) {
        IrisViewPipelines.release(portalId);
    }

    @Override
    public void releaseAllPortalViews() {
        IrisViewPipelines.clear();
    }
}

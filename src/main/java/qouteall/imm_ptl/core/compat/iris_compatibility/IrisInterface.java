package qouteall.imm_ptl.core.compat.iris_compatibility;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector4fc;

import java.util.UUID;

/**
 * Everything Immersive Portals needs from Iris.
 * <p>
 * This class must not reference any Iris class: it is loaded without Iris. The default {@link Invoker} is a no-op
 * used when Iris is absent; {@link IrisInterfaceOnPresent} (and through it {@code compat.iris_portal_view}) is
 * instantiated reflectively by {@code IPModEntryClient} only when the {@code iris} mod is loaded.
 * <p>
 * Portal views with an active shader pack (see PORTING.md, section B): every portal view owns an Iris camera
 * pipeline (own uniforms, previous-frame matrices, G-buffers, temporal targets, shadow resources), selected while
 * the destination's LevelRenderer renders the view. Adapted from the Immersive Portals 26.3 fork (Apache-2.0).
 */
public class IrisInterface {

    public static class Invoker {
        public boolean isIrisPresent() {
            return false;
        }

        /**
         * Whether a shader pack is loaded and enabled.
         */
        public boolean isShaders() {
            return false;
        }

        public boolean isRenderingShadowMap() {
            return false;
        }

        public void reloadPipelines() {}

        @Nullable
        public String getShaderpackName() {
            return null;
        }

        /**
         * @return null when portal views can be rendered through Iris camera pipelines with the active shader pack,
         * otherwise the reason why not (the optional Iris mixins did not apply, unsupported Iris version, ...)
         */
        @Nullable
        public String getPortalViewUnsupportedReason() {
            return "Iris is not present";
        }

        /**
         * Incremented whenever Iris destroys its pipelines (shader reload, pack change, dimension change).
         * A failure of the Iris portal views is remembered until this changes.
         */
        public int getPipelineGeneration() {
            return 0;
        }

        /**
         * Saves Iris' singleton scratch state (captured matrices, fog, shadow statics). Run the result in finally.
         */
        public Runnable preserveCameraState() {
            return () -> {};
        }

        /**
         * Selects (creating it if needed) the camera pipeline of the given portal view as Iris' current pipeline.
         * Always run the returned restoration in finally. May throw if the pipeline cannot be created.
         */
        public Runnable beginPortalView(UUID portalId) {
            return () -> {};
        }

        /**
         * Sets the clip plane of the shader pack's G-buffer programs (clip-space coefficients, see
         * {@code ShaderClipCoefficients}), or disables clipping with null.
         */
        public void setShaderClipping(@Nullable Vector4fc coefficients) {}

        /** Releases camera pipelines that were not used for a while. */
        public void onFrameStart(long frame) {}

        public void releasePortalView(UUID portalId) {}

        public void releaseAllPortalViews() {}
    }

    public static Invoker invoker = new Invoker();
}

/*
 * Adapted from the Immersive Portals port for Minecraft 26.3 (other-ports/ImmersivePortalsMod,
 * compat/iris_portal_view/IrisViewPipelines.java), Copyright 2020 qouteall and contributors,
 * licensed under the Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0).
 * Changes for Iris 1.11.4 / Minecraft 26.2: no override-wrapper cache cleanup (Iris 1.11.4 has no
 * RenderSystem.iris$overrides cache; GlRenderPipeline is a plain record without a VAO), pipeline pointer set
 * through IEIrisPipelineManager instead of an accessor, expiry of unused pipelines, generation counter,
 * null-safe scratch state preservation, no exceptions from clear().
 */
package qouteall.imm_ptl.core.compat.iris_portal_view;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.PipelineManager;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.UUID;

/**
 * Camera-owned Iris pipelines for portal views (render thread only).
 * <p>
 * Iris keeps one {@link IrisRenderingPipeline} per dimension; its per-frame state (uniforms, previous-frame
 * matrices, camera position tracker, G-buffers, temporal ping-pong targets, shadow targets) belongs to one camera.
 * Every portal view therefore gets its own pipeline, built from the destination's program set of the current pack.
 * {@link #begin} makes it Iris' current pipeline while the destination LevelRenderer renders (Iris' LevelRenderer
 * hook calls {@code PipelineManager.preparePipeline}, which {@code MixinIrisPipelineManager} answers with it); the
 * pipelines are never put into Iris' per-dimension map and never advance Iris' global frame counters.
 * <p>
 * At most {@link #MAX_PIPELINES} pipelines, least recently used evicted. Released on expiry (unused frames), on
 * Iris' destroyPipeline (shader reload, pack change, dimension change; hook in MixinIrisPipelineManager) and on
 * client cleanup (world change / disconnect, via PortalViewRenderer.cleanUp).
 */
public final class IrisViewPipelines {
    private static final Logger LOGGER = LoggerFactory.getLogger(IrisViewPipelines.class);

    public static final int MAX_PIPELINES = 2;
    private static final long EXPIRY_FRAMES = 200;

    private static final class Entry {
        final NamespacedId dimension;
        final IrisRenderingPipeline pipeline;
        long lastUsedFrame;

        Entry(NamespacedId dimension, IrisRenderingPipeline pipeline) {
            this.dimension = dimension;
            this.pipeline = pipeline;
        }
    }

    private static final LinkedHashMap<UUID, Entry> VIEWS = new LinkedHashMap<>(4, .75f, true);
    private static @Nullable IrisRenderingPipeline active;
    private static long frame = 0;
    private static int generation = 0;

    private IrisViewPipelines() {}

    /** The camera pipeline of the portal view being rendered, null otherwise. */
    public static @Nullable WorldRenderingPipeline current() {
        return active;
    }

    public static int size() {
        return VIEWS.size();
    }

    public static int getGeneration() {
        return generation;
    }

    public static @Nullable String getUnsupportedReason() {
        if (!(Iris.getPipelineManager() instanceof IEIrisPipelineManager)) {
            return "the Iris PipelineManager mixin did not apply (unsupported Iris version?)";
        }
        return null;
    }

    public static void onFrameStart(long currentFrame) {
        frame = currentFrame;
        if (active != null || VIEWS.isEmpty()) {
            return;
        }
        UUID[] ids = VIEWS.keySet().toArray(UUID[]::new);
        for (UUID id : ids) {
            Entry entry = VIEWS.get(id);
            if (entry != null && currentFrame - entry.lastUsedFrame > EXPIRY_FRAMES) {
                releaseQuietly(id);
            }
        }
    }

    /**
     * Makes the camera pipeline of the given portal view Iris' current pipeline. Must be called with
     * {@code Minecraft.level} switched to the destination and {@code GameRenderer.mainRenderTarget()} returning
     * the view's target (a new pipeline sizes its targets after it).
     *
     * @return the restoration, to run in finally
     */
    public static Runnable begin(UUID id) {
        ShaderPack pack = Iris.getCurrentPack().orElse(null);
        if (pack == null) {
            return () -> {};
        }
        if (active != null) {
            throw new IllegalStateException("Nested Iris portal view");
        }
        PipelineManager manager = Iris.getPipelineManager();
        if (!(manager instanceof IEIrisPipelineManager access)) {
            throw new IllegalStateException("Iris PipelineManager mixin not applied");
        }
        WorldRenderingPipeline previous = manager.getPipelineNullable();
        try {
            NamespacedId dimension = Iris.getCurrentDimension();
            Entry entry = VIEWS.get(id);
            if (entry != null && !entry.dimension.equals(dimension)) {
                release(id);
                entry = null;
            }
            if (entry == null) {
                while (VIEWS.size() >= MAX_PIPELINES) {
                    release(VIEWS.keySet().iterator().next());
                }
                LOGGER.info("Creating Iris camera pipeline for portal view {} (program set {})", id, dimension);
                IrisRenderingPipeline pipeline = new IrisRenderingPipeline(pack.getProgramSet(dimension));
                entry = new Entry(dimension, pipeline);
                VIEWS.put(id, entry);
            }
            entry.lastUsedFrame = frame;
            active = entry.pipeline;
            access.ip_setCurrentPipeline(active);
        }
        catch (RuntimeException | Error failure) {
            active = null;
            access.ip_setCurrentPipeline(previous);
            throw failure;
        }
        return () -> {
            active = null;
            access.ip_setCurrentPipeline(previous);
        };
    }

    public static void release(UUID id) {
        if (active != null && VIEWS.get(id) != null && VIEWS.get(id).pipeline == active) {
            LOGGER.error("Not releasing the Iris camera pipeline of {} while it renders", id);
            return;
        }
        Entry removed = VIEWS.remove(id);
        if (removed != null) {
            // Iris 1.11.4 has no program-keyed override cache (its GlDevice hook wraps programs in plain
            // GlRenderPipeline records per call), so the pipeline owns everything it created.
            removed.pipeline.destroy();
        }
    }

    private static void releaseQuietly(UUID id) {
        try {
            release(id);
        }
        catch (Throwable e) {
            LOGGER.error("Failed to release the Iris camera pipeline of {}", id, e);
        }
    }

    /** Releases all camera pipelines. Never throws. */
    public static void clear() {
        if (active != null) {
            LOGGER.error("Iris pipelines destroyed while a portal view renders; camera pipelines kept until the next cleanup");
            return;
        }
        for (UUID id : VIEWS.keySet().toArray(UUID[]::new)) {
            releaseQuietly(id);
        }
        VIEWS.clear();
    }

    /** Called (via mixin) at the start of Iris' PipelineManager.destroyPipeline. */
    public static void onIrisPipelinesDestroyed() {
        generation++;
        clear();
    }

    /**
     * Saves Iris' singleton scratch values (unlike the temporal resources owned by each pipeline): extraction
     * hooks (fog, lightmap) and the LevelRenderer hook overwrite them for every camera.
     */
    public static Runnable preserveGlobals() {
        CapturedRenderingState s = CapturedRenderingState.INSTANCE;
        Matrix4fc modelViewRef = s.getGbufferModelView();
        Matrix4fc projectionRef = s.getGbufferProjection();
        Matrix4f model = modelViewRef == null ? null : new Matrix4f(modelViewRef);
        Matrix4f projection = projectionRef == null ? null : new Matrix4f(projectionRef);
        Vector3d fog = new Vector3d(s.getFogColor());
        float density = s.getFogDensity(), tick = s.getTickDelta(), realTick = s.getRealTickDelta();
        float darkness = s.getDarknessLightFactor(), alpha = s.getCurrentAlphaTest(), cloud = s.getCloudTime();
        int block = s.getCurrentRenderedBlockEntity(), entity = s.getCurrentRenderedEntity(), item = s.getCurrentRenderedItem();
        Matrix4f shadowModel = ShadowRenderer.MODELVIEW;
        Matrix4f shadowProjection = ShadowRenderer.PROJECTION;
        var shadowFrustum = ShadowRenderer.FRUSTUM;
        var shadowEntities = ShadowRenderer.visibleBlockEntities;
        int resolution = ShadowRenderer.RESOLUTION, distance = ShadowRenderer.renderDistance;
        boolean shadowActive = ShadowRenderer.ACTIVE;
        return () -> {
            if (model != null) {
                s.setGbufferModelView(model);
            }
            if (projection != null) {
                s.setGbufferProjection(projection);
            }
            s.setFogColor((float) fog.x, (float) fog.y, (float) fog.z);
            s.setFogDensity(density);
            s.setTickDelta(tick);
            s.setRealTickDelta(realTick);
            s.setDarknessLightFactor(darkness);
            s.setCurrentAlphaTest(alpha);
            s.setCloudTime(cloud);
            s.setCurrentBlockEntity(block);
            s.setCurrentEntity(entity);
            s.setCurrentRenderedItem(item);
            ShadowRenderer.MODELVIEW = shadowModel;
            ShadowRenderer.PROJECTION = shadowProjection;
            ShadowRenderer.FRUSTUM = shadowFrustum;
            ShadowRenderer.visibleBlockEntities = shadowEntities;
            ShadowRenderer.RESOLUTION = resolution;
            ShadowRenderer.renderDistance = distance;
            ShadowRenderer.ACTIVE = shadowActive;
        };
    }
}

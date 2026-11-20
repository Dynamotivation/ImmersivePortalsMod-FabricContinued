package qouteall.imm_ptl.core.render.portalview;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.ducks.IEPortalCamera;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.OverlayRendering;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.renderer.PortalRenderer;
import qouteall.imm_ptl.core.render.context_management.RenderStates;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The portal renderer of the 26.x port.
 * <p>
 * Every frame, before the main level is extracted, the destination of each visible portal is rendered
 * (extract + render with the destination dimension's own LevelExtractor and LevelRenderer) from the
 * virtual camera into an offscreen {@link RenderTarget}. Then, after the main terrain and entities are
 * drawn, the portal's view area mesh is drawn into the main target, sampling that offscreen target by
 * screen position (see portal_view.fsh). The depth of the view area mesh makes entities and blocks in front
 * of the portal occlude it properly.
 * <p>
 * There is no stencil buffer in the new graphics API, and there is no clip plane in vanilla shaders, so
 * the destination view is clipped by an oblique near plane ({@link ObliqueProjection}).
 */
@Environment(EnvType.CLIENT)
public class PortalViewRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(PortalViewRenderer.class);

    private static final Minecraft client = Minecraft.getInstance();

    public static final int MAX_VIEWS = 8;
    
    private static final boolean DUMP_VIEWS = System.getProperty("ip.dumpviews") != null;
    private static int dumpCounter = 0;
    private static String lastFogColor = "?";
    
    // one Lightmap per dimension (Lightmap.render can only be called once per frame per object)
    private static final java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, net.minecraft.client.renderer.Lightmap>
        lightmaps = new java.util.HashMap<>();
    private static final java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, net.minecraft.client.renderer.state.LightmapRenderState>
        lightmapStates = new java.util.HashMap<>();
    private static final java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, Long> lightmapFrame = new java.util.HashMap<>();
    private static long frameCounter = 0;
    
    // cloud renderers for portal views: CloudRenderer's buffer ring cannot be used twice in one frame
    private static final java.util.List<net.minecraft.client.renderer.CloudRenderer> cloudRendererPool = new java.util.ArrayList<>();
    private static int cloudRenderersUsed = 0;
    private static java.util.Optional<net.minecraft.client.renderer.CloudRenderer.TextureData> cloudTextureData = null;
    
    private static net.minecraft.client.renderer.CloudRenderer acquireCloudRenderer() {
        if (cloudRenderersUsed >= cloudRendererPool.size()) {
            net.minecraft.client.renderer.CloudRenderer renderer = new net.minecraft.client.renderer.CloudRenderer();
            qouteall.imm_ptl.core.mixin.client.render.IECloudRenderer access =
                (qouteall.imm_ptl.core.mixin.client.render.IECloudRenderer) renderer;
            if (cloudTextureData == null) {
                cloudTextureData = access.ip_prepare(client.getResourceManager(), Profiler.get());
            }
            access.ip_apply(cloudTextureData, client.getResourceManager(), Profiler.get());
            cloudRendererPool.add(renderer);
        }
        return cloudRendererPool.get(cloudRenderersUsed++);
    }

    // fog UBOs for portal views: FogRenderer.updateBuffer maps the CURRENT slot of a ring buffer that only rotates once
    // per frame (unsynchronized persistent mapping), so the main view's later write would replace the fog of every
    // portal view rendered this frame
    private static final java.util.List<FogRenderer> fogRendererPool = new java.util.ArrayList<>();
    private static int fogRenderersUsed = 0;

    private static FogRenderer acquireFogRenderer() {
        if (fogRenderersUsed >= fogRendererPool.size()) {
            fogRendererPool.add(new FogRenderer());
        }
        return fogRendererPool.get(fogRenderersUsed++);
    }

    // secondary level renderers that rendered a view last frame: Minecraft only calls endFrame() on its levelRenderer
    // (Sodium's per-renderer terrain uniform storage never rotates/shrinks otherwise)
    private static final java.util.Set<LevelRenderer> renderersUsed =
        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    private static void endFrameOfViewResources() {
        // rotating at the start of the next frame puts the fence after all commands of the previous frame
        for (int i = 0; i < cloudRenderersUsed; i++) {
            cloudRendererPool.get(i).endFrame();
        }
        cloudRenderersUsed = 0;
        for (int i = 0; i < fogRenderersUsed; i++) {
            fogRendererPool.get(i).endFrame();
        }
        fogRenderersUsed = 0;
        for (LevelRenderer renderer : renderersUsed) {
            if (renderer != client.levelRenderer) {
                renderer.endFrame();
            }
        }
        renderersUsed.clear();
    }
    private static com.mojang.blaze3d.textures.GpuTextureView activeLightmap = null;
    
    public static com.mojang.blaze3d.textures.GpuTextureView getActiveLightmapOverride() {
        return activeLightmap;
    }

    public static final RenderPipeline PORTAL_VIEW_PIPELINE = RenderPipeline.builder(
            net.minecraft.client.renderer.RenderPipelines.GLOBALS_SNIPPET
        )
        .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
        .withLocation(Identifier.fromNamespaceAndPath("immersive_portals", "pipeline/portal_view"))
        .withVertexShader(Identifier.fromNamespaceAndPath("immersive_portals", "core/portal_view"))
        .withFragmentShader(Identifier.fromNamespaceAndPath("immersive_portals", "core/portal_view"))
        .withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_COLOR))
        .withCull(false)
        .withVertexBinding(0, DefaultVertexFormat.POSITION)
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
        .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
        .build();

    public static class PortalView {
        public final Portal portal;
        public final TextureTarget target;
        // the destination has its terrain built, so the view can be revealed
        public boolean ready = false;

        PortalView(Portal portal, TextureTarget target) {
            this.portal = portal;
            this.target = target;
        }
    }

    // ---- loading placeholder: opaque portal texture that fades out once the destination is ready ----
    private static final Identifier PLACEHOLDER_TEXTURE =
        Identifier.fromNamespaceAndPath("immersive_portals", "textures/gui/portal_placeholder.png");
    private static final float PLACEHOLDER_FADE_SECONDS = 0.6f;
    private static final float PLACEHOLDER_MAX_WAIT_SECONDS = 8.0f;
    private static final int PLACEHOLDER_READY_FRAMES = 3;

    private static class PlaceholderState {
        float waitedSeconds = 0;
        int readyFrames = 0;
        float fade = 0; // 0: placeholder fully opaque, 1: view fully revealed
        long lastSeenFrame;
    }

    private static final java.util.Map<java.util.UUID, PlaceholderState> placeholderStates = new java.util.HashMap<>();
    private static long placeholderLastNanos = 0;
    private static GpuSampler placeholderSampler;

    public static final RenderPipeline PORTAL_PLACEHOLDER_PIPELINE = RenderPipeline.builder(
            net.minecraft.client.renderer.RenderPipelines.GLOBALS_SNIPPET
        )
        .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
        .withLocation(Identifier.fromNamespaceAndPath("immersive_portals", "pipeline/portal_placeholder"))
        .withVertexShader(Identifier.fromNamespaceAndPath("immersive_portals", "core/portal_placeholder"))
        .withFragmentShader(Identifier.fromNamespaceAndPath("immersive_portals", "core/portal_placeholder"))
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
        .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
        .build();

    // only the automatically generated two-way nether portals: manual portals, one-way portals (end, ...) and
    // API portals show their view directly
    private static boolean usesLoadingPlaceholder(Portal portal) {
        return portal instanceof qouteall.imm_ptl.core.portal.nether_portal.NetherPortalEntity netherPortal
            && !netherPortal.isOneWay();
    }

    private static final Identifier OVERLAY_TEXTURE =
        Identifier.fromNamespaceAndPath("immersive_portals", "textures/gui/portal_overlay.png");
    private static final int OVERLAY_FRAMES = 32;

    public static final RenderPipeline PORTAL_OVERLAY_PIPELINE = RenderPipeline.builder(
            net.minecraft.client.renderer.RenderPipelines.GLOBALS_SNIPPET
        )
        .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
        .withLocation(Identifier.fromNamespaceAndPath("immersive_portals", "pipeline/portal_overlay"))
        .withVertexShader(Identifier.fromNamespaceAndPath("immersive_portals", "core/portal_placeholder"))
        .withFragmentShader(Identifier.fromNamespaceAndPath("immersive_portals", "core/portal_overlay"))
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
        .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
        .build();

    /**
     * The translucent animated overlay (nether portal swirl) over the pane, see {@link OverlayRendering}.
     * One quad per block, so the 16x16 frames of the vanilla portal texture strip repeat per block.
     */
    private static void drawOverlay(PortalView view, RenderTarget mainTarget, Matrix4f viewRotation, Vec3 cameraPos) {
        Portal portal = view.portal;
        if (!(portal instanceof qouteall.imm_ptl.core.portal.nether_portal.BreakablePortalEntity breakable)) {
            return;
        }
        qouteall.imm_ptl.core.portal.nether_portal.BreakablePortalEntity.OverlayInfo overlay = breakable.getActualOverlay();
        if (overlay == null || !OverlayRendering.shouldRenderOverlay(portal)) {
            return;
        }
        double width = portal.getWidth();
        double height = portal.getHeight();
        int cellsW = (int) java.lang.Math.max(1, java.lang.Math.round(width));
        int cellsH = (int) java.lang.Math.max(1, java.lang.Math.round(height));
        if (cellsW * cellsH > 1600) {
            return;
        }
        Vec3 originRel = portal.getOriginPos().subtract(cameraPos);
        Vec3 axisW = portal.getAxisW();
        Vec3 axisH = portal.getAxisH();
        double cw = width / cellsW;
        double ch = height / cellsH;
        int frame = (int) ((System.nanoTime() / 100_000_000L) % OVERLAY_FRAMES);
        float v0 = frame / (float) OVERLAY_FRAMES;
        float v1 = (frame + 1) / (float) OVERLAY_FRAMES;

        net.minecraft.client.renderer.texture.AbstractTexture texture = client.getTextureManager().getTexture(OVERLAY_TEXTURE);
        if (placeholderSampler == null) {
            placeholderSampler = RenderSystem.getDevice().createSampler(
                AddressMode.REPEAT, AddressMode.REPEAT,
                FilterMode.NEAREST, FilterMode.NEAREST, 1, OptionalDouble.empty()
            );
        }

        try (ByteBufferBuilder byteBuffer = new ByteBufferBuilder(cellsW * cellsH * 6 * 20 + 64)) {
            BufferBuilder builder = new BufferBuilder(byteBuffer, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_TEX);
            for (int i = 0; i < cellsW; i++) {
                for (int j = 0; j < cellsH; j++) {
                    double x0 = -width / 2 + i * cw;
                    double x1 = x0 + cw;
                    double y0 = -height / 2 + j * ch;
                    double y1 = y0 + ch;
                    Vec3 a = originRel.add(axisW.scale(x0)).add(axisH.scale(y0));
                    Vec3 b = originRel.add(axisW.scale(x1)).add(axisH.scale(y0));
                    Vec3 c = originRel.add(axisW.scale(x1)).add(axisH.scale(y1));
                    Vec3 d = originRel.add(axisW.scale(x0)).add(axisH.scale(y1));
                    builder.addVertex((float) a.x, (float) a.y, (float) a.z).setUv(0, v1);
                    builder.addVertex((float) b.x, (float) b.y, (float) b.z).setUv(1, v1);
                    builder.addVertex((float) c.x, (float) c.y, (float) c.z).setUv(1, v0);
                    builder.addVertex((float) a.x, (float) a.y, (float) a.z).setUv(0, v1);
                    builder.addVertex((float) c.x, (float) c.y, (float) c.z).setUv(1, v0);
                    builder.addVertex((float) d.x, (float) d.y, (float) d.z).setUv(0, v0);
                }
            }
            MeshData meshData = builder.build();
            if (meshData == null) {
                return;
            }
            try (meshData) {
                int vertexCount = meshData.drawState().vertexCount();
                GpuBuffer vertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "Portal overlay mesh", com.mojang.blaze3d.buffers.GpuBuffer.USAGE_VERTEX, meshData.vertexBuffer()
                );
                buffersToClose.add(vertexBuffer);
                RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.TRIANGLES);
                GpuBuffer indexBuffer = indices.getBuffer(vertexCount);
                GpuBufferSlice dynamicTransform = RenderSystem.getDynamicUniforms().writeTransform(
                    viewRotation, new Vector4f(1, 1, 1, (float) overlay.opacity()), new Vector3f(), new Matrix4f()
                );
                try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "Portal overlay", mainTarget.getColorTextureView(), Optional.empty(),
                    mainTarget.getDepthTextureView(), OptionalDouble.empty()
                )) {
                    renderPass.setPipeline(PORTAL_OVERLAY_PIPELINE);
                    RenderSystem.bindDefaultUniforms(renderPass);
                    renderPass.setUniform("DynamicTransforms", dynamicTransform);
                    renderPass.bindTexture("Sampler0", texture.getTextureView(), placeholderSampler);
                    renderPass.setVertexBuffer(0, vertexBuffer.slice());
                    renderPass.setIndexBuffer(indexBuffer, indices.type());
                    renderPass.drawIndexed(vertexCount, 1, 0, 0, 0);
                }
            }
        }
    }

    /** @return 0 when the placeholder is fully opaque, 1 when the portal view is fully visible */
    private static float updatePlaceholder(PortalView view, float deltaSeconds) {
        PlaceholderState state = placeholderStates.computeIfAbsent(view.portal.getUUID(), k -> new PlaceholderState());
        state.lastSeenFrame = frameCounter;
        if (state.fade < 1) {
            state.waitedSeconds += deltaSeconds;
            if (view.ready) {
                state.readyFrames++;
            }
            else {
                state.readyFrames = 0;
            }
            // reveal when the terrain has been built for a few frames (or after a maximum wait, so it never sticks)
            boolean reveal = state.readyFrames >= PLACEHOLDER_READY_FRAMES
                || state.waitedSeconds > PLACEHOLDER_MAX_WAIT_SECONDS;
            if (reveal) {
                state.fade = java.lang.Math.min(1.0f, state.fade + deltaSeconds / PLACEHOLDER_FADE_SECONDS);
            }
        }
        return state.fade;
    }

    // views rendered this frame (waiting to be drawn into the main target)
    private static final List<PortalView> views = new ArrayList<>();
    private static final Deque<TextureTarget> targetPool = new ArrayDeque<>();
    private static final List<GpuBuffer> buffersToClose = new ArrayList<>();
    private static GpuSampler sampler;

    private static RenderTarget activeTarget = null;
    private static boolean isRenderingViews = false;

    // The oblique projection clips everything on the camera side of the portal plane, which would also cut the sky
    // dome above/around the camera. So the sky is drawn with the normal projection, then an extra frame pass
    // (MixinLevelRenderer_Portal) switches to the oblique one for terrain, entities, clouds, ...
    private static GpuBufferSlice pendingObliqueSlice = null;
    private static Matrix4f pendingNormalProjection = null;

    public static GpuBufferSlice getPendingObliqueSlice() {
        return pendingObliqueSlice;
    }

    /**
     * When not null, {@code GameRenderer.mainRenderTarget()} returns this instead of the real main target.
     */
    public static RenderTarget getActiveTargetOverride() {
        return activeTarget;
    }

    public static boolean isRenderingViews() {
        return isRenderingViews;
    }

    public static void cleanUp() {
        endFrame();
        cloudRendererPool.forEach(net.minecraft.client.renderer.CloudRenderer::close);
        cloudRendererPool.clear();
        cloudRenderersUsed = 0;
        fogRendererPool.forEach(FogRenderer::close);
        fogRendererPool.clear();
        fogRenderersUsed = 0;
        renderersUsed.clear();
        cloudTextureData = null;
        lightmaps.values().forEach(net.minecraft.client.renderer.Lightmap::close);
        lightmaps.clear();
        lightmapStates.clear();
        lightmapFrame.clear();
        for (TextureTarget target : targetPool) {
            target.destroyBuffers();
        }
        targetPool.clear();
        if (placeholderSampler != null) {
            placeholderSampler.close();
            placeholderSampler = null;
        }
        placeholderStates.clear();
        if (sampler != null) {
            sampler.close();
            sampler = null;
        }
    }

    private static void endFrame() {
        for (PortalView view : views) {
            targetPool.push(view.target);
        }
        views.clear();
        for (GpuBuffer buffer : buffersToClose) {
            buffer.close();
        }
        buffersToClose.clear();
    }

    private static TextureTarget acquireTarget(int width, int height) {
        while (!targetPool.isEmpty()) {
            TextureTarget target = targetPool.pop();
            if (target.width == width && target.height == height) {
                return target;
            }
            target.destroyBuffers();
        }
        return new TextureTarget("Portal view", width, height, true, GpuFormat.RGBA8_UNORM);
    }

    /**
     * Called at the beginning of {@code GameRenderer.extract}, when the level is going to be rendered.
     * It must run before the main level extraction because every extraction overwrites the shared
     * level render state.
     */
    public static void onBeforeExtract(GameRenderer gameRenderer, DeltaTracker deltaTracker) {
        endFrame();
        frameCounter++;
        endFrameOfViewResources();
        // per-frame portal counters (RenderStates.getRenderedPortalNum() gates particle extraction in views)
        if (!isRenderingViews) {
            RenderStates.onFrameStart();
        }

        if (isRenderingViews || client.level == null || client.player == null) {
            return;
        }
        if (!ClientWorldLoader.getIsInitialized()) {
            try {
                ClientWorldLoader.initializeIfNeeded();
            }
            catch (Exception e) {
                return;
            }
        }
        if (IPGlobal.maxPortalLayer == 0) {
            return;
        }

        Camera camera = gameRenderer.mainCamera();
        if (!camera.isInitialized()) {
            return;
        }

        Vec3 cameraPos = camera.position();

        List<Portal> portals = collectPortalsToRender(camera, cameraPos);
        if (portals.isEmpty()) {
            return;
        }

        isRenderingViews = true;
        try {
            Profiler.get().push("imm_ptl_portal_views");
            for (Portal portal : portals) {
                try {
                    renderPortalView(gameRenderer, deltaTracker, camera, portal);
                }
                catch (Throwable e) {
                    LOGGER.error("Failed to render portal view {}", portal, e);
                    activeTarget = null;
                    activeLightmap = null;
                    // the failed LevelRenderer.render left vanilla's shared feature frame open, which would crash the
                    // next render ("PreparedFrame already in use"): release it
                    try {
                        ((qouteall.imm_ptl.core.mixin.client.render.IEFeatureRenderDispatcher)
                            gameRenderer.featureRenderDispatcher()).ip_getPreparedFrame().close();
                    }
                    catch (Throwable ignored) {
                    }
                }
            }
            Profiler.get().pop();
        }
        finally {
            isRenderingViews = false;
            activeTarget = null;
            activeLightmap = null;
        }
    }

    private static List<Portal> collectPortalsToRender(Camera camera, Vec3 cameraPos) {
        double range = PortalRenderer.getRenderRange();

        List<Portal> result = new ArrayList<>();
        CHelper.getClientNearbyPortals(range).forEach(portal -> {
            if (!portal.isPortalValid()) {
                return;
            }
            if (!portal.isVisible()) {
                return;
            }
            if (!portal.isRoughlyVisibleTo(cameraPos)) {
                return;
            }
            double distance = portal.getDistanceToNearestPointInPortal(cameraPos);
            if (distance > range) {
                return;
            }
            if (distance > 0.1) {
                Frustum frustum = camera.getCullFrustum();
                if (!frustum.isVisible(portal.getThinBoundingBox())) {
                    return;
                }
            }
            if (!PortalRenderer.PORTAL_RENDERING_PREDICATE.invoker().test(portal)) {
                return;
            }
            result.add(portal);
        });

        result.sort(Comparator.comparingDouble(p -> p.getDistanceToNearestPointInPortal(cameraPos)));

        if (result.size() > MAX_VIEWS) {
            return new ArrayList<>(result.subList(0, MAX_VIEWS));
        }
        return result;
    }

    private static void renderPortalView(
        GameRenderer gameRenderer, DeltaTracker deltaTracker, Camera camera, Portal portal
    ) {
        ClientLevel destWorld = ClientWorldLoader.getOptionalWorld(portal.getDestDim());
        if (destWorld == null) {
            return;
        }
        LevelExtractor destExtractor = ClientWorldLoader.WORLD_EXTRACTOR_MAP.get(portal.getDestDim());
        LevelRenderer destRenderer = ClientWorldLoader.getWorldRenderer(portal.getDestDim());
        if (destExtractor == null) {
            return;
        }

        RenderTarget mainTarget = gameRenderer.mainRenderTarget();
        TextureTarget target = acquireTarget(mainTarget.width, mainTarget.height);

        Vec3 cameraPos = camera.position();

        // view matrix of the virtual camera: V_B = V_A * M
        Matrix4f viewA = camera.getViewRotationMatrix(new Matrix4f());
        Matrix4f transformation = PortalRenderer.getPortalTransformation(portal);
        Matrix4f viewB = transformation == null ? new Matrix4f(viewA) : viewA.mul(transformation, new Matrix4f());

        Vec3 newCameraPos = portal.transformPoint(cameraPos);

        // the projection of the main camera
        Matrix4f projection = camera.getViewRotationProjectionMatrix(new Matrix4f());
        Matrix4f viewRotInverse = viewA.invert(new Matrix4f());
        projection.mul(viewRotInverse); // projection = (P * V) * V^-1

        // the clip plane: the portal's destination plane, only things behind it (in the content direction) are kept
        Vec3 contentDir = portal.getContentDirection();
        Vec3 planePoint = portal.getDestPos().subtract(newCameraPos);
        Vector3f nEye = viewB.transformDirection(
            new Vector3f((float) contentDir.x, (float) contentDir.y, (float) contentDir.z)
        );
        Vector3f pEye = viewB.transformPosition(
            new Vector3f((float) planePoint.x, (float) planePoint.y, (float) planePoint.z)
        );
        float d = -nEye.dot(pEye);
        boolean zZeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        // When the camera is almost on the clip plane the oblique frustum degenerates (everything gets clipped, the
        // view turns into a solid colour). Close to the pane only a sliver of the destination lies in front of the
        // destination plane, so the plain projection is used there.
        boolean cameraNearPlane = java.lang.Math.abs(portal.getDistanceToPlane(cameraPos)) < 0.3;
        Matrix4f obliqueProjection = cameraNearPlane
            ? new Matrix4f(projection)
            : ObliqueProjection.apply(projection, nEye.x, nEye.y, nEye.z, d, zZeroToOne);
        pendingNormalProjection = new Matrix4f(projection);

        // culling frustum (non-reversed projection)
        Matrix4f cullProjection = new Matrix4f().perspective(
            (float) java.lang.Math.toRadians(camera.getFov()),
            (float) mainTarget.width / mainTarget.height,
            0.05f, 1024.0f, zZeroToOne
        );
        Frustum frustum = new Frustum(viewB, cullProjection);
        frustum.prepare(newCameraPos.x, newCameraPos.y, newCameraPos.z);

        Quaternionf rotationB = viewB.getNormalizedRotation(new Quaternionf()).conjugate();

        PortalRendering.pushPortalLayer(portal);
        try {
            PortalRendering.onBeginPortalWorldRendering();

            ClientWorldLoader.withSwitchedWorld(destWorld, () -> {
                IEPortalCamera portalCamera = (IEPortalCamera) camera;
                portalCamera.ip_beginPortalOverride(destWorld, newCameraPos, rotationB, frustum);
                try {
                    renderLevelInto(
                        gameRenderer, deltaTracker, camera, destWorld, destExtractor, destRenderer,
                        target, viewB, obliqueProjection, frustum, newCameraPos
                    );
                }
                finally {
                    portalCamera.ip_endPortalOverride();
                    activeTarget = null;
                    activeLightmap = null;
                }
            });

            PortalRendering.onEndPortalWorldRendering();
        }
        finally {
            PortalRendering.popPortalLayer();
        }

        PortalView portalView = new PortalView(portal, target);
        portalView.ready = destRenderer.hasRenderedAllSections();
        views.add(portalView);
        
        if (DUMP_VIEWS) {
            dumpCounter++;
            if (dumpCounter % 40 == 5) {
                LOGGER.info(
                    "Portal view debug: dest {} loadedChunks={} camPos={} sections={}",
                    portal.getDestDim().identifier(), destWorld.getChunkSource().getLoadedChunksCount(),
                    newCameraPos, destRenderer.sectionRenderDispatcher() == null ? "null dispatcher" : "ok"
                );
                LOGGER.info(
                    "Portal view debug 2: viewArea={} visibleSections={} compiledAllSections={} skyRenderer={} fogColor={}",
                    destRenderer.viewArea() != null, destRenderer.visibleSections().size(),
                    destRenderer.hasRenderedAllSections(), destRenderer.skyRenderer() != null,
                    lastFogColor
                );
            }
            if (dumpCounter % 120 == 5) {
                net.minecraft.client.Screenshot.grab(
                    client.gameDirectory, "portalview_" + dumpCounter + ".png", target, 1,
                    message -> LOGGER.info("Dumped portal view: {}", message.getString())
                );
            }
        }
    }

    private static void renderLevelInto(
        GameRenderer gameRenderer, DeltaTracker deltaTracker, Camera camera, ClientLevel destWorld,
        LevelExtractor destExtractor, LevelRenderer destRenderer, TextureTarget target,
        Matrix4f viewMatrix, Matrix4f projectionMatrix, Frustum frustum, Vec3 cameraPos
    ) {
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        float cameraEntityPartialTicks = camera.getCameraEntityPartialTicks(deltaTracker);

        // fill the camera state
        CameraRenderState cameraState = gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        camera.extractRenderState(cameraState, cameraEntityPartialTicks);
        // cave/occlusion culling assumes the camera is in open space. The virtual camera of a portal can be
        // inside terrain relative to the destination, which culls the terrain wrongly (sky visible through ground)
        cameraState.smartCull = PortalRendering.shouldEnableSodiumCaveCulling();
        cameraState.pos = cameraPos;
        cameraState.viewRotationMatrix.set(viewMatrix);
        cameraState.projectionMatrix.set(projectionMatrix);
        cameraState.cullFrustum.set(frustum);
        cameraState.fogType = camera.getFluidInCamera();
        FogRenderer fogRenderer = ((qouteall.imm_ptl.core.ducks.IEGameRendererPortal) gameRenderer).ip_getFogRenderer();
        cameraState.fogData = fogRenderer.setupFog(
            camera, client.options.getEffectiveRenderDistance(), deltaTracker, 0.0f, destWorld
        );

        lastFogColor = cameraState.fogData.color.toString();
        // the lightmap of the destination dimension: one Lightmap object per dimension, rendered once per frame
        // (the camera already has the destination's environment attributes now)
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> destDim = destWorld.dimension();
        net.minecraft.client.renderer.Lightmap destLightmap =
            lightmaps.computeIfAbsent(destDim, k -> new net.minecraft.client.renderer.Lightmap());
        if (lightmapFrame.getOrDefault(destDim, -1L) != frameCounter) {
            lightmapFrame.put(destDim, frameCounter);
            ((qouteall.imm_ptl.core.ducks.IEGameRendererPortal) gameRenderer).ip_renderLightmap(
                destLightmap,
                lightmapStates.computeIfAbsent(destDim, k -> new net.minecraft.client.renderer.state.LightmapRenderState())
            );
        }
        activeLightmap = destLightmap.getTextureView();
        destExtractor.extract(deltaTracker, camera, partialTick);

        // the extraction may have updated the camera state
        cameraState = gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        cameraState.viewRotationMatrix.set(viewMatrix);
        cameraState.projectionMatrix.set(projectionMatrix);

        // vanilla decides the dark lower sky disc by the PLAYER's eye height in the destination level
        // (SkyRenderer.shouldRenderDarkDisc); use the virtual camera instead
        net.minecraft.client.renderer.state.level.SkyRenderState skyState =
            gameRenderer.gameRenderState().levelRenderState.skyRenderState;
        skyState.shouldRenderDarkDisc =
            cameraPos.y - destWorld.getLevelData().getHorizonHeight(destWorld) < 0.0;

        // render
        activeTarget = target;

        // slot 0: normal projection (sky), slot 1: oblique projection (set by an extra frame pass after the sky)
        qouteall.imm_ptl.core.ducks.IEGameRendererPortal portalAccess =
            (qouteall.imm_ptl.core.ducks.IEGameRendererPortal) gameRenderer;
        GpuBufferSlice normalSlice = portalAccess.ip_getPortalProjectionBuffer(new Matrix4f(pendingNormalProjection), 0);
        pendingObliqueSlice = portalAccess.ip_getPortalProjectionBuffer(new Matrix4f(cameraState.projectionMatrix), 1);
        RenderSystem.setProjectionMatrix(normalSlice, com.mojang.blaze3d.ProjectionType.PERSPECTIVE);
        // vanilla terrain.vsh positions sections relative to Globals.CameraBlockPos/CameraOffset, which still hold the
        // previous frame's MAIN camera here (written in GameRenderer.render). Not needed with Sodium, harmless with it.
        ((qouteall.imm_ptl.core.ducks.IEGameRendererPortal) gameRenderer).ip_updateGlobalSettingsForView(deltaTracker, cameraPos);

        // own fog UBO per view (see fogRendererPool). fogRenderer.setupFog above must stay on the main FogRenderer:
        // Sodium reads its fog parameters from there in LevelExtractor.extract
        FogRenderer viewFogRenderer = acquireFogRenderer();
        viewFogRenderer.updateBuffer(cameraState.fogData);
        GpuBufferSlice terrainFog = viewFogRenderer.getBuffer(FogRenderer.FogMode.WORLD);

        // Sodium draws terrain with its own captured projection (set in GameRenderer.renderLevel, i.e. the previous
        // frame's main projection at this point): make it use the oblique projection of this view too
        Matrix4f sodiumProjection = SodiumProjectionCompat.getCapturedProjection(gameRenderer);
        Matrix4f savedSodiumProjection = sodiumProjection == null ? null : new Matrix4f(sodiumProjection);
        if (sodiumProjection != null) {
            sodiumProjection.set(cameraState.projectionMatrix);
        }

        renderersUsed.add(destRenderer);

        qouteall.imm_ptl.core.ducks.IELevelRendererPortal rendererAccess =
            (qouteall.imm_ptl.core.ducks.IELevelRendererPortal) destRenderer;
        net.minecraft.client.renderer.CloudRenderer originalClouds = rendererAccess.ip_getCloudRenderer();
        rendererAccess.ip_setCloudRenderer(acquireCloudRenderer());
        try {
            destRenderer.render(
                ((qouteall.imm_ptl.core.ducks.IEGameRendererPortal) gameRenderer).ip_getResourcePool(),
                deltaTracker, false, cameraState, cameraState.viewRotationMatrix,
                terrainFog, cameraState.fogData.color, true
            );
        }
        finally {
            pendingObliqueSlice = null;
            rendererAccess.ip_setCloudRenderer(originalClouds);
            if (sodiumProjection != null) {
                sodiumProjection.set(savedSodiumProjection);
            }
        }
    }

    /**
     * Called by the frame graph of the main LevelRenderer after the main pass.
     */
    public static void drawViews(RenderTarget mainTarget, Matrix4f viewRotation, Vec3 cameraPos) {
        if (views.isEmpty()) {
            return;
        }

        if (sampler == null) {
            sampler = RenderSystem.getDevice().createSampler(
                AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                FilterMode.NEAREST, FilterMode.NEAREST, 1, OptionalDouble.empty()
            );
        }

        long now = System.nanoTime();
        float deltaSeconds = placeholderLastNanos == 0 ? 0 : java.lang.Math.min(0.1f, (now - placeholderLastNanos) / 1.0e9f);
        placeholderLastNanos = now;

        for (PortalView view : views) {
            drawView(view, mainTarget, viewRotation, cameraPos);
            drawOverlay(view, mainTarget, viewRotation, cameraPos);
            if (!usesLoadingPlaceholder(view.portal)) {
                continue;
            }
            float fade = updatePlaceholder(view, deltaSeconds);
            if (fade < 1) {
                drawPlaceholder(view, mainTarget, viewRotation, cameraPos, 1 - fade);
            }
        }
        placeholderStates.values().removeIf(state -> frameCounter - state.lastSeenFrame > 600);
    }

    private static void drawPlaceholder(
        PortalView view, RenderTarget mainTarget, Matrix4f viewRotation, Vec3 cameraPos, float alpha
    ) {
        Portal portal = view.portal;
        net.minecraft.client.renderer.texture.AbstractTexture texture = client.getTextureManager().getTexture(PLACEHOLDER_TEXTURE);
        if (placeholderSampler == null) {
            placeholderSampler = RenderSystem.getDevice().createSampler(
                AddressMode.REPEAT, AddressMode.REPEAT,
                FilterMode.NEAREST, FilterMode.NEAREST, 1, OptionalDouble.empty()
            );
        }

        Vec3 originRel = portal.getOriginPos().subtract(cameraPos);
        Vec3 axisW = portal.getAxisW();
        Vec3 axisH = portal.getAxisH();
        double width = portal.getWidth();
        double height = portal.getHeight();

        try (ByteBufferBuilder byteBuffer = new ByteBufferBuilder(4096)) {
            BufferBuilder builder = new BufferBuilder(byteBuffer, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_TEX);
            portal.renderViewAreaMesh(
                originRel,
                (p0x, p0y, p0z, p1x, p1y, p1z, p2x, p2y, p2z) -> {
                    double[][] ps = {{p0x, p0y, p0z}, {p1x, p1y, p1z}, {p2x, p2y, p2z}};
                    for (double[] p : ps) {
                        // texture coordinates: one texture repeat per block, in the plane of the portal
                        double rx = p[0] - originRel.x;
                        double ry = p[1] - originRel.y;
                        double rz = p[2] - originRel.z;
                        float u = (float) (rx * axisW.x + ry * axisW.y + rz * axisW.z + width / 2);
                        float v = (float) (rx * axisH.x + ry * axisH.y + rz * axisH.z + height / 2);
                        builder.addVertex((float) p[0], (float) p[1], (float) p[2]).setUv(u, 1 - v);
                    }
                }
            );
            MeshData meshData = builder.build();
            if (meshData == null) {
                return;
            }

            try (meshData) {
                int vertexCount = meshData.drawState().vertexCount();
                GpuBuffer vertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "Portal placeholder mesh", com.mojang.blaze3d.buffers.GpuBuffer.USAGE_VERTEX, meshData.vertexBuffer()
                );
                buffersToClose.add(vertexBuffer);

                RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.TRIANGLES);
                GpuBuffer indexBuffer = indices.getBuffer(vertexCount);

                GpuBufferSlice dynamicTransform = RenderSystem.getDynamicUniforms().writeTransform(
                    viewRotation, new Vector4f(1, 1, 1, alpha), new Vector3f(), new Matrix4f()
                );

                try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "Portal placeholder", mainTarget.getColorTextureView(), Optional.empty(),
                    mainTarget.getDepthTextureView(), OptionalDouble.empty()
                )) {
                    renderPass.setPipeline(PORTAL_PLACEHOLDER_PIPELINE);
                    RenderSystem.bindDefaultUniforms(renderPass);
                    renderPass.setUniform("DynamicTransforms", dynamicTransform);
                    renderPass.bindTexture("Sampler0", texture.getTextureView(), placeholderSampler);
                    renderPass.setVertexBuffer(0, vertexBuffer.slice());
                    renderPass.setIndexBuffer(indexBuffer, indices.type());
                    renderPass.drawIndexed(vertexCount, 1, 0, 0, 0);
                }
            }
        }
    }

    private static void drawView(PortalView view, RenderTarget mainTarget, Matrix4f viewRotation, Vec3 cameraPos) {
        Portal portal = view.portal;

        MeshData meshData;
        try (ByteBufferBuilder byteBuffer = new ByteBufferBuilder(4096)) {
            BufferBuilder builder = new BufferBuilder(byteBuffer, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION);
            portal.renderViewAreaMesh(
                portal.getOriginPos().subtract(cameraPos),
                (p0x, p0y, p0z, p1x, p1y, p1z, p2x, p2y, p2z) -> {
                    builder.addVertex((float) p0x, (float) p0y, (float) p0z);
                    builder.addVertex((float) p1x, (float) p1y, (float) p1z);
                    builder.addVertex((float) p2x, (float) p2y, (float) p2z);
                }
            );
            // Right before the teleport the camera is closer to the pane than the near plane, which cuts a hole into
            // the pane (the real world behind it shows). Cover the screen just beyond the near plane instead.
            double planeDistance = java.lang.Math.abs(portal.getDistanceToPlane(cameraPos));
            if (planeDistance < 0.25
                && portal.getDistanceToNearestPointInPortal(cameraPos) < planeDistance + 0.02
            ) {
                Matrix4f inverseView = viewRotation.invert(new Matrix4f());
                float z = -0.07f;
                float e = 1.0f;
                Vector3f c0 = inverseView.transformPosition(new Vector3f(-e, -e, z));
                Vector3f c1 = inverseView.transformPosition(new Vector3f(e, -e, z));
                Vector3f c2 = inverseView.transformPosition(new Vector3f(e, e, z));
                Vector3f c3 = inverseView.transformPosition(new Vector3f(-e, e, z));
                builder.addVertex(c0.x, c0.y, c0.z);
                builder.addVertex(c1.x, c1.y, c1.z);
                builder.addVertex(c2.x, c2.y, c2.z);
                builder.addVertex(c0.x, c0.y, c0.z);
                builder.addVertex(c2.x, c2.y, c2.z);
                builder.addVertex(c3.x, c3.y, c3.z);
            }
            meshData = builder.build();
            if (meshData == null) {
                return;
            }

            try (meshData) {
                int vertexCount = meshData.drawState().vertexCount();
                GpuBuffer vertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "Portal view mesh", com.mojang.blaze3d.buffers.GpuBuffer.USAGE_VERTEX, meshData.vertexBuffer()
                );
                buffersToClose.add(vertexBuffer);

                RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.TRIANGLES);
                GpuBuffer indexBuffer = indices.getBuffer(vertexCount);

                GpuBufferSlice dynamicTransform = RenderSystem.getDynamicUniforms().writeTransform(
                    viewRotation, new Vector4f(1, 1, 1, 1), new Vector3f(), new Matrix4f()
                );

                try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "Portal view", mainTarget.getColorTextureView(), Optional.empty(),
                    mainTarget.getDepthTextureView(), OptionalDouble.empty()
                )) {
                    renderPass.setPipeline(PORTAL_VIEW_PIPELINE);
                    RenderSystem.bindDefaultUniforms(renderPass);
                    renderPass.setUniform("DynamicTransforms", dynamicTransform);
                    renderPass.bindTexture("Sampler0", view.target.getColorTextureView(), sampler);
                    renderPass.setVertexBuffer(0, vertexBuffer.slice());
                    renderPass.setIndexBuffer(indexBuffer, indices.type());
                    renderPass.drawIndexed(vertexCount, 1, 0, 0, 0);
                }
            }
        }
    }
}

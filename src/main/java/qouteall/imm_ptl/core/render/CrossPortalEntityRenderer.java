package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.collision.PortalCollisionEntry;
import qouteall.imm_ptl.core.collision.PortalCollisionHandler;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.ducks.IEEntityRenderState;
import qouteall.imm_ptl.core.portal.Mirror;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.q_misc_util.my_util.DQuaternion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Entities that are partly through a portal (26.2 extract/render split).
 * <p>
 * The part of an entity that went through a portal must appear on the other side. Whenever a LevelExtractor extracts
 * the entities of a level (main view, portal views, nested views: {@code MixinLevelExtractor_CrossPortal}), every
 * entity (of any client level) that collides with a portal leading INTO that level and is partly behind the portal
 * plane gets an extra EntityRenderState: the entity's normal render state (interpolated pose of its own level),
 * moved to the transformed position, lit by the destination level, without shadow/leash, and with the portal's
 * rotation and scale applied around the entity anchor when it is submitted ({@code MixinEntityRenderDispatcher_CrossPortal}).
 * <ul>
 * <li>Seen through the portal: the projection is in the destination view, the oblique clip plane of the view keeps
 * only the part past the portal plane; the part still in front is drawn normally in the main view.</li>
 * <li>Reverse: an entity of the destination going through the reverse portal gets a projection in the main view; the
 * part that is not through yet is behind the pane, which covers it.</li>
 * </ul>
 * The source entity is never modified, nothing is extracted twice in one view (one projection per entity per
 * extraction). Mirrors are skipped (not passable). Disabled with an active shader pack and by the config option
 * correctCrossPortalEntityRendering. Any error disables the feature for the session (logged once).
 * Known limit: the main view cannot clip, so the projection's part that is not through yet can be seen from behind
 * the frame (the same holds for the real entity's part that is already through).
 */
@Environment(EnvType.CLIENT)
public class CrossPortalEntityRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrossPortalEntityRenderer.class);

    // there is no weak hash set
    private static final WeakHashMap<Entity, Object> collidedEntities = new WeakHashMap<>();

    private static boolean disabled = false;

    public static void init() {
        IPGlobal.POST_CLIENT_TICK_EVENT.register(CrossPortalEntityRenderer::onClientTick);

        IPCGlobal.CLIENT_CLEANUP_EVENT.register(CrossPortalEntityRenderer::cleanUp);

        ClientWorldLoader.CLIENT_DIMENSION_DYNAMIC_REMOVE_EVENT.register(dim -> cleanUp());
    }

    private static void cleanUp() {
        collidedEntities.clear();
    }

    private static void onClientTick() {
        try {
            collidedEntities.entrySet().removeIf(entry -> {
                Entity entity = entry.getKey();
                return entity == null || entity.isRemoved() || !((IEEntity) entity).ip_isCollidingWithPortal();
            });
        }
        catch (Throwable e) {
            disable(e);
            collidedEntities.clear();
        }
    }

    public static void onEntityTickClient(Entity entity) {
        if (disabled || entity instanceof Portal) {
            return;
        }

        if (((IEEntity) entity).ip_isCollidingWithPortal()) {
            collidedEntities.put(entity, null);
        }
    }

    private static void disable(Throwable e) {
        if (!disabled) {
            disabled = true;
            LOGGER.error("Cross-portal entity rendering failed and is disabled for this session", e);
        }
    }

    private static boolean isEnabled() {
        if (disabled || !IPGlobal.correctCrossPortalEntityRendering) {
            return false;
        }
        try {
            // shader packs: the views are clipped by the pack's programs, keep it simple and off
            return !IrisInterface.invoker.isShaders();
        }
        catch (Throwable e) {
            return false;
        }
    }

    /**
     * Called at the end of LevelExtractor.extractVisibleEntities for every view.
     *
     * @param level the level being extracted (the destination of the projected entities)
     */
    public static void extractProjections(
        ClientLevel level, Camera camera, Frustum frustum, DeltaTracker deltaTracker,
        EntityRenderDispatcher dispatcher, LevelRenderState output
    ) {
        if (collidedEntities.isEmpty() || !isEnabled()) {
            return;
        }
        try {
            extractProjectionsInner(level, camera, frustum, deltaTracker, dispatcher, output);
        }
        catch (Throwable e) {
            disable(e);
        }
        finally {
            output.lastEntityRenderStateCount = output.entityRenderStates.size();
        }
    }

    private static void extractProjectionsInner(
        ClientLevel level, Camera camera, Frustum frustum, DeltaTracker deltaTracker,
        EntityRenderDispatcher dispatcher, LevelRenderState output
    ) {
        ResourceKey<Level> dim = level.dimension();
        Set<Entity> done = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Entity> entities = new ArrayList<>(collidedEntities.keySet());

        for (Entity entity : entities) {
            if (entity == null || entity.isRemoved() || entity instanceof Portal) {
                continue;
            }
            PortalCollisionHandler collisionHandler = ((IEEntity) entity).ip_getPortalCollisionHandler();
            if (collisionHandler == null) {
                continue;
            }
            float partialTick = getPartialTick(entity, deltaTracker);
            for (PortalCollisionEntry collision : new ArrayList<>(collisionHandler.portalCollisions)) {
                Portal portal = collision.portal;
                if (!isProjectingPortal(portal, entity, dim)) {
                    continue;
                }
                if (!isPartlyThrough(entity, portal, partialTick)) {
                    continue;
                }
                addProjection(entity, portal, partialTick, level, camera, frustum, dispatcher, output, done);
                // riders are not ticked as non-passengers (no collision tracking): project them with their vehicle
                for (Entity passenger : entity.getIndirectPassengers()) {
                    if (passenger != entity && !(passenger instanceof Portal)) {
                        addProjection(
                            passenger, portal, getPartialTick(passenger, deltaTracker),
                            level, camera, frustum, dispatcher, output, done
                        );
                    }
                }
                // one projection per entity and view
                break;
            }
        }
    }

    private static float getPartialTick(Entity entity, DeltaTracker deltaTracker) {
        return deltaTracker.getGameTimeDeltaPartialTick(!entity.level().tickRateManager().isEntityFrozen(entity));
    }

    private static boolean isProjectingPortal(Portal portal, Entity entity, ResourceKey<Level> dim) {
        return portal != null
            && !(portal instanceof Mirror)
            && !portal.isRemoved()
            && portal.level() == entity.level()
            && portal.getDestDim() == dim
            && portal.isPortalValid();
    }

    /**
     * The entity (interpolated) has its anchor in front of the portal plane and some part behind it.
     * The anchor test picks the right face of a two-sided frame (the back face also collides sometimes).
     */
    private static boolean isPartlyThrough(Entity entity, Portal portal, float partialTick) {
        Vec3 pos = entity.getPosition(partialTick);
        AABB box = entity.getBoundingBox().move(pos.subtract(entity.position()));
        Vec3 center = box.getCenter();
        if (portal.getDistanceToPlane(center) < 0) {
            return false;
        }
        double minDistance = Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            Vec3 corner = new Vec3(
                (i & 1) == 0 ? box.minX : box.maxX,
                (i & 2) == 0 ? box.minY : box.maxY,
                (i & 4) == 0 ? box.minZ : box.maxZ
            );
            minDistance = Math.min(minDistance, portal.getDistanceToPlane(corner));
        }
        return minDistance < 0;
    }

    private static AABB transformBox(Portal portal, AABB box) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            Vec3 corner = portal.transformPoint(new Vec3(
                (i & 1) == 0 ? box.minX : box.maxX,
                (i & 2) == 0 ? box.minY : box.maxY,
                (i & 4) == 0 ? box.minZ : box.maxZ
            ));
            minX = Math.min(minX, corner.x);
            minY = Math.min(minY, corner.y);
            minZ = Math.min(minZ, corner.z);
            maxX = Math.max(maxX, corner.x);
            maxY = Math.max(maxY, corner.y);
            maxZ = Math.max(maxZ, corner.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static void addProjection(
        Entity entity, Portal portal, float partialTick, ClientLevel level, Camera camera, Frustum frustum,
        EntityRenderDispatcher dispatcher, LevelRenderState output, Set<Entity> done
    ) {
        if (entity.isRemoved() || !done.add(entity)) {
            return;
        }
        // first person: the virtual camera would be inside the projection's head
        if (entity == camera.entity() && !camera.isDetached()) {
            return;
        }
        // vanilla never renders another LocalPlayer than the camera entity
        if (entity instanceof LocalPlayer && entity != camera.entity()) {
            return;
        }

        Vec3 pos = entity.getPosition(partialTick);
        AABB box = entity.getBoundingBox().move(pos.subtract(entity.position()));
        if (box.hasNaN()) {
            return;
        }
        if (!frustum.isVisible(transformBox(portal, box).inflate(0.5))) {
            return;
        }

        // the normal render state of the entity, with its interpolated pose in its own level
        EntityRenderState state = dispatcher.extractEntity(entity, partialTick);

        Vec3 newPos = portal.transformPoint(new Vec3(state.x, state.y, state.z));
        state.x = newPos.x;
        state.y = newPos.y;
        state.z = newPos.z;
        state.distanceToCameraSq = newPos.distanceToSqr(camera.position());
        if (state.distanceToCameraSq > 64.0 * 64.0) {
            state.nameTag = null;
            state.scoreText = null;
        }
        // shadows and leashes were computed in the source level
        state.shadowPieces.clear();
        state.shadowRadius = 0;
        state.leashStates = null;
        // lighting of the destination
        BlockPos lightPos = BlockPos.containing(portal.transformPoint(entity.getLightProbePosition(partialTick)));
        int blockLight = entity.isOnFire() ? 15 : level.getBrightness(LightLayer.BLOCK, lightPos);
        int skyLight = level.getBrightness(LightLayer.SKY, lightPos);
        state.lightCoords = LightCoordsUtil.pack(blockLight, skyLight);

        DQuaternion rotation = portal.getRotation();
        double scale = portal.getScaling();
        if (rotation != null || scale != 1.0) {
            ((IEEntityRenderState) state).ip_setCrossPortalTransform(
                rotation == null ? null : rotation.toMcQuaternion(), (float) scale
            );
        }

        output.entityRenderStates.add(state);
    }

    /**
     * Called in EntityRenderDispatcher.submit after the pose is moved to the entity anchor.
     */
    public static void applyProjectionTransform(EntityRenderState state, PoseStack poseStack) {
        try {
            IEEntityRenderState access = (IEEntityRenderState) state;
            if (!access.ip_hasCrossPortalTransform()) {
                return;
            }
            Quaternionf rotation = access.ip_getCrossPortalRotation();
            if (rotation != null) {
                poseStack.mulPose(rotation);
            }
            float scale = access.ip_getCrossPortalScale();
            if (scale != 1.0f) {
                poseStack.scale(scale, scale, scale);
            }
        }
        catch (Throwable e) {
            disable(e);
        }
    }
}

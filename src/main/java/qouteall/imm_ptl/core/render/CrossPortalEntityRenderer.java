package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.joml.Matrix4f;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.collision.PortalCollisionEntry;
import qouteall.imm_ptl.core.collision.PortalCollisionHandler;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.ducks.IEWorldRenderer;
import qouteall.imm_ptl.core.portal.Mirror;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalManipulation;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.imm_ptl.core.render.renderer.PortalRenderer;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.Plane;

import java.util.WeakHashMap;

@Environment(EnvType.CLIENT)
public class CrossPortalEntityRenderer {
    private static final Minecraft client = Minecraft.getInstance();
    
    //there is no weak hash set
    private static final WeakHashMap<Entity, Object> collidedEntities = new WeakHashMap<>();
    
    public static boolean isRenderingEntityNormally = false;
    
    public static boolean isRenderingEntityProjection = false;
    
    public static void init() {
        IPGlobal.POST_CLIENT_TICK_EVENT.register(CrossPortalEntityRenderer::onClientTick);
        
        IPCGlobal.CLIENT_CLEANUP_EVENT.register(CrossPortalEntityRenderer::cleanUp);
        
        ClientWorldLoader.CLIENT_DIMENSION_DYNAMIC_REMOVE_EVENT.register(dim -> cleanUp());
    }
    
    private static void cleanUp() {
        collidedEntities.clear();
    }
    
    private static void onClientTick() {
        collidedEntities.entrySet().removeIf(entry -> {
            Entity entity = entry.getKey();
            return entity.isRemoved() || !((IEEntity) entity).ip_isCollidingWithPortal();
        });
    }
    
    public static void onEntityTickClient(Entity entity) {
        if (entity instanceof Portal) {
            return;
        }
        
        if (((IEEntity) entity).ip_isCollidingWithPortal()) {
            collidedEntities.put(entity, null);
        }
    }
    
    public static void onBeginRenderingEntitiesAndBlockEntities(Matrix4f modelView) {
        isRenderingEntityNormally = true;
        
        if (PortalRendering.isRendering()) {
            FrontClipping.setupInnerClipping(
                PortalRendering.getActiveClippingPlane(),
                modelView, 0
            );
        }
    }
    
    private static boolean isCrossPortalRenderingEnabled() {
        if (IrisInterface.invoker.isIrisPresent()) {
            return false;
        }
        return IPGlobal.correctCrossPortalEntityRendering;
    }
    
    public static void onEndRenderingEntitiesAndBlockEntities(PoseStack matrixStack) {
        isRenderingEntityNormally = false;
        
        FrontClipping.disableClipping();
        
        if (!isCrossPortalRenderingEnabled()) {
            return;
        }
        
        renderEntityProjections(matrixStack);
    }
    
    public static void beforeRenderingEntity(Entity entity, PoseStack matrixStack) {
    }
    
    public static void afterRenderingEntity(Entity entity) {
    }
    
    //if an entity is in overworld but halfway through a nether portal
    //then it has a projection in nether
    private static void renderEntityProjections(PoseStack matrixStack) {
        if (!isCrossPortalRenderingEnabled()) {
            return;
        }
        
        ResourceKey<Level> clientDim = client.level.dimension();
        
        for (Entity entity : collidedEntities.keySet()) {
            PortalCollisionHandler collisionHandler = ((IEEntity) entity).ip_getPortalCollisionHandler();
            
            if (collisionHandler != null) {
                for (PortalCollisionEntry e : collisionHandler.portalCollisions) {
                    Portal collidingPortal = e.portal;
                    if (!(collidingPortal instanceof Mirror)) {
                        ResourceKey<Level> projectionDimension = collidingPortal.getDestDim();
                        if (clientDim == projectionDimension) {
                            renderProjectedEntity(entity, collidingPortal, matrixStack);
                        }
                    }
                }
            }
        }
    }
    
    public static boolean hasIntersection(
        Vec3 outerPlanePos, Vec3 outerPlaneNormal,
        Vec3 entityPos, Vec3 collidingPortalNormal
    ) {
        return entityPos.subtract(outerPlanePos).dot(outerPlaneNormal) > 0.01 &&
            outerPlanePos.subtract(entityPos).dot(collidingPortalNormal) > 0.01;
    }
    
    private static void renderProjectedEntity(
        Entity entity,
        Portal collidingPortal,
        PoseStack matrixStack
    ) {
    }
    
    private static void renderEntity(
        Entity entity,
        Portal transformingPortal,
        PoseStack matrixStack
    ) {
    }
    
    private static void setupEntityProjectionRenderingTransformation(
        Portal portal, PoseStack matrixStack,
        Vec3 entityPos, Vec3 entityLastTickPos, Vec3 cameraPos
    ) {
        if (portal.getScaling() == 1.0 && portal.getRotation() == null) {
            return;
        }
        
        Vec3 anchor = entityLastTickPos.lerp(entityPos, RenderStates.getPartialTick())
            .subtract(cameraPos);
        
        matrixStack.translate(anchor.x, anchor.y, anchor.z);
        
        float scaling = (float) portal.getScaling();
        matrixStack.scale(scaling, scaling, scaling);
        
        if (portal.getRotation() != null) {
            matrixStack.mulPose(portal.getRotation().toMcQuaternion());
        }
        
        matrixStack.translate(-anchor.x, -anchor.y, -anchor.z);
    }
    
    public static boolean shouldRenderPlayerDefault() {
        if (!IPGlobal.renderYourselfInPortal) {
            return false;
        }
        if (!WorldRenderInfo.isRendering()) {
            return false;
        }
        LocalPlayer player = client.player;
        assert player != null;
        
        if (PortalRendering.isRendering()) {
            Portal renderingPortal = PortalRendering.getRenderingPortal();
            if (renderingPortal instanceof Mirror) {
                // if the camera pos is too close to the mirror,
                // it will show the inside of the player head.
                // avoid rendering player in this case.
                float width = player.getBbWidth();
                if (renderingPortal.getDistanceToNearestPointInPortal(player.getEyePosition()) < width * 0.8) {
                    return false;
                }
            }
        }
        
        if (client.level == player.level()) {
            return true;
        }
        
        return false;
    }
    
    public static boolean shouldRenderEntityNow(Entity entity) {
        return false;
    }
    
    public static boolean shouldRenderPlayerNormally(Entity entity) {
        return false;
    }
    
    public static Vec3 getRenderingCameraPos(Entity entity) {
        if (entity instanceof LocalPlayer) {
            return RenderStates.originalPlayerPos.add(
                McHelper.getEyeOffset(entity)
            );
        }
        return entity.getEyePosition(RenderStates.getPartialTick());
    }
}

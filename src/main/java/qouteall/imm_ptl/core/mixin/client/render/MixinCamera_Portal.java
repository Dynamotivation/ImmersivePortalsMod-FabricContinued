package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import qouteall.imm_ptl.core.ducks.IEPortalCamera;

@Mixin(Camera.class)
public abstract class MixinCamera_Portal implements IEPortalCamera {
    @Shadow
    private @Nullable Level level;
    @Shadow
    private Vec3 position;
    @Shadow
    @Final
    private BlockPos.MutableBlockPos blockPosition;
    @Shadow
    @Final
    private Vector3f forwards;
    @Shadow
    @Final
    private Vector3f up;
    @Shadow
    @Final
    private Vector3f left;
    @Shadow
    private float xRot;
    @Shadow
    private float yRot;
    @Shadow
    @Final
    private Quaternionf rotation;
    @Shadow
    private Frustum cullFrustum;
    @Shadow
    private int matrixPropertiesDirty;
    // the probe caches the environment (fog, sky colour, ...) of the level the camera is in
    @Shadow
    @Mutable
    @Final
    private EnvironmentAttributeProbe attributeProbe;
    // a stack: portal views can be nested (a portal seen through a portal)
    @Unique
    private final java.util.ArrayDeque<qouteall.imm_ptl.core.render.portalview.CameraOverrideState> ip_overrideStack =
        new java.util.ArrayDeque<>();

    @Override
    public void ip_beginPortalOverride(ClientLevel newLevel, Vec3 pos, Quaternionf newRotation, Frustum newFrustum) {
        qouteall.imm_ptl.core.render.portalview.CameraOverrideState saved =
            new qouteall.imm_ptl.core.render.portalview.CameraOverrideState();
        saved.level = level;
        saved.position = position;
        saved.blockPos = blockPosition.immutable();
        saved.forwards.set(forwards);
        saved.up.set(up);
        saved.left.set(left);
        saved.xRot = xRot;
        saved.yRot = yRot;
        saved.rotation.set(rotation);
        saved.frustum = cullFrustum;
        saved.probe = attributeProbe;
        ip_overrideStack.push(saved);

        EnvironmentAttributeProbe newProbe = new EnvironmentAttributeProbe();
        newProbe.tick(newLevel, pos);
        attributeProbe = newProbe;

        level = newLevel;
        position = pos;
        blockPosition.set(pos.x, pos.y, pos.z);
        rotation.set(newRotation);
        new Vector3f(0, 0, -1).rotate(rotation, forwards);
        new Vector3f(0, 1, 0).rotate(rotation, up);
        new Vector3f(-1, 0, 0).rotate(rotation, left);
        // approximate euler angles, only used for things like chunk visibility update thresholds and billboards
        xRot = (float) java.lang.Math.toDegrees(java.lang.Math.asin(-forwards.y));
        yRot = (float) java.lang.Math.toDegrees(java.lang.Math.atan2(-forwards.x, forwards.z));
        cullFrustum = newFrustum;
        matrixPropertiesDirty |= 3;
    }

    @Override
    public void ip_endPortalOverride() {
        qouteall.imm_ptl.core.render.portalview.CameraOverrideState saved = ip_overrideStack.poll();
        if (saved == null) {
            return;
        }

        attributeProbe = saved.probe;
        level = saved.level;
        position = saved.position;
        blockPosition.set(saved.blockPos);
        forwards.set(saved.forwards);
        up.set(saved.up);
        left.set(saved.left);
        xRot = saved.xRot;
        yRot = saved.yRot;
        rotation.set(saved.rotation);
        cullFrustum = saved.frustum;
        matrixPropertiesDirty |= 3;
    }
}

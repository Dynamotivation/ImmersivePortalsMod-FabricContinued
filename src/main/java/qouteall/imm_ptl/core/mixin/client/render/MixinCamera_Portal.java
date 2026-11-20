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
    @Unique
    private EnvironmentAttributeProbe ip_savedProbe;
    
    @Unique
    private boolean ip_overridden = false;
    @Unique
    private Level ip_savedLevel;
    @Unique
    private Vec3 ip_savedPosition;
    @Unique
    private BlockPos ip_savedBlockPos;
    @Unique
    private final Vector3f ip_savedForwards = new Vector3f();
    @Unique
    private final Vector3f ip_savedUp = new Vector3f();
    @Unique
    private final Vector3f ip_savedLeft = new Vector3f();
    @Unique
    private float ip_savedXRot;
    @Unique
    private float ip_savedYRot;
    @Unique
    private final Quaternionf ip_savedRotation = new Quaternionf();
    @Unique
    private Frustum ip_savedFrustum;
    
    @Override
    public void ip_beginPortalOverride(ClientLevel newLevel, Vec3 pos, Quaternionf newRotation, Frustum newFrustum) {
        if (ip_overridden) {
            throw new IllegalStateException("camera already overridden");
        }
        ip_overridden = true;
        
        ip_savedLevel = level;
        ip_savedPosition = position;
        ip_savedBlockPos = blockPosition.immutable();
        ip_savedForwards.set(forwards);
        ip_savedUp.set(up);
        ip_savedLeft.set(left);
        ip_savedXRot = xRot;
        ip_savedYRot = yRot;
        ip_savedRotation.set(rotation);
        ip_savedFrustum = cullFrustum;
        
        ip_savedProbe = attributeProbe;
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
        if (!ip_overridden) {
            return;
        }
        ip_overridden = false;
        
        attributeProbe = ip_savedProbe;
        ip_savedProbe = null;
        
        level = ip_savedLevel;
        position = ip_savedPosition;
        blockPosition.set(ip_savedBlockPos);
        forwards.set(ip_savedForwards);
        up.set(ip_savedUp);
        left.set(ip_savedLeft);
        xRot = ip_savedXRot;
        yRot = ip_savedYRot;
        rotation.set(ip_savedRotation);
        cullFrustum = ip_savedFrustum;
        matrixPropertiesDirty |= 3;
        
        ip_savedLevel = null;
        ip_savedFrustum = null;
    }
}

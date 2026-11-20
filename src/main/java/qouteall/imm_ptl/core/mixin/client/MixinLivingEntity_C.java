package qouteall.imm_ptl.core.mixin.client;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.portal.Portal;

/**
 * 26.x moved entity position interpolation into {@link InterpolationHandler}
 * (it replaces the lerpX/lerpY/lerpZ/lerpSteps fields of LivingEntity).
 * Avoid entity position interpolation when crossing portal to the same dimension.
 * (Class name kept from the 1.21 port.)
 */
@Mixin(InterpolationHandler.class)
public abstract class MixinLivingEntity_C {
    @Shadow
    @Final
    private Entity entity;

    @Shadow
    public abstract void cancel();

    @Inject(
        method = "interpolateTo",
        at = @At("RETURN")
    )
    private void onInterpolateTo(Vec3 position, float yRot, float xRot, CallbackInfo ci) {
        if (!IPGlobal.allowClientEntityPosInterpolation) {
            entity.setPos(position.x, position.y, position.z);
            cancel();
            return;
        }

        Portal collidingPortal = ((IEEntity) entity).ip_getCollidingPortal();
        if (collidingPortal != null) {
            double dx = entity.getX() - position.x;
            double dy = entity.getY() - position.y;
            double dz = entity.getZ() - position.z;
            if (dx * dx + dy * dy + dz * dz > 4) {
                McHelper.setPosAndLastTickPos(
                    entity,
                    position,
                    position.subtract(McHelper.getWorldVelocity(entity))
                );
                McHelper.updateBoundingBox(entity);
                cancel();
            }
        }
    }
}

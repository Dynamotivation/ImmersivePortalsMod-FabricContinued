package qouteall.imm_ptl.core.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.phys.Vec3;
import qouteall.q_misc_util.Tuple;
import qouteall.q_misc_util.my_util.DQuaternion;

/**
 * Extract Minecraft look angles (pitch, yaw) from a world-to-camera rotation that may contain a roll.
 * Derived from the forward vector, so a rolled rotation (after going through a rolled portal) keeps the look
 * direction instead of flipping it. From the 26.3 fork by Kurokekkon (Apache-2.0).
 */
@Environment(EnvType.CLIENT)
public final class CameraAngles {
    private CameraAngles() {}
    
    /**
     * @param verticalYaw the yaw used when looking straight up or down (the yaw is undefined there)
     * @return (pitch, yaw) in degrees
     */
    public static Tuple<Double, Double> fromRotation(DQuaternion rotation, double verticalYaw) {
        Vec3 forward = rotation.getConjugated().rotate(new Vec3(0, 0, -1));
        double horizontal = Math.hypot(forward.x, forward.z);
        double pitch = Math.toDegrees(Math.atan2(-forward.y, horizontal));
        double yaw = horizontal < 1e-8 ? verticalYaw : Math.toDegrees(Math.atan2(-forward.x, forward.z));
        return new Tuple<>(pitch, yaw);
    }
}

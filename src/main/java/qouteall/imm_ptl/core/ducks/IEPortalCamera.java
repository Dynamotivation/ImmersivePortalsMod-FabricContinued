package qouteall.imm_ptl.core.ducks;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Lets the portal renderer temporarily make the main camera look from the virtual camera of a portal.
 */
public interface IEPortalCamera {
    void ip_beginPortalOverride(ClientLevel level, Vec3 pos, Quaternionf rotation, Frustum cullFrustum);
    
    void ip_endPortalOverride();
}

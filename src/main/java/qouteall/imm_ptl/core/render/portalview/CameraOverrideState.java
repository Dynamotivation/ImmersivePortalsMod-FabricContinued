package qouteall.imm_ptl.core.render.portalview;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The state of the main Camera that a portal view overrides (see MixinCamera_Portal). A stack of these allows nested
 * portal views (a portal seen through a portal).
 */
public class CameraOverrideState {
    public Level level;
    public Vec3 position;
    public BlockPos blockPos;
    public final Vector3f forwards = new Vector3f();
    public final Vector3f up = new Vector3f();
    public final Vector3f left = new Vector3f();
    public float xRot;
    public float yRot;
    public final Quaternionf rotation = new Quaternionf();
    public Frustum frustum;
    public EnvironmentAttributeProbe probe;
}

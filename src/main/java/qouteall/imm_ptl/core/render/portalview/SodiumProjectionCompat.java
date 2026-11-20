package qouteall.imm_ptl.core.render.portalview;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

/**
 * Sodium draws terrain with the projection matrix it captured in {@code GameRenderer.renderLevel}
 * (GameRendererMixin#sodium$setProjection), not with the bound projection UBO. Portal views are rendered
 * during {@code GameRenderer.extract}, before renderLevel, so without this Sodium would draw the destination
 * terrain with the previous frame's main (non-oblique) projection while entities, clouds, sky and
 * particles use the oblique projection: their depth values are not comparable.
 */
public final class SodiumProjectionCompat {
    private static final boolean SODIUM_LOADED = FabricLoader.getInstance().isModLoaded("sodium");

    private SodiumProjectionCompat() {}

    /**
     * @return Sodium's mutable captured projection matrix, or null if Sodium is absent
     */
    public static @Nullable Matrix4f getCapturedProjection(GameRenderer gameRenderer) {
        if (!SODIUM_LOADED) {
            return null;
        }
        return Impl.get(gameRenderer);
    }

    // separate class so that Sodium classes are only resolved when Sodium is present
    private static final class Impl {
        static @Nullable Matrix4f get(GameRenderer gameRenderer) {
            if (!(gameRenderer instanceof net.caffeinemc.mods.sodium.client.util.GameRendererStorage storage)) {
                return null;
            }
            Matrix4fc m = storage.sodium$getProjectionMatrix();
            return m instanceof Matrix4f mutable ? mutable : null;
        }
    }
}

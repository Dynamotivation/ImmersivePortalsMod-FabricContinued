package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SkyRenderer;
import org.jetbrains.annotations.Nullable;

/**
 * Vanilla's SkyRenderer does not draw into the frame graph's main target: every sky draw call (disc, sun, moon,
 * stars, sunrise, dark disc, end sky) uses the RenderTarget passed to its constructor, i.e. whatever
 * {@code GameRenderer.mainRenderTarget()} returned when {@code LevelRenderer.addSkyPass} first created it.
 * With one LevelRenderer per dimension that target goes stale:
 * <ul>
 * <li>the overworld renderer created by vanilla keeps drawing its sky into the REAL main target while it renders a
 * portal view (player in the Nether): the main frame clears that later, the view target never gets a sky and
 * shows the plain clear/fog colour (grey sky seen from the Nether);</li>
 * <li>a renderer created by ClientWorldLoader gets its SkyRenderer during a portal view, bound to one pooled view
 * target, and would keep drawing there (also after it becomes the main renderer).</li>
 * </ul>
 * The access widener makes the field mutable; {@link #sync} points it at the target the level renderer is about to
 * render into (called from the GameRenderer.mainRenderTarget() hook, which LevelRenderer.render calls first).
 */
@Environment(EnvType.CLIENT)
public final class SkyRendererTargets {
    private SkyRendererTargets() {
    }

    public static void sync(@Nullable LevelRenderer levelRenderer, @Nullable RenderTarget target) {
        if (levelRenderer == null || target == null) {
            return;
        }
        SkyRenderer skyRenderer = levelRenderer.skyRenderer();
        if (skyRenderer != null && skyRenderer.renderTarget != target) {
            skyRenderer.renderTarget = target;
        }
    }
}

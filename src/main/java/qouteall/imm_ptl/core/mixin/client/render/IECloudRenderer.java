package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Optional;

/**
 * Secondary dimension renderers are not registered as resource reload listeners,
 * so their cloud texture is loaded manually.
 */
@Mixin(CloudRenderer.class)
public interface IECloudRenderer {
    @Invoker("prepare")
    Optional<CloudRenderer.TextureData> ip_prepare(ResourceManager manager, ProfilerFiller profiler);
    
    @Invoker("apply")
    void ip_apply(Optional<CloudRenderer.TextureData> preparations, ResourceManager manager, ProfilerFiller profiler);
}

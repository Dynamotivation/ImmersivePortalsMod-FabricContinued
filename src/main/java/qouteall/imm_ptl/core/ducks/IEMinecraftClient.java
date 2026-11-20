package qouteall.imm_ptl.core.ducks;

import net.minecraft.client.renderer.LevelRenderer;

public interface IEMinecraftClient {
    void ip_setWorldRenderer(LevelRenderer r);
    
    Thread ip_getRunningThread();
}

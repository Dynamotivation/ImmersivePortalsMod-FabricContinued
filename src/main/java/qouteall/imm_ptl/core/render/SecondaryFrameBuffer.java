package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.Minecraft;
import qouteall.q_misc_util.Helper;

//it will always be the same size as the main frame buffer
public class SecondaryFrameBuffer {
    public TextureTarget fb;
    
    public void prepare() {
    }
    
    public void prepare(int width, int height) {
    }
    
    
}

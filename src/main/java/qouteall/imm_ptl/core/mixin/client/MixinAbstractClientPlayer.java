package qouteall.imm_ptl.core.mixin.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import qouteall.imm_ptl.core.ducks.IEAbstractClientPlayer;
import qouteall.imm_ptl.core.mixin.common.collision.IEEntity_Collision;

@Mixin(AbstractClientPlayer.class)
public abstract class MixinAbstractClientPlayer implements IEAbstractClientPlayer {
    // 26.x: AbstractClientPlayer no longer has its own clientLevel field; the level lives in Entity
    @Override
    public void ip_setClientLevel(ClientLevel clientWorld) {
        ((IEEntity_Collision) this).ip_setLevel(clientWorld);
    }
}

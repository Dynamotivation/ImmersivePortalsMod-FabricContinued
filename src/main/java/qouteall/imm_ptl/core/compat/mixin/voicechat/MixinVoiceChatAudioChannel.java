package qouteall.imm_ptl.core.compat.mixin.voicechat;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Simple Voice Chat's audio thread resolves PlayerSoundPacket senders through Minecraft.level.
 * Portal rendering and remote world ticking temporarily switch that field (ClientWorldLoader.withSwitchedWorld),
 * but never the local player's level, so speakers were sometimes not found and audio dropped.
 * Use the player's physical world for the speaker lookup.
 * <p>
 * From the 26.3 fork by Kurokekkon (Apache-2.0). Only applied when the voicechat mod is loaded (IPCompatMixinPlugin);
 * require = 0, so a changed Voice Chat version only disables the fix.
 */
@Pseudo
@Mixin(targets = "de.maxhenkel.voicechat.voice.client.AudioChannel", remap = false)
public class MixinVoiceChatAudioChannel {
    @ModifyExpressionValue(
        method = "writeToSpeaker",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;level:Lnet/minecraft/client/multiplayer/ClientLevel;"),
        require = 0
    )
    private ClientLevel ip_physicalSpeakerWorld(ClientLevel temporaryLevel) {
        var player = Minecraft.getInstance().player;
        return player == null ? temporaryLevel : (ClientLevel) player.level();
    }
}

package qouteall.imm_ptl.core.mixin.client;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.miscellaneous.IPortalInitialScreen;
import qouteall.imm_ptl.core.platform_specific.IPConfig;

import java.util.List;
import java.util.function.Function;

/**
 * Shows the Immersive Portals intro screen (compatibility and performance notes) once, before the title screen.
 * In 26.x the initial screens are collected by {@code Gui.addInitialScreens}.
 */
@Mixin(Gui.class)
public class MixinGui_InitialScreens {
    @Inject(
        method = "addInitialScreens",
        at = @At("RETURN")
    )
    private void ip_onAddInitialScreens(
        List<Function<Runnable, Screen>> screens, CallbackInfoReturnable<Boolean> cir
    ) {
        IPConfig config = IPConfig.getConfig();
        if (!config.initialScreenShown) {
            screens.add(IPortalInitialScreen::new);
        }
    }
}

package qouteall.imm_ptl.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DEVELOPMENT ONLY. With -Dip.autoscreenshot=N, takes screenshots N, 2N and 3N client ticks after the
 * client joined a world, then closes the game. Used to check portal rendering without a human.
 */
public class DebugAutoScreenshot {
    private static final Logger LOGGER = LoggerFactory.getLogger("IP-AutoScreenshot");
    private static int ticksInWorld = 0;
    private static int interval = -1;
    
    public static void init() {
        String property = System.getProperty("ip.autoscreenshot");
        if (property == null) {
            return;
        }
        interval = Integer.parseInt(property);
        LOGGER.info("Auto screenshot enabled, interval {} ticks", interval);
        
        IPGlobal.POST_CLIENT_TICK_EVENT.register(() -> {
            Minecraft client = Minecraft.getInstance();
            if (client.level == null || client.player == null) {
                ticksInWorld = 0;
                return;
            }
            ticksInWorld++;
            if (ticksInWorld % interval == 0) {
                int index = ticksInWorld / interval;
                LOGGER.info("Taking screenshot {}", index);
                Screenshot.grab(
                    client.gameDirectory, client.gameRenderer.mainRenderTarget(),
                    message -> LOGGER.info("Screenshot: {}", message.getString())
                );
                if (index >= 3) {
                    LOGGER.info("Auto screenshot finished, stopping the client");
                    client.stop();
                }
            }
        });
    }
}

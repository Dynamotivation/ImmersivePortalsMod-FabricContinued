package qouteall.q_misc_util;

import net.minecraft.util.profiling.Profiler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.TreeMap;

/**
 * Make this because {@link Gui#setOverlayMessage(Component, boolean)} does not support multi-line
 */
@Environment(EnvType.CLIENT)
public class CustomTextOverlay {
    
    public static record Entry(
        Component component,
        long clearingTime
    ) {}
    
    private static final TreeMap<String, Entry> ENTRIES = new TreeMap<>();
    
    private static final boolean renderAtBottomCenter = true;
    
    @Nullable
    private static MultiLineLabel multiLineLabelCache;
    
    public static void putText(Component component, double durationSeconds, String key) {
        ENTRIES.put(
            key,
            new Entry(
                component,
                System.nanoTime() + Helper.secondToNano(durationSeconds)
            )
        );
        multiLineLabelCache = null;
    }
    
    public static void putText(Component component, double durationSeconds) {
        putText(component, durationSeconds, "5_defaultKey");
    }
    
    public static void putText(Component component, String key) {
        putText(component, 0.2, key);
    }
    
    public static void putText(Component component) {
        putText(component, 0.2, "5_defaultKey");
    }
    
    public static boolean remove(String key) {
        return ENTRIES.remove(key) != null;
    }
    
    /**
     * {@link Gui#render(GuiGraphics, float)}
     * {@link net.minecraft.client.gui.screens.AlertScreen}
     */
    public static void render(net.minecraft.client.gui.GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
    }
}

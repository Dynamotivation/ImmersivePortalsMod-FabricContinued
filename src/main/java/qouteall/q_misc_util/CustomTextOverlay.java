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
        if (ENTRIES.isEmpty()) {
            return;
        }

        long now = System.nanoTime();
        ENTRIES.values().removeIf(entry -> entry.clearingTime() < now);
        if (ENTRIES.isEmpty()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        Font font = client.font;

        // all entries, in key order, one component per line (components can contain newlines which are split)
        java.util.List<net.minecraft.util.FormattedCharSequence> lines = new java.util.ArrayList<>();
        int maxWidth = Math.max(100, guiGraphics.guiWidth() - 40);
        for (Entry entry : ENTRIES.values()) {
            lines.addAll(font.split(entry.component(), maxWidth));
        }
        if (lines.isEmpty()) {
            return;
        }

        int lineHeight = font.lineHeight + 1;
        int totalHeight = lines.size() * lineHeight;
        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();
        // above the hotbar / action bar
        int y = renderAtBottomCenter ? screenHeight - 72 - totalHeight : 20;

        for (net.minecraft.util.FormattedCharSequence line : lines) {
            int width = font.width(line);
            int x = (screenWidth - width) / 2;
            guiGraphics.fill(x - 2, y - 1, x + width + 2, y + font.lineHeight, 0x80000000);
            guiGraphics.text(font, line, x, y, 0xFFFFFFFF);
            y += lineHeight;
        }
    }
}

package qouteall.imm_ptl.peripheral.dim_stack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import qouteall.q_misc_util.Helper;

public class DimListWidget extends AbstractSelectionList<DimEntryWidget> {
    
    public static final int ROW_WIDTH = 300;
    
    public static interface DraggingCallback {
        void run(int selectedIndex, int mouseOnIndex);
    }
    
    public final Screen parent;
    private final Type type;
    @Nullable
    private final DraggingCallback draggingCallback;
    
    
    public static enum Type {
        mainDimensionList, addDimensionList
    }
    
    public DimListWidget(
        int width,
        int height,
        int top,
        int itemHeight,
        Screen parent,
        Type type,
        @Nullable DraggingCallback draggingCallback
    ) {
        super(Minecraft.getInstance(), width, height, top, itemHeight);
        this.parent = parent;
        this.type = type;
        this.draggingCallback = draggingCallback;
    }
    
    
    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
    
    }
    
    // make it wider
    @Override
    public int getRowWidth() {
        return ROW_WIDTH;
    }
    
    
}

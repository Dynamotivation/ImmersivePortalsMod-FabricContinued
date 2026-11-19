package qouteall.imm_ptl.core.miscellaneous;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageWidget;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import qouteall.imm_ptl.core.platform_specific.IPConfig;

public class IPortalInitialScreen extends Screen {
    private static final int PAGE_NUM = 4;
    
    private Runnable onClose;
    
    private Button prevButton;
    private StringWidget pageNumberWidget;
    private Button iKnowButton;
    
    private ImageWidget iconWidget;
    private StringWidget titleWidget;
    
    private MultiLineTextWidget contentWidget;
    
    private int currentPageIndex = 0;
    
    public IPortalInitialScreen(Runnable onClose) {
        super(net.minecraft.network.chat.Component.empty());
    }
    
    private void onPrevious() {
        if (currentPageIndex > 0) {
            currentPageIndex--;
            updateUiStatus(currentPageIndex);
        }
    }
    
    private void onIKnow() {
        if (currentPageIndex == PAGE_NUM - 1) {
            IPConfig config = IPConfig.getConfig();
            config.initialScreenShown = true;
            config.saveConfigFile();
            
            onClose();
        }
        else {
            currentPageIndex++;
            updateUiStatus(currentPageIndex);
        }
    }
    
    private void updateUiStatus(int newPageIndex) {
        this.currentPageIndex = newPageIndex;
        
        prevButton.visible = newPageIndex > 0;
        
        pageNumberWidget.setMessage(
            Component.literal("%d / %d".formatted(newPageIndex + 1, PAGE_NUM))
        );
        
        contentWidget.setMessage(
            Component.translatable(
                "iportal.initial_screen.content." + newPageIndex
            )
        );
    }
    
    @Override
    public void onClose() {
        onClose.run();
    }
    
    @Override
    public void init() {
    }
}

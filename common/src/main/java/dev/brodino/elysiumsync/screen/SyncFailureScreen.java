package dev.brodino.elysiumsync.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

/**
 * @author Claude
 * Error screen shown when sync fails.
 */
public class SyncFailureScreen extends Screen {
    
    private final Exception error;
    private MultiLineLabel errorMessageLabel;
    private boolean showStackTrace = false;
    private List<FormattedCharSequence> stackTraceLines;
    private int scrollOffset = 0;
    
    public SyncFailureScreen(Exception error) {
        super(Component.literal("Sync Failed"));
        this.error = error;
    }
    
    @Override
    protected void init() {
        super.init();
        
        // Error message
        String errorMessage = "Failed to sync repository";
        if (error != null && error.getMessage() != null) {
            errorMessage += ":\n" + error.getMessage();
        }
        
        this.errorMessageLabel = MultiLineLabel.create(
            this.font, 
            Component.literal(errorMessage), 
            this.width - 100
        );
        
        // Prepare stack trace
        if (error != null) {
            StringWriter sw = new StringWriter();
            error.printStackTrace(new PrintWriter(sw));
            String stackTrace = sw.toString();
            this.stackTraceLines = this.font.split(
                Component.literal(stackTrace), 
                this.width - 60
            );
        }
        
        // Continue Anyway button
        this.addRenderableWidget(
            new Button(this.width / 2 - 155, this.height - 50, 150, 20,
                Component.literal("Continue Anyway"),
                button -> this.minecraft.setScreen(null))
        );
        
        // Show Details button
        this.addRenderableWidget(
            new Button(this.width / 2 + 5, this.height - 50, 150, 20,
                Component.literal(showStackTrace ? "Hide Details" : "Show Details"),
                button -> {
                    showStackTrace = !showStackTrace;
                    button.setMessage(Component.literal(showStackTrace ? "Hide Details" : "Show Details"));
                    scrollOffset = 0;
                })
        );
    }
    
    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(poseStack);
        
        // Title
        drawString(poseStack, this.font, this.title, 
            this.width / 2 - this.font.width(this.title) / 2, 20, 0xFF5555);
        
        // Error message
        this.errorMessageLabel.renderCentered(poseStack, this.width / 2, 60);
        
        // Stack trace (if enabled)
        if (showStackTrace && stackTraceLines != null) {
            int startY = 120;
            int maxLines = (this.height - 180) / 10;
            
            // Background for stack trace
            fill(poseStack, 20, startY - 5, this.width - 20, this.height - 60, 0x88000000);
            
            for (int i = scrollOffset; i < Math.min(scrollOffset + maxLines, stackTraceLines.size()); i++) {
                this.font.drawShadow(poseStack, stackTraceLines.get(i), 
                    30, startY + (i - scrollOffset) * 10, 0xCCCCCC);
            }
            
            // Scroll indicator
            if (stackTraceLines.size() > maxLines) {
                Component scrollText = Component.literal("(Use mouse wheel to scroll)");
                drawString(poseStack, this.font, scrollText, 
                    this.width / 2 - this.font.width(scrollText) / 2, 
                    this.height - 70, 0x888888);
            }
        }
        
        // Render buttons
        super.render(poseStack, mouseX, mouseY, partialTick);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (showStackTrace && stackTraceLines != null) {
            int maxLines = (this.height - 180) / 10;
            int maxScroll = Math.max(0, stackTraceLines.size() - maxLines);
            
            scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}

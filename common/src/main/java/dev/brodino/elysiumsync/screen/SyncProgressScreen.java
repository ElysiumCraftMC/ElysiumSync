package dev.brodino.elysiumsync.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.brodino.elysiumsync.sync.SyncContext;
import dev.brodino.elysiumsync.sync.SyncScheduler;
import dev.brodino.elysiumsync.sync.SyncState;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * @author Claude
 * Full-screen UI showing sync progress.
 */
public class SyncProgressScreen extends Screen {
    
    private static final int PROGRESS_BAR_WIDTH = 200;
    private static final int PROGRESS_BAR_HEIGHT = 20;
    
    private Button cancelButton;
    private long lastUpdate = 0;
    private static final long UPDATE_INTERVAL_MS = 50; // Update UI 20 times per second
    
    public SyncProgressScreen() {
        super(Component.literal("ElysiumSync"));
    }
    
    @Override
    protected void init() {
        super.init();
        
        // Add cancel button
        this.cancelButton = this.addRenderableWidget(
            new Button(this.width / 2 - 100, this.height / 2 + 80, 200, 20,
                Component.literal("Cancel"),
                button -> onCancel())
        );
    }
    
    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        // Render background
        this.renderBackground(poseStack);
        
        SyncContext context = SyncScheduler.getCurrentContext();
        
        if (context == null) {
            // No sync in progress, close screen
            this.minecraft.setScreen(null);
            return;
        }
        
        // Title
        drawCenteredText(poseStack, this.font, 
            Component.literal("ElysiumSync").getVisualOrderText(), 
            this.width / 2, 20, 0xFFFFFF);
        
        // Status message
        drawCenteredText(poseStack, this.font,
            Component.literal(context.getStatusMessage()).getVisualOrderText(),
            this.width / 2, this.height / 2 - 50, 0xAAAAAA);
        
        // Progress bar
        drawProgressBar(poseStack, context);
        
        // Progress text
        int percentage = context.getProgressPercentage();
        drawCenteredText(poseStack, this.font,
            Component.literal(percentage + "%").getVisualOrderText(),
            this.width / 2, this.height / 2 + 15, 0xFFFFFF);
        
        // File progress
        if (context.getTotalFiles() > 0) {
            drawCenteredText(poseStack, this.font,
                Component.literal(String.format("%d / %d files", 
                    context.getFilesCopied(), context.getTotalFiles())).getVisualOrderText(),
                this.width / 2, this.height / 2 + 30, 0xCCCCCC);
        }
        
        // Elapsed time
        drawCenteredText(poseStack, this.font,
            Component.literal(String.format("Elapsed: %ds", context.getElapsedSeconds())).getVisualOrderText(),
            this.width / 2, this.height / 2 + 45, 0x888888);
        
        // Repository info (smaller text)
        List<FormattedCharSequence> wrappedRepo = this.font.split(
            Component.literal("Repository: " + context.getRepositoryUrl()), 
            this.width - 40
        );
        int yOffset = this.height - 40;
        for (FormattedCharSequence line : wrappedRepo) {
            drawCenteredText(poseStack, this.font, line, this.width / 2, yOffset, 0x666666);
            yOffset += 10;
        }
        
        // Render buttons
        super.render(poseStack, mouseX, mouseY, partialTick);
        
        // Auto-close if sync completed
        if (context.getState() == SyncState.COMPLETED) {
            this.minecraft.execute(() -> {
                this.minecraft.setScreen(null);
            });
        }
        
        // Show error screen if sync failed
        if (context.getState() == SyncState.FAILED) {
            this.minecraft.execute(() -> {
                this.minecraft.setScreen(new SyncFailureScreen(context.getLastError()));
            });
        }
    }
    
    /**
     * Draw the progress bar.
     */
    private void drawProgressBar(PoseStack poseStack, SyncContext context) {
        int barX = this.width / 2 - PROGRESS_BAR_WIDTH / 2;
        int barY = this.height / 2 - 10;
        
        // Background (dark gray)
        fill(poseStack, barX, barY, 
            barX + PROGRESS_BAR_WIDTH, barY + PROGRESS_BAR_HEIGHT, 
            0xFF333333);
        
        // Progress fill (green)
        int fillWidth = (int) (PROGRESS_BAR_WIDTH * (context.getProgressPercentage() / 100.0f));
        if (fillWidth > 0) {
            fill(poseStack, barX, barY, 
                barX + fillWidth, barY + PROGRESS_BAR_HEIGHT, 
                0xFF00AA00);
        }
        
        // Border (white)
        // Top
        fill(poseStack, barX, barY, barX + PROGRESS_BAR_WIDTH, barY + 1, 0xFFFFFFFF);
        // Bottom
        fill(poseStack, barX, barY + PROGRESS_BAR_HEIGHT - 1, 
            barX + PROGRESS_BAR_WIDTH, barY + PROGRESS_BAR_HEIGHT, 0xFFFFFFFF);
        // Left
        fill(poseStack, barX, barY, barX + 1, barY + PROGRESS_BAR_HEIGHT, 0xFFFFFFFF);
        // Right
        fill(poseStack, barX + PROGRESS_BAR_WIDTH - 1, barY, 
            barX + PROGRESS_BAR_WIDTH, barY + PROGRESS_BAR_HEIGHT, 0xFFFFFFFF);
    }
    
    /**
     * Helper to draw centered text.
     */
    private void drawCenteredText(PoseStack poseStack, net.minecraft.client.gui.Font font, 
                                   FormattedCharSequence text, int x, int y, int color) {
        font.drawShadow(poseStack, text, x - font.width(text) / 2.0f, y, color);
    }
    
    /**
     * Handle cancel button.
     */
    private void onCancel() {
        SyncScheduler.cancelSync();
        this.minecraft.setScreen(null);
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        // Don't close on ESC, require explicit cancel
        return false;
    }
    
    @Override
    public boolean isPauseScreen() {
        // Don't pause the game
        return false;
    }
}

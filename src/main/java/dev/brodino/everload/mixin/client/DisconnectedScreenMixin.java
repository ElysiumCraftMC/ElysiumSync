package dev.brodino.everload.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.brodino.everload.EverLoad;
import dev.brodino.everload.sync.SyncScheduler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(DisconnectedScreen.class)
public abstract class DisconnectedScreenMixin extends Screen {

    @Unique
    private Button everload$syncButton = null;

    protected DisconnectedScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void everload$addSyncButton(CallbackInfo ci) {
        // Only add the button if the mod is configured and enabled
        if (EverLoad.CONFIG.isDisabled() || !EverLoad.CONFIG.hasRepository()) {
            return;
        }

        int buttonWidth = 200;
        int buttonHeight = 20;
        int margin = 8;
        int centerX = this.width - buttonWidth - margin;
        int syncButtonY = this.height - buttonHeight - margin;

        everload$syncButton = new Button(
                centerX, syncButtonY,
                buttonWidth, buttonHeight,
                Component.translatable("everload.screen.disconnect.button.force_sync.label"),
                this::everload$onSyncClick
        );

        everload$syncButton.active = !SyncScheduler.isSyncInProgress();

        this.addRenderableWidget(everload$syncButton);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void everload$renderTooltip(PoseStack poseStack, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (everload$syncButton == null || !everload$syncButton.isMouseOver(mouseX, mouseY)) {
            return;
        }

        Component tooltipText = Component.translatable("everload.screen.disconnect.button.force_sync.hover");
        List<FormattedCharSequence> lines = this.font.split(tooltipText, 200);
        this.renderTooltip(poseStack, lines, mouseX, mouseY);
    }

    @Unique
    private void everload$onSyncClick(Button button) {
        // Disable the button immediately to prevent double-clicks
        button.active = false;

        EverLoad.triggerManualSync(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                try {
                    mc.stop();
                } catch (Exception e) {
                    EverLoad.LOGGER.warn("Could not stop Minecraft cleanly, returning to title screen: {}", e.getMessage());
                    mc.setScreen(new TitleScreen());
                }
            });
        });
    }
}

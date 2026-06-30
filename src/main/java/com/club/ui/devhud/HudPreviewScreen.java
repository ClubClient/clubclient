package com.club.ui.devhud;

import com.club.ui.Ui;
import com.club.ui.component.UiContextImpl;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** [DEV HUD — TEMPORARY] In-game HUD preview over a flat dark stand-in scene (non-editable canvas). */
public final class HudPreviewScreen extends Screen {
    private final UiContextImpl uiCtx = new UiContextImpl();
    private final long start = System.nanoTime();
    private final HudCanvas canvas = new HudCanvas(false)
            .add(new EffectsElement()).add(new TargetElement()).add(new InfoElement());

    public HudPreviewScreen() { super(Text.literal("HUD")); }

    @Override public void render(DrawContext dc, int mx, int my, float d) {
        Ui.beginFrame(dc);
        Ui.renderer().rect(0, 0, width, height, 0xFF0A0E15);
        uiCtx.setTime((System.nanoTime() - start) / 1_000_000_000f);
        canvas.setScreen(width, height);
        canvas.layoutFromConfig(MinecraftClient.getInstance());
        Decals.watermark(uiCtx);
        Decals.crosshair(uiCtx, width, height);
        canvas.render(uiCtx);
    }
    @Override public void renderBackground(DrawContext dc, int mx, int my, float d) { }
    @Override public boolean shouldPause() { return false; }
}

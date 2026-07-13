package com.club.hud;

import com.club.config.ClubConfig;
import com.club.modules.screenstretch.ScreenStretchModule;
import com.club.ui.Ui;
import com.club.ui.component.UiContextImpl;
import com.club.ui.hud.ArmorElement;
import com.club.ui.hud.EffectsElement;
import com.club.ui.hud.HudCanvas;
import com.club.ui.hud.InfoElement;
import com.club.ui.hud.TargetElement;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Registers and dispatches the Club HUD: the V2 HUD canvas (Effects / Target / Info / Armor) on the frozen
 * UI render stack. Icons draw through the {@link PixelIcons} DrawContext seam (duotone vanilla textures).
 * The elements read the same {@link ClubConfig.Hud} positions/scales as the editor and hide when disabled or empty.
 */
public final class HudManager {
    private HudManager() {}

    // The in-world V2 HUD: a non-editor canvas (real data, hides disabled/empty elements), built once.
    private static final HudCanvas CANVAS = new HudCanvas(false)
            .add(new EffectsElement()).add(new TargetElement()).add(new InfoElement()).add(new ArmorElement())
            .add(new com.club.ui.hud.SprintElement());
    private static final UiContextImpl UI = new UiContextImpl();
    private static final long START = System.nanoTime();

    /** Promo capture (dev only, see com.club.harness): keep drawing the Club HUD while the VANILLA HUD is
     *  hidden — F1 is how you get a frame with no hotbar, no health bar and no debug text in it, but the
     *  same flag would otherwise take our HUD down with it, and a promo shot of an empty screen is not a
     *  promo shot. Inert in production: nothing outside the harness ever sets it. */
    private static boolean promo;
    public static void promo(boolean on) { promo = on; }

    public static void init() {
        // the HUD callback stops firing outside a world — without this the target cache would pin the
        // unloaded ClientWorld (via the held entity) for as long as the player sits at the title screen
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> TargetHud.clear());
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return;

            // The letterbox is NOT part of the HUD, and it does not get to opt out (Stage 62). Screen
            // Stretch warps the world projection unconditionally — the bars are the mask that hides what
            // the warp over-renders past the frame. Drawn below the two guards, they vanished on F1 and
            // behind the pause menu, and the player saw the raw stretched edges through the mask's hole.
            // Whatever hides the HUD must not un-mask the world.
            drawBlackBars(ctx, mc);

            if (mc.options.hudHidden && !promo) return;
            if (mc.currentScreen != null && mc.currentScreen.shouldPause()) return;
            long t0 = profiling ? System.nanoTime() : 0L;

            // V2 HUD (Effects / Target / Info / Armor); duotone icons draw through the PixelIcons DrawContext seam.
            Ui.beginFrame(ctx);
            PixelIcons.set(ctx);   // duotone icons draw through this DrawContext
            UI.setTime((System.nanoTime() - START) / 1_000_000_000f);
            TargetHud.frame(mc, tickCounter.getTickDelta(true));   // one crosshair raycast per frame, real partial tick
            CANVAS.setScreen(mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
            CANVAS.layoutFromConfig(mc);
            CANVAS.render(UI);
            com.club.ui.LegacyNotice.draw(UI, mc.getWindow().getScaledWidth());   // loud fallback plaque
            Ui.endFrame();   // submit the batched shapes — nothing else will (Stage 61)

            if (profiling) {
                accNanos += System.nanoTime() - t0; accFrames++;
                accDraws += com.club.ui.backend.ModernBackend.DRAWS;
                accShape += com.club.ui.backend.ModernBackend.SHAPE_DRAWS;
                accText  += com.club.ui.backend.ModernBackend.TEXT_DRAWS;
            }
            com.club.ui.backend.ModernBackend.DRAWS = 0;
            com.club.ui.backend.ModernBackend.SHAPE_DRAWS = 0;
            com.club.ui.backend.ModernBackend.TEXT_DRAWS = 0;
        });
    }

    // ---- draw-cost profiler (harness) ------------------------------------------------------------
    // Timing the mod by watching the FPS counter turned out to measure the WORLD (chunk loads, mobs, the
    // time of day) more than the mod: the same build "cost" 0.3 ms on one run and 1.1 ms on the next.
    // This times the draw itself, so the number is about us and nothing else. Two nanoTime calls per
    // frame, and only while armed — off, the flag makes it free.
    private static volatile boolean profiling;
    private static long accNanos;
    private static int accFrames, accDraws;

    private static int accShape, accText;
    public static void profile(boolean on) { accNanos = 0; accFrames = accDraws = accShape = accText = 0; profiling = on; }
    /** Mean milliseconds the Club HUD spent drawing, per frame, since {@link #profile}(true). */
    public static double avgDrawMs() { return accFrames == 0 ? 0 : accNanos / 1_000_000.0 / accFrames; }
    /** Mean GL draw calls the Club HUD submitted per frame. */
    public static double avgDraws() { return accFrames == 0 ? 0 : (double) accDraws / accFrames; }
    public static double avgShapeDraws() { return accFrames == 0 ? 0 : (double) accShape / accFrames; }
    public static double avgTextDraws()  { return accFrames == 0 ? 0 : (double) accText  / accFrames; }
    public static int profiledFrames() { return accFrames; }

    // Letterbox bars for Screen Stretch. Trade-off (Stage 29): these opaque fills cover the screen-edge
    // strips wholesale — including the vanilla chat (bottom-left) and hotbar/bar ends under horizontal
    // bars. Accepted: the bars mask the over-rendered edges of the faked aspect. See ANIMATIONS.md §7.
    private static void drawBlackBars(DrawContext ctx, MinecraftClient mc) {
        if (!ScreenStretchModule.isActive() || !ScreenStretchModule.blackBars()) return;
        int gw = mc.getWindow().getScaledWidth();
        int gh = mc.getWindow().getScaledHeight();
        boolean[] vertical = {false};
        int bar = ScreenStretchModule.barThickness(gh, gw, vertical);
        if (bar <= 0) return;
        int black = 0xFF000000;
        if (vertical[0]) {
            ctx.fill(0, 0, bar, gh, black);
            ctx.fill(gw - bar, 0, gw, gh, black);
        } else {
            ctx.fill(0, 0, gw, bar, black);
            ctx.fill(0, gh - bar, gw, gh, black);
        }
    }
}
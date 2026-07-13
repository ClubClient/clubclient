package com.club.hud;

import com.club.config.ClubConfig;
import com.club.modules.perf.HudProfiler;
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

    /** Harness seam (Stage 63): one real element to measure across GUI scales. The Armor chip — it always
     *  has a box (the editor sample keeps it non-empty) and it sits at a saved coordinate, not an auto one,
     *  so both its SIZE and its POSITION are meaningful. */
    public static com.club.ui.hud.HudElement probeElement() {
        for (var c : CANVAS.children())
            if (c instanceof com.club.ui.hud.ArmorElement e) return e;
        return null;
    }

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
            boolean prof = HudProfiler.armed();
            long t0 = prof ? System.nanoTime() : 0L;
            // The order recorder (harness only) holds ONE frame: reset it here, so whatever the harness
            // reads afterwards is the last complete frame and not a pile of them.
            if (com.club.modules.perf.DrawBoxes.recording) com.club.modules.perf.DrawBoxes.reset();

            // V2 HUD (Effects / Target / Info / Armor); duotone icons draw through the PixelIcons DrawContext seam.
            //
            // Drawn on the CLUB canvas, not in Minecraft's GUI units (Stage 63). The HUD used to be laid out
            // in the player's GUI Scale, so a video setting resized it — on top of each element's own Size
            // slider, which meant "1.0" was a different physical size on every machine. One matrix scale maps
            // Club units to the screen: the HUD is now the size it was designed at, and only its own slider
            // and the screen resolution can change that. (The letterbox above is NOT in this space — it masks
            // the world, so it stays in Minecraft's.)
            float k = com.club.ui.ClubCanvas.scale(mc);
            ctx.getMatrices().push();
            ctx.getMatrices().scale(k, k, 1f);
            Ui.beginFrame(ctx, k);
            PixelIcons.set(ctx);   // duotone icons draw through this DrawContext
            UI.setTime((System.nanoTime() - START) / 1_000_000_000f);

            // The frame, cut into the four things it actually does — so the profiler can say WHERE the
            // time goes instead of handing us one number to guess about (Stage 64). The stamps are taken
            // only while a measurement window is open.
            TargetHud.frame(mc, tickCounter.getTickDelta(true));   // one crosshair raycast per frame, real partial tick
            long t1 = prof ? System.nanoTime() : 0L;               // ── RAYCAST
            HudSpace.migrate(mc);   // once: saved positions were absolute GUI pixels of the old space
            CANVAS.setScreen(com.club.ui.ClubCanvas.widthI(mc), com.club.ui.ClubCanvas.heightI());
            CANVAS.layoutFromConfig(mc);
            long t2 = prof ? System.nanoTime() : 0L;               // ── LAYOUT
            CANVAS.render(UI);
            com.club.ui.LegacyNotice.draw(UI, com.club.ui.ClubCanvas.width(mc));   // loud fallback plaque
            long t3 = prof ? System.nanoTime() : 0L;               // ── BUILD (shaping + geometry)
            Ui.endFrame();   // submit the batched shapes — nothing else will (Stage 61)
            long t4 = prof ? System.nanoTime() : 0L;               // ── SUBMIT (the driver's time, not ours)
            ctx.getMatrices().pop();

            if (prof)
                HudProfiler.frame(t0, t1 - t0, t2 - t1, t3 - t2, t4 - t3,
                        com.club.ui.backend.ModernBackend.SHAPE_DRAWS,
                        com.club.ui.backend.ModernBackend.TEXT_DRAWS,
                        // every GL draw the icons cost: the batch's one, plus any sprite the atlas could
                        // not take and that therefore still goes out the old way
                        com.club.ui.backend.IconBatch.DRAWS + PixelIcons.DRAWS);
            com.club.ui.backend.ModernBackend.DRAWS = 0;
            com.club.ui.backend.ModernBackend.SHAPE_DRAWS = 0;
            com.club.ui.backend.ModernBackend.TEXT_DRAWS = 0;
            com.club.ui.backend.IconBatch.DRAWS = 0;
            PixelIcons.DRAWS = 0;
        });
    }

    // ---- draw-cost profiler (harness) ------------------------------------------------------------
    // Timing the mod by watching the FPS counter measured the WORLD, not the mod. Timing it with a running
    // MEAN did the same thing more quietly: one hitch inside the window moved the number by more than the
    // whole feature we were trying to weigh (0.45 ms and 1.22 ms, same code, same 11 draws). The window,
    // the phases and the order statistics now live in HudProfiler; this class only takes the stamps.
    /** Open/close a measurement window. See {@link HudProfiler}. */
    public static void profile(boolean on) { HudProfiler.arm(on); }
    /** The window's numbers: per-phase medians, the mean beside the median, and every GL draw — icons too. */
    public static HudProfiler.Snapshot stats() { return HudProfiler.snapshot(); }

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
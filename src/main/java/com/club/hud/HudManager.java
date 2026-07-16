package com.club.hud;

import com.club.ClubMod;
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

    /** Latched when the HUD render threw. A HUD that throws sixty times a second is worse than a HUD that
     *  turns itself off and says why: the throw is logged ONCE and the canvas is not entered again this
     *  session. Read on the way IN, so the element that threw cannot be reached a second time. */
    private static boolean failed;

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

            if (failed) return;   // it threw once; it does not get to throw again — see hudFailed()
            if (mc.options.hudHidden && !promo) return;
            if (mc.currentScreen != null && mc.currentScreen.shouldPause()) return;
            boolean prof = HudProfiler.armed();
            long t0 = prof ? System.nanoTime() : 0L;
            long t1 = 0L, t2 = 0L, t3 = 0L, t4 = 0L;   // phase stamps, taken inside the guarded pass below
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
            com.club.compat.Mtx.push(ctx);
            // GUARDED. Nothing up the chain catches an exception out of the HUD callback, and one thrown
            // between this push() and its pop() would leave the matrix stack one deep forever: every
            // matrix VANILLA pushes for the rest of the frame would ride our canvas scale, and the
            // screenshot that reaches us would look like a vanilla bug. The happy path is the same calls
            // in the same order — the picture does not change by a pixel.
            try {
                com.club.compat.Mtx.scale(ctx, k);
                try {
                    Ui.beginFrame(ctx, k);
                    PixelIcons.set(ctx);   // duotone icons draw through this DrawContext
                    UI.setTime((System.nanoTime() - START) / 1_000_000_000f);

                    // The frame, cut into the four things it actually does — so the profiler can say WHERE the
                    // time goes instead of handing us one number to guess about (Stage 64). The stamps are taken
                    // only while a measurement window is open.
                    // Renamed in 1.21.5 (measured, not guessed — 1.21.4 still has getTickDelta). Same number,
                    // same meaning: the fraction of a tick this frame sits at.
                    //? if <1.21.5 {
                    TargetHud.frame(mc, tickCounter.getTickDelta(true));   // one crosshair raycast per frame, real partial tick
                    //?} else {
                    /*TargetHud.frame(mc, tickCounter.getTickProgress(true));*/
                    //?}
                    t1 = prof ? System.nanoTime() : 0L;                    // ── RAYCAST
                    HudSpace.migrate(mc);   // once: saved positions were absolute GUI pixels of the old space
                    CANVAS.setScreen(com.club.ui.ClubCanvas.widthI(mc), com.club.ui.ClubCanvas.heightI());
                    CANVAS.layoutFromConfig(mc);
                    t2 = prof ? System.nanoTime() : 0L;                    // ── LAYOUT
                    CANVAS.render(UI);
                    com.club.ui.LegacyNotice.draw(UI, com.club.ui.ClubCanvas.width(mc));   // loud fallback plaque
                    t3 = prof ? System.nanoTime() : 0L;                    // ── BUILD (shaping + geometry)
                } finally {
                    // endFrame() submits the batched shapes — nothing else will (Stage 61). It has to run on
                    // the way out of a failure too, and it has to run HERE: the batches hold geometry queued
                    // under THIS matrix, and skipping the flush would not drop that geometry — it would leave
                    // it for whatever pass flushes next, which would paint it under a matrix it was never
                    // built for. Half a HUD is a bug; half a HUD in the middle of the inventory screen is a
                    // bug report about someone else's mod.
                    Ui.endFrame();
                    t4 = prof ? System.nanoTime() : 0L;                    // ── SUBMIT (the driver's time, not ours)
                }
            } catch (Exception e) {
                hudFailed(e);
            } finally {
                com.club.compat.Mtx.pop(ctx);
            }

            // A frame that threw has stamps that never happened — do not feed it to the profiler as data.
            //
            // The draw counters come out of the shader path, which is not built past 1.21.4 yet (see
            // com.club.ui.backend.Backends). Rather than report zeros there — a zero is a NUMBER, and this
            // project has published numbers it could not reproduce three times — the profiler is simply not
            // fed on those versions. It is a dev instrument; it comes back with the shaders.
            //? if <1.21.5 {
            if (prof && !failed)
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
            //?}
            PixelIcons.DRAWS = 0;
        });
    }

    /**
     * The HUD threw. Take it down for the session and say so ONCE — with the stack trace, because a HUD
     * that fails silently is a bug report nobody can answer, and a HUD that logs its failure every frame
     * buries the one line that mattered under a megabyte of the same line.
     *
     * <p>Only the Club HUD stops. The letterbox is not part of it and keeps drawing (Stage 62), and
     * ERRORS are deliberately not caught here: the pop() around this still runs, so an Error reaches
     * Minecraft's crash handler with a balanced matrix stack and an honest report, instead of being
     * swallowed into a game that keeps running in a state we no longer understand.
     */
    private static void hudFailed(Exception e) {
        if (failed) return;   // the latch IS the once-flag
        failed = true;
        ClubMod.LOGGER.error("[Club] The Club HUD threw while rendering and is now OFF for this session. "
                + "Nothing else in the mod is affected; restart the game to re-enable it. Please report this:", e);
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
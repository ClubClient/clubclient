package com.club.ui.hud;

import com.club.config.ClubConfig;
import com.club.hud.TargetHud;
import com.club.ui.Color;
import com.club.ui.Ui;
import com.club.ui.UiContext;
import com.club.ui.text.TextStyle;
import com.club.ui.text.Weight;
import com.club.ui.theme.Tokens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;

import java.util.Locale;

/**
 * Hit Distance readout — a QUIET chip in the V4 language (the same capsule ground + one Medium 12 label as
 * {@link SprintElement}): the blocks between you and the entity under your crosshair, {@code "2.34 blocks"}.
 *
 * <p><b>Two channels, like Sprint, but here they are DISTANCE and REACH.</b> The number is the distance; the
 * colour answers "can I hit them?" — {@link Tokens.Palette#stateGood green} while the target is within reach
 * ({@code <= 3.0}, vanilla's attack range), {@link Tokens.Palette#stateLow red} once it slips past. Owner:
 * "зелёным ровно пока дотягиваешься, то есть 3 блока… дальше красным". No configurable range — that would be
 * a soft cheat (see {@link TargetHud}); reach is fixed and the palette states are the whole readout.</p>
 *
 * <p><b>Why it never hides, and never strobes.</b> Owner: with no target, or a target past the 5-block
 * detection, the chip does not vanish — it reads a neutral {@code "0.00 blocks"}, so the readout has a
 * settled resting state instead of blinking on and off as the crosshair sweeps across an opponent. The
 * anti-flicker itself lives one layer down: {@link TargetHud} holds a just-lost target through a 200 ms grace
 * window, so a crosshair slipping off for a frame keeps the last real number rather than snapping to 0.00 and
 * back. The colour adds a small hysteresis at the 3.0 boundary ({@link #HYST}) so a target hovering right at
 * reach does not flash green/red.</p>
 *
 * <p><b>Visibility is owned by the Combat card</b> ({@code MenuContent.hitDistance}), not the HUD editor's
 * Enabled row — the editor shows only this element's Size. One switch, one place (the v0.1.3 "two switches,
 * one pixel" rule); the card writes {@code hud.hitDistance}, which {@link #cfgEnabled()} reads.</p>
 */
public final class HitDistanceElement extends HudElement {
    private static final float TEXT_SIZE = 12f;
    private static final float PAD_X = 8f;
    private static final int   CONTENT_H = 20;
    /** The box is sized from this fixed template so a changing readout ("2.34" ↔ "0.00") never resizes the
     *  chip. Every live value is "d.dd blocks" (distance is <= ~5), so this is the stable width. */
    private static final String SIZING = "0.00 blocks";

    /** Green exactly while the target is reachable (vanilla's 3-block attack range). Owner-fixed, not a setting. */
    private static final double GREEN_MAX = 3.0;
    /** Colour deadband around {@link #GREEN_MAX}: flip to red only above 3.15, back to green only below 2.85. */
    private static final double HYST = 0.15;

    private boolean green = true;      // last colour verdict — latched across the hysteresis band
    private boolean hadTarget = false; // rising edge → seed the verdict cleanly instead of carrying a stale one
    private int lastId = Integer.MIN_VALUE; // last target's entity id — a swap re-seeds the verdict too

    /** The colour verdict, as a pure hysteresis latch (unit-tested). On acquire ({@code !hadTarget}) it seeds
     *  cleanly from the distance; afterwards it only flips once the distance leaves the deadband around
     *  {@link #GREEN_MAX}, so a target hovering right at reach does not flash green/red. */
    public static boolean greenLatch(boolean prevGreen, boolean hadTarget, double d) {
        if (!hadTarget) return d <= GREEN_MAX;
        if (d <= GREEN_MAX - HYST) return true;
        if (d >= GREEN_MAX + HYST) return false;
        return prevGreen;
    }

    /** The readout string for a distance (unit-tested). {@code 0.00} is also the neutral no-target text. */
    public static String readout(double d) { return String.format(Locale.ROOT, "%.2f blocks", d); }

    public HitDistanceElement() { super("hitDistance"); }
    @Override public String displayName() { return "Hit Distance"; }

    private ClubConfig.Hud h() { return ClubConfig.get().hud; }
    @Override public int   cfgX() { return h().hitDistanceX; }
    @Override public int   cfgY() { return h().hitDistanceY; }
    @Override public void  cfgX(int v) { h().hitDistanceX = v; }
    @Override public void  cfgY(int v) { h().hitDistanceY = v; }
    @Override public float cfgScale() { return h().hitDistanceScale; }
    /** The Combat card is the SINGLE owner of this flag; the editor suppresses its Enabled row for this
     *  element and shows only Size (HudEditorScreen.rebuildPopover). */
    @Override public boolean cfgEnabled() { return h().hitDistance; }

    // V4: the chip is the element — capsule painted in paint(), no shared panel.
    @Override protected float panelPadX() { return 0f; }
    @Override protected float panelPadY() { return 0f; }

    /** Default: centred horizontally, one step below the crosshair — a combat readout sits where the eye
     *  already is, clear of Sprint (top-left) and the Target chip (right of the crosshair). Movable. */
    @Override public int autoX(MinecraftClient mc) {
        if (mc == null) return -1;
        int sw = com.club.ui.ClubCanvas.widthI(mc);
        return (sw - contentSize(mc, false)[0]) / 2;
    }
    @Override public int autoY(MinecraftClient mc) {
        return mc != null ? com.club.ui.ClubCanvas.heightI() / 2 + 14 : -1;
    }

    /** Never hides in-world (owner): with no target it reads a neutral 0.00 rather than blinking away. */
    @Override public boolean hasContent(MinecraftClient mc) { return true; }

    @Override public int[] contentSize(MinecraftClient mc, boolean live) {
        float w = 2 * PAD_X + Ui.text().width(SIZING, Weight.MEDIUM, TEXT_SIZE);
        return new int[]{ Math.round(w), CONTENT_H };
    }

    @Override public void paint(UiContext ctx, MinecraftClient mc, float ox, float oy, float s, boolean live) {
        String text;
        int base;
        LivingEntity e = live ? TargetHud.hitEntity() : null;
        if (!live) {
            // Editor sample: a representative in-reach readout so the chip shows its real look, not 0.00.
            text = readout(2.34);
            base = Tokens.palette().stateGood();
            hadTarget = false;
        } else if (e != null) {
            double d = TargetHud.hitDistance();
            // Re-seed the verdict on a NEW target (identity change), not only on first acquire — a swap must
            // not carry the previous target's colour through the deadband.
            boolean sameTarget = hadTarget && e.getId() == lastId;
            green = greenLatch(green, sameTarget, d);
            hadTarget = true; lastId = e.getId();
            text = readout(d);
            base = green ? Tokens.palette().stateGood() : Tokens.palette().stateLow();
        } else {
            hadTarget = false;
            text = readout(0);                                     // neutral 0.00: nothing aimed at
            base = Tokens.palette().textHi();
        }

        float cw = 2 * PAD_X + Ui.text().width(SIZING, Weight.MEDIUM, TEXT_SIZE);
        // Same dense ground as the Sprint chip (bg1 at 0.72): a single quiet line must stay legible over a
        // bright PvP world without becoming a loud panel.
        ctx.renderer().roundedRect(ox, oy, cw * s, CONTENT_H * s, HudPaint.CHIP_RAD * s,
                Color.scaleAlpha(Tokens.surface().bg1(), 0.72f * alpha));
        int col = Color.scaleAlpha(base, alpha);
        float lh = Ui.text().lineHeight(Weight.MEDIUM, TEXT_SIZE);
        ctx.text().draw(text, ox + PAD_X * s, oy + (CONTENT_H - lh) * 0.5f * s,
                TextStyle.of(Weight.MEDIUM, TEXT_SIZE * s, col).effect(HudPaint.textShadow(alpha)));
    }
}

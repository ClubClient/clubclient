package com.club.ui.hud;

import com.club.config.ClubConfig;
import com.club.ui.Color;
import com.club.ui.Ui;
import com.club.ui.UiContext;
import com.club.ui.text.TextStyle;
import com.club.ui.text.Weight;
import com.club.ui.theme.Tokens;
import net.minecraft.client.MinecraftClient;

/**
 * FPS — the utility WHISPER (Stage 13.6, owner: a capsule made it read like content, not like
 * pinned-down auxiliary info). No capsule, no edge, no accent: just a small tabular value +
 * a faint caps-ish label, sitting quietly in the corner on a text shadow. Role over uniformity —
 * this is the one element that must NOT look like the others.
 * (Coordinates / CPS / BPS are a separate future element.)
 *
 * <p><b>THE NUMBER IS VANILLA'S OWN, AND IT MUST STAY VANILLA'S.</b> {@code getCurrentFps()} reads the
 * static {@code MinecraftClient.currentFps} field. That field is not merely "a" frame counter — it is the
 * SAME int the game formats into {@code fpsDebugString} one statement later, inside the once-a-second block
 * at the bottom of {@code MinecraftClient.render()} ({@code currentFps = fpsCounter;} then
 * {@code fpsDebugString = String.format("%d fps T: …", currentFps, …)}), and {@code fpsDebugString} is what
 * the F3 overlay prints. So this chip and the debug overlay are the same integer BY CONSTRUCTION, changing
 * at the same instant. They cannot disagree by a digit. (Verified against the 1.21.1 bytecode, not from
 * memory — see the report for item 16.)
 *
 * <p>Which is the whole reason nothing else may ever feed this element. A smoothed value, a
 * {@code 1000/frame-time}, a {@link com.club.modules.perf.FrameStats} median — each is a DIFFERENT
 * definition of "fps", and each reads systematically HIGH against a frames-per-wall-second counter,
 * because each quietly discards the slow frames that a wall-clock second is forced to include. Putting
 * such a figure on screen beside the game's own, every frame, is this project's cardinal sin in its
 * purest form; it has already retracted three false numbers. The definition of THIS number, and the only
 * one it is allowed to have, is: <i>the frames the client completed during the last whole second</i>.
 */
public final class InfoElement extends HudElement {
    private static final float VALUE_SIZE = 12f, LABEL_SIZE = 10f;
    private static final int GAP = 4;
    /** Width reserved for the value so 59↔240 doesn't shift the label every second. Three digits is the
     *  common case; a wider reading grows the reserve rather than overflowing it — see {@link #reserve}. */
    private static final String VALUE_RESERVE = "888";

    public InfoElement() { super("info"); }
    @Override public String displayName() { return "FPS"; }

    private ClubConfig.Hud h() { return ClubConfig.get().hud; }
    @Override public int   cfgX() { return h().infoX; }
    @Override public int   cfgY() { return h().infoY; }
    @Override public void  cfgX(int v) { h().infoX = v; }
    @Override public void  cfgY(int v) { h().infoY = v; }
    @Override public float cfgScale() { return h().infoScale; }
    @Override public boolean cfgEnabled() { return h().info; }

    // Whisper: no ground at all.
    @Override protected float panelPadX() { return 0f; }
    @Override protected float panelPadY() { return 0f; }

    /** The game's own counter, printed as-is. A live 0 is a LEGAL reading — vanilla's counter is 0 until
     *  its first once-a-second update runs, and F3 says "0 fps" there too. It is printed, not hidden and
     *  not floored to 1: a number that cannot show zero cannot be caught being broken. (240 is the
     *  editor's sample; {@code live} is false only in the editor, never in-world.) */
    private static String fps(MinecraftClient mc, boolean live) { return (live && mc != null ? mc.getCurrentFps() : 240) + ""; }

    /**
     * Width of the value column. The label hangs off its RIGHT edge, so the column must not breathe as the
     * digits change — hence a fixed three-digit reserve, and the value right-aligned inside it.
     *
     * <p>But a reserve is not a clamp. Above 999 fps — an ordinary reading in a menu or a small world on a
     * fast machine — the value is WIDER than "888", so right-aligning it inside the narrower reserve put it
     * at a NEGATIVE offset: the digits drew to the LEFT OF THE ELEMENT'S OWN BOX. Not off the screen — the
     * default position is 8px in, so they simply escaped the bounds the element reports and the editor
     * draws — but an element that lies about its own width cannot be laid out, snapped, or dragged without
     * the box and the ink disagreeing. Taking the wider of the two costs nothing at three digits (identical
     * layout) and lets a four-digit reading grow the box instead of escaping it.
     *
     * <p>The cost, stated plainly: crossing 999 now changes the element's WIDTH by one digit, which is the
     * very jitter VALUE_RESERVE was written to prevent. That trade is deliberate. Below 999 nothing moves at
     * all (the reserve wins), and a one-off shift at a boundary a player crosses seconds after loading a
     * world is a smaller lie than ink outside its own box.
     *
     * <p>{@link #contentSize} and {@link #paint} MUST agree on this width, so both go through here. They
     * are called in the same frame, and vanilla only assigns {@code currentFps} AFTER the frame is rendered
     * (the once-a-second block sits below the "Post render" phase), so the two calls cannot straddle an
     * update and disagree.
     */
    private static float reserve(MinecraftClient mc, boolean live) {
        return Math.max(HudText.width(VALUE_RESERVE, Weight.SEMIBOLD, VALUE_SIZE),
                        HudText.width(fps(mc, live), Weight.SEMIBOLD, VALUE_SIZE));
    }

    @Override public int[] contentSize(MinecraftClient mc, boolean live) {
        float w = reserve(mc, live) + GAP + Ui.text().width("FPS", Weight.MEDIUM, LABEL_SIZE);
        return new int[]{ Math.round(w), Math.round(Ui.text().lineHeight(Weight.SEMIBOLD, VALUE_SIZE)) };
    }

    @Override public void paint(UiContext ctx, MinecraftClient mc, float ox, float oy, float s, boolean live) {
        int val = Color.scaleAlpha(Tokens.palette().textMuted(), alpha);   // quiet — this is aux info
        int lab = Color.scaleAlpha(Tokens.palette().textFaint(), alpha);
        float reserve = reserve(mc, live);
        String v = fps(mc, live);
        float tw = HudText.width(v, Weight.SEMIBOLD, VALUE_SIZE);   // reserve >= tw by construction
        // value right-aligned inside its reserve → the label never shifts as digits change
        HudText.draw(ctx, v, ox + (reserve - tw) * s, oy,
                TextStyle.of(Weight.SEMIBOLD, VALUE_SIZE * s, val).effect(HudPaint.textShadow(alpha)), VALUE_SIZE, s);
        float labDy = Ui.text().ascent(Weight.SEMIBOLD, VALUE_SIZE) - Ui.text().ascent(Weight.MEDIUM, LABEL_SIZE);
        ctx.text().draw("FPS", ox + (reserve + GAP) * s, oy + labDy * s,
                TextStyle.of(Weight.MEDIUM, LABEL_SIZE * s, lab).effect(HudPaint.textShadow(alpha)));
    }
}

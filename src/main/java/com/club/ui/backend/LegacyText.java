package com.club.ui.backend;

import com.club.ui.UiText;
import com.club.ui.text.Align;
import com.club.ui.text.TextStyle;
import com.club.ui.text.Weight;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * The text backend {@code Ui.text()} hands out when MODERN is not in play: Club's own font where it can be
 * drawn, Minecraft's where it cannot.
 *
 * <p><b>Why this class picks rather than just falling back.</b> {@code Ui.backend()} chooses between MODERN
 * and LEGACY, and from 1.21.5 MODERN is not built at all, so LEGACY is not the parachute there — it is the
 * road. Below 1.21.5 nothing changes: {@code SpriteText} reports itself unavailable (its pipeline path does
 * not exist before 1.21.5), every call takes the vanilla branch, and this is the same fallback it has always
 * been, reached only when a shader fails to load. From 1.21.5 {@code SpriteText} answers instead, and the
 * menu is set in Club's font on 1.21.8 and 1.21.11 exactly as it is on 1.21.1.</p>
 *
 * <p><b>The choice is per call, and it must be.</b> A caller measures with {@link #width} and paints with
 * {@link #draw}, and the two have to come from the SAME font or every label sits at an offset of its own.
 * {@code SpriteText.available()} is what guarantees that: it latches its verdict for the span of a frame, so
 * a width and its draw cannot land on opposite sides of a shader that failed in between. This class simply
 * asks it, every time, and never caches the answer itself — a second latch here could only disagree with
 * that one.</p>
 *
 * <p>Not resolution-independent on the vanilla branch, and honest about it: {@link #isResolutionIndependent}
 * answers for whichever font is actually live.</p>
 */
public final class LegacyText implements UiText {

    /** Club's font. Inert below 1.21.5 and wherever the shader will not compile — see the class note. */
    private final SpriteText msdf = new SpriteText();

    private DrawContext ctx;

    public void begin(DrawContext ctx) {
        this.ctx = ctx;
        msdf.begin(ctx);   // also the frame boundary its shader verdict is latched against
    }

    /** True when Club's own font is live. Every method below branches on exactly this. */
    private boolean club() { return msdf.available(); }

    private static TextRenderer tr() { return MinecraftClient.getInstance().textRenderer; }
    private static float scale(float size) { return size / 9f; } // vanilla font ~9px

    @Override public boolean isResolutionIndependent() { return club() && msdf.isResolutionIndependent(); }

    @Override
    public float width(String s, Weight w, float size) {
        return club() ? msdf.width(s, w, size) : tr().getWidth(s) * scale(size);
    }

    @Override
    public float ascent(Weight w, float size) {
        return club() ? msdf.ascent(w, size) : 7f * scale(size);
    }

    @Override
    public float descent(Weight w, float size) {
        return club() ? msdf.descent(w, size) : 2f * scale(size);
    }

    @Override
    public float lineHeight(Weight w, float size) {
        return club() ? msdf.lineHeight(w, size) : 9f * scale(size);
    }

    @Override
    public List<String> wrap(String s, Weight w, float size, float maxWidth) {
        if (club()) return msdf.wrap(s, w, size, maxWidth);
        List<String> out = new ArrayList<>();
        for (net.minecraft.text.StringVisitable line :
                tr().getTextHandler().wrapLines(s, (int) (maxWidth / scale(size)), net.minecraft.text.Style.EMPTY))
            out.add(line.getString());
        return out;
    }

    @Override
    public float draw(String s, float x, float y, TextStyle st) {
        if (club()) return msdf.draw(s, x, y, st);
        if (ctx == null) return x;
        float origX = x;
        float sc = scale(st.size);
        float w = width(s, st.weight, st.size);
        if (st.align == Align.CENTER) x -= w / 2f;
        else if (st.align == Align.RIGHT) x -= w;
        com.club.compat.Mtx.push(ctx);
        com.club.compat.Mtx.translate(ctx, x, y);
        com.club.compat.Mtx.scale(ctx, sc);
        ctx.drawText(tr(), s, 0, 0, st.color, false);
        com.club.compat.Mtx.pop(ctx);
        return origX + w;
    }

    @Override
    public void drawWrapped(String s, float x, float y, float maxWidth, TextStyle st) {
        if (club()) { msdf.drawWrapped(s, x, y, maxWidth, st); return; }
        float lh = lineHeight(st.weight, st.size), cy = y;
        for (String line : wrap(s, st.weight, st.size, maxWidth)) { draw(line, x, cy, st); cy += lh; }
    }
}

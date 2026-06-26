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

/** Fallback text over the vanilla TextRenderer. Not resolution-independent. Fallback only. */
public final class LegacyText implements UiText {
    private DrawContext ctx;
    public void begin(DrawContext ctx) { this.ctx = ctx; }
    private static TextRenderer tr() { return MinecraftClient.getInstance().textRenderer; }
    private static float scale(float size) { return size / 9f; } // vanilla font ~9px

    @Override public boolean isResolutionIndependent() { return false; }
    @Override public float width(String s, Weight w, float size) { return tr().getWidth(s) * scale(size); }
    @Override public float ascent(Weight w, float size) { return 7f * scale(size); }
    @Override public float descent(Weight w, float size) { return 2f * scale(size); }
    @Override public float lineHeight(Weight w, float size) { return 9f * scale(size); }

    @Override public List<String> wrap(String s, Weight w, float size, float maxWidth) {
        List<String> out = new ArrayList<>();
        for (net.minecraft.text.StringVisitable line :
                tr().getTextHandler().wrapLines(s, (int) (maxWidth / scale(size)), net.minecraft.text.Style.EMPTY))
            out.add(line.getString());
        return out;
    }
    @Override public float draw(String s, float x, float y, TextStyle st) {
        if (ctx == null) return x;
        float sc = scale(st.size);
        if (st.align == Align.CENTER) x -= width(s, st.weight, st.size) / 2f;
        else if (st.align == Align.RIGHT) x -= width(s, st.weight, st.size);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(sc, sc, 1f);
        ctx.drawText(tr(), s, 0, 0, st.color, false);
        ctx.getMatrices().pop();
        return x + width(s, st.weight, st.size);
    }
    @Override public void drawWrapped(String s, float x, float y, float maxWidth, TextStyle st) {
        float lh = lineHeight(st.weight, st.size), cy = y;
        for (String line : wrap(s, st.weight, st.size, maxWidth)) { draw(line, x, cy, st); cy += lh; }
    }
}

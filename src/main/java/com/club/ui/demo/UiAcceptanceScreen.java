package com.club.ui.demo;

import com.club.ui.Axis;
import com.club.ui.Color;
import com.club.ui.Radii;
import com.club.ui.Ui;
import com.club.ui.UiContext;
import com.club.ui.UiRenderer;
import com.club.ui.UiText;
import com.club.ui.text.Align;
import com.club.ui.text.TextEffect;
import com.club.ui.text.TextStyle;
import com.club.ui.text.Weight;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Exercises every UiRenderer/UiText method. Toggle backend with B, zoom with Z. */
public class UiAcceptanceScreen extends Screen {
    private static final int BG = 0xFF0B111A, TXT = 0xFFF4F6FA, MUT = 0xFFA6ADBB,
            ACC = 0xFF7CABFF, ACC2 = 0xFF78D7FF, SURF = 0xFF131B2A, BORD = 0xFF1D2536;
    private float zoom = 1f, zcx, zcy;
    public UiAcceptanceScreen() { super(Text.literal("UI V2 Acceptance")); }
    public void setZoom(float z, float cx, float cy) { zoom = z; zcx = cx; zcy = cy; }

    @Override public void render(DrawContext ctx, int mx, int my, float delta) {
        Ui.beginFrame(ctx);
        Ui.renderer().rect(0, 0, width, height, BG);   // no direct ctx.fill — honours the hard rule
        UiContext c = new UiContext() {
            public UiRenderer renderer() { return Ui.renderer(); }
            public UiText text() { return Ui.text(); }
            public float time() { return 0f; }
        };
        UiText t = c.text();
        t.draw("UI V2 — backend: " + Ui.backend(), 24, 16, TextStyle.of(Weight.SEMIBOLD, 18, TXT));

        boolean zoomed = zoom != 1f;
        if (zoomed) { ctx.getMatrices().push(); ctx.getMatrices().translate(zcx, zcy, 0); ctx.getMatrices().scale(zoom, zoom, 1f); ctx.getMatrices().translate(-zcx, -zcy, 0); }
        content(c, 24, 56);
        if (zoomed) ctx.getMatrices().pop();
    }

    private void content(UiContext c, int x, int y) {
        UiRenderer r = c.renderer(); UiText t = c.text();
        // text weights/effects
        t.draw("Regular Ag Яр", x, y, TextStyle.of(Weight.REGULAR, 22, TXT));
        t.draw("Medium Ag Яр", x + 200, y, TextStyle.of(Weight.MEDIUM, 22, TXT));
        t.draw("SemiBold Ag Яр", x + 400, y, TextStyle.of(Weight.SEMIBOLD, 22, TXT));
        t.draw("Outline", x, y + 36, TextStyle.of(Weight.SEMIBOLD, 22, TXT).effect(new TextEffect.Outline(0.25f, 0xFF000000)));
        t.draw("Shadow", x + 160, y + 36, TextStyle.of(Weight.SEMIBOLD, 22, TXT).effect(new TextEffect.Shadow(1.5f, 1.5f, 0.3f, 0xCC000000)));
        t.draw("Glow", x + 320, y + 36, TextStyle.of(Weight.SEMIBOLD, 22, ACC).effect(new TextEffect.Glow(0.6f, Color.withAlpha(ACC2, 0xCC))));
        // shapes
        int sy = y + 80;
        r.roundedRect(x, sy, 120, 40, 12, SURF);
        r.border(x + 140, sy, 120, 40, 12, 1.5f, ACC);
        r.gradient(x + 280, sy, 160, 40, 10, ACC, ACC2, Axis.HORIZONTAL);
        r.roundedRect(x + 460, sy, 40, 40, new Radii(16, 4, 16, 4), SURF);
        // glow + shadow + circle + line
        int gy = sy + 60;
        r.glow(x + 18, gy + 8, 18, 18, 9, 14, Color.withAlpha(ACC, 0xCC)); r.roundedRect(x + 18, gy + 8, 18, 18, 9, ACC);
        r.shadow(x + 120, gy, 90, 34, 8, 0, 3, 10, 0x99000000); r.roundedRect(x + 120, gy, 90, 34, 8, SURF);
        r.circle(x + 260, gy + 17, 16, ACC);
        r.line(x + 300, gy + 17, x + 440, gy + 17, 2, BORD);
        // clip demo
        int cy2 = gy + 60;
        r.pushClip(x, cy2, 200, 30);
        r.gradient(x - 40, cy2, 320, 30, 0, ACC, ACC2, Axis.HORIZONTAL);
        t.draw("clipped content АБВ", x + 6, cy2 + 8, TextStyle.of(Weight.MEDIUM, 13, 0xFF0E1421));
        r.popClip();
        // wrapped + aligned
        t.drawWrapped("Wrapped Cyrillic текст для проверки переноса по ширине контейнера.", x + 240, cy2, 240, TextStyle.of(Weight.REGULAR, 13, MUT));
        t.draw("RIGHT", x + 480, cy2, TextStyle.of(Weight.MEDIUM, 13, TXT).align(Align.RIGHT));
    }

    @Override public boolean shouldPause() { return false; }
}

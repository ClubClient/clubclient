package com.club.gui.components;

import com.club.gui.Theme;
import com.club.util.ClubFont;
import com.club.util.Mth;
import com.club.util.RenderHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Setting slider, redesigned as a single quiet line: the label sits on the left,
 * a thin gradient track runs across the middle, and the value (accent gradient)
 * is right-aligned at the end of the row — all on one baseline. The knob is a
 * small, soft dot; there are no large white elements and no glow.
 */
public class SliderWidget extends ClickableWidget {
    private final String label;
    private final float min, max, step;
    private float value;
    private final Consumer<Float> onChange;
    private Function<Float, String> fmt = v -> String.format(java.util.Locale.US, "%.2f", v);
    private int trackW = -1; // -1 = auto

    /** Fixed gutter reserved for the right-aligned value, so the track never shifts as digits change. */
    private static final int VAL_GUTTER = 40;

    public SliderWidget(int x, int y, int width, int height, String label, float min, float max, float step,
                        float value, Consumer<Float> onChange) {
        super(x, y, width, height, Text.literal(label));
        this.label = label;
        this.min = min;
        this.max = max;
        this.step = step;
        this.value = Mth.clamp(value, min, max);
        this.onChange = onChange;
    }

    public SliderWidget format(Function<Float, String> f) { this.fmt = f; return this; }
    public SliderWidget trackWidth(int w) { this.trackW = w; return this; }

    private int trackW() {
        if (trackW > 0) return trackW;
        return Mth.clamp((int) (width * 0.46f), 64, 160);
    }
    private int labelGutter() {
        return (label == null || label.isEmpty()) ? 0 : ClubFont.widthSmall(label) + 12;
    }
    private int trackEndX() { return getX() + width - VAL_GUTTER; }
    private int trackX() { return Math.max(getX() + labelGutter(), trackEndX() - trackW()); }
    private int effTrackW() { return Math.max(8, trackEndX() - trackX()); }

    @Override
    protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY();
        int midY = y + height / 2;
        int textY = midY - 6;

        // label, on the left, vertically centred
        if (label != null && !label.isEmpty())
            ClubFont.drawSmall(ctx, label, x, textY, Theme.TEXT, false);

        // value — plain white, right-aligned (information reads cleanest solid; the
        // accent lives on the track fill below, never on the number).
        String val = fmt.apply(value);
        int vw = ClubFont.widthSmall(val);
        ClubFont.drawSmall(ctx, val, x + width - vw, textY, Theme.TEXT, false);

        // thin 2px track between label and value, on the same baseline; the fill is
        // a FLAT accent (no gradient — that is reserved for tab/toggle).
        int tw = effTrackW(), tx = trackX();
        int trackH = 2;
        int trackY = midY - 1;
        float frac = (max > min) ? (value - min) / (max - min) : 0f;
        RenderHelper.roundedRect(ctx, tx, trackY, tw, trackH, 1, Theme.FILL_TRACK);
        int fillW = Math.round(tw * frac);
        if (fillW > 0) RenderHelper.roundedRect(ctx, tx, trackY, fillW, trackH, 1, Theme.ACCENT);

        // technical thumb — a small 10px-tall accent pill, not a round ball, no glow.
        int knobW = 5, knobH = 10;
        int knobX = tx + Math.round((tw - knobW) * frac);
        int knobY = midY - knobH / 2;
        RenderHelper.roundedRect(ctx, knobX, knobY, knobW, knobH, knobW / 2, Theme.ACCENT);
    }

    private void setFromMouse(double mouseX) {
        float frac = (float) ((mouseX - trackX()) / Math.max(1, effTrackW()));
        frac = Mth.clamp(frac, 0f, 1f);
        float v = Mth.clamp(Mth.snap(min + frac * (max - min), step), min, max);
        if (v != value) {
            value = v;
            if (onChange != null) onChange.accept(value);
        }
    }

    public float getValue() { return value; }

    @Override
    public void onClick(double mouseX, double mouseY) { setFromMouse(mouseX); }

    @Override
    protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) { setFromMouse(mouseX); }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {}
}

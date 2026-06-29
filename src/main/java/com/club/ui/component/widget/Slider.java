package com.club.ui.component.widget;

import com.club.ui.UiContext;
import com.club.ui.component.Component;
import com.club.ui.component.Container;
import com.club.ui.layout.CrossAlign;
import com.club.ui.layout.Row;
import com.club.ui.layout.Size;
import com.club.ui.layout.Sizing;
import com.club.ui.text.Align;
import com.club.ui.theme.Tokens;

import java.util.Locale;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Numeric slider. Composition: internal {@code Row[ track (Fill), valueLabel (Fixed) ]}.
 * The {@code track} is a private interactive component (drag → value, via pointer-capture); the value label
 * is optional ({@link #showValue}). Flat accent fill (no gradient — frozen DESIGN). Arrow keys step the value
 * when focused. Slider is NOT a {@link Control} (drag model, not press-activate).
 */
public final class Slider extends Container {
    private final float min, max, step;
    private float value;
    private FloatConsumer onChange;
    private boolean showValue = true;
    private float lastFormatted = Float.NaN;   // cache: reformat valueLabel only when value changes (alloc-free)

    private final Track track = new Track();
    private final Label valueLabel = new Label("", Tokens.type().label()).align(Align.RIGHT);
    private Row row;
    private boolean built;

    public Slider(float value, float min, float max, float step) {
        this.min = min; this.max = max; this.step = step;
        this.value = quantize(value, min, max, step);
    }

    public Slider onChange(FloatConsumer cb) { this.onChange = cb; return this; }
    public Slider showValue(boolean s) { this.showValue = s; built = false; return this; }
    public float value() { return value; }

    /** Clamp to [min,max] then snap to step (step==0 → continuous). Pure — unit-tested without GL. */
    static float quantize(float raw, float min, float max, float step) {
        float v = Math.max(min, Math.min(max, raw));
        if (step > 0f) v = min + Math.round((v - min) / step) * step;
        return Math.max(min, Math.min(max, v));
    }

    /** Integer text when step is a whole number, else one decimal. Locale-independent. Pure — unit-tested. */
    static String format(float v, float step) {
        boolean integral = step >= 1f && step == Math.rint(step);
        return integral ? Integer.toString(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
    }

    private void setValue(float v) {
        float q = quantize(v, min, max, step);
        if (q != value) { value = q; if (onChange != null) onChange.accept(value); }
    }

    private void build() {
        row = new Row().gap(Tokens.spacing().sm()).crossAlign(CrossAlign.CENTER);
        row.add(track, Sizing.fill());
        if (showValue) row.add(valueLabel);
        children.clear();
        children.add(row);
        built = true;
    }

    @Override public Size measure(float availW, float availH) {
        if (!built) build();
        return row.measure(availW, availH);
    }

    @Override public void layout(float x, float y, float w, float h) {
        if (!built) build();
        super.layout(x, y, w, h);
        row.layout(x, y, w, h);
    }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (!enabled) return false;
        float d = step > 0f ? step : (max - min) / 100f;
        if (key == GLFW_KEY_LEFT)  { setValue(value - d); return true; }
        if (key == GLFW_KEY_RIGHT) { setValue(value + d); return true; }
        return false;
    }

    @Override public void render(UiContext ctx) {
        if (showValue && value != lastFormatted) {         // reformat only on change — no per-frame String alloc
            valueLabel.text(format(value, step));
            lastFormatted = value;
        }
        super.render(ctx);                                 // renders the Row (track + valueLabel)
        WidgetPaint.focusRing(ctx, this, Tokens.radius().sm());
    }

    /** Interactive track: draws track + flat accent fill + knob, owns the drag (pointer-capture). */
    private final class Track extends Component {
        @Override public Size measure(float availW, float availH) {
            return new Size(Tokens.spacing().xxl() * 3f, Tokens.spacing().lg());   // sensible min width
        }
        @Override public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0) { pressed = true; mapTo(mx); return true; }
            return false;
        }
        @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            mapTo(mx); return true;
        }
        @Override public boolean mouseReleased(double mx, double my, int button) { pressed = false; return true; }
        private void mapTo(double mx) {
            float frac = (float) ((mx - x) / Math.max(1f, w));
            setValue(min + frac * (max - min));
        }
        @Override public void render(UiContext ctx) {
            float th = Tokens.border().thickness() * 2f;       // 2px track
            float ty = y + (h - th) / 2f, r = th / 2f;
            ctx.renderer().roundedRect(x, ty, w, th, r, Tokens.surface().surfaceHi());
            float frac = (max > min) ? (value - min) / (max - min) : 0f;
            ctx.renderer().roundedRect(x, ty, w * frac, th, r, Tokens.accent().accent());   // FLAT fill (no gradient)
            float kr = h / 2f - Tokens.border().thickness();
            ctx.renderer().circle(x + w * frac, y + h / 2f, kr, Tokens.accent().accent());
        }
    }
}

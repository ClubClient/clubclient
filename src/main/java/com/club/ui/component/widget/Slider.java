package com.club.ui.component.widget;

import com.club.ui.Ui;
import com.club.ui.UiContext;
import com.club.ui.component.Component;
import com.club.ui.component.Container;
import com.club.ui.layout.CrossAlign;
import com.club.ui.layout.Row;
import com.club.ui.layout.Size;
import com.club.ui.layout.Sizing;
import com.club.ui.motion.Transition;
import com.club.ui.text.Align;
import com.club.ui.text.TextStyle;
import com.club.ui.theme.Tokens;
import com.club.ui.theme.Typography;

import java.util.Locale;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Numeric slider — a full interactive control, not a "line with a knob".
 *
 * <p>Composition: internal {@code Row[ track (Fill), valueReadout (Fixed) ]}. The {@code track} is a
 * private interactive component (drag → value via pointer-capture) drawn as a recessed groove + flat
 * accent fill + a compact light-accent knob (thin dark ring); the readout is an optional ({@link #showValue})
 * fixed-width, right-aligned column so the rail length never jiggles as the number's width changes.
 *
 * <p>Weight without depth (frozen flat spec — no shadow/glow): the puck is sized larger than the rail
 * and grows on hover/press via a {@link Transition}. Focus shows as a localized accent ring around the
 * puck. Arrow keys step the value when focused. Slider is NOT a {@link Control} (drag, not press-activate).
 */
public final class Slider extends Container {

    // Shape geometry — proportions of the control, NOT design tokens (cf. Checkbox CK_* constants).
    // Handle grow / rim / focus-gap come from the shared design language (WidgetPaint).
    private static final float RAIL_H = 6f;       // groove thickness (was a 2px hairline — read cheap)
    private static final float KNOB_R = 6f;       // base knob radius (12px); grows on hover/press

    private final float min, max, step;
    private float value;
    private FloatConsumer onChange;
    private boolean showValue = true;
    private float lastFormatted = Float.NaN;   // cache: reformat readout only when value changes (alloc-free)

    private final Track track = new Track();
    private final Value valueView = new Value();
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
        row = new Row().gap(Tokens.spacing().md()).crossAlign(CrossAlign.CENTER);
        row.add(track, Sizing.fill());
        if (showValue) row.add(valueView);          // Fixed column: reserves a stable width (no rail jiggle)
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
        if (showValue && value != lastFormatted) {     // reformat only on change — no per-frame String alloc
            valueView.set(format(value, step));
            lastFormatted = value;
        }
        super.render(ctx);                             // renders the Row (track + readout)
    }

    // -------------------------------------------------------------------------
    // Value readout: fixed-width, right-aligned column so the right edge is anchored
    // and the track's Fill width stays constant across value changes (no jiggle).
    // -------------------------------------------------------------------------
    private final class Value extends Component {
        private String text = "";
        private TextStyle style;     // cached once — alloc-free render

        void set(String t) { this.text = t; }

        @Override public Size measure(float availW, float availH) {
            Typography.Role r = Tokens.type().body();
            // Reserve the widest the readout can ever be (min vs max), so the column never resizes.
            float w = Math.max(Ui.text().width(format(min, step), r.weight(), r.size()),
                               Ui.text().width(format(max, step), r.weight(), r.size()));
            return new Size(w, r.lineHeight());
        }

        @Override public void render(UiContext ctx) {
            if (style == null) {
                Typography.Role r = Tokens.type().body();
                style = TextStyle.of(r.weight(), r.size(), Tokens.palette().textHi()).align(Align.RIGHT);
            }
            ctx.text().draw(text, x + w, y, style);    // RIGHT align → right edge anchored at x+w
        }
    }

    // -------------------------------------------------------------------------
    // Interactive track: recessed groove + accent fill + light-accent knob; owns the drag (pointer-capture).
    // -------------------------------------------------------------------------
    private final class Track extends Component {
        private final Transition grow =
                new Transition(0f, Tokens.motion().durations().fast(), Tokens.motion().easings().decelerate());

        @Override public Size measure(float availW, float availH) {
            return new Size(Tokens.spacing().xxl() * 3f, Tokens.spacing().xl());   // min width, 24px lane
        }

        @Override public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0) { pressed = true; mapTo(mx); return true; }
            return false;
        }
        @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            mapTo(mx); return true;
        }
        @Override public boolean mouseReleased(double mx, double my, int button) { pressed = false; return true; }

        /** Map cursor x → value across the inset travel range (so the ends map to the puck's extremes). */
        private void mapTo(double mx) {
            float travelL = x + KNOB_R, travelW = Math.max(1f, w - 2f * KNOB_R);
            float frac = (float) ((mx - travelL) / travelW);
            setValue(min + frac * (max - min));
        }

        @Override public void render(UiContext ctx) {
            float now = ctx.time();
            grow.target(pressed ? WidgetPaint.HANDLE_PRESS_GROW : (hovered ? WidgetPaint.HANDLE_HOVER_GROW : 0f), now);
            float kr = KNOB_R + grow.value(now);

            float cy    = y + h / 2f;
            float railY = cy - RAIL_H / 2f, railR = RAIL_H / 2f;
            float frac  = (max > min) ? (value - min) / (max - min) : 0f;
            float knobX = (x + KNOB_R) + (w - 2f * KNOB_R) * frac;   // inset travel — puck never spills past ends

            // Groove (full width) then flat accent fill up to the puck centre.
            ctx.renderer().roundedRect(x, railY, w, RAIL_H, railR, Tokens.surface().surfaceHi());
            ctx.renderer().roundedRect(x, railY, knobX - x, RAIL_H, railR, Tokens.accent().accent());

            // Focus ring + knob: a compact light-accent knob with a thin dark ring — reads integrated
            // with the fill (not a foreign white puck), defined on both fill and groove. Flat.
            if (Slider.this.isFocused()) WidgetPaint.focusRingCircle(ctx, knobX, cy, kr);
            WidgetPaint.puck(ctx, knobX, cy, kr, Tokens.accent().accentHi(), Tokens.accent().onAccent(),
                    Tokens.border().thickness());
        }
    }
}

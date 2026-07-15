package com.club.ui.menu;

import com.club.modules.particles.ParticleCatalog;
import com.club.modules.particles.ParticleGroup;
import com.club.modules.particles.ParticleVisibility;
import com.club.ui.Color;
import com.club.ui.UiContext;
import com.club.ui.UiRenderer;
import com.club.ui.component.Container;
import com.club.ui.text.TextStyle;
import com.club.ui.text.Weight;
import com.club.ui.theme.Tokens;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The Particles category content — a TWO-PANE surface that replaces the card grid when the Particles
 * category is active (owner-designed; see the approved mock). LEFT: a sub-rail of particle GROUPS, each with
 * an On/Off pill that sets the whole group. RIGHT: the selected group's particle rows, each with its own
 * On/Off pill, scrollable, entering with the same fade+rise cascade the main categories use
 * ({@link TileMotion#categoryEnter}).
 *
 * <p>WHY FULLY CUSTOM-DRAWN (no {@code Toggle} children): the config hidden-set is the single source of
 * truth, and every pill reads it LIVE via {@link ParticleVisibility}. So a group master and a per-particle
 * pill can never disagree — flip a particle and the group pill reflects it the same frame, with no state to
 * keep in sync. It also lets the cascade alpha ride straight into the pill colours.
 *
 * <p>Nothing here is protected: every type is togglable, everything on by default (owner reversed the
 * earlier "Always on" idea — it's your client and your eyes). Interaction stays on the brand accent; the
 * category's gold is identity only (a swatch + the active bar), per the v2.5 rule.
 */
final class ParticlesPane extends Container {

    // Geometry — club units (proportions, not design tokens).
    private static final float PAD = 10f, RAIL_W = 152f, GAP = 12f;
    private static final float RAIL_ROW = 30f, LIST_ROW = 26f, HEAD_H = 26f;
    private static final float PILL_W = 30f, PILL_H = 16f, PILL_PAD = 3f;

    private static final class GroupUi {
        final ParticleGroup group;
        final List<ParticleCatalog.Entry> entries;
        GroupUi(ParticleGroup g, List<ParticleCatalog.Entry> e) { this.group = g; this.entries = e; }
    }

    private final List<GroupUi> groups = new ArrayList<>();
    private int selected;

    // manual scroll for the right list (a lighter twin of ScrollArea — the list is the only scroller here)
    private float scroll, scrollMax;

    // per-row entrance cascade, re-seeded whenever the selected group changes
    private TileMotion[] rowMotion = new TileMotion[0];

    ParticlesPane() {
        Map<ParticleGroup, List<ParticleCatalog.Entry>> byGroup = new EnumMap<>(ParticleGroup.class);
        for (ParticleGroup g : ParticleGroup.values()) byGroup.put(g, new ArrayList<>());
        for (ParticleCatalog.Entry e : ParticleCatalog.all()) byGroup.get(e.group()).add(e);
        for (ParticleGroup g : ParticleGroup.values()) {
            List<ParticleCatalog.Entry> es = byGroup.get(g);
            if (!es.isEmpty()) groups.add(new GroupUi(g, es));   // OTHER shows only if a modded type landed there
        }
        seedCascade();
    }

    /** Called by the screen every time the Particles category is (re)entered — replays the cascade. */
    void onEnter() { scroll = 0f; seedCascade(); }

    private void seedCascade() {
        if (groups.isEmpty()) { rowMotion = new TileMotion[0]; return; }
        List<ParticleCatalog.Entry> rows = current().entries;
        rowMotion = new TileMotion[rows.size()];
        for (int i = 0; i < rowMotion.length; i++) rowMotion[i] = TileMotion.categoryEnter(i);
    }

    private GroupUi current() { return groups.get(Math.min(selected, groups.size() - 1)); }

    private static boolean anyVisible(List<ParticleCatalog.Entry> es) {
        for (ParticleCatalog.Entry e : es) if (!ParticleVisibility.isHidden(e.id())) return true;
        return false;
    }

    // ---- layout --------------------------------------------------------------

    @Override public void layout(float x, float y, float w, float h) {
        super.layout(x, y, w, h);
        if (groups.isEmpty()) return;
        float listH = h - PAD - HEAD_H - PAD;
        float contentH = current().entries.size() * LIST_ROW;
        scrollMax = Math.max(0f, contentH - listH);
        scroll = Math.max(0f, Math.min(scroll, scrollMax));
    }

    // ---- render --------------------------------------------------------------

    @Override public void render(UiContext ctx) {
        if (groups.isEmpty()) return;
        UiRenderer r = ctx.renderer();
        float now = ctx.time();
        Weight lw = Tokens.type().label().weight();
        float ls = Tokens.type().label().size();
        float lh = ctx.text().lineHeight(lw, ls);

        // LEFT — group sub-rail, a recessed sub-tray (one tone below the content well)
        float railX = x, railY = y, railH = h;
        r.roundedRect(railX, railY, RAIL_W, railH, Tokens.radius().md(), Tokens.surface().wellShallow());
        for (int i = 0; i < groups.size(); i++) {
            GroupUi g = groups.get(i);
            float ry = railY + PAD + i * RAIL_ROW;
            boolean active = i == selected;
            if (active) {
                r.roundedRect(railX + 6, ry + 2, RAIL_W - 12, RAIL_ROW - 4, Tokens.radius().sm(), Tokens.surface().surfaceHi());
                r.roundedRect(railX + 2, ry + 6, 3, RAIL_ROW - 12, 1.5f, Tokens.categories().particles());   // identity bar
            }
            int nameCol = active ? Tokens.palette().textHi() : Tokens.palette().textMuted();
            ctx.text().draw(g.group.label(), railX + 16, ry + (RAIL_ROW - lh) / 2f, TextStyle.of(lw, ls, nameCol));
            drawPill(r, railX + RAIL_W - PAD - PILL_W, ry + (RAIL_ROW - PILL_H) / 2f, anyVisible(g.entries), 1f);
        }

        // RIGHT — detail: header (group name) + scrolling particle list
        float dx = x + RAIL_W + GAP, dw = x + w - dx;
        ctx.text().draw(current().group.label(), dx + 2, y + (HEAD_H - Tokens.type().heading().lineHeight()) / 2f,
                TextStyle.of(Tokens.type().heading().weight(), Tokens.type().heading().size(), Tokens.palette().textHi()));
        float hairY = y + HEAD_H;
        r.rect(dx, hairY, dw, 1f, Tokens.border().subtle());

        float listY = hairY + PAD, listH = y + h - PAD - listY;
        r.pushClip(dx, listY, dw, listH);
        List<ParticleCatalog.Entry> rows = current().entries;
        for (int i = 0; i < rows.size(); i++) {
            ParticleCatalog.Entry e = rows.get(i);
            float ta = rowMotion[i].tick(now);
            float ry = listY + i * LIST_ROW - scroll + rowMotion[i].driftY(ta);
            if (ry + LIST_ROW < listY || ry > listY + listH) continue;   // cheap cull outside the viewport
            boolean vis = !ParticleVisibility.isHidden(e.id());
            int nameCol = Color.scaleAlpha(vis ? Tokens.palette().textHi() : Tokens.palette().textMuted(), ta);
            ctx.text().draw(e.label(), dx + PAD, ry + (LIST_ROW - lh) / 2f, TextStyle.of(lw, ls, nameCol));
            drawPill(r, dx + dw - PILL_W - PAD, ry + (LIST_ROW - PILL_H) / 2f, vis, ta);
        }
        r.popClip();

        // slim scrollbar when the list overflows (neutral — accent is for interaction, not chrome)
        if (scrollMax > 0f) {
            float trackH = listH, thumbH = Math.max(18f, trackH * trackH / (rows.size() * LIST_ROW));
            float thumbY = listY + (trackH - thumbH) * (scroll / scrollMax);
            r.roundedRect(dx + dw - 3f, thumbY, 3f, thumbH, 1.5f, Tokens.palette().textFaint());
        }
    }

    /** Draws the Toggle pill (custom, so its state is read live from config) — same visual language as
     *  {@code Toggle}: off = inset surface + hairline, on = flat accent, white puck. Alpha rides the cascade. */
    private void drawPill(UiRenderer r, float px, float py, boolean on, float a) {
        float rad = PILL_H / 2f;
        if (on) {
            r.roundedRect(px, py, PILL_W, PILL_H, rad, Color.scaleAlpha(Tokens.accent().accent(), a));
        } else {
            r.roundedRect(px, py, PILL_W, PILL_H, rad, Color.scaleAlpha(Tokens.surface().surfaceHi(), a));
            r.border(px, py, PILL_W, PILL_H, rad, Tokens.border().thickness(), Color.scaleAlpha(Tokens.border().defaultColor(), a));
        }
        float knobR = rad - PILL_PAD;
        float knobCx = on ? (px + PILL_W - PILL_PAD - knobR) : (px + PILL_PAD + knobR);
        r.circle(knobCx, py + rad, knobR, Color.scaleAlpha(Tokens.palette().white(), a));
    }

    // ---- input ---------------------------------------------------------------

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || groups.isEmpty() || !contains(mx, my)) return false;

        // LEFT sub-rail: a pill click toggles the whole group; a row click selects it.
        if (mx < x + RAIL_W) {
            for (int i = 0; i < groups.size(); i++) {
                float ry = y + PAD + i * RAIL_ROW;
                if (my < ry || my >= ry + RAIL_ROW) continue;
                GroupUi g = groups.get(i);
                float pillX = x + RAIL_W - PAD - PILL_W, pillY = ry + (RAIL_ROW - PILL_H) / 2f;
                if (inPill(mx, my, pillX, pillY)) {
                    boolean show = !anyVisible(g.entries);         // any on -> turn all off; all off -> all on
                    for (ParticleCatalog.Entry e : g.entries) ParticleVisibility.setVisible(e.id(), show);
                } else if (i != selected) {
                    selected = i; onEnter();
                }
                return true;
            }
            return true;   // swallow clicks on the sub-rail background
        }

        // RIGHT list: a pill click toggles that particle.
        float dx = x + RAIL_W + GAP, dw = x + w - dx;
        float listY = y + HEAD_H + PAD, listH = y + h - PAD - listY;
        if (mx >= dx && my >= listY && my <= listY + listH) {
            int i = (int) ((my - listY + scroll) / LIST_ROW);
            List<ParticleCatalog.Entry> rows = current().entries;
            if (i >= 0 && i < rows.size()) {
                float pillX = dx + dw - PILL_W - PAD;
                float ry = listY + i * LIST_ROW - scroll;
                if (inPill(mx, my, pillX, ry + (LIST_ROW - PILL_H) / 2f)) {
                    var id = rows.get(i).id();
                    ParticleVisibility.setVisible(id, ParticleVisibility.isHidden(id));   // flip
                }
                return true;
            }
        }
        return false;
    }

    private static boolean inPill(double mx, double my, float px, float py) {
        return mx >= px - 3 && mx <= px + PILL_W + 3 && my >= py - 3 && my <= py + PILL_H + 3;
    }

    @Override public boolean mouseScrolled(double mx, double my, double amount) {
        if (scrollMax <= 0f || mx < x + RAIL_W) return false;
        float before = scroll;
        scroll = Math.max(0f, Math.min(scrollMax, scroll - (float) amount * LIST_ROW));
        return scroll != before;
    }
}

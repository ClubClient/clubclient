package com.club.ui.menu;

import com.club.modules.particles.ParticleCatalog;
import com.club.modules.particles.ParticleGroup;
import com.club.modules.particles.ParticleVisibility;
import com.club.ui.Color;
import com.club.ui.UiContext;
import com.club.ui.UiRenderer;
import com.club.ui.component.Container;
import com.club.ui.motion.Transition;
import com.club.ui.text.TextStyle;
import com.club.ui.text.Weight;
import com.club.ui.theme.Tokens;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Particles category content — a TWO-PANE surface that replaces the card grid when the Particles
 * category is active (owner-designed; see the approved mock). LEFT: a sub-rail of particle GROUPS, each with
 * an On/Off pill that sets the whole group. RIGHT: the selected group's particles, each with its own On/Off
 * pill, scrollable — or, while the mini-search has text, a FLAT list of every matching particle across all
 * groups (each tagged with its group).
 *
 * <p>WHY FULLY CUSTOM-DRAWN (no {@code Toggle} children): the config hidden-set is the single source of
 * truth, and every pill reads it LIVE via {@link ParticleVisibility}. So a group master and a per-particle
 * pill can never disagree — flip a particle and the group pill reflects it the same frame, no state to sync.
 *
 * <p>MOTION, matched to the rest of the menu: entering plays ONE cascade (group rows, then the right rows,
 * one wave via {@link TileMotion#categoryEnter}); switching group slides the active-row highlight like the
 * rail indicator; each pill's knob slides and its track cross-fades on toggle. Interaction stays on the brand
 * accent; the category's gold is identity only (the active bar). The search box itself is a {@code SearchField}
 * owned by the screen, positioned into {@link #searchBounds()} — this class only reads the resulting filter.
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
    private final List<ParticleCatalog.Entry> all = new ArrayList<>();   // flat, for the global search
    private int selected;
    private float scroll, scrollMax;
    private String filter = "";

    // Entrance cascades — one wave: groups first, then the right-pane rows (offset by group count).
    private TileMotion[] groupMotion = new TileMotion[0];
    private TileMotion[] rowMotion = new TileMotion[0];
    // Active-group highlight Y (local, within the sub-rail): slides between groups like the rail indicator.
    private Transition selBar;
    // Per-pill knob ease (key = particle id string, or "grp:"+group). Bounded by the registry — never pruned.
    private final Map<Object, Transition> pillAnim = new HashMap<>();

    ParticlesPane() {
        Map<ParticleGroup, List<ParticleCatalog.Entry>> byGroup = new EnumMap<>(ParticleGroup.class);
        for (ParticleGroup g : ParticleGroup.values()) byGroup.put(g, new ArrayList<>());
        for (ParticleCatalog.Entry e : ParticleCatalog.all()) { byGroup.get(e.group()).add(e); all.add(e); }
        for (ParticleGroup g : ParticleGroup.values()) {
            List<ParticleCatalog.Entry> es = byGroup.get(g);
            if (!es.isEmpty()) groups.add(new GroupUi(g, es));   // OTHER shows only if a modded type landed there
        }
        onEnter();
    }

    /** Called by the screen every time the Particles category is (re)entered — clears the filter and replays
     *  the full-panel wave. */
    void onEnter() {
        if (groups.isEmpty()) return;
        filter = ""; scroll = 0f; selBar = null;
        groupMotion = new TileMotion[groups.size()];
        for (int i = 0; i < groupMotion.length; i++) groupMotion[i] = TileMotion.categoryEnter(i);
        seedRows(groups.size());   // the wave continues into the right pane after the last group row
    }

    /** Screen hook: the mini-search text changed. Empty -> the selected group; non-empty -> a flat match list. */
    void setFilter(String q) {
        if (groups.isEmpty()) return;
        String f = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        if (f.equals(filter)) return;
        filter = f; scroll = 0f;
        seedRows(0);               // re-cascade the new result set (no group offset — this isn't a category enter)
    }

    private void seedRows(int startIndex) {
        List<ParticleCatalog.Entry> rows = viewRows();
        rowMotion = new TileMotion[rows.size()];
        for (int i = 0; i < rowMotion.length; i++) rowMotion[i] = TileMotion.categoryEnter(startIndex + i);
    }

    /** The right pane's current rows: the selected group, or — while searching — every match across all groups. */
    private List<ParticleCatalog.Entry> viewRows() {
        if (filter.isEmpty()) return current().entries;
        List<ParticleCatalog.Entry> out = new ArrayList<>();
        for (ParticleCatalog.Entry e : all) if (e.label().toLowerCase(Locale.ROOT).contains(filter)) out.add(e);
        return out;
    }

    private GroupUi current() { return groups.get(Math.min(selected, groups.size() - 1)); }

    private static boolean anyVisible(List<ParticleCatalog.Entry> es) {
        for (ParticleCatalog.Entry e : es) if (!ParticleVisibility.isHidden(e.id())) return true;
        return false;
    }

    /** Eased knob position [0,1] for a pill — the smooth on/off animation (knob slide + track cross-fade). */
    private float knob(Object key, boolean on, float now) {
        Transition t = pillAnim.computeIfAbsent(key,
                k -> new Transition(on ? 1f : 0f, Tokens.motion().durations().normal(), Tokens.motion().easings().standard()));
        t.target(on ? 1f : 0f, now);
        return t.value(now);
    }

    /** Where the screen should place the shared SearchField — the top-right of the detail header. Kept compact
     *  (short "Search" placeholder, no "/" hint) so it doesn't read as a twin of the global module search. */
    float[] searchBounds() {
        float dx = x + RAIL_W + GAP, dw = x + w - dx;
        float sw = Math.min(dw * 0.48f, 158f), sh = 22f;
        return new float[]{ dx + dw - sw, y + (HEAD_H - sh) / 2f, sw, sh };
    }

    // ---- layout --------------------------------------------------------------

    @Override public void layout(float x, float y, float w, float h) {
        super.layout(x, y, w, h);
        if (groups.isEmpty()) return;
        float listH = h - PAD - HEAD_H - PAD;
        float contentH = viewRows().size() * LIST_ROW;
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
        boolean searching = !filter.isEmpty();

        // LEFT — group sub-rail, a recessed sub-tray (one tone below the content well)
        float railX = x, railY = y;
        r.roundedRect(railX, railY, RAIL_W, h, Tokens.radius().md(), Tokens.surface().wellShallow());

        // sliding highlight (like the rail indicator): eases to the selected row's local Y
        float selLocalY = PAD + selected * RAIL_ROW;
        if (selBar == null) selBar = new Transition(selLocalY, Tokens.motion().durations().normal(), Tokens.motion().easings().standard());
        selBar.target(selLocalY, now);
        float barY = railY + selBar.value(now);
        r.roundedRect(railX + 6, barY + 2, RAIL_W - 12, RAIL_ROW - 4, Tokens.radius().sm(), Tokens.surface().surfaceHi());
        r.roundedRect(railX + 2, barY + 6, 3, RAIL_ROW - 12, 1.5f, Tokens.categories().particles());   // gold identity bar

        for (int i = 0; i < groups.size(); i++) {
            GroupUi g = groups.get(i);
            float ta = groupMotion[i].tick(now);
            float ry = railY + PAD + i * RAIL_ROW + groupMotion[i].driftY(ta);
            int nameCol = Color.scaleAlpha(i == selected ? Tokens.palette().textHi() : Tokens.palette().textMuted(), ta);
            ctx.text().draw(g.group.label(), railX + 16, ry + (RAIL_ROW - lh) / 2f, TextStyle.of(lw, ls, nameCol));
            drawPill(r, railX + RAIL_W - PAD - PILL_W, ry + (RAIL_ROW - PILL_H) / 2f,
                    knob("grp:" + g.group.name(), anyVisible(g.entries), now), ta);
        }

        // RIGHT — detail: header (group name, or "Search results") + scrolling list.
        // The search box itself is drawn by the screen's SearchField, over searchBounds() on the right.
        float dx = x + RAIL_W + GAP, dw = x + w - dx;
        String head = searching ? "Search results" : current().group.label();
        ctx.text().draw(head, dx + 2, y + (HEAD_H - Tokens.type().heading().lineHeight()) / 2f,
                TextStyle.of(Tokens.type().heading().weight(), Tokens.type().heading().size(), Tokens.palette().textHi()));
        float hairY = y + HEAD_H;
        r.rect(dx, hairY, dw, 1f, Tokens.border().subtle());

        float listY = hairY + PAD, listH = y + h - PAD - listY;
        r.pushClip(dx, listY, dw, listH);
        List<ParticleCatalog.Entry> rows = viewRows();
        if (rows.isEmpty()) {
            ctx.text().draw("Nothing matches", dx + dw / 2f, listY + listH / 2f - lh / 2f,
                    TextStyle.of(lw, ls, Tokens.palette().textFaint()).align(com.club.ui.text.Align.CENTER));
        }
        for (int i = 0; i < rows.size(); i++) {
            ParticleCatalog.Entry e = rows.get(i);
            float ta = i < rowMotion.length ? rowMotion[i].tick(now) : 1f;
            float dy = i < rowMotion.length ? rowMotion[i].driftY(ta) : 0f;
            float ry = listY + i * LIST_ROW - scroll + dy;
            if (ry + LIST_ROW < listY || ry > listY + listH) continue;   // cheap cull outside the viewport
            boolean vis = !ParticleVisibility.isHidden(e.id());
            int nameCol = Color.scaleAlpha(vis ? Tokens.palette().textHi() : Tokens.palette().textMuted(), ta);
            ctx.text().draw(e.label(), dx + PAD, ry + (LIST_ROW - lh) / 2f, TextStyle.of(lw, ls, nameCol));
            // while searching, tag each row with its group so a flat list stays legible
            if (searching) {
                String tag = e.group().label();
                float tagW = ctx.text().width(tag, lw, ls - 1.5f);
                ctx.text().draw(tag, dx + dw - PILL_W - PAD - 10 - tagW, ry + (LIST_ROW - lh) / 2f,
                        TextStyle.of(lw, ls - 1.5f, Color.scaleAlpha(Tokens.palette().textFaint(), ta)));
            }
            drawPill(r, dx + dw - PILL_W - PAD, ry + (LIST_ROW - PILL_H) / 2f, knob(e.id().toString(), vis, now), ta);
        }
        r.popClip();

        if (scrollMax > 0f) {   // slim neutral scrollbar (accent is for interaction, not chrome)
            float trackH = listH, thumbH = Math.max(18f, trackH * trackH / (rows.size() * LIST_ROW));
            float thumbY = listY + (trackH - thumbH) * (scroll / scrollMax);
            r.roundedRect(dx + dw - 3f, thumbY, 3f, thumbH, 1.5f, Tokens.palette().textFaint());
        }
    }

    /** Draws the Toggle pill (custom, live state). {@code k}∈[0,1] is the eased on-ness (knob slide + track
     *  cross-fade); {@code a} is the cascade alpha. Off = inset surface + hairline; on = flat accent; white puck. */
    private void drawPill(UiRenderer r, float px, float py, float k, float a) {
        float rad = PILL_H / 2f;
        int fill = Color.lerp(Tokens.surface().surfaceHi(), Tokens.accent().accent(), k);
        r.roundedRect(px, py, PILL_W, PILL_H, rad, Color.scaleAlpha(fill, a));
        r.border(px, py, PILL_W, PILL_H, rad, Tokens.border().thickness(),
                Color.scaleAlpha(Tokens.border().defaultColor(), a * (1f - k)));   // hairline fades out as it turns on
        float knobR = rad - PILL_PAD;
        float leftCx = px + PILL_PAD + knobR, rightCx = px + PILL_W - PILL_PAD - knobR;
        r.circle(leftCx + (rightCx - leftCx) * k, py + rad, knobR, Color.scaleAlpha(Tokens.palette().white(), a));
    }

    // ---- input ---------------------------------------------------------------

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || groups.isEmpty() || !contains(mx, my)) return false;

        // LEFT sub-rail: a pill click toggles the whole group; a row click selects it (and clears the search).
        if (mx < x + RAIL_W) {
            for (int i = 0; i < groups.size(); i++) {
                float ry = y + PAD + i * RAIL_ROW;
                if (my < ry || my >= ry + RAIL_ROW) continue;
                GroupUi g = groups.get(i);
                float pillX = x + RAIL_W - PAD - PILL_W, pillY = ry + (RAIL_ROW - PILL_H) / 2f;
                if (inPill(mx, my, pillX, pillY)) {
                    boolean show = !anyVisible(g.entries);         // any on -> turn all off; all off -> all on
                    for (ParticleCatalog.Entry e : g.entries) ParticleVisibility.setVisible(e.id(), show);
                } else if (i != selected || !filter.isEmpty()) {
                    selected = i; scroll = 0f; seedRows(0);         // slide the bar (selBar eases in render), re-cascade
                }
                return true;
            }
            return true;   // swallow clicks on the sub-rail background
        }

        // RIGHT list: a pill click toggles that particle (works in group view and in search results alike).
        float dx = x + RAIL_W + GAP, dw = x + w - dx;
        float listY = y + HEAD_H + PAD, listH = y + h - PAD - listY;
        if (mx >= dx && my >= listY && my <= listY + listH) {
            int i = (int) ((my - listY + scroll) / LIST_ROW);
            List<ParticleCatalog.Entry> rows = viewRows();
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

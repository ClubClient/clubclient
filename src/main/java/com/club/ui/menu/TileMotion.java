package com.club.ui.menu;

import com.club.ui.motion.Transition;
import com.club.ui.theme.Tokens;

/**
 * One module card's motion across grid rebuilds (Stage 21 reflow choreography, owner-tuned). Keyed by
 * Module in {@code ClubMenuScreen} so it SURVIVES rebuilds — fast typing retargets the SAME transitions
 * mid-flight (the interface flows, it never restarts). Extracted from the screen (Stage 28) so the
 * state machine — the part that survived six rounds of fixes — can be unit-tested in isolation.
 *
 * <p>Phases: exits fade+shrink from 0ms, receding FROM THE TAIL ({@link #scheduleHideFromTail}); survivors
 * re-aim at +70ms ({@link #scheduleMove}); enters fade+grow at +110ms. Category opens stagger enters
 * 42ms/card and RISE into place (6px drift, not scale); search never staggers and never drifts. The eased
 * position lives in WINDOW space so cards move rigidly with the frame (screen-space easing made them chase
 * the window on open). Every duration/easing here is the owner-frozen value — do not retune.</p>
 */
final class TileMotion {
    // Owner-frozen pacing (final round: everything ×1.4 slower). Moved here with the state machine.
    static final float EXIT_DUR = 0.30f, ENTER_DUR = 0.28f, MOVE_DUR = 0.34f;
    static final float MOVE_DELAY = 0.07f, ENTER_DELAY = 0.11f, CAT_STAGGER = 0.042f;
    /** Category enters RISE into place (6px drift, translation not scale) — a bare fade read flat. */
    static final float CAT_DRIFT = 6f;
    /** Exit cascade: 105ms per card, receding FROM THE TAIL — the last card in the grid dissolves
     *  first and the wave walks back toward the start. */
    static final float EXIT_STAGGER = 0.105f;
    static final float TILE_SCALE_FROM = 0.97f;   // enter 0.97→1; exit mirrors it

    Transition px, py;      // eased position — created snapped on first sighting (no fly-in)
    Transition fade;        // 0→1 enter / →0 exit; recreated per direction (durations differ),
                            //   always seeded from the current value → turn-arounds stay smooth
    float tx, ty;           // last applied position target
    boolean hasPos;
    float showDelay;        // enter delay (stagger / +110ms phase), resolved on first render —
    float showAt = -1f;     //   rebuilds can run before the ui clock ticks (init)
    boolean shown;
    float hideAt = -1f;     // exit gate: the dissolve starts once time passes this (exit cascade)
    boolean scaleIn = true; // search language: fade+scale; category cascades are FADE-ONLY
                            //   (the 0.97→1 pop per card read as popcorn — owner)
    boolean driftIn;        // category language: the card rises 6px into place as it fades in
    float moveAt;           // survivor gate: position re-aims only after this (+70ms phase)
    boolean movePending;
    boolean leaving;
    float bx, by, bw, bh;   // last visual box — the frozen stage for a dissolving card

    private static com.club.ui.motion.Easing decel() { return Tokens.motion().easings().decelerate(); }
    private static com.club.ui.motion.Easing standard() { return Tokens.motion().easings().standard(); }

    // ---- factories (grid rebuild) --------------------------------------------

    /** Menu OPEN: cards ride the whole-window entrance fade, already shown (no per-card animation). */
    static TileMotion opening() {
        TileMotion tm = new TileMotion();
        tm.fade = new Transition(1f, ENTER_DUR, decel());
        tm.shown = true;
        return tm;
    }

    /** Category switch: a fade-only cascade (no scale pop), rising 6px into place, staggered by index. */
    static TileMotion categoryEnter(int index) {
        TileMotion tm = new TileMotion();
        tm.fade = new Transition(0f, ENTER_DUR, decel());
        tm.showDelay = index * CAT_STAGGER;
        tm.scaleIn = false;
        tm.driftIn = true;
        return tm;
    }

    /** A brand-new search match: fade+grow in after the +110ms enter phase. */
    static TileMotion searchEnter() {
        TileMotion tm = new TileMotion();
        tm.fade = new Transition(0f, ENTER_DUR, decel());
        tm.showDelay = ENTER_DELAY;
        return tm;
    }

    // ---- reflow transitions ---------------------------------------------------

    /** A card that stopped matching begins its dissolve, seeded from the current fade (turn-arounds stay smooth). */
    void beginExit(float now) {
        leaving = true; shown = true; movePending = false; scaleIn = true;
        fade = new Transition(fade.value(now), EXIT_DUR, standard());
    }

    /** Set this exit's cascade gate. {@code indexInExits} is grid order; the LAST exit fires first
     *  ({@code hideAt = now}) and the wave walks back toward the head. */
    void scheduleHideFromTail(float now, int indexInExits, int exitCount) {
        hideAt = now + (exitCount - 1 - indexInExits) * EXIT_STAGGER;
    }

    /** A dissolving card matched again mid-exit: turn around toward shown, no restart. */
    void reverseToEnter(float now) {
        leaving = false; shown = true; hideAt = -1f;
        Transition f = new Transition(fade.value(now), ENTER_DUR, decel());
        f.target(1f, now);
        fade = f;
    }

    /** A survivor whose slot changed re-aims only after the +70ms move phase. */
    void scheduleMove(float now) {
        movePending = true; moveAt = now + MOVE_DELAY;
    }

    // ---- per-frame gates (render) --------------------------------------------

    /** Resolve the lazy show/hide gates against the live clock and return the current fade value
     *  ({@code ta}). showAt is bound on the first render (a rebuild can run before the ui clock ticks). */
    float tick(float now) {
        if (showAt < 0f) showAt = now + showDelay;
        if (!shown && now >= showAt) { shown = true; fade.target(1f, now); }
        if (hideAt >= 0f && now >= hideAt) { hideAt = -1f; fade.target(0f, now); }
        return fade.value(now);
    }

    /** Non-leaving position machine: first sighting snaps (never flies in), a pending survivor move
     *  re-aims once its gate fires, and any other slot change re-aims immediately. Updates the visual
     *  box {@link #bx}/{@link #by}/{@link #bw}/{@link #bh} (window-relative). */
    void place(float now, float relX, float relY, float w, float h) {
        if (!hasPos) {
            px = new Transition(relX, MOVE_DUR, standard());
            py = new Transition(relY, MOVE_DUR, standard());
            tx = relX; ty = relY; hasPos = true;
        } else if (movePending) {
            if (now >= moveAt) {
                movePending = false;
                tx = relX; ty = relY;
                px.target(relX, now); py.target(relY, now);
            }
        } else if (tx != relX || ty != relY) {
            tx = relX; ty = relY;
            px.target(relX, now); py.target(relY, now);
        }
        bx = px.value(now); by = py.value(now);
        bw = w; bh = h;
    }

    /** Extra Y offset for the category rise (0 for search/open). Applied on top of {@link #by}. */
    float driftY(float ta) { return driftIn ? (1f - Math.min(1f, ta)) * CAT_DRIFT : 0f; }

    /** Box scale from the fade (search fade+scale; category/open are fade-only → 1). */
    float scale(float ta) { return scaleIn ? TILE_SCALE_FROM + (1f - TILE_SCALE_FROM) * Math.min(1f, ta) : 1f; }

    /** Current fade value (for the leaving-layer prune once a dissolve completes). */
    float fadeValue(float now) { return fade.value(now); }
}

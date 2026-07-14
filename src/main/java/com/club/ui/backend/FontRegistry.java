package com.club.ui.backend;

import com.club.ClubMod;
import com.club.ui.text.GlyphSource;
import com.club.ui.text.MsdfMetrics;
import com.club.ui.text.ResolvedGlyph;
import com.club.ui.text.Weight;

/**
 * Lazy atlas registry that exposes the three Inter MSDF weights as a {@link GlyphSource}.
 *
 * <p>Atlas indices: 0 = REGULAR, 1 = MEDIUM, 2 = SEMIBOLD. Each atlas is loaded once on
 * first access and then cached for the lifetime of this registry.</p>
 *
 * <p>GLYPH-CACHE SEAM: {@link #resolve} currently consults only the atlas matching the
 * requested weight. A future implementation may try other atlases, families, or icon/emoji
 * atlases when the primary lookup misses, and write a different {@code out.atlasId} — the
 * layout and renderer are unaffected because they key the texture off {@code out.atlasId}.</p>
 */
public final class FontRegistry implements GlyphSource {

    // 0=REGULAR, 1=MEDIUM, 2=SEMIBOLD, 3=ICONS (Stage 11 — PUA-mapped SDF icon glyphs)
    private final MsdfAtlas[] atlases = new MsdfAtlas[4];
    private static final int ICONS = 3;
    /** Set on the first icon-atlas load failure: PUA lookups then fall through to the '?' path
     *  instead of re-throwing every frame (a missing icon atlas must not break text). */
    private boolean iconsBroken;

    // -------------------------------------------------------------------------
    // GlyphSource
    // -------------------------------------------------------------------------

    /**
     * Resolves {@code codePoint} at {@code weight} into {@code out}.
     * Private-Use-Area code points (U+E000..U+F8FF) resolve through the ICON atlas first
     * (the icon seam — an icon IS a glyph; layout/renderer key the texture off
     * {@code out.atlasId}). Everything else falls back to '?' if the primary code point
     * is absent — see {@link #noteMissingGlyph} for what that fallback actually hides.
     * Returns {@code false} only when nothing resolves.
     */
    @Override
    public boolean resolve(Weight weight, int codePoint, ResolvedGlyph out) {
        if (codePoint >= 0xE000 && codePoint <= 0xF8FF && !iconsBroken) {
            try {
                MsdfMetrics.Glyph ig = atlas(ICONS).metrics.get(codePoint);
                if (ig != null) {
                    out.atlasId = ICONS;
                    out.glyph   = ig;
                    return true;
                }
            } catch (Exception e) {
                iconsBroken = true;
                // WARN, not ERROR: icons vanish, text survives — a degraded UI, not a dead one. Once per
                // session (the iconsBroken flag), and the cause travels with it or nobody can act on it.
                ClubMod.LOGGER.warn("[Club] icon atlas failed to load — icons are disabled for this session; "
                        + "text keeps rendering", e);
            }
        }
        int id = atlasId(weight);
        MsdfAtlas a = atlas(id);
        MsdfMetrics.Glyph g = a.metrics.get(codePoint);
        if (g == null) {
            noteMissingGlyph(codePoint);
            g = a.metrics.get((int) '?');   // '?' is in every atlas, which is why a miss is otherwise SILENT
        }
        if (g == null) return false;
        out.atlasId = id;
        out.glyph   = g;
        return true;
    }

    // -------------------------------------------------------------------------
    // Missing-glyph diagnostics — the '?' fallback, said out loud
    // -------------------------------------------------------------------------

    /** Once-flag: the first code point the TEXT atlas cannot draw. Keeps this to one line per session. */
    private boolean missingTextGlyphLogged;
    /** Once-flag: the first PUA code point the ICON atlas does not contain (a different failure — see below). */
    private boolean missingIconGlyphLogged;

    /**
     * Called on every code point the atlas does not contain, just before it is replaced by '?'.
     *
     * <p>WHAT THE ATLAS COVERS. Read out of the committed {@code assets/club/ui/font/msdf/inter_*.json}
     * (all three weights carry the same set): ASCII, most of Latin-1, part of Cyrillic, and nine
     * typographic punctuation marks — the highest code point in the whole set is U+2192. There is no CJK
     * in it, and no Arabic, Hebrew, Devanagari or emoji. {@code com.club.ui.text.Charset} is the checked
     * statement of exactly which code points are in; do not restate the set here, it would drift.
     * Because '?' (U+003F) IS in the atlas, the miss never surfaces as a return value — {@link #resolve}
     * keeps returning {@code true} — so an uncovered script is INVISIBLE to every caller and shows up
     * only as question marks on screen.</p>
     *
     * <p>WHY THIS LINE EXISTS. {@code TargetElement} feeds server-supplied entity names straight into
     * this. On a Chinese, Japanese or Korean client every mob name under the crosshair draws as '?'. What
     * is drawn is deliberately left exactly as it was — but the log now carries the reason, so a bug
     * report can say "no glyph for U+4E2D" instead of attaching a screenshot full of question marks.</p>
     *
     * <p>WHAT THE REAL FIX WOULD BE. Regenerating the atlases with the missing ranges. That is a build
     * asset, not a code change, and it is not free: a CJK range is thousands of glyphs and would take the
     * atlas texture from kilobytes into megabytes for every player, including the ones who will never see
     * a CJK character. The alternative is a dynamic glyph cache that rasterises on demand into a scratch
     * atlas — the GLYPH-CACHE SEAM named in this class's header. Both are open decisions, not oversights.</p>
     */
    private void noteMissingGlyph(int codePoint) {
        // Hot path: this runs for every missing glyph of every frame, so everything expensive (the hex
        // formatting, the log call) lives behind the once-flags. Steady state is two int compares.
        if (codePoint >= 0xE000 && codePoint <= 0xF8FF) {
            // A PUA code point that reaches the TEXT atlas is one of ours that the ICON atlas did not have —
            // either the atlas is broken (already logged above) or the icon was never packed into it. That
            // is a packaging bug of ours, not a language the user picked, so it gets its own message.
            if (missingIconGlyphLogged) return;
            missingIconGlyphLogged = true;
            ClubMod.LOGGER.warn("[Club] no icon glyph for U+{} — drawing '?' instead (icon atlas unavailable, "
                    + "or that icon is not in it)", String.format("%04X", codePoint));
            return;
        }
        if (missingTextGlyphLogged) return;
        missingTextGlyphLogged = true;
        ClubMod.LOGGER.warn("[Club] text atlas has no glyph for U+{} — drawing '?' instead. The atlas covers "
                + "Latin and Cyrillic only: CJK and other scripts are not in it, so those characters (entity "
                + "names on the Target HUD, above all) render as question marks. Not a corrupt install.",
                String.format("%04X", codePoint));
    }

    /** Returns the metrics object for a given weight (line height, ascent, descent). */
    @Override
    public MsdfMetrics metrics(Weight weight) {
        return atlas(atlasId(weight)).metrics;
    }

    // -------------------------------------------------------------------------
    // Renderer accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the atlas for {@code id} (0/1/2/3). Used by the renderer to bind the
     * correct texture and read {@code pxRange}.
     */
    public MsdfAtlas atlasById(int id) {
        return atlas(id);
    }

    /** True if {@code id} is the icon atlas — the renderer keeps ICON failures non-fatal. */
    public boolean isIconAtlas(int id) { return id == ICONS; }

    /**
     * Disables icon resolution for the session — called by the renderer when the icon TEXTURE
     * fails after a successful metrics load (icons.json fine, icons.png missing/corrupt).
     * Subsequent PUA lookups fall through to the '?' path; text keeps rendering MODERN.
     */
    public void disableIcons(Exception cause) {
        // WARN for the same reason as the metrics failure above: icons are gone for the session, text is not.
        // The once-flag is the caller's iconsBroken, so a repeated failure cannot spam the log.
        if (!iconsBroken) {
            ClubMod.LOGGER.warn("[Club] icon atlas texture failed to bind — icons are disabled for this "
                    + "session; text keeps rendering", cause);
        }
        iconsBroken = true;
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    /** Maps a Weight enum to an atlas index (0/1/2). */
    private static int atlasId(Weight w) {
        return switch (w) {
            case REGULAR  -> 0;
            case MEDIUM   -> 1;
            case SEMIBOLD -> 2;
        };
    }

    /** Returns the name string used in the resource path for atlas index {@code id}. */
    private static String atlasName(int id) {
        return switch (id) {
            case 0  -> "regular";
            case 1  -> "medium";
            default -> "semibold";
        };
    }

    /** Lazy atlas accessor — loads on first access. */
    private MsdfAtlas atlas(int id) {
        if (atlases[id] == null) {
            atlases[id] = (id == ICONS) ? MsdfAtlas.loadIcons() : MsdfAtlas.load(atlasName(id));
        }
        return atlases[id];
    }
}

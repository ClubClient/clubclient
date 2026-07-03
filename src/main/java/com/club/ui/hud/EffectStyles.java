package com.club.ui.hud;

import com.club.ui.IconGlyph;
import net.minecraft.entity.effect.StatusEffectInstance;

/**
 * Effect → (icon, association color) for the icon-only Effects element (Stage 14). One curated
 * entry per vanilla effect a player actually meets; anything unknown (rare/modded) falls back to
 * the generic test tube tinted with the effect's own liquid color, normalized into the HUD's
 * pastel band so it stays readable on the dark capsule and never screams brighter than the
 * curated set. Association colors are identity only — time/state speaks through the row's line.
 */
final class EffectStyles {
    private EffectStyles() {}

    record Style(IconGlyph icon, int color) {}

    static Style of(StatusEffectInstance e) {
        String id = e.getEffectType().getKey().map(k -> k.getValue().getPath()).orElse("");
        Style s = byId(id);
        return s != null ? s : new Style(IconGlyph.FX_GENERIC, normalize(e.getEffectType().value().getColor()));
    }

    /** Curated map (null → caller falls back to the generic tube + normalized liquid color). */
    static Style byId(String id) {
        return switch (id) {
            case "speed"           -> new Style(IconGlyph.FX_SPEED,           0xFF7CC4E8);
            case "slowness"        -> new Style(IconGlyph.FX_SLOWNESS,        0xFF8C9BB5);
            case "haste"           -> new Style(IconGlyph.FX_HASTE,           0xFFE8C97A);
            case "mining_fatigue"  -> new Style(IconGlyph.FX_MINING_FATIGUE,  0xFF98A2B3);
            case "strength"        -> new Style(IconGlyph.FX_STRENGTH,        0xFFC26161);   // deeper red, de-pinked (owner)
            case "jump_boost"      -> new Style(IconGlyph.FX_JUMP_BOOST,      0xFF7FD68A);
            case "nausea"          -> new Style(IconGlyph.FX_NAUSEA,          0xFFA87FB8);
            case "regeneration"    -> new Style(IconGlyph.FX_REGENERATION,    0xFFE38BB0);
            case "resistance"      -> new Style(IconGlyph.FX_RESISTANCE,      0xFFA9B4C4);
            case "fire_resistance" -> new Style(IconGlyph.FX_FIRE_RESISTANCE, 0xFFE0A24E);
            case "water_breathing" -> new Style(IconGlyph.FX_WATER_BREATHING, 0xFF6FB9D6);
            case "invisibility"    -> new Style(IconGlyph.FX_INVISIBILITY,    0xFFB8C0CC);
            case "blindness"       -> new Style(IconGlyph.FX_BLINDNESS,       0xFF8B95A6);
            case "night_vision"    -> new Style(IconGlyph.FX_NIGHT_VISION,    0xFF8F9FE8);
            case "hunger"          -> new Style(IconGlyph.FX_HUNGER,          0xFFB09A6A);
            case "poison"          -> new Style(IconGlyph.FX_POISON,          0xFF87A363);
            case "wither"          -> new Style(IconGlyph.FX_WITHER,          0xFF9AA1AB);
            case "health_boost"    -> new Style(IconGlyph.FX_HEALTH_BOOST,    0xFFDB7070);
            case "absorption"      -> new Style(IconGlyph.FX_ABSORPTION,      0xFFE8D27A);
            case "weakness"        -> new Style(IconGlyph.FX_WEAKNESS,        0xFF9AA0AA);
            case "levitation"      -> new Style(IconGlyph.FX_LEVITATION,      0xFFC4B5E8);
            case "slow_falling"    -> new Style(IconGlyph.FX_SLOW_FALLING,    0xFFE8E0B8);
            case "glowing"         -> new Style(IconGlyph.FX_GLOWING,         0xFFEFE6A8);
            case "darkness"        -> new Style(IconGlyph.FX_DARKNESS,        0xFF8E96A8);
            case "luck"            -> new Style(IconGlyph.FX_LUCK,            0xFF7FBFA6);
            default -> null;
        };
    }

    /** Clamps a raw effect liquid color into the pastel band: saturation ≤ .52, value in [.72, .88]. */
    static int normalize(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float v = max, sat = max > 0 ? (max - min) / max : 0f;
        float h;
        if (max == min)     h = 0f;
        else if (max == r)  h = ((g - b) / (max - min)) % 6f;
        else if (max == g)  h = (b - r) / (max - min) + 2f;
        else                h = (r - g) / (max - min) + 4f;
        h *= 60f; if (h < 0f) h += 360f;
        sat = Math.min(sat, 0.52f);
        v = Math.max(0.72f, Math.min(0.88f, v));
        float c = v * sat, x = c * (1f - Math.abs((h / 60f) % 2f - 1f)), m = v - c;
        float rr, gg, bb;
        if (h < 60f)       { rr = c; gg = x; bb = 0; }
        else if (h < 120f) { rr = x; gg = c; bb = 0; }
        else if (h < 180f) { rr = 0; gg = c; bb = x; }
        else if (h < 240f) { rr = 0; gg = x; bb = c; }
        else if (h < 300f) { rr = x; gg = 0; bb = c; }
        else               { rr = c; gg = 0; bb = x; }
        return 0xFF000000 | (Math.round((rr + m) * 255f) << 16) | (Math.round((gg + m) * 255f) << 8)
                | Math.round((bb + m) * 255f);
    }
}

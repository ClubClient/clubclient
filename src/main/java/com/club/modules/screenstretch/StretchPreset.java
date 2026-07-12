package com.club.modules.screenstretch;

/** Target aspect-ratio presets for the screen-stretch module. */
public enum StretchPreset {
    R4_3 ("4:3",   4f / 3f),
    R16_9("16:9",  16f / 9f),
    R16_10("16:10",16f / 10f),
    R21_9("21:9",  21f / 9f),
    R32_9("32:9",  32f / 9f),
    AUTO ("Auto",  -1f); // follow window's real aspect (no stretch)

    private final String label;
    private final float aspect;

    StretchPreset(String label, float aspect) {
        this.label = label;
        this.aspect = aspect;
    }

    public String label() { return label; }

    /** Target aspect ratio, or -1 for AUTO. */
    public float aspect() { return aspect; }

    public boolean isAuto() { return this == AUTO; }

    /** Unknown/junk value → AUTO: the safe fallback is "don't touch the projection". Falling back to a
     *  real ratio silently WARPED the world of anyone whose monitor isn't that ratio (Stage 59 audit). */
    public static StretchPreset fromName(String name) {
        try { return valueOf(name); } catch (Exception e) { return AUTO; }
    }
}
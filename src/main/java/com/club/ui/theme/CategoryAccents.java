package com.club.ui.theme;

/**
 * Per-category identity accents (Stage 11, palette "A", approved 2026-07-02). Muted, lightness-equalized
 * hues calibrated to the Target HP bar's steel-blue (#86A6CC) so no category shouts over another.
 *
 * <p><b>Identity only.</b> These colors mark whose a thing is — a module card's icon chip, state stripe
 * and ghost glyph, and the rail category indicator. All INTERACTION stays on the brand accent
 * ({@link Accent#accent()}): search focus, sliders, buttons, popover controls are identical in every
 * category. Text is never tinted with a category color (v2.5 rule).
 */
public record CategoryAccents(int combat, int visuals, int player, int misc, int performance) {}

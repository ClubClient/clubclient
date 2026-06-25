package com.club.ui.text;

/** Distance-field text effects. Rendered by the backend from the MSDF field. */
public sealed interface TextEffect {
    record None() implements TextEffect {}
    record Outline(float widthPx, int color) implements TextEffect {}
    record Shadow(float dx, float dy, float softness, int color) implements TextEffect {}
    record Glow(float radius, int color) implements TextEffect {}
    TextEffect NONE = new None();
}
